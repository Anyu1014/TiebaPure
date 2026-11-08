# 依赖与工具链清单

2026-10-26补充：本机已完成双端构建和Android测试，证据见VERIFICATION。新增XcodeGen2.46.0在统一工具箱；未新装SDK/JDK/Xcode或iOS运行时。Gradle下载了工程声明的构建/运行依赖。以下10月25日只读依赖快照保留；NOTICE提取90份归档、44条原文记录的结果另见ANDROID_NOTICE_INVENTORY。

审查日期：2026-10-25。以下为本地工程当前声明及解析快照，**依赖图解析成功不等于编译、运行或许可逐行审计通过**。本次只读解析和缓存POM检查，没有安装组件、执行构建或测试。上游固定提交见 [UPSTREAM.md](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/UPSTREAM.md)。

## iOS

- 唯一Swift Package声明：`https://github.com/apple/swift-protobuf.git`，精确版本 **1.38.1**，锁定revision `55d7a1cc5666b85c13464aea1c4b4a90feccb4c8`。来源：[project.yml](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/project.yml)、[Package.resolved](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/TiebaPure.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved)。
- 许可 **Apache-2.0 + Runtime Library Exception**，保留原完整正文；不能把例外扩展成Android Protobuf或所有Apache库的统一豁免。来源：[本机许可](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/LICENSES/SwiftProtobuf-Apache-2.0.txt)、[上游1.38.1 LICENSE.txt](https://github.com/apple/swift-protobuf/blob/1.38.1/LICENSE.txt)。
- SwiftUI/UIKit/Foundation/WebKit/AVFoundation/Photos/Security/SwiftData等来自Apple平台SDK，不是另行声明的开源Swift包；其SDK/系统API许可不因本项目GPL而改变。SwiftData采用弱链接兼容旧系统。此处未全面审查Apple SDK合同。[工程配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/ios/project.yml)。
- Protobuf输入有28个 `.proto`，生成Swift源码已随上游提交；常规编译不必重新安装protoc。重新生成需python3/protoc/protoc-gen-swift 1.38.1；Android生成Java代码读取同一份 `ios/Protos`。[上游生成说明](https://github.com/infinityf4p/TiebaPure-iOS/blob/5a87225190d6a79b1e3ad3b7d846edd407d416c7/Protos/README.md)、[Android配置](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build.gradle)。

## Android声明与实际版本

直接运行时依赖声明来源：[android/app/build.gradle](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/app/build.gradle)。

| 声明 | 此次解析版本 | 用途 |
| --- | --- | --- |
| androidx.compose:compose-bom | 2025.09.01 | 版本平台，自己不含运行时代码 |
| androidx.activity:activity-compose | 1.11.0 | Activity/Compose |
| androidx.compose.material3:material3 | 1.4.0 | Material3 UI |
| androidx.compose.material:material-icons-core | 1.7.8 | 界面图标 |
| androidx.compose.ui:ui-tooling-preview | 1.9.2 | Preview相关API |
| androidx.compose.ui:ui-tooling（debugImplementation） | 1.9.2 | 仅Debug声明 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.9.4 | UI状态 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.10.2 | 异步 |
| com.google.protobuf:protobuf-java | 3.25.5 | Android Protobuf运行库 |
| com.squareup.okhttp3:okhttp | 4.12.0 | HTTP |
| io.coil-kt:coil-compose | 2.7.0 | 图像加载 |
| androidx.media3:media3-exoplayer | 1.8.0 | 媒体播放 |
| androidx.media3:media3-ui | 1.8.0 | 媒体控件 |
| androidx.media3:media3-datasource-okhttp | 1.8.0 | 媒体HTTP数据源 |

Kotlin stdlib由插件自动加入，实际2.2.20。Okio最终3.9.0、Guava最终33.3.1-android等传递依赖以完整表为准，不把声明最低版本误报为实际版本。

## 可复核方法与审查范围

已成功运行（离线、退出码0、无FAILED节点）：

```sh
cd '/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android'
source /Users/y/toolchains/env.sh
./gradlew --offline --no-daemon --console=plain -q :app:dependencies --configuration debugRuntimeClasspath
```

从该配置去除仅 `(c)` 约束的重复行，按箭头右侧已选择版本去重，得到 **129个解析坐标节点**。其中包含BOM、Kotlin多平台selector与Android/JVM实际变体，所以129不是APK内库文件数，也不是129份独立打包代码。Debug工具依赖已包含；**没有把这张Debug表当成Release依赖图或APK内容审计**。

逐个读取现有 `/Users/y/.gradle/caches/modules-2/files-2.1/<group>/<name>/<version>/<hash>/*.pom` 的 `licenses`；缺省时递归读取缓存父POM。本次129个节点均取得声明：128个Apache-2.0，1个BSD-3-Clause（protobuf-java）。这只是发布者元数据的核对；未逐行追踪源码、native对象、嵌入第三方代码、NOTICE及最终APK合并结果。完整许可与剩余事项见 [LICENSE_AUDIT.md](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/docs/LICENSE_AUDIT.md)。

| 精确解析坐标 | POM许可元数据 | 本机证据 |
| --- | --- | --- |
| `androidx.activity:activity:1.11.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.activity/activity/1.11.0/87efc503be5883f0105ef68f69f54e838e022cb4/activity-1.11.0.pom) |
| `androidx.activity:activity-compose:1.11.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.activity/activity-compose/1.11.0/c2afccdbe377fb59ecb05e88fe2ad7767bfed83d/activity-compose-1.11.0.pom) |
| `androidx.activity:activity-ktx:1.11.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.activity/activity-ktx/1.11.0/f14af8f84478c689c3f54f418564ecdd18ef3bc7/activity-ktx-1.11.0.pom) |
| `androidx.annotation:annotation:1.9.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation/1.9.1/67deed6cfcb79363aa9125ca08355b05a6571b30/annotation-1.9.1.pom) |
| `androidx.annotation:annotation-experimental:1.4.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-experimental/1.4.1/9e57caeeb7f02cecfe51c27d11352284b5161276/annotation-experimental-1.4.1.pom) |
| `androidx.annotation:annotation-jvm:1.9.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm/1.9.1/33599f2420d44099d3d9fa410a6c855928f59e82/annotation-jvm-1.9.1.pom) |
| `androidx.appcompat:appcompat-resources:1.6.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.appcompat/appcompat-resources/1.6.1/9f7e2a43a9a3e8fea938f0d0da224ab5e30c9c91/appcompat-resources-1.6.1.pom) |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.arch.core/core-common/2.2.0/9627f64db0ba64e3c2432a274b83d23e1c0c627e/core-common-2.2.0.pom) |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.arch.core/core-runtime/2.2.0/d9cb0beb2de910cd9922535b6028054786fea928/core-runtime-2.2.0.pom) |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.autofill/autofill/1.0.0/52c15707a9cc4dd57f439185c52b6aebd4eee6a3/autofill-1.0.0.pom) |
| `androidx.collection:collection:1.5.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.collection/collection/1.5.0/f7fe387eb2f661c870036ea307f237e84ea5ab9c/collection-1.5.0.pom) |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.collection/collection-jvm/1.5.0/c244730b1ae1455937809d767b662a938a52bda0/collection-jvm-1.5.0.pom) |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.collection/collection-ktx/1.5.0/9eb0f5f31647327a2a70b0ee346cb7c617343baa/collection-ktx-1.5.0.pom) |
| `androidx.compose:compose-bom:2025.09.01` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose/compose-bom/2025.09.01/4b00812b59efe1eb11373827ab2173b1aec059e4/compose-bom-2025.09.01.pom) |
| `androidx.compose.animation:animation:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.animation/animation/1.9.2/4ca1103682897a3c335a63610c2cf08d353aef5d/animation-1.9.2.pom) |
| `androidx.compose.animation:animation-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.animation/animation-android/1.9.2/1cc215653739d71d047f1befa9451ebb2d0a5ed0/animation-android-1.9.2.pom) |
| `androidx.compose.animation:animation-core:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.animation/animation-core/1.9.2/a7bf31bfed3206bc1e4b342ecb31c887f1e914c7/animation-core-1.9.2.pom) |
| `androidx.compose.animation:animation-core-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.animation/animation-core-android/1.9.2/5e6f9dc14278c9d42da3a2007f2118aa7032ba63/animation-core-android-1.9.2.pom) |
| `androidx.compose.foundation:foundation:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation/1.9.2/44fb1b44a28cc69c87838f90106139d2c0e1fb69/foundation-1.9.2.pom) |
| `androidx.compose.foundation:foundation-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-android/1.9.2/cd8ec8062cfaed439baf0471320839ebe4071153/foundation-android-1.9.2.pom) |
| `androidx.compose.foundation:foundation-layout:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-layout/1.9.2/2a86ff7e0ea44da0a21d7219bf8f492d15b1a700/foundation-layout-1.9.2.pom) |
| `androidx.compose.foundation:foundation-layout-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-layout-android/1.9.2/865e28adf18d2a38f1660b20cf367226c5377dab/foundation-layout-android-1.9.2.pom) |
| `androidx.compose.material:material:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material/1.9.2/abf8e4a78926e31390ae3db856e984b85630aa42/material-1.9.2.pom) |
| `androidx.compose.material:material-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-android/1.9.2/23755a70b42060f9ddf42f93ee1483cb3a7f3a02/material-android-1.9.2.pom) |
| `androidx.compose.material:material-icons-core:1.7.8` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-icons-core/1.7.8/154df52ee5768c475436961a9a417f149e5d57dc/material-icons-core-1.7.8.pom) |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-icons-core-android/1.7.8/772256020a8d9b700b66e151e747b908bd7c9a8f/material-icons-core-android-1.7.8.pom) |
| `androidx.compose.material:material-ripple:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-ripple/1.9.2/9f449762eae21774504f572ce99ecd64bcabc841/material-ripple-1.9.2.pom) |
| `androidx.compose.material:material-ripple-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-ripple-android/1.9.2/3e20e33811b2a52447f4c7325d8ff4a97e10eca9/material-ripple-android-1.9.2.pom) |
| `androidx.compose.material3:material3:1.4.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material3/material3/1.4.0/a28c516473aff070508271220514abbf1d3dd0b2/material3-1.4.0.pom) |
| `androidx.compose.material3:material3-android:1.4.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.material3/material3-android/1.4.0/d99c806db67833e3a1931bcb04533aae9e47866e/material3-android-1.4.0.pom) |
| `androidx.compose.runtime:runtime:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime/1.9.2/9710e2de09edc6489128d6e0a6dfc4153aa9dd45/runtime-1.9.2.pom) |
| `androidx.compose.runtime:runtime-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-android/1.9.2/3a1dca1f15c942a6e163032cd36074fdae93fcfa/runtime-android-1.9.2.pom) |
| `androidx.compose.runtime:runtime-annotation:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-annotation/1.9.2/22354e936cf327c736f874f5c25d745b8401bf60/runtime-annotation-1.9.2.pom) |
| `androidx.compose.runtime:runtime-annotation-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-annotation-android/1.9.2/76f9fa8ce8851834b7311e30efb8705942088c4a/runtime-annotation-android-1.9.2.pom) |
| `androidx.compose.runtime:runtime-saveable:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-saveable/1.9.2/94a62aa206f3c0a72c4a55c99f6de5cc62627f95/runtime-saveable-1.9.2.pom) |
| `androidx.compose.runtime:runtime-saveable-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-saveable-android/1.9.2/df59512d704d43a0a83b9c24cf64d251d89a5dae/runtime-saveable-android-1.9.2.pom) |
| `androidx.compose.ui:ui:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui/1.9.2/a3889cd1cf44bf9b55ecbc9d052d5a170ab486c0/ui-1.9.2.pom) |
| `androidx.compose.ui:ui-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-android/1.9.2/a3a1421275500ace3fc9d8287f3c9cb8c39c5938/ui-android-1.9.2.pom) |
| `androidx.compose.ui:ui-geometry:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-geometry/1.9.2/c2edba7d53a6b2230c12b54ffcabb0bfe2bb2a83/ui-geometry-1.9.2.pom) |
| `androidx.compose.ui:ui-geometry-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-geometry-android/1.9.2/750096d7ad435e868396a89f6ff0ab25e49eca76/ui-geometry-android-1.9.2.pom) |
| `androidx.compose.ui:ui-graphics:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-graphics/1.9.2/2fec7505e11c2140edb2384248bc8ad2ae80333/ui-graphics-1.9.2.pom) |
| `androidx.compose.ui:ui-graphics-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-graphics-android/1.9.2/f198fb2bac4612dc4e97e9078dbb51fbf004dbf4/ui-graphics-android-1.9.2.pom) |
| `androidx.compose.ui:ui-text:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text/1.9.2/74e9be09a03b0af89f795e0349ac6e248efbced2/ui-text-1.9.2.pom) |
| `androidx.compose.ui:ui-text-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android/1.9.2/2049dfde5bf2ef1031f2f780e2b14586df041e18/ui-text-android-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling/1.9.2/904a329dbcf5832359ab0408918b953c908efff6/ui-tooling-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling-android/1.9.2/ea37dfabbd18125617391c768abc895c67377c86/ui-tooling-android-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling-data:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling-data/1.9.2/f3b8491da31dc514cf75ed1521883d1f3a02a117/ui-tooling-data-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling-data-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling-data-android/1.9.2/fcbcf2624335615cb5629d01c3b1b26f862d8052/ui-tooling-data-android-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling-preview:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling-preview/1.9.2/9eac627dc49807fbaf0d115b670a73a5697dfef2/ui-tooling-preview-1.9.2.pom) |
| `androidx.compose.ui:ui-tooling-preview-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-tooling-preview-android/1.9.2/94782bfe68c29f1509f1d6eca47cbcff14fb2cb7/ui-tooling-preview-android-1.9.2.pom) |
| `androidx.compose.ui:ui-unit:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-unit/1.9.2/d94e42dce12b7237849f380a84f60d1b37e0c20e/ui-unit-1.9.2.pom) |
| `androidx.compose.ui:ui-unit-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-unit-android/1.9.2/18e110b7ee44e7e6cfea54fbec4ffe537c998c87/ui-unit-android-1.9.2.pom) |
| `androidx.compose.ui:ui-util:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-util/1.9.2/b47c0cac581e3194eaa7deae7a08fd0a9d095a1b/ui-util-1.9.2.pom) |
| `androidx.compose.ui:ui-util-android:1.9.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-util-android/1.9.2/62d7d1cbe0e57cb75cb36a3d4d68c1b7b61a157e/ui-util-android-1.9.2.pom) |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.concurrent/concurrent-futures/1.1.0/1a2c7df73a77c58e30bd0b20cbc23e9854515438/concurrent-futures-1.1.0.pom) |
| `androidx.core:core:1.15.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.core/core/1.15.0/858e7b6a16c6ff2a5271c0c41190d028d8d62389/core-1.15.0.pom) |
| `androidx.core:core-ktx:1.15.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.core/core-ktx/1.15.0/871a72521837c9905aa1e55105cc9cb48312c37c/core-ktx-1.15.0.pom) |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.core/core-viewtree/1.0.0/4867db2b46b603ace8f822e6d476977300c98c21/core-viewtree-1.0.0.pom) |
| `androidx.customview:customview:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.customview/customview/1.0.0/53c54220d41f17dcb79d4d7f39432a328df544b2/customview-1.0.0.pom) |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.customview/customview-poolingcontainer/1.0.0/474789a5ab58d304a413b3d89a8f14edeb6be5c3/customview-poolingcontainer-1.0.0.pom) |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.emoji2/emoji2/1.4.0/6dffa7e49260a5f46212c2c00d5e7f1bb5385dde/emoji2-1.4.0.pom) |
| `androidx.exifinterface:exifinterface:1.3.7` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.exifinterface/exifinterface/1.3.7/6dfaa4b73d5f8262e391c7b1c5381a2e781d34f0/exifinterface-1.3.7.pom) |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.graphics/graphics-path/1.0.1/2a6c8471f30c0b4dc898723d23d5f43ab3fac687/graphics-path-1.0.1.pom) |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.interpolator/interpolator/1.0.0/dfa9c6b5826fbf2f910e1720ce2b0421d7aafec3/interpolator-1.0.0.pom) |
| `androidx.lifecycle:lifecycle-common:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common/2.9.4/97632b1e958b0f992080687e71a40c90d2c8f4b4/lifecycle-common-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-common-java8:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-java8/2.9.4/fc7fbf9eec2c25364ee54cdec8999c3fc3c52992/lifecycle-common-java8-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-jvm/2.9.4/42c2796c95ff82607ded037f2720e2435a3b49e3/lifecycle-common-jvm-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-livedata-core/2.9.4/8cda7e11bf0f4f80b54c2d090d3dfec925eb84e4/lifecycle-livedata-core-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-process:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-process/2.9.4/8d4daf1dcb613bb6c7746ff8ab9f16cf9e1d979a/lifecycle-process-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime/2.9.4/2d7d21ec505fceab55b726272982bc4c925aa25e/lifecycle-runtime-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-android/2.9.4/6b41b8cbc6c621ee898ece52040a8ca97e17d037/lifecycle-runtime-android-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime-compose:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-compose/2.9.4/fc5713658ec413765252713ce565b8502499892c/lifecycle-runtime-compose-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-compose-android/2.9.4/f3782c6aaeced2684aeffa1a83306288e4255f5/lifecycle-runtime-compose-android-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-ktx/2.9.4/2dfbd4069944839d0850ba6fca96c2f219f80d66/lifecycle-runtime-ktx-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-ktx-android/2.9.4/6dbeb2ec033ab03f28b4fffb2785261ef3926cf1/lifecycle-runtime-ktx-android-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel/2.9.4/7e78eb2dcc87c4b26dcbc6d14c48de7b95159d6b/lifecycle-viewmodel-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-android/2.9.4/4d07d9795f66229744bc96722d4273d43605098d/lifecycle-viewmodel-android-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-compose/2.9.4/ebf37a3b1c7873458dc5a1f444e0624fbd65b6e0/lifecycle-viewmodel-compose-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-compose-android/2.9.4/b7ca210f26f67a133ee63aab8f7aaf185581559a/lifecycle-viewmodel-compose-android-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-ktx/2.9.4/8b175bfe3775a7e8d7f26deb1ebf838dc744ef9a/lifecycle-viewmodel-ktx-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-savedstate/2.9.4/c73dd27088ff2b8d0ad10d4726513a47d998d591/lifecycle-viewmodel-savedstate-2.9.4.pom) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-savedstate-android/2.9.4/f9c38bc712d8ed1a8866e52f2a316688e262b14b/lifecycle-viewmodel-savedstate-android-2.9.4.pom) |
| `androidx.media3:media3-common:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-common/1.8.0/391e3b85440725cec3a54daa25fff5fa464a7496/media3-common-1.8.0.pom) |
| `androidx.media3:media3-container:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-container/1.8.0/86f2ccf5d54210b767f6c4310ca941bd376852a4/media3-container-1.8.0.pom) |
| `androidx.media3:media3-database:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-database/1.8.0/3e0abca4d88a46c55f3483fef03a11d1d70d370e/media3-database-1.8.0.pom) |
| `androidx.media3:media3-datasource:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-datasource/1.8.0/b5229d313c00e49c5e08d6f99c4e7fb9d9d74025/media3-datasource-1.8.0.pom) |
| `androidx.media3:media3-datasource-okhttp:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-datasource-okhttp/1.8.0/ac07f5131651760a6b52c527770f352de55d3a58/media3-datasource-okhttp-1.8.0.pom) |
| `androidx.media3:media3-decoder:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-decoder/1.8.0/908eaf60ddebfd3f8329e04e382a25a442e9f86e/media3-decoder-1.8.0.pom) |
| `androidx.media3:media3-exoplayer:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-exoplayer/1.8.0/392bc4c587f8f449b33d63d2d004e8f79056b89d/media3-exoplayer-1.8.0.pom) |
| `androidx.media3:media3-extractor:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-extractor/1.8.0/e00df73f6e659cc5c95686559438b3bce8cdf4ea/media3-extractor-1.8.0.pom) |
| `androidx.media3:media3-ui:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.media3/media3-ui/1.8.0/a104a284341cf043ea005f8bc39ffce1dc5b1849/media3-ui-1.8.0.pom) |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.profileinstaller/profileinstaller/1.4.0/ce5ab72a997e0ddfb08e1cbdc39f77ac5ed315cf/profileinstaller-1.4.0.pom) |
| `androidx.recyclerview:recyclerview:1.3.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.recyclerview/recyclerview/1.3.0/5db86f652a11c15289a671cc3c0b4cda13eb5ca2/recyclerview-1.3.0.pom) |
| `androidx.savedstate:savedstate:1.3.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate/1.3.3/893802799b15114cc6449554c8b2608cae0c8f49/savedstate-1.3.3.pom) |
| `androidx.savedstate:savedstate-android:1.3.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-android/1.3.3/c1f304bec755b33a9d0480e4209a0a3b8c1239d9/savedstate-android-1.3.3.pom) |
| `androidx.savedstate:savedstate-compose:1.3.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-compose/1.3.3/19597377d910352145124cdd9edf6a64eb16d96a/savedstate-compose-1.3.3.pom) |
| `androidx.savedstate:savedstate-compose-android:1.3.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-compose-android/1.3.3/a341288b6a1c0e98d9603863e16ba237117dad07/savedstate-compose-android-1.3.3.pom) |
| `androidx.savedstate:savedstate-ktx:1.3.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-ktx/1.3.3/564bbee6d6885851d1c48ca262099f9de641ae84/savedstate-ktx-1.3.3.pom) |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.startup/startup-runtime/1.1.1/1ee5722679a9f73d27bb12d0ed4711745d4345b9/startup-runtime-1.1.1.pom) |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.tracing/tracing/1.2.0/141eea2b77be286c2f1821af2c5b4992aaef964a/tracing-1.2.0.pom) |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.vectordrawable/vectordrawable/1.1.0/8b15e03d94f7d9a07cbea653ad7f524315d1d507/vectordrawable-1.1.0.pom) |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.vectordrawable/vectordrawable-animated/1.1.0/2c40e16427f4e94ea471526868ba5ac4bd13de6d/vectordrawable-animated-1.1.0.pom) |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/androidx.versionedparcelable/versionedparcelable/1.1.1/301263729256e9f19b144ce00f83d600ba14c38b/versionedparcelable-1.1.1.pom) |
| `com.google.accompanist:accompanist-drawablepainter:0.32.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.accompanist/accompanist-drawablepainter/0.32.0/b5bf549945ad05de69d81e3c81d0bd5a9b08aff3/accompanist-drawablepainter-0.32.0.pom) |
| `com.google.guava:failureaccess:1.0.2` | Apache-2.0（父POM继承） | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/failureaccess/1.0.2/2df7ebe307b885a222be352d3bffd5fcf0428ad4/failureaccess-1.0.2.pom) + [parent POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/guava-parent/26.0-android/a2c0df489614352b7e8e503e274bd1dee5c42a64/guava-parent-26.0-android.pom) |
| `com.google.guava:guava:33.3.1-android` | Apache-2.0（父POM继承） | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/guava/33.3.1-android/d06497aead4f2c87559a3149eefbb00c72ece9f0/guava-33.3.1-android.pom) + [parent POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/guava-parent/33.3.1-android/d4598d14da6c708054bc42f2be7e6245ac715be4/guava-parent-33.3.1-android.pom) |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | Apache-2.0（父POM继承） | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/1b77ba79f9b2b7dfd4e15ea7bb0d568d5eb9cb8d/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.pom) + [parent POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.guava/guava-parent/26.0-android/a2c0df489614352b7e8e503e274bd1dee5c42a64/guava-parent-26.0-android.pom) |
| `com.google.protobuf:protobuf-java:3.25.5` | BSD-3-Clause（父POM继承） | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-java/3.25.5/2c4a0bfc6a807df9b919b366d15f4904b3bd0170/protobuf-java-3.25.5.pom) + [parent POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protobuf-parent/3.25.5/fefa8b2e68e227b4b32f795d4206c70558eb07ef/protobuf-parent-3.25.5.pom) |
| `com.squareup.okhttp3:okhttp:4.12.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.squareup.okhttp3/okhttp/4.12.0/6354764c8008d477d21735a4906ba035fa0d5556/okhttp-4.12.0.pom) |
| `com.squareup.okio:okio:3.9.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio/3.9.0/8989bd398b6baa872828d5cdc5b7cc70dad5c138/okio-3.9.0.pom) |
| `com.squareup.okio:okio-jvm:3.9.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio-jvm/3.9.0/bf3077cd09d62e9c637cade61800be83b76a964e/okio-jvm-3.9.0.pom) |
| `io.coil-kt:coil:2.7.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/io.coil-kt/coil/2.7.0/1e23e966e9978819e33c902b22d563bb3a6d6267/coil-2.7.0.pom) |
| `io.coil-kt:coil-base:2.7.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/io.coil-kt/coil-base/2.7.0/d6bc2b25500f2f8f3c60aee78bc88e8e7127bf8f/coil-base-2.7.0.pom) |
| `io.coil-kt:coil-compose:2.7.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/io.coil-kt/coil-compose/2.7.0/e3db2406b92a38c6abfdbb14b1df9a9e668c8fa2/coil-compose-2.7.0.pom) |
| `io.coil-kt:coil-compose-base:2.7.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/io.coil-kt/coil-compose-base/2.7.0/808462890216cd4afbb8c1cc5e0b5a9afc7a8419/coil-compose-base-2.7.0.pom) |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/23.0.0/deb853be3c9e05ed99fbbf83e49d349d08a6d10e/annotations-23.0.0.pom) |
| `org.jetbrains.kotlin:kotlin-bom:1.8.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-bom/1.8.0/a253c3d63fcb05f163a105ea92a35d9ba2f71ce6/kotlin-bom-1.8.0.pom) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.20` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.20/62f87c4f4a53f249cb42926a1ef24bffdf190557/kotlin-stdlib-2.2.20.pom) |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.2.20` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/2.2.20/80c14f97000c48b7d6bbcc3abeb39a28e59b697a/kotlin-stdlib-common-2.2.20.pom) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.9.0/5ce6c5278ef6e342507a96372ee534ed0b830e54/kotlin-stdlib-jdk7-1.9.0.pom) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk8/1.9.0/4f04e57e4a12d4a59f620c63520681c4ff1d03e6/kotlin-stdlib-jdk8-1.9.0.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-android/1.10.2/f301310c3e7e01e8ef5c86bcb98efe349da80288/kotlinx-coroutines-android-1.10.2.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-bom/1.10.2/bc3b109335d4fa0106a1e17b7863e388ef93c553/kotlinx-coroutines-bom-1.10.2.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core/1.10.2/828afd085f442cafb6d5d9b6556c2227142a4d03/kotlinx-coroutines-core-1.10.2.pom) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.10.2/bbb9139f1d9ee60ca991bfb4979b13ddd9ef1c8e/kotlinx-coroutines-core-jvm-1.10.2.pom) |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-bom/1.7.3/358a3249085a860b24b5769b74b307725b53e4e5/kotlinx-serialization-bom-1.7.3.pom) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core/1.7.3/8d9caf131b7b60132ae6ea612a0b76d574cc4bcf/kotlinx-serialization-core-1.7.3.pom) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.7.3/efdf58645976b9fa789259e96c41ad8136a559a3/kotlinx-serialization-core-jvm-1.7.3.pom) |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 | [POM](/Users/y/.gradle/caches/modules-2/files-2.1/org.jspecify/jspecify/1.0.0/fae4a252aeee8a649653b2f239067bcead8f80cb/jspecify-1.0.0.pom) |

## 构建时与测试时依赖（不等同App运行时）

| 坐标/工具 | 当前配置版本 | 范围/证据 |
| --- | --- | --- |
| Gradle wrapper | 8.14.3-all | [wrapper](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/gradle/wrapper/gradle-wrapper.properties) |
| com.android.application / com.android.tools.build:gradle | 8.13.0 | [插件](/Users/y/Downloads/Codex/Mac工作区/TiebaPure/android/build.gradle) |
| org.jetbrains.kotlin.android / Kotlin Compose plugin | 2.2.20 | 同上 |
| com.google.protobuf Gradle plugin | 0.9.4 | 同上 |
| com.google.protobuf:protoc | 3.25.5 | 代码生成器声明，非protobuf-java运行库 |
| junit:junit | 4.13.2 | testImplementation |
| org.json:json | 20250517 | testImplementation，非App附带JSON运行库 |
| androidx.test:runner | 1.6.2 | androidTestImplementation |
| androidx.test.ext:junit | 1.2.1 | androidTestImplementation |

上述构建/测试范围没有纳入129节点POM许可核对，未声称完成其全部传递依赖许可审计。构建插件不会因为出现在Gradle缓存里就自动算作APK运行库。

Android配置为compileSdk/targetSdk 36、minSdk26、Java/Kotlin目标17；构建JDK固定走工具箱JDK21。XcodeGen、Xcode、SDK等工具位置与版本以 [工具箱README](/Users/y/toolchains/README.md) 及各次构建日志为准。项目本次审查未安装或升级任何工具。

## 再现性限制

直接版本与Compose BOM已固定；当前未见Gradle dependency locking文件，传递依赖的长期再现不能只靠本表保障。升级依赖后须重新解析同一variant并复核许可；本表不是安全漏洞扫描或未来兼容性保证。
