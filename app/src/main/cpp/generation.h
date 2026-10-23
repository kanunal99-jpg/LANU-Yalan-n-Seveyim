#pragma once
#include <llama.h>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <string>

namespace lanu {
// Shared without modification by Android JNI and the desktop quality probe.
class GenerationEngine {
public:
    static constexpr int context_tokens = 3072;
    static constexpr int batch_tokens = 256;
    static constexpr uint32_t sampling_seed = 42;
    struct Result { std::string text; int prompt_tokens; int generated_tokens; int grammar_fallbacks; };
    ~GenerationEngine();
    void prepare(int timeout_seconds);
    void load(const std::string& path, int threads);
    void set_threads(int threads);
    Result generate(const std::string& prompt, const std::string& grammar, int max_tokens);
    void cancel();
    void clear();
private:
    llama_model* model_ = nullptr;
    llama_context* context_ = nullptr;
    std::atomic<bool> cancelled_{false};
    std::chrono::steady_clock::time_point deadline_{};
    static bool abort_callback(void* data);
    bool stopped() const;
    void require_running() const;
};
}
