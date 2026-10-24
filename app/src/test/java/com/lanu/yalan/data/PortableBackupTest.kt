package com.lanu.yalan.data

import com.lanu.yalan.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class PortableBackupTest {
    private val password="Uzun ve gizli şifrem 42".toCharArray()
    private fun save() = GameSave(unlocked=8,best=mapOf(1 to 82,7 to 70),mode=PlayMode.FAST,
        session=Session("portable-test",8,"İşe geç kaldım","Neden geciktin?",
            listOf(Turn("Neden geciktin?","Otobüsün lastiği patladı.",Verdict("Lastik dosyaya girdi.","Ne kadar bekledin?",17,18,16,15))),mode=PlayMode.FAST))
    @Test fun encryptedRoundTripPreservesProgressPreferencesAndActiveStory() {
        val original=save()
        val bytes=PortableBackup.encode(original,password)
        assertEquals(original,PortableBackup.decode(bytes,password))
        assertFalse(bytes.decodeToString().contains("Otobüsün lastiği"))
        assertFalse(bytes.decodeToString().contains("portable-test"))
        assertEquals("Uzun ve gizli şifrem 42",password.concatToString())
    }
    @Test fun sameSaveAndPasswordUseFreshSaltAndNonce() {
        val first=PortableBackup.encode(save(),password)
        val second=PortableBackup.encode(save(),password)
        assertFalse(first.contentEquals(second))
        assertEquals(save(),PortableBackup.decode(first,password))
        assertEquals(save(),PortableBackup.decode(second,password))
    }
    @Test fun fiveStoriesKeepEveryDraftAndModeInAPortableEncryptedBackup() {
        val base=save()
        val stories=(1..5).map { n -> base.session!!.copy(id="backup-story-$n",topic="Yedek konu $n",mode=PlayMode.entries[(n-1)%3],draftAnswer="Gönderilmemiş cevap $n") }
        val original=base.copy(session=stories.first(),parkedSessions=stories.drop(1))
        val bytes=PortableBackup.encode(original,password)
        assertEquals(original,PortableBackup.decode(bytes,password))
        assertFalse(bytes.decodeToString().contains("backup-story"))
    }
    @Test fun fiveLongValidTranscriptsExceedingTheOldSingleStoryLimitRemainReadable() {
        val q="ş".repeat(500)
        val v=Verdict("ğ".repeat(700),q,20,20,20,20,
            explanations=ScoreReasons("ö".repeat(400),"ö".repeat(400),"ö".repeat(400),"ö".repeat(400)),
            evidence=EvidenceCard(EvidenceStatus.NEEDS_EXPLANATION,"ı".repeat(280),"ü".repeat(280),"ş".repeat(400)))
        val stories=(1..5).map { n -> Session("large-$n",1,"Uzun konu $n",q,List(4) {Turn(q,"ç".repeat(280),v)},mode=PlayMode.FAST,
            draftAnswer="Taslak $n",recentJokeKeys=List(40) {"$it"+"ş".repeat(115)}) }
        val original=GameSave(session=stories.first(),parkedSessions=stories.drop(1))
        assertTrue(Json.encodeToString(original).encodeToByteArray().size>128*1024)
        val bytes=PortableBackup.encode(original,password)
        assertEquals(bytes.size,PortableBackup.readBounded(ByteArrayInputStream(bytes)).size)
        assertEquals(original,PortableBackup.decode(bytes,password))
    }
    @Test fun wrongPasswordAndModifiedCiphertextAreTheSameAuthenticationFailure() {
        val bytes=PortableBackup.encode(save(),password)
        val wrong=assertThrows(BackupException::class.java) { PortableBackup.decode(bytes,"Başka ve uzun parola".toCharArray()) }
        val modified=bytes.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        val tamper=assertThrows(BackupException::class.java) { PortableBackup.decode(modified,password) }
        assertEquals(BackupFailure.AUTHENTICATION,wrong.reason)
        assertEquals(wrong.reason,tamper.reason)
        assertEquals(wrong.message,tamper.message)
    }
    @Test fun headerSaltAndNonceAreAuthenticated() {
        val bytes=PortableBackup.encode(save(),password)
        // Header offsets: magic8, iterations4, salt16, nonce12.
        for(offset in listOf(12,28)) {
            val modified=bytes.copyOf().also { it[offset]=(it[offset].toInt() xor 1).toByte() }
            assertEquals(BackupFailure.AUTHENTICATION,
                assertThrows(BackupException::class.java) { PortableBackup.decode(modified,password) }.reason)
        }
    }
    @Test fun fileAndKdfBoundsRejectBeforePasswordWork() {
        assertEquals(BackupFailure.FORMAT,
            assertThrows(BackupException::class.java) { PortableBackup.decode(ByteArray(PortableBackup.MAX_FILE_BYTES+1),password) }.reason)
        assertEquals(BackupFailure.FORMAT,
            assertThrows(BackupException::class.java) { PortableBackup.decode(ByteArray(20),password) }.reason)
        val bytes=PortableBackup.encode(save(),password)
        ByteBuffer.wrap(bytes,8,4).putInt(Int.MAX_VALUE)
        assertEquals(BackupFailure.FORMAT,
            assertThrows(BackupException::class.java) { PortableBackup.decode(bytes,password) }.reason)
    }
    @Test fun boundedReaderAcceptsExactLimitAndRejectsExtraByte() {
        assertEquals(40,PortableBackup.readBounded(ByteArrayInputStream(ByteArray(40)),40).size)
        assertEquals(BackupFailure.FORMAT,
            assertThrows(BackupException::class.java) { PortableBackup.readBounded(ByteArrayInputStream(ByteArray(41)),40) }.reason)
    }
    @Test fun passwordBoundsAreEnforced() {
        for(length in listOf(0,11,129)) {
            assertEquals(BackupFailure.PASSWORD,
                assertThrows(BackupException::class.java) { PortableBackup.encode(save(),CharArray(length){'a'}) }.reason)
        }
    }
    @Test fun oldSchemaDefaultsRoundTripWithoutChangingModeOrProgress() {
        val old=Json.decodeFromString<GameSave>("""{"schema":1,"unlocked":4,"best":{"1":70},"session":{"id":"legacy","level":3,"topic":"Eski konu","opening":"Ne oldu?"}}""")
        val restored=PortableBackup.decode(PortableBackup.encode(old,password),password)
        assertEquals(old,restored)
        assertEquals(PlayMode.LOCAL_AI,restored.session!!.mode)
        assertEquals(4,restored.unlocked)
    }
    @Test fun invalidSaveCannotBeExportedOrSilentlyRepaired() {
        val base=save()
        val invalid=listOf(base.copy(unlocked=101),base.copy(best=mapOf(9 to 70)),base.copy(best=mapOf(1 to 101)),
            base.copy(session=base.session!!.copy(turns=base.session.turns+base.session.turns.first())),
            base.copy(session=base.session.copy(credited=true)))
        invalid.forEach { state ->
            assertEquals(BackupFailure.CONTENT,
                assertThrows(BackupException::class.java) { PortableBackup.encode(state,password) }.reason)
        }
    }
}
