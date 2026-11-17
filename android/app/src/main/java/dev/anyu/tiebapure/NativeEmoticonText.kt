package dev.anyu.tiebapure

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

@Composable fun NativeEmoticonText(
    blocks: List<Block>, modifier: Modifier = Modifier, fontSize: TextUnit = 17.sp,
    color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    prefix: String = "", onUser: ((String, String) -> Unit)? = null
) {
    val normalized = remember(blocks) { blocks.flatMap(TiebaEmoticon::normalize) }
    val context = LocalContext.current
    val nativeNames = remember { context.assets.list("tieba-emoticons").orEmpty().toSet() }
    val inline = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        append(prefix)
        normalized.forEachIndexed { index, b ->
            val name = if (b.type == 2) TiebaEmoticon.imageName(b.text) ?: TiebaEmoticon.nameFromURL(b.url) else null
            when {
                name != null -> {
                    val key = "emoticon-$index"
                    appendInlineContent(key, "[${TiebaEmoticon.label(name)}]")
                    inline[key] = InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                        val bundled = remember(name) { if ("$name.png" in nativeNames) runCatching { context.assets.open("tieba-emoticons/$name.png").use(android.graphics.BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull() else null }
                        if (bundled != null) Image(bundled, TiebaEmoticon.label(name), Modifier.fillMaxSize())
                        else AsyncImage(OfflineLibrary.safeFile(context, b.localFile) ?: TiebaEmoticon.url(name), TiebaEmoticon.label(name), Modifier.fillMaxSize())
                    }
                }
                b.type == 1 && Policy.webpage(b.url) != null -> withLink(LinkAnnotation.Url(b.url)) { append(b.text.ifBlank { b.url }) }
                b.type == 4 && b.url.startsWith("user:") && onUser != null -> withLink(LinkAnnotation.Clickable(b.url) { onUser(b.url.removePrefix("user:"), b.text) }) { append(b.text) }
                else -> append(b.text.ifBlank { if (b.type in setOf(3, 20)) "[图片]" else if (b.type in setOf(5, 10)) "[媒体]" else "" })
            }
        }
    }
    Text(text, modifier = modifier, inlineContent = inline, fontSize = fontSize, lineHeight = fontSize * 1.4f,
        fontWeight = fontWeight, fontFamily = fontFamily, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable fun ContentBlocksView(blocks: List<Block>, vm: AppViewModel) {
    val normalized = remember(blocks) { blocks.flatMap(TiebaEmoticon::normalize) }
    val singleImage = normalized.count { it.type in setOf(3, 20) } == 1
    var start = 0
    for (index in 0..normalized.size) {
        val media = index < normalized.size && normalized[index].type in setOf(3, 20, 5, 10)
        if (index == normalized.size || media) {
            if (index > start) NativeEmoticonText(normalized.subList(start, index), fontSize = vm.fontSize.sp,
                fontFamily = vm.readerTypeface?.let(::FontFamily), onUser = { id, name -> if (id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", id, name)) })
            if (media) ContentBlockView(normalized[index], vm, singleImage)
            start = index + 1
        }
    }
}
