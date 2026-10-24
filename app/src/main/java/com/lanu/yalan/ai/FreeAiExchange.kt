package com.lanu.yalan.ai

import com.lanu.yalan.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.IOException

/** Two bounded free attempts, explicit offline fallback handled by the player, no scorer substitution. */
class FreeAiExchange(private val transport: FreeAiTransport,private val timeoutMs: Long=FreeAiProtocol.TOTAL_TIMEOUT_MS) {
    init {require(timeoutMs in 1..FreeAiProtocol.TOTAL_TIMEOUT_MS)}
    suspend fun verify(key: String) = withTimeout(timeoutMs) {
        require(FreeAiProtocol.validKey(key))
        val response=transport.send(key,null)
        failForStatus(response.status)
        try {require(FreeAiProtocol.responseObject(response.body)["data"] is JsonObject)}
        catch(e: Exception) {throw AiException(AiFailure.ONLINE_OUTPUT,e)}
    }
    suspend fun ask(key: String,s: Session,answer: String?,spicy: Boolean): Verdict = withTimeout(timeoutMs) {
        var last: Exception?=null
        for(attempt in 0..1) {
            currentCoroutineContext().ensureActive()
            try {
                val response=transport.send(key,FreeAiProtocol.request(s,answer,spicy,simpleJson=attempt==1))
                if(response.status in listOf(400,422,408,500,502,503,504) && attempt==0) continue
                failForStatus(response.status)
                return@withTimeout try {FreeAiProtocol.parse(response.body,s,answer)}
                catch(e: AiException) {throw e}
                catch(e: Exception) {throw AiException(AiFailure.ONLINE_OUTPUT,e)}
            } catch(e: CancellationException) {throw e}
            catch(e: IOException) {last=e}
            catch(e: AiException) {if(e.reason!=AiFailure.ONLINE_OUTPUT) throw e;last=e}
        }
        throw (last as? AiException ?: AiException(AiFailure.ONLINE_NETWORK,last))
    }
    private fun failForStatus(status: Int) {
        when(status) {
            in 200..299 -> Unit
            401,403 -> throw AiException(AiFailure.ONLINE_AUTH)
            402,429 -> throw AiException(AiFailure.ONLINE_QUOTA)
            else -> throw AiException(AiFailure.ONLINE_NETWORK)
        }
    }
}
