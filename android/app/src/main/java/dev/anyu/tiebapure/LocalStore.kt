package dev.anyu.tiebapure

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class LocalStore(private val context: Context, namespace: String = "") : SQLiteOpenHelper(context, namespace + "reader.db", null, 1) {
    init { require(namespace.matches(Regex("[A-Za-z0-9_-]*"))) }
    val prefs = context.getSharedPreferences(namespace + "reader", Context.MODE_PRIVATE)
    private val credentials = context.getSharedPreferences(namespace + "credentials", Context.MODE_PRIVATE)
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE items(kind TEXT NOT NULL, id TEXT NOT NULL, data TEXT NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(kind,id))") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("不支持的数据库迁移 $oldVersion → $newVersion") }

    @Synchronized fun put(kind: String, id: String, json: JSONObject) {
        if (kind == "saved" && json.has("posts")) { putSaved(id, json); return }
        require(kind in kinds && id.isNotBlank() && id.length <= 512 && json.toString().toByteArray().size <= 1024 * 1024) { "本地记录无效或超过 1 MB" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val value = ContentValues().apply { put("kind", kind); put("id", id); put("data", json.toString()); put("updated", System.currentTimeMillis()) }
            check(db.insertWithOnConflict("items", null, value, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "本地保存失败" }
            if (kind in setOf("history", "position")) db.execSQL("DELETE FROM items WHERE kind=? AND id NOT IN (SELECT id FROM items WHERE kind=? ORDER BY updated DESC,rowid DESC LIMIT 500)", arrayOf(kind, kind))
            check(android.database.DatabaseUtils.longForQuery(db, "SELECT COALESCE(SUM(length(CAST(data AS BLOB))),0) FROM items", null) <= 128L * 1024 * 1024) { "本地正文达到 128 MB 上限，请先删除部分保存" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun list(kind: String): List<Pair<String, JSONObject>> = readableDatabase.query("items", arrayOf("id", "data"), "kind=?", arrayOf(kind), null, null, "updated DESC,rowid DESC").use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0) to JSONObject(c.getString(1))) }
    }
    @Synchronized private fun header(kind: String, id: String): JSONObject? = readableDatabase.query("items", arrayOf("data"), "kind=? AND id=?", arrayOf(kind, id), null, null, null, "1").use { if (it.moveToFirst()) JSONObject(it.getString(0)) else null }
    @Synchronized fun get(kind: String, id: String): JSONObject? {
        val data = header(kind, id) ?: return null
        if (kind != "saved" || !data.has("snapshot-file")) return data
        val file = snapshotFile(data)
        val bytes = file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash == data.getString("snapshot-sha256")) { "本地正文校验失败，请重新保存此帖" }
        return JSONObject(bytes.toString(Charsets.UTF_8)).also { check(it.getString("id") == id) }
    }
    private fun snapshotFile(data: JSONObject): File {
        val relative = data.getString("snapshot-file")
        require(relative.matches(Regex("[a-f0-9-]{36}/snapshot\\.json"))) { "本地正文路径无效" }
        val root = File(context.filesDir, "offline").canonicalFile
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.path + "/") && file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.length() in 1..8L * 1024 * 1024) { "本地正文缺失或过大" }
        return file
    }
    // Large snapshots stay in files so Android's CursorWindow never has to hold a whole thread.
    @Synchronized fun putSaved(id: String, snapshot: JSONObject, ownedDirectory: File? = null): File {
        require(snapshot.getString("id") == id && id.toLongOrNull()?.let { it > 0 } == true)
        require(header("saved", id) != null || list("saved").size < 100) { "本地保存最多 100 个帖子" }
        val bytes = snapshot.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8 * 1024 * 1024) { "离线正文超过 8 MB" }
        val existingBytes = list("saved").filterNot { it.first == id }.sumOf { it.second.optLong("snapshot-bytes", it.second.toString().toByteArray().size.toLong()) }
        require(existingBytes + bytes.size <= 128L * 1024 * 1024) { "离线正文总量达到 128 MB，请先删除部分保存" }
        val root = File(context.filesDir, "offline").apply { mkdirs() }.canonicalFile
        val directory = ownedDirectory?.canonicalFile ?: File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        require(directory.parentFile == root && directory.name.matches(Regex("[a-f0-9-]{36}")))
        try {
            val file = File(directory, "snapshot.json")
            check(!file.exists()) { "已有离线正文不会被覆盖" }
            file.outputStream().use { it.write(bytes); it.fd.sync() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val metadata = threadFromJson(snapshot).json().put("media-directory", directory.name).put("snapshot-file", directory.name + "/snapshot.json").put("snapshot-bytes", bytes.size).put("snapshot-sha256", hash)
            put("saved", id, metadata)
            return directory
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    @Synchronized fun updateForumMetadata(name: String, forum: ForumEntry) {
        val saved = header("forum", name) ?: return
        val avatar = Policy.avatar(forum.avatar) ?: return
        saved.put("avatar", avatar)
        if (forum.id.toLongOrNull()?.let { it > 0 } == true) saved.put("id", forum.id)
        require(saved.toString().toByteArray().size <= 1024 * 1024)
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(db.update("items", ContentValues().apply { put("data", saved.toString()) }, "kind=? AND id=?", arrayOf("forum", name)) == 1)
            check(android.database.DatabaseUtils.longForQuery(db, "SELECT COALESCE(SUM(length(CAST(data AS BLOB))),0) FROM items", null) <= 128L * 1024 * 1024) { "本地正文达到128MB上限" }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun remove(kind: String, id: String) { writableDatabase.delete("items", "kind=? AND id=?", arrayOf(kind, id)) }
    @Synchronized fun clear(kind: String) { writableDatabase.delete("items", "kind=?", arrayOf(kind)) }
    fun blocked(thread: Thread): Boolean = list("block").any { (_, j) ->
        val value = j.optString("value")
        when (j.optString("type")) {
            "forum" -> thread.forum.equals(value, true)
            "user" -> thread.author.id == value || thread.author.name == value
            else -> thread.title.contains(value, true) || thread.summary.contains(value, true)
        }
    }
    fun backup(): ByteArray {
        val placeholders = backupKinds.joinToString(",") { "?" }
        val size = android.database.DatabaseUtils.longForQuery(readableDatabase, "SELECT COALESCE(SUM(length(CAST(data AS BLOB))),0) FROM items WHERE kind IN ($placeholders)", backupKinds.toTypedArray())
        val fileBytes = list("saved").sumOf { (_, data) -> if (data.has("snapshot-file")) snapshotFile(data).length() else 0L }
        require(size + fileBytes <= 8 * 1024 * 1024 - 128 * 1024) { "本地备份超过 8 MB，请先减少保存内容" }
        val rows = JSONArray()
        for (kind in backupKinds) for ((id, data) in list(kind)) rows.put(JSONObject().put("kind", kind).put("id", id).put("data", if (kind == "saved") get(kind, id) else data))
        return JSONObject().put("format", "tiebapure-android").put("version", 1).put("rows", rows).toString().toByteArray(Charsets.UTF_8).also { require(it.size <= 8 * 1024 * 1024) { "备份超过 8 MB" } }
    }
    @Synchronized fun restore(bytes: ByteArray) {
        require(bytes.size <= 8 * 1024 * 1024) { "备份超过 8 MB" }
        val j = JSONObject(bytes.toString(Charsets.UTF_8))
        require(j.optString("format") == "tiebapure-android" && j.optInt("version") == 1) { "备份格式不受支持" }
        val rawRows = j.getJSONArray("rows")
        val rows = rawRows.objects()
        require(rows.size == rawRows.length()) { "备份记录格式无效" }
        require(rows.size <= 3000 && rows.all { it.optString("kind") in backupKinds && it.optString("id").length in 1..512 && it.optJSONObject("data") != null }) { "备份内容不合法" }
        require(rows.map { it.getString("kind") to it.getString("id") }.distinct().size == rows.size) { "备份包含重复记录" }
        for (row in rows) {
            val data = row.getJSONObject("data")
            when (row.getString("kind")) {
                "history", "saved" -> {
                    val thread = threadFromJson(data)
                    require(thread.id == row.getString("id") && thread.id.toLongOrNull()?.let { it > 0 } == true)
                    if (row.getString("kind") == "saved") data.getJSONArray("posts").objects().forEach(::postFromJson)
                }
                "position" -> require(data.optInt("page") in 1..10000 && data.optInt("index") in 0..1000000 && data.optInt("offset") in 0..1000000)
                "block" -> require(data.optString("type") in setOf("keyword", "user", "forum") && data.optString("value").trim().length in 1..512)
                "forum" -> require(data.optString("name").trim().length in 1..100)
            }
        }
        val db = writableDatabase
        val created = mutableListOf<File>()
        db.beginTransaction()
        try {
            rows.asReversed().forEach { row ->
                val kind = row.getString("kind"); val id = row.getString("id")
                if (header(kind, id) == null) {
                    if (kind == "saved") created.add(putSaved(id, row.getJSONObject("data"))) else put(kind, id, row.getJSONObject("data"))
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) { created.forEach { it.deleteRecursively() }; throw e }
        finally { db.endTransaction() }
    }
    private fun secretKey(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey("tiebapure.account", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tiebapure.account", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun saveAccount(account: Account?) {
        if (account == null) { check(credentials.edit().clear().commit()); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val data = cipher.doFinal(account.json().toString().toByteArray(Charsets.UTF_8))
        check(credentials.edit().putString("session", Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)).commit()) { "账号保存失败" }
    }
    @Synchronized fun loadAccount(): Account? {
        val text = credentials.getString("session", null) ?: return null
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        require(bytes.size in 29..20000) { "账号数据损坏，请重新登录" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return Account.parse(JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)))
    }
    companion object {
        val backupKinds = setOf("history", "position", "saved", "block", "forum")
        val kinds = backupKinds + setOf("draft", "automation")
    }
}
