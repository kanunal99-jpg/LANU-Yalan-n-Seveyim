package com.lanu.yalan.ai

import org.junit.Assert.*
import org.junit.Test

class EngineGrammarTest {
    @Test fun pointDemandsCannotEarnDetailOrDefensePoints() {
        val grammar=GameEngine.grammarFor(opening=false,final=false,scoreInstruction=true)
        assertTrue(grammar.contains("guarded-score ::= \"0\""))
        assertTrue(grammar.lines().any { it.trim()=="score ::= \"0\"" })
        assertFalse(GameEngine.grammarFor(false,false).contains("guarded-score ::= \"0\""))
    }
    @Test fun openingCannotAwardPoints() {
        val grammar=GameEngine.grammarFor(opening=true,final=false)
        assertTrue(grammar.lines().any {it.trim()=="score ::= \"0\""})
        assertTrue(grammar.contains("question-value ::= string"))
    }
    @Test fun finalCannotGenerateSixthQuestion() {
        val grammar=GameEngine.grammarFor(opening=false,final=true)
        assertFalse(grammar.contains("question-value ::= string"))
        assertTrue(grammar.contains("question-value ::= \"\\\"\\\"\""))
        assertTrue(grammar.contains("score ::= \"0\" | [1-9]"))
    }
}
