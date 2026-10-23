package com.lanu.yalan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanu.yalan.data.BackupException
import com.lanu.yalan.data.BackupFailure
import com.lanu.yalan.data.SaveStore
import com.lanu.yalan.domain.GameSave
import com.lanu.yalan.domain.PlayMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class BackupCompatibilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun portableBackupRestoresAfterTheDeviceKeyIsGone() = runBlocking {
        val store=SaveStore(context)
        val password="Ayrı cihazda geçerli parola 42".toCharArray()
        val state=GameSave(unlocked=7,best=mapOf(1 to 83,6 to 78),mode=PlayMode.FAST)
        try {
            store.reset()
            store.write(state)
            val backup=store.exportBackup(password)
            store.reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null);deleteEntry("lanu.progress") }
            assertEquals(1,SaveStore(context).read().unlocked)
            assertEquals(state,SaveStore(context).restoreBackup(backup,password))
            assertEquals(state,SaveStore(context).read())
        } finally { password.fill('\u0000');store.reset() }
    }
    @Test fun failedImportKeepsCurrentProgressAndValidBackupRecoversCorruptStorage() = runBlocking {
        val store=SaveStore(context)
        val password="Sağlam bir yedek parolası".toCharArray()
        val original=GameSave(unlocked=6,best=mapOf(1 to 90),mode=PlayMode.FAST)
        try {
            store.reset()
            store.write(original)
            val backup=store.exportBackup(password)
            val error=runCatching { store.restoreBackup(backup,"Yanlış ama uzun parola".toCharArray()) }.exceptionOrNull()
            assertTrue(error is BackupException)
            assertEquals(BackupFailure.AUTHENTICATION,(error as BackupException).reason)
            assertEquals(original,store.read())
            File(context.filesDir,"progress.enc").writeBytes(byteArrayOf(1,2,3))
            assertTrue(runCatching { store.read() }.isFailure)
            assertEquals(original,store.restoreBackup(backup,password))
            assertEquals(original,SaveStore(context).read())
        } finally { password.fill('\u0000');store.reset() }
    }
}
