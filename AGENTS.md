# TiebaPure 双端工作区

先读 `docs/PROGRESS.md`，以其中的实际验证结果继续工作。

- `original-source/` 是只读原仓库；编辑 `ios/` 与 `android/`。来源与固定提交见 `docs/UPSTREAM.md`。
- 本机工具统一使用 `/Users/y/toolchains`，非交互命令先加载其 `env.sh`。
- 品牌、应用标识和版本以 `app-config.json` 为准；iOS 构建前运行 `tool/tiebapure/sync-config.mjs`。
- 协议变更同步 iOS/Android schema、映射与独立 wire fixture。账号切换应取消并隔离旧账号写操作。
- 网络保留 HTTPS、目标域名、重定向、响应体积与内容类型校验；凭据不进入日志、源码或备份。
- 修改后运行对应的构建/测试脚本；区分编译、安装、启动、模拟数据测试与真实接口测试。
- 构建说明见 `docs/BUILD_IOS.md`、`docs/BUILD_ANDROID.md`；功能差异见 `docs/FEATURES.md`。
- 本地开发和打包已获授权。发布仓库、上传安装包或安装用户真机须有对应指令。
