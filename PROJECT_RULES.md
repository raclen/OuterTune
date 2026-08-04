# OuterTune 二次开发规则

## 在线音乐源必须经过统一边界

- **触发信号**：新增在线搜索、播放地址解析、歌词或封面来源。
- **根因/约束**：原项目的 YouTube/InnerTube 类型渗透到 UI、队列和播放服务，直接替换单个 API 会继续产生跨层耦合。
- **正确做法**：UI 与播放队列只使用 `MediaMetadata`；平台专属参数放入 `source`、`sourceId`、`sourceData`，由 `source/lx` 适配层完成搜索和播放 URL 解析；这三个字段必须同步持久化到 `SongEntity`，避免队列恢复后丢失路由信息。
- **验证方式**：在线搜索结果可转换为 `MediaMetadata`，播放服务根据 `source` 路由，不新增 `com.zionhuang.innertube` 依赖到洛雪相关页面；保存并恢复队列后仍保留在线源参数。
- **适用范围**：所有新增或替换的在线音乐源。

## 洛雪自定义源的职责边界

- **触发信号**：导入或执行洛雪自定义音源脚本。
- **根因/约束**：洛雪自定义源主要提供 `musicUrl`、`lyric`、`pic`，不保证提供搜索；脚本依赖 `lx` API 和 QuickJS 运行环境。
- **正确做法**：搜索元数据由独立客户端提供，播放 URL 由 QuickJS 兼容运行时调用音源脚本解析；项目可携带一个明确标注的推荐音源用于开箱测试，用户导入的替换脚本仅保存在本地 DataStore。
- **验证方式**：未导入脚本时给出明确配置错误；导入后可初始化脚本并通过 `musicUrl` action 返回 HTTP(S) 地址。
- **适用范围**：洛雪音源设置、搜索、播放、歌词与封面扩展。

## Media3 fork 的 AGP 必须与主工程一致

- **触发信号**：Gradle 报告同一构建混用 AGP 8.13.2 与 9.0.0。
- **根因/约束**：上游 `media` composite build 使用 AGP 9.0.0，主工程使用 AGP 8.13.2，Gradle 不允许共同解析。
- **正确做法**：保留本地 `media` dependency substitution（项目依赖其中的定制 API），并将 `media/build.gradle` 的 AGP 版本对齐主工程。
- **验证方式**：`:app:compileCoreDebugKotlin` 不出现 `AgpVersionCompatibilityRule`，且 Media3 定制 override 可以编译。
- **适用范围**：当前二次开发分支的 Android 构建。
