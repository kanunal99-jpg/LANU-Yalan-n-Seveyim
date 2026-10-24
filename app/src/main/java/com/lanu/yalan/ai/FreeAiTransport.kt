package com.lanu.yalan.ai

data class FreeAiResponse(val status: Int,val body: String)
interface FreeAiTransport {
    suspend fun send(key: String,body: String?): FreeAiResponse
    fun cancel()
}
