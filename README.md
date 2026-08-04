# OuterTune 洛雪版

<img src="./assets/outertune.webp" height="88" alt="OuterTune 应用图标">

OuterTune 洛雪版是一个 Android 本地音乐播放器和在线音乐播放器二次开发项目。项目保留原有 Material 3 界面、播放队列和 Media3 播放内核，在线搜索与播放链路接入酷我搜索和洛雪自定义音源。

## 当前功能

- 本地音乐播放
  - MP3、FLAC、OGG 等常见格式
  - 本地文件夹、媒体库、播放历史
  - 多播放队列、队列持久化和恢复
- 洛雪在线音乐
  - 使用酷我接口搜索歌曲、歌手、专辑和时长
  - 使用洛雪 JS 音源解析实际播放地址
  - 内置 `长青SVIP音源 v1.2.0` 作为开箱测试音源
  - 支持导入其他洛雪 JS 音源
  - 支持 `128k`、`320k`、`flac`、`flac24bit` 音质选择
  - 自动修正酷我封面 CDN 地址，兼容 Android HTTPS 网络环境
- 播放体验
  - Material 3 界面和深色模式
  - 后台播放和媒体通知
  - 歌词显示、LRC/TTML 等歌词格式
  - 音频均衡、速度和音调调整
  - Android Auto 支持

## 导入洛雪音源

1. 打开应用搜索页。
2. 点击顶部右侧的音源文件图标，或进入 **设置 → 洛雪音源**。
3. 点击 **导入洛雪自定义音源**。
4. 选择一个 `.js` 音源文件。
5. 在同一页面选择默认音质。

应用已经内置推荐音源，不导入也可以直接搜索歌曲并播放。用户导入的脚本只保存在本机应用数据中。

## 搜索与播放

1. 在搜索页输入歌曲名称，例如 `一路生花`。
2. 在线搜索结果来自酷我。
3. 点击歌曲后，应用将歌曲元数据转换为统一的 `MediaMetadata`。
4. 播放服务根据歌曲的 `source` 信息调用洛雪 JS 音源解析播放地址。

在线源参数会随歌曲保存到本地数据库，队列重启恢复后仍然可以使用洛雪解析链路。

## 架构说明

```text
搜索页
  -> KuwoSearchClient
  -> MediaMetadata(source/sourceId/sourceData)
  -> MusicService
  -> LxSourceRuntime (QuickJS)
  -> 洛雪 JS 音源
  -> Media3 播放器
```

- `source/lx/KuwoSearchClient.kt`：酷我搜索与封面元数据适配。
- `source/lx/LxSourceRuntime.kt`：QuickJS 运行时、洛雪 API 和 HTTP Bridge。
- `app/src/main/assets/script/default-lx-source.js`：内置测试音源。
- `app/src/main/assets/script/user-api-preload.js`：洛雪移动版 API 兼容层。
- `MediaMetadata.sourceData`：保存音源所需的歌曲参数，不让平台字段渗透到 UI 和队列代码。

## 构建

### 环境

- Windows 11 或 Linux
- JDK 21
- Android SDK 36
- Android NDK/CMake（本地 TagLib 模块需要）

### PowerShell

```powershell
$env:JAVA_HOME = "PATH_TO_JDK_21"
$env:ANDROID_SDK_ROOT = "PATH_TO_ANDROID_SDK"
./gradlew.bat :app:assembleCoreDebug --no-configuration-cache
```

生成的 APK 位于：

```text
app/build/outputs/apk/core/debug/
```

Debug 包名为 `com.dd3boh.outertune.debug`，应用名称为 **OuterTune 洛雪版**，可与其他安装包并存。

## 项目状态

当前版本已经完成“酷我搜索 → 洛雪音源解析 → Media3 播放”的首个完整闭环。原项目中的部分 YouTube 页面和兼容代码仍在逐步清理，后续会继续收敛为本地音乐与洛雪在线音源两类功能。

## 目录结构

```text
app/src/main/java/com/dd3boh/outertune/
  source/lx/          洛雪搜索与脚本运行时
  playback/           播放服务、队列和 Media3 接入
  ui/screens/         Compose 页面
  db/                 Room 数据库和队列持久化
app/src/main/assets/script/
  default-lx-source.js
  user-api-preload.js
media/                本地 Media3 定制模块
```

## 许可证与致谢

本项目沿用原 OuterTune/InnerTune 代码的 GPL-3.0 许可证。具体版权和第三方依赖请查看应用内开源许可页面及各模块源码头部声明。

- [OuterTune](https://github.com/OuterTune/OuterTune)
- [InnerTune](https://github.com/z-huang/InnerTune)
- [洛雪音乐 API 规范](https://github.com/lyswhut/lx-music-desktop)
