# Android 构建

已用JDK21.0.12.1、Gradle8.14.3、AGP8.13.0、Kotlin2.2.20、Compile/Target SDK36；Min SDK26。唯一SDK目录 `/Users/y/toolchains/android-sdk`。Gradle wrapper复用已有工具，依赖来自Google/Maven Central。

```sh
source /Users/y/toolchains/env.sh
bash tool/tiebapure/build-android.sh lintDebug assembleDebugAndroidTest
```

Debug包归档到 `../build/history/TiebaPure-2.2-debug-<SHA前12位>/TiebaPure-2.2-debug.apk`，不替换当前Release。脚本执行单测和APK签名校验。网络需要本机代理时按环境传入Gradle的http/https.proxyHost/proxyPort，不将代理凭据硬编码。

Release配置从密钥和密码文件环境变量读取本地密钥和密码文件，不在源码中保存密码。本机既有私有密钥在 `signing/tiebapure-release.jks`，alias `tiebapure`，密码文件同目录 `release-password.txt`；未来升级要复用这份密钥。

```sh
TIEBAPURE_ANDROID_MODE=release \
TIEBAPURE_KEYSTORE="$PWD/signing/tiebapure-release.jks" \
TIEBAPURE_KEY_PASSWORD_FILE="$PWD/signing/release-password.txt" \
bash tool/tiebapure/build-android.sh lintRelease
```

输出 `../build/latest/TiebaPure-2.2.apk`，与当前签名IPA放在同一文件夹。内容变化时旧APK自动移至同级`history/`，相同SHA256只保留一份，新APK不会移走当前IPA。文件存储逻辑见tool/tiebapure/store-package.mjs，验证命令为`/Users/y/toolchains/bin/node --test tool/tiebapure/store-package.test.mjs`。

Release未开启代码混淆；Debug和Release签名不同，不能互相直接覆盖，不能在有用户数据的设备上以卸载作为修复方法。

从源码包重建时，私钥不会随包提供。可先用工具箱的`keytool -genkeypair -alias tiebapure -keyalg RSA -keysize 3072 -validity 10000 -keystore /自选私有路径/release.jks`交互创建自己的密钥，再把相应密码保存到仅本人可读的文件并设置上述变量。勿覆盖已有升级签名，勿把私钥和密码发到仓库或日志。Debug重建不需要Release密钥。

独立线上匿名验证（仅公开读取）：

```sh
cd android
RUN_ANONYMOUS_LIVE_SMOKE=1 ./gradlew testDebugUnitTest --rerun-tasks
```

设备验证只使用本项目私有AVD `TiebaPure-lab`、端口5580、Android35 ARM64，AVD目录在 `.build/avd`。设备存储测试命令：

```sh
/Users/y/toolchains/bin/adb -s emulator-5580 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/y/toolchains/bin/adb -s emulator-5580 shell am instrument -w -r dev.anyu.tiebapure.test/androidx.test.runner.AndroidJUnitRunner
```

该测试要求先安装同源码Debug主包；它不针对Release签名包运行。Release需独立做安装/启动检查。Android35模拟器结果不能替代Android26/36与各厂商真机验收。

只验证而不生成安装包：`cd android && ./gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin -Pkotlin.compiler.execution.strategy=in-process`。脚本增加`clean`仅清空项目`.cache/`。2.2/build79已签名打包到项目build/latest/。
