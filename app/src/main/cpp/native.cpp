#include <jni.h>
#include "generation.h"
#include <algorithm>
#include <mutex>
#include <new>
#include <stdexcept>

namespace {
std::mutex engine_mutex;
lanu::GenerationEngine engine;
void fail(JNIEnv* env, const char* text) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), text); }
std::string read_string(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if(!chars) throw std::bad_alloc();
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_prepare(JNIEnv*, jobject, jint timeout_seconds) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    engine.prepare(std::clamp(static_cast<int>(timeout_seconds), 1, 30));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lanu_yalan_ai_NativeBridge_load(JNIEnv* env, jobject, jstring path, jint threads) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try { engine.load(read_string(env, path), threads); return JNI_TRUE; }
    catch(const std::bad_alloc&) { fail(env, "LANU_AI_MEMORY"); }
    catch(const std::exception& e) { fail(env, e.what()); }
    catch(...) { fail(env, "LANU_AI_LOAD"); }
    return JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_setThreads(JNIEnv* env, jobject, jint threads) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try { engine.set_threads(threads); }
    catch(const std::exception& e) { fail(env, e.what()); }
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lanu_yalan_ai_NativeBridge_generate(JNIEnv* env, jobject, jbyteArray input, jstring grammar, jint max_tokens) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    try {
        const auto length = env->GetArrayLength(input);
        std::string prompt(length, '\0');
        env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(prompt.data()));
        const auto result = engine.generate(prompt, read_string(env, grammar), max_tokens);
        auto output = env->NewByteArray(static_cast<jsize>(result.text.size()));
        if(!output) throw std::bad_alloc();
        env->SetByteArrayRegion(output, 0, static_cast<jsize>(result.text.size()), reinterpret_cast<const jbyte*>(result.text.data()));
        return output;
    } catch(const std::bad_alloc&) { fail(env, "LANU_AI_MEMORY"); }
    catch(const std::exception& e) { fail(env, e.what()); }
    catch(...) { fail(env, "LANU_AI_ENGINE"); }
    return nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_cancel(JNIEnv*, jobject) { engine.cancel(); }
extern "C" JNIEXPORT void JNICALL
Java_com_lanu_yalan_ai_NativeBridge_close(JNIEnv*, jobject) { std::lock_guard<std::mutex> guard(engine_mutex); engine.clear(); }
