package com.lanu.yalan.ai

import com.lanu.yalan.data.OnlineAiCredentials
import com.lanu.yalan.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeAiService @Inject constructor(private val credentials: OnlineAiCredentials,private val client: FreeAiClient) {
    private val exchange=FreeAiExchange(client)
    fun cancel()=client.cancel()
    suspend fun connect(key: String,consent: Boolean) {
        require(consent && FreeAiProtocol.validKey(key))
        try {exchange.verify(key)}
        catch(e: TimeoutCancellationException) {throw AiException(AiFailure.ONLINE_TIMEOUT,e)}
        catch(e: IOException) {throw AiException(AiFailure.ONLINE_NETWORK,e)}
        try {credentials.write(key)}
        catch(e: CancellationException) {throw e}
        catch(e: Exception) {throw AiException(AiFailure.ONLINE_STORAGE,e)}
    }
    suspend fun ask(s: Session,answer: String?,spicy: Boolean): Verdict {
        val key=try {credentials.read()} catch(e: CancellationException) {throw e} catch(e: Exception) {throw AiException(AiFailure.ONLINE_KEY,e)}
            ?: throw AiException(AiFailure.ONLINE_KEY)
        try {
            val v=exchange.ask(key,s,answer,spicy)
            return if(answer==null) v else v.copy(evidence=ReasonedScoring.evidence(s,StoryEvidence.inspect(s,answer)))
        } catch(e: TimeoutCancellationException) {throw AiException(AiFailure.ONLINE_TIMEOUT,e)}
    }
}
