# 1.0 界面与分页验收

- 共用版本源：app-config.json，版本1.0/build59；iOS App.xcconfig同步，Android BuildConfig自动读取。配置测试3/3通过。
- Android 自动分页：原生测试通过辅助功能滚动实际列表至底部，确认page>=2、原有帖子仍在、没有“加载下一页”按钮。错误重试沿用失败页的追加模式。下一页为空或去重/屏蔽后没有新可见条目时停止自动续载。
- 连续回复：追加并去重，达到服务端末页停止；阅读位置按可见帖所属分页保存。JVM用例覆盖重复页、末页、前页保留与跨页位置，总20通过/1跳过。未逐项实测所有排序及楼中楼场景。
- 关于页：原生自动验收1.0、Anyu、原作者说明、GPL与SwiftProtobuf；实际打开GPL/Apache正文通过。Android额外保留自身依赖与Protobuf BSD正文。截图：../logs/android-about-1.0.png。
- iOS：移除正常刷帖列表与回复的手动续载按钮，保留已有自动预加载和失败重试；全页被过滤时的有界空状态继续入口保留。关于页修改、应用及测试目标编译通过；本机没有运行时，未运行XCTest/UITest。

日志：logs/android-auto-pagination-build.log、logs/android-pagination-ui-test-build.log、logs/android-auto-pagination-native-test.log、logs/ios-auto-pagination-build.log。构建和哈希记录：logs/ui-1.0-validation.json。

验证用APK：/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build/outputs/apk/debug/app-debug.apk，已更新到专用TiebaPure-lab/emulator-5580。正式releases保持原交付版本。

工具：共用工具箱/Users/y/toolchains，JDK21.0.12.1、Gradle8.14.3、AGP8.13.0、Kotlin2.2.20、SDK36、ADB35.0.2、Emulator37.2.12、Xcode27.0。未下载或安装新组件，未增加依赖，未触及用户真机。

右侧iPhone预览为本地临时预览页（`.cache/previews/`，不随仓库分发）的网页布局示意，非原生运行结果，也不代表最新关于页的iOS运行截图。
