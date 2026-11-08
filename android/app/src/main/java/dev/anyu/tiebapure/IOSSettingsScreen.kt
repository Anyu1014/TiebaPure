package dev.anyu.tiebapure

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable private fun SettingsFooter(text: String) {
    Text(text, Modifier.padding(horizontal = 36.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable private fun SettingsToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, modifier = Modifier.weight(1f)); IOSSwitch(checked, onChange, label = title)
    }
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}

@Composable fun IOSSettingsScreen(vm: AppViewModel, padding: PaddingValues, onLogout: () -> Unit) {
    var automatic by remember { mutableStateOf(vm.store.prefs.getBoolean("auto-sign", false)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { GroupHeader("显示模式") { Text(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "深色" else "浅色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { IOSGroup {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setSettings(theme = value) }.padding(horizontal = 16.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f)); if (vm.darkMode == value) Icon(Icons.Default.Check, "已选择$label", tint = IOSLayout.blue, modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
        } }
        item { SettingsFooter("选择后会立即应用；跟随系统会随手机的外观设置自动切换。") }
        item { GroupHeader("底栏显示") }
        item { IOSGroup {
            SettingsToggle("底栏自动收缩", vm.bottomBarAutoCollapse) { vm.setBottomBarPreferences(autoCollapse = it) }
            SettingsToggle("显示底栏文字", vm.bottomBarShowsLabels) { vm.setBottomBarPreferences(showsLabels = it) }
            Column(Modifier.padding(16.dp)) {
                Text("底栏收缩后位置", fontSize = 17.sp)
                Spacer(Modifier.height(12.dp))
                IOSSegmentedControl(BottomBarPosition.entries.map { it to it.title }, vm.bottomBarPosition, { vm.setBottomBarPreferences(position = it) }, Modifier.fillMaxWidth())
            }
        } }
        item { GroupHeader("内容") }
        item { IOSGroup {
            SettingsToggle("允许发帖", vm.newThreadsEnabled) { vm.setContentPreferences(newThreads = it) }
            SettingsToggle("允许回帖", vm.repliesEnabled) { vm.setContentPreferences(replies = it) }
            SettingsToggle("允许点赞", vm.likesEnabled) { vm.setContentPreferences(likes = it) }
            IOSRow("阅读设置", Icons.Default.Edit) { vm.navigate(Route("readingSettings")) }
            IOSRow("本地保存的帖子", Icons.Default.AccountBox) { vm.navigate(Route("saved")) }
            IOSRow("屏蔽设置", Icons.Default.Clear) { vm.navigate(Route("block")) }
        } }
        item { SettingsFooter("发帖和回帖使用非官方实验接口，可能触发贴吧风控。关闭点赞后仍显示数量；设置和屏蔽规则仅保存在本机。") }
        if (vm.account != null) {
            item { GroupHeader("自动化") }
            item { IOSGroup {
                SettingsToggle("自动签到", automatic) { automatic = it; vm.store.prefs.edit().putBoolean("auto-sign", it).apply() }
                IOSRow("立即签到", Icons.Default.Check) { vm.signAll() }
                IOSRow("自动回复", TiebaIcons.Comment) { vm.navigate(Route("automation")) }
            } }
            item { SettingsFooter("每天第一次打开应用时，为关注的贴吧签到。") }
            item { GroupHeader("账号") }
            item { IOSGroup { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IOSAvatar(vm.account!!.name, vm.account!!.portrait, diameter = 48.dp); Spacer(Modifier.width(12.dp))
                Column { Text(vm.account!!.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("UID ${vm.account!!.uid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } } }
            item { Spacer(Modifier.height(20.dp)); IOSGroup { TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录", color = Color(0xffff3b30), fontSize = 17.sp) } } }
        }
    }
}

@Composable fun IOSReadingSettingsScreen(vm: AppViewModel, padding: PaddingValues, export: () -> Unit, restore: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var fontMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { GroupHeader("字体") }
        item { IOSGroup {
            Box {
                Row(Modifier.fillMaxWidth().clickable { fontMenu = true }.padding(horizontal = 16.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("字体", fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(vm.fonts.firstOrNull { it.file == vm.fontStore.selected() }?.name ?: "系统", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                    Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(fontMenu, { fontMenu = false }) {
                    DropdownMenuItem(text = { Text("系统") }, onClick = { fontMenu = false; runCatching { vm.fontStore.select(""); vm.refreshFonts() }.onFailure { vm.report("字体选择失败") } })
                    vm.fonts.forEach { font -> DropdownMenuItem(text = { Text(font.name) }, onClick = { fontMenu = false; runCatching { vm.fontStore.select(font.file); vm.refreshFonts() }.onFailure { vm.report("字体选择失败") } }) }
                }
            }
            IOSRow("导入字体", Icons.Default.Add) { (context as? MainActivity)?.chooseFont() }
            vm.fonts.forEach { font -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.fontStore.select(font.file); vm.refreshFonts() }, modifier = Modifier.weight(1f)) { Text(font.name, fontSize = 17.sp) }
                IconButton(onClick = { vm.fontStore.remove(font); vm.refreshFonts() }) { Icon(Icons.Default.Delete, "移除字体") }
            } }
        } }
        item { SettingsFooter("导入包含中文字形的 TTF 或 OTF。字体只保存在本机应用私有目录。") }
        item { GroupHeader("正文") }
        item { IOSGroup { Column(Modifier.padding(16.dp)) {
            IOSSegmentedControl(listOf(15.3f to "小", 17f to "标准", 19.04f to "大", 21.25f to "特大"), vm.fontSize, { vm.setSettings(size = it) }, Modifier.fillMaxWidth())
            Text("阅读设置会应用到主贴、楼层回复和楼中楼正文。", Modifier.padding(top = 16.dp), fontSize = vm.fontSize.sp, lineHeight = (vm.fontSize * 1.4f).sp)
        } } }
        item { GroupHeader("回复") }
        item { IOSGroup { Box(Modifier.padding(16.dp)) { IOSSegmentedControl(listOf(2 to "热门", 0 to "正序", 1 to "倒序"), vm.defaultReplySort, vm::selectDefaultReplySort, Modifier.fillMaxWidth()) } } }
        item { SettingsFooter("只影响新打开的帖子；恢复上次阅读位置时会使用正序定位。") }
        item { GroupHeader("媒体") }
        item { IOSGroup { SettingsToggle("手动加载媒体", vm.manualMedia) { vm.setSettings(media = it) } } }
        item { GroupHeader("本机内容") }
        item { IOSGroup {
            IOSRow("本机草稿", Icons.Default.Edit) { vm.navigate(Route("drafts", title = "本机草稿")) }
            IOSRow("导出本地备份", Icons.Default.Share, onClick = export)
            IOSRow("合并导入本地备份", Icons.Default.Add, onClick = restore)
        } }
        item { SettingsFooter("备份包含历史、阅读页码、本地正文与屏蔽；不包含账号凭据、草稿、字体和离线媒体。") }
    }
}
