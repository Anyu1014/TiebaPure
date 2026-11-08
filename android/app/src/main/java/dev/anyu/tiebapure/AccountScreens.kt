package dev.anyu.tiebapure

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@Composable fun ProfileScreen(vm: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val p = vm.data.profile ?: return
    val context = LocalContext.current
    var copied by remember(p.user.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = padding.calculateBottomPadding())) {
        IOSAvatar(p.user.name, p.user.portrait, Modifier.padding(bottom = 12.dp), diameter = 72.dp)
        Text(p.user.name, style = MaterialTheme.typography.headlineSmall)
        val displayedId = p.tiebaId.ifBlank { p.user.id }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("ID $displayedId", color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("用户ID", displayedId))
                copied = true
            }) { Icon(TiebaIcons.Copy, if (copied) "已复制用户ID" else "复制用户ID", tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(p.intro.ifBlank { "还没有简介" }, Modifier.padding(vertical = 16.dp))
        Row {
            TextButton(onClick = { vm.navigate(Route("following", p.user.id, "关注")) }) { Text("${p.following} 关注") }
            TextButton(onClick = { vm.navigate(Route("followers", p.user.id, "粉丝")) }) { Text("${p.fans} 粉丝") }
            TextButton(onClick = { vm.navigate(Route("userthreads", p.user.id, "用户主题")) }) { Text("${p.threads} 主题") }
        }
        if (p.user.id == vm.account?.uid) Button(onClick = { vm.navigate(Route("editprofile", p.user.id, "编辑资料")) }) { Text("编辑本人资料") }
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (vm.account == null) vm.navigate(Route("login"))
                else vm.navigate(Route("messages", p.user.id, p.user.name, "private"))
            }) { Text("发私信") }
            Button(onClick = { vm.action(onSuccess = { vm.load() }) { a -> vm.api.followUser(a, p.user.portrait, !p.followed); if (p.followed) "已取消关注" else "已关注" } }) { Text(if (p.followed) "取消关注" else "关注用户") }
        }
        Text("关注的贴吧", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        if (p.forumsPrivate) Text("用户未公开关注的贴吧", Modifier.padding(vertical = 12.dp))
        else if (p.forums.isEmpty()) Text("暂无贴吧", Modifier.padding(vertical = 12.dp))
        else p.forums.filterNot { vm.store.blocked(Thread("", "", it.second)) }.forEach { (_, name) ->
            Row(Modifier.fillMaxWidth().clickable { vm.navigate(Route("forum", name)) }.padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ForumAvatar(vm, name); Spacer(Modifier.width(12.dp)); Text("${name}吧")
            }
        }
    }
}

@Composable fun RelationshipScreen(vm: AppViewModel, padding: PaddingValues = PaddingValues()) {
    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
        if (vm.data.users.isEmpty() && !vm.loading && vm.error.isBlank()) item { EmptyMessage("暂无用户") }
        items(vm.data.users, key = { it.id }) { u ->
            Row(Modifier.fillMaxWidth().clickable { vm.navigate(Route("profile", u.id, u.name)) }.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IOSAvatar(u.name, u.portrait); Spacer(Modifier.width(12.dp)); Text(u.name, modifier = Modifier.weight(1f)); Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(start = 60.dp))
        }
        if (vm.data.hasMore) item { AutomaticPageFooter(vm) }
    }
}

@Composable fun EditProfileScreen(vm: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val p = vm.data.profile ?: return
    if (p.user.id != vm.account?.uid) { Text("只能编辑自己的资料"); return }
    var name by remember(p.user.id) { mutableStateOf(p.user.name) }
    var intro by remember(p.user.id) { mutableStateOf(p.intro) }
    var gender by remember(p.user.id) { mutableIntStateOf(p.gender.coerceIn(0, 2)) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = padding.calculateBottomPadding())) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("昵称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = intro, onValueChange = { intro = it }, label = { Text("简介") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), minLines = 3)
        Row { listOf(0 to "不更改", 1 to "男", 2 to "女").forEach { (v, label) -> FilterChip(selected = gender == v, onClick = { gender = v }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp)) } }
        Button(onClick = { confirm = true }, enabled = name.isNotBlank()) { Text("保存到账号") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("修改百度账号资料？") }, text = { Text("此操作会修改账号昵称、简介和你选择的性别。") }, confirmButton = { TextButton(onClick = { confirm = false; vm.modifyProfile(name, intro, gender) }) { Text("确认保存") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable fun ComposerScreen(vm: AppViewModel) {
    val account = vm.account ?: run { EmptyMessage("请先登录"); return }
    val route = vm.route
    val target = remember(route) { JSONObject(route.extra) }
    val newThread = !target.has("tid")
    val draftKey = remember(account.uid, route) { account.uid + ":" + java.security.MessageDigest.getInstance("SHA-256").digest((route.id + route.extra).toByteArray()).joinToString("") { "%02x".format(it) } }
    val draft = remember(draftKey) { vm.store.get("draft", draftKey) }
    var title by remember(draftKey) { mutableStateOf(draft?.optString("title").orEmpty()) }
    val emoticonEditor = remember(draftKey) { NativeEmoticonEditorState() }
    var showEmoticons by remember { mutableStateOf(false) }
    var showsMentions by remember { mutableStateOf(false) }
    var text by remember(draftKey) { mutableStateOf(draft?.optString("text").orEmpty()) }
    var images by remember(draftKey) { mutableStateOf(draft?.optJSONArray("images")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun save(): Boolean {
        return try {
            if (text.isBlank() && title.isBlank() && images.isEmpty()) vm.store.remove("draft", draftKey)
            else vm.store.put("draft", draftKey, JSONObject().put("title", title).put("text", text).put("images", JSONArray(images)).put("account", account.uid).put("forum", route.id).put("target", route.extra))
            true
        } catch (e: Exception) { vm.report("草稿未能保存，请检查本机空间；当前输入仍保留在编辑器中"); false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        images = (images + uris.map { it.toString() }).distinct().take(9)
        save()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("${route.id}吧 · ${account.name}", style = MaterialTheme.typography.bodySmall)
        if (newThread) OutlinedTextField(value = title, onValueChange = { if (it.length <= 4096) { title = it; save() } else vm.report("标题过长") }, label = { Text("标题，最多 64 字") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        NativeEmoticonEditor(text, { if (it.length <= 40000) { text = it; save() } else vm.report("正文过长") }, emoticonEditor, Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Row {
            TextButton(onClick = { showEmoticons = !showEmoticons }) { Text(if (showEmoticons) "收起表情" else "贴吧表情") }
            TextButton(onClick = { showsMentions = true }, enabled = !vm.submitting) { Text("@ 艾特") }
        }
        if (showsMentions) FollowedPrivateRecipientPicker(vm, account, onDismiss = { showsMentions = false }, onSelected = { user ->
            emoticonEditor.view?.insertToken("@" + user.mentionName + " "); showsMentions = false
        })
        if (showEmoticons) androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().height(52.dp)) {
            items(TiebaEmoticon.names.entries.toList()) { (id, label) ->
                IconButton(onClick = { emoticonEditor.view?.insertToken("#($label)") }) {
                    coil.compose.AsyncImage("file:///android_asset/tieba-emoticons/image_emoticon$id.png", "插入$label" , Modifier.size(30.dp))
                }
            }
        }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = images.size < 9 && !vm.submitting) { Text("选择图片 ${images.size}/9") }
        images.forEachIndexed { index, uri -> Row(Modifier.fillMaxWidth()) { Text("图片 ${index + 1}", Modifier.weight(1f)); TextButton(onClick = { images = images - uri; save() }) { Text("移除") } } }
        Text("正文自动保存在本机草稿中。发布与回复通过非官方接口，可能触发贴吧风控。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { if (save()) confirm = true }, enabled = !vm.submitting && (text.isNotBlank() || images.isNotEmpty()) && (!newThread || title.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (vm.submitting) "正在提交…" else "发送") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认发送？") }, text = { Text("将以当前百度账号公开发布内容。遇到结果未知时，请先刷新确认，避免重复发送。") }, confirmButton = { TextButton(onClick = { confirm = false; vm.submitDraft(title, text, images, draftKey) }) { Text("确认发送") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable fun UserSearchScreen(vm: AppViewModel) {
    var word by remember { mutableStateOf(vm.route.id) }
    var submitted by remember { mutableStateOf(vm.route.id) }
    var users by remember { mutableStateOf(emptyList<User>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted) {
        users = emptyList(); error = null
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true
        try { users = vm.api.searchUsers(submitted, vm.account) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(word, { if (it.length <= 500) word = it }, label = { Text("昵称或主页 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TextButton(onClick = { submitted = word.trim() }) { Text("搜索用户") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        androidx.compose.foundation.lazy.LazyColumn {
            items(users.size) { i -> val user = users[i]
                TextButton(onClick = { vm.navigate(Route("user", user.id, user.name)) }) {
                    IOSAvatar(user.name, user.portrait, diameter = 44.dp); Spacer(Modifier.width(12.dp)); Text(user.name)
                }
            }
        }
    }
}
