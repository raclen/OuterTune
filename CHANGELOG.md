# OuterTune v1.0.3

## 修复

- 修复未配置 GitHub Actions 签名 Secrets 时 Release 构建失败的问题，自动回退到 Debug 签名。
- 修复 Android 媒体搜索入口缺少 `MEDIA_PLAY_FROM_SEARCH` 声明导致的 Lint 构建失败。
- Release 草稿发布在未配置 `REL_TOKEN` 时自动使用 GitHub Actions 默认令牌。

# OuterTune v1.0.2

## 修复

- 恢复 `ffMetadataEx` Git 子模块，确保 GitHub Actions checkout 后可配置 Gradle 项目。

# OuterTune v1.0.1

## 修复

- 修复 GitHub Actions 中 Gradle Wrapper 缺少执行权限导致 Release 构建失败的问题。

# OuterTune v1.0.0

首个洛雪音源版公开版本。

## 主要更新

- 保留 Material 3 播放器界面，首页精简为最近播放。
- 移除 YouTube/InnerTube 登录、推荐、浏览、同步和相关播放链路。
- 接入洛雪 JS 音源运行时，支持导入自定义音源。
- 搜索支持本地优先，并可切换酷狗、网易云、QQ 音乐和酷我音源。
- 搜索结果先显示文字，封面异步加载；播放时补齐缺失封面。
- 修复酷狗 `union_cover` 和网易云 `picId` 封面解析。
- 最近播放保留本地歌曲记录；内嵌歌词优先于联网歌词。
- 下载音质支持低品质、标准、高品质和 Hi-Res，直接保存到设置的下载文件夹。
- 移除 Media3 下载缓存和迁移逻辑。
- 使用系统字体与简体中文界面。

## 构建

GitHub Actions 会在 `main` 推送时构建 Debug APK；推送 `v*` 标签时构建 Release APK 并创建 Release 草稿。
