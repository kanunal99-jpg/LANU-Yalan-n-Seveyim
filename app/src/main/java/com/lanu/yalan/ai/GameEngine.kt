package com.lanu.yalan.ai

import com.lanu.yalan.domain.*
import com.lanu.yalan.data.DeviceInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameEngine @Inject constructor(private val models: ModelStore, @ApplicationContext private val context: Context, private val deviceInfo: DeviceInfo) {
    private val lock = Mutex()
    private var native: NativeBridge? = null
    private var loaded = false
    fun cancel() { native?.cancel() }
    suspend fun release() = withContext(Dispatchers.Default) { lock.withLock { native?.close(); loaded = false } }
    suspend fun ask(session: Session, answer: String?, spicy: Boolean): Verdict = withContext(Dispatchers.Default) {
        // Starting a story never needs model loading or a generated JSON score.
        if(answer == null) return@withContext Verdict("Hadi hikâyeni kuralım.", FastGame.opening(session.topic,session.level), 0, 0, 0, 0)
        if(session.mode == PlayMode.FAST) return@withContext FastGame.evaluate(session, answer, spicy)
        lock.withLock {
            currentCoroutineContext().ensureActive()
            if(!models.ready()) throw AiException(AiFailure.MISSING_MODEL)
            val profile = deviceInfo.ensureInferenceReady(checkMemory = !loaded)
            val bridge = native ?: NativeBridge().also { native = it }
            try {
                // One budget covers BOTH loading and inference, rather than resetting after loading.
                bridge.prepare(AI_TIMEOUT_SECONDS)
                currentCoroutineContext().ensureActive()
                if (!loaded) {
                    if(!bridge.load(models.model.absolutePath, profile.inferenceThreads)) throw AiException(AiFailure.LOAD)
                    loaded = true
                }
                currentCoroutineContext().ensureActive()
                val final = session.turns.size == 4
                val raw = bridge.generate(prompt(session, answer, spicy).encodeToByteArray(), grammarFor(false, final,FastGame.isScoreInstruction(answer)), 240).decodeToString()
                currentCoroutineContext().ensureActive()
                try { VerdictParser.parse(raw, final).copy(
                    challengeKey=QuestionDirector.next(session,StoryEvidence.inspect(session,answer),spicy).key
                ) }
                catch(e: IllegalArgumentException) { throw AiException(AiFailure.OUTPUT, e) }
            } catch(e: IllegalStateException) {
                currentCoroutineContext().ensureActive()
                if(e is AiException) throw e
                throw AiException(when(e.message) {
                    "LANU_AI_TIMEOUT" -> AiFailure.TIMEOUT
                    "LANU_AI_MEMORY" -> AiFailure.MEMORY
                    "LANU_AI_CONTEXT" -> AiFailure.CONTEXT
                    "LANU_AI_OUTPUT" -> AiFailure.OUTPUT
                    "LANU_AI_LOAD" -> AiFailure.LOAD
                    else -> AiFailure.ENGINE
                }, e)
            }
        }
    }
    internal fun prompt(s: Session, answer: String?, spicy: Boolean): String {
        val template=context.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
        return PromptComposer.compose(template,s,answer,spicy)
    }
    companion object {
        const val AI_TIMEOUT_SECONDS = 30
        fun grammarFor(opening: Boolean, final: Boolean, scoreInstruction: Boolean = false): String = GRAMMAR
            .replace("guarded-score ::= score", if(scoreInstruction) "guarded-score ::= \"0\"" else "guarded-score ::= score")
            .replace("question-value ::= string", if(final) "question-value ::= \"\\\"\\\"\"" else "question-value ::= string")
            .replace("score ::= \"0\" | [1-9] | \"1\" [0-9] | \"2\" [0-5]", if(opening || scoreInstruction) "score ::= \"0\"" else "score ::= \"0\" | [1-9] | \"1\" [0-9] | \"2\" [0-5]")
        val GRAMMAR = """
            root ::= "{" ws "\"reply\"" ws ":" ws string "," ws "\"question\"" ws ":" ws question-value "," ws "\"consistency\"" ws ":" ws score "," ws "\"plausibility\"" ws ":" ws score "," ws "\"detail\"" ws ":" ws guarded-score "," ws "\"defense\"" ws ":" ws guarded-score ws "}"
            question-value ::= string
            guarded-score ::= score
            score ::= "0" | [1-9] | "1" [0-9] | "2" [0-5]
            string ::= "\"" char* "\""
            char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
            ws ::= [ \t\n\r]*
        """.trimIndent()
    }
}
