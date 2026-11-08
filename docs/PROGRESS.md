# 当前进展

## 2026-11-08 双端 2.3/build87 签名包

Android 私信输入框只避让一次键盘，评论回复条的右侧四个按钮平分五分之二宽度。双端私信会话按最新活动倒序排列，新增本机一键已读，数字角标按字形居中。修正媒体上传参数并支持带后缀的语音标识。

两份新包已放入 `build/latest/`，旧双端 build86 自动归档到 `build/history/`。Android 115 项单测通过，6 项条件跳过，Release Lint、APK v2 签名及 16 KB 对齐通过。iOS Release 归档、导出、主应用及 Open In 扩展的严格签名和升级兼容性校验通过。iOS 15 项私信宿主测试通过。真实账号媒体收发和 Android 真机键盘仍待验收。校验清单位于 `.cache/release-2.3-build87/manifest.json`。

## 2026-11-08 双端 2.3/build86 签名包

iOS 和 Android 均包含圆形未读角标、评论回复栏键盘避让和按本地日期显示的私信会话时间。签名 APK 和 IPA 位于 `build/latest/`，Android build81 和 iOS build85 已按哈希归档至 `build/history/`。详情见 [2.3 发布记录](releases/2.3.md)。

Android 113 项单测通过，6 项条件跳过。Release Lint、APK v2 签名、升级证书及 16 KB 对齐检查通过。iOS Release 归档及 IPA 导出通过，主应用和 Open In 扩展的版本、严格签名、升级权限和登记设备范围已核对。校验清单位于 `.cache/release-2.3-build86/manifest.json`。复用 Xcode 27.0、Swift 6.4、Gradle 8.14.3、JDK 21.0.12.1、Android SDK 和 Build Tools 36、Node 24.22.1，未下载或安装新组件。尚未安装到设备或进行真机验收。

## 2026-11-08 iOS 2.3/build85 签名包

关闭底栏文字时，同一次 UIKit 更新提交三枚图标。收缩时原生栏提前淡出，头像沿用原弹簧动画。两项宿主测试通过。主应用和 Open In 扩展的版本、严格签名、升级权限和描述文件检查通过。

新 IPA 已覆盖 `build/latest/TiebaPure-2.3.ipa`，build84 已归档。Android 仍为 2.3/build81。校验清单位于 `.cache/release-2.3-build85/manifest.json`，未安装或真机验收。

## 2026-11-08 iOS 2.3/build84 签名包

关闭底栏文字时保留同一条原生液态玻璃栏，使用 2.1 实心图标的尺寸和位置。UIKit 宿主在三种宽度下验证了三个选中状态。主应用和 Open In 扩展的版本、严格签名、升级权限和描述文件检查通过。

新 IPA 已覆盖 `build/latest/TiebaPure-2.3.ipa`，build83 已归档。Android 仍为 2.3/build81。校验清单位于 `.cache/release-2.3-build84/manifest.json`，未安装或真机验收。

## 2026-11-08 iOS 2.3/build83 签名包

仅重打 iOS。关闭底栏文字时使用 2.1 的固定胶囊样式。宿主验证三种选中状态下图标尺寸和位置一致，图标宽高与参考图的测量差异最多 1 像素。主应用和 Open In 扩展的版本、严格签名、升级权限和描述文件检查通过。

新 IPA 已覆盖 `build/latest/TiebaPure-2.3.ipa`，build82 已归档。Android 仍为 2.3/build81。校验清单位于 `.cache/release-2.3-build83/manifest.json`，未安装或真机验收。

## 2026-11-08 iOS 2.3/build82 签名包

仅重打 iOS。隐藏底栏文字时，「首页、进吧、我的」使用 2.1 的实心图标，UIKit 宿主验证三枚图标宽高与 build81 一致。主应用和 Open In 扩展的签名、版本、升级权限及描述文件检查通过。

新 IPA 已覆盖 `build/latest/TiebaPure-2.3.ipa`，build81 已归档。Android 仍为 2.3/build81。校验清单位于 `.cache/release-2.3-build82/manifest.json`，未安装或真机验收。

## 2026-11-08 双端 2.3/build81 签名包

已生成并校验 `build/latest/TiebaPure-2.3.apk` 与 `build/latest/TiebaPure-2.3.ipa`。包含 iOS 底栏图标、显隐及回复条动画修复，以及 Android 吧内搜索点赞计数与回复条布局调整。上一版双端包已归档，详见 [2.3 发布记录](releases/2.3.md)。

Android 111 项单测通过、6 项条件跳过，Release Lint、APK v2 签名、升级证书、ZIP 和 16 KB 对齐检查通过。iOS 主应用和 Open In 扩展签名 Release 归档、IPA 导出、ZIP 和升级权限检查通过。复用 Xcode 27.0、Swift 6.4、XcodeGen 2.46.0、Gradle 8.14.3、JDK 21.0.12.1、Android SDK/Build Tools 36、Node 24.22.1，无新增组件。未安装或真机验收。

## 2026-11-08 iOS 2.2/build80 签名包

仅重打 iOS。包含 1.0 原生液态玻璃底栏、现有头像收缩和展开手势、头像位置及文字开关。签名 Release 归档和 IPA 导出完成。主应用和 Open In 扩展均为 2.2/build80，已核对 ZIP、严格签名、升级权限、开发证书和原登记设备，开发描述文件最早于北京时间 2026-11-11 19:34:01 到期。

新 IPA 覆盖 `build/latest/TiebaPure-2.2.ipa`，build79 归档至 `build/history/TiebaPure-2.2-63c6309c9706/TiebaPure-2.2.ipa`，Android APK 哈希保持。配置和包归档测试 10 项通过。复用 Xcode 27.0、Swift 6.4、XcodeGen 2.46.0 和 Node 24.22.1，无新增组件。校验清单位于 `.cache/release-2.2-build80/manifest.json`，未安装或真机验收。

## 2026-11-05 2.2/build79 双端签名包（完成，未安装）

用户要求打包后，显示版本保持2.2，内部构建78→79，已生成双端签名成品，包含下节帖子时间格式、悬浮页内回复、@/表情/图片/发送、独立会话缓存及无草稿目标自动进入空白编辑器。原悬浮导航底栏样式不改。打包期间413份业务/测试/资源文件哈希一致，仅更新版本配置、生成工程及说明。

当前产物均在build/latest：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.2.apk（15177138字节，SHA256 131ce3b732c98c39f89d9761a552e4df77753ac867399a9f26feb0b33b6e1f77）；/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.2.ipa（6659624字节，SHA256 63c6309c9706b76909289bf4f5db71fab67e06b405569ffda242130dd07e08b3）。旧2.2/build78 APK自动归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.2-446ba316f862/TiebaPure-2.2.apk，旧2.2/build78 IPA自动归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.2-0205eb7f793b/TiebaPure-2.2.ipa，原哈希保持。签名归档留src/.build/TiebaPure-2.2-build79.xcarchive。

Android本次全量116项JVM：110通过，6项既有网络/账号条件跳过；lintRelease、2.2/build79、APK v2签名、原升级证书、ZIP完整性及16KB原生库ZIP对齐通过。iOS签名Release archive/export成功，主应用及Open In扩展均2.2/build79，严格签名、原团队/证书/应用标识、有效开发描述文件、已登记设备兼容及无测试Runner检查通过。最终DEX/Mach-O确认包含FloatingReplyComposer、InlineReplyTarget/InlineReplyDraftCache及现有云端点赞/已读/计数/滚动修复符号，且未包含NativeRootTabBarState。iOS开发签名最早到期北京时间2026-11-11 19:34:01，仅限已登记设备。

沿用本轮应用/设备测试目标编译与14项Mac Catalyst原生编辑器/SwiftUI/草稿/手势宿主回归，未安装、卸载或操作手机，未发送真实帖子、私信或图片，未上传或提交。输入栏独立草稿仍为进程内缓存，不是跨重启持久草稿。复用Node24.21.0、JDK21.0.12.1、Gradle8.14.3/SDK36、Xcode27.0/Swift6.4、XcodeGen2.46.0，无新增组件下载或安装。配置及包归档脚本10项测试通过。证据.cache/logs/2.2-build79-*及.cache/release-2.2-build79/manifest.json、SHA256SUMS。

## 2026-11-05 双端帖子时间、悬浮输入栏与无草稿检测（源码完成，未打包）

帖子主帖、普通回帖和楼中楼的时间统一为HH:mm:ss「MM-dd」，保留楼层和属地，不改首页/浏览/私信等其他时间显示。两端复用原BottomBarMotion/原生触摸观察器：上滑24点/dp收缩，慢下滑保持收缩，至少12点/dp且速度达到650点/dp每秒的下滑展开；收缩显示回复图标，展开直接输入，右侧从左到右为@、表情、图片、发送，垂直居中。复用原玻璃/磨砂材质、自动收缩偏好及左中右位置，正文为恒定底部留白，收缩不改变滚动视口；编辑/选表情/发送时不误收缩，滚动展开不重新弹出键盘。原FloatingRootTabBar.swift/FrostedChrome.kt与本轮开始基线逐字节相同，原悬浮导航底栏样式/高度没有修改。

主帖、楼层、楼中楼回复均接入页内输入栏；@关注用户选择、原生贴吧表情和最多9张图片沿用现有组件及上传/提交校验。第一次进入某个回复目标时不读取旧完整编辑器的磁盘草稿；独立输入栏草稿按当前登录会话与TID/PID/SPID缓存，切换目标恢复对应文字/图片，取消回复目标不删除其缓存，失败保留，成功仅清除对应且未再次编辑的缓存。账号/会话切换隔离，迟到的完成回调不清除其他目标或新会话的新输入。该缓存为进程内缓存，应用结束后清除；新发帖的完整编辑器仍沿用原持久草稿。Android发送复用原上传/校验和账号取消屏障，页内回复发送不导航或重新创建Reader，保留当前位置；旧完整发帖路径保留。

用户再次报告“未存草稿却无法恢复”后，在主后端返回unavailable时新增针对该目标的真实空记录检测：旧文件草稿及iOS17+实际SwiftData查询都明确返回不存在，才自动打开空白发布页，并为该目标建立独立可写恢复目录。已有/损坏/无法读取的目标不被当作空草稿，不重置全库；原清空草稿、损坏保护及继续编辑入口保留。Android原本无草稿时直接空白，无额外错误分支。具体iPhone上导致原主后端不可用的系统状态未读取，未宣称已在该设备复现。

验证：Android全量116项JVM，110通过、6项既有网络/账号条件跳过，lintDebug及设备测试目标编译通过；新增缓存目标稳定标识测试，以及仅在匿名测试安装可运行的假接口设备回归（切换目标/失败保留/成功仅清当前草稿/Reader和位置不变，已编译，未运行设备）。iOS应用/扩展及单元/UI测试目标build-for-testing通过；独立Mac Catalyst宿主实际执行14项测试全通过，涵盖时间/时区/缺失时间、空目标自动进入且可保存、未知/损坏目标继续保护、跨目标/跨会话缓存及迟到完成保护。真实SwiftUI页+UIKit原生编辑器/观察器宿主验证输入宽高、输入回调写入缓存、上滑收缩/慢下滑保持/快下滑展开、重建后恢复文本、视口不变、滚动展开不抢键盘；样本pan为确定注入，不能替代iPhone真触摸验收。旧依赖完整编辑器弹层的回复UI回归已改为页内入口/发送与保持Reader，真实账号测试继续显式默认跳过。

版本仍2.2/build78，未打包/上传、未操作手机或安装/卸载、未发送真实帖子或图片。现有build/latest/TiebaPure-2.2.apk及.ipa哈希分别仍446ba316f8621ed83606650aa6e8a69f85ab7c179bd2edebfc36011eeae8cc54和0205eb7f793bc8250625839bb20853b1a08855e795a68c5429f95e3fb4fd9a1d，不包含本轮源码。复用现有Node/JDK/SDK36/Xcode27/SwiftProtobuf依赖；无新工具安装。证据.cache/logs/inline-reply-android-final.log、inline-reply-ios-final.log、reply-host-tests.log及.cache/floating-reply/verification.json。真机的键盘/图片选择/真实接口发送尚待验收。

## 2026-11-05 2.2/build78 双端签名包（完成，未安装）

用户要求打包并指定2.2后，将显示版本2.1→2.2，内部构建77→78，生成双端签名成品；包含云端“我的点赞”三分类/完整服务端分页/主帖回帖楼中楼定位，以及悬浮底栏所有可见场景的统一手势。保留此前草稿、私信、楼层、排序和计数修复；底栏继续1.3浮层样式，不改高度/文字/头像/位置。379份业务/测试/资源文件在打包期间哈希保持，仅更新版本配置、生成工程与说明。

当前产物均在build/latest：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.2.apk（15160754字节，SHA256 446ba316f8621ed83606650aa6e8a69f85ab7c179bd2edebfc36011eeae8cc54）；/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.2.ipa（6613002字节，SHA256 0205eb7f793bc8250625839bb20853b1a08855e795a68c5429f95e3fb4fd9a1d）。旧2.1/build77 APK自动归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.1-26266102e24a/TiebaPure-2.1.apk，旧2.1/build77 IPA归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.1-62455904eeab/TiebaPure-2.1.ipa，原哈希保持。Xcode签名归档留src/.build/TiebaPure-2.2-build78.xcarchive。

Android打包时全量115项JVM：109通过、6项既有网络/账号条件跳过；lintRelease、2.2/build78、APK v2签名、原升级证书、ZIP完整性与16KB原生库ZIP对齐通过。iOS签名Release archive/export成功；主应用及Open In扩展均2.2/build78，严格签名、原签名团队/证书/应用标识、有效开发描述文件及已登记设备兼容检查通过，无测试Runner/Bundle。最终DEX/Mach-O确认含云端userAgree、AccountLikesLoader及BottomBarPanInput/RootTabScrollObserver等新功能，并保留此前修复符号；不含NativeRootTabBarState。iOS开发签名最早到期北京时间2026-11-11 19:34:01，仅限已登记设备。

沿用前两节双端应用/设备测试目标编译、iOS云端分页宿主断言和四类实际SwiftUI/UIKit手势宿主回归；未安装/卸载、执行设备UI或真实账号云端点赞验收，未发送私信/帖子或新增真实点赞。官方云端列表当前说明范围为2023年1月1日至今，更早记录无已验证入口。复用Node24.21.0、JDK21.0.12.1、Gradle8.14.3/SDK36、Xcode27.0/Swift6.4、XcodeGen2.46.0，无新增组件下载或安装，未上传或提交。证据.cache/logs/2.2-build78-*、.cache/release-2.2-build78/manifest.json及SHA256SUMS。


## 2026-11-05 双端悬浮底栏跨场景手势统一（源码完成，未打包）

用户要求所有显示底栏的场景与主页一样响应后，先以原iOS pan处理器做UIKit/Mac Catalyst宿主最小复现：相同上滑24点，长列表collapsed=true、短列表false（进程预期失败）。原因是原入口依赖被选中UIScrollView的实际contentOffset，并排除边界回弹；短列表/静态页没有足够内容位移，外围观察器还可能固定在旧列表。Android原入口同样只取已消费的nested scroll，短页面/边界的手势会被跳过。

两端统一读取手指的纵向移动，继续使用原BottomBarMotion，原阈值与过渡代码逐字节保持：上滑累计24点/dp收缩，下滑至少12点/dp且达到650点/dp每秒才展开，慢速下滑保持收缩。iOS在可见页面的共同hosting surface被动观察pan，并仅启用当前选中的首页/进吧/我的拥有者；无需逐页挑选某个scroll view，覆盖其下方仍显示底栏的列表、表单、设置、历史/收藏/我的点赞和空/静态场景。识别器单指、同时识别、不取消或延迟子视图触摸，横向和未激活页面不触发。Android在内容容器用不消费事件的motionEventSpy观察真实触摸，以系统VelocityTracker并按density统一dp/秒，不跟随程序滚动或惯性，也不依赖内容能否继续滚动。取消、多指与离开场景清理采样。隐藏场景及自动收缩开关沿用原规则。

没有重做底栏样式。iOS胶囊/玻璃/图标内容和Android浮层绘制块比对相同，尺寸、高度、头像、文字及左中右位置不变；原运动阈值/过渡源码前缀哈希相同。共335份其余业务文件与本轮开始基线哈希一致，保留刚完成的云端我的点赞与此前修复。范围校验.cache/bottom-bar-scenes/scope-check.json，原入口和样式基线同目录留档。

验证：最小复现修复后long=true、short=true；实际SwiftUI NavigationStack宿主中的ScrollView长列表、短List、Form和静态VStack四类页面均覆盖共同监听根、向上收缩、慢下滑保持、快下滑展开、横向及失活无动作，并检查单指/不取消触摸/同时识别属性，全部通过。宿主向生产pan处理器注入确定样本，不能等同iPhone实际触摸验收。新增iOS SDK场景回归与Android原生MotionEvent/VelocityTracker密度回归（设备测试仅编译）。Android全量115项JVM：109通过、6项既有网络/账号条件跳过，lintDebug及设备测试目标编译通过；iOS应用/扩展及单元/UI测试目标build-for-testing通过。证据.cache/logs/bar-scenes-host-{red,green,all}.log、bar-scenes-android-final.log、bar-scenes-ios-final.log、bar-scenes-touch-test-compile.log。

版本保持2.1/build77，未重新打包、安装/卸载、操作手机或用户数据；现有build/latest APK/IPA未变，不含本次源码修复与未打包的我的点赞。工具复用Xcode27.0/Swift6.4、JDK21.0.12.1、Gradle8.14.3及SDK36，无新增构建组件下载或安装。真机触感及所有具体页面仍需设备验收。


## 2026-11-05 双端“我的点赞”云端列表与定位（源码完成，未打包）

在“我的→浏览”新增“我的点赞”，两端提供全部/主帖/回帖三个服务端分类，POST /c/u/feed/userAgree（区分大写A），使用当前账号BDUSS/stoken及签名，按tab_id=0/1/2、pn、rn=20请求；一直遵循服务端has_more，没有100/500条本地历史限额，不以本地点赞日志、收藏或“收到的赞”填充。匿名签名请求已确认官方接口返回明确登录校验，但未使用真实账号取记录。API直接依据官方H5当前请求结构实现，未宣称真实账号全历史已验收。

官方入口 https://tieba.baidu.com/mo/q/hybrid-main-usercenter/myLike/hybrid?nonavigationbar=1 ，其脚本 https://tb3.bdstatic.com/tb/wise/hybrid-main-usercenter/static/js/my-like.a7085cb6.js 明确三分类、pn/rn分页、thread_list/top_agree_post，以及“当前为2023年1月1日至今的点赞内容”。因此只承诺该云端入口可返回的范围，不能以本地存储补成2023年前完整记录。页面显示此范围；静态官方代码未执行，来源/REA排除AgreeMe及匿名登录拒绝证据在.cache/cloud-likes/。

主题和回复按TID+PID标识合并，保留同主题的多条点赞回复；兼容数字/字符串分页标志与大整数ID。主帖明确跳首楼并跳过本地阅读进度；回帖请求pid定位；quote_id楼中楼定位到父楼并以spid打开子回复。Android同时保留目标子回复实际服务端页号，继续分页不从第2页错刷；新三段路由保留原两段楼中楼路由和删除入口的forum_id解析。已删除/无法定位的目标给出提示，不伪造目标。返回列表保留内存中的已载分页/位置，账号或登录会话切换取消旧请求并清空前一账号记录，无登录时只显示登录提示。列表与入口沿用原生组件，未增加云存储后端或依赖。

新JSON响应保持HTTPS、目标域名、POST不跟重定向、响应体积和类型校验，支持官方application/x-javascript，拒绝HTML/错误状态/不完整分页；显式空页可省略thread_list。独立共享合成fixture protocol-fixtures/account-likes.json覆盖主帖、同主题多回复、楼中楼、64位ID、重复页、空页和登录失败，并非真实账号抓包。

验证：Android全量112项JVM，106通过、6项既有网络/账号条件跳过；lintDebug和设备测试目标编译通过。新增6项覆盖映射/定位、云端三分类与签名、同主题多回复合并、空/重复页不提前截断、MIME/重定向拒绝和spid实际页号。iOS应用/扩展及单元/UI测试目标build-for-testing通过；Mac宿主执行生产DTO和分页loader断言，覆盖120条无本地上限、重复/空页、重新登录的迟到响应隔离及MIME，平台显示/网络适配用桩，不能等同登录或设备UI。日志.cache/logs/account-likes-android-final.log、account-likes-ios-final.log、account-likes-ios-host.log，摘要.cache/cloud-likes/implementation-checks.json。

版本保持2.1/build77，FloatingRootTabBar.swift和RootView.swift与打包前基线哈希一致，原APK/IPA哈希未变；现有build/latest包不包含本次功能。未重新打包、安装/卸载、操作手机、发送真实消息/帖子或执行真实点赞，未下载/安装新构建组件。真实账号云端列表与设备定位仍待验收。


## 2026-11-05 2.1/build77 双端修复包（完成，未安装）

用户要求“打包”后，显示版本保留2.1，内部构建76→77，生成Android Release签名APK与iOS开发签名IPA。包含下节安卓排序切换局部刷新保位、用户主题点赞field40、私信已读协议修正及双端预览/详情整帖计数同步。底栏保持已回退的1.3实现，打包前后367份业务源码哈希一致；仅更新构建配置、生成工程及说明。

当前产物均在build/latest：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.1.apk（15144370字节，SHA256 26266102e24a858080ef5ca5ffa449e64e99a6c7f04e099c3a0fb19f74e31555）；/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.1.ipa（6561197字节，SHA256 62455904eeab93237d87cee6008e5b4d6202be5fec6794b668a4b1e9b91e877f）。旧APK/build73自动归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.1-0eefa086fc7f/TiebaPure-2.1.apk；旧IPA/build76自动归档/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/history/TiebaPure-2.1-9347cffc6a4f/TiebaPure-2.1.ipa，旧哈希保持。

打包时Android全量106项JVM：100通过、6项网络/账号条件跳过；lintRelease、APK v2签名、原升级证书、2.1/build77、ZIP完整性与16KB原生库ZIP对齐通过。iOS Release archive/export、主应用及Open In扩展2.1/build77、严格签名、原签名团队/证书/应用标识、有效开发描述文件、保留已登记设备及无测试Runner检查通过。最终包确认含评论刷新/计数修复符号及旧FloatingRootTabBar，不含新增NativeRootTabBarState。开发签名最早到期北京时间2026-11-11 19:34:01，仅限已登记设备。

沿用上节iOS测试目标编译、宿主协议/计数断言及安卓设备测试目标编译结果；本次未执行手机安装、卸载、设备UI、真实私信发送或帖子发布。真实双账号已读送达和滚动触摸仍需验收，未打开帖子的服务端接口缓存时差仍存在。复用Node24.21.0、JDK21.0.12.1、Gradle8.14.3/SDK36、Xcode27.0/Swift6.4、XcodeGen2.46.0，无新增组件下载或安装；未上传或提交。日志.cache/logs/2.1-build77-*，校验清单.cache/release-2.1-build77/manifest.json、SHA256SUMS，签名归档src/.build/TiebaPure-2.1-build77.xcarchive。


## 2026-11-05 安卓排序保位、用户主题点赞、已读与双端主题计数（源码完成，未打包）

安卓评论切换正序/倒序/热门及只看楼主时复用同一个阅读列表状态，移除排序条件对整个阅读页的重建；只替换回复、保留原正文，并在提交新评论集合时固定当前列表索引及像素偏移，避免 Compose 按旧回复 ID 重新锚定。评论刷新不触发整页刷新指示，失败重试沿用评论局部刷新；深链目标不再重复用于切换后的评论请求。原回帖返回栈与阅读快照继续保留。新增屏幕级排序/正文/索引偏移回归，已编译未运行设备测试。

官方 APK 的 PostInfoList 注解及用户动态 mapper 证实点赞位于 Agree agree=40，原共享 UserThreadItem 仅有旧 agree_num=37。补共享 schema、Swift 生成文件和双端用户主题映射，以独立 wire fixture thread-counters.json 覆盖 field40。Android JSON 预览同时补 flat/nested 点赞。官方 nested Agree 存在时包括显式零值均优先；旧字段只在 nested 缺失时使用。

匿名公开接口核对证实同一帖整帖点赞394、首楼帖子点赞336，两者不是同一对象。双端详情主楼现统一采用 thread 点赞与状态，普通回复仍保留各自计数。详情加载及成功点赞后，将整帖点赞和 reply_num 按主题 ID 同步到主页/吧群预览缓存，不重载列表；账号切换隔离计数。FRS/PBPage 回复总量差值并非固定1，故不以减1、最高楼层、已加载条数或 Page.total_count 推算。未打开帖子的不同服务端接口仍可能有缓存时差；已读详情的汇总为本会话预览依据。公开诊断在 .cache/count-protocol/。

安卓已读按官方 type22/22001 协议修正：eventParam.groupId 对应登录 type6 私信流，不是消息外层 groupId；按对方会话的 readMsgId 水位标记发出气泡，支持官方可省略 to_uid 的入站元事件，仍拒绝其他收件人、本人事件和非本账号私信流。本机读取/提交已读确认不当作对方已读。新增真实 inbox merge→messages 回归先失败，修复后8/8通过，覆盖分页、会话、账号及本地已读隔离；AtomicFile重启/中断恢复设备测试已编译未执行。静态证据和红绿日志在 .cache/diagnostics/read-receipt-2026-11-05/。

验证：Android 全量 JVM 106项，100通过、6项需要显式网络/账号条件的测试跳过，lint及设备测试目标编译通过；独立 wire fixture 改为共用资源后，相关6项再次通过。iOS应用/扩展及单元/UI测试目标 build-for-testing 通过；Mac宿主执行生产生成协议、UserProfileMapper/PostMapper/ThreadMapper及ThreadCountersState断言通过（平台图像/过滤适配使用桩，不等同设备UI）。日志 .cache/logs/comment-counts-android-final.log、thread-counters-final.log、comment-counts-ios-final.log、counts-host-run.log。

版本保留2.1/build76，未改底栏；FloatingRootTabBar.swift SHA256保持b5583356da68ea3b97fdd3017d0ac6358afb10f236f02fde02d87c91b68bcf2e。既有build/latest APK/IPA哈希均保持。复用Node24.21.0、JDK21.0.12.1、Gradle8.14.3、Xcode27.0及缓存protoc/SwiftProtobuf工具，无新增组件下载或安装。未打包、安装/卸载应用、操作手机、发送真实私信或帖子；真实对方已读送达及滚动触摸尚需双账号/设备验收。


## 2026-11-05 iOS 2.1/build76 旧底栏回退包（完成，未安装）

按用户打包指令，仅重建iOS，显示版本2.1，内部构建75→76；底栏恢复为2.0之前1.3/build70的浮层源码及主标签接入，不改动其他业务。491份受保护文件及RootView非底栏部分与回退前哈希一致，除底栏及其测试/引用外仅更新构建元数据和说明。底栏文件与历史1.3源码逐字节相同，MainTabView代码块相同；最终IPA包含旧浮层标识，不包含新增NativeRootTabBarState/NativeRootTabBarView适配层。

新IPA位于/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.1.ipa，6551143字节，SHA256为9347cffc6a4f9625b95f64019d68694baef561fc4bc8d57e7eb32121c0d517ee。旧build75 IPA自动归档到build/history/TiebaPure-2.1-96869cea74bc/TiebaPure-2.1.ipa，原哈希保持；Android APK仍2.1/build73，哈希不变。签名Release archive/export、主应用/扩展2.1/build76、严格签名、ZIP、原签名团队与证书、有效开发描述文件及无测试Runner检查通过。开发签名有效至北京时间2026-11-11 19:34:01，仅限已登记设备。

配置及存储脚本10项通过，沿用下节旧手势/偏好宿主与应用/单元/UI测试目标编译结果；未运行设备UI。证据.cache/logs/2.1-build76-*、.cache/release-2.1-build76/manifest.json、SHA256SUMS、scope-check.json。复用Xcode27.0/Swift6.4、XcodeGen2.46.0、Node24.21.0，无新增组件下载或安装。未安装/卸载手机应用、操作用户数据、发送真实内容或上传。

## 2026-11-05 仅将iOS底栏回退到2.0之前的1.3实现（完成，未打包）

用户要求直接回退底栏、保留其他功能后，以最后一个2.0之前的1.3/build70底栏为基准。已从原开发会话的完整源码读取记录及随后两项历史补丁还原FloatingRootTabBar.swift，并恢复同阶段MainTabView；逐字节/代码块比对通过。恢复固定62高、最多280宽的浮层胶囊、头像收缩、左中右位置、文字开关及旧手势/过渡，不再继续调整原生底栏样式。移除本轮新增NativeRootTabBar适配层及其专属测试，保留1.3已有的私信页面隐藏底栏逻辑。

仅修改底栏文件、RootView中的MainTabView块、相关UI测试及生成的Xcode文件引用；RootView其余内容和其他业务源码与回退前哈希一致，包括全部Android、私信、发帖图片、草稿恢复、楼层/排序、点赞、协议和数据存储。未重置偏好、清理用户数据或降级应用版本，配置保持2.1/build75。基准记录及范围核对在.cache/bottom-bar-rollback/reference-1.3与scope-check.json，恢复前文件存于同目录before，作为可删诊断资料。

生产旧手势/过渡/偏好宿主检查通过，iOS应用、扩展及单元/UI测试目标build-for-testing通过；日志.cache/logs/bottom-bar-rollback-host.log、bottom-bar-rollback-ios.log。没有打包、替换build/latest、安装设备或上传；现有IPA仍为先前build75，不含本次回退。未做实机复测。

## 2026-11-05 iOS 2.1/build75 展开尺寸修复打包（完成，未安装）

用户要求打包后，仅重建iOS：显示版本2.1、内部构建74→75，包含录屏对应的展开尺寸修复，恢复系统控制器托管底栏，保留文字/头像/位置/快慢滑动规则。开发签名IPA已更新至/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.1.ipa。旧build74自动归档至build/history/TiebaPure-2.1-621632830681/TiebaPure-2.1.ipa，哈希保持621632830681cf8436674affb49731ba881f30a4082d813299de1e50648bc0a8。Android APK仍为2.1/build73，原哈希保持不变。

签名Release archive/export成功；最终主应用和Open In扩展2.1/build75、严格签名、原签名团队与证书、ZIP完整性及有效描述文件检查通过。包内确认包含NativeRootTabBarState且不再包含旧NativeRootTabBarView，无测试Runner/Bundle。新IPA6562411字节，SHA256为96869cea74bc8a2a2ab505c1cb0bb21e402adf53466749ec4606d6de689ab0ff。开发签名有效至北京时间2026-11-11 19:34:01，仅限已登记设备。配置/存储脚本10项通过；沿用下节36组UIKit宿主布局与应用/测试目标编译结果，不计为iPhone触摸验收。证据.cache/logs/2.1-build75-*及.cache/release-2.1-build75/manifest.json、SHA256SUMS。

复用工具箱Xcode27.0/Swift6.4、XcodeGen2.46.0、Node24.21.0，无新增下载或安装组件。未安装/卸载手机应用、执行设备UI或发送实际私信媒体，未提交或上传。

## 2026-11-05 录屏中的iOS展开底栏尺寸回归（源码完成，未重新打包）

已读取用户5.48秒录屏并提取12帧；录屏显示头像尺寸正常，展开后的原生玻璃条明显更矮。原因是build74把独立UITabBar装进280×62外框，而UIKit内部仍使用紧凑玻璃布局，外框62点不能代表可见表面尺寸，且title=nil会丢失正常标题布局。Mac Catalyst/UIKit最小复现真实失败：原控制器表面239.5×44，独立条204×42；这些数值仅是宿主平台对照，不作为iPhone实际像素尺寸。

修复恢复TabView/UITabBarController托管的正常原生底栏，展开高度/大小回到原控制器布局，不拉伸图标或玻璃球。隐藏文字改为保留标题占位、透明文字及居中图标；收缩时仅隐藏条的可见性/交互/辅助功能并叠放62点头像，保留安全区及阅读视口。原头像左中右位置、文字开关、上滑收缩/快速下滑展开/慢速保持等门槛继续生效，系统自动minimize仍关闭。所有根页面共享展示状态，并以当前页面所有权阻止旧页面清理覆盖新状态；原生条的位置、选择、delegate及私有子视图未修改。

生产展示状态在Mac Catalyst/UIKit宿主36组文字开关/收缩展开组合检查通过，实际玻璃表面与原控制器完全一致，页面状态继承与属性恢复通过；更新XCTest为检查可见表面，而非仅外框高度。应用、扩展及单元/UI测试目标build-for-testing通过，原生产手势与偏好回归沿用此前结果。录屏/红绿证据在.cache/native-tab-size-video，编译证据.cache/logs/native-tab-size-ios.log。仅本地修复，版本保持2.1/build74，未重新打包、安装设备或上传；build/latest安装包不含本次尺寸修复。iPhone实际触摸、头像锚点与最终视觉尺寸仍需实机验收，宿主布局检查不代替iPhone测试。

## 2026-11-05 iOS 2.1/build74 底栏修复重打包（完成，未安装）

用户要求重新打包后，仅重建含最新底栏偏好/头像/固定高度/左右位置/滑动规则的iOS，显示版本2.1，内部构建73→74。开发签名IPA已更新至/Users/y/Downloads/Codex/Mac工作区/TiebaPure/build/latest/TiebaPure-2.1.ipa；旧build73由脚本自动归档至build/history/TiebaPure-2.1-07f7f225db82/TiebaPure-2.1.ipa，原SHA256一致。Android现有APK仍为2.1/build73，哈希0eefa086fc7f1bce3d2db03f185bc5bee24dbc69cad6691e160736d51d4fed7d保持不变。

签名Release archive/export成功；最终IPA中主应用和Open In扩展版本/严格签名、ZIP完整性、原签名团队及证书、有效开发描述文件、原生底栏实现标识、未包含测试Runner/Bundle均检查通过。新IPA6556887字节，SHA256为621632830681cf8436674affb49731ba881f30a4082d813299de1e50648bc0a8，开发签名最早到期为北京时间2026-11-11 19:34:01，仅支持已登记设备。配置/安装包存储脚本10项通过，沿用上一阶段手势/偏好宿主和应用/测试目标编译结果，未再次执行设备UI。证据.cache/logs/2.1-build74-*及.cache/release-2.1-build74/manifest.json、SHA256SUMS。

工具箱复用Xcode27.0/Swift6.4、XcodeGen2.46.0、Node24.21.0，无新增组件下载或安装。未安装/卸载手机应用、执行实机拖动或真实私信媒体发送，未提交或上传。

## 2026-11-05 iOS原生底栏保留文字/头像/位置及原滑动规则（源码完成，未重打包）

用户补充要求后，纠正build73中iOS26/27走系统默认minimize而忽略既有设置的行为。展开状态改用公开支持的独立原生UITabBar，UIKit提供玻璃选择球/左右拖动；浮层复用原有BottomBarMotion，手指上滑累计24点收缩为账号头像，下滑至少12点且速度达到650点/秒才展开，慢速下滑保持收缩。展开和头像容器均62点高，原生条目/头像背景不纵向缩放；收缩头像继续支持居左/居中/居右，点击头像只展开。文字/位置/自动收缩设置在26/27重新显示并读写原键，隐藏文字保留图标及辅助功能名称，未重置已有偏好。

TabView继续保存各页导航状态；关闭其自动minimize和自带条显示，独立原生条通过原绑定切换，不绘制自建选择胶囊或挂接横向手势，不访问系统私有视图。帖子/私信显隐、减少动态效果及阅读视口规则保留。来源Apple UITabBar文档与本机iOS27 SDK。

宿主执行生产BottomBarMotion/BottomBarTransition/AppAppearanceStore与现有回归通过，覆盖慢速不展开、快速及释放展开、抖动过滤、连续过渡和偏好持久化。新增原生条目/文字/高度/选择绑定单元测试，三位置/速度/头像高度和设置可见UI回归；应用及单元/UI测试目标build-for-testing通过，仅编译，未运行UIKit或设备UI。本机无iOS模拟器运行时，未操作手机、重打包或改变build/latest中的2.1/build73。证据.cache/logs/native-tabs-preferences-host.log、native-tabs-preferences-ios-final.log。

## 2026-11-05 2.1/build73 双端重打包（完成，未安装）

用户明确“打包”后，显示版本保留2.1，内部构建72→73，包含下节草稿清空、iOS26/27系统原生底栏、私信布局/静默刷新/自适应气泡/上传协议，以及Android回帖返回保位与真正倒序分页修复。双端签名包已更新到项目build/latest/TiebaPure-2.1.apk及.ipa；旧build72自动归档到build/history/TiebaPure-2.1-6b734379807f/TiebaPure-2.1.apk及build/history/TiebaPure-2.1-8f5b279239ab/TiebaPure-2.1.ipa，逐一核对SHA256保持一致。

Android98项JVM中92通过、6项真实联网测试跳过，lintRelease与assembleRelease通过；版本/非Debug/沿用Release证书/ZIP完整性/ZIP及64位原生库16KB页对齐检查通过。iOS签名Release archive及开发IPA导出通过，主应用和Open In扩展2.1/build73及严格签名检查通过，无测试Runner或测试Bundle。两份描述文件均有效，最早到期为北京时间2026-11-11 19:34:01，仅支持已登记设备。配置和安装包存储脚本10项通过。日志在.cache/logs/2.1-build73-*；全量哈希、字节数、旧包归档与验证结果在.cache/release-2.1-build73/manifest.json及SHA256SUMS。

工具箱复用Xcode27.0/Swift6.4、JDK21.0.12.1、Gradle8.14.3、Android SDK36、Node24.21.0；全程使用现有组件及缓存，没有下载或安装新组件。未安装/卸载手机应用、运行设备回归、实际发送私信媒体或公开发帖，未提交或上传。源码阶段的双端编译、草稿宿主与私信核心检查见下节，不能将签名/编译视为实机业务验收。

## 2026-11-05 草稿、系统底栏、私信与Android回帖导航（源码完成，未重新打包）

iOS无法恢复草稿的重试下方新增“清空草稿信息”，确认后仅重置当前账号/当前发布目标。通过持久化的恢复后端替换该目标的旧草稿，即使原数据库仍不可用，立即及下次进入也能打开空白可保存编辑器；其他草稿不受影响。损坏激活信息、缺失已提交目录和符号链接仍显式失败，不静默丢稿。macOS宿主执行生产草稿/迁移/文件存储/恢复代码37项，全部通过；iOS专属文件保护检查未在macOS替代执行。

iOS26/27改回SwiftUI TabView创建的系统UITabBar，玻璃选择球及左右拖动由系统管理，删除自建选择胶囊和拖动手势。系统收缩行为由现有总开关控制，位置/文字偏好仅旧系统显示；帖子和私信继续隐藏底栏。双端私信输入框与语音/表情/图片按钮垂直居中，消息刷新不显示旋转或进度条；准备图片仍显示对应状态。iOS文字气泡按内容测量宽度，长消息在280点上限内换行，Android沿用内容自适应宽度。

对照本机官方APK静态实现，修正语音上传域名为tiebac.baidu.com，图片groupId改用当前认证账号type6分组，multipart文件名file且不发送文件分片Content-Type；上传与LCS使用同一CUID。保留分片大小、全文件MD5、签名、上传收据校验及取消/账号隔离，图片兼容originPic/bigPic/smallPic有效收据。服务器拒绝显示具体数字错误码，不输出原始响应。上传及类型2/3发送采用模拟网络/协议回归，尚未执行真实附件发送或确认官方客户端送达。

Android回帖成功直接弹回已有原帖路由并移除其上的楼中楼/编辑器层，保留完整已加载数据、页码、排序与同一个LazyListState，不依赖300ms阅读位置落盘。无原帖时回到已有楼中楼，脱离阅读页的回复仍可打开结果帖；新主题替换编辑器，不残留compose返回栈。晚到回执校验账号会话、路由及导航代次，避免关闭重新打开的编辑器或清掉新草稿。为保持位置，成功后不自动刷新原帖，新回帖可手动刷新查看。

Android倒序不再依赖服务端忽略的r=1：先取正序首页获取总页数及主楼，再读取最后一页，将逻辑后续页映射到服务器前一页，按楼层倒排并保留主楼。首次多页仅请求首页+末页，续页一次请求一页，不预加载整栋楼；匹配pid不再覆盖倒序页码。导航/倒序5项回归先真实失败，修复后针对性13项通过。

最终验证：Android98项JVM中92通过、6项显式真实网络测试跳过，lintDebug及compileDebugAndroidTestKotlin通过；iOS应用、扩展及单元/UI测试目标build-for-testing通过（仅编译），Swift私信宿主检查通过，覆盖已读过滤、watermark、缓存、type2/3发送和分组缓存。新增设备回归覆盖原帖状态保留及晚到回执，尚未执行。证据.cache/logs/fixes-nov05-android-final.log、fixes-nov05-ios-final.log、fixes-nov05-im-host.log及.cache/draft-reset-check/xctest-host-supported.log。未重新生成APK/IPA、更新build/latest、操作用户手机或上传；此前2.1包不含本节修改。使用共用工具箱既有Xcode27/Swift6.4、JDK21.0.12.1、Gradle8.14.3、SDK36及Node24.21，无新增下载或安装组件。

## 2026-11-04 2.1/build72 悬浮栏拖动修复与双端打包（完成，未装手机）

用户先要求iPhone验收，随后撤回安装/卸载并明确直接打包。iOS自定义悬浮栏原来只有点击，新增水平拖动、连续玻璃胶囊跟随、最近标签切换、回弹与边界钳制；保留收缩、点击、辅助功能和减少动态效果。宿主几何检查与最终签名测试目标编译通过；设备UI执行先后被原应用重签名身份不匹配及免费应用名额阻挡，未计为功能测试结果。本轮测试助手已清理，没有安装2.1或卸载原2.0。

双端2.1/build72成品已存build/latest/TiebaPure-2.1.apk及.ipa，原2.0同平台包由脚本自动归档build/history并核对SHA256一致。Android91项JVM中85通过/6项显式网络跳过，lintRelease、签名与16KB对齐通过；iOS Release签名archive/export、最终主应用/扩展版本及严格签名通过。IPA开发签名有效至北京时间2026-11-11 19:34，不是通用分发。工具箱复用，无新增构建工具/SDK；未推送上传。详情[2.1记录](releases/2.1.md)，证据.cache/release-2.1/manifest.json、SHA256SUMS。

## 2026-11-04 2.1/build72 源码修复与附件能力（本地验证，未打包）

iOS草稿后端失败后重试可重新解析，仍无法读取时提供保留旧草稿继续编辑；旁路禁止覆盖或删除旧稿，保存失败保留输入。Android返回吧内恢复列表分页与滚动位置，最多8个分类缓存；楼层显示、数字正倒序及用户主题点赞映射已修正。双端发帖支持9图；私信支持图片、语音录制/播放与表情。官方type22/event22001已读事件不再显示为气泡，按会话/group/watermark显示在自己发送的气泡旁。

Android91项JVM中85通过、6项显式网络跳过，lintDebug与原生测试源码编译通过；iOS应用/单元/UI测试目标build-for-testing通过，Swift宿主核心检查与草稿文件后端恢复检查通过。实际设备操作、原机首次草稿失败原因及新增附件真实发布/送达未验收。工具箱复用，Gradle补齐已声明的测试依赖，未新增SDK/JDK/Xcode/运行时。未生成APK/IPA、安装设备、提交或上传。详见[2.1](releases/2.1.md)。

目录按build/src/.cache整理，脚本在src/tool/tiebapure；旧诊断与预览移入.cache，依赖和编译中间物留src。逐项移动清单位于项目.cache/logs/directory-moves-nov04.md。

## 2026-10-30 TiebaPure 2.0/build71 双端打包与iPhone原位更新（完成）

用户明确要求2.0并改名TiebaPure后完成双端签名包，根latest仅保留TiebaPure-2.0.apk/ipa，旧TiebaPure-1.3同平台包按原名与原哈希归档，完整性一致。应用标识、URL scheme、签名身份和数据目录保留。iPhone从最终IPA安装后查询名称/版本/build正确，启动及运行进程核对通过；49个原始与新增私信持久化文件在安装前后全部字节一致，未卸载/清理/恢复数据。

Android78项JVM中72通过/6项显式网络跳过、lintRelease及原生测试编译通过，中断原子写入恢复1项通过。iPhone8项私信回归与官方入站读取全部通过；另一次持续授权下仅发送一次苹果端验证，用户确认在官方客户端收到。双端官方私信实际互通已验收；iOS跨入口共享缓存修正也已通过宿主及真机回归，最终签名归档已核对包含该修正。59份表情图片与签名/版本/ZIP等检查通过。详细范围与限制见docs/releases/2.0.md。

工具Xcode27/Swift6.4、JDK21/Gradle8.14.3、SDK36、Node24.21全部复用，无新增组件或依赖安装。未推送/上传。开发签名至11月3日10:05北京时间；证据.build/release-records/2.0-build71/manifest.json、SHA256SUMS及logs/build71-*。临时真实账号诊断全部移到忽略的.build/legacy-im，不进入源码或安装包。

## 2026-10-30 官方 LCS 私信互通修复（本地，最终回归中）

用户确认此前 REST 和原生 BIM 虽有回执，官方客户端均未收到。已删除 pim/pimc 收发实现，改用本机官方参考 APK 的 Tieba LCS protobuf 协议。用户明确批准固定 im.tieba.baidu.com:8000 的官方应用层加密 WS 例外，并给予指定测试收件人持续实发授权。双端使用固定官方 RSA 公钥包装随机秘密、官方 PBKDF2/AES 参数；先匿名协商并校验加密回包，再仅以加密正文提交账号资料并核对 UID，无明文登录回退、凭据日志或自动发信重试。

新通道仅向用户指定5989493916发送一次“贴吧官方通道验证1030”，205001回执的收件人、record ID 与有效消息 ID 匹配；用户明确确认官方客户端收到且已回发。“官方接收验证1030B”已从官方202003同步读到。排查确认零游标会返回成功但跳过私信，正数有效游标可返回真实入站消息；双端初次同步和旧零游标统一使用1，并保留增量游标与新版repair字段。proto2保留显式零值用户类型，双端独立 wire/密码学 fixture 及初始游标回归防止误判空成功。

官方消息按账号/双方UID隔离、去重、原子持久化，再更新游标；会话从真实消息生成头像/昵称/最新消息/时间，本机未读角标在头像右上角，进入会话清除本机未读。旧BIM气泡不导入为官方消息。模拟器生产API已核对目标会话、入站正文/发送人、头像、7条未读；从私有文件重新载入消息存在，阅读后未读清零，日志lcs-inbox-production.log。昵称使用官方UserInfo.display_name/tag18并保留资料查询补全。新增@关注人和昵称/ID用户搜索沿用此前改动。

截至记录时 Android77项JVM中71通过、6项显式网络跳过，lintDebug通过；iOS应用/单元/UI测试目标编译通过。当前有效游标/昵称的最终回归与Swift宿主协议检查继续进行，尚未在iOS设备执行新通道。新通道启用前的全量历史和跨设备已读同步未作承诺；发送确认只来自用户指定测试账号。尚未生成签名发布包、更新latest、安装iPhone、推送或上传，工具和依赖全部复用，无新组件下载/安装。

## 2026-10-30 原生私信通道、关注人艾特与用户搜索（本地，互通待验收）

私信改为固定 pimc.baidu.com:443 的 TLS 原生协议：method50 登录并核对百度UID，94会话同步，93历史，55手动发送；按请求ID对应回包。生产只读复现历史的命令11压缩帧后，双端补齐GZIP解码及4MiB解压限制、命令/长度边界、系统证书/主机名校验、超时和取消。会话解析最新消息中的双方身份，昵称/头像复用资料查询；头像右上角显示未读数，行内显示最新消息及时间。前台每15秒只读刷新，发送中不轮询，不自动发信或重发。原REST get_user_info仅用于身份映射，不再用于收发/会话列表。

用户另一次明确授权后，通过新原生通道仅向5989493916发送一次“官方互通验证1030”；发送前核对当前账号主页ID5988871570与用户截图相符，应用缓存原子标记attempted，得到回执并回查同消息，未再次发送。用户说已从官方发出入站测试，但此次历史尚未找到“官方接收验证1030”，method94依旧返回空列表。已请求核对官方客户端是否收到新测试及入站目标ID；尚不能宣称官方收发或非空会话/未读计数实际验收通过。证据native-authorized-interop.log；所有单次实发和账号读取诊断均移入忽略的.build/private-message-native-reference，源码无测试账号/凭据。

双端编辑器在图片/表情旁增加艾特入口，复用关注用户分页与错误重试，在光标处插入@用户名；Android关注列表保留原始用户名以避免用展示昵称替代。搜索增加用户入口，昵称查询复用官方search/user的exactMatch/fuzzyMatch并去重，纯数字主页ID经已有专用接口解析，选择结果打开用户主页。Android生产只读昵称查询返回50条，原生登录/同步及指定用户历史读取通过（空会话、6条历史，不等于真实送达）。共享fixture增加独立GZIP帧，回归覆盖解压/非法帧、会话身份/最新消息、原生发送字段与未知结果不重试、昵称匹配/去重。

Android JVM87项中81通过、6项显式网络测试跳过，最终lint及测试目标检查见native-clean-final-android.log；iOS应用/测试目标编译见native-clean-final-ios.log，尚未在iOS设备执行本轮测试。此前native-gzip-final-android.log、native-gzip-final-ios.log均通过。未更新签名Release/IPA、latest、iPhone或仓库；只更新安卓Debug诊断预览，工具与依赖全部复用，无新组件安装。

## 2026-10-30 私信收发与会话混用排查（进行中，未发布）

用户确认 iOS 发送无报错但对方未收到，且两名收件人消息混入一个界面；Android 用户尚未验证。本轮新增跨收件人消息与 JSON 数字字段回归，先运行失败后修正。双端历史解析同时校验 from_user/to_user 及正文身份，拒绝外部会话消息；JSON 数字字段保持整数与64位精度。iOS 主页入口身份包含收件人，异步请求增加会话代次校验，离页不再永久关闭复用中的 URLSession；Android 同步增加会话代次检查。

对照本机官方 SDK 静态参考，Utility.getBusinessType(0, ...) 返回1，IMSendChatMsgRequest 对 category0/business_type1 使用 to_user_type0。双端据此修正普通私信发送类型，历史查询使用 business_type1。只读生产检查两名收件人身份不同，历史分别为4条和0条、无交集；改变 business_type 后历史数量不变，会话 fetch_session（包括指定 contacter_type1）仍为空。官方普通会话另有 method94 同步，当前尚未接通，不能宣称会话列表及接收已修复。

用户明确授权后，仅向其指定测试ID发送一次“私信修复验证1030”；发送前在模拟器应用缓存以原子新建文件记录尝试，禁止自动重试。修正后的生产客户端得到回执，刷新核对同一消息ID/发送人/正文且通过接收人校验。证据 logs/private-authorized1030-send.log；用户随后明确确认接收方官方客户端仍未收到，故该次回执与历史回查不构成送达证据，普通用户类型修正未解决实际发送。此次授权已用完，不再试发。用户另确认尚未发送入站对照消息，故接收路径尚无实际验收。一次性实发及只读诊断已移到忽略的 .build/private-message-native-reference，不留在测试源码。

Android 私信13项测试中12通过、1项需显式开启的网络测试跳过；全套JVM、lintDebug及原生测试编译通过。iOS应用及单元/UI测试目标 build-for-testing 通过，尚未执行iOS真机回归。日志 private-isolation-android-final.log、private-isolation-ios-final.log。仅更新本地源码/安卓Debug诊断预览，未生成签名Release/IPA、更新latest、安装iPhone或上传；无新增依赖或组件。此前build70的单次历史回查不能等同实际送达或完整私信可用。

## 2026-10-30 安卓吧内作者资料与 iOS 主页昵称位置（本地）

Android 仅吧内帖子卡片改为作者头像、昵称及回复/发布时间，点击头像或昵称打开作者主页；首页继续使用原有吧名和吧头像。最新回复及精华优先使用 last_time_int，最新发布优先使用 create_time，缺失时回退有效时间；复用 iOS 已有字段与优先级，不新增协议字段或依赖。置顶折叠、图片和卡片其他操作保留。

iOS 本人/他人主页共用资料行改为在文字外叠放复制按钮，消除 44 点点击区域撑高性别/ID行导致昵称上移的问题，恢复原头像、昵称及性别/ID的相对位置；复制、私信和关注按钮保留。

Android JVM 82项中76通过、6项显式网络测试跳过，lintDebug及 Debug/原生测试编译通过；模拟器原生点击回归1项通过，覆盖吧内昵称/头像打开作者与首页吧名打开贴吧。模拟器已原位更新 Debug，保留账号数据。iOS 应用及单元/UI测试目标 build-for-testing 通过，新增位置断言仅编译，尚未在手机执行。日志 forum-author-header-verified-android.log、forum-author-header-native.log、profile-name-alignment-ios.log。未更新签名 Release/IPA、安装 iPhone 或上传；latest 中 1.3/build70 双端历史产物哈希保持。工具箱全部复用，无新组件安装。

## 2026-10-30 1.3/build70 双端签名包与iPhone更新

按用户“打包并安装到手机”将构建号69→70，保留版本1.3，包含最新原生私信、ID解析/主页入口、关注人选择及此前未封包的布局修正。此前“暂停打包”记录仅对应其历史阶段。本机模拟器已先更新当前源码Debug69并打开关注选择空状态，保留登录和原数据；最终Release70没有替换模拟器Debug签名。

双端最终包在根latest，旧build69同平台包按原SHA256归档到releases，历史包完整性核对通过。Android81项JVM中75通过/6项网络跳过，lintRelease、签名/版本/ZIP/16KB对齐通过；配置及存储9项通过。iOS签名归档、最终IPA导出/严格签名、主应用/扩展版本及目标设备资格核对通过；两端59表情哈希一致，408生产/测试源码及资源哈希保持。无新组件或依赖下载/安装。

已从最终IPA原位安装到iPhone18ProMax并查询1.3/build70，启动成功。更新前完整备份1257文件/137812207字节校验通过；更新后48个持久化文件中47个字节一致，另一个自动化状态文件仅启动扫描缓存游标变化，规则及任务历史保持。未卸载、清理设备容器或恢复旧数据。最终重开因手机自动锁屏被拒，前次启动已成功，用户解锁可自行打开。开发签名至2026-11-03 10:05:48北京时间。

详情docs/releases/1.3.md；私有证据.build/release-records/1.3-build70/manifest.json、SHA256SUMS及logs/build70-*。未推送/上传、未在本轮再发测试私信，Android真机及非空关注列表的完整选择/收发仍不计为验收通过。

## 2026-10-30 私信直接选择关注的人（仅本地）

双端“发消息”新增“从关注的人中选择”，点选头像/昵称即打开该用户的会话；原手输主页ID保留。iOS直接复用FollowedUsersView的头像、分页、错误重试及账号隔离；Android调用已有relationships关注接口，在独立选择器维护分页/去重/失败重试，关闭和换号取消请求，不改动全局列表数据。各入口复用同一收件人构造，使用真实账号UID，保留昵称/头像，拒绝本人、无效或非规范ID。不会因点选自动发信，无新接口/schema/依赖。

Android81项JVM中75通过、6项显式网络测试跳过，包含新增关注收件人的身份/头像/本人及无效ID回归；lintDebug和原生测试编译通过。iOS应用和单元/UI测试目标编译通过，新增相同回归仅编译未在设备执行。日志private-following-android.log、private-following-ios-final.log；未做真机点选/真实关注接口的新验收或发送新私信。遵守等待打包指令：未生成APK/IPA、安装模拟器/手机、更新latest或上传，版本仍1.3/build69，现有安装包哈希不变，工具箱与依赖全部复用。

## 2026-10-30 按主页 ID 发起私信、一次授权实发验证与布局恢复（未打包）

双端收件人输入改为“用户ID”，不再要求用户寻找内部UID。新增官方getUserByTiebaUid/cmd309702的最小protobuf投影、双端映射、iOS会话监控转发及独立共享wire fixture；必须回包中展示ID一致且账号UID有效才打开会话，避免误用显示ID找错用户。匿名真实接口验证，以及Android生产API“ID→账号→核对主页ID”通过。原生私信拒绝保留服务端error_msg并去除凭据回显，系统消息存在正文时不再统一遮成“其他类型消息”。

用户解锁iPhone并提供测试收件人ID后，按已告知的“Anyu测试”仅发送一次。生产客户端完成ID解析、历史读取、发送、有效消息回执与刷新历史核对（同消息ID/发送人/正文），全部通过；无自动重试，未再发第二条。一次性防重复标记仅位于手机Caches，只有attempted/confirmed状态；临时诊断源码已移到本机忽略目录，不进入项目/源码包。证据logs/private-id-authorized-send.log与对应xcresult，打印确认布尔值，不打印凭据/账号UID/消息ID。该结果不是接收方手机已读或所有账号/既有会话同步验收。

主页按IMG_0233.heic恢复原头像+资料+右侧关注排列，将私信放在关注左边；复制按钮保留，图标为文字大小，点击目标44，普通字号ID不换行。375/440宽度的原生宿主布局检查通过，快照在本机.build/private-profile-reference-final；随后窄屏额外缩小两按钮文字/宽度，编译通过，但手机再度自动锁定，未继续执行该微调及私信权限的只读宿主复查。无障碍字号保留适配。

Android80项JVM中74通过/6项需显式开启的网络测试跳过，lintDebug及原生测试编译通过；另单独开启的5项私信网络用例全部通过，含1项真实匿名ID查询。iPhone此前10项私信/布局/只读检查通过，随后一次授权发送与独立ID网络回归2项通过；最终移除诊断后的iOS应用及所有测试目标编译通过。日志private-id-final-android.log、private-id-android-live.log、private-id-final-ios-compile.log。原截图615未向另一收件人再次试发，服务端限制不能凭数字错误码推断为代码问题。

遵守暂停打包：没有生成新签名APK/IPA、更新latest或上传；1.3/build69旧包哈希保持，尚不含这些本地修复。手机仅因已授权宿主验证运行Debug，不卸载/清理用户数据。全部使用工具箱现有Xcode27/Swift6.4、JDK21/Gradle8.14/SDK36；用项目既有SwiftProtobuf1.38.1源码编译protoc插件，并复用已有protoc3.25.5生成投影，无新组件/依赖下载或安装。

## 2026-10-29 原生私信查询修正、主页入口与紧凑按钮（本地，暂停打包）

旧官方私信网页登录后跳转 HTTP，改为双端原生消息列表、会话、手动发起与发送。复现用户“打开会话就出现310001”，原 Media fetch_msg 对该查询返回 bjh 错误，不能以此前空会话列表成功视为普通账号接入已完成。对照官方 SDK 区分主页展示 ID、百度 UID、IM UK：通过 get_user_info 解析真实收件人身份，历史查询切换 fetch_msg_by_contacter_client，发送切换 send_msg_client，补齐账号类型、消息 ID 区间及正文发送人/收件人字段。保留有界 HTTPS/JSON、固定目标、取消/换号隔离与发送未知不重试。独立共享 fixture 增补身份和原生消息结构、64位消息ID。

他人主页“发私信”位于关注按钮左侧，使用资料中的真实 UID 直接进入会话；显示 ID 后新增系统剪贴板复制按钮，本人主页保留编辑。私信列表/会话隐藏根悬浮底栏，iOS 在共享浮层消费私信页面状态，覆盖主页入口。按随后 IMG_0234 反馈去掉普通字号下按钮组整行回退：昵称与私信/关注保持同行，按钮最小宽度64，ID/复制使用下一行完整宽度；较长昵称在其区域内换行，无障碍大字号保留适配。

验证：Android77项JVM中72通过/5项真实网络默认跳过，含7项私信协议与模拟发送；lintDebug及原生测试编译通过。iPhone执行7项协议/模拟网络回归和1项真实账号只读诊断，8/8通过；生产客户端成功解析有效账号并查询空历史，未发送/标记已读。尚不能证明存在既往会话时的历史完整性、真实收发或Android真机行为。iOS应用、单元/UI测试目标编译通过；布局快照检查另见logs/private-profile-compact-layout-*。私有诊断已移出源码到忽略目录，证据logs/private-message-native-corrected.log、private-native-corrected-android-v2.log及本机.build对应xcresult。说明见PRIVATE_MESSAGES.md。

遵守“先不打包等我指令”：本轮未生成新的签名APK/IPA、替换latest或上传。现有1.3/build69 APK/IPA SHA256仍为e012b80cd6cbb8b2bf058030785708372928eaf849eb8b711f4742728b5c266f、e84d1f93c835d504f31cdc1e16d4b86ce2c7bc3c3eb03a1e9785b78ec4f03636，仍含旧网页私信。iPhone宿主测试原位运行Debug以验证本地源码，未卸载/清理用户数据；不能等同新发布包交付。工具箱Xcode27.0/Swift6.4、JDK21.0.12.1/Gradle8.14.3/SDK36、Node24.21.0复用；仅下载官方参考APK作本机静态核对，无新组件/依赖安装。

## 2026-10-29 双端底栏收缩/展开过渡优化（仅本地）

原Android宽度/位置280ms与内容200ms互相错开；双端改为同一弹簧进度驱动宽度、横移、缩放和内容交接。使用接近临界阻尼的弹簧、无刻意回弹；反向沿用当前进度，标签0至0.75渐隐、头像0.25至1渐现，平滑交接并保留重叠。iOS用Animatable进度绘制并关闭内部重复隐式动画，Android用单一animateFloatAsState驱动形变；位置偏好切换另保留同类型过渡。固定62高度、正文视口、手势门槛、总开关与无障碍标签保留，尊重系统减弱动画/时长设置。

Android72项JVM中67通过、5项真实网络默认跳过，原生测试编译及lintDebug通过；Swift直接编译运行生产过渡/手势/偏好代码，新回归覆盖1001个进度采样的渐变连续性、单调性、交接非空和端点限幅。iOS应用及测试目标build-for-testing通过，未在iOS设备运行XCTest；未做本轮设备UI、快速反向录屏或帧率验收。日志logs/bottom-bar-smooth-*。仅本地修改，未生成APK/IPA、安装模拟器/手机或上传，版本仍1.2/build68，工具复用且无新增依赖。

## 2026-10-29 双端底栏自动收缩总开关（仅本地）

双端显示设置新增“底栏自动收缩”总开关，默认开启、本机持久化，旧用户无该键时沿用开启。关闭立即展开并停止监听/处理收缩手势，重新开启从展开状态重置累计，不改变正文视口；位置及文字偏好保留，文字开关继续独立生效。iOS复用同一AppAppearanceStore，重置外观同时恢复该开关默认值；Android复用reader偏好及现有底栏shell参数。

Swift直接编译运行生产显示存储/运动代码，新增断言验证默认开启、关闭后重载、重新开启保留位置/文字及重置；Android71项JVM中66通过/5项网络默认跳过，原生测试编译及lintDebug通过；iOS应用/测试目标build-for-testing通过。未做本轮设备UI验收，未生成APK/IPA、更新模拟器/手机或上传，版本仍1.2/build68，latest两份1.2的SHA256不变。日志logs/bottom-bar-master-switch-*，工具全部复用，无新增组件。

## 2026-10-29 消息内私信与模拟器预览

双端消息新增“私信”第三标签，当前采用内嵌贴吧官方HTTPS私信页（非原生重绘聊天列表），以当前账号的最小Secure/HttpOnly会话Cookie加载，由官方页面处理会话与手动收发。iOS独立非持久WebKit存储、会话变化销毁；Android进入前清除旧网页会话/缓存再绑定账号，初始化/导航复核账号，关闭销毁WebView，保留既有退出清理。官网外导航、非HTTPS/非443/嵌入凭据/证书错误被拒绝，未注入原生JS桥；错误可重开固定GET入口，不重放发送POST。Android标签切换复用消息页路由，不再增加返回栈；原回复/@通知、登录门槛和下拉刷新保留。说明见docs/PRIVATE_MESSAGES.md。

Android71项JVM中66通过、5项真实网络默认跳过，应用/原生测试编译与lintDebug通过；专用emulator-5580实际运行1项新增UI回归通过，核对三标签、点击私信、未登录提示且不创建WebView、切换标签后返回“我的”。iOS应用和单元/UI测试目标build-for-testing通过，新增两项导航/Cookie回归仅编译，未运行iOS设备测试；此前底栏/自动化改动包含在本轮预览源码。官方入口匿名请求实际返回百度登录页，真实登录后的私信收发尚未验收，未代用户发送消息。

按本轮“打开模拟器预览”授权，仅生成并原位安装当前源码Debug到可见私有Android35模拟器，版本仍1.2/build68；未生成签名Release/IPA、替换根latest、安装手机或上传源码。latest现有两份1.2的SHA256不变。工具复用工具箱Xcode27.0/Swift6.4、JDK21/Gradle8.14.3/SDK36，无新增组件/依赖。日志logs/private-message-*，模拟器登录门槛截图位于应用external files/private-messages-login-gate.png及本机.build。

## 2026-10-29 双端底栏头像收缩、速度门槛与显示设置（仅本地）

读取用户ScreenRecording_10-29-2026 18-02-51_1.MP4（4.9167秒），按精确时间提取12帧核对完整胶囊/左侧圆形及内容方向。按用户要求双端向下浏览累计24点/dp收缩，快速回看至少12点/dp及650点/dp每秒才展开，慢速保持收缩；补足短促快速甩动释放速度。点击圆形仅展开，不调用切页/刷新/滚动；登录显示用户头像，未登录固定默认头像。设置增加收缩后左/中/右（默认左）及文字开关（默认开）；关闭文字图标23→29并居中，保留无障碍名称，持久化复用已有显示设置。

两端约280ms宽度/位置过渡，固定62高度与原正文视口/底部留白，避免展开推动内容。Android复用现有磨砂层，观察用户消费滚动/释放速度且不消费事件，忽略程序滚动；iOS保留TabView页面导航并统一玻璃浮层，26/27复用glassEffect，旧系统材质回退，尊重减弱动态效果。观察原UIScrollView pan target，不替换delegate/拦截手势；复用原ShortPull滚动查找器，默认刷新匹配门槛不变，另过滤横向及跨边界回弹。更新原“26/27只用系统标签条”的说明以对应本次明确的新交互要求。

Android69项JVM中64通过、5项真实网络默认跳过，原生测试编译和lintDebug通过；新增5项覆盖距离/速度、慢速长距离、快速反向、短促甩动、手动复位/抖动与位置键回退。Swift直接运行生产运动/显示存储代码，覆盖相同手势及持久化/重置/非法值；iOS应用、单元/UI测试目标build-for-testing通过。未执行设备UI/触摸命中/正文位置像素或帧率测试，速度门槛与动画手感待实机验收，详见docs/BOTTOM_BAR.md。

继续遵守“仅本地修改不打包”：未生成APK/IPA、未安装或上传，1.2/build68配置不变；根latest的1.2及releases的1.1共四份包SHA256不变。工具全部复用，无新增组件。日志logs/bottom-bar-*，本机.build/bottom-bar-check及bottom-bar-video；前一轮自动回复图标/历史删除源码继续保留。

## 2026-10-29 自动回复图标、规则开关与历史批量删除（仅本地）

按IMG_0224.jpg与460.heic，双端“自动回复”设置入口补蓝色线条评论图标；每条贴吧规则标题行右侧改用既有系统风格启停开关，与自动签到的右边距一致，开关左侧三点菜单收纳编辑/移除，移除原底部三枚文字按钮。历史任务标题右侧增加选择/一键删除，选中后显示取消/删除数量；选择模式用勾选控件且隐藏原帖/确认回复操作，删除前确认数量。

删除仅作用于本机历史，并取消尚未开始发送的任务，不请求贴吧删评；已经发送中的请求不能撤回。实际移除标题、关键词、消息等内容，仅按账号保留最小帖子ID防重。Android事务批量删除及缓存同步，发送占用后再核对删除标记；iOS原子状态提交移除已删除ID，阻止晚到回执恢复记录。新可选字段兼容旧1.2状态文件，清空释放历史条目额度但保留去重标记。

Android完整JVM64项中59通过、5项真实网络默认跳过，原生测试编译与lintDebug通过；新增引擎回归覆盖选择删除、清空、发送前取消、补扫不复活与晚回执不恢复，SQLite持久化/账号隔离原生用例仅编译未上机运行。Swift直接编译生产Store/Engine运行模拟后端，验证真实本机文件删除/重载、旧状态兼容、账号隔离及并发回执；iOS应用和单元/UI测试目标build-for-testing通过。设备UI未验收。

遵照用户“仅本地修改不打包”，未生成APK/IPA、未安装、未提升版本或上传；仍为1.2/build68配置，latest两份1.2及releases两份1.1的SHA256核对不变。日志logs/automation-history-ui-*，本机.build/automation-check/history-check；工具全部复用，无新增组件。

## 2026-10-28 重制自动化之前的 1.1 历史包

按用户指令，在独立的.build/historical-1.1-pre-automation/source重建“加入自动化前”的源码：保留双端楼中楼秒数、Android吧内图片、置顶折叠及过渡动画；移除自动回复代码、设置入口、账号/生命周期接入、后台服务权限及iOS后台刷新注册，签到分组恢复原样。仅历史包标为1.1/build67；当前工作区配置及源码继续1.2/build68，418个生产/测试/构建文件哈希与构建前一致。

双端签名包已替换releases内原来的两份1.1历史文件，原件移入本机忽略目录的replaced-history备份。新APK为releases/TiebaPure-1.1-8ceb7751cf13/TiebaPure-1.1.apk，新IPA为releases/TiebaPure-1.1-2fb6e1cdefc4/TiebaPure-1.1.ipa。根latest两份1.2的名称、大小、SHA256完全不变，未向手机/模拟器安装、未上传。

Android46项JVM中41通过/5项网络跳过，lintRelease、签名/标识/版本/ZIP/16KB对齐、无自动回复DEX和新增保活权限检查通过；确认含置顶展示与秒数格式。iOSRelease签名归档/导出、严格签名、主应用及扩展版本、无自动回复类型/后台注册、原生产日期格式器Foundation自检通过。两端59份原生表情逐项哈希一致。未执行历史包设备UI验收，以免影响当前1.2。Xcode27.0/Swift6.4、JDK21.0.12.1/Gradle8.14.3/SDK36全部复用，无新增组件；iOS开发签名仍至2026-11-03 10:05:48北京时间。

源码恢复清单、检查及替换记录见本机.build/historical-1.1-pre-automation/manifest.json、source-audit.json和SHA256SUMS；构建日志在该目录source/logs。完整历史交付说明见docs/releases/1.1.md最上方。

## 2026-10-28 1.2/build68 双端签名交付与手机更新

按用户指令将共享配置从1.1/67升到1.2/68，打入此前未封包的双端楼中楼秒数、Android吧内配图/置顶折叠动画及双端自动化/自动回复/历史原帖入口。Android签名Release、lintRelease、62项JVM（57通过/5项网络跳过）及配置存储9项通过；iPhone宿主46项回归通过，未真实自动发评。签名、版本、ZIP、两端59份原生表情哈希与Android16KB对齐核对通过。包在根latest，旧1.1同平台包自动归档到releases且旧哈希不变。

已从最终IPA原位安装到iPhone、查询1.2/build68并启动。测试/更新前备份746文件/70409310字节逐项验证；原有32个业务持久化文件在测试和安装后SHA256不变。最终安装前后52个Library持久化文件中48个一致，4个变化为系统启动画面快照。未卸载/清理设备数据。开发签名至2026-11-03 10:05:48北京时间。工具箱复用Xcode27.0/Swift6.4、JDK21.0.12.1/Gradle8.14.3/SDK36/Node24.21.0，无新增组件。

完整交付见docs/releases/1.2.md及本机.build/release-records/1.2-build68/manifest.json，日志logs/build68-*。本轮未推送源码/上传包，未做真实自动回复、长期后台或Android真机验收；以下“仅本地/未打包”是对应历史阶段的结果。

## 2026-10-28 双端自动化、自动回复及历史任务（仅本地）

设置“签到”分组更名“自动化”，原签到选项保留。双端新增已关注贴吧规则、任一关键词匹配标题/首楼正文、原生表情回复编辑、0至3600秒延迟，默认关闭。正常界面启动8秒后检查，首次规则仅建立基线；在线每60秒检查新增帖子，离线/停线补扫命中进入待确认，未命中不提醒。分页保留进度，刷新第一页后继续，短页不作为扫描结束依据；已提醒状态随任务持久化，覆盖iOS后台刷新先发现、稍后回前台的情况。

按用户追加要求，两端历史列表包含帖子、贴吧、关键词、回复快照、发现/计划时间、待确认/等待/发送中/成功/失败/不明/忽略状态及说明；每条可打开原帖，iOS成功回执含回复ID时可进一步定位。补扫任务逐条同意或忽略；仅关闭弹窗不会批准发送。修改/关闭/删除规则取消对应待执行任务，换吧重新建基线，退关注不发送；旧账号任务在退出/切换时取消并隔离。写前持久化占用、发送前重新核对账号/规则/关注/任务，未知或失败暂停且不自动重试。

Android采用显式开启的dataSync前台服务、常驻通知与可停止保活操作，申请通知权限；系统超时、清除任务停止，不自启动。iOS使用合法短暂后台窗口及BGAppRefresh，系统调度不能提供持续实时监测，后台补扫任务仍须前台确认。账户规则/游标/任务仅本机保存并排除导出备份，无新增依赖。使用/限额/后台边界见docs/AUTOMATION.md。

验证：Android完整JVM62项，57通过、5项真实网络用例默认跳过，其中自动回复16项通过；应用/原生测试编译与lintDebug通过。Swift直接编译生产Models/Store/Engine并运行模拟后端场景，覆盖基线、标题/正文、延迟、补扫确认、持久化、已提醒去重、分页短页/续扫、账号/规则变化、退关注与未知结果不重发；iOS应用、单元/UI测试目标build-for-testing通过，未执行iOS设备测试。日志logs/automation-android-verified.log、automation-swift-core-final.log及automation-ios-tests-final.log，本机自检入口.build/automation-check/。编译警告包含既有XCTest最低系统链接及AppIntents提取提示，不影响本轮编译。

本轮未真实自动发评、未后台长时间实机验收，未生成APK/IPA、安装手机、改版本或上传。当前latest仍为1.1/build67，APK/IPA大小及SHA256与原交付记录一致。复用工具箱JDK21.0.12.1/Gradle8.14.3/SDK36与Xcode27.0/Swift6.4，无下载/安装新组件；此前楼中楼秒数、配图和置顶动画修改继续保留。

## 2026-10-28 Android 置顶过渡动画与模拟器预览

按用户要求为置顶展开/收起增加过渡：复用Compose LazyColumn稳定条目key及animateItem，新增条目200ms渐入、移除160ms渐出，下方帖子使用280ms FastOutSlowIn缓动移动；箭头以240ms旋转到展开/收起位置，可随状态反向过渡。使用现有Compose1.9.2，无新增依赖，不以整页高度动画替代真实条目位移。

assembleDebug及lintDebug通过。包含此前配图、置顶折叠和楼中楼秒数的新Debug已用-r原位安装在可见emulator-5580，进入奥比交易吧：真实图片显示，默认置顶2条收起；实际点击展开出现温馨提示/防骗置顶帖，连续往返后回到普通帖子，最终留在默认收起界面，未见崩溃/ANR。截图及录屏在本机.build/forum-pinned-animation/和.build/forum-preview-pins/emulator/。录屏的本机AVFoundation解帧失败，未将其作为逐帧性能验收，未做真机帧率测试。

预览APK位于android/app/build/outputs/apk/debug/app-debug.apk，仍为1.1/build67测试构建；未生成新签名Release、替换latest、安装手机或上传源码。工具复用JDK21.0.12.1/Gradle8.14.3/SDK36及API35模拟器，未下载或安装新组件。日志logs/forum-pinned-animation-*与forum-preview-emulator-*；此前双端时间修改继续保留。

## 2026-10-28 Android 贴吧列表配图与置顶折叠（仅本地）

按49.JPG，贴吧JSON帖子原先未读取media，previews始终为空；补齐src_pic/big_pic/dynamic_pic/origin_pic的首个有效可信HTTPS地址，兼容旧HTTP图片并沿用主页最多9张的数据上限，列表继续复用主页既有摘要下方、评论点赞行上方的图片布局及手动加载设置。iOS原映射已读取media，本轮不修改其列表。回归先复现有图片但返回空列表，再修复通过；匿名读取奥比交易吧的普通帖并实际请求首张预览，HTTP成功且返回image内容。

按51.JPG与iOS源码，Android新增置顶内容入口：灰色图钉、数量、展开/收起箭头，默认收起，展开后置顶排在普通帖子之前，无置顶时不显示入口；更换贴吧或分类重置收起。Thread保留服务端is_top，并兼容持久化和旧缓存默认非置顶。展开/收起仅改变展示，不删除列表数据。图钉复用本地矢量实现，无新增依赖。

Android完整JVM46项中41通过、5项网络默认跳过；另单独开启真实公开图片检查1项通过，compileDebugAndroidTestKotlin及lintDebug通过。验证包括分类请求链配图、图片来源回退/安全校验/数量限制、置顶过滤与展开排序、旧缓存与JSON往返；设备点击和视觉未运行。此前双端楼中楼秒数修改保留。本轮未打包、升级版本、安装或上传，现有build67 APK/IPA哈希核对不变。日志logs/forum-previews-red.log、forum-previews-pinned-local-final.log、forum-previews-live.log，本机.build/forum-preview-pins/。

## 2026-10-28 楼中楼时间补充秒数（仅本地）

按用户最终更正，双端楼中楼回复页时间统一HH:mm:ss「MM-dd」，例09:08:07「10-28」。复用既有subpost格式入口，覆盖iOS回复页父楼/子回复及离线回复页、Android楼中楼页父楼/子回复。只补秒数，保留月日连字符、设备本地时区、公历、IP及缺失时间处理；首楼和普通评论沿用各自格式。

既有两端日期断言同步更新。Android定向JVM两项通过；从当前iOS源码提取原格式器及元数据文本的Foundation自检通过，覆盖秒数、补零、跨年、本地时区、非公历系统日历及IP/空日期。未运行设备UI检查，未打包、提升版本、安装或上传；当前latest与手机仍为此前build67。日志logs/subpost-timestamp-seconds-android-final.log、logs/subpost-timestamp-seconds-swift-final.log，本机.build/subpost-timestamp-seconds/。

## 2026-10-28 1.1/build67 签名交付与源码分支

汇总此前输入焦点、双端时间格式、Android顶部及贴吧分类调整，版本统一1.1/build67。iOS回复失败回包的零tid/pid原先被提前视为不明结果，遮蔽业务拒绝、登录和验证提示；现仅在成功回执阶段验证ID，冲突状态及成功ID校验、未知结果不自动重试的保护保留。真实匿名空内容响应复现该处理问题，回归先失败后通过；Android同一响应原先已正确分类，补入一致性回归。手机只读账号/网页校验正常，空内容预检不产生评论；用户随后明确确认新版可以发送，再次授权打包。

最终APK/IPA均完成签名、版本/标识、ZIP及59份原生表情SHA256核对，APK使用原Release签名并通过16KB对齐。iPhone从最终IPA原位安装、查询1.1/67并启动；更新前备份656文件/76354563字节，与设备大小逐项一致；安装后启动前28个持久化文件完全一致。开发签名至2026-11-03 10:05:48北京时间。当前同平台旧包自动进入releases，当前包位于根latest。

本轮36项iOS发布工厂/网络回归真机通过；此前同源焦点/时间/回复等137项通过、4项真实账号用例跳过。Android38项JVM通过、4项网络默认跳过，lintRelease通过；配置与包存储9项通过。Android共6项不同原生用例通过：冷启动后主组5项通过，跨页面顶栏用例刷新无障碍缓存后单独通过。首次运行受System UI无响应弹窗干扰；旧坐标缓存导致的测量失败保留日志，未计为通过。当前界面重新读取确认48dp按钮区域，跨页面高度与帖子页一致，装饰背景另由截图核对。独立iOS UI Runner仍受签名数量限制；真实账号发评由用户手动确认，未代发或代删。临时当前账号诊断测试与所有设备备份仅保留在.build，不上传仓库。

当前源码按用户指令创建并推送1.1分支，运行时代码提交16a98dd；上传前检查444个可纳入文件未检出凭据模式；签名/账号/本机备份/安装包由Git忽略。工具复用/Users/y/toolchains，未下载或安装新组件，未创建GitHub Release。完整路径、包哈希、安装及测试证据见docs/releases/1.1.md、本机.build/release-records/1.1-build67与logs/build67-*；以下未封包条目为历史阶段。

## 2026-10-27 双端时间格式及 Android 全局紧凑顶栏（仅本地）

按40.JPG与208 2.JPG，双端首楼发帖时间改为HH:mm:ss「yyyy-MM-dd」，例08:58:07「2026-10-25」；楼中楼回复页统一HH:mm「MM-dd」，例08:58「10-25」。iOS覆盖回复页父楼、子回复及离线回复页，首楼在线/离线复用PostRowView；Android按主楼/楼中楼route显式选择。使用设备本地时区、24小时与公历、数字补零，保留IP属地；缺失时间仍不虚构时间，普通楼层和列表相对时间沿用原格式。iOS复用带格式/日历/时区/locale缓存的DateFormatter，Android复用不可变DateTimeFormatter。

用户追加要求安卓其余顶栏均与已缩小的帖子页一致：FrostedAppShell统一56dp导航高度、18dp图标、36dp圆形按钮背景及36dp高操作组背景，返回/搜索保留48dp触控区，组合按钮保留原触控高度。覆盖楼中楼、首页、贴吧、设置等所有公共顶栏，并同步正文/提示栏顶部留白；论坛右侧图标也缩至18dp，吧名继续纯文字，14sp及左右留白按紧凑尺寸适配。帖子详情原有紧凑尺寸保持一致。

验证：Android42项JVM中38通过、4项真实网络默认跳过；应用与原生测试编译、lintDebug通过。iOS应用/单元/UI测试目标build-for-testing通过，提取原生产日期/属地格式器的Foundation自检通过，覆盖准确字符串、补零、时区跨日/跨年、非公历系统日历、空日期及旧普通评论格式。新增两端日期用例及安卓各页面实际顶栏高度/背景/触控区原生回归。设备UI用例仅编译，未安装执行；本轮未打包APK/IPA、未改版本、未更新手机，当前latest仍为1.1/build65且SHA256不变。此前输入焦点和论坛顶栏/分类改动均保留待封包。

证据：logs/post-timestamps-swift-check.log、logs/post-timestamps-ios-compile.log、logs/post-timestamps-compact-toolbar-android.log，本机.build/post-timestamps/。未下载或安装新组件。

## 2026-10-27 Android 贴吧顶部按 iOS 参考调整（仅本地）

按用户截屏2026-10-27 20.39.04及后续确认，Android贴吧列表顶部改为返回键右侧的纯文字吧名，无头像/胶囊背景；右侧同一磨砂胶囊提供关注星标、发布新帖、搜索本吧、更多。移除分类行原有发帖/关注/取消关注文字按钮，更多菜单保留刷新和关注切换；复用既有发帖设置、登录校验、会话隔离及真实关注接口。关注状态读取失败时可重试，服务端成功才更新选中状态。帖子详情的吧名胶囊保持原设置。

分类行按iOS参考改为“最新”下拉（回复时间排序/发帖时间排序）和独立“精华”胶囊，蓝色选中及淡蓝底，未选中灰字；选择后重置分页与旧列表。贴吧分类的请求与最新选中态不再依赖评论区共用sort值，始终按当前route.extra构造0/1/-1分类请求，防止回复热门/正倒序影响贴吧分类。

调查边界：匿名读取奥比交易/旅行三个分类返回不同ID列表，精华原始响应为精华帖；在已安装build65的奥比交易真实页面也观察到回复/发布/精华标题变化，未持续复现用户所述全部不变。修正已明确的共享状态风险，并用API集成回归核对分类参数/列表/分页/离开精华；原生UI回归从只检查变量升级为核对实际列表、菜单与顶栏入口，并加入评论sort被修改后的分类一致性用例。

验证：Android完整JVM40项中36通过/4项网络默认跳过；另单独开启匿名分类读取时2项API回归均通过。应用/原生测试编译与lintDebug通过，git diff --check通过。新版布局/菜单与关注写入未上机验收：遵守用户“等指令再打包”，未生成APK/IPA、未更新手机、未提升版本；当前latest仍为1.1/build65且SHA256未变。截图/原始接口/界面层级记录在本机.build/forum-category/，日志logs/forum-category-*与logs/forum-header-local-checks-final.log。前一轮iOS输入焦点修复继续保留、同样尚未封包。

## 2026-10-27 回复打字卡死修复（仅本地，等待用户打包指令）

用户明确继续修改，要求收到后续指令再打包；本轮不生成APK/IPA、不提升版本、不安装或更新手机。当前latest及手机仍为此前1.1/build65，尚不包含本轮修复。

从已连接iPhone只读取得4份真实崩溃报告，均为0x8BADF00D看门狗终止；主线程卡在NativeEmoticonEditor.updateUIView同步调用UITextView.resignFirstResponder并进入AttributeGraph焦点更新循环。正文自定义UIKit编辑器原先复用了没有对应SwiftUI焦点节点的.body FocusState。现改为独立正文焦点状态，标题继续使用SwiftUI焦点；焦点与可编辑性变更统一在界面更新结束后合并执行，原生开始/结束编辑同步最新意图，避免旧排队请求反向抢焦点。冗余正文/选区绑定写入也加相等判断，表情资源通知回到MainActor后再处理。

检查：实际崩溃堆栈与源调用链吻合；本地Foundation边界回放旧同步路径为失败，新队列10000请求、最新意图合并及嵌套请求检查通过。iOS应用/单元/UI测试目标build-for-testing通过；新增完整SwiftUI回复页持续输入、中文合成和焦点保持测试已编译，未安装测试宿主执行。Android35项JVM通过/3跳过，原生测试编译通过；当前已安装构建的真实Android Span/光标区间探针通过，未发现同类SwiftUI焦点问题。补入真实InputConnection分阶段中文合成、表情后输入与删除的原生回归，待允许测试打包后运行。独立shell进程的IME探针因系统字体初始化失败，未计为应用测试结果；Android业务源码未做无证据修改。

诊断证据保留在本机.build/reply-input-freeze/，日志logs/reply-input-*、logs/reply-focus-*。版本/配置及当前APK/IPA的SHA256核对未变；未安装组件或下载资源。后续打包授权到来时应先执行新增设备键盘用例，再重新签名交付。

## 2026-10-27 全局原生表情与回复修复、1.1/build65 交付

完成双端贴吧原生表情适配：共用59个原始PNG，补齐Android原生名称/c字段/数字ID及JSON摘要解析，正文/评论/楼中楼/预览/离线内容使用行内图案，普通照片保留；有真实图片时去掉上传占位标记。双端编辑器显示图案但持久化与发送保留原生编码，支持光标插入、选区、退格、复制剪切粘贴和中文合成保护。iOS表情格去掉贴图底板，直接InlineContentText及标题高亮入口也识别表情。

iOS作者行重复回复图标已回退，仅保留下方回复。Android回帖条固定页面屏幕底部，正文预留空间；原生验证滚动前后位置不变。双端单条楼中楼也可展开，进入详情使用本人回复删除入口，旧作者/会话校验及确认保持。

Android35项JVM通过/3跳过，lintRelease、2项原生与签名/16KB检查通过；iOS真机96项不同检查通过，含实际UITextView插入退格与截图，UI Runner未执行。配置/存储9项通过。真实账号公开发送/删除未执行。用户截图未提供具体帖数据，不能将夹具覆盖等同于所有服务端内容均已实测；未知表情名称仍有可读回退。

两端1.1/build65签名包已写入根latest，旧同平台包自动进入同级releases；iPhone已原位安装最终IPA并查询版本、启动。备份444文件/56415041字节；安装前后22个持久化文件完全一致，原始业务记录保留，新增一条浏览历史与相关元数据变化单独记录。开发签名至2026-11-03 10:05:48北京时间。完整哈希和范围见docs/releases/1.1.md及本机.build/release-records/1.1-build65/。

全部复用工具箱，未安装新组件，仅下载59个贴吧原生图案；未推送或公开发布。以下build64及本地回退条目为历史阶段记录。

## 2026-10-27 iOS 回复图标回退（本地源码）

按用户36.JPG，仅移除iOS作者行、点赞左侧新增的回复图标，覆盖首楼、普通评论、楼中楼父层和子回复。复用UserHeaderView原有单独点赞布局；保留下方时间/IP行的“图标＋回复”、正文点按回复、回帖默认设置及删除本人评论。Android不变。UI回归改为使用下方回复入口，并检查旧作者行图标不存在。

本次不更改1.1/build64配置或现有安装包，不更新手机；当前latest与已安装手机仍为上一阶段build64，尚不含此次图标回退。编译验证记录为logs/ios-reply-icon-rollback-compile.log，实际结果以下续记为准。

## 2026-10-27 双端回复与删除修复、1.1/build64 打包

修复iOS评论回调因默认回帖关闭而置空、禁用变浅的问题。双端回帖默认开启，主动关闭明确保存；所有在线首楼/普通评论/楼中楼入口保留真实回调，未登录及关闭回帖时有提示。评论线条使用未点赞状态相同颜色，离线不增加写能力。两端本人评论及楼中楼在时间/IP行增加删除及确认，接口验证作者、目标完整性、账号会话；仅服务端确认后移出列表，响应不明不自动重试，主楼仍走原有删主题流程。

Android33项JVM通过/3项真实网络跳过，lintRelease及签名包检查通过；最终API35原生集成用例通过，覆盖3类按钮、实际编辑器/请求映射/模拟回显、2类删除、取消和权限/登录/关闭拦截。iOS真机68项单元通过/4项真实账号测试跳过，包含回复及删除夹具、真实请求工厂和模拟响应、结果不明/账号隔离。UI用例编译通过，执行受到免费签名3应用限制；未卸载其他应用。Mac兼容测试宿主启动也失败，未计为通过。真实账号公开发评/删评未执行。

按用户“完成后打包”生成latest/TiebaPure-1.1.apk与TiebaPure-1.1.ipa，均为build64；证书、版本、标识、ZIP及实现打入检查通过，APK16KB对齐，旧包分别自动归档releases。iPhone已从测试宿主原位换回签名Release、查询版本并启动；备份340文件/52746642字节，原有历史/最近吧/阅读位置/草稿/收藏业务字段核对保留，数据库日志/排序索引及风险偏好变化单独记录。未卸载或清理用户应用容器。

完整产物哈希、验收边界和工具版本见docs/releases/1.1.md及本机.build/release-records/1.1-ios-build64/。iOS开发签名有效至2026-11-03 10:05:48北京时间。工具全部复用/Users/y/toolchains，未新增组件，未推送或发布。

## 2026-10-27 iOS 1.1/build63 原位安装

按用户指令共享版本更新为1.1/build63，生成开发签名Release及`latest/TiebaPure-1.1.ipa`，在已连接iPhone 18 Pro Max上从1.0/build61原位安装、启动并查询确认1.1/build63。主应用及Open In扩展均通过版本/标识/签名/登记设备校验；开发签名有效至2026-11-02 22:44:23北京时间。Android源码配置随共享版本更新，未生成APK，latest中的1.0/build62 APK内容不变；旧IPA自动归档至同级releases。

更新前备份322文件/47294642字节，文件大小与手机清单逐个一致；更新后、启动前10个持久化文件SHA256不变，未卸载或清理设备容器。配置及存储9项测试通过，签名归档、导出、ZIP和codesign检查通过；本次只确认安装与启动，未执行设备UI自动化或真实账号发送。包SHA256及完整验证见docs/releases/1.1.md、本机.build/release-records/1.1-ios-build63/manifest.json。工具复用Xcode27.0/Swift6.4/iOS27 SDK/XcodeGen2.46.0/Node24.21.0，未新增组件、推送或发布。

## 2026-10-27 帖子顶栏、正文与双端评论入口（本地源码）

范围按用户补充：顶栏、正文过滤修复和评论间距仅Android；点赞左侧的评论按钮双端新增，覆盖首楼、普通评论及楼中楼。复用现有编辑器、回复目标、账号校验和回帖开关；离线内容不启用写操作。iOS保留原有底部回复入口，未调整顶栏或评论间距。

Android帖子顶栏由64dp收至56dp，按钮图标18dp、返回玻璃36dp、右侧玻璃36dp高，保留48dp点击范围；吧名区域左右预留从64/168dp改为52/148dp，胶囊头像18dp、文字14sp，常规360dp宽度下为约7字吧名（另加“吧”）预留空间。评论时间/IP上下留白10/8dp改为5/4dp，楼中楼内边距和条目间距收紧，查看全部回复入口视觉高度44dp改为32dp；Android原行尾回复文字移至作者行评论图标。

用户提供的https://tieba.baidu.com/p/9107183498已匿名复现：首楼150670290200的正文由接口正常返回，但携带advertisement_test1占位标记，被旧版hasAdvertisement判断整层删除。Android仅对具有非零ID及正楼层的该占位标记放行，保留其他广告过滤与用户屏蔽。新增原映射链路回归，覆盖首楼/普通回复/独立first_floor_post、真实广告及无效占位楼；真实指定帖子回归也从失败转为通过。未改协议schema或iOS正文过滤。

验证：Android34项JVM测试通过、2项无关真实网络测试默认跳过；指定帖子匿名真实读取包含在通过项中，lintDebug及compileDebugAndroidTestKotlin通过。iOS应用、XCTest和UITest目标build-for-testing通过。原生回归代码补充评论/点赞顺序、点击范围及现有回复流程，但未执行设备UI测试：当前ADB无设备、simctl无iOS运行时。常规字号以外的吧名显示、设备手感及真实账号发送尚未验收。日志logs/thread-body-red.log、thread-ui-android-checks.log、thread-ui-ios-checks.log；早期匿名探针已移除，仅保留回归测试。

仅本地源码与编译检查，未生成新APK/IPA、更新latest/release、发布或安装设备；未下载/安装新组件。

## 2026-10-27 最新包与历史包同级目录

按用户最新指令，将`latest/`移到工作区根目录，与`releases/`同级：新签名APK/IPA各保留一份在latest，同平台出现不同内容的新包时，旧包自动移入releases的版本/哈希子目录，再替换当前包；另一平台不受影响，相同内容不重复保存。Debug和未签名包继续只进releases，不替换可安装的当前签名包。

用户已自行删除archives，未恢复其内容。现有APK、IPA已从releases/latest移动到根目录latest，SHA256保持不变；私有签名文件未操作。存储及配置测试9项通过，覆盖APK/IPA独立替换、同版本重打包、跨版本归档、去重及归档冲突保护；Shell和Node语法检查通过。未重新打包、执行完整双端构建、发布或安装设备。当前包路径以本节为准。

## 2026-10-27 latest 改名与历史安装包清理

按用户指定拼写，将当前交付目录改为`releases/latest/`，仅含现有APK与签名IPA。历史版本里的2份源码ZIP、2份manifest及2份SHA256SUMS完整移至`archives/releases/`，保留原版本子目录并逐文件核对SHA256；该归档目录加入Git忽略。随后删除用户指定的历史APK与未签名IPA各1份，共18158609字节，并移除空历史目录。当前安装包及私有签名文件未变化。

共享存储脚本、测试与构建文档已改用latest；保留后续更新时的旧包归档行为，不会提前生成空历史目录。存储与配置测试共9项通过，Shell与Node语法检查通过，未重新构建或发布。清理记录在`.build/release-records/storage-cleanup-2026-10-27.json`；以下记录中的旧路径与旧包保留状态仅代表对应阶段。

## 2026-10-27 安装包目录整理

本机交付路径统一为`releases/当前安装包/`，当前仅含TiebaPure-1.0.apk（Android build62）及TiebaPure-1.0.ipa（签名iOS build61）。旧发行归入`releases/历史版本/1.0-build61/`，早期源码及其记录归入`releases/历史版本/1.4.15-build59/`；当前Android构建记录移至`.build/release-records/1.0-android-build62/`。以下早期记录中的路径为当时位置，以本节为准。

核对整理前后所有APK/IPA/源码ZIP的SHA256，6种不同内容全部保留，合并1份重复APK，当前两端包内容与原签名不变。打包脚本改用共享存储函数：当前每个平台只保留一份，替换时自动归档旧包，Debug/未签名包只进历史目录，重复构建去重。未重新打包、发布或安装设备；存储逻辑与脚本语法单独验证，未重新执行完整双端构建。迁移清单在`.build/release-records/storage-migration-2026-10-27.json`。

## 2026-10-27 Android 1.0 / build62 本地重打包

按用户指令重新构建当前Android源码，包含本轮头像/昵称、分类切换、返回首页不刷新、磨砂工具栏/菜单、大圆角、首楼布局与白色开屏修改。显示版本保持1.0，内部构建号由61升为62，文件名TiebaPure-1.0.apk。共享app-config已更新62；未重建或更换iOS应用，现有iOS安装仍为build61。

签名Release：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/releases/android/2026-10-27-build62/TiebaPure-1.0.apk，12470186字节；SHA256为bd863bdd6e12a5aa36da9bac119ba915406e9f4d0a5fb8aa89f7f6dac37f20a7。同内容更新至releases/android/TiebaPure-1.0.apk；先前公开发行releases/1.0中的旧包保持不变。日期目录包含manifest.json与SHA256SUMS。

配置测试3项通过，JVM32项通过/2项真实网络测试默认跳过，lintRelease与assembleRelease通过；APK签名、16KB页对齐、ZIP完整性、应用标识/名称/版本、非Debug属性及未打入测试账号检查通过，签名证书与原1.0发行完全一致。该签名Release未另做安装/启动验收；此前最新Debug上的原生12项及“我的”模拟验证另1项见上一阶段记录。

工具：JDK21.0.12.1、Gradle8.14.3、AGP8.13.0、Kotlin2.2.20、Android SDK/Build Tools36/36.0.0、apksigner0.9、Node24.21.0，全部复用/Users/y/toolchains，未下载或安装新组件。源码工作区含尚未提交的本轮修改；本次只生成本地安装包，未推送GitHub、更新发行或安装用户真机。

## 2026-10-27 Android 头像、导航与磨砂工具栏（本地预览）

最终返回首页复查：安装中的原生回归backFromThreadRestoresHomeWithoutRefreshing再次通过，确认复用同一ScreenData、页码不变且未进入加载状态；随后在首页下滑浏览两屏，实际打开帖子，分别用顶部返回和Android系统返回键返回。两种入口及延后复查的所有可见文本与屏幕坐标均与进入帖子前一致。证据home-return-final-check.log、home-return-scroll-check.txt及home-return-*.xml/png在logs/avatar-investigation/。本次仅检查，未重新构建安装包、修改应用逻辑或发布；尚未进行真机复测。

头像旧域名 tb.himg.baidu.com 在本机真实请求中证书主机名不匹配；统一迁移到经验证可用的 himg.bdimg.com，保留 HTTPS、域名白名单及原有网络限制。补齐 JSON 昵称别名、protobuf 备用头像字段，以及空 author 对象从 user_list 逐字段回填；我的页面通过当前会话资料补全显示身份。贴吧网格、最近浏览、关注列表、帖子工具栏和个人资料中的吧头像统一加载真实 URL，缺失时从公共接口补取；本地元数据补全不改变最近浏览顺序，也不重建已删除记录。失败加载保留文字占位。

最新回复、最新发布、精华切换清理上一分类条件；返回首页复用缓存内容和页码，顶部下拉刷新与底部自动续页保留。我的页面、分组、图片及相关组件改用大圆角，页面工具按钮与菜单使用线条图标，悬浮底栏保留原图标。

首楼按最新参考图改为头像、昵称、紧凑等级牌和浅蓝楼主牌同行；点赞图标/数量位于该行右侧，原底部蓝色点赞入口删除，回复、楼中楼、离线内容保留。标题移到用户信息下方；正文图片按实际图片比例撑满内容宽度，保留查看大图与本地文件校验，日期地区显示在正文后。首楼与评论复用同一作者/点赞组件，网络成功后才更新点赞状态，原账号会话隔离保留。

安卓启动使用纯白背景和已有蓝色“贴”字，不加入标题或其他品牌图。API31+启动图标增加安全内缩，避免系统遮罩切掉笔画；旧版使用居中图加白色启动背景。API35模拟器冷启动录屏已抽帧核对完整字形，见本机logs/avatar-investigation/splash-cold.mp4及splash-cold.png；未在旧Android或其他厂商真机验收。

顶部按钮和更多菜单复用 Anyu 的 blur20/saturate120% 磨砂采样。工具栏下缘增加 8dp 留白，保留 48dp 点击范围并消除父层对子按钮的裁剪。按最新指令交换按钮与整块灰色顶栏的覆盖色 alpha：浅色模式按钮0.90、顶栏0.42；深色模式按钮0.90、顶栏0.72，磨砂算法和底栏不变。贴吧名称胶囊单独按截图缩至常规字体下36dp可见高度、纯色不透明背景（浅色为白），保留48dp点击范围；该胶囊不再使用磨砂采样。帖子菜单提供刷新、保存到本地、复制链接、浏览器打开、分享，并保留本人的删除确认和三种保存模式。

验证：默认 JVM 32项通过、2项网络测试默认跳过；单独启用的匿名真实接口测试1项通过，包含吧头像、作者昵称、个人资料与图片响应。Lint、Debug及设备测试目标构建通过；最新应用上的原生12项测试通过（final-native.log），包含头像解码/失败占位、竖图宽高比、用户信息/点赞位置/紧凑标记、顶栏点击范围与下缘留白、五项菜单及保存选择、导航、自动续页/顶部下拉与本地存储。证据在本机 logs/avatar-investigation/。已更新专用 emulator-5580 原生窗口；真实账号身份回填、账号关注列表及发帖等写操作尚未使用用户账号验收。

预览产物：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build/outputs/apk/debug/app-debug.apk。工具为共用工具箱 JDK21.0.12.1、Gradle8.14.3、ADB35.0.2及既有API35模拟器，未下载或安装新组件。未改动iOS、公开1.0发行安装包、签名密钥或用户真机。

按用户“进行模拟验证”的指令，另用测试内存中的虚拟账号，在真实IOSMeScreen验证空昵称占位、资料回填、头像成功加载及UID显示；不调用登录、不写入任何账号凭据，测试结束恢复匿名状态。用例myPageRepairsAnEmptyCachedNicknameAndDisplaysTheAvatar通过，截图my-account-simulated.png明确标注“模拟验证·非真实账号”；不能据此声称已验证用户本人的登录资料。专用AVD原hw.keyboard=no，已开启实体键盘支持并重启保留用户数据，屏幕键盘保持开启；ADB向实际搜索框输入TiebaPure并读取EditText核对通过，未提交搜索，Mac实体键盘人工输入尚未由用户确认。

## 2026-10-26 1.0 / build61 发布

当前配置为1.0/build61。关于页删除指定二改说明，项目名称/许可条目改为TiebaPure，源码链接为https://github.com/Anyu1014/TiebaLite；仓库中保留上游版权、来源和第三方许可。源码发布目标为公开仓库的1.0分支，发行标签1.0，安装包命名TiebaPure-1.0.apk与TiebaPure-1.0-unsigned.ipa。

iOS26/27恢复系统原生TabView标签栏，使用系统液态玻璃及拖动选择；此版本不挂接自定义底栏或拦截系统标签栏代理。旧版保留兼容底栏。底部内容继续延伸到屏幕边缘，普通切换不会刷新首页，顶部下拉手动刷新。已添加原生拖动UITest；无模拟器运行时，仅编译该测试目标，尚未自动执行拖动手感验收。

Android首页缓存内容、页码、失败重试状态及独立滚动位置；账号变化清理缓存，失败续页仍可按原追加模式重试。列表与帖子支持顶部下拉刷新、底部自动续载。评论区按用户IMG_0114.PNG调整筛选/排序条、头像、等级、点赞数、日期地区和灰色楼中楼预览；数据来自已有公共protobuf字段，保留屏蔽、回复开关、离线全文及展开入口，增加独立wire fixture和旧备份兼容测试。

iOS开发签名主应用与扩展验证通过；已在连接的iPhone18ProMax上从build60原位更新至build61并启动。更新前完整容器备份123文件/26643071字节，更新后10个持久化文件SHA256不变，无卸载或容器清理。设备备份、描述文件、私钥和安装日志不公开。开发签名仅支持登记设备，过期需重新签名；本机签名IPA保存在releases/ios/TiebaPure-1.0.ipa，公开发行使用未签名包。

验证与安装记录：本机logs/release-1.0-*，公开范围见docs/releases/1.0.md。工具均使用/Users/y/toolchains；没有下载/安装新SDK或运行时，也未操作其他手机应用。自动配置本项目匹配的Apple描述文件不等同于安装新工具。

## 2026-10-26 自动续载与 1.0 关于页面

双端共用版本改为1.0，build59保持；关于页项目作者显示Anyu，按用户截图展示版本、作者、介绍和开源许可分组，保留infinityf4p/TiebaPure-iOS来源署名、GPL和SwiftProtobuf许可正文。Android另保留实际依赖通知和Protobuf BSD正文。许可证在独立页面本地读取，Android中的SwiftProtobuf明确标注为上游iOS依赖。

Android 列表、帖子回复和楼中楼改为触底自动追加，保留加载指示及失败重试，重复/空页停止续载，连续阅读按可见楼层所属页保存位置。iOS 保留原有可见行自动预加载并移除正常列表/回复的手动加载按钮；过滤后的空状态保留有界继续操作，防止全部内容被屏蔽时无限请求。

验收：共享配置3项通过；Android20项JVM单测通过、1项默认真实网络测试跳过，Lint与Debug构建通过；另在原生emulator-5580实际执行关于内容、GPL/Apache正文和滚动自动追加测试，1/1通过（7.493秒）。iOS应用与测试目标编译通过，未执行XCTest/UITest。证据见UI_1.0_VERIFICATION.md。验证用Debug已原位更新至专用可操作Android模拟器，无新增工具或依赖。

右侧打开ios-layout.html，标题和页内注明为布局示意，不能代替原生iOS模拟器；本机仍无iOS运行时。releases内的旧交付包及源码zip未更新，未上传或发布、未安装用户真机。

## 2026-10-26 Android 原生布局与可操作模拟器

Android 首页、进吧、我的、设置、阅读设置及帖子工具栏已按 iOS 布局调整；顶部和底部悬浮层采样真实内容，磨砂参数参考 Anyu 的 blur20/saturate120%。内容视口延伸至屏幕底部。实现与验收边界见 ANDROID_IOS_LAYOUT.md；尚未完成双端同尺寸逐像素验收。

最新 Debug 的单元测试、Lint、应用与设备测试目标构建通过（logs/android-ios-layout-final-build.log，BUILD SUCCESSFUL）。最新 Debug 已更新到专用 emulator-5580 / TiebaPure-lab 并启动；按用户要求关闭 headless 实例，改为有原生窗口的 Android Emulator，允许用户直接点击、滑动和输入。当前交互交给用户，不再自动运行会抢占操作的 UI 驱动。此前设备测试结果见该布局报告，最新搜索流程尚未重复交互验收。

iOS 本机仍无模拟器运行时（simctl list runtimes 为空），磁盘剩余约5.6GiB，未下载新组件；网页示意图不能替代原生模拟器。releases 中安装包和源码zip仍为此前交付版本，不包含当前 UI 修改。未发布、未安装用户真机。

## 2026-10-26 iOS界面修改（未更新安装包）

按用户截图修改顶部居中标题/玻璃圆形搜索按钮，以及底部悬浮玻璃胶囊。列表视口延伸到屏幕底边，胶囊以overlay覆盖内容，根列表保留112pt滚动尾部余量；没有底部整块不透明背景。首页/进吧/我的切换、首页重复点击返回/刷新逻辑保留；当前导航末项为帖子时隐藏胶囊。

代码在Core/UI/FloatingRootTabBar.swift、RootView及三个根页面。iOS26使用原生glassEffect，旧版使用ultraThinMaterial，减少透明度设置使用不透明系统色。新增全屏内容/悬浮栏UI回归用例，并更新现有标签定位与截图边界断言。日志logs/ios-floating-chrome-final-build.log为TEST BUILD SUCCEEDED，仅测试目标编译，未执行测试（本机仍无iOS模拟器运行时）。

右侧iOS改为previews/ios-layout.html布局示意，可滚动检查内容覆盖关系；不是原生运行画面。Android未改。releases中的安装包与源码zip仍为此前4d85efb的交付版本，不包含本轮UI修改，后续打包时需包含本轮修改。

## 先前双端打包交付

2026-10-26：TiebaPure双端本地开发、打包和文档交付已收尾。根目录 /Users/y/Downloads/Codex/Mac工作区/TiebaPure，版本1.4.15/build59，新ID dev.anyu.tiebapure，用户指定白角图标已接入两端。固定上游5a87225190d6a79b1e3ad3b7d846edd407d416c7，完整只读克隆在original-source；可编辑iOS与新增Android均在根Git。

- iOS最终Release归档/未签名IPA成功，主应用与扩展ID/名称/版本/Scheme/许可核对通过。测试目标编译成功、Mac路由断言通过；没有iOS运行时和匹配profile，未安装启动或执行XCTest/UITest。
- Android Debug/Release构建、Lint和签名通过；12项JVM单测通过；另1项真实匿名流程实际通过。专用AVD设备存储5/5通过，含2.3MB文件快照及损坏校验；正文保存后断网重开通过。最终本地签名Release已在专用AVD安装/冷启动/打开真实帖子。
- Android主要业务已实现；真实账号登录/写操作、媒体库保存与完整媒体离线、平板/多版本/无障碍矩阵尚未验收，不能称双端全功能验证完成。差异和限额见FEATURES/KNOWN_ISSUES。
- 原作者、GPL与依赖许可保留；129依赖元数据节点/90归档/44条NOTICE提取完成。APK许可assets、IPA GPL和SwiftProtobuf正文与源码字节一致；完整素材清权并未完成。
- 右侧预览 http://127.0.0.1:8768：iOS原项目参考截图，Android模拟器实际截图，静态首页/帖子切换。server仅提供previews目录。README/ARCHITECTURE/DEVELOPMENT/BUILD_IOS/BUILD_ANDROID齐全；VERIFICATION列实际证据。
- 最终安装包、对应源码zip、manifest与SHA256SUMS在releases。对应提交以manifest为准；未来开发先核对本地Git状态，不重建或覆盖原始仓库。

新增XcodeGen2.46.0于工具箱；没有另装SDK/JDK/Xcode/iOS运行时。AndroidRelease新密钥/密码仅在私有.build/signing，未进Git/源码包；未来升级须保留。Debug与Release签名不互通，不能通过卸载用户应用解决冲突。

未推送、未上传、未发布、未安装用户真机。专用AVD数据均为本任务测试数据。后续优先做匹配iOS签名和实际运行、真实账号由用户手动登录后的逐项验收，不自动执行发帖等写操作。
