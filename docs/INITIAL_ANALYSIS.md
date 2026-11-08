# TiebaPure-iOS：项目、许可证与双端可行性分析

审查日期：2026-10-25（Asia/Shanghai）。范围：公开仓库目录与核心源码抽查、上游构建记录、许可原文和本机只读工具检查。未克隆整个仓库、修改应用、安装依赖、构建、登录百度或操作真实账号。

## 结论

**适合以现有原生工程为基础继续做 iOS 二次开发；Android 技术上可行，但属于完整客户端移植，需要重新实现平台代码。**

iOS 已有可维护的源码、资源、协议定义、测试、Xcode 工程和未签名 IPA 脚本，不需要从零重写。Android 没有现成工程，不能把 SwiftUI 或 IPA 直接转换成等价 APK。两端最大的共同不确定性是百度非官方接口的当前可用性、登录风控和第三方内容/服务权利。[项目说明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)、[工程配置](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/project.yml)、[完整目录](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/)

建议先验证 iOS 的真实匿名浏览和登录，再验证一个最小 Android 阅读流程。需要完整双端功能时，逐项扩展消息、收藏、关注、签到、媒体、离线保存和写操作。此建议没有被当作开发或打包授权执行。

## 1. 本次检查的版本与规模

| 项目 | 已核实结果 |
| --- | --- |
| 仓库 | infinityf4p/TiebaPure-iOS，公开仓库 |
| 默认分支 / 固定提交 | main / 5a87225190d6a79b1e3ad3b7d846edd407d416c7 |
| 提交日期 | 2026-10-10 UTC |
| 应用版本 | 1.4.15，build 59；主应用与 Open In 扩展一致 |
| 最低 iOS | 16.4 |
| Git 跟踪文件 | 255 个；递归目录响应未截断 |
| Swift 文件 | 199 个：主应用 150 个、单测 46 个、UI 测试 2 个、分享扩展 1 个 |
| 主应用内生成代码 | 28 个 .pb.swift；其余 122 个为非生成 Swift 文件 |
| Protobuf 输入 | 28 个 .proto |
| 仓库图片 | 2 个 AppIcon PNG、3 张 README 截图 |
| Android 工程 | 未见 Kotlin、Gradle 或 AndroidManifest.xml |
| 依赖管理 | Swift Package Manager；锁定 SwiftProtobuf 1.38.1 |
| submodule / LFS | 未见 .gitmodules；.gitattributes 没有 LFS 配置，目录中没有 gitlink 条目 |

数量来自固定提交的完整 Git 树，是文件数，不是测试用例数，也不代表所有源码均已逐行审计。[固定提交](https://github.com/infinityf4p/TiebaPure-iOS/commit/5a87225190d6a79b1e3ad3b7d846edd407d416c7)、[源码目录](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/)、[版本配置](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/project.yml)、[依赖锁定](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved)、[Git 属性](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/.gitattributes)

## 2. 架构与已有功能

### 架构

| 层 | 关键位置 | 实际职责 |
| --- | --- | --- |
| 应用入口 | App/TiebaPureApp.swift、AppEnvironment.swift、RootView.swift | 注入真实/测试环境，账号切换、前后台状态、过期会话与导航 |
| UI | Features、Core/UI | 首页、进吧、我的三个根页；搜索、帖子、媒体、消息、资料、设置和编辑器 |
| 领域层 | Domain/Models、Domain/Mapping、Domain/Filtering | 帖子/楼层/用户模型、协议映射、屏蔽规则、阅读与草稿状态 |
| 网络 | Core/Network | API 协议、HTTP 请求、表单签名、JSON/Protobuf 解码及各业务端点 |
| 认证 | Core/Auth | 百度网页登录、Cookie 校验、Keychain 账号保存、退出和会话失效 |
| 数据存储 | AppPersistence、各 PersistenceFactory | iOS 17+ SwiftData；iOS 16 受保护文件存储及迁移 |
| 跨应用打开 | TiebaPureOpenIn、ExternalRoute | Safari Action 扩展、分享载荷和自定义 URL Scheme |
| 验证 | TiebaPureTests、TiebaPureUITests、FixtureTiebaAPI | 协议/业务/安全/迁移测试与模拟数据 UI 检查 |

[入口](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/App/TiebaPureApp.swift)、[环境组装](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/App/AppEnvironment.swift)、[根导航](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/App/RootView.swift)、[API 接口](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaAPIService.swift)、[存储后端选择](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/OrderedCollectionPersistenceFactory.swift)

### 功能范围

- 匿名阅读：首页推荐、进吧、吧内最新/精华及排序、主题/回复搜索、帖子与楼中楼。
- 账号功能：网页登录、消息、关注与粉丝、账号收藏、签到、点赞、资料编辑，以及实验性的发帖和回复。
- 媒体：图片预览和保存、视频、语音播放。
- 本地：关键词/用户/贴吧屏蔽、浏览历史、阅读位置、草稿、字体导入、阅读偏好、保存帖子及离线媒体、备份导入/导出。
- 平台适配：iPad 双栏、深浅主题、减弱动态效果、外部链接与 Safari 分享打开。

这是一份基于 README、接口与相应实现的功能清单，**不表示本次已经逐项运行验收**。本地保存和媒体分级保存可见于源码，超出了 README 中较简略的功能概述。[README](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)、[保存管理界面](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Features/Settings/SavedThreadsView.swift)、[离线媒体模式](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/SavedThreadMedia.swift)、[阅读与字体](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/ReadingPreferences.swift)

### 后端与安全边界

已检查的核心端点直接请求 tieba.baidu.com、c.tieba.baidu.com、tiebac.baidu.com；仓库目录未见独立服务端工程。因此可以维护自己的客户端，但无法据此独立维护或控制百度后端。[端点](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaEndpoint.swift)

认证代码使用 WKWebView 获取并校验百度 Cookie；账号凭据使用 Keychain，包含旧明文存储迁移。网络代码可见 HTTPS/重定向范围约束、响应大小限制以及 Cookie 存储隔离。这些是值得保留的实现，不能因“移植”而删去；存在这些防护也不等于完成了全面安全审计。[认证策略](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Auth/AuthSession.swift)、[账号存储](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Auth/AccountStore.swift)、[HTTP 防护](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaHTTPClient.swift)

写操作还有独立的身份初始化、加密/签名和风控响应解析，工作量明显高于简单 GET 列表。安全验证的真实触发条件和处理闭环本次没有运行验证，不能承诺任何账号都能成功发帖。[初始化](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaPostingBootstrap.swift)、[风控响应](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Network/TiebaSubmissionChallenge.swift)

## 3. 许可证、资源和分发边界

### 已确认

1. 项目明确采用 **GPL-3.0-only**。本地研究、运行和修改可行；仅私用修改不自动要求向全网公开。GPL 也不是仅限非商用。[README](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)、[LICENSE 第 2、4 节](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/LICENSE)
2. 对外分发受覆盖的衍生程序时，需要保留许可/版权/免责告知、显著标明修改与日期，按适用条款提供匹配该版本的对应源码。对应源码包括必要的构建脚本和接口定义；只给原版上游链接不能当然覆盖自己的修改。源码提供方式应符合 GPL 第 6 节，不能简化成“必须向全网公开”或“只给几段补丁即可”。[LICENSE 第 1、4、5、6 节](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/LICENSE)
3. 将原代码翻译或改写成 Kotlin，不会仅因换语言就自动摆脱衍生作品的许可要求；应结合实际复用内容判断。[LICENSE 第 0、5 节](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/LICENSE)
4. SwiftProtobuf 1.38.1 采用 Apache-2.0 加 Runtime Library Exception；仓库所附文本与上游标签文本一致。现有工程将两份许可证打包，About 页面也提供展示，后续应保留。[依赖许可](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/LICENSES/SwiftProtobuf-Apache-2.0.txt)、[上游许可](https://github.com/apple/swift-protobuf/blob/1.38.1/LICENSE.txt)、[About 页面](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Features/Profile/AboutView.swift)、[工程资源配置](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/project.yml)

### 尚未核实的权利

- 2 个应用图标和 3 张截图未见独立的来源/授权清单；目录元数据不能证明素材权属。本次未做图像溯源。
- 贴吧表情从 tb2.bdstatic.com 动态下载；帖子图片、头像、音视频也依赖外部内容。可下载不等于已获得素材再分发许可，代码的 GPL 不能替代内容权利。
- 字体功能是用户导入 ttf/otf；仓库没有发现随包字体文件，后续若新增字体须单独核实。
- README 感谢 TiebaPure、aiotieba；Protos README 自称自行维护最小协议定义。这些是项目声明，不能替代逐文件的历史来源审计。
- 百度名称、标识与服务授权不由本仓库的代码许可授予。若以后上架 App Store，还需核实 Apple 5.2 的知识产权、第三方服务和媒体要求；本报告不对商店合同与 GPL 的兼容性作最终定论。

[资源目录](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Resources/Assets.xcassets)、[截图](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/docs/images)、[动态表情](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/TiebaEmoticonRepository.swift)、[字体导入](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/ReadingPreferences.swift)、[协议来源说明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/README.md)、[项目声明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)、[Apple 审核规则 5.2](https://developer.apple.com/app-store/review/guidelines/#intellectual-property)

## 4. iOS 二次开发可行性

**判断：可行性高，优先保留现有 SwiftUI 与 UIKit 互操作实现。**

有现成 .xcodeproj、共享 Scheme、project.yml、锁定依赖和生成后的协议代码。日常构建可以从已提交工程开始；只有改协议或重生成时，才需要 protoc 及匹配的 protoc-gen-swift 1.38.1，不能把生成器缺失误判成源码缺失。[工程配置](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/project.yml)、[Protobuf 说明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/README.md)、[生成脚本](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/tool/tiebapure/generate-ios-protos.sh)

未来如做独立改名版本，需一起核对主应用与扩展 Bundle ID/版本、Display Name、URL Scheme、导出备份类型标识、关于页来源和图标。Keychain service、UserDefaults 命名及原用户数据的迁移策略也要明确；仅替换一个显示名称不足以形成干净的独立配置。[主应用 plist](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Resources/Info.plist)、[配置](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/project.yml)、[账号存储](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Core/Auth/AccountStore.swift)

未签名 IPA 脚本已经存在，默认要求 XcodeGen 校验工程与 YAML 一致，再无签名归档和打包。它不能提供开发者身份或安装授权；后续签名需覆盖主应用和 Open In 扩展。本次不检查用户证书或签名账户。[打包脚本](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/scripts/package-unsigned-ipa.sh)

维护难点主要在复杂界面/媒体交互、iOS 16 文件与 iOS 17+ SwiftData 的迁移，以及会话切换时取消和隔离写操作。应先建立原版基线，再做小范围修改。[存储迁移](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/ContentDraftPersistenceFactory.swift)、[根状态处理](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/App/RootView.swift)

## 5. Android 移植可行性与对应方案

下表是工程判断和建议方案，**不是已实现清单，也不是精确工期估算**。原仓库没有 Android 模块，现有 Swift 文件不能直接作为 Compose 源码使用。[目录](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/)

| 能力 | 可沿用的内容 | Android 需要实现的部分 | 相对难度 |
| --- | --- | --- | --- |
| 首页、进吧、搜索、帖子/楼中楼 | 数据形状、分页规则、排序、过滤逻辑和界面参考 | Compose 页面、导航、状态与列表性能 | 中 |
| JSON / Protobuf 网络 | .proto、字段编号、请求规则、错误与映射测试思路 | Java/Kotlin 协议生成和网络层；保持 ID 数值语义 | 中 |
| 登录与会话 | 域名/Cookie 校验规则、过期与退出语义 | WebView/CookieManager、回跳限制、凭据加密存储及清理 | 高 |
| 消息、收藏、关注、签到、点赞 | API 与状态流转规则 | Kotlin API、状态协调、失败回滚与重试边界 | 中高 |
| 发帖、回复、图片上传、资料修改 | 协议定义、初始化及风控状态模型 | 请求兼容、加密/签名、草稿和真实账号验收 | 高 |
| 历史、屏蔽、阅读位置、草稿 | 领域模型与保留/迁移规则 | Room/文件与偏好存储；不能直接读取 SwiftData 作为通用数据库 | 中高 |
| 图片、视频、语音、离线保存 | URL 校验规则、保存模式、媒体校验语义 | Media3/图片组件、音频焦点、下载、共享存储与权限 | 高 |
| 字体、备份导入导出 | 文件格式与校验规则可作为设计输入 | Android 文件选择和字体加载；跨端格式兼容需另测 | 中高 |
| Safari 分享、深链接、iPad 双栏 | 帖子/贴吧路由语义与布局参考 | Android 分享接收、Intent 路由、平板自适应；系统体验不完全相同 | 中 |

建议用原生 Kotlin + Jetpack Compose 建立 Android UI；Room 可承载结构化本地数据，Media3 可承载音视频播放。凭据应使用 Android Keystore 管理的密钥加密保存到应用私有存储，**Keystore 不是直接存 Cookie 字符串的通用保险箱**。具体网络、图片及序列化依赖等到实施时按实际工程选择，本次没有安装任何库。[Compose 官方文档](https://developer.android.com/compose)、[Room](https://developer.android.com/training/data-storage/room)、[Media3](https://developer.android.com/media/media3)、[Keystore](https://developer.android.com/privacy-and-security/keystore)

### 更容易遗漏的双端一致性

- 帖子、楼层等标识存在 Int64/UInt64；Kotlin Long 或 JSON 浮点中转不能无检查地替换原表示。
- 协议需要保留字段编号、wire 类型和未知字段行为；移植应加入独立 wire fixture，不能只测同一份 schema 自己编码再解码。
- 退出或切换账号时，要隔离旧账号的排队写操作与草稿，防止状态串到新账号。
- iOS 受保护文件、Keychain、备份过滤不能原封不动复制，应保留其安全目标并在 Android 重新实现。
- 离线保存、账号云收藏、阅读位置是不同概念；本地库不等于百度账号数据的完整备份。
- 自动签到是每天首次打开/回前台触发的逻辑，不应误扩展成要求全天后台常驻。

[ID 与存储语义](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/AppPersistence.swift)、[协议验证要求](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/README.md)、[账号切换](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/App/RootView.swift)、[文件保护](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/SecureFilePersistence.swift)、[本地阅读状态](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Domain/Models/LocalThreadLibrary.swift)

## 6. 构建与测试：证据及本机前提

### 上游证据

最新固定提交的 GitHub Actions 工作流在 2026-10-10 显示成功，ci-ok 的单测、兼容性链接检查、模拟数据 UI smoke、iPad 布局 smoke 和 Release 构建步骤均成功。[CI 运行](https://github.com/infinityf4p/TiebaPure-iOS/actions/runs/38029279056)、[具体 Job](https://github.com/infinityf4p/TiebaPure-iOS/actions/runs/38029279056/job/114146620250)

但工作流明确跳过 AnonymousLiveSmokeTests，UI smoke 是选定的模拟数据用例，并非全部 UI 测试。线上匿名测试还要求显式开启 RUN_ANONYMOUS_LIVE_SMOKE。该 CI 也没有以真实 iOS 16.4 运行时完成所有功能验收；弱链接检查不能等价于旧系统完整运行通过。[CI 定义](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/.github/workflows/ci.yml)、[线上 smoke](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPureTests/AnonymousLiveSmokeTests.swift)

已发布 v1.4.15 的未签名 arm64 IPA，Release 声明来源为本次固定提交；资产约 9.35 MB。本次仅检查发布元数据，没有下载、复算 SHA-256、检查包内容或安装。[发布页](https://github.com/infinityf4p/TiebaPure-iOS/releases/tag/v1.4.15)

### 本机只读结果

统一通过 /Users/y/toolchains 检查；没有使用 Codex/DSH 缓存运行时。

| 检查项 | 结果 |
| --- | --- |
| 共用工具箱 check.sh | PASS；工具箱验证声明未构建/未下载 |
| 平台 | Darwin 27.0.0，arm64 |
| Xcode | 27.0，27A5252f |
| Swift | 6.4；工程 SWIFT_VERSION 是语言模式 5.9 |
| SDK | iOS 27.0 与 iOS Simulator 27.0 SDK 可用 |
| iOS 模拟器运行时 | simctl list runtimes 返回空数组；有 SDK 不等于有可运行模拟器 |
| XcodeGen | 工具箱 PATH 及检查的常见位置未发现；不等于对整台磁盘做过穷尽搜索 |
| JDK | Temurin 21.0.12.1+1 |
| Android SDK | /Users/y/toolchains/android-sdk；platforms 35/36，build-tools 34.0.0/35.0.0/36.0.0 |
| ADB | 35.0.2 |
| Android AVD | 列表包含 anyu、super-schedule-lab；未启动或验证可运行性 |
| Gradle / Protobuf 生成器 | 当前工具箱 PATH 未发现独立 gradle、protoc、protoc-gen-swift；未扫描全部项目缓存 |
| 新组件 | 未下载、未安装 |

本机 Xcode 与上游 CI 的 Xcode 26.6 不同；编译兼容性要等实际构建验证。模拟器运行时缺失影响后续启动/UI 测试；XcodeGen 缺失影响工程重生成和现有打包脚本的默认一致性门槛。Android SDK/JDK 在场只说明基础工具存在，尚无本项目的 Android 构建可供同步。

| 验收维度 | iOS | Android |
| --- | --- | --- |
| 源码/工程 | 上游已有，本次只读检查 | 原仓库未实现 |
| 编译 | 上游 CI 成功；本机未构建 | 未创建，未构建 |
| 安装与启动 | 本次未验证 | 本次未验证 |
| 真实匿名接口 | 本次未验证，CI 明确跳过线上 smoke | 本次未验证 |
| 真实账号与写操作 | 本次未登录或操作账号 | 本次未验证 |
| 本次产物 | 无 IPA | 无 APK |

## 7. 后续实施顺序建议

1. 固定本次提交，保存原始来源；先用已提交 Xcode 工程建立本机编译基线，再补模拟器运行时并运行现有测试。
2. 先验证匿名首页→搜索→帖子→媒体，再由用户控制完成百度登录和账号只读功能验收。写操作另列清单与真实结果，不能用 fixture 测试代替。
3. 明确新名称、应用标识、图标与改动范围后再做独立 iOS 版本，保留原功能和许可展示。
4. Android 先打通同一条匿名阅读链路，使用协议 fixture 校验请求/响应和分页，再实现登录、存储、媒体与账号功能。
5. 最后补齐风控/失败流程、离线保存与备份兼容、双端交互差异，再分别做打包、安装、启动和功能验收。

当前无需为了“分析”去下载 SDK、创建空 Android 项目或伪造安装包。没有足够证据给出“几天完成完整双端”的可靠承诺；明确功能范围并完成登录/协议验证后，才适合估算实施工期。

## 8. 未完成与限制

本次不是完整代码审计或法律意见。未逐行审查全部源码/历史来源，未证明所有资源引用无缺失，未验证素材权属和百度服务条款，未执行依赖解析、构建、测试、签名、安装或真实接口调用。主要未决风险：百度接口与风控变化、第三方素材/服务权利、GPL 分发履行，以及双端平台行为差异。


