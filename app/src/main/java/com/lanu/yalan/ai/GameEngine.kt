package com.lanu.yalan.ai

import com.lanu.yalan.domain.*
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
class GameEngine @Inject constructor(private val models: ModelStore, @ApplicationContext private val context: Context) {
    private val lock = Mutex()
    private var native: NativeBridge? = null
    private var loaded = false
    fun cancel() { native?.cancel() }
    suspend fun release() = withContext(Dispatchers.Default) { lock.withLock { native?.close(); loaded = false } }
    suspend fun ask(session: Session, answer: String?, spicy: Boolean): Verdict = withContext(Dispatchers.Default) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            check(models.ready()) { "Önce oyun beynini indir." }
            val bridge = native ?: NativeBridge().also { native = it }
            if (!loaded) { check(bridge.load(models.model.absolutePath)) { "Model açılamadı. Diğer uygulamaları kapatıp yeniden dene." }; loaded = true }
            currentCoroutineContext().ensureActive()
            val raw = bridge.generate(prompt(session, answer, spicy).encodeToByteArray(), grammarFor(answer == null, session.turns.size == 4 && answer != null), 420).decodeToString()
            currentCoroutineContext().ensureActive()
            VerdictParser.parse(raw, session.turns.size == 4 && answer != null)
        }
    }
    internal fun prompt(s: Session, answer: String?, spicy: Boolean): String {
        fun safe(s: String) = s.replace("<|", "‹").replace("|>", "›").replace("<think>", "").replace("</think>", "")
        val system = context.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
            .replace("{{TONE}}", if(spicy) "Mild Turkish slang is allowed, such as 'yok artık be'. No heavy profanity." else "No profanity or slang. Friendly playful Turkish only.")
            .replace("{{LEVEL}}", s.level.toString())
            .replace("{{CHALLENGE}}", Rules.challenge(s.level))
            .replace("{{ROUND}}", if(answer == null) "OPENING: No answers yet. Ask one specific question about the player's topic. All four scores MUST be 0."
                else if(s.turns.size == 4) "FINAL ROUND: question MUST be empty. Give final scores for the complete story and a witty conclusion."
                else "NEXT ROUND: evaluate the latest answer in context and ask one follow-up.")
        return buildString {
            append("<|im_start|>system\n$system<|im_end|>\n")
            append("<|im_start|>user\nOyunun konusu: ${safe(s.topic)}<|im_end|>\n")
            s.turns.forEach { turn ->
                append("<|im_start|>assistant\n${safe(turn.question)}<|im_end|>\n")
                append("<|im_start|>user\n${safe(turn.answer)}<|im_end|>\n")
            }
            if(answer != null) {
                append("<|im_start|>assistant\n${safe(s.question)}<|im_end|>\n")
                append("<|im_start|>user\n${safe(answer)}<|im_end|>\n")
            }
            append("<|im_start|>assistant\n<think>\n\n</think>\n\n")
        }
    }
    companion object {
        fun grammarFor(opening: Boolean, final: Boolean): String = GRAMMAR
            .replace("question-value ::= string", if(final) "question-value ::= \"\\\"\\\"\"" else "question-value ::= string")
            .replace("score ::= \"0\" | [1-9] | \"1\" [0-9] | \"2\" [0-5]", if(opening) "score ::= \"0\"" else "score ::= \"0\" | [1-9] | \"1\" [0-9] | \"2\" [0-5]")
        val GRAMMAR = """
            root ::= "{" ws "\"reply\"" ws ":" ws string "," ws "\"question\"" ws ":" ws question-value "," ws "\"consistency\"" ws ":" ws score "," ws "\"plausibility\"" ws ":" ws score "," ws "\"detail\"" ws ":" ws score "," ws "\"defense\"" ws ":" ws score ws "}"
            question-value ::= string
            score ::= "0" | [1-9] | "1" [0-9] | "2" [0-5]
            string ::= "\"" char* "\""
            char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F])
            ws ::= [ \t\n\r]*
        """.trimIndent()
    }
}
