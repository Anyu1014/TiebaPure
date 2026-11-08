package dev.anyu.tiebapure

import com.google.protobuf.MessageLite
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import tieba.CommonRequestOuterClass.CommonRequest
import tieba.Personalized.*
import tieba.ThreadInfoOuterClass.ThreadInfo
import tieba.UserOuterClass as UserProto
import tieba.PbContentOuterClass.PbContent
import tieba.PostOuterClass as PostProto
import tieba.SubPostListOuterClass.SubPostList
import tieba.pbPage.PbPageRequestOuterClass.*
import tieba.pbPage.PbPageResponseOuterClass.PbPageResponse
import tieba.pbPage.PbPageResponseOuterClass.PbPageResponseData
import tieba.pbFloor.PbFloorRequestOuterClass.*
import tieba.pbFloor.PbFloorResponseOuterClass.PbFloorResponse

class TiebaApi(private val clientId: String, private val screenWidth: Int = 1080, private val screenHeight: Int = 2400, private val screenScale: Double = 3.0, private val systemVersion: String = "15", private val modelName: String = "Android", private val brand: String = "Android", private val privateDirectory: java.io.File? = null) {
    internal var officialPull: suspend (Account, List<dev.anyu.tiebapure.official.OfficialIM.Group>) -> OfficialPrivateSnapshot = { account, cursors ->
        OfficialPrivateTransport.session(account, clientId) { transport ->
            val request = OfficialPrivateTransport.pullRequest(clientId,transport.groups,cursors)
            OfficialPrivateSnapshot(transport.groups,dev.anyu.tiebapure.official.OfficialIM.PullResponse.parseFrom(transport.request(202003,request.toByteArray())))
        }
    }
    internal var officialSend: suspend (Account, dev.anyu.tiebapure.official.OfficialIM.CommitRequest) -> dev.anyu.tiebapure.official.OfficialIM.CommitResponse = { account, request ->
        OfficialPrivateTransport.session(account, clientId) { transport -> dev.anyu.tiebapure.official.OfficialIM.CommitResponse.parseFrom(transport.request(205001,request.toByteArray())) }
    }
    private val privateLock = kotlinx.coroutines.sync.Mutex()
    private val privateInboxes = mutableMapOf<String,OfficialPrivateInbox>()
    private fun inbox(a: Account): OfficialPrivateInbox {
        Policy.signedId(a.uid)
        return privateInboxes.getOrPut(a.uid) { OfficialPrivateInbox(a.uid,privateDirectory?.resolve(a.uid + ".pb")) }
    }

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val base = "https://tieba.baidu.com"
    private val app = "https://c.tieba.baidu.com"
    private val social = "https://tiebac.baidu.com"
    private val ua = "bdtb for Android 12.52.1.0"
    private fun common(account: Account?) = CommonRequest.newBuilder().setClientType(2).setClientVersion("12.52.1.0").setClientId(clientId)
        .setTimestamp(System.currentTimeMillis()).setBrand(brand).setModel(modelName).setOsVersion(systemVersion).setCuid(clientId).setCuidGalaxy2(clientId)
        .setFrom("1020031h").setNetType(1).setPversion("1.0.3").setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale)
        .setQType(0).setIsTeenager(0).setPersonalizedRecSwitch(1).setUserAgent("tieba/12.52.1.0").setBDUSS(account?.bduss.orEmpty()).setStoken(account?.stoken.orEmpty()).build()
    private fun mini() = linkedMapOf("_client_id" to clientId, "_client_type" to "2", "_client_version" to "7.2.0.0", "_os_version" to systemVersion, "_phone_imei" to "000000000000000", "cuid" to "${clientId.uppercase()}|000000000000000", "cuid_galaxy2" to "${clientId.uppercase()}|000000000000000", "from" to "1021636m", "model" to modelName, "net_type" to "1", "subapp_type" to "mini", "timestamp" to System.currentTimeMillis().toString())
    private fun officialFields(version: String, timestamp: String = System.currentTimeMillis().toString()) = linkedMapOf(
        "_client_id" to clientId, "_client_type" to "2", "_client_version" to version, "_os_version" to systemVersion, "_phone_imei" to "000000000000000", "active_timestamp" to timestamp,
        "brand" to brand, "cmode" to "1", "cuid" to "${clientId.uppercase()}|000000000000000", "cuid_galaxy2" to "${clientId.uppercase()}|000000000000000", "cuid_gid" to "", "from" to "tieba", "is_teenager" to "0", "mac" to "02:00:00:00:00:00", "model" to modelName, "net_type" to "1", "start_scheme" to "", "start_type" to "1", "timestamp" to timestamp
    )
    private suspend fun clientLogin(bduss: String, stoken: String): JSONObject {
        val timestamp = System.currentTimeMillis().toString()
        val fields = officialFields("11.10.8.6", timestamp) + mapOf("bdusstoken" to "$bduss|", "stoken" to stoken, "channel_id" to "", "channel_uid" to "", "authsid" to "null")
        val cuid = "${clientId.uppercase()}|000000000000000"
        return form("$app/c/s/login", fields, headers = mapOf("User-Agent" to "bdtb for Android 11.10.8.6", "Cookie" to "CUID=$cuid; ka=open", "Pragma" to "no-cache", "client_logid" to timestamp, "cuid" to cuid, "cuid_galaxy2" to cuid, "cuid_gid" to "")).also { validate(it, true) }
    }

    private suspend fun bytes(request: Request, maximumBytes: Int = 8 * 1024 * 1024, allowedContentTypes: Set<String>? = null): ByteArray {
        require(Policy.apiUrl(request.url.toString())) { "接口地址不受信任" }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("网络请求失败，请检查网络后重试", e)) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            check(it.isSuccessful) { "服务器返回 HTTP ${it.code}" }
                            val body = it.body ?: error("服务器响应为空")
                            if (allowedContentTypes != null) {
                                val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                                require(type in allowedContentTypes) { "接口响应类型无效，请重试" }
                            }
                            require(body.contentLength() <= maximumBytes) { "响应过大" }
                            body.byteStream().use { stream ->
                                val out = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val size = stream.read(buffer)
                                    if (size < 0) break
                                    check(out.size() + size <= maximumBytes) { "响应过大" }
                                    out.write(buffer, 0, size)
                                }
                                out.toByteArray()
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
    private suspend fun get(url: String, query: Map<String, String> = emptyMap(), cookie: String? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        val target = url.toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(target).header("User-Agent", ua).apply { if (cookie != null) header("Cookie", cookie); headers.forEach { (k, v) -> header(k, v) } }.build()
        return JSONObject(bytes(request).toString(Charsets.UTF_8))
    }
    private suspend fun form(url: String, fields: Map<String, String>, signed: Boolean = true, cookie: String? = null, headers: Map<String, String> = emptyMap(), maximumBytes: Int = 8 * 1024 * 1024, allowedContentTypes: Set<String>? = null): JSONObject {
        val values = fields.toMutableMap()
        if (signed) values["sign"] = Policy.sign(values)
        val body = FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).post(body).header("User-Agent", if (url.startsWith(social)) "tieba/22.5.1.0" else ua).apply { if (cookie != null) header("Cookie", cookie); headers.forEach { (k, v) -> header(k, v) } }.build()
        return JSONObject(bytes(request, maximumBytes, allowedContentTypes).toString(Charsets.UTF_8))
    }
    private suspend fun protobuf(url: String, message: MessageLite, account: Account?, includeToken: Boolean): ByteArray {
        val body = MultipartBody.Builder("--------7da3d81520810*").setType(MultipartBody.FORM).apply {
            if (includeToken && account != null) addFormDataPart("stoken", account.stoken)
            addFormDataPart("data", "file", message.toByteArray().toRequestBody("application/octet-stream".toMediaType()))
        }.build()
        return bytes(Request.Builder().url(url).post(body).header("X-BD-DATA-TYPE", "protobuf").header("Cookie", "ka=open").header("User-Agent", ua).build())
    }
    private fun validate(code: Int, message: String) { check(code == 0) { message.take(240).ifBlank { "贴吧接口错误 $code" } } }
    private fun validate(j: JSONObject, requireCode: Boolean = false) {
        if (requireCode) { SubmissionPolicy.clientStatus(j); return }
        val code = j.text("error_code", "no", "errno").toIntOrNull()
        require(!requireCode || code != null) { "服务器未返回可确认的操作结果" }
        validate(code ?: 0, j.text("error_msg", "error", "errmsg"))
        j.optJSONObject("error")?.let { validate(it.optInt("errno", 0), it.optString("errmsg")) }
    }
    private fun user(u: UserProto.User) = User(u.id.toString(), u.nameShow.ifBlank { u.name }.ifBlank { "贴吧用户" }, u.portrait.ifBlank { u.portraith })
    private fun thread(t: ThreadInfo): Thread = Thread((if (t.id != 0L) t.id else t.threadId).toString(), t.title, t.forumName.ifBlank { t.forumInfo.name }, user(t.author), TiebaEmoticon.summary(t.getAbstractList().flatMap { TiebaEmoticon.normalize(Block(it.type, it.text, it.src)) }), t.replyNum, t.mediaList.mapNotNull { Policy.mediaUrl(it.srcPic.ifBlank { it.bigPic }.replaceFirst("http://", "https://")) }.take(9), likes = (if (t.hasAgree()) t.agree.agreeNum else t.agreeNum.toLong()).coerceAtLeast(0), forumAvatar = t.forumInfo.avatar, timestamp = t.lastTimeInt.toLong().takeIf { it > 0 } ?: t.createTime.toLong(), collected = t.collectStatus == 1, isTop = t.isTop != 0)
    internal fun content(list: List<PbContent>): List<Block> {
        val hasImages = list.any { it.type in setOf(3, 20) && listOf(it.originSrc, it.bigCdnSrc, it.cdnSrc, it.bigSrc, it.src).any { src -> Policy.mediaUrl(src.replaceFirst("http://", "https://")) != null } }
        return list.flatMap { b ->
            if (b.type == 4 && b.uid > 0) return@flatMap listOf(Block(4, b.text, "user:${b.uid}"))
            if (b.type == 1) return@flatMap listOf(Block(1, b.text, Policy.webpage(b.link).orEmpty()))
            val sources = listOf(b.originSrc, b.bigCdnSrc, b.cdnSrc, b.bigSrc, b.src)
            val emoji = (if (b.type == 2) listOf(b.text, b.c).firstNotNullOfOrNull(TiebaEmoticon::imageName) else null)
                ?: sources.firstNotNullOfOrNull(TiebaEmoticon::nameFromURL)
            if (emoji != null && b.type in setOf(2, 3, 20)) return@flatMap listOf(Block(2, emoji, TiebaEmoticon.url(emoji).orEmpty()))
            val raw = when (b.type) { 3, 20 -> sources.firstOrNull { it.isNotBlank() }.orEmpty(); 5 -> b.link.ifBlank { b.src }; 10 -> Policy.voice(b.voiceMD5).orEmpty(); else -> b.link }
            val text = b.text.ifBlank { if (b.type == 2) b.c.ifBlank { "[表情]" } else "" }
            // Upload markers accompany real photos; never discard a standalone user message.
            val clean = if (hasImages && b.type in setOf(0, 9, 27)) text.replace(Regex("\\(?_ImageToUpload_\\)?"), "") else text
            TiebaEmoticon.normalize(Block(b.type, clean, Policy.mediaUrl(raw.replaceFirst("http://", "https://")).orEmpty()))
        }
    }
    private fun subpost(p: SubPostList): Post = Post(
        java.lang.Long.toUnsignedString(p.id), p.floor, user(p.author), content(p.contentList),
        liked = p.agree.hasAgree != 0, timestamp = Integer.toUnsignedLong(p.time), ipAddress = p.author.ipAddress,
        likes = if (p.hasAgree()) p.agree.agreeNum.coerceAtLeast(0) else null, authorLevel = p.author.levelId.coerceAtLeast(0), authorLevelName = p.author.levelName
    )
    internal fun post(p: PostProto.Post, users: Map<Long, UserProto.User> = emptyMap()): Post {
        val listed = users[p.authorId] ?: users[p.author.id]
        val author = if (p.hasAuthor()) listed?.toBuilder()?.mergeFrom(p.author)?.build() ?: p.author else listed ?: UserProto.User.getDefaultInstance()
        return Post(java.lang.Long.toUnsignedString(p.id), p.floor, user(author), content(p.contentList), p.subPostNumber, p.agree.hasAgree != 0,
            children = p.subPostList.subPostListList.take(3).map(::subpost), timestamp = Integer.toUnsignedLong(p.time), ipAddress = author.ipAddress,
            likes = if (p.hasAgree()) p.agree.agreeNum.coerceAtLeast(0) else null, authorLevel = author.levelId.coerceAtLeast(0), authorLevelName = author.levelName)
    }
    internal suspend fun privateConversations(a: Account, before: Long? = null): PrivateMessagePage<PrivateConversation> = privateLock.withLock {
        val inbox = inbox(a)
        val snapshot = officialPull(a, inbox.state.groupsList)
        currentCoroutineContext().ensureActive()
        inbox.merge(snapshot)
        inbox.conversations()
    }
    internal suspend fun markAllPrivateRead(a: Account) = privateLock.withLock {
        currentCoroutineContext().ensureActive()
        inbox(a).markAllRead()
    }
    internal suspend fun cachedPrivateUnread(a: Account): Boolean = privateLock.withLock {
        inbox(a).conversations().items.any { it.unread > 0 }
    }
    internal suspend fun privateMessages(a: Account, peer: PrivateConversation, before: Long? = null): PrivateMessagePage<PrivateChatMessage> = privateLock.withLock {
        val inbox = inbox(a)
        if(before == null) inbox.merge(officialPull(a,inbox.state.groupsList))
        inbox.messages(peer.userId,before)
    }
    internal suspend fun sendPrivateMessage(a: Account, peer: PrivateConversation, text: String, key: String, type: Int = 1): String = privateLock.withLock {
        PrivateMessageContent.validate(text, type)
        Policy.signedId(peer.userId); require(peer.userId != a.uid)
        val record = System.currentTimeMillis()
        val request = dev.anyu.tiebapure.official.OfficialIM.CommitRequest.newBuilder().setData(dev.anyu.tiebapure.official.OfficialIM.CommitData.newBuilder().setToUid(peer.userId.toLong()).setToUserType(0).setMsgType(type).setContent(text).setRecordId(record)).build()
        val response = try { officialSend(a,request) }
        catch(e: PrivateMessageRejected) { throw e }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) { throw PrivateSendUnknown() }
        if(!response.hasError()) throw PrivateSendUnknown()
        if(response.error.code != 0) throw PrivateMessageRejected(response.error.code.toLong())
        val receipt = response.data
        if(receipt.id <= 0 || receipt.toUid.toString() != peer.userId || receipt.recordId != record) throw PrivateSendUnknown()
        try { inbox(a).sent(peer,a,text,receipt,type) } catch(_: Exception) { throw PrivateSendUnknown() }
        receipt.id.toString()
    }

    internal suspend fun uploadPrivateVoice(a: Account, audio: ByteArray, duration: Int): String {
        require(audio.size in 1..2 * 1024 * 1024 && duration in 1..60) { "语音限60秒，文件不能超过2MB" }
        require(audio.take(6).toByteArray().contentEquals("#!AMR\n".toByteArray()) || audio.size >= 44 && audio.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && audio.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) { "语音格式无效，请重新录制" }
        val digest = java.security.MessageDigest.getInstance("MD5").digest(audio).joinToString("") { "%02x".format(it) }
        val common = privateUploadFields(a)
        val chunkSize = 30720
        for ((index, offset) in (audio.indices step chunkSize).withIndex()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val end = minOf(offset + chunkSize, audio.size)
            val fields = common + mapOf("chunk_md5" to digest, "voice_md5" to digest, "length" to (end - offset).toString(), "offset" to offset.toString(), "total_length" to audio.size.toString(), "chunk_no" to (index + 1).toString(), "total_num" to ((audio.size + chunkSize - 1) / chunkSize).toString())
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                (fields + ("sign" to Policy.sign(fields))).toSortedMap().forEach { (key, value) -> addFormDataPart(key, value) }
                addFormDataPart("voice_chunk", "file", audio.copyOfRange(offset, end).toRequestBody())
            }.build()
            val response = privateUploadResponse(bytes(Request.Builder().url("$social/c/c/voice/chunkupload").post(body).headers(privateUploadHeaders(a)).build(), 65536), "语音")
            validatePrivateUpload(response, "语音")
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val response = form("$social/c/c/voice/voice_fin_chunk_upload", common + mapOf("voice_md5" to digest, "type" to "2"), headers = privateUploadHeaders(a).toMultimap().mapValues { it.value.single() }, maximumBytes = 65536)
        validatePrivateUpload(response, "语音")
        val voiceID = response.optJSONObject("info")?.optString("voice_md5")?.let(PrivateMessageContent::voiceID)
            ?: error("语音上传未返回有效收据")
        return JSONArray().put(JSONObject().put("type", 10).put("voice_md5", voiceID).put("during_time", duration).put("has_read", 0).put("isUploaded", true)).toString()
    }

    internal suspend fun uploadPrivateImage(a: Account, bytes: ByteArray, width: Int, height: Int): String {
        require(bytes.size in 1..10 * 1024 * 1024 && width in 1..20000 && height in 1..20000 && width.toLong() * height <= 80000000) { "私信图片无效或过大" }
        val common = privateUploadFields(a)
        val groupId = privateLock.withLock {
            val store = inbox(a)
            if (store.state.groupsList.none { it.type == 6 && it.id > 0 }) store.merge(officialPull(a, store.state.groupsList))
            store.state.groupsList.firstOrNull { it.type == 6 && it.id > 0 }?.id ?: error("未取得当前账号的私信图片上传分组，请刷新会话后重试")
        }
        val chunkSize = 512000
        val digest = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02X".format(it) }
        var result = JSONObject()
        for ((index, offset) in (bytes.indices step chunkSize).withIndex()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val end = minOf(offset + chunkSize, bytes.size)
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                val fields = common + mapOf("resourceId" to digest + chunkSize, "chunkNo" to (index + 1).toString(), "isFinish" to if (end == bytes.size) "1" else "0", "size" to bytes.size.toString(), "width" to width.toString(), "height" to height.toString(), "groupId" to groupId.toString(), "alt" to "json", "is_bjh" to "0", "saveOrigin" to "1", "pic_water_type" to "3")
                (fields + ("sign" to Policy.sign(fields))).forEach { (key, value) -> addFormDataPart(key, value) }
                addFormDataPart("chunk", "file", bytes.copyOfRange(offset, end).toRequestBody())
            }.build()
            result = privateUploadResponse(this.bytes(Request.Builder().url("$social/c/s/uploadPicture").post(body).headers(privateUploadHeaders(a)).build(), 65536), "图片")
            validatePrivateUpload(result, "图片")
        }
        return privateImageContent(result, width, height)
    }
    internal fun privateImageContent(result: JSONObject, width: Int, height: Int): String {
        validatePrivateUpload(result, "图片")
        val info = result.optJSONObject("picInfo") ?: error("私信图片上传响应缺少 picInfo 收据")
        fun imageURL(key: String) = Policy.mediaUrl(info.optJSONObject(key)?.optString("picUrl").orEmpty().replaceFirst("http://", "https://"))
        val url = listOf("originPic", "bigPic", "smallPic").firstNotNullOfOrNull(::imageURL) ?: error("私信图片上传收据缺少有效图片地址")
        val thumbnail = imageURL("smallPic") ?: url
        return JSONArray().put(JSONObject().put("src", thumbnail).put("big_src", url).put("type", "3").put("bsize", "$width,$height")).toString()
    }

    private val privateUploadVersion = "22.13.1.1"

    private fun privateUploadFields(account: Account): Map<String, String> {
        require(Policy.cookie(account.bduss) && Policy.cookie(account.stoken)) { "账号凭据格式无效" }
        return officialFields(privateUploadVersion) + mapOf(
            "BDUSS" to account.bduss, "stoken" to account.stoken, "cuid" to clientId,
            "cuid_galaxy2" to clientId, "support_image" to "jepgwebp",
        )
    }

    private fun privateUploadHeaders(account: Account): Headers = Headers.Builder()
        .add("User-Agent", "bdtb for Android $privateUploadVersion")
        .add("Cookie", "CUID=$clientId; ka=open; BDUSS=${account.bduss}; stoken=${account.stoken}")
        .add("cuid", clientId)
        .add("cuid_galaxy2", clientId)
        .build()
    private fun privateUploadResponse(data: ByteArray, kind: String): JSONObject = try { JSONObject(data.toString(Charsets.UTF_8)) }
        catch (_: org.json.JSONException) { error("私信${kind}上传响应格式无法识别") }
    private fun validatePrivateUpload(response: JSONObject, kind: String) {
        val code = SubmissionPolicy.strictInt(response.optJSONObject("error")?.opt("errno") ?: response.opt("error_code")) ?: error("私信${kind}上传响应缺少有效状态码")
        check(code == 0) { "私信${kind}上传未通过（错误码 $code）" }
    }

    suspend fun home(account: Account?, page: Int): List<Thread> {
        require(page > 0)
        val data = PersonalizedRequestData.newBuilder().setCommon(common(account)).setLoadType(if (page == 1) 1 else 2).setPn(page).setPageThreadCount(11).setQType(1).setNewNetType(1).setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale)
        val response = PersonalizedResponse.parseFrom(protobuf("$base/c/f/excellent/personalized?cmd=309264", PersonalizedRequest.newBuilder().setData(data).build(), account, false))
        validate(response.error.errorCode, response.error.userMsg.ifBlank { response.error.errorMsg })
        check(response.hasData()) { "未返回首页内容" }
        return response.data.threadListList.filter { it.isDeleted == 0 && !it.hasAlaInfo() && !it.hasTwzhiboInfo() }.map(::thread).filter { it.id != "0" }
    }
    private suspend fun forumResponse(name: String, page: Int, sort: Int, good: Boolean): JSONObject {
        require(name.isNotBlank() && name.length <= 100 && page > 0)
        val fields = mini() + mapOf("kw" to name, "pn" to "$page", "sort_type" to if (good) "-1" else "$sort", "q_type" to "2", "st_type" to "tb_forumlist", "with_group" to "0", "rn" to "20", "scr_w" to screenWidth.toString(), "scr_h" to screenHeight.toString(), "scr_dip" to screenScale.toString()) + if (good) mapOf("is_good" to "1", "cid" to "0") else emptyMap()
        return form("$app/c/f/frs/page", fields, cookie = "ka=open").also { validate(it) }
    }
    suspend fun forumInfo(name: String): ForumEntry = forumEntry(forumResponse(name, 1, 0, false).optJSONObject("forum") ?: JSONObject(), name)
    suspend fun automationThreads(name: String, page: Int): List<AutoReplyThread> = forumResponse(name, page, 1, false)
        .optJSONArray("thread_list")?.objects().orEmpty().filter { it.optInt("is_ad") == 0 && it.optInt("is_deleted") == 0 }
        .map { AutoReplyThread(it.text("id", "tid"), it.text("title"), it.optJSONObject("author")?.text("id", "uid").orEmpty().ifBlank { it.text("author_id") }, it.optLong("create_time"), it.optInt("is_top") != 0) }
        .filter { it.id.toLongOrNull()?.let { value -> value > 0 } == true }
    suspend fun forum(name: String, page: Int, sort: Int = 0, good: Boolean = false): List<Thread> {
        val j = forumResponse(name, page, sort, good)
        val info = forumEntry(j.optJSONObject("forum") ?: JSONObject(), name)
        val users = j.optJSONArray("user_list")?.objects().orEmpty().associateBy { it.text("id", "uid") }
        return j.optJSONArray("thread_list")?.objects().orEmpty().filter { it.optInt("is_ad") == 0 && it.optInt("is_deleted") == 0 }.map { jsonThread(it, name, users, publishedFirst = sort == 1 && !good).copy(forumAvatar = info.avatar) }
    }
    internal fun jsonSummary(values: org.json.JSONArray?): String = TiebaEmoticon.summary(values?.objects().orEmpty().flatMap {
        val type = it.optInt("type")
        val code = if (type == 2) listOf(it.text("text"), it.text("c")).firstOrNull { candidate -> TiebaEmoticon.imageName(candidate) != null } ?: it.text("text", "c") else it.text("text")
        TiebaEmoticon.normalize(Block(type, code, it.text("src", "url", "cdn_src")))
    })
    internal fun jsonThread(j: JSONObject, forum: String = "", users: Map<String, JSONObject> = emptyMap(), publishedFirst: Boolean = false): Thread {
        val inline = j.optJSONObject("author") ?: JSONObject()
        val uid = inline.text("id", "uid").ifBlank { j.text("author_id") }
        val listed = users[uid] ?: JSONObject()
        val author = User(uid, inline.userDisplayName().ifBlank { listed.userDisplayName() }.ifBlank { "贴吧用户" }, inline.userPortrait().ifBlank { listed.userPortrait() })
        val abstracts = jsonSummary(j.optJSONArray("abstract"))
        val previews = j.optJSONArray("media")?.objects().orEmpty().mapNotNull { media ->
            listOf("src_pic", "big_pic", "dynamic_pic", "origin_pic").firstNotNullOfOrNull { key -> Policy.mediaUrl(media.text(key).replaceFirst("http://", "https://")) }
        }.take(9)
        val replied = j.optLong("last_time_int").takeIf { it in 1..253402300799L }
        val created = j.optLong("create_time").takeIf { it in 1..253402300799L }
        val timestamp = (if (publishedFirst) created ?: replied else replied ?: created) ?: 0
        return Thread(j.text("id", "tid", "thread_id"), j.text("title", "thread_title"), forum.ifBlank { j.text("forum_name", "fname") }, author, abstracts, j.optInt("reply_num"), previews = previews, likes = (j.optJSONObject("agree")?.takeIf { it.has("agree_num") }?.optLong("agree_num") ?: j.optLong("agree_num", j.optLong("agreeNum"))).coerceAtLeast(0), timestamp = timestamp, isTop = j.optInt("is_top") != 0)
    }

    suspend fun thread(id: String, page: Int, account: Account?, descending: Boolean = false, onlyAuthor: Boolean = false, postId: String? = null, hot: Boolean = false, descendingTotalPages: Int? = null): ThreadPage {
        require(page > 0)
        if (descending && !hot) {
            val discovery = if (descendingTotalPages == null || descendingTotalPages <= 0) thread(id, 1, account, onlyAuthor = onlyAuthor) else null
            val total = descendingTotalPages?.takeIf { it > 0 } ?: discovery!!.pages
            val serverPage = (total - page + 1).coerceAtLeast(1)
            val loaded = if (serverPage == 1 && discovery != null) discovery else thread(id, serverPage, account, onlyAuthor = onlyAuthor)
            val main = discovery?.posts?.firstOrNull { it.floor == 1 }
            val posts = if (main != null && loaded.posts.none { it.floor == 1 }) listOf(main) + loaded.posts else loaded.posts
            return loaded.copy(posts = posts.inReplyOrder(1), page = page, pages = total)
        }
        val data = PbPageRequestData.newBuilder().setCommon(common(account)).setKz(Policy.signedId(id)).setPn(page).setR(if (hot) 2 else 0).setLz(if (onlyAuthor) 1 else 0).setWithFloor(1).setFloorRn(4).setFloorSortType(1).setQType(2).setRn(15).setSourceType(2).setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale)
        if (postId != null && page <= 1) data.setPid(Policy.signedId(postId)).setPn(0)
        val r = PbPageResponse.parseFrom(protobuf("$base/c/f/pb/page?cmd=302001&format=protobuf", PbPageRequest.newBuilder().setData(data).build(), account, true))
        validate(r.error.errorCode, r.error.userMsg.ifBlank { r.error.errorMsg }); check(r.hasData()) { "未返回帖子内容" }
        return threadPage(r.data, if (hot) 2 else 0)
    }
    internal fun threadPage(d: PbPageResponseData, sort: Int = 0): ThreadPage {
        val users = d.userListList.associateBy { it.id }
        val rawPosts = d.postListList.toMutableList()
        if (d.hasFirstFloorPost() && d.firstFloorPost.id != 0L && rawPosts.none { it.id == d.firstFloorPost.id }) rawPosts.add(0, d.firstFloorPost)
        // Public PBPage also attaches this placeholder to ordinary floors, including thread 9107183498.
        val posts = rawPosts.filter { !it.hasAdvertisement() || it.advertisement.advertisementId == "advertisement_test1" && it.id != 0L && it.floor > 0 }.map { post(it, users) }
        val summary = thread(d.thread).copy(forum = d.forum.name, forumAvatar = d.forum.avatar.ifBlank { d.thread.forumInfo.avatar })
        // The first floor has a separate post counter; its action likes the thread.
        val displayed = posts.map { if (it.floor == 1) it.copy(likes = summary.likes, liked = d.thread.agree.hasAgree != 0) else it }
        return ThreadPage(summary, d.forum.id.toString(), displayed.inReplyOrder(sort), d.page.currentPage.coerceAtLeast(1), d.page.totalPage.coerceAtLeast(1))
    }
    internal data class SubpostsPage(val posts: List<Post>, val page: Int, val hasMore: Boolean)
    suspend fun subposts(tid: String, pid: String, fid: String, page: Int, account: Account?): List<Post> = subpostsPage(tid, pid, fid, page, account).posts
    internal suspend fun subpostsPage(tid: String, pid: String, fid: String, page: Int, account: Account?, subpostId: String? = null): SubpostsPage {
        require(page > 0)
        val d = PbFloorRequestData.newBuilder().setCommon(common(account)).setKz(Policy.signedId(tid)).setPid(Policy.signedId(pid)).setForumId(Policy.signedId(fid)).setPn(page).setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale)
        if (subpostId != null && page == 1) d.setSpid(Policy.signedId(subpostId)).setPn(0)
        val r = PbFloorResponse.parseFrom(protobuf("$base/c/f/pb/floor?cmd=302002&format=protobuf", PbFloorRequest.newBuilder().setData(d).build(), account, false))
        validate(r.error.errorCode, r.error.userMsg.ifBlank { r.error.errorMsg })
        val posts = r.data.subpostListList.map(::subpost)
        val currentPage = r.data.page.currentPage.takeIf { it > 0 } ?: page
        val more = if (r.data.hasPage()) r.data.page.hasMore != 0 || currentPage < r.data.page.totalPage else posts.size >= 10
        return SubpostsPage(posts, currentPage, more)
    }

    internal suspend fun accountLikes(account: Account, page: Int, tab: Int): AccountLikesPage {
        require(page > 0 && tab in 0..2)
        val fields = officialFields("12.64.1.0") + mapOf("BDUSS" to account.bduss, "stoken" to account.stoken, "tbs" to account.tbs, "tab_id" to tab.toString(), "pn" to page.toString(), "rn" to "20")
        return accountLikesPage(form("$base/c/u/feed/userAgree", fields,
            cookie = "BDUSS=${account.bduss};STOKEN=${account.stoken}",
            headers = mapOf("User-Agent" to "tieba/12.64.1.0", "Origin" to base,
                "Referer" to "$base/mo/q/hybrid-main-usercenter/myLike/hybrid?nonavigationbar=1", "Accept" to "application/json, text/plain, */*"),
            allowedContentTypes = setOf("application/json", "text/json", "application/x-javascript", "application/javascript", "text/javascript")))
    }
    suspend fun searchUsers(keyword: String, account: Account?): List<User> {
        val word = keyword.trim(); require(word.length in 1..500)
        if (word.all { it in '0'..'9' }) return listOf(userByTiebaId(word, account))
        val root = get("$base/mo/q/search/user", mapOf("word" to word, "_client_version" to "8.0.8.0", "cuid_gid" to ""), headers = mapOf("User-Agent" to "bdtb for Android 8.0.8.0"))
        validate(root)
        val data = root.optJSONObject("data") ?: return emptyList()
        fun rows(value: Any?): List<JSONObject> = when (value) {
            is JSONArray -> value.objects()
            is JSONObject -> if (value.has("id") || value.has("user_id") || value.has("uid")) listOf(value) else value.keys().asSequence().mapNotNull { value.optJSONObject(it) }.toList()
            else -> emptyList()
        }
        return (rows(data.opt("exactMatch")) + rows(data.opt("fuzzyMatch"))).map { User(it.text("id", "user_id", "uid"), it.text("show_nickname", "user_nickname", "name").ifBlank { "贴吧用户" }, it.text("portrait")) }.filter { it.id.toLongOrNull()?.let { id -> id > 0 } == true }.distinctBy { it.id }
    }

    suspend fun search(keyword: String, page: Int, forum: String = ""): List<Thread> {
        require(keyword.isNotBlank() && keyword.length <= 500 && page > 0)
        val query = mapOf("word" to keyword, "pn" to "$page", "st" to "5", "tt" to "2", "ct" to if (forum.isBlank()) "1" else "2", "cv" to "99.9.101") + if (forum.isBlank()) emptyMap() else mapOf("fname" to forum, "rn" to "30")
        val j = get("$base/mo/q/search/thread", query, headers = mapOf("User-Agent" to "tieba/12.52.1.0 skin/default", "Referer" to "$base/mo/q/hybrid/search?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}")); validate(j)
        return j.optJSONObject("data")?.optJSONArray("post_list")?.objects().orEmpty().map { row ->
            val info = row.optJSONObject("thread_info") ?: row
            val author = row.optJSONObject("user") ?: row.optJSONObject("user_info") ?: JSONObject()
            val f = row.optJSONObject("forum_info") ?: JSONObject()
            val post = row.optJSONObject("post_info")
            val likes = row.text("like_num").toLongOrNull() ?: (post ?: info).text("like_num").toLongOrNull()
            Thread(
                info.text("tid", "thread_id", "id"), info.text("title", "thread_title"),
                f.text("forum_name", "name").ifBlank { row.text("forum_name") },
                User(author.text("user_id", "id"), author.text("show_nickname", "user_name").ifBlank { "贴吧用户" }, author.text("portrait")),
                jsonSummary(row.optJSONArray("content")),
                matchedPost = row.text("pid").takeIf { it.toLongOrNull()?.let { value -> value > 0 } == true }.orEmpty(),
                likes = likes?.takeIf { it >= 0 }
            )
        }.filter { it.id.toLongOrNull()?.let { n -> n > 0 } == true }
    }
    suspend fun login(bduss: String, stoken: String): Account {
        require(Policy.cookie(bduss) && Policy.cookie(stoken)) { "登录凭据不完整，请在百度页面完成登录" }
        val client = try { clientLogin(bduss, stoken) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        val user = client?.optJSONObject("user")
        val clientTbs = client?.optJSONObject("anti")?.text("tbs").orEmpty()
        if (user != null && user.text("id").toLongOrNull()?.let { it > 0 } == true && clientTbs.isNotBlank()) {
            return Account.parse(JSONObject().put("uid", user.text("id")).put("name", user.userDisplayName()).put("bduss", bduss).put("stoken", stoken).put("tbs", clientTbs).put("portrait", user.userPortrait()))
        }
        val j = get("$base/mo/q/newmoindex", mapOf("need_user" to "1"), "BDUSS=$bduss; STOKEN=$stoken")
        val d = j.optJSONObject("data") ?: error("未取得账号资料")
        check(d.text("is_login").lowercase() !in setOf("0", "false")) { "百度未确认登录状态，请重新登录" }
        val tbs = d.text("tbs", "itb_tbs")
        return Account.parse(JSONObject().put("uid", d.text("uid", "id")).put("name", d.userDisplayName()).put("bduss", bduss).put("stoken", stoken).put("tbs", tbs).put("portrait", d.userPortrait()))
    }
    suspend fun followedForums(a: Account): List<ForumEntry> {
        val j = form("$app/c/f/forum/getforumlist", mapOf("BDUSS" to a.bduss, "stoken" to a.stoken, "user_id" to a.uid, "_client_version" to "11.10.8.6"), false); validate(j)
        return j.optJSONArray("forum_info")?.objects().orEmpty().map { forumEntry(it) }.filter { it.name.isNotBlank() }
    }
    private suspend fun tbs(a: Account): String {
        val token = try { clientLogin(a.bduss, a.stoken).optJSONObject("anti")?.text("tbs") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: SessionExpired) { throw e } catch (e: Exception) { null }
        if (!token.isNullOrBlank()) return token
        val j = get("$base/mo/q/newmoindex", mapOf("need_user" to "1"), "BDUSS=${a.bduss}; STOKEN=${a.stoken}")
        val d = j.optJSONObject("data") ?: error("登录校验返回为空，请重试")
        if (d.text("is_login").lowercase() in setOf("0", "false") || d.text("uid", "id") != a.uid) throw SessionExpired()
        return d.text("tbs", "itb_tbs").takeIf(String::isNotBlank) ?: error("未取得新的登录校验信息，请重新登录")
    }
    suspend fun favorites(a: Account, page: Int): List<Thread> {
        val j = form("$app/c/u/feed/threadStoreList", mapOf("BDUSS" to a.bduss, "stoken" to a.stoken, "pn" to "$page", "rn" to "20")); validate(j)
        return (j.optJSONArray("store_thread") ?: j.optJSONArray("thread_list") ?: j.optJSONObject("data")?.optJSONArray("thread_list") ?: JSONArray()).objects().map { jsonThread(it) }
    }
    suspend fun favorite(a: Account, tid: String, pid: String, add: Boolean) {
        Policy.signedId(tid)
        val fields = mutableMapOf("BDUSS" to a.bduss, "tbs" to tbs(a))
        if (add) fields["data"] = JSONArray().put(JSONObject().put("tid", tid).put("pid", pid).put("status", 1)).toString() else fields["tid"] = tid
        validate(form("$app/c/c/post/${if (add) "addstore" else "rmstore"}", fields), true)
    }
    suspend fun signForum(a: Account, fid: String, name: String): String {
        val fields = officialFields("12.52.1.0") + mapOf("BDUSS" to a.bduss, "kw" to name, "fid" to fid, "tbs" to tbs(a))
        val j = form("$app/c/c/forum/sign", fields)
        if (j.optInt("error_code") == 160002) return "今天已经签到"
        validate(j, true); return "签到成功"
    }
    suspend fun like(a: Account, tid: String, pid: String, kind: LikeObject, liked: Boolean) {
        Policy.signedId(tid); require(pid.toULongOrNull()?.let { it > 0u } == true)
        val fields = mapOf("BDUSS" to a.bduss, "_client_version" to "22.5.1.0", "agree_type" to "2", "cuid" to "${clientId.uppercase()}|000000000000000", "obj_type" to kind.value.toString(), "op_type" to if (liked) "0" else "1", "post_id" to if (kind == LikeObject.THREAD) "0" else pid, "tbs" to tbs(a), "thread_id" to tid)
        validate(form("$social/c/c/agree/opAgree", fields), true)
    }
    internal suspend fun messages(a: Account, at: Boolean, page: Int): MessageFeed {
        val fields = mini().apply { remove("subapp_type"); remove("cuid_galaxy2"); putAll(mapOf("_client_version" to "8.2.2", "from" to "baidu_appstore", "BDUSS" to a.bduss, "pn" to "$page", "stErrorNums" to "0")) }
        val j = form("$app/c/u/feed/${if (at) "atme" else "replyme"}", fields); validate(j)
        return MessageFeed.decode(j, at)
    }

    suspend fun userByTiebaId(id: String, a: Account?): User {
        require(id.toLongOrNull()?.let { it > 0 && it.toString() == id } == true) { "请输入有效的贴吧 ID" }
        val data = tiebapure.profile.PrivateMessageUser.PrivateMessageUserRequestData.newBuilder().setCommon(common(a)).setTiebaUid(id)
        val request = tiebapure.profile.PrivateMessageUser.PrivateMessageUserRequest.newBuilder().setData(data).build()
        val response = tiebapure.profile.PrivateMessageUser.PrivateMessageUserResponse.parseFrom(protobuf("$social/c/u/user/getUserByTiebaUid?cmd=309702&format=protobuf", request, a, false))
        validate(response.error.errorCode, response.error.userMsg.ifBlank { response.error.errorMsg })
        require(response.hasData() && response.data.hasUser() && response.data.user.id > 0 && response.data.user.tiebaUid == id) { "没有找到对应 ID 的用户" }
        return user(response.data.user)
    }
    suspend fun profile(uid: String, a: Account?): Profile {
        val data = tiebapure.profile.UserProfile.UserProfileRequestData.newBuilder().setCommon(common(a)).setFriendUid(Policy.signedId(uid))
            .setNeedPostCount(1).setIsGuest(if (uid == a?.uid) 0 else 1).setPn(1).setRn(20).setHasPlist(1).setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale).setIsFromUsercenter(1).setPage(1)
        if (a != null) data.setUid(Policy.signedId(a.uid))
        val request = tiebapure.profile.UserProfile.UserProfileRequest.newBuilder().setData(data).build()
        val r = tiebapure.profile.UserProfile.UserProfileResponse.parseFrom(protobuf("$social/c/u/user/profile?cmd=303012&format=protobuf", request, a, false))
        validate(r.error.errorCode, r.error.userMsg.ifBlank { r.error.errorMsg })
        check(r.hasData() && r.data.hasUser()) { "未返回用户资料，可能已设置隐私限制" }
        val u = r.data.user
        val forums = u.likeForumList.map { java.lang.Long.toUnsignedString(it.forumId) to it.forumName }.filter { it.second.isNotBlank() }.distinctBy { it.second }
        val privateForums = uid != a?.uid && u.privSets.like != 1 && u.myLikeNum > 0 && forums.isEmpty()
        return Profile(user(u), u.displayIntro.ifBlank { u.intro }, if (u.sex != 0) u.sex else u.gender, u.concernNum, u.fansNum, u.threadNum, u.hasConcerned != 0, forums, privateForums, u.tiebaUid.ifBlank { u.id.toString() })
    }
    suspend fun userThreads(uid: String, a: Account?, page: Int): List<Thread> {
        val data = tiebapure.profile.UserProfile.UserThreadsRequestData.newBuilder().setCommon(common(a)).setUid(Policy.signedId(uid)).setRn(20).setIsThread(1).setNeedContent(1).setPn(page).setScrW(screenWidth).setScrH(screenHeight).setScrDip(screenScale).setQType(1).setIsViewCard(1)
        val request = tiebapure.profile.UserProfile.UserThreadsRequest.newBuilder().setData(data).build()
        val r = tiebapure.profile.UserProfile.UserThreadsResponse.parseFrom(protobuf("$social/c/u/feed/userpost?cmd=303002&format=protobuf", request, a, true))
        validate(r.error.errorCode, r.error.userMsg.ifBlank { r.error.errorMsg })
        check(r.hasData()) { "未返回用户主题" }
        check(r.data.hidePost == 0) { "用户设置了主题隐私" }
        return r.data.postListList.map { Thread(java.lang.Long.toUnsignedString(it.threadId), it.title, it.forumName, User(it.userId.toString(), it.nameShow.ifBlank { it.userName }, it.userPortrait), it.contentThread.ifBlank { TiebaEmoticon.summary(it.abstractThreadList.flatMap { p -> TiebaEmoticon.normalize(Block(p.type, p.text, p.src)) }) }, it.replyNum, likes = (if (it.hasAgree()) it.agree.agreeNum else it.agreeNum.toLong()).coerceAtLeast(0)) }
    }
    suspend fun relationships(uid: String, a: Account?, followers: Boolean, page: Int): List<User> {
        Policy.signedId(uid); require(page > 0)
        val j = form("$social/c/u/${if (followers) "fans/page" else "follow/followList"}", mapOf("BDUSS" to a?.bduss.orEmpty(), "_client_version" to "22.5.1.0", "uid" to uid, "pn" to "$page")); validate(j)
        return (j.optJSONArray(if (followers) "user_list" else "follow_list") ?: JSONArray()).objects().map { User(it.text("id"), it.userDisplayName().ifBlank { "贴吧用户" }, it.userPortrait().substringBefore('?'), it.text("name", "user_name").ifBlank { it.userDisplayName() }) }
    }
    suspend fun followUser(a: Account, portrait: String, follow: Boolean) {
        require(portrait.isNotBlank() && portrait.length <= 1024)
        validate(form("$social/c/c/user/${if (follow) "follow" else "unfollow"}", mapOf("BDUSS" to a.bduss, "portrait" to portrait.substringBefore('?'), "tbs" to tbs(a))), true)
    }
    suspend fun forumId(name: String): String {
        require(name.isNotBlank() && name.length <= 100)
        val j = get("$app/f/commit/share/fnameShareApi", mapOf("fname" to name, "ie" to "utf-8")); validate(j, true)
        return j.optJSONObject("data")?.text("fid")?.also { Policy.signedId(it) } ?: error("未取得贴吧 ID")
    }
    suspend fun followForum(a: Account, name: String, follow: Boolean) {
        validate(form("$social/c/c/forum/${if (follow) "like" else "unfavolike"}", mapOf("BDUSS" to a.bduss, "fid" to forumId(name), "tbs" to tbs(a))), true)
    }
    suspend fun updateProfile(a: Account, name: String, intro: String, sex: Int) {
        require(name.trim().isNotEmpty() && name.length <= 128 && intro.length <= 2000 && sex in 0..2)
        val fields = mutableMapOf("BDUSS" to a.bduss, "nick_name" to name.trim(), "intro" to intro)
        if (sex != 0) fields["sex"] = sex.toString()
        val j = try { form("$social/c/c/profile/modify", fields) } catch (e: Exception) { throw OutcomeUnknown() }
        SubmissionPolicy.modifiedProfile(j)
    }
    suspend fun deleteOwnThread(a: Account, page: ThreadPage) {
        val first = page.posts.firstOrNull { it.floor == 1 } ?: error("未取得主楼，不能删除")
        require(first.author.id == a.uid) { "只能删除本人发布的主题" }
        Policy.signedId(page.thread.id); Policy.signedId(page.forumId); Policy.signedId(first.id)
        val token = tbs(a)
        val fields = officialFields("12.25.1.0") + mapOf("BDUSS" to a.bduss, "delete_my_thread" to "1", "fid" to page.forumId, "is_frs_mask" to "0", "is_vipdel" to "0", "src" to "1", "tbs" to token, "word" to page.thread.forum, "z" to page.thread.id)
        val j = try { form("$app/c/c/bawu/delthread", fields, headers = mapOf("User-Agent" to "bdtb for Android 12.25.1.0")) } catch (e: Exception) { throw OutcomeUnknown() }
        SubmissionPolicy.modifiedProfile(j)
    }
    suspend fun deleteOwnComment(a: Account, tid: String, fid: String, forum: String, post: Post, subpost: Boolean) {
        require(a.uid.toLongOrNull()?.let { it > 0 } == true && post.author.id == a.uid) { "只能删除本人发布的评论" }
        require(subpost || post.floor > 1) { "主楼请使用删除主题" }
        Policy.signedId(tid); Policy.signedId(fid); Policy.signedId(post.id)
        require(forum.isNotBlank() && forum.length <= 100) { "缺少贴吧信息，无法删除评论" }
        val fields = officialFields("12.25.1.0") + mapOf("BDUSS" to a.bduss, "delete_my_post" to "1", "fid" to fid, "word" to forum, "z" to tid, "pid" to post.id, "isfloor" to "0", "src" to "1", "is_vipdel" to "0", "tbs" to tbs(a))
        val j = try { form("$app/c/c/bawu/delpost", fields, headers = mapOf("User-Agent" to "bdtb for Android 12.25.1.0")) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { throw OutcomeUnknown() }
        SubmissionPolicy.modifiedProfile(j)
    }
    private suspend fun postingTbs(a: Account): String {
        val j = form("$social/c/s/login", mapOf("_client_version" to "22.5.1.0", "bdusstoken" to a.bduss), headers = mapOf("User-Agent" to "tieba/22.5.1.0 skin/default"))
        validate(j, true)
        return j.optJSONObject("anti")?.text("tbs")?.takeIf { it.isNotBlank() && it.length <= 4096 && it.all { c -> c.code in 33..126 } } ?: error("未能取得发布校验，请重新登录")
    }
    private fun webHeaders(referer: String) = mapOf("Origin" to base, "Referer" to referer, "X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json, text/plain, */*", "Accept-Language" to "zh-CN,zh;q=0.9", "Cache-Control" to "no-cache", "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
    suspend fun uploadReplyImage(a: Account, bytes: ByteArray, tid: String, index: Int): String {
        require(bytes.size in 1..10 * 1024 * 1024 && index in 0..8)
        val url = "$base/mo/q/cooluploadpic?type=ajax&r=${System.currentTimeMillis()}_$index"
        val j = form(url, mapOf("pic" to java.util.Base64.getEncoder().encodeToString(bytes)), false, "BDUSS=${a.bduss}; STOKEN=${a.stoken}", webHeaders("$base/p/$tid"))
        if (SubmissionPolicy.challenge(j)) throw ChallengeRequired()
        for (obj in listOfNotNull(j, j.optJSONObject("data"))) for (key in listOf("error_code", "err_code")) if (obj.has(key)) validate(SubmissionPolicy.strictInt(obj.opt(key)) ?: error("图片上传状态无效"), "图片上传失败")
        val info = j.text("image_info", "imageInfo").ifBlank { j.optJSONObject("data")?.text("image_info", "imageInfo").orEmpty() }
        require(info.isNotBlank() && info.toByteArray().size <= 16384 && info.none { it.code < 32 || it.code == 127 }) { "图片上传未返回有效收据" }
        return info
    }
    suspend fun uploadThreadImage(a: Account, image: ByteArray): String {
        require(image.size in 1..10 * 1024 * 1024)
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(image, 0, image.size, options)
        require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80000000) { "图片尺寸无效或过大" }
        val digest = java.security.MessageDigest.getInstance("MD5").digest(image).joinToString("") { "%02X".format(it) }
        val fields = mapOf("BDUSS" to a.bduss, "stoken" to a.stoken, "_client_type" to "2", "_client_version" to "12.35.1.0", "alt" to "json", "chunkNo" to "1", "groupId" to "1", "height" to options.outHeight.toString(), "width" to options.outWidth.toString(), "isFinish" to "1", "is_bjh" to "0", "resourceId" to digest, "saveOrigin" to "1", "support_image" to "jepgwebp")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
            addFormDataPart("chunk", "image_${digest.lowercase()}", image.toRequestBody("application/octet-stream".toMediaType()))
        }.build()
        val response = JSONObject(bytes(Request.Builder().url("$social/c/s/uploadPicture").post(body).header("User-Agent", "tieba/12.35.1.0").header("Cookie", "ka=open").build()).toString(Charsets.UTF_8))
        SubmissionPolicy.clientStatus(response)
        val id = response.text("picId", "pic_id").ifBlank { response.optJSONObject("info")?.text("pic_id", "picId").orEmpty() }
        require(id.matches(Regex("[A-Za-z0-9_-]{1,2048}"))) { "图片上传未返回有效标识" }
        return id
    }
    suspend fun submit(a: Account, forum: String, title: String, text: String, tid: String?, pid: String?, floor: Int, subpost: String?, images: List<String>): String {
        require(forum.isNotBlank() && forum.length <= 100 && text.length <= 40000 && images.size <= 9 && (text.isNotBlank() || images.isNotEmpty()))
        if (tid == null) require(title.isNotBlank()) else Policy.signedId(tid)
        if (pid != null) Policy.signedId(pid)
        if (subpost != null) Policy.signedId(subpost)
        val fid = forumId(forum)
        val token = postingTbs(a)
        val timestamp = System.currentTimeMillis().toString()
        val upload = images.joinToString("|")
        require(upload.toByteArray().size <= 9 * 16384 && upload.none { it.code < 32 || it.code == 127 })
        val fields: Map<String, String>
        val url: String
        val referer: String
        if (tid == null) {
            url = "$base/f/commit/thread/add"
            referer = "$base/f?kw=${java.net.URLEncoder.encode(forum, "UTF-8")}"
            fields = mapOf("ie" to "utf-8", "fid" to fid, "kw" to forum, "tbs" to token, "title" to title.trim(), "content" to SubmissionPolicy.threadContent(text, images), "nick_name" to a.name, "bsk" to timestamp)
        } else {
            url = "$base/mo/q/apubpost?_t=$timestamp"
            referer = "$base/p/$tid?lp=5028&mo_device=1&is_jingpost=0&pn=1&"
            fields = mutableMapOf("co" to text, "_t" to timestamp, "tag" to "11", "upload_img_info" to upload, "fid" to fid, "src" to "1", "word" to forum, "tbs" to token, "z" to tid, "lp" to "6026", "nick_name" to a.name, "_BSK" to timestamp).apply {
                if (pid != null) { put("pid", pid); put("floor", floor.coerceAtLeast(1).toString()) }
                if (subpost != null) put("lzl_id", subpost)
            }
        }
        val j = try { form(url, fields, false, "BDUSS=${a.bduss}; STOKEN=${a.stoken}", webHeaders(referer)) } catch (e: Exception) { throw OutcomeUnknown() }
        return SubmissionPolicy.receipt(j, tid)
    }
}
