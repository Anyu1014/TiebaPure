# 本地验证报告

验证时间：2026-10-25～26，Asia/Shanghai。版本1.4.15（59）。固定上游来源见UPSTREAM；产物和对应源码提交的最终校验值以 `releases/manifest.json`、`releases/SHA256SUMS` 为准。

## 已实际完成

| 检查 | 结果 | 证据（工程根目录下） |
| --- | --- | --- |
| 共享配置Node测试 | 2/2通过 | `tool/tiebapure/config.test.mjs`；本次执行输出 |
| iOS原始工程Debug无签名编译 | 通过 | `logs/ios-baseline.log` |
| iOS最终名称/白角图标Release归档 | ARCHIVE SUCCEEDED | `logs/ios-final-release.log` |
| iOS主应用+测试+UI测试目标编译 | TEST BUILD SUCCEEDED，仅编译 | `logs/ios-tests-compile.log` |
| Swift原ExternalRoute Mac断言 | 通过，包括嵌入Info.plist的新Scheme、旧入口、伪造主机、溢出ID | `logs/ios-route-checks.log` |
| Android单元测试 | Policy7/7、SubmissionPolicy5/5；默认线上测试跳过 | `logs/android-release-build.log`、`android/app/build/test-results/testDebugUnitTest/` |
| Android真实匿名接口 | 首页→帖子→搜索“旅行”→旅行吧通过；1测试0失败0跳过 | `logs/android-final-live-smoke.log`；单独启用RUN_ANONYMOUS_LIVE_SMOKE运行 |
| Android Debug/Release编译与Lint | 构建通过；Debug0错误19警告、Release0错误18警告 | `logs/android-final-build.log`、`logs/android-release-build.log`，`android/app/build/reports/lint-results-*.html` |
| Android设备存储测试 | 5/5通过，1.667秒 | `logs/android-final-instrument-tests.log` |
| Android Debug检查轮安装/冷启动 | 通过；该轮冷启动1020ms | `logs/android-final-launch.log`、`logs/android-final-home.*` |
| Android正文保存与断网重开 | 成功提示；无活动网络时可显示正文 | `logs/android-final-save.xml`、`logs/android-final-offline.*`、`logs/android-final-offline-connectivity.txt`（Active default network:none） |
| Android最终Release安装/冷启动/真实帖子 | 通过，冷启动306ms；旅行吧→集中帖实际显示 | `logs/android-release-install.log`、`logs/android-release-launch.log`、`logs/android-release-thread.*` |
| Android安装包身份 | ID dev.anyu.tiebapure，version1.4.15/build59，min26/target36，Release无DEBUGGABLE flag | `logs/android-release-manifest.xml`、`logs/android-release-installed-package.txt` |
| 包内许可 | APK全部许可assets与源码字节一致；IPA GPL/SwiftProtobuf正文一致 | 最终包内容核对；`docs/ANDROID_NOTICE_INVENTORY.json` |
| 原始源仓库保护 | HEAD仍为固定上游提交，工作树无改动 | `original-source/`只读；git status检查 |

设备测试使用私有Android35 ARM64 AVD（emulator-5580），从未登录真实账号。存储5项分别覆盖假凭据加密/退出及备份排除、坏备份不覆盖旧历史、500条历史限额、路径穿越/ICU字数、2.3MB离线正文绕过CursorWindow及校验损坏拒绝。没有操作真实用户容器。换到Release签名时只卸载本私有AVD的测试包，未对用户设备执行卸载。

## 最终安装包

| 文件 | SHA-256 |
| --- | --- |
| `releases/android/TiebaPure-1.4.15-release.apk` | `eb4b584ecfef845f0639c73729a961d9b9432303e446d19b359ba7e67ab6186e` |
| `releases/android/TiebaPure-1.4.15-debug.apk` | `6eccb7fca55687b41177882ae900be1cf4c2dade3537283c57b7a0edf8311d23` |
| `releases/ios/TiebaPure-1.4.15-unsigned.ipa` | `aa5b9121ce47d0e87c18c7b45df6292bcc916a8e939f1d330b23a4d4986486da` |

Android Release通过APK Signature Scheme v2；本地新建RSA3072密钥，证书SHA-256 `f5fa134f311e1625b45a13273119fbc61703cc846ab4b0d40bdf9a3c78e065f1`。Debug为测试密钥，与Release不同。签名核对不等同所有运行行为验收。

IPA通过ZIP完整性检查，主应用`dev.anyu.tiebapure`、扩展`dev.anyu.tiebapure.open-in`、显示名称TiebaPure/用TiebaPure打开、版本/build、Scheme均核对一致；没有embedded.mobileprovision。它是未签名的真实Release归档，不能直接安装。本机无匹配描述文件。

最后一次包装改为先创建全新ZIP并校验后替换输出，避免旧IPA中残留已删除文件；增量归档和新包检查通过，见 `logs/ios-final-package.log`。源码包只收录Git跟踪的工程、协议、资源和文档，不包含只读原始Git克隆、日志/缓存、生成包或私钥；双端当前完整源码均已包含。

## 工具与新增组件

统一入口 `/Users/y/toolchains`：Xcode27.0（27A5252f）、Swift6.4、JDK21.0.12.1、Android SDK35/36、BuildTools36.0.0、ADB35.0.2、Emulator37.2.12、Gradle8.14.3、Kotlin2.2.20、XcodeGen2.46.0。新增安装仅XcodeGen2.46.0（官方包校验后放入工具箱）；Gradle下载项目依赖及protoc构件。没有另装SDK/JDK/Xcode/iOS运行时。

## 未验证与限制

没有iOS模拟器运行时，未运行完整XCTest/UITest、未安装启动iOS。右侧iOS预览为上游截图，Android为真实模拟器静态截图。未验证真实登录/风控、消息、收藏/关注/点赞/签到、发帖/回复、资料修改或本人删帖；没有向真实账号发送数据。

未做真实音视频播放/媒体库保存、完整离线媒体、多厂家和Android26/36、iPad/Android平板、无障碍与性能矩阵验收。白角图标已生成和接入，不声称像素级只改四角或真机桌面验收。关于许可证来源、同名商标及第三方素材的未核实项见LICENSE_AUDIT/RESOURCES。

没有上传、推送、发布、上架或安装用户真机。当前交付是可继续验证的本地双端工程及安装包，不作生产级全功能验收结论。
