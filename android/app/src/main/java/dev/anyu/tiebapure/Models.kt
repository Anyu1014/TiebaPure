package dev.anyu.tiebapure

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class User(val id: String = "", val name: String = "贴吧用户", val portrait: String = "", val mentionName: String = name)
enum class LikeObject(val value: Int) { THREAD(3), POST(1), SUBPOST(2) }
data class Block(val type: Int, val text: String = "", val url: String = "", val localFile: String = "")
data class Thread(val id: String, val title: String, val forum: String = "", val author: User = User(), val summary: String = "", val replies: Int = 0, val previews: List<String> = emptyList(), val matchedPost: String = "", val likes: Long? = null, val forumAvatar: String = "", val timestamp: Long = 0, val collected: Boolean = false, val isTop: Boolean = false)
data class Post(val id: String, val floor: Int, val author: User, val blocks: List<Block>, val subCount: Int = 0, val liked: Boolean = false, val children: List<Post> = emptyList(), val timestamp: Long = 0, val ipAddress: String = "", val likes: Long? = null, val authorLevel: Int = 0, val authorLevelName: String = "")
data class ThreadPage(val thread: Thread, val forumId: String, val posts: List<Post>, val page: Int, val pages: Int)
data class Profile(val user: User, val intro: String, val gender: Int, val following: Int, val fans: Int, val threads: Int, val followed: Boolean, val forums: List<Pair<String, String>> = emptyList(), val forumsPrivate: Boolean = false, val tiebaId: String = "")
data class Account(val uid: String, val name: String, val bduss: String, val stoken: String, val tbs: String, val portrait: String = "") {
    fun sessionMatches(other: Account?) = other != null && uid == other.uid && bduss == other.bduss && stoken == other.stoken
    fun withIdentity(user: User): Account {
        if (user.id != uid) return this
        return copy(name = user.name.trim().takeIf { it.isNotBlank() && it != "贴吧用户" } ?: name.trim().ifBlank { "贴吧用户" },
            portrait = user.portrait.takeIf { Policy.avatar(it) != null } ?: portrait)
    }
    fun json() = JSONObject().put("uid", uid).put("name", name).put("bduss", bduss).put("stoken", stoken).put("tbs", tbs).put("portrait", portrait)
    companion object {
        fun parse(j: JSONObject): Account {
            val account = Account(j.getString("uid"), j.optString("name").trim().ifBlank { "贴吧用户" }, j.getString("bduss"), j.getString("stoken"), j.getString("tbs"), j.optString("portrait"))
            require(account.uid.toLongOrNull()?.let { it > 0 } == true && account.tbs.isNotBlank()) { "账号资料不完整" }
            require(Policy.cookie(account.bduss) && Policy.cookie(account.stoken)) { "账号凭据格式无效" }
            return account
        }
    }
}

data class ForumEntry(val id: String, val name: String, val avatar: String = "")
internal fun forumEntry(j: JSONObject, fallbackName: String = ""): ForumEntry = ForumEntry(
    j.text("forum_id", "fid", "id"), j.text("forum_name", "name").ifBlank { fallbackName },
    Policy.avatar(j.text("avatar", "avatar_url", "forum_avatar")).orEmpty()
)

data class Route(val kind: String = "home", val id: String = "", val title: String = "", val extra: String = "")

object Policy {
    private val apiHosts = setOf("tieba.baidu.com", "c.tieba.baidu.com", "tiebac.baidu.com")
    fun apiUrl(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        it.isHttps && it.host in apiHosts && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
    } == true
    fun mediaUrl(raw: String): String? = raw.toHttpUrlOrNull()?.takeIf { u ->
        u.isHttps && u.username.isEmpty() && u.password.isEmpty() && u.port == 443 &&
            (u.host == "himg.bdimg.com" || listOf("baidu.com", "bdstatic.com", "baidupcs.com", "bcebos.com").any { u.host == it || u.host.endsWith(".$it") })
    }?.toString()
    fun loginUrl(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        it.isHttps && (it.host == "baidu.com" || it.host.endsWith(".baidu.com")) && it.username.isEmpty() && it.password.isEmpty() && it.port == 443
    } == true
    fun cookie(raw: String) = raw.isNotBlank() && raw.length <= 4096 && raw.all { it.code in 33..126 && it != ';' && it != ',' }
    fun avatar(raw: String): String? {
        val text = raw.trim().replaceFirst(Regex("^http://"), "https://")
        text.toHttpUrlOrNull()?.let { url ->
            val migrated = if (url.host == "tb.himg.baidu.com") url.newBuilder().host("himg.bdimg.com").build() else url
            return mediaUrl(migrated.toString())
        }
        return text.substringBefore('?').takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,512}")) }?.let { "https://himg.bdimg.com/sys/portrait/item/$it" }
    }
    fun webpage(raw: String): String? = raw.toHttpUrlOrNull()?.takeIf { u -> u.isHttps && u.port == 443 && u.username.isEmpty() && u.password.isEmpty() && u.host.contains('.') && !u.host.contains(':') && !u.host.matches(Regex("[0-9.]+")) && !u.host.endsWith(".local") && !u.host.endsWith(".localhost") }?.toString()
    fun voice(md5: String): String? = md5.takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) }?.let { "https://tiebac.baidu.com/c/p/voice?voice_md5=${it.lowercase()}&play_from=pb_voice_play" }
    fun signedId(raw: String): Long = raw.toLongOrNull()?.takeIf { it > 0 } ?: error("无效或超出范围的 ID")
    fun sign(fields: Map<String, String>): String {
        val data = fields.entries.map { "${it.key}=${it.value}" }.sorted().joinToString("") + "tiebaclient!!!"
        return MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }
    fun route(raw: String): Route? {
        if (raw.length > 8192) return null
        val u = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        if (u.rawUserInfo != null) return null
        val query = u.rawQuery.orEmpty().split('&').mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2) runCatching { java.net.URLDecoder.decode(p[0], "UTF-8") to java.net.URLDecoder.decode(p[1], "UTF-8") }.getOrNull() else null
        }.toMap()
        val parts = u.path.orEmpty().split('/').filter(String::isNotEmpty)
        if (u.scheme in setOf(BuildConfig.APP_SCHEME, "tiebapure")) {
            return when (u.host) {
                "thread" -> parts.firstOrNull()?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }?.let { Route("thread", it, extra = query["pid"]?.takeIf { value -> value.toLongOrNull()?.let { n -> n > 0 } == true }.orEmpty()) }
                "forum" -> (parts.firstOrNull() ?: query["kw"])?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 }?.let { Route("forum", it, it) }
                "open" -> query["url"]?.takeIf { it.startsWith("https://") }?.let { route(it) }
                else -> null
            }
        }
        if (u.scheme != "https" || !(u.host == "tieba.baidu.com" || u.host?.endsWith(".tieba.baidu.com") == true) || u.port !in listOf(-1, 443)) return null
        if (parts.size >= 2 && parts[0] == "p") return parts[1].takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }?.let { Route("thread", it, extra = (query["pid"] ?: query["post_id"])?.takeIf { value -> value.toLongOrNull()?.let { n -> n > 0 } == true }.orEmpty()) }
        if (parts.firstOrNull() in listOf(null, "f")) return query["kw"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 }?.let { Route("forum", it, it) }
        return null
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
internal fun JSONObject.userDisplayName(): String = text("name_show", "show_nickname", "user_nickname", "user_name", "name", "nick_name", "nick").trim()
internal fun JSONObject.userPortrait(): String = text("portrait", "portrait_url", "portraith").trim()
fun Thread.json() = JSONObject().put("id", id).put("title", title).put("forum", forum).put("author", JSONObject().put("id", author.id).put("name", author.name).put("portrait", author.portrait)).put("summary", summary).put("replies", replies).put("previews", JSONArray(previews)).put("likes", likes ?: JSONObject.NULL).put("forum-avatar", forumAvatar).put("timestamp", timestamp).put("collected", collected).put("is-top", isTop)
fun threadFromJson(j: JSONObject): Thread {
    val u = j.optJSONObject("author") ?: JSONObject()
    return Thread(j.getString("id"), j.getString("title"), j.optString("forum"), User(u.optString("id"), u.optString("name", "贴吧用户"), u.optString("portrait")), j.optString("summary"), j.optInt("replies"), j.optJSONArray("previews")?.let { a -> (0 until a.length()).map { a.optString(it) }.mapNotNull(Policy::mediaUrl).take(9) }.orEmpty(), likes = if (j.isNull("likes")) null else j.optLong("likes").coerceAtLeast(0), forumAvatar = j.optString("forum-avatar"), timestamp = j.optLong("timestamp"), collected = j.optBoolean("collected"), isTop = j.optBoolean("is-top"))
}

fun Post.json(): JSONObject = JSONObject().put("id", id).put("floor", floor).put("name", author.name).put("author-id", author.id).put("portrait", author.portrait).put("sub-count", subCount).put("liked", liked).put("timestamp", timestamp).put("ip-address", ipAddress).put("likes", likes ?: JSONObject.NULL).put("author-level", authorLevel).put("author-level-name", authorLevelName)
    .put("blocks", JSONArray().apply { blocks.forEach { put(JSONObject().put("type", it.type).put("text", it.text).put("url", it.url).put("local", it.localFile)) } })
    .put("children", JSONArray().apply { children.forEach { put(it.json()) } })
private fun postRowFromJson(j: JSONObject): Post = Post(
    j.getString("id"), j.optInt("floor"), User(j.optString("author-id"), j.optString("name"), j.optString("portrait")),
    j.getJSONArray("blocks").objects().map { Block(it.getInt("type"), it.optString("text"), it.optString("url"), it.optString("local")) },
    j.optInt("sub-count"), j.optBoolean("liked"), timestamp = j.optLong("timestamp"), ipAddress = j.optString("ip-address"),
    likes = if (j.isNull("likes")) null else j.optLong("likes").coerceAtLeast(0), authorLevel = j.optInt("author-level").coerceAtLeast(0), authorLevelName = j.optString("author-level-name")
)
fun postFromJson(j: JSONObject): Post = postRowFromJson(j).copy(children = j.optJSONArray("children")?.objects()?.map(::postRowFromJson).orEmpty())
