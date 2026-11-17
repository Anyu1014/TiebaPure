package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tieba.MediaOuterClass.Media
import tieba.Personalized.PersonalizedResponse
import tieba.ThreadInfoOuterClass.ThreadInfo

class HomeFeedImageTest {
    @Test fun feedKeepsThumbnailsAndTrustedFullSizeImagesAligned() = runBlocking {
        val first = Media.newBuilder().setSrcPic("https://imgsa.baidu.com/thumb.jpg").setOriginPic("https://imgsa.baidu.com/original.jpg")
        val skipped = Media.newBuilder().setSrcPic("https://untrusted.invalid/not-a-thumbnail.jpg").setOriginPic("https://imgsa.baidu.com/unused.jpg")
        val second = Media.newBuilder().setSrcPic("https://imgsa.baidu.com/thumb2.jpg").setOriginPic("https://untrusted.invalid/unsafe.jpg").setBigPic("https://imgsa.baidu.com/big2.jpg")
        val item = ThreadInfo.newBuilder().setId(42).addMedia(first).addMedia(skipped).addMedia(second)
        val response = PersonalizedResponse.newBuilder().setData(tieba.Personalized.PersonalizedResponseData.newBuilder().addThreadList(item)).build().toByteArray()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toResponseBody("application/octet-stream".toMediaType())).build() }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val thread = api.home(null, 1).single()
        assertEquals(listOf("https://imgsa.baidu.com/thumb.jpg", "https://imgsa.baidu.com/thumb2.jpg"), thread.previews)
        assertEquals(listOf("https://imgsa.baidu.com/original.jpg", "https://imgsa.baidu.com/big2.jpg"), thread.previewOriginals)
    }
}
