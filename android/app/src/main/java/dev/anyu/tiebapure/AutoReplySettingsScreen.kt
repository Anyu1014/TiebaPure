package dev.anyu.tiebapure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun AutoReplySettingsScreen(vm: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val runtime = remember { AutoReplyRuntime.get(context) }
    val account = vm.account
    if (account == null) { Box(Modifier.padding(padding).padding(24.dp)) { Text("请先登录后配置自动回复") }; return }
    val uid = account.uid
    val revision = runtime.revision
    var rules by remember(uid) { mutableStateOf(emptyList<AutoReplyRule>()) }
    var jobs by remember(uid) { mutableStateOf(emptyList<AutoReplyJob>()) }
    var forums by remember(account) { mutableStateOf(emptyList<ForumEntry>()) }
    var error by remember(uid) { mutableStateOf("") }
    var editing by remember(uid) { mutableStateOf<AutoReplyRule?>(null) }
    var editorOpen by remember(uid) { mutableStateOf(false) }
    var selecting by remember(uid) { mutableStateOf(false) }
    var selected by remember(uid) { mutableStateOf(emptySet<String>()) }
    var deleting by remember(uid) { mutableStateOf<Set<String>?>(null) }
    var deletingHistory by remember(uid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(account) {
        try { val loaded = withContext(Dispatchers.IO) { vm.api.followedForums(account) }; if (account.sessionMatches(vm.account)) forums = loaded }
        catch (failure: Exception) { error = "关注列表读取失败：${failure.message?.take(120)}" }
    }
    LaunchedEffect(uid, revision) {
        try { val loaded = withContext(Dispatchers.IO) { runtime.repository.rules(uid) to runtime.repository.jobs(uid) }; rules = loaded.first; jobs = loaded.second; selected = selected.intersect(jobs.map { it.threadId }.toSet()); if (jobs.isEmpty()) selecting = false }
        catch (failure: Exception) { error = "自动化记录读取失败：${failure.message?.take(120)}" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (account.sessionMatches(vm.account)) {
            if (granted) runtime.configureBackground(uid, true) else error = "未允许通知，后台保活未开启；前台监测仍可使用"
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { GroupHeader("自动回复") }
        item { IOSGroup {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("启用自动回复", Modifier.weight(1f)); IOSSwitch(runtime.enabled(uid), { runtime.configureEnabled(uid, it) }, "启用自动回复")
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("后台保活", Modifier.weight(1f)); IOSSwitch(runtime.repository.background(uid), {
                    if (it && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else runtime.configureBackground(uid, it)
                }, "后台保活")
            }
            Text(runtime.status, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("后台电池设置") }
        } }
        item { Text("前台或保活期间每60秒检查一次，系统可能延迟。后台保活有常驻通知并增加耗电；Android后台同步达到系统时限后会暂停。重新打开后延迟8秒补扫，匹配结果须确认才发送。首次添加仅建立基线，不回复历史帖子。规则、缓存和任务历史按账号隔离，仅保存在本机。", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
        if (error.isNotBlank()) item { Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        item { GroupHeader("已关注贴吧规则") { TextButton(onClick = { editing = null; editorOpen = true }, enabled = forums.isNotEmpty()) { Text("添加") } } }
        if (forums.isEmpty()) item { Text("暂无可选择的已关注贴吧，或关注列表尚未加载。", Modifier.padding(20.dp)) }
        items(rules, key = { it.id }) { rule ->
            var menu by remember(rule.id) { mutableStateOf(false) }
            IOSGroup { Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rule.forum + "吧", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, rule.forum + "规则操作", tint = IOSLayout.blue, modifier = Modifier.rotate(90f)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; editing = rule; editorOpen = true })
                        DropdownMenuItem(text = { Text("移除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; runCatching { runtime.repository.removeRule(uid, rule.id); runtime.changed() }.onFailure { error = it.message.orEmpty() } })
                    }
                }
                IOSSwitch(rule.enabled, { value -> runCatching { runtime.repository.saveRule(uid, rule.copy(enabled = value, revision = java.util.UUID.randomUUID().toString())); runtime.changed() }.onFailure { error = it.message.orEmpty() } }, "启用${rule.forum}自动回复")
            }
            Text(rule.keywords.joinToString("、") + " · 延迟${rule.delaySeconds}秒", style = MaterialTheme.typography.bodySmall)
            NativeEmoticonText(TiebaEmoticon.parts(rule.message), fontSize = 14.sp, maxLines = 3)
        } }; Spacer(Modifier.height(8.dp)) }
        item { GroupHeader("历史任务 · ${jobs.size}") {
            TextButton(onClick = { selecting = !selecting; selected = emptySet() }, enabled = jobs.isNotEmpty() && !deletingHistory) { Text(if (selecting) "取消" else "选择") }
            TextButton(onClick = { deleting = if (selecting) selected else jobs.map { it.threadId }.toSet() }, enabled = !deletingHistory && if (selecting) selected.isNotEmpty() else jobs.isNotEmpty()) { Text(if (selecting) "删除(${selected.size})" else "一键删除", color = if (jobs.isEmpty() || deletingHistory || selecting && selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
        } }
        if (jobs.isEmpty()) item { Text("命中关键词后，待确认、延迟发送和最终结果都会记录在这里。", Modifier.padding(20.dp)) }
        items(jobs, key = { it.threadId }) { job -> IOSGroup { Row(Modifier.padding(16.dp)) {
            if (selecting) Checkbox(checked = job.threadId in selected, onCheckedChange = { checked -> selected = if (checked) selected + job.threadId else selected - job.threadId }, enabled = !deletingHistory, modifier = Modifier.semantics { contentDescription = "选择" + job.title })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${job.forum}吧 · ${job.status.title}", style = MaterialTheme.typography.labelLarge)
            Text(job.title, style = MaterialTheme.typography.titleSmall)
            Text("命中：${job.keywords.joinToString("、")}\n发现：${automationTime(job.discoveredAt)}" + if (job.status == AutoReplyStatus.QUEUED) "\n计划发送：${automationTime(job.dueAt)}" else "", style = MaterialTheme.typography.bodySmall)
            NativeEmoticonText(TiebaEmoticon.parts(job.message), fontSize = 14.sp, maxLines = if (job.status == AutoReplyStatus.REVIEW) Int.MAX_VALUE else 4)
            if (job.note.isNotBlank()) Text(job.note, style = MaterialTheme.typography.bodySmall)
            if (!selecting) Row {
                TextButton(onClick = { vm.navigate(Route("thread", job.threadId, job.title)) }) { Text("打开帖子") }
                if (job.status == AutoReplyStatus.REVIEW) {
                    TextButton(onClick = { runCatching { runtime.engine.review(uid, job.threadId, true) }.onFailure { error = it.message.orEmpty() } }, enabled = runtime.enabled(uid)) { Text("同意回复") }
                    TextButton(onClick = { runCatching { runtime.engine.review(uid, job.threadId, false) }.onFailure { error = it.message.orEmpty() } }) { Text("忽略") }
                }
            }
        } } }; Spacer(Modifier.height(8.dp)) }
    }
    if (editorOpen) AutoReplyRuleEditor(forums, editing, { editorOpen = false }) { rule ->
        check(account.sessionMatches(vm.account)) { "账号已变化" }
        require(forums.any { it.id == rule.forumId }) { "请从已关注贴吧中选择" }
        runtime.repository.saveRule(uid, rule); runtime.changed(); editorOpen = false
    }
    deleting?.let { ids -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除${ids.size}条历史任务？") }, text = { Text("仅删除本机记录并取消尚未开始发送的任务，不会删除贴吧评论。正在发送的请求无法撤回。") }, confirmButton = {
        TextButton(onClick = {
            deleting = null; deletingHistory = true
            scope.launch {
                try { check(account.sessionMatches(vm.account)) { "账号已变化" }; withContext(Dispatchers.IO) { runtime.repository.deleteJobs(uid, ids) }; selected = emptySet(); selecting = false }
                catch (failure: CancellationException) { throw failure }
                catch (failure: Exception) { error = failure.message ?: "删除历史失败" }
                finally { deletingHistory = false }
            }
        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
    }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable private fun AutoReplyRuleEditor(forums: List<ForumEntry>, original: AutoReplyRule?, dismiss: () -> Unit, save: (AutoReplyRule) -> Unit) {
    var selected by remember { mutableStateOf(forums.firstOrNull { it.id == original?.forumId } ?: forums.firstOrNull()) }
    var keywords by remember { mutableStateOf(original?.keywords?.joinToString("\n").orEmpty()) }
    var message by remember { mutableStateOf(original?.message.orEmpty()) }
    var delay by remember { mutableStateOf((original?.delaySeconds ?: 30).toString()) }
    var menu by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var emoticons by remember { mutableStateOf(false) }
    val editor = remember { NativeEmoticonEditorState() }
    Dialog(onDismissRequest = dismiss) { Surface(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (original == null) "添加自动回复规则" else "编辑自动回复规则", style = MaterialTheme.typography.titleMedium)
            Box { TextButton(onClick = { menu = true }) { Text("已关注的吧：${selected?.name ?: "请选择"}") }; DropdownMenu(menu, { menu = false }) { forums.forEach { forum -> DropdownMenuItem(text = { Text(forum.name) }, onClick = { selected = forum; menu = false }) } } }
            OutlinedTextField(keywords, { keywords = it }, label = { Text("关键词，每行一个；任一命中") })
            Text("匹配标题和首楼正文；回复内容", style = MaterialTheme.typography.bodySmall)
            NativeEmoticonEditor(message, { if (it.length <= 10000) message = it }, editor, Modifier.fillMaxWidth().height(160.dp))
            TextButton(onClick = { emoticons = !emoticons }) { Text("贴吧表情") }
            if (emoticons) LazyRow { items(TiebaEmoticon.names.entries.toList()) { (id, label) -> IconButton(onClick = { editor.view?.insertToken("#($label)") }) { coil.compose.AsyncImage("file:///android_asset/tieba-emoticons/image_emoticon$id.png", label, Modifier.size(28.dp)) } } }
            OutlinedTextField(delay, { delay = it.filter(Char::isDigit).take(4) }, label = { Text("发送延迟（0至3600秒）") }, singleLine = true)
            if (errorText.isNotBlank()) Text(errorText, color = MaterialTheme.colorScheme.error)
            Row { TextButton(onClick = dismiss) { Text("取消") }; TextButton(onClick = {
                runCatching { val forum = selected ?: error("请选择贴吧"); val rule = AutoReplyRule(original?.id ?: java.util.UUID.randomUUID().toString(), forum.id, forum.name, AutoReplyRule.keywords(keywords), message, delay.toIntOrNull() ?: -1, original?.enabled ?: true); rule.validate(); save(rule) }.onFailure { errorText = it.message ?: "保存失败" }
            }) { Text("保存") } }
        }
    } }
}

@Composable fun AutoReplyCatchUpPrompt(vm: AppViewModel) {
    val context = LocalContext.current
    val runtime = remember { AutoReplyRuntime.get(context) }
    val uid = vm.account?.uid ?: return
    val revision = runtime.revision
    if (!runtime.enabled(uid) || !runtime.readyForPrompts) return
    var pending by remember(uid) { mutableIntStateOf(0) }
    var dismissed by remember(uid, runtime.reviewEpoch) { mutableStateOf(false) }
    LaunchedEffect(uid, revision, runtime.promptJobIds) { pending = withContext(Dispatchers.IO) { runCatching { runtime.repository.jobs(uid).count { it.status == AutoReplyStatus.REVIEW && it.threadId in runtime.promptJobIds } }.getOrDefault(0) } }
    val visible = pending > 0 && !dismissed && runtime.foreground && !runtime.isScanning && vm.route.kind !in setOf("compose", "login", "automation")
    LaunchedEffect(visible) { if (visible) withContext(Dispatchers.IO) { runCatching { runtime.engine.acknowledgePrompts(uid, runtime.promptJobIds) } } }
    if (visible) AlertDialog(onDismissRequest = { dismissed = true }, title = { Text("补扫发现 $pending 条匹配新帖") }, text = { Text("后台监测曾中断，这些任务不会直接发送。请查看每条帖子的关键词和回复内容后确认。") }, confirmButton = { TextButton(onClick = { dismissed = true; vm.navigate(Route("automation")) }) { Text("查看并确认") } }, dismissButton = { TextButton(onClick = { dismissed = true }) { Text("稍后") } })
}

private fun automationTime(seconds: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date(seconds * 1000))
