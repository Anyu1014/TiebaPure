package dev.anyu.tiebapure

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var namespace: String
    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        namespace = "test-${UUID.randomUUID()}-"
        store = LocalStore(context, namespace)
    }
    @After fun cleanUp() {
        store.list("saved").forEach { (_, j) ->
            val dir = j.optString("media-directory")
            if (dir.matches(Regex("[a-f0-9-]{36}"))) File(File(context.filesDir, "offline"), dir).deleteRecursively()
        }
        store.close()
        context.deleteDatabase(namespace + "reader.db")
        context.deleteSharedPreferences(namespace + "reader")
        context.deleteSharedPreferences(namespace + "credentials")
    }
    @Test fun encryptedAccountIsExcludedFromBackupAndLogoutClearsIt() {
        val account = Account("42", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        store.saveAccount(account)
        assertEquals(account, store.loadAccount())
        val raw = context.getSharedPreferences(namespace + "credentials", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains(account.bduss))
        store.put("draft", "42:draft", JSONObject().put("text", "private draft"))
        val backup = store.backup().toString(Charsets.UTF_8)
        assertFalse(backup.contains(account.bduss)); assertFalse(backup.contains("private draft"))
        store.saveAccount(null); assertNull(store.loadAccount())
    }
    @Test fun automationHistoryDeletionPersistsMinimalMarkersWithoutLeakingAcrossAccounts() {
        val repo = AndroidAutoReplyRepository(store) {}
        val job = AutoReplyJob("11", "private title", "rule", "rev", "7", "测试", listOf("收物"), "private reply", 30, 100, 130, AutoReplyStatus.QUEUED)
        repo.saveJob("99", job); repo.saveJob("99", job.copy(threadId = "12")); repo.saveJob("100", job)
        repo.deleteJobs("99", setOf("11"))
        assertEquals(listOf("12"), repo.jobs("99").map { it.threadId })
        assertEquals(1, repo.jobs("100").size)
        val fresh = AndroidAutoReplyRepository(store) {}
        assertTrue(fresh.wasDeleted("99", "11")); assertFalse(fresh.wasDeleted("100", "11"))
        val deletingFresh = AndroidAutoReplyRepository(store) {}
        deletingFresh.deleteJobs("99", setOf("12")); assertTrue(deletingFresh.wasDeleted("99", "11"))
        deletingFresh.saveJob("99", job.copy(status = AutoReplyStatus.SENT)); assertTrue(deletingFresh.jobs("99").isEmpty())
        val rows = store.list("automation").filter { it.first.startsWith("99:") }
        assertTrue(rows.all { it.second.keys().asSequence().toSet() == setOf("threadId") })
        val reload = AndroidAutoReplyRepository(store) {}
        assertTrue(reload.wasDeleted("99", "11")); assertTrue(reload.wasDeleted("99", "12"))
        assertFalse(store.backup().toString(Charsets.UTF_8).contains("private reply"))
    }
    @Test fun malformedImportLeavesExistingHistoryIntact() {
        store.put("history", "42", Thread("42", "kept").json())
        val bad = """{"format":"tiebapure-android","version":1,"rows":[{"kind":"history","id":"42","data":{"id":"42"}}]}"""
        assertTrue(runCatching { store.restore(bad.toByteArray()) }.isFailure)
        assertEquals("kept", store.get("history", "42")?.getString("title"))
        val good = store.backup(); store.clear("history"); store.restore(good)
        assertEquals("kept", store.get("history", "42")?.getString("title"))
    }
    @Test fun historyAndPositionEnforceUserVisibleLimit() {
        for (i in 1..503) store.put("history", "$i", Thread("$i", "thread $i").json())
        assertEquals(500, store.list("history").size)
        assertNotNull(store.get("history", "503"))
    }
    @Test fun forumAvatarUpdatesKeepRecentOrderAndDoNotRestoreDeletedRecords() {
        store.put("forum", "旅行", JSONObject().put("name", "旅行"))
        android.os.SystemClock.sleep(5)
        store.put("forum", "北京", JSONObject().put("name", "北京"))
        val before = store.list("forum").map { it.first }
        val info = ForumEntry("42", "旅行", "https://himg.bdimg.com/sys/portrait/item/fixture")
        store.updateForumMetadata("旅行", info)
        assertEquals(before, store.list("forum").map { it.first })
        assertEquals(info.avatar, store.get("forum", "旅行")!!.getString("avatar"))
        store.clear("forum")
        store.updateForumMetadata("旅行", info)
        assertTrue(store.list("forum").isEmpty())
    }
    @Test fun localMediaRejectsTraversalAndReaderCountsWholeEmoji() {
        assertNull(OfflineLibrary.safeFile(context, "../credentials.xml"))
        assertNull(OfflineLibrary.safeFile(context, "/data/local/tmp/x"))
        assertEquals(1, "👩‍👩‍👧‍👧".graphemes())
        assertEquals(2, "中文".graphemes())
    }
    @Test fun largeOfflineThreadUsesFilePayloadAndDetectsCorruption() {
        val body = "x".repeat(2300000)
        val snapshot = Thread("42", "large").json().put("posts", org.json.JSONArray().put(Post("7", 1, User("5", "fixture"), listOf(Block(0, body))).json()))
        store.put("saved", "42", snapshot)
        val header = store.list("saved").single().second
        assertTrue(header.toString().length < 2000)
        assertEquals(body, postFromJson(store.get("saved", "42")!!.getJSONArray("posts").getJSONObject(0)).blocks.first().text)
        val file = File(File(context.filesDir, "offline"), header.getString("snapshot-file"))
        file.writeText("{}")
        assertTrue(runCatching { store.get("saved", "42") }.isFailure)
    }
}
