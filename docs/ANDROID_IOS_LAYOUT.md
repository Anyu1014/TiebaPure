# Android 的 iOS 布局与 Anyu 磨砂移植

2026-10-26。按用户指定，布局参照本工作区当前 iOS 源码及用户提供的截图，材质使用 Anyu/Velorah 的磨砂风格。本次只修改 TiebaPure，Anyu 源项目只读，没有复制其账号、配置、服务器代码或私有数据。

## 已实现的布局

| 区域 | iOS 对照 | Android 实现 |
| --- | --- | --- |
| 主要导航 | 居中标题、44pt导航高度、圆形搜索/返回 | `FrostedAppShell`，44dp导航，17sp标题，圆形44dp操作区域 |
| 底栏 | 280pt宽胶囊，图标23pt、标签11pt，按钮最小54pt高 | 280dp宽、62dp高；覆盖于内容上，不保留整条底色；首页重复点击返回顶部/刷新 |
| 内容范围 | 内容延伸到屏幕底边，末尾保留滚动余量 | 取消Scaffold底部占位，列表视口铺到底；内容内设置112dp余量；键盘出现时同步调整底栏与滚动余量 |
| 首页卡片 | 横向12、卡间12、圆角8；卡内横向16/纵向12 | 对齐对应dp值；贴吧头像/名称、作者/时间、更多菜单、标题与摘要、媒体、评论与点赞数 |
| 字体与图片 | 标题/摘要最多2行，紧凑模式单图容器2:1、多图3:1 | 对齐行数、容器比例及8dp图片间距；最多3图，更多数量角标；未知点赞数不编造为0 |
| 进吧 | 打开贴吧、最近浏览、关注贴吧分组，自适应网格 | 44dp输入行，52dp头像，76dp最小网格宽度；管理/清空/单项移除；已关注吧长按签到 |
| 我的 | 账号、浏览、应用三组 | 游客登录提示；登录后头像/UID、消息/关注；帖子收藏、历史、设置、关于；保存/草稿/屏蔽移到相应设置入口 |
| 设置 | 显示模式、内容、签到和账号分组 | iOS式列表、开关和分组；回帖默认关闭，发帖/点赞默认开启，与iOS设置默认值一致；关闭发送时保留草稿 |
| 阅读设置 | 独立页面承载字体、字号、媒体设置 | 系统/导入字体、四档字号预览、媒体加载及现有Android草稿/备份入口；仍保留原有Android能力边界 |
| 帖子 | 贴吧标题胶囊、收藏/搜索/更多、作者行、回复/排序 | 贴吧头像和标题、收藏状态、搜索本吧、菜单；全部回复/只看楼主与热门/正序/倒序；帖子页隐藏底栏 |
| 提示 | 不因全屏内容而遮挡状态 | 加载、保存/取消及错误/重试位于顶栏下方独立浮层；正文仍铺到底 |

文件：`android/app/src/main/java/dev/anyu/tiebapure/FrostedChrome.kt`、`IOSRootScreens.kt`、`IOSSettingsScreen.kt`、`ReaderDateText.kt`、`MainActivity.kt`。日期、贴吧头像、点赞/收藏元数据保存兼容旧记录；协议schema未改，热门排序使用现有schema的`r=2`（iOS原实现同值）。

紧凑媒体布局按 `MediaGridView.usesCompactFeedLayout` 的实际执行分支移植；该分支不会采用外层传入的180/118高度上限。

## 磨砂来源与实现

来源为本机 `/Users/y/Downloads/DSH/Mac工作区/velorah-hero/src/index.css` 中 `.auth-shell--glass .auth-card` / `.portal-shell--glass .portal-card` 的 `blur(20px) saturate(120%)`、半透明底色、内侧高光及阴影。没有把CSS代码直接塞进原生界面。

原生Compose先把正文记录到独立GraphicsLayer，再在顶栏/胶囊局部采样，通过RenderEffect进行20dp模糊与1.2饱和度处理，最后绘制半透明色与清晰前景。正文和导航层分离，避免把文字图标一起模糊，或递归采样自己的玻璃。浅色使用白色0.42透明度；暗色使用Anyu的深色基底并加深至0.72以保证对比度。整块顶栏不加胶囊描边与浮动阴影，浮动胶囊保留高光描边和阴影。

Android12/API31及以上使用实际GPU背景模糊；API26–30使用实色磨砂外观降级，未宣称旧系统也支持相同GPU效果。未新增第三方库或下载SDK/工具。

## 验证证据

- `logs/android-ios-layout-final-build.log`：Debug构建、Lint成功。单测16项通过（Policy7、Submission5、Presentation4），默认跳过线上测试。
- `logs/android-layout-live-smoke.log`：独立启用后，真实匿名首页→帖子→热门回复→搜索→贴吧列表通过。没有登录或提交真实账号写操作。
- `logs/android-layout-storage-tests.log`：隔离命名空间的5项存储设备测试通过；包含加密/备份排除、坏备份、大正文及校验。
- `logs/android-ui-checks.json`：专用Android35 ARM64 AVD实测。1080×2400、density2.625，列表视口为`[0,0,1080,2400]`；胶囊测量约279.62dp，符合280dp的像素取整；单图示例932×466，比例2:1。
- `logs/android-ui-driver.log` / `android-ui-*.xml/png`：实际切换根页、设置/阅读页、浅深色、帖子排序；关闭“允许发帖”时，在登录/发布之前拦截，之后恢复测试前状态。测试只操作专用AVD本机偏好。
- 右侧本地预览页（`.cache/previews/`，截图类临时产物，不随仓库分发）中，Android 展示原生模拟器截图；iOS 仍是布局示意，不冒充原生iOS运行结果。

验证包在 `/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build/outputs/apk/debug/app-debug.apk`，使用Debug签名，仅在专用AVD安装。工具：JDK21.0.12.1、Gradle8.14.3、AGP8.13.0、Kotlin2.2.20、Compose1.9.2、SDK/BuildTools36、Emulator37.2.12（API35）。本轮没有新装组件或新增依赖。

## 尚不能作出的结论

没有本次iOS版本的同尺寸运行截图，本机仍缺少iOS模拟器运行时，因此不能把源码尺寸对齐、Android设备检查写成“100%逐像素一致”。系统状态栏、字体字形、平台图标、原生系统弹层仍有平台差异。Android阅读选项和全部账号/编辑表单尚未达到iOS所有功能项的一比一覆盖，真实登录后的页面、TalkBack、平板和API26/36设备矩阵未完整验收。

本轮改动尚未更新 `releases/` 中此前交付的Release APK、未签名IPA和源码zip。没有发布、上传或安装用户真机；后续打包应使用本轮工作区代码并重新产生对应源码和校验值。

## 1.0/build61 后续更新

关于页显示TiebaPure/1.0/Anyu，GPL项目条目也显示TiebaPure，源码链接本仓库。新增顶部下拉刷新与首页返回缓存；评论区使用头像、等级、右侧点赞、日期地区和楼中楼灰色卡片。独立wire fixture及旧离线备份兼容检查位于CommentPresentationTest；预览数据缺失时不伪造等级、地区或点赞数。iOS26/27最终采用系统原生标签栏，旧版自定义胶囊尺寸不再是新版原生栏的像素验收指标。
