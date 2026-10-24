package com.lanu.yalan.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FreeAiClient @Inject constructor(): FreeAiTransport {
    private val http=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
        .callTimeout(12,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val active=AtomicReference<Call?>()
    override fun cancel() { active.getAndSet(null)?.cancel() }
    override suspend fun send(key: String,body: String?): FreeAiResponse=suspendCancellableCoroutine { continuation ->
        require(FreeAiProtocol.validKey(key))
        val request=Request.Builder().url(FreeAiProtocol.ORIGIN+if(body==null) "/api/v1/key" else "/api/v1/chat/completions")
            .header("Authorization","Bearer $key").header("X-Title","LANU Yalanini Seveyim")
            .apply { if(body!=null) post(body.toRequestBody("application/json".toMediaType())) }.build()
        val call=http.newCall(request)
        check(active.compareAndSet(null,call)) { "Concurrent free AI request" }
        continuation.invokeOnCancellation { active.compareAndSet(call,null);call.cancel() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call,e: IOException) {
                active.compareAndSet(call,null)
                if(continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call,response: Response) {
                try {
                    val result=response.use {
                        val stream=it.body?.byteStream() ?: throw IOException("Missing response")
                        val bytes=stream.readBytesBounded(FreeAiProtocol.MAX_RESPONSE_BYTES)
                        FreeAiResponse(it.code,bytes.decodeToString(throwOnInvalidSequence=true))
                    }
                    active.compareAndSet(call,null)
                    if(continuation.isActive) continuation.resume(result)
                } catch(e: Exception) {
                    active.compareAndSet(call,null)
                    if(continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val result=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        while(true) {
            val n=read(buffer);if(n<0) break
            if(result.size()+n>limit) throw IOException("Oversized AI response")
            result.write(buffer,0,n)
        }
        return result.toByteArray()
    }
}
