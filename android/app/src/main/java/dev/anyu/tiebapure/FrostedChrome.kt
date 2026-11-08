package dev.anyu.tiebapure

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

object IOSLayout {
    val blue = Color(0xff007aff)
    val background = Color(0xfff2f2f7)
    val barWidth = 280.dp
    val barHeight = 62.dp
    val navigationHeight = 56.dp
    val cardRadius = 24.dp
    val groupRadius = 26.dp
    val innerRadius = 16.dp
    val cardGap = 12.dp
    val feedInset = 12.dp
    val cardInset = 16.dp
    val scrollClearance = 112.dp
    const val blur = 20f
    const val saturation = 1.2f
}

internal data class AnyuBackdrop(val layer: GraphicsLayer, val origin: Offset)
internal val LocalAnyuBackdrop = staticCompositionLocalOf<AnyuBackdrop?> { null }

/** Anyu src/index.css .auth-shell--glass: blur(20px) saturate(120%), tint .42, highlight .16. */
@Composable fun AnyuFrostedSurface(
    backdrop: GraphicsLayer,
    origin: Offset,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    floating: Boolean = true,
    tintOverride: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val effect = remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            val matrix = android.graphics.ColorMatrix().apply { setSaturation(IOSLayout.saturation) }
            android.graphics.RenderEffect.createChainEffect(
                android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix)),
                android.graphics.RenderEffect.createBlurEffect(IOSLayout.blur * density.density, IOSLayout.blur * density.density, android.graphics.Shader.TileMode.CLAMP)
            ).asComposeRenderEffect()
        } else null
    }
    // shortcut: API26–30 use a solid frost tint; GPU backdrop blur requires API31.
    val tint = if (effect == null) (tintOverride ?: MaterialTheme.colorScheme.surface).copy(alpha = 1f) else tintOverride ?: if (dark) Color(0xff080f16).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.42f)
    Box(modifier.then(if (floating) Modifier.shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f)) else Modifier).then(if (floating) Modifier.clip(shape) else Modifier).onGloballyPositioned { val screen = it.localToScreen(Offset.Zero); if (screen.x.isFinite() && screen.y.isFinite()) position = screen }) {
        if (effect != null) Canvas(Modifier.matchParentSize().clip(shape).graphicsLayer { renderEffect = effect }) {
            translate(origin.x - position.x, origin.y - position.y) { drawLayer(backdrop) }
        }
        Box(Modifier.matchParentSize().background(tint, shape))
        if (floating) Box(Modifier.matchParentSize().border(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.15f))), shape))
        content()
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable fun FrostedAppShell(
    title: String,
    root: String,
    showTabs: Boolean,
    showBack: Boolean,
    showSearch: Boolean,
    hasActions: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onTab: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    snackbar: @Composable () -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
    feedback: @Composable () -> Unit = {},
    startAlignedTitle: Boolean = false,
    titleTrailingInset: Dp = 96.dp,
    tabPosition: BottomBarPosition = BottomBarPosition.LEFT,
    showTabLabels: Boolean = true,
    autoCollapseTabs: Boolean = true,
    account: Account? = null,
    scrollKey: String = root,
    content: @Composable (PaddingValues) -> Unit
) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), WindowInsets.ime.asPaddingValues().calculateBottomPadding())
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val toolbarButtonTint = (if (dark) Color(0xff080f16) else Color.White).copy(alpha = 0.9f)
    val navigationHeight = IOSLayout.navigationHeight
    val motion = remember(scrollKey, account?.uid, autoCollapseTabs) { BottomBarMotion() }
    var collapsed by remember(motion) { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val input = remember(motion) { BottomBarPanInput(motion) }
    val touchObserver = remember(input, density) { BottomBarTouchObserver(input, density) { collapsed = it } }
    DisposableEffect(touchObserver) { onDispose { touchObserver.close() } }
    CompositionLocalProvider(LocalAnyuBackdrop provides AnyuBackdrop(layer, origin)) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).semantics { testTagsAsResourceId = true }) {
        Box(Modifier.fillMaxSize().motionEventSpy { event -> if (showTabs && autoCollapseTabs) touchObserver.observe(event) else touchObserver.close() }.onGloballyPositioned { val screen = it.localToScreen(Offset.Zero); if (screen.x.isFinite() && screen.y.isFinite()) origin = screen }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) {
            content(PaddingValues(top = status + navigationHeight, bottom = if (showTabs) maxOf(IOSLayout.scrollClearance, bottom + IOSLayout.barHeight + 16.dp) else bottom + 12.dp))
        }
        AnyuFrostedSurface(layer, origin, Modifier.fillMaxWidth().testTag("top-frosted-bar"), RoundedCornerShape(0.dp), floating = false, tintOverride = MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.72f else 0.42f)) {
            Box(Modifier.fillMaxWidth().height(status + navigationHeight).padding(top = status, bottom = 4.dp)) {
                if (titleContent == null) Text(title, if (startAlignedTitle) Modifier.align(Alignment.CenterStart).padding(start = 52.dp, end = titleTrailingInset).testTag("forum-title") else Modifier.align(Alignment.Center).padding(horizontal = if (showBack) 96.dp else 64.dp).testTag("toolbar-title"), fontSize = if (startAlignedTitle) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Box(Modifier.align(Alignment.CenterStart).padding(start = 52.dp, end = 148.dp).testTag("toolbar-forum-pill")) { titleContent() }
                if (showBack) Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(48.dp).testTag("toolbar-back-glass"), contentAlignment = Alignment.Center) {
                    AnyuFrostedSurface(layer, origin, Modifier.size(36.dp).testTag("toolbar-back-background"), CircleShape, tintOverride = toolbarButtonTint) {}
                    IconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) { Icon(TiebaIcons.Back, "返回", modifier = Modifier.size(18.dp)) }
                }
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showSearch) Box(Modifier.size(48.dp).testTag("toolbar-search-glass"), contentAlignment = Alignment.Center) {
                        AnyuFrostedSurface(layer, origin, Modifier.size(36.dp).testTag("toolbar-search-background"), CircleShape, tintOverride = toolbarButtonTint) {}
                        IconButton(onClick = onSearch, modifier = Modifier.fillMaxSize()) { Icon(Icons.Default.Search, "搜索", modifier = Modifier.size(18.dp)) }
                    }
                    if (hasActions) Box(Modifier.testTag("toolbar-actions-glass")) {
                        AnyuFrostedSurface(layer, origin, Modifier.matchParentSize().padding(horizontal = 4.dp, vertical = 6.dp).testTag("toolbar-actions-background"), CircleShape, tintOverride = toolbarButtonTint) {}
                        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = status + navigationHeight).fillMaxWidth()) { feedback() }
        if (showTabs) BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = bottom + 8.dp)) {
            val expandedWidth = minOf(IOSLayout.barWidth, maxWidth)
            val progress by animateFloatAsState(if (collapsed) 1f else 0f, spring(dampingRatio = 1f, stiffness = 380f, visibilityThreshold = 0.001f), label = "bottom-bar-morph")
            val transition = BottomBarTransition(progress)
            val fraction = transition.progress
            val compactOffset by animateDpAsState(((maxWidth - IOSLayout.barHeight) / 2) * tabPosition.fraction, spring(dampingRatio = 1f, stiffness = 380f), label = "bottom-bar-position")
            val width = expandedWidth + (IOSLayout.barHeight - expandedWidth) * fraction
            AnyuFrostedSurface(layer, origin, Modifier.align(Alignment.Center).offset(x = compactOffset * fraction).width(width).height(IOSLayout.barHeight).testTag("floating-root-tab-bar")) {
            Row(Modifier.align(Alignment.Center).requiredWidth(expandedWidth).padding(4.dp).height(IOSLayout.barHeight - 8.dp).graphicsLayer { alpha = transition.expandedOpacity; scaleX = 1f - 0.06f * fraction; scaleY = scaleX }.then(if (collapsed) Modifier.clearAndSetSemantics {} else Modifier)) {
                listOf(Triple("home", "首页", Icons.Default.Home), Triple("forums", "进吧", Icons.Default.List), Triple("me", "我的", Icons.Default.AccountCircle)).forEach { (kind, label, icon) ->
                    val selected = root == kind
                    Column(Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(50)).background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent).selectable(selected = selected, enabled = !collapsed, role = Role.Button, onClick = { onTab(kind) }).then(if (!showTabLabels) Modifier.semantics { contentDescription = label } else Modifier).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val color = if (selected) IOSLayout.blue else MaterialTheme.colorScheme.onSurface
                        val iconSize = if (showTabLabels) 23.dp else 29.dp
                        if (kind == "forums") Canvas(Modifier.size(iconSize)) {
                            val gap = 2.dp.toPx(); val edge = (size.width - gap) / 2
                            for (x in 0..1) for (y in 0..1) drawRoundRect(color, Offset(x * (edge + gap), y * (edge + gap)), androidx.compose.ui.geometry.Size(edge, edge), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        } else Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
                        if (showTabLabels) {
                            Spacer(Modifier.height(3.dp))
                            Text(label, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) IOSLayout.blue else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.Center).size(IOSLayout.barHeight).graphicsLayer { alpha = transition.avatarOpacity; scaleX = 0.88f + 0.12f * fraction; scaleY = scaleX }.then(if (collapsed) Modifier.clickable(role = Role.Button) { motion.expand(); collapsed = false }.semantics { contentDescription = "展开底栏" }.testTag("collapsed-root-tab-avatar") else Modifier.clearAndSetSemantics {}), contentAlignment = Alignment.Center) {
                if (account == null) Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                else IOSAvatar(account.name, account.portrait, diameter = 44.dp)
            }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (showTabs) bottom + 84.dp else bottom + 12.dp)) { snackbar() }
    }
    }
}

@Composable fun <T> IOSSegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(IOSLayout.innerRadius)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(2.dp)) {
        options.forEach { (value, label) -> Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected == value) MaterialTheme.colorScheme.surface else Color.Transparent).selectable(selected == value, role = Role.Tab, onClick = { onSelect(value) }).heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        } }
    }
}

@Composable fun IOSSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String? = null) {
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switch")
    Box(Modifier.sizeIn(minWidth = 51.dp, minHeight = 44.dp).semantics { if (label != null) contentDescription = label }.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(if (checked) IOSLayout.blue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))) {
            Box(Modifier.offset(x = offset, y = 2.dp).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}
