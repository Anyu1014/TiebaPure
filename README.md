# TiebaPure

Anyu 维护的非官方百度贴吧客户端，原名 TiebaLite。当前版本为 **2.3（双端 build87）**；已有双端签名安装包，未安装到手机。iOS使用SwiftUI，Android使用Kotlin与Compose；与百度及贴吧官方无隶属、授权或认可关系。

2.3包含 Android 私信输入框键盘避让及评论回复条宽度调整，并统一双端会话排序、一键已读、数字角标居中、私信媒体上传和语音解析修正。底栏保留1.3浮层样式、高度、文字、头像与位置，见[底栏说明](docs/BOTTOM_BAR.md)。

保留此前草稿恢复/清空、发帖图片、私信媒体/表情/已读、楼层与排序、回帖返回保位、用户主题点赞及预览计数修复。最新验证和限制见[开发记录](docs/PROGRESS.md)及[2.3记录](docs/releases/2.3.md)。应用标识继续使用`dev.anyu.tiebapure`。

2.3安装包位于项目根的`build/latest/TiebaPure-2.3.apk`和`build/latest/TiebaPure-2.3.ipa`，均为build87；旧2.2包及更早版本由脚本自动归档到`build/history/`。历史记录见[2.2记录](docs/releases/2.2.md)。

此前版本见[1.3记录](docs/releases/1.3.md)、[1.1记录](docs/releases/1.1.md)；以下为既有公开1.0发行。

- [1.0 源码分支](https://github.com/Anyu1014/TiebaPure/tree/1.0)
- [1.0 发行版与安装包](https://github.com/Anyu1014/TiebaPure/releases/tag/1.0)
- [本版变化和验收范围](docs/releases/1.0.md)

## 1.0

- iOS 26/27 使用系统原生标签栏，液态玻璃和拖动选择由系统管理；旧版 iOS 使用兼容悬浮栏。
- Android 统一三栏布局，磨砂参考 Anyu；内容延伸至屏幕底部，评论区采用分层楼中楼预览。
- 刷到底部自动续载，顶部下拉手动刷新；从其他底栏页面切回首页保留内容和位置。
- 关于页显示版本1.0、作者Anyu，源码链接指向本仓库，GPL许可条目名称显示TiebaPure。

## 安装包

本机`build/latest/`与`build/history/`为项目根 `build/` 下的同级文件夹：`latest/` 保留当前 APK 和签名 IPA 各一份；`history/` 保存历史版本。生成不同内容的同平台新包时，旧包自动移入 `history/` 后再替换当前包。

发行版提供 `TiebaPure-1.0.apk`（Android签名Release）与 `TiebaPure-1.0-unsigned.ipa`（iOS可重签包），以及对应源码zip、manifest和SHA256校验文件。

iOS 开发签名与通用分发不同：本机可以为已登记设备签名安装，开发签名包含设备描述文件且有有效期限。公开发行保留未签名IPA供使用者自行签名。签名私钥、密码、描述文件和设备备份不会进入源码仓库或源码zip。

## 构建

本机工具统一使用 `/Users/y/toolchains`，先阅读其README。配置来自 `app-config.json`。

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test tool/tiebapure/config.test.mjs
bash tool/tiebapure/build-ios.sh ipa
bash tool/tiebapure/build-android.sh lintDebug
```

仅清空项目临时物：`bash tool/tiebapure/build-ios.sh clean`（保留源码、依赖、编译缓存与产物）。

Release签名及设备安装方法见 [Android构建](docs/BUILD_ANDROID.md)、[iOS构建](docs/BUILD_IOS.md)。主要工具为Xcode27、JDK21、Android SDK36和Gradle8.14.3。

## 来源与许可

本项目基于 [infinityf4p/TiebaPure-iOS](https://github.com/infinityf4p/TiebaPure-iOS) 进行双端二次开发，固定上游提交和原始版本见 [UPSTREAM](docs/UPSTREAM.md)。上游作者、感谢名单和许可保留在 `ios/README.md`、`ios/LICENSE` 与 `ios/LICENSES/`。项目代码遵循 [GPL-3.0-only](LICENSE)，第三方组件保留各自许可与版权说明。[资源清单](docs/RESOURCES.md) 单独记录图标和动态内容来源。

## 文档与验证

- [架构](docs/ARCHITECTURE.md)、[功能与双端差异](docs/FEATURES.md)、[开发约定](docs/DEVELOPMENT.md)
- [已知问题](docs/KNOWN_ISSUES.md)、[验证报告](docs/VERIFICATION.md)、[开发记录](docs/PROGRESS.md)
- [许可证审查](docs/LICENSE_AUDIT.md)、[依赖](docs/DEPENDENCIES.md)

Android原生检查覆盖自动续载、下拉刷新、首页返回和许可正文。iOS区分编译、签名、安装、启动及手动体验验收；真机启动不等于所有业务通过验收。真实账号写操作和完整系统版本矩阵尚未验收。

早期网页布局示意（本地临时预览页 `.cache/previews/`，不随仓库分发）不代表最新系统原生液态玻璃。开发使用 `ios/` 与 `android/`；本机完整只读上游克隆 `original-source/` 不重复放入源码zip。
