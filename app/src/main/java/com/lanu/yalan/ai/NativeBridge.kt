package com.lanu.yalan.ai

import androidx.annotation.Keep

@Keep
class NativeBridge {
    companion object { init { System.loadLibrary("lanu-ai") } }
    external fun prepare(timeoutSeconds: Int)
    external fun load(path: String, threads: Int): Boolean
    external fun generate(prompt: ByteArray, grammar: String, maxTokens: Int): ByteArray
    external fun cancel()
    external fun close()
}
