# 许可证与来源审查

## 2026-10-26 打包复核补充

`tool/tiebapure/audit-android-notices.mjs` 已对上述129个解析节点的90份缓存JAR/AAR（包括嵌套classes.jar）提取44条可识别的许可/NOTICE条目，去重为2份原文，记录在 `ANDROID_NOTICE_INVENTORY.json`。原文与依赖清单随Android assets打包，最终Debug/Release APK中的全部许可资产已逐字节与工程副本核对一致。原历史冗余SwiftProtobuf Android文件已移除；Android使用独立Protobuf BSD正文。

iOS最终IPA包含原GPL与SwiftProtobuf许可，字节与源码副本一致。根源码归档保留GPL、双端源码、schema、构建脚本、原作者和修改日期。以上增加了实际压缩包材料检查；**仍不等于全部二进制/native源码版权溯源或图像清权**。其余材料与10月25日审查边界如下保留，历史段落中的“未构建/未核对”指当时的研究阶段。

审查日期：2026-10-25。上游 `infinityf4p/TiebaPure-iOS` 固定提交 `5a87225190d6a79b1e3ad3b7d846edd407d416c7`。本次为公开许可原文、工作区文件及Android缓存POM的核对，不是逐行版权溯源或法律意见。没有修改业务代码、配置或许可正文，没有安装组件、构建、跑测试或操作百度账号。[上游记录](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/UPSTREAM.md)。

## 原项目与本地衍生工程

上游README及应用关于页明确标注 **GPL-3.0-only**，根LICENSE为GPL第3版正文。`only`来自作者的许可选择，不能仅因GPL正文讨论后续版本就改成`or-later`。本地根、iOS、Android三处GPL正文与只读`original-source/LICENSE`逐字一致。[原README](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)、[根LICENSE](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/LICENSE)、[iOS LICENSE](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/LICENSE)、[Android LICENSE](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/assets/LICENSE)。

本地改名TiebaPure并使用独立标识`dev.anyu.tiebapure`，不改变已有GPL许可。iOS关于页保留原作者、源码和许可说明；Android关于页说明独立移植及GPL。对原代码进行Swift→Kotlin翻译不能仅凭语言变化就假设脱离衍生作品义务。[统一配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/app-config.json)、[iOS关于页](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure/Features/Profile/AboutView.swift)、[Android关于页源码](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/java/dev/anyu/tiebapure/MainActivity.kt)。

GPL的适用边界应按实际行为区分：

- **只在本地修改、运行**：第2节允许未向外传递的使用及修改，不自动要求把私用修改公开到全网。
- **分发源码或修改版**：第4、5节要求保留版权、许可、免责告知，标注修改及日期，按GPL许可整个受覆盖作品；不能通过改名抹去上游作者。
- **向他人分发IPA/APK等二进制**：还须按第6节认可的方式提供与该二进制匹配的Corresponding Source，包括构建需要的相关脚本和接口定义。只给未修改上游地址不能当然覆盖本地修改。此次工程和安装包仅存于本地，未替用户执行上传或对外发布。
- **商业使用**：GPL第4节允许免费或收费分发；本项目并非“仅限非商用”。GPL第10节又不允许对受许可权利叠加进一步限制。应遵守已有条件，而不是擅加“禁止商用/禁止修改/必须先联系作者”等原许可没有的普遍限制。
- **独立作品边界**：GPL第5节也区分独立作品的集合；不能把任何同目录文件都武断认定必须GPL。具体衍生边界取决于实际代码复用和组合关系。[根LICENSE第2/4/5/6/10节](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/LICENSE)。

## iOS SwiftProtobuf

实际Swift Package为`apple/swift-protobuf` **1.38.1**，锁定revision `55d7a1cc5666b85c13464aea1c4b4a90feccb4c8`。许可为 **Apache-2.0 + Runtime Library Exception**；先前已与上游1.38.1正文核对一致，本次再次确认iOS副本与原仓库逐字一致，并继续作为工程资源保留。[Package.resolved](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved)、[完整许可](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/LICENSES/SwiftProtobuf-Apache-2.0.txt)、[上游许可](https://github.com/apple/swift-protobuf/blob/1.38.1/LICENSE.txt)。

该例外针对使用该软件编译、部分库代码因此嵌入二进制时的特定归属告知豁免，不是所有Apache依赖的统一例外，也不改变TiebaPure自身GPL。保留现有完整正文与关于页展示即可避免在本工程随意扩大例外范围。

## Android运行时依赖

离线解析当前`debugRuntimeClasspath`成功，129个唯一解析坐标节点（含BOM/平台元数据/Debug工具）。逐个读取缓存POM及必要父POM，128个节点声明Apache-2.0、`com.google.protobuf:protobuf-java:3.25.5`声明BSD-3-Clause；此轮没有缺少可取得许可元数据的节点。坐标、缓存证据和解析命令完整列于[DEPENDENCIES.md](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/DEPENDENCIES.md)。**这不是129个APK运行库的数量统计，也不是所有包内文件来源的证明。**

| 对象 | 已核实许可/材料 | 审查结果与边界 |
| --- | --- | --- |
| AndroidX/Compose/Material3/Media3、Kotlin/coroutines/serialization、OkHttp/Okio、Coil、Accompanist、Guava相关节点 | 缓存POM或父POM声明Apache-2.0 | 仅元数据确认；嵌入源码、NOTICE、版权告知和最终打包内容未逐文件核实 |
| `com.google.protobuf:protobuf-java:3.25.5` | `protobuf-parent:3.25.5`的POM声明BSD-3-Clause | 运行库不是SwiftProtobuf，不适用Swift Runtime Library Exception |
| Android `assets/licenses/Protobuf-BSD-3-Clause.txt` | 与protobuf上游v25.5 LICENSE比较，除文件末尾额外1个换行外正文一致 | 包含Google版权、BSD三条件、免责和生成代码归属说明；原文保留 |
| Android `assets/licenses/Apache-2.0.txt` | 与原SwiftProtobuf附带Apache正文比较，去除Runtime Library Exception后内容一致（忽略首尾空白） | 通用Apache正文，不夹带Swift专属例外；不能据此断言所有依赖的独立NOTICE已齐全 |
| Android根assets原有`SwiftProtobuf-Apache-2.0.txt` | 本次检查时存在的历史冗余文件 | Android实际依赖图未包含SwiftProtobuf；不把这份文件作为Android Protobuf许可依据。主代理负责清理与包内复核，本审查不删除资源 |

证据：[Protobuf上游v25.5 LICENSE](https://github.com/protocolbuffers/protobuf/blob/v25.5/LICENSE)、[本地BSD正文](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/assets/licenses/Protobuf-BSD-3-Clause.txt)、[本地Apache正文](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/assets/licenses/Apache-2.0.txt)。

Apache第4节的许可证、修改、归属及可能的NOTICE要求应按实际库材料履行；只放一个通用Apache文本不自动等同完成全部归属告知审计。当前Android packaging排除了`META-INF/INDEX.LIST`及`META-INF/DEPENDENCIES`；是否有依赖所需材料受合并/排除影响仍须结合AAR/JAR及最终APK核实，不能仅由文件名认定合规或违规。[Android配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build.gradle)、[Apache正文第4节](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/assets/licenses/Apache-2.0.txt)。

构建插件和测试库是另一组范围，未混进App运行时许可结论；本次未审计其全部传递依赖。POM是发布者声明，不能替代源码/二进制逐行来源审计。

## 图标、表情、字体、媒体与名称

- 用户提供`IMG_0090.jpg`并明确选用调整白角后的结果作桌面图标；工程保存参考图及选定PNG。本次确认文件、尺寸与复制关系，**用户授权作为本项目图标不等于已经证明图像全部权属**。不把它自动标成GPL/CC0或可任意再分发。来源链和文件见[RESOURCES.md](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/RESOURCES.md)。
- 只读原图标和原截图完整保留；上游未提供逐项图像创作来源或独立授权清单，因此只能记录为“仓库许可声明下的现有资产、权属来源未单独证实”，不能声称已对每张图完成清权。[原资源目录](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/original-source/TiebaPure/Resources/Assets.xcassets)、[原截图](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/original-source/docs/images)。
- 百度表情从`tb2.bdstatic.com`运行时下载，头像和帖子图片/音视频来自第三方服务；用户字体由本机导入。这些不是新加入安装包的一套已获GPL授权素材，不能把缓存或用户导入文件直接复制进shared-assets后声称已取得共享权。[表情仓库](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure/Domain/Models/TiebaEmoticonRepository.swift)、[Android数据映射](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/java/dev/anyu/tiebapure/TiebaApi.kt)、[字体导入](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/src/main/java/dev/anyu/tiebapure/FontStore.kt)。
- 上游明确与百度/贴吧无隶属、授权或认可关系；本地采用的名称TiebaPure也不能据此主张与其他同名项目有官方关系。代码许可不能代替商标/素材/百度服务访问的权利证据。未检索TiebaPure商标，也未验证第三方接口服务条款适用性。[上游声明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/README.md)。

## 未完成的审查项

1. 对所有AAR/JAR/native内容及最终APK/IPA做NOTICE/版权告知完整性复核；此文档没有把POM核对提升为完整清权结果。
2. 图标、原截图及其内第三方内容的权属证明；百度表情/媒体和服务条款的使用、下载、再分发边界。
3. 对外发布时提供与产物对应的完整修改源码和构建材料、修改日期及作者告知；本轮未上传或发布。
4. 若以后上架App Store，另核实具体分发合同与GPL，以及Apple对第三方知识产权、服务访问和媒体下载的要求；本次不作“必能上架”或“GPL一律不能上架”的结论。[Apple审核指南5.2](https://developer.apple.com/app-store/review/guidelines/#intellectual-property)。

不存在因本报告而新增的“禁止商用/禁止修改/仅私用”等许可限制；尚未完成的权利核实与工程许可条件分开记录。
