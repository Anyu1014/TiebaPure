package dev.anyu.tiebapure

import dev.anyu.tiebapure.official.OfficialIM as IM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// The user approved this fixed official WS endpoint; credentials are sent only after encrypted key confirmation.
internal class OfficialPrivateTransport private constructor(private val socket: Socket, private val key: ByteArray) : Closeable {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())
    var groups: List<IM.Group> = emptyList(); private set
    private var sequence = 0
    private lateinit var onlineData: IM.OnlineData
    override fun close() { key.fill(0); socket.close() }
    private fun send(data: ByteArray, opcode: Int = 2) {
        require(data.size <= LIMIT)
        val mask = ByteArray(4).also(random::nextBytes)
        output.writeByte(128 or opcode)
        when { data.size < 126 -> output.writeByte(128 or data.size)
            data.size <= 65535 -> { output.writeByte(254); output.writeShort(data.size) }
            else -> { output.writeByte(255); output.writeLong(data.size.toLong()) } }
        output.write(mask)
        output.write(ByteArray(data.size) { (data[it].toInt() xor mask[it % 4].toInt()).toByte() })
        output.flush()
    }
    private fun receive(): ByteArray {
        val combined = java.io.ByteArrayOutputStream()
        var started = false
        repeat(256) {
            val first = input.readUnsignedByte(); val second = input.readUnsignedByte()
            require(first and 112 == 0 && second and 128 == 0) { "私信帧格式无效" }
            val opcode = first and 15
            val size = when (val n = second and 127) { 126 -> input.readUnsignedShort().toLong(); 127 -> input.readLong(); else -> n.toLong() }
            require(size in 0..LIMIT.toLong()) { "私信消息过大" }
            val bytes = ByteArray(size.toInt()); input.readFully(bytes)
            when(opcode) {
                8 -> error("官方私信连接已关闭")
                9 -> { require(size <= 125 && first and 128 != 0); send(bytes, 10) }
                10 -> require(size <= 125)
                2, 0 -> {
                    require((opcode == 2 && !started) || (opcode == 0 && started))
                    started = true; require(combined.size() + bytes.size <= LIMIT); combined.write(bytes)
                    if (first and 128 != 0) return combined.toByteArray()
                }
                else -> error("私信帧类型无法识别")
            }
        }
        error("私信响应过多")
    }
    fun request(command: Int, data: ByteArray, encrypted: Boolean = true): ByteArray {
        val seq = ++sequence
        val body = if (encrypted) crypt(data, key, true) else data
        send(ByteBuffer.allocate(9 + body.size).put(if(encrypted) 136.toByte() else 8).putInt(command).putInt(seq).put(body).array())
        repeat(64) {
            val frame = receive(); require(frame.size >= 9)
            val header = ByteBuffer.wrap(frame); val flags = header.get().toInt() and 255
            val cmd = header.int; val correlation = header.int
            var payload = frame.copyOfRange(9, frame.size)
            if (encrypted) require(flags and 128 != 0) { "官方私信未返回加密响应" }
            if (flags and 128 != 0) payload = crypt(payload, key, false)
            if (flags and 64 != 0) payload = java.util.zip.GZIPInputStream(payload.inputStream()).use { stream ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) { val n = stream.read(buffer); if(n < 0) break; require(out.size() + n <= LIMIT); out.write(buffer, 0, n) }; out.toByteArray()
            }
            if (flags and 4 != 0) { require(payload.size >= 4); val n = ByteBuffer.wrap(payload).int; require(n in 0..payload.size-4); payload = payload.copyOfRange(4+n, payload.size) }
            if(cmd == command && correlation == seq) return payload
        }
        error("私信响应无法对应请求")
    }
    companion object {
        internal fun pullRequest(device: String, groups: List<IM.Group>, cursors: List<IM.Group>): IM.PullRequest {
            val known = cursors.associateBy { it.id }
            // Zero is a server sentinel that skips private history, even with a successful response.
            val values = groups.map { IM.GroupCursor.newBuilder().setGroupId(it.id).setLastMsgId((known[it.id]?.lastMsgId ?: 1).coerceAtLeast(1)).build() }
            return IM.PullRequest.newBuilder().setCuid(device).setData(IM.PullData.newBuilder().setGetType("2").setRepair(IM.Repair.newBuilder().setFollowType("0")).addAllGroups(values)).build()
        }
        private const val LIMIT = 4194304
        private val random = SecureRandom()
        private const val PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwQpwBZxXJV/JVRF/uNfyMSdu7YWwRNLM8+2xbniGp2iIQHOikPpTYQjlQgMi1uvq1kZpJ32rHo3hkwjy2l0lFwr3u4Hk2Wk7vnsqYQjAlYlK0TCzjpmiI+OiPOUNVtbWHQiLiVqFtzvpvi4AU7C1iKGvc/4IS45WjHxeScHhnZZ7njS4S1UgNP/GflRIbzgbBhyZ9kEW5/OO5YfG1fy6r4KSlDJw4o/mw5XhftyIpL+5ZBVBC6E1EIiP/dd9AbK62VV1PByfPMHMixpxI3GM2qwcmFsXcCcgvUXJBa9k6zP8dDQ3csCM2QNT+CQAOxthjtp/TFWaD7MzOdsIYb3THwIDAQAB"
        internal fun crypt(data: ByteArray, key: ByteArray, encrypt: Boolean): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(if(encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data)
        }
        internal fun derive(secret: String): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(secret.toCharArray(), byteArrayOf(-92,11,-56,52,-42,-107,-13,19),5,256)).encoded
        private fun connect(socket: Socket, device: String): OfficialPrivateTransport {
            try {
                socket.connect(InetSocketAddress("im.tieba.baidu.com",8000),15000); socket.soTimeout = 15000
                val nonce = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
                val header = "GET / HTTP/1.1\r\nHost: im.tieba.baidu.com:8000\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $nonce\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Extensions: im_version=2.3\r\nContent-Type: application/octet-stream\r\ncuid: $device\r\n\r\n"
                socket.getOutputStream().write(header.toByteArray(Charsets.US_ASCII))
                val response = java.io.ByteArrayOutputStream()
                while(!response.toString("US-ASCII").endsWith("\r\n\r\n")) { require(response.size() < 16384); val b = socket.getInputStream().read(); check(b >= 0); response.write(b) }
                val lines = response.toString("US-ASCII").split("\r\n")
                require(lines.first().startsWith("HTTP/1.1 101 ")) { "官方私信连接失败" }
                val headers = lines.drop(1).filter { it.contains(':') }.associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                require(headers["sec-websocket-accept"] == expected && headers["upgrade"].equals("websocket",true)) { "官方私信握手无效" }
                val secret = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
                val key = derive(secret)
                val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                rsa.init(Cipher.ENCRYPT_MODE,KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY))))
                val wrapped = rsa.doFinal(secret.toByteArray(Charsets.US_ASCII))
                val transport = OfficialPrivateTransport(socket,key)
                val metadata = JSONObject().put("_client_type","2").put("_client_version","22.12.1.0").put("cuid",device).put("pversion","1.0.3").toString()
                val anonymous = IM.OnlineRequest.newBuilder().setCuid(device).setData(IM.OnlineData.newBuilder().setDevice(metadata).setSecretKey(com.google.protobuf.ByteString.copyFrom(wrapped))).build()
                transport.onlineData = anonymous.data
                fun online(bytes: ByteArray): IM.OnlineResponse = IM.OnlineResponse.parseFrom(bytes).also { require(it.hasError()); if(it.error.code != 0) throw PrivateMessageRejected(it.error.code.toLong()) }
                online(transport.request(1001,anonymous.toByteArray(),false))
                online(transport.request(1001,anonymous.toByteArray()))
                return transport
            } catch(e: Exception) { socket.close(); throw e }
        }
        suspend fun <T> session(account: Account, device: String, block: suspend (OfficialPrivateTransport) -> T): T = withContext(Dispatchers.IO) {
            require(Policy.cookie(account.bduss) && Policy.cookie(account.stoken)); require(device.matches(Regex("[a-zA-Z0-9_-]{1,128}")))
            val socket = Socket()
            val deadline = launch(start = CoroutineStart.UNDISPATCHED) {
                try { delay(45_000) } finally { socket.close() }
            }
            try { connect(socket,device).use { transport ->
                coroutineContext.ensureActive()
                val data = transport.onlineData.toBuilder().setBduss(account.bduss).setStoken(account.stoken)
                val request = IM.OnlineRequest.newBuilder().setCuid(device).setData(data).build()
                val response = IM.OnlineResponse.parseFrom(transport.request(1001,request.toByteArray()))
                require(response.hasError()); if(response.error.code != 0) throw PrivateMessageRejected(response.error.code.toLong())
                require(response.data.user.id.toString() == account.uid) { "官方私信登录账号不匹配" }
                transport.groups = response.data.groupsList
                coroutineContext.ensureActive(); block(transport)
            } } finally { deadline.cancel(); socket.close() }
        }
    }
}
