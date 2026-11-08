package dev.anyu.tiebapure

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PreparedExport(val file: File, val mime: String, val name: String, val folder: String)

class OfflineLibrary(private val context: Context, private val api: TiebaApi, private val store: LocalStore) {
    // shortcut: 正文快照限 8 MB、离线媒体总量限 512 MB；更大离线库改为分页持久化。
    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()

    suspend fun capture(tid: String, account: Account?, mode: Int, progress: suspend (String) -> Unit): JSONObject {
        require(mode in 0..2)
        val first = api.thread(tid, 1, account)
        check(first.thread.id == tid && first.pages in 1..10000 && first.posts.any { it.floor == 1 }) { "帖子内容不完整，未覆盖已有保存" }
        val posts = first.posts.toMutableList()
        var textCharacters = first.posts.sumOf { it.blocks.sumOf { b -> b.text.length + b.url.length } }
        for (page in 2..first.pages) {
            currentCoroutineContext().ensureActive()
            progress("正在保存正文 $page/${first.pages}")
            val loaded = api.thread(tid, page, account)
            check(loaded.thread.id == tid && loaded.page == page && loaded.pages == first.pages) { "保存期间帖子分页发生变化，请稍后重试" }
            posts.addAll(loaded.posts)
            textCharacters += loaded.posts.sumOf { it.blocks.sumOf { b -> b.text.length + b.url.length } }
            check(textCharacters <= 2 * 1024 * 1024 && posts.size <= 30000) { "帖子正文过大，未覆盖已有保存" }
        }
        val unique = posts.distinctBy { it.id }.sortedBy { it.floor }.map { post ->
            if (post.subCount == 0) post else {
                val children = mutableListOf<Post>()
                for (page in 1..10000) {
                    currentCoroutineContext().ensureActive()
                    progress("正在保存 ${post.floor}楼的楼中楼，第 $page 页")
                    val loaded = api.subposts(tid, post.id, first.forumId, page, account)
                    val before = children.size
                    children.addAll(loaded.filter { child -> children.none { it.id == child.id } })
                    textCharacters += loaded.sumOf { it.blocks.sumOf { b -> b.text.length + b.url.length } }
                    check(textCharacters <= 2 * 1024 * 1024 && children.size <= 30000) { "楼中楼内容过大，未覆盖已有保存" }
                    if (loaded.size < 10) { check(children.size >= post.subCount) { "楼中楼未完整返回，已有保存保持不变" }; break }
                    check(children.size > before && page < 10000) { "楼中楼分页未前进" }
                }
                post.copy(children = children)
            }
        }
        val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val existingBytes = root.walkTopDown().filter(File::isFile).sumOf(File::length)
        var downloaded = 0L
        val cached = mutableMapOf<String, String>()
        suspend fun savedBlock(block: Block): Block {
            val save = block.type in setOf(2, 3, 20) && mode >= 1 || block.type in setOf(5, 10) && mode == 2
            if (!save || block.url.isBlank()) return block
            cached[block.url]?.let { return block.copy(localFile = it) }
            currentCoroutineContext().ensureActive()
            progress("正在保存离线媒体 ${cached.size + 1}")
            val limit = if (block.type in setOf(5, 10)) 128 * 1024 * 1024 else 20 * 1024 * 1024
            val file = File(directory, "pending-${cached.size}.bin")
            download(block.url, file, limit)
            downloaded += file.length()
            check(existingBytes + downloaded <= 512L * 1024 * 1024) { "离线媒体达到 512 MB 上限，请先删除部分本地保存" }
            if (block.type in setOf(2, 3, 20)) {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, options)
                check(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80000000) { "离线图片元数据不合法" }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            val target = File(directory, digest.digest().joinToString("") { "%02x".format(it) } + ".bin")
            if (target.exists()) file.delete() else check(file.renameTo(target)) { "离线媒体落盘失败" }
            val relative = directory.name + "/" + target.name
            cached[block.url] = relative
            return block.copy(localFile = relative)
        }
        try {
            val saved = unique.map { post -> post.copy(blocks = post.blocks.map { savedBlock(it) }, children = post.children.map { child -> child.copy(blocks = child.blocks.map { savedBlock(it) }) }) }
            val snapshot = first.thread.json().put("format", 1).put("forum-id", first.forumId).put("page", 1).put("pages", first.pages).put("mode", mode).put("media-directory", directory.name).put("posts", JSONArray().apply { saved.forEach { put(it.json()) } })
            check(snapshot.toString().toByteArray().size <= 8 * 1024 * 1024) { "离线正文超过 8 MB，已有保存保持不变" }
            val previous = store.list("saved").firstOrNull { it.first == tid }?.second?.optString("media-directory")
            store.putSaved(tid, snapshot, directory)
            if (previous != null && previous.matches(Regex("[a-f0-9-]{36}"))) File(root, previous).deleteRecursively()
            return snapshot
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    private suspend fun download(url: String, output: File, limit: Int) {
        require(Policy.mediaUrl(url) != null) { "不受信任的媒体地址" }
        suspendCancellableCoroutine<Unit> { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("Referer", "https://tieba.baidu.com/").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { r ->
                            check(r.isSuccessful) { "媒体下载 HTTP ${r.code}" }
                            val body = r.body ?: error("媒体响应为空")
                            require(body.contentLength() <= limit) { "媒体超过大小限制" }
                            body.byteStream().use { input -> output.outputStream().use { out ->
                                var count = 0L; val buffer = ByteArray(8192)
                                while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= limit) { "媒体超过大小限制" }; out.write(buffer, 0, n) }
                                check(count > 0) { "媒体响应为空" }
                            } }
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
    fun delete(tid: String) {
        val directory = store.list("saved").firstOrNull { it.first == tid }?.second?.optString("media-directory")
        store.remove("saved", tid)
        if (directory != null && directory.matches(Regex("[a-f0-9-]{36}"))) File(root, directory).deleteRecursively()
    }
    suspend fun prepareExport(block: Block): PreparedExport {
        require(block.type in setOf(3, 20, 5, 10))
        val output = File(context.cacheDir, "export-${UUID.randomUUID()}.bin")
        try {
            val local = safeFile(context, block.localFile)
            if (local != null) local.copyTo(output) else download(block.url, output, if (block.type in setOf(3, 20)) 20 * 1024 * 1024 else 128 * 1024 * 1024)
            val header = ByteArray(32)
            val size = output.inputStream().use { it.read(header) }
            require(size >= 4) { "媒体数据无效" }
            val prefix = header.toString(Charsets.ISO_8859_1)
            val type: Pair<String, String>
            val folder: String
            if (block.type in setOf(3, 20)) {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(output.path, options)
                require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80000000) { "图片元数据无效" }
                val mime = options.outMimeType ?: error("不能识别图片格式")
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: error("不能确定图片扩展名")
                type = mime to ext; folder = "Pictures"
            } else {
                type = when {
                    prefix.substring(4, 8) == "ftyp" -> if (block.type == 10) "audio/mp4" to "m4a" else "video/mp4" to "mp4"
                    prefix.startsWith("#!AMR") -> "audio/amr" to "amr"
                    prefix.startsWith("OggS") -> "audio/ogg" to "ogg"
                    prefix.startsWith("RIFF") && prefix.substring(8, 12) == "WAVE" -> "audio/wav" to "wav"
                    prefix.startsWith("ID3") -> "audio/mpeg" to "mp3"
                    header[0].toInt() and 255 == 255 && header[1].toInt() and 224 == 224 -> "audio/mpeg" to "mp3"
                    header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())) -> "video/webm" to "webm"
                    else -> error("媒体格式无法确认，未保存到系统媒体库")
                }
                folder = if (type.first.startsWith("video/")) "Movies" else "Music"
            }
            return PreparedExport(output, type.first, "TiebaPure-${System.currentTimeMillis()}.${type.second}", folder)
        } catch (e: Exception) { output.delete(); throw e }
    }
    companion object {
        fun safeFile(context: Context, relative: String): File? {
            if (!relative.matches(Regex("[a-f0-9-]{36}/[a-f0-9]{64}\\.bin"))) return null
            val root = File(context.filesDir, "offline").canonicalFile
            val file = File(root, relative).canonicalFile
            return file.takeIf { it.path.startsWith(root.path + "/") && it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.length() in 1..128L * 1024 * 1024 }
        }
    }
}
