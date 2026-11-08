# 资源与来源清单

2026-10-26补充：最终APK的全部许可assets与源码逐字节一致；最终IPA的GPL与SwiftProtobuf正文一致。iOS图标已随Release归档，Android使用指定自适应图标资源。右侧预览页另保存iOS上游参考截图和Android模拟器截图，均非新增已清权素材；未将它们标为实时iOS运行结果。以下为10月25日研究盘点，历史“未做包内比对”已由本补充与VERIFICATION更新。

审查日期：2026-10-25。工程根 `/Users/y/Downloads/Codex/Mac工作区/TiebaPure`。仅盘点源码/资源文件及声明，不下载动态媒体，不改图标或业务代码。许可结论见[LICENSE_AUDIT.md](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/LICENSE_AUDIT.md)。

## 用户指定桌面图标

用户提供`IMG_0090.jpg`，要求只修改白角，并选定结果作为桌面图标。以下来源链来自本次任务授权与现有文件；这能证明工程采用意图，不能单独证明图像的完整版权/商标权属。本资源未被擅自标注GPL、CC0或另加许可。

| 文件 | 实测尺寸 | 来源/用途 | 许可或待核实状态 |
| --- | --- | --- | --- |
| `shared-assets/TiebaPure-icon-reference.jpg` | 203×201 | 留存用户参考图 | 用户提供；权属证明未核验 |
| `shared-assets/TiebaPure-desktop-icon.png` | 1254×1254 | 用户选定的白角调整后桌面图标结果 | 当前项目使用授权；完整权属待核实 |
| `shared-assets/TiebaPure-icon-1024.png` | 1024×1024 | 选定结果的iOS图标尺寸副本 | 同来源；不是新独立许可来源 |
| `ios/TiebaPure/Resources/Assets.xcassets/TiebaPureIcon.appiconset/TiebaPure-1024.png` | 1024×1024 | iOS当前AppIcon；与上行SHA-256一致 | 同来源 |
| `android/app/src/main/res/drawable-nodpi/tiebapure_icon.png` | 432×432 | Android adaptive launcher前景素材 | 同来源；尺寸转换副本 |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | XML | 白色background + inset后的前景；manifest普通/roundIcon均引用它 | 工程配置，不是新增第三方图像 |

文件证据：[shared-assets](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/shared-assets)、[iOS图标配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/project.yml)、[Android图标配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)、[Android Manifest](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/AndroidManifest.xml)。本次没有做像素级“只改白角”或设备桌面外观验收，只确认资源引用与文件信息。

| 文件 | SHA-256 |
| --- | --- |
| 桌面选定PNG | `5bde14b59369ed2e4ac976f002237e0110e2f21d7feb2e93360db9e6c045d48b` |
| 共享1024 PNG / iOS1024副本 | `b8e5334595b191518e07b904d8517129ee9e75708340d72904b1fa4fb1313d22` |
| Android432 PNG | `b6fe63a956415f51021f84289ef1d228827355729cc9bb7a790bb6989ee56315` |

## 上游随仓库资源与保留位置

固定上游提交：`5a87225190d6a79b1e3ad3b7d846edd407d416c7`；完整原始Git仓库在只读`original-source/`，可编辑副本在`ios/`。[来源记录](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/UPSTREAM.md)。

| 对象/数量 | 来源与本地位置 | 用途/打包范围 | 许可/来源状态 |
| --- | --- | --- | --- |
| 原AppIcon PNG 2张 | `original-source/TiebaPure/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`与`AppIcon-1024-dark.png`；ios副本仍保留 | 原亮/暗图标；当前编译图标名改为TiebaPureIcon，旧图标未从原始备份删除 | 上游GPL声明覆盖仓库，但未见单独图像创作/权属清单，不能据此声称完成逐项清权 |
| 原README截图3张 | `original-source/docs/images/{home-light,search-light,thread-guest-light}.png`，ios/docs副本 | 文档演示；不是App图标，也不能用访客截图证明账号功能已验收 | 截图中第三方内容权利未专项核验 |
| GPL正文 | 根`LICENSE`、`ios/LICENSE`、`android/app/src/main/assets/LICENSE` | 原作者许可告知，双端保留 | GPL-3.0-only；三份正文与original-source一致 |
| SwiftProtobuf完整正文1份 | `ios/LICENSES/SwiftProtobuf-Apache-2.0.txt` | iOS资源及关于页 | Apache-2.0 + Runtime Library Exception，与原仓库一致 |
| Android通用Apache正文 | `android/app/src/main/assets/licenses/Apache-2.0.txt` | Apache依赖基础许可材料 | 已核对不含Swift Runtime Exception；不代表全部NOTICE核验完成 |
| AndroidProtobuf BSD正文 | `android/app/src/main/assets/licenses/Protobuf-BSD-3-Clause.txt` | protobuf-java3.25.5许可材料 | 与上游v25.5正文一致，仅多末尾1个换行 |
| Protobuf schema 28个 | `ios/Protos/**/*.proto`，原始副本在`original-source/Protos` | 请求/响应协议输入；Android读取这组schema生成Java | 作者声明为自行维护的最小协议定义；受工程来源许可约束，字段编号不是可随意变更的资源配置 |
| iOS隐私清单 | `ios/TiebaPure/Resources/PrivacyInfo.xcprivacy` | 应用资源；声明UserDefaults/FileTimestamp原因 | 来源于上游；清单存在不等同当前全部行为隐私审查通过 |

上游当前树的自有位图资源为2图标+3截图；其中不含一套离线表情、字体、视频或音频包。AndroidX/Material等依赖内图标/资源属于相应依赖材料，未计入这5张上游位图。[上游资源树](https://github.com/infinityf4p/TiebaPure-iOS/tree/5a87225190d6a79b1e3ad3b7d846edd407d416c7/TiebaPure/Resources)、[schema说明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/README.md)。

## 运行时动态资源与用户文件

| 类型 | 来源/存放方式 | 用途 | 许可与审查边界 |
| --- | --- | --- | --- |
| 贴吧表情 | 动态HTTPS `tb2.bdstatic.com/tb/editor/images/client/image_emoticon*.png`，本机缓存 | 正文/编辑器表情显示 | 百度第三方素材，源码GPL不等于表情再分发许可；不收集成已清权共享素材包 |
| 用户头像/吧头像/帖子图片 | 服务端返回URL，经网络策略过滤后加载或缓存 | 阅读界面 | 对应用户/平台内容，逐项权利未核验；缓存不是取得版权 |
| 视频/语音 | 服务端协议返回媒体信息，按各端媒体策略加载 | 帖子播放 | 第三方内容及服务条款未逐项审查；不是随安装包附带的演示素材 |
| 用户导入字体 `.ttf/.otf` | 用户本机选择，保存于应用私有reader-fonts；源码无随包字体文件 | 自定义阅读字体 | 用户应有相应使用权；不能假设允许把个人导入字体一并发包 |
| 系统字体/iOS SF Symbols | 操作系统API提供 | 默认文字和图标 | 不是工程拷贝的一套可跨平台自由分发资产；未导出Apple图标到Android |
| Android Material Icons | `androidx.compose.material:material-icons-core:1.7.8`依赖 | Compose UI图标 | 缓存POM声明Apache-2.0；依赖详见DEPENDENCIES |
| 草稿图片/收藏离线媒体/搜索历史等 | 用户操作产生的应用私有数据 | 本机体验 | 不属于源码共享资源清单；不将真实用户数据、Cookie或密钥加入仓库和安装包 |

证据：[iOS表情仓库](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure/Domain/Models/TiebaEmoticonRepository.swift)、[iOS字体导入策略](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure/Domain/Models/ReadingPreferences.swift)、[Android字体存储](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/java/dev/anyu/tiebapure/FontStore.kt)、[Android媒体映射](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/java/dev/anyu/tiebapure/TiebaApi.kt)、[依赖完整清单](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/DEPENDENCIES.md)。

## 缺失/待核实清单

- 已列出的关键图标、许可正文、schema和隐私清单均存在；本次未做最终APK/IPA逐资源比对，不能仅以源码文件存在证明所有目标包内都带齐。
- 用户图标、上游原图标/截图没有在本次取得独立权属证明；同名品牌及百度相关商标也未完成检索。
- 动态表情/媒体在线可用性、用户自备字体的许可及跨账号/跨设备转移授权，不能从仓库推导。
- 资源样式和真机桌面图标未在本次研究任务中肉眼验收；不把尺寸/哈希检查等同视觉一致性验收。
