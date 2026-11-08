# iOS 构建

已用 Xcode27.0（27A5252f）、Swift6.4、iOS27 SDK、XcodeGen2.46.0。最低iOS16.4；SwiftProtobuf锁定1.38.1。内部工程/Module/Scheme保留TiebaPure，桌面显示TiebaPure（2.0起）。

在根目录执行：

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test tool/tiebapure/config.test.mjs
bash tool/tiebapure/build-ios.sh debug
bash tool/tiebapure/build-ios.sh ipa
```

脚本同步 App.xcconfig 并生成 Xcode 工程；Release归档在 `.build/TiebaPure.xcarchive`。未签名IPA保存至 `../build/history/TiebaPure-2.2-unsigned-<SHA前12位>/TiebaPure-2.2-unsigned.ipa`，不替换当前可安装的签名IPA。包含主应用及Open In扩展，未签名。不能把ZIP检查、归档成功当成已可直接安装。

具备已安装模拟器时：

```sh
/Users/y/toolchains/bin/xcrun simctl list devices available
SIMULATOR_UDID='<已存在的模拟器UDID>' bash tool/tiebapure/build-ios.sh test
```

本机没有iOS模拟器运行时；当前版本已在已登记iPhone运行宿主单元测试，覆盖真实UITextView、完整SwiftUI编辑页及中文输入焦点。独立UI Runner受免费签名应用数量限制，未计为通过。详见docs/releases/1.1.md。

签名须同时匹配 `dev.anyu.tiebapure` 和 `dev.anyu.tiebapure.open-in`。本机已通过现有开发者账号的Automatic Signing为主应用和扩展签名；其他环境须使用自己的团队和匹配profiles，可用Xcode配置，或：

```sh
DEVELOPMENT_TEAM='<Team ID>' bash tool/tiebapure/build-ios.sh signed-archive
bash tool/tiebapure/build-ios.sh export '/绝对路径/ExportOptions.plist'
```

签名导出先使用`../.cache/ios-export.*`临时目录，最终只把`TiebaPure-<版本>.ipa`存入项目根目录下的`build/latest/`，与APK放在一起。旧签名IPA自动归档到同级`history/`，相同SHA256不重复保留，新IPA不会移走当前APK。

ExportOptions须与实际开发、Ad Hoc或分发方式匹配。不得将证书私钥、密码或描述文件放进Git/源码zip。当前1.3/build70已从最终签名IPA原位安装到已登记iPhone；签名期限和验收范围见docs/releases/1.3.md。

## 1.0/build61 设备签名记录

本轮使用本机开发者账号与Automatic Signing，为主应用和Open In扩展生成匹配描述文件，开发签名归档、原位安装和启动已在已登记iPhone上完成。开发IPA仅支持描述文件中的设备且有有效期限，不属于通用分发。签名文件和设备备份留在本机忽略目录。

需要自动配置时，在已有Xcode账号环境运行签名归档可加 `-allowProvisioningUpdates -allowProvisioningDeviceRegistration CODE_SIGN_STYLE=Automatic`，DEVELOPMENT_TEAM以自己的账号为准。更新已有应用前先停止本应用并从appDataContainer复制和校验备份；使用devicectl install app原位更新，不卸载、不清理设备容器。

2.2/build80 的 iOS IPA 已放入 `build/latest/`，使用 1.0 原生液态玻璃底栏，保留文字、头像位置和收缩设置。[底栏实现说明](BOTTOM_BAR.md)

编译缓存复用 `src/ios/DerivedData/`，临时导出放项目 `.cache/`。`bash tool/tiebapure/build-ios.sh clean` 只清临时物。2.2/build80 已通过签名 Release 归档、IPA 导出和升级兼容检查。设备 XCTest 和安装尚未执行。
