# TiebaPure

**一个干净、原生、可离线阅读的开源百度贴吧客户端。** iOS 与 Android 双端独立实现，不依赖 WebView 套壳，不做信息流推荐，也不在界面里塞第三方广告位。

Anyu 维护，原名 TiebaLite。与百度公司及贴吧官方无隶属、授权或认可关系；所有贴吧数据来自使用者自己的账号与贴吧公开接口。

---

## 它解决什么问题

官方客户端把阅读、广告、推荐和社交玩法混在一条信息流里。TiebaPure 只做一件事：**把吧里的内容读好、写好、存下来**。

- **原生界面对齐系统**：iOS 用 SwiftUI 与系统原生标签栏（iOS 26/27 走系统液态玻璃），Android 用 Kotlin + Jetpack Compose 的磨砂浮层底栏，两端都是真实原生控件，滚动、手势、键盘与系统栏行为跟随平台规范。
- **本地优先**：帖子正文、图片、楼中楼可以整帖离线保存，断网也能重开；历史、阅读位置、屏蔽与草稿都存在本机，不依赖自建服务器。
- **不偷偷上传**：除贴吧官方接口外没有其它后端，凭据用系统密钥库加密，不会出现在日志、源码或备份里。
- **源码公开**：GPL-3.0，双端实现与协议映射全部在仓库里，可以自行编译、修改、再分发。

## 核心功能

**浏览与阅读**
首页信息流、吧内最新/精华、多排序、只看楼主、倒序、楼中楼分层预览、主题与回复搜索、图片预览与缩放、视频与语音播放、平板双栏（阅读宽度 ≥840dp 时列表与详情并排）、刷到底自动续载、下拉刷新、离开再回来保留位置。

**发帖与互动**
发主题与回复（图片最多 9 张）、楼中楼回复、贴吧表情、草稿自动保存与失败恢复、点赞、收藏、关注/粉丝、签到、资料与昵称修改、本人主题删除、屏蔽用户与关键词。

**私信**
会话列表与未读角标、一键已读、时间分组（今天/昨天/本周/更早）、贴吧表情、已读回执。双端输入区都是一行文本、表情与发送按钮，图片消息直接以圆角呈现、不套气泡底色；发送能力只保留文本，接收侧的图片预览与语音播放照常可用。

**离线与备份**
整帖正文与楼中楼、图片与完整媒体可分级离线保存；Android 单帖正文快照上限 8MiB、正文总量 128MiB、媒体总量 512MiB，历史与阅读位置各 500 条、本地保存 100 帖；备份导出为 JSON（上限 8MiB），不含账号、草稿、字体与媒体。两端备份格式互不通用。

**自动化**
按吧与关键词配置自动回复规则，支持延迟、启用开关、离线补扫与历史任务确认、原帖定位；Android 可选常驻通知保活，iOS 受系统后台刷新调度限制。

**外观**
系统/浅色/深色主题、字号调节、本机字体、底栏收缩与快速回看、底栏文字开关、回复条位置（居左/居中/居右，独立于底栏保存）。

## 技术实现

| | iOS | Android |
| --- | --- | --- |
| 语言与界面 | Swift + SwiftUI | Kotlin + Jetpack Compose |
| 最低系统 | iOS 16.4 | Android 8.0（API 26），targetSdk 36 |
| 通信 | HTTPS + 官方 protobuf 私信/贴吧接口 | 同左，OkHttp + protobuf |
| 凭据 | Keychain | AndroidKeyStore（AES） |
| 应用标识 | `dev.anyu.tiebapure` | 同左 |
| Scheme | `tiebapure://thread/<id>`、`tiebapure://forum/<name>` | 同左，另接收系统分享文本 |

双端 schema、字段映射与 wire fixture 同步维护；网络层保留 HTTPS、目标域名、重定向、响应体积与内容类型校验。详细差异与验收边界见[功能与双端差异](docs/FEATURES.md)，架构见[架构说明](docs/ARCHITECTURE.md)。

## 隐私与安全

- 只连接贴吧官方域名，外部入口限定真实 `tieba.baidu.com` 主机。
- 登录走百度官方网页流程，会话加密后写入系统密钥库；备份与日志不包含凭据。
- 换号时取消并隔离旧账号的写操作，避免串号。
- 媒体地址做可信来源校验，拒绝非 HTTPS、异常图片元数据与未知导出格式。

## 安装

到 [Releases](https://github.com/Anyu1014/TiebaPure/releases) 下载：

- `TiebaPure-<版本>.apk`：Android 签名 Release 包，直接安装（需允许安装未知来源应用）。
- `TiebaPure-<版本>-unsigned.ipa`：iOS 未签名包，需自行用 AltStore、Sideloadly 或开发者证书重签后安装。

本机打包产物位于项目根的 `build/latest/`，历史版本由脚本归档到 `build/history/`。

## 构建

工具链统一放在 `/Users/y/toolchains`，版本与配置来自 `app-config.json`。

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test tool/tiebapure/config.test.mjs
bash tool/tiebapure/build-ios.sh ipa
bash tool/tiebapure/build-android.sh lintDebug
```

清空可丢弃的临时物：`bash tool/tiebapure/build-android.sh clean`。Release 签名与真机安装见 [Android 构建](docs/BUILD_ANDROID.md)、[iOS 构建](docs/BUILD_IOS.md)。

## 来源与致谢

本项目基于 [infinityf4p/TiebaPure-iOS](https://github.com/infinityf4p/TiebaPure-iOS) 做双端二次开发，固定上游提交见 [UPSTREAM](docs/UPSTREAM.md)。上游作者、感谢名单与许可保留在 `ios/README.md`、`ios/LICENSE` 与 `ios/LICENSES/`；图标与动态内容来源见[资源清单](docs/RESOURCES.md)；第三方依赖与许可见[依赖](docs/DEPENDENCIES.md)与[许可证审查](docs/LICENSE_AUDIT.md)。

## 已知限制

- 真实账号的写操作（发帖、私信、自动回复）与完整系统版本矩阵尚未逐项验收，验收记录见[验证报告](docs/VERIFICATION.md)与[开发记录](docs/PROGRESS.md)。
- iOS 后台自动回复受系统刷新调度限制；Android 常驻通知会增加耗电。
- 双端备份格式不互通；Android 图片预览已支持缩放，暂不支持平移。
- 本项目只实现贴吧公开与账号自身可见的数据，不提供任何绕过权限或抓取他人隐私的能力。

## 开源声明

TiebaPure 以 **GNU General Public License v3.0 only（GPL-3.0-only）** 发布，完整条款见仓库根目录 [LICENSE](LICENSE)。

- 你可以在遵守协议的前提下自由使用、复制、修改和分发本软件，包括商业使用。
- 分发本软件或其衍生作品时，必须以同一协议公开完整对应源码，保留版权声明与许可声明，并说明修改内容。
- 本软件按“现状”提供，不附带任何明示或默示担保；作者不对使用本软件造成的任何损失承担责任。
- 本项目包含的上游代码、第三方库、图标与字体分别遵循其原有许可，版权归各自作者所有；本项目作者保留其对新增代码的著作权。

Copyright © Anyu 及 TiebaPure 贡献者。上游代码版权归 infinityf4p/TiebaPure-iOS 原作者所有。
