package dev.anyu.tiebapure

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun AboutScreen(vm: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = padding.calculateBottomPadding())) {
        GroupHeader("TiebaPure")
        IOSGroup {
            AboutRow("版本", BuildConfig.VERSION_NAME)
            AboutRow("项目作者", "Anyu", blueValue = true)
            Text("以浏览为主的非官方百度贴吧客户端；登录后支持关注、点赞，以及实验性的发帖与回复。与百度公司及贴吧官方无隶属、授权或认可关系。", fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(16.dp))
        }
        GroupHeader("开源与许可")
        IOSGroup {
            AboutRow("查看 TiebaPure 源码", blueValue = true) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Anyu1014/TiebaLite"))) }
            AboutRow("TiebaPure", "GPL-3.0-only") { vm.navigate(Route("license", "LICENSE", "TiebaPure")) }
            AboutRow("SwiftProtobuf", "Apache-2.0") { vm.navigate(Route("license", "licenses/SwiftProtobuf-Apache-2.0.txt", "SwiftProtobuf（上游 iOS 依赖）")) }
            AboutRow("Android 第三方依赖", "许可与版权") { vm.navigate(Route("license", "THIRD_PARTY_NOTICES.md", "Android 第三方依赖")) }
        }
    }
}

@Composable private fun AboutRow(title: String, value: String = "", blueValue: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, color = if (value.isEmpty() && blueValue) IOSLayout.blue else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 17.sp, color = if (blueValue) IOSLayout.blue else MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null && value.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(17.dp))
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}

@Composable fun LicenseScreen(resource: String, padding: PaddingValues) {
    val context = LocalContext.current
    val text = remember(resource) {
        val allowed = setOf("LICENSE", "licenses/SwiftProtobuf-Apache-2.0.txt", "THIRD_PARTY_NOTICES.md")
        if (resource !in allowed) "无法读取许可证"
        else runCatching { context.assets.open(resource).bufferedReader().use { it.readText() } + if (resource == "THIRD_PARTY_NOTICES.md") "\n\nProtobuf — BSD-3-Clause\n\n" + context.assets.open("licenses/Protobuf-BSD-3-Clause.txt").bufferedReader().use { it.readText() } else "" }.getOrElse { "无法读取许可证，请返回后重试" }
    }
    SelectionContainer { Text(text, fontSize = 13.sp, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = padding.calculateBottomPadding())) }
}
