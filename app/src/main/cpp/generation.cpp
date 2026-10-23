#include "generation.h"
#include <algorithm>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <vector>

namespace lanu {
GenerationEngine::~GenerationEngine() { clear(); }
void GenerationEngine::prepare(int timeout_seconds) {
    cancelled_.store(false);
    deadline_ = std::chrono::steady_clock::now() + std::chrono::seconds(std::max(1, timeout_seconds));
}
void GenerationEngine::cancel() { cancelled_.store(true); }
bool GenerationEngine::stopped() const { return cancelled_.load() || std::chrono::steady_clock::now() > deadline_; }
bool GenerationEngine::abort_callback(void* data) { return static_cast<GenerationEngine*>(data)->stopped(); }
void GenerationEngine::require_running() const {
    if(cancelled_.load()) throw std::runtime_error("LANU_AI_CANCELLED");
    if(std::chrono::steady_clock::now() > deadline_) throw std::runtime_error("LANU_AI_TIMEOUT");
}
void GenerationEngine::clear() {
    if(context_) llama_free(context_);
    if(model_) llama_model_free(model_);
    context_ = nullptr; model_ = nullptr;
}
void GenerationEngine::set_threads(int threads) {
    if(!context_) throw std::runtime_error("LANU_AI_LOAD");
    const int workers = std::clamp(threads, 1, 4);
    llama_set_n_threads(context_, workers, workers);
}
void GenerationEngine::load(const std::string& path, int threads) {
    clear();
    try {
        require_running();
        static std::once_flag backend;
        std::call_once(backend, [] {
            llama_log_set([](ggml_log_level, const char*, void*) {}, nullptr);
            llama_backend_init();
        });
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.progress_callback = [](float, void* data) { return !abort_callback(data); };
        params.progress_callback_user_data = this;
        model_ = llama_model_load_from_file(path.c_str(), params);
        require_running();
        if(!model_) throw std::runtime_error("LANU_AI_LOAD");
        auto cp = llama_context_default_params();
        cp.n_ctx = context_tokens; cp.n_batch = batch_tokens; cp.n_ubatch = 128;
        cp.n_outputs_max = 1; cp.n_outputs_max_per_seq = 1;
        cp.abort_callback = abort_callback; cp.abort_callback_data = this;
        cp.n_threads = cp.n_threads_batch = std::clamp(threads, 1, 4);
        context_ = llama_init_from_model(model_, cp);
        require_running();
        if(!context_) throw std::runtime_error("LANU_AI_MEMORY");
    } catch(...) { clear(); throw; }
}
GenerationEngine::Result GenerationEngine::generate(const std::string& prompt, const std::string& grammar, int max_tokens) {
    if(!context_ || !model_) throw std::runtime_error("LANU_AI_LOAD");
    require_running();
    if(max_tokens <= 0 || max_tokens >= context_tokens) throw std::runtime_error("LANU_AI_CONTEXT");
    llama_set_abort_callback(context_, abort_callback, this);
    llama_memory_clear(llama_get_memory(context_), true);
    const auto* vocab = llama_model_get_vocab(model_);
    int count = -llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), nullptr, 0, true, true);
    if(count <= 0 || count + max_tokens >= static_cast<int>(llama_n_ctx(context_))) throw std::runtime_error("LANU_AI_CONTEXT");
    std::vector<llama_token> tokens(count);
    count = llama_tokenize(vocab, prompt.data(), static_cast<int>(prompt.size()), tokens.data(), count, true, true);
    if(count <= 0) throw std::runtime_error("LANU_AI_ENGINE");
    for(int i = 0; i < count; i += batch_tokens) {
        require_running();
        auto batch = llama_batch_get_one(tokens.data() + i, std::min(batch_tokens, count - i));
        const int rc = llama_decode(context_, batch);
        require_running();
        if(rc != 0) throw std::runtime_error("LANU_AI_ENGINE");
    }
    auto deleter = [](llama_sampler* sampler) { llama_sampler_free(sampler); };
    std::unique_ptr<llama_sampler, decltype(deleter)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), deleter);
    std::unique_ptr<llama_sampler, decltype(deleter)> grammar_sampler(llama_sampler_init_grammar(vocab, grammar.c_str(), "root"), deleter);
    if(!grammar_sampler) throw std::runtime_error("LANU_AI_OUTPUT");
    // Repeating a numeric score or a story detail is valid in short JSON.
    // No prompt/generated-token repetition penalty is applied.
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.5f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(sampling_seed));
    Result result{"", count, 0, 0};
    std::vector<llama_token_data> candidates(llama_vocab_n_tokens(vocab));
    int depth = 0;
    bool quoted = false, escaped = false, complete = false;
    for(int i = 0; i < max_tokens; ++i) {
        require_running();
        // Pinned llama.cpp common/sampling.cpp rejection strategy: check one token,
        // then apply a full grammar mask only when the first candidate is invalid.
        // apply() does NOT accept. Commit each valid token exactly once below.
        const auto* logits = llama_get_logits_ith(context_, -1);
        if(!logits) throw std::runtime_error("LANU_AI_ENGINE");
        auto reset_candidates = [&] {
            for(size_t id = 0; id < candidates.size(); ++id) candidates[id] = {static_cast<llama_token>(id), logits[id], 0.0f};
            return llama_token_data_array{candidates.data(), candidates.size(), -1, false};
        };
        auto probabilities = reset_candidates();
        llama_sampler_apply(sampler.get(), &probabilities);
        if(probabilities.selected < 0) throw std::runtime_error("LANU_AI_OUTPUT");
        auto token = probabilities.data[probabilities.selected].id;
        llama_token_data selected{token, 1.0f, 0.0f};
        llama_token_data_array selected_array{&selected, 1, -1, false};
        llama_sampler_apply(grammar_sampler.get(), &selected_array);
        if(selected_array.data[0].logit == -INFINITY) {
            ++result.grammar_fallbacks;
            probabilities = reset_candidates();
            llama_sampler_apply(grammar_sampler.get(), &probabilities);
            llama_sampler_apply(sampler.get(), &probabilities);
            if(probabilities.selected < 0) throw std::runtime_error("LANU_AI_OUTPUT");
            token = probabilities.data[probabilities.selected].id;
            llama_token_data fallback{token, 1.0f, 0.0f};
            llama_token_data_array fallback_array{&fallback, 1, -1, false};
            llama_sampler_apply(grammar_sampler.get(), &fallback_array);
            if(fallback_array.data[0].logit == -INFINITY) throw std::runtime_error("LANU_AI_OUTPUT");
        }
        llama_sampler_accept(grammar_sampler.get(), token);
        llama_sampler_accept(sampler.get(), token);
        if(llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
        if(n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false); }
        if(n < 0) throw std::runtime_error("LANU_AI_ENGINE");
        result.text.append(piece.data(), n);
        ++result.generated_tokens;
        for(int j = 0; j < n; ++j) {
            const char c = piece[j];
            if(quoted) {
                if(escaped) escaped = false;
                else if(c == '\\') escaped = true;
                else if(c == '"') quoted = false;
            } else if(c == '"') quoted = true;
            else if(c == '{') ++depth;
            else if(c == '}' && --depth == 0) complete = true;
        }
        if(complete) break;
        auto batch = llama_batch_get_one(&token, 1);
        const int rc = llama_decode(context_, batch);
        require_running();
        if(rc != 0) throw std::runtime_error("LANU_AI_ENGINE");
    }
    if(!complete) throw std::runtime_error("LANU_AI_OUTPUT");
    return result;
}
}
