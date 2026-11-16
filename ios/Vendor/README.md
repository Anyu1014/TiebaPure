# AMR-NB decoder

`OpenCoreAMR.xcframework` is built from the adjacent `OpenCoreAMR-Source`, upstream https://github.com/CrispStrobe/opencore-amr commit `f3e8f39841f9eb2447ab34701e5d5a99760836cc`, Apache-2.0; see its `LICENSE`. The GNU Automake 1.16.5 `compile` helper was retrieved from its upstream repository for the bundled generated configure script.

The framework contains iOS arm64, iOS Simulator arm64/x86_64, and Mac Catalyst arm64/x86_64 slices, built with Xcode 27 and deployment target 16.4. For each slice, configure the source in a separate out-of-tree directory with `--disable-shared --enable-static` and the corresponding Xcode SDK, architecture/target and minimum version; run `make -C amrnb`. Combine same-platform architectures with `xcrun lipo -create`, then assemble the three libraries with `xcodebuild -create-xcframework -library <library> -headers <source>/amrnb ...`. Use the toolchain at `/Users/y/toolchains`.
