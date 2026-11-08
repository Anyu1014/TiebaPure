package dev.anyu.tiebapure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable internal fun MyLikesScreen(vm: AppViewModel, padding: PaddingValues, scroll: LazyListState) {
    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            listOf("全部", "主帖", "回帖").forEachIndexed { tab, title ->
                FilterChip(selected = vm.route.extra.toIntOrNull().let { (it ?: 0) == tab },
                    onClick = { vm.selectLikesTab(tab) }, label = { Text(title) },
                    modifier = Modifier.padding(end = 8.dp).testTag("my-likes-tab-$tab"))
            }
        }
        Text("贴吧云端记录：2023年1月1日至今", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (vm.account == null) {
            EmptyMessage("登录后可查看贴吧账号的云端点赞记录")
        } else PullRefreshContent(vm, Modifier.weight(1f), 0.dp) {
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("my-likes-list"), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                val rows = vm.data.likes?.items.orEmpty()
                if (rows.isEmpty() && !vm.loading && vm.error.isBlank() && !vm.data.hasMore) item { EmptyMessage("暂无云端点赞记录") }
                items(rows, key = { it.id }) { item ->
                    Column(Modifier.testTag("my-like-${item.id}")) {
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                        ThreadRow(vm, item.preview, vm.fontSize, !vm.manualMedia) { vm.navigate(item.route()) }
                    }
                }
                if (vm.data.hasMore) item { AutomaticPageFooter(vm) }
                if (!vm.data.hasMore && rows.isNotEmpty()) item {
                    Text("已加载全部可返回的云端记录", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
