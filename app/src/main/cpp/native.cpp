#include <jni.h>
#include <llama.h>
#include <atomic>
#include <algorithm>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include <memory>
#include <stdexcept>
#include <new>

namespace {
std::mutex engine_mutex;
llama_model* model = nullptr;
llama_context* context = nullptr;
std::atomic<bool> cancelled{false};
std::chrono::steady_clock::time_point deadline;
bool abort_generation(void*) { return cancelled.load() || std::chrono::steady_clock::now() > deadline; }
void require_running() {
    if(cancelled.load()) throw std::runtime_error("LANU_AI_CANCELLED");
    if(std::chrono::steady_clock::now() > deadline) throw std::runtime_error("LANU_AI_TIMEOUT");
}
void clear() { if(context) llama_free(context); if(model) llama_model_free(model); context=nullptr; model=nullptr; }
void fail(JNIEnv* env, const char* text) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_prepare(JNIEnv*, jobject, jint timeout_seconds) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    cancelled.store(false);
    deadline=std::chrono::steady_clock::now()+std::chrono::seconds(std::max(1, std::min(30, static_cast<int>(timeout_seconds))));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lanu_yalan_ai_NativeBridge_load(JNIEnv* env, jobject, jstring path, jint threads) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try {
        clear();
        require_running();
        llama_log_set([](ggml_log_level, const char*, void*){}, nullptr);
        llama_backend_init();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.progress_callback=[](float, void*) { return !abort_generation(nullptr); };
        const char* p = env->GetStringUTFChars(path, nullptr);
        const std::string file(p); env->ReleaseStringUTFChars(path, p);
        model = llama_model_load_from_file(file.c_str(), params);
        require_running();
        if(!model) throw std::runtime_error("LANU_AI_LOAD");
        auto cp = llama_context_default_params();
        cp.n_ctx=3072; cp.n_batch=256; cp.n_ubatch=128;
        cp.n_outputs_max=1; cp.n_outputs_max_per_seq=1;
        cp.abort_callback=abort_generation;
        const int workers = std::max(1, std::min(4, static_cast<int>(threads)));
        cp.n_threads=workers; cp.n_threads_batch=workers;
        context=llama_init_from_model(model, cp);
        require_running();
        if(!context) throw std::runtime_error("LANU_AI_MEMORY");
        return JNI_TRUE;
    } catch(const std::bad_alloc&) { clear(); fail(env, "LANU_AI_MEMORY"); return JNI_FALSE; }
      catch(const std::exception& e) { clear(); fail(env, e.what()); return JNI_FALSE; }
      catch(...) { clear(); fail(env, "LANU_AI_LOAD"); return JNI_FALSE; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lanu_yalan_ai_NativeBridge_generate(JNIEnv* env, jobject, jbyteArray input, jstring grammar, jint max_tokens) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try {
        if(!context || !model) throw std::runtime_error("LANU_AI_LOAD");
        require_running();
        llama_set_abort_callback(context, abort_generation, nullptr);
        llama_memory_clear(llama_get_memory(context), true);
        const auto length=env->GetArrayLength(input);
        std::string prompt(length, '\0');
        env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(prompt.data()));
        const auto* vocab=llama_model_get_vocab(model);
        int count=-llama_tokenize(vocab,prompt.data(),length,nullptr,0,true,true);
        if(count <= 0 || count + max_tokens >= static_cast<int>(llama_n_ctx(context))) throw std::runtime_error("LANU_AI_CONTEXT");
        std::vector<llama_token> tokens(count);
        count=llama_tokenize(vocab,prompt.data(),length,tokens.data(),count,true,true);
        if(count<=0) throw std::runtime_error("Tokenization failed");
        for(int i=0;i<count;i+=256) {
            require_running();
            auto batch=llama_batch_get_one(tokens.data()+i,std::min(256,count-i));
            const int rc=llama_decode(context,batch);
            require_running();
            if(rc!=0) throw std::runtime_error("LANU_AI_ENGINE");
        }
        const char* g=env->GetStringUTFChars(grammar,nullptr);
        auto* grammar_sampler=llama_sampler_init_grammar(vocab,g,"root");
        env->ReleaseStringUTFChars(grammar,g);
        if(!grammar_sampler) throw std::runtime_error("Grammar failed");
        auto deleter=[](llama_sampler* s){llama_sampler_free(s);};
        std::unique_ptr<llama_sampler,decltype(deleter)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),deleter);
        llama_sampler_chain_add(sampler.get(),grammar_sampler);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(20));
        // Apply penalties to the short candidate list, not the entire vocabulary.
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),128,1.08f,0.0f,0.0f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_p(0.8f,1));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.65f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        std::string output;
        int depth=0;
        bool quoted=false, escaped=false, complete=false;
        for(int i=0;i<max_tokens;i++) {
            require_running();
            auto token=llama_sampler_sample(sampler.get(),context,-1);
            if(llama_vocab_is_eog(vocab,token)) break;
            std::vector<char> piece(256);
            int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
            if(n<0) { piece.resize(-n); n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false); }
            if(n<0) throw std::runtime_error("Text decoding failed");
            output.append(piece.data(),n);
            // The grammar defines one JSON object. Do not decode extra tokens after its closing brace.
            for(int j=0;j<n;j++) {
                const char c=piece[j];
                if(quoted) {
                    if(escaped) escaped=false;
                    else if(c=='\\') escaped=true;
                    else if(c=='"') quoted=false;
                } else if(c=='"') quoted=true;
                else if(c=='{') ++depth;
                else if(c=='}' && --depth==0) complete=true;
            }
            if(complete) break;
            auto batch=llama_batch_get_one(&token,1);
            const int rc=llama_decode(context,batch);
            require_running();
            if(rc!=0) throw std::runtime_error("LANU_AI_ENGINE");
        }
        if(!complete) throw std::runtime_error("LANU_AI_OUTPUT");
        auto result=env->NewByteArray(output.size());
        env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));
        return result;
    } catch(const std::bad_alloc&) { fail(env,"LANU_AI_MEMORY"); return nullptr; }
      catch(const std::exception& e) { fail(env,e.what()); return nullptr; }
      catch(...) { fail(env,"LANU_AI_ENGINE"); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_cancel(JNIEnv*, jobject) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_close(JNIEnv*, jobject) { std::lock_guard<std::mutex> guard(engine_mutex); clear(); }
