# 开发约定

1. 阅读根 AGENTS.md、PROGRESS 和 `/Users/y/toolchains/README.md`。`original-source/` 保持只读；所有修改在双端工作目录中。
2. 修改配置后运行 `/Users/y/toolchains/bin/node tool/tiebapure/sync-config.mjs`。名称的解释文案还需同步检查两个原生界面，不能只改安装器名称。
3. 协议改变同时检查 Swift/Java schema、字段映射、签名与 SubmissionPolicy fixtures；保留字符串 ID，避免超出数值范围。端点与字段证据见 ANDROID_PROTOCOL_NOTES。
4. 不记录 BDUSS、STOKEN、Cookie、TBS 或密码；不将私钥、描述文件、真实备份和草稿提交源码。账号写操作不能因网络失败自动重试。
5. 运行受影响检查；日志写入根 logs，构建缓存 `.build`，最终交付 releases。只在指定测试 AVD 验证，不对未指定的真机执行安装或数据清理。

当前检查：Node 配置2项，Android Policy7项+SubmissionPolicy5项，默认跳过线上匿名1项。`RUN_ANONYMOUS_LIVE_SMOKE=1` 才访问真实匿名服务；不要给普通单元测试注入真实凭据。设备 StorageInstrumentedTest 使用随机命名空间及fixture账号，清理仅限它自己创建的数据。

Swift 路由轻量检查可在 Mac 直接编译原 ExternalRoute 源码和 tool/tiebapure/test-routes.swift；嵌入测试 Info.plist 以核对新 Scheme。完整 XCTest 与 UI 测试须使用已安装的 iOS Simulator。`build-for-testing` 仅证明测试目标可编译，不能写为测试套件通过。

Android 同一 applicationId 的 Debug 与本地 Release 签名不同，不能直接覆盖升级。真实用户升级必须复用原签名，禁止通过卸载绕过签名冲突而丢失数据。`signing` 是本次新建的本地 Release 密钥与密码文件，目录权限应保持私有；仅供本项目签名，未来升级须保留，源码包不携带它。

根 Git 记录完整可编辑源码。制作对应源码：`git archive --format=zip --prefix=TiebaPure/ HEAD -o releases/TiebaPure-1.4.15-source.zip`。发布二进制时同时提供匹配源码和许可材料；本任务只做本地交付，未发布远端。
