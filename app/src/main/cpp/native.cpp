#include <jni.h>
#include <llama.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <vector>
#include <memory>
#include <stdexcept>

namespace {
std::mutex engine_mutex;
llama_model* model = nullptr;
llama_context* context = nullptr;
std::atomic<bool> cancelled{false};
std::chrono::steady_clock::time_point deadline;
bool abort_generation(void*) { return cancelled.load() || std::chrono::steady_clock::now() > deadline; }
void clear() { if(context) llama_free(context); if(model) llama_model_free(model); context=nullptr; model=nullptr; }
void fail(JNIEnv* env, const char* text) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text); }
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lanu_yalan_ai_NativeBridge_load(JNIEnv* env, jobject, jstring path) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try {
        clear();
        llama_log_set([](ggml_log_level, const char*, void*){}, nullptr);
        llama_backend_init();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        const char* p = env->GetStringUTFChars(path, nullptr);
        const std::string file(p); env->ReleaseStringUTFChars(path, p);
        model = llama_model_load_from_file(file.c_str(), params);
        if(!model) return JNI_FALSE;
        auto cp = llama_context_default_params();
        cp.n_ctx=4096; cp.n_batch=256; cp.n_ubatch=128;
        cp.n_threads=4; cp.n_threads_batch=4;
        context=llama_init_from_model(model, cp);
        if(!context) { clear(); return JNI_FALSE; }
        return JNI_TRUE;
    } catch(...) { clear(); fail(env, "Model could not be loaded"); return JNI_FALSE; }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lanu_yalan_ai_NativeBridge_generate(JNIEnv* env, jobject, jbyteArray input, jstring grammar, jint max_tokens) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try {
        if(!context || !model) throw std::runtime_error("Model not loaded");
        cancelled.store(false);
        deadline=std::chrono::steady_clock::now()+std::chrono::seconds(150);
        llama_set_abort_callback(context, abort_generation, nullptr);
        llama_memory_clear(llama_get_memory(context), true);
        const auto length=env->GetArrayLength(input);
        std::string prompt(length, '\0');
        env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(prompt.data()));
        const auto* vocab=llama_model_get_vocab(model);
        int count=-llama_tokenize(vocab,prompt.data(),length,nullptr,0,true,true);
        if(count <= 0 || count + max_tokens >= 4096) throw std::runtime_error("Story is too long");
        std::vector<llama_token> tokens(count);
        count=llama_tokenize(vocab,prompt.data(),length,tokens.data(),count,true,true);
        if(count<=0) throw std::runtime_error("Tokenization failed");
        for(int i=0;i<count;i+=256) {
            if(abort_generation(nullptr)) throw std::runtime_error("Generation interrupted");
            auto batch=llama_batch_get_one(tokens.data()+i,std::min(256,count-i));
            if(llama_decode(context,batch)!=0) throw std::runtime_error("Could not process story");
        }
        const char* g=env->GetStringUTFChars(grammar,nullptr);
        auto* grammar_sampler=llama_sampler_init_grammar(vocab,g,"root");
        env->ReleaseStringUTFChars(grammar,g);
        if(!grammar_sampler) throw std::runtime_error("Grammar failed");
        auto deleter=[](llama_sampler* s){llama_sampler_free(s);};
        std::unique_ptr<llama_sampler,decltype(deleter)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),deleter);
        llama_sampler_chain_add(sampler.get(),grammar_sampler);
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),128,1.08f,0.0f,0.0f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(20));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_p(0.8f,1));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(0.65f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        std::string output;
        for(int i=0;i<max_tokens;i++) {
            if(abort_generation(nullptr)) throw std::runtime_error("Generation interrupted");
            auto token=llama_sampler_sample(sampler.get(),context,-1);
            if(llama_vocab_is_eog(vocab,token)) break;
            std::vector<char> piece(256);
            int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
            if(n<0) { piece.resize(-n); n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false); }
            if(n<0) throw std::runtime_error("Text decoding failed");
            output.append(piece.data(),n);
            auto batch=llama_batch_get_one(&token,1);
            if(llama_decode(context,batch)!=0) throw std::runtime_error("Generation failed");
        }
        auto result=env->NewByteArray(output.size());
        env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));
        return result;
    } catch(const std::exception& e) { fail(env,e.what()); return nullptr; }
      catch(...) { fail(env,"Native inference failed"); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_cancel(JNIEnv*, jobject) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_close(JNIEnv*, jobject) { std::lock_guard<std::mutex> guard(engine_mutex); clear(); }
