#include "generation.h"
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>

static std::string json_string(const std::string& value) {
    const char* hex = "0123456789abcdef";
    std::string escaped = "\"";
    for(unsigned char c : value) {
        if(c == '"' || c == '\\') { escaped += '\\'; escaped += c; }
        else if(c < 0x20) { escaped += "\\u00"; escaped += hex[c >> 4]; escaped += hex[c & 15]; }
        else escaped += c;
    }
    return escaped + "\"";
}
static std::string read(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    if(!input) throw std::runtime_error("Could not read probe input: " + path);
    return std::string(std::istreambuf_iterator<char>(input), {});
}
int main(int argc, char** argv) {
    if(argc != 6) {
        std::cerr << "Usage: lanu-ai-probe MODEL MANIFEST THREADS TIMEOUT_SECONDS MAX_TOKENS\n";
        return 2;
    }
    try {
        const int threads = std::stoi(argv[3]), timeout = std::stoi(argv[4]), max_tokens = std::stoi(argv[5]);
        lanu::GenerationEngine engine;
        const auto load_started = std::chrono::steady_clock::now();
        engine.prepare(timeout); engine.load(argv[1], threads);
        std::cout << "{\"model_load_seconds\":" << std::chrono::duration<double>(std::chrono::steady_clock::now() - load_started).count() << "}" << std::endl;
        std::ifstream manifest(argv[2]);
        if(!manifest) throw std::runtime_error("Could not read manifest");
        std::string line;
        while(std::getline(manifest, line)) {
            std::istringstream record(line);
            std::string name, prompt, grammar;
            if(!std::getline(record, name, '\t') || !std::getline(record, prompt, '\t') || !std::getline(record, grammar))
                throw std::runtime_error("Invalid probe manifest record");
            const auto started = std::chrono::steady_clock::now();
            engine.prepare(timeout); engine.set_threads(threads);
            try {
                const auto result = engine.generate(read(prompt), read(grammar), max_tokens);
                const auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
                std::cout << "{\"case\":" << json_string(name) << ",\"host_seconds\":" << elapsed
                    << ",\"prompt_tokens\":" << result.prompt_tokens << ",\"generated_tokens\":" << result.generated_tokens
                    << ",\"grammar_fallbacks\":" << result.grammar_fallbacks << ",\"raw\":" << json_string(result.text) << "}" << std::endl;
            } catch(const std::exception& e) {
                std::cout << "{\"case\":" << json_string(name) << ",\"error\":" << json_string(e.what()) << "}" << std::endl;
            }
        }
        return 0;
    } catch(const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
