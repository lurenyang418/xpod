[English](README.md) | **简体中文**

# XPOD

XPOD 是一款面向 Android 13+ 的本地优先播客与文章阅读器。它将播客 RSS、文章 RSS/Atom、离线播放、原生文章阅读以及本地音乐和视频库整合在同一个 Jetpack Compose 应用中。媒体库可以使用平台级扫描，也可以通过 Android 存储访问框架选择文件夹进行限制访问。

当前版本：**0.10.0** · Android **13+** · **arm64-v8a** · [Apache-2.0](LICENSE)

## 截图

<p align="center">
  <img src="screenshots/01-podcasts-home.png" width="30%" alt="XPOD 播客订阅、原创展示封面和迷你播放器" />
  <img src="screenshots/02-now-playing.png" width="30%" alt="XPOD 完整播客播放器，包含封面、进度、倍速和跳转控制" />
  <img src="screenshots/04-article-reader.png" width="30%" alt="XPOD 原生文章阅读器，包含标题、配图和格式化正文" />
</p>

更多真机截图和可重复执行的展示数据流程见 [`screenshots/`](screenshots/README.md)。

## 功能

### 播客与播放

- 通过 HTTPS 添加播客 Feed，并在刷新过程中保持播客和单集标识稳定。
- 浏览订阅、新单集、未播放单集、收藏、最近播放和继续收听内容。
- 通过 Media3 前台服务播放音频并持久化播放状态。
- 支持倍速、后退 10 秒、前进 30 秒、上一首/下一首和完整播放器。
- 通过“下一首播放”和“加入队尾”构建队列，并可调整顺序、移除或清空项目。
- 将单集下载到应用专属目录。默认仅使用非计费网络，也可在设置中允许蜂窝网络。

### 文章

- 使用与播客相同的订阅入口添加 RSS 或 Atom 文章 Feed。
- `Reader` Tab 专用于 RSS/Atom 文章；本地书籍位于独立的 `Books`（书库）Tab。
- 按订阅源、未读状态或收藏筛选文章，并支持单篇或批量更新已读状态。
- 在原生 Compose 阅读器中展示标题、图片、引用、列表、代码和表格等结构化内容。
- 当 Feed 正文不足时，可在应用内打开原始网页。

### 本地音乐

- 授予 `READ_MEDIA_AUDIO` 后扫描设备的 MediaStore 音频库，也可以通过 Android 存储访问框架选择文件夹，以限制扫描范围。
- 递归建立受支持音频文件的索引，原始文件仍保留在用户选择的位置。
- 按标题、艺术家或专辑搜索，并支持播放全部、队列、随机播放和循环模式。
- 可识别 AAC、AMR、FLAC、M4A、MP3、OGA、OGG、Opus、WAV 和 WMA 扩展名，实际播放能力取决于设备编解码器。

### 本地视频

- 授予 `READ_MEDIA_VIDEO` 后，可通过 MediaStore 自动扫描设备视频库；也可以通过存储访问框架选择单个文件夹进行限制访问。MediaStore 只覆盖系统已建立索引的视频，其他位置可使用文件夹选择。
- 递归建立常见视频文件的索引，支持 3GP、AVI、FLV、M4V、MKV、MOV、MP4、MPEG、MPG、TS、WebM 和 WMV 扩展名。
- 使用 Media3 沉浸式播放器播放视频，支持适应画面、暂停/继续、后退 10 秒、前进 30 秒、倍速和观看进度记忆。
- 播放时可打开当前文件夹的视频队列切换视频；视频库提供紧凑的纯图标操作菜单，可执行重命名、查看属性和删除。
- 视频播放与播客/音乐后台队列隔离；打开视频时会暂停音频。

### 本地书籍

- 通过 Android 存储访问框架选择目录，为 EPUB 和 PDF 文件建立应用内书库。
- 在原生 Compose 阅读器中阅读 EPUB，支持按 spine 排列的目录、字号、行距和阅读主题调节。
- 使用系统 `PdfRenderer` 按需渲染 PDF 页面，支持缩放和有上限的位图缓存。
- 阅读进度、收藏和所选目录保存在本机，原始书籍文件保持在原位置。

### 本地 Markdown 笔记

- 在独立的“笔记”Tab 中创建和编辑本地 Markdown；即使隐藏或不使用 Cloud Memos，也可以单独使用本地笔记。
- 支持源码编辑与主题化预览切换。每篇笔记会单独记住 GitHub、Newsprint、Night、“跟随应用”或导入的自定义主题；自定义主题可通过经过校验的 JSON 导入/导出（不接受任意 CSS 或脚本），本地最多保存 24 套。可从[主题 JSON 示例](docs/markdown-theme-example.json)开始修改；新笔记默认使用最近选择的主题。Kotlin、Java、Python 代码围栏提供轻量语法高亮，远程图片只加载 HTTPS 地址。
- 可通过系统图片选择器插入 JPEG、PNG、WebP 或 GIF（单张最大 20 MB）；图片会复制到应用私有目录，全部笔记 ZIP 会打包图片并改写为可移植的相对链接。
- 支持键盘上方 Markdown 工具栏和有界撤销/重做、标题/正文搜索、笔记排序，以及分享 Markdown 文本或文件。
- 可通过系统文件选择器导入 `.md` 文件；正文中的 H1 优先作为标题，否则使用文件名。
- 单篇笔记可导出为 UTF-8 Markdown、主题化 HTML 或分页 PDF，也可以将全部笔记导出为包含 Markdown 文件、图片附件、逐篇主题信息、自定义主题定义和清单的 ZIP。需要连同本地图片或自定义主题迁移时请使用 ZIP；单独导出的 `.md` 不会包含图片文件，HTML/PDF 暂不嵌入本地附件。PDF 中的表格会按文本行呈现，不保留 Markdown 表格的列对齐方式。
- 笔记只保存在本地 Room 数据库中。应用备份默认关闭，清除应用数据前请先导出 Markdown 或 ZIP。

### 订阅与组织

- 通过 OPML 导入或导出混合的播客和文章订阅。
- 在网络可用时，每天自动刷新一次播客和文章 Feed。
- 调整可选 Tab 的顺序或隐藏它们；手机底部导航和平板侧栏共享同一顺序。
- 支持跟随系统、浅色和深色主题，并可选择 Android 动态配色。
- 适配手机和平板；宽度达到 600dp 时使用大屏导航和内容布局。

### 可选的 Cloud Memos 集成

- 使用具备读写权限的 `cm_pat_` Token 连接 HTTPS [Cloud Memos](https://github.com/lurenyang418/cloud-memos) 实例。
- 浏览、搜索、筛选、新建、归档、恢复、分享 Memo，并在实例支持时移入回收站。
- 将播客单集或文章保存为 Markdown Memo。
- 可在本地笔记编辑器中手动将笔记上传为私有 Memo；这是单向上传，不会同步。当前不上传本地图片，图片会以替代文本保留。
- 使用 Android Keystore 加密保存 API Token；断开连接时删除已保存的凭据和密钥。

## 本地优先与隐私

XPOD 不要求注册 XPOD 账号。播客、单集、文章、播放记录、队列、偏好设置、本地媒体索引和 Markdown 笔记通过 Room 或 DataStore 保存在设备上，应用备份默认关闭；清除应用数据前请先导出本地笔记。

只有本身需要联网的操作才会访问网络，包括获取 Feed 与配图、串流或下载音频、打开原始网页，以及访问用户主动配置的 Cloud Memos 实例。下载文件保存在应用专属目录。扫描音乐库使用 Android 的 `READ_MEDIA_AUDIO` 权限；自动扫描视频使用 `READ_MEDIA_VIDEO` 查询 Android MediaStore 索引。用户也可以通过系统选择器选择文件夹，以限制视频访问范围。

Cloud Memos 完全可选，也不是 XPOD 本地数据库的通用跨设备同步服务。

## 环境要求

- JDK 17
- Android SDK Platform 37
- 用于安装的 Android 13+ ARM64 真机或模拟器

仓库已包含 Gradle 9.7.1 Wrapper，无需单独安装 Gradle。

## 构建与安装

```bash
git clone https://github.com/lurenyang418/xpod.git
cd xpod
./gradlew assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

安装到已连接的设备：

```bash
./gradlew installDebug
```

Debug 包名为 `tech.lury.xpod.debug`，可以和正式版包名 `tech.lury.xpod` 同时安装。

构建经过优化的 Release 版本：

```bash
./gradlew assembleRelease
```

输出文件为 `app/build/outputs/apk/release/app-arm64-v8a-release.apk`。发布正式构建前应配置 `keystore.properties`；如果缺少该文件，本地 Release 版本会回退使用 Debug 签名。带 Tag 的构建由 [Release 工作流](.github/workflows/build-apk.yml) 生成并发布到 [GitHub Releases](https://github.com/lurenyang418/xpod/releases)。

## 验证

提交变更前建议运行以下检查：

```bash
./gradlew spotlessCheck testDebugUnitTest assembleDebug lintDebug
```

Spotless 使用仓库固定的 ktfmt 版本检查 Kotlin 和 Gradle 文件格式。需要
格式化时运行 `./gradlew spotlessApply`，然后审查格式化产生的 diff 再提交。

发布正式版本前建议运行面向 Release 的检查：

```bash
./gradlew spotlessCheck testDebugUnitTest lintDebug assembleRelease
```

连接真机或模拟器后运行：

```bash
./gradlew connectedDebugAndroidTest
```

## 架构

| 分层 | 职责 | 主要技术 |
| --- | --- | --- |
| UI | 自适应 Compose 页面；操作通过 `MainViewModel` 下发；状态通过 `StateFlow` 暴露 | Jetpack Compose、Material 3、Lifecycle |
| 数据 | Feed 解析、稳定实体、持久化、设置、OPML、本地音乐/视频与 Cloud Memos | Room、DataStore、OkHttp、Android Keystore、SAF、MediaStore |
| 播放 | 后台播放、状态恢复、队列、随机/循环与媒体库集成 | Media3 `MediaLibraryService` |
| 下载 | 具有可配置网络要求的应用私有单集下载 | Media3 `DownloadService` 和 `DownloadManager` |
| 后台任务 | 带有网络约束和重试机制的每日播客、文章刷新 | WorkManager |
| 依赖注入 | 应用级 Repository、数据库、网络客户端与时钟 | Hilt |

常规数据流为：Compose UI → `MainViewModel` → Repository/Controller → Room、DataStore、Media3、SAF、MediaStore 或明确的外部 I/O。

## 当前边界

- 最低支持 Android 13 / API 33。
- 当前 APK 仅面向 `arm64-v8a`。
- Feed 地址和播客音频地址必须使用 HTTPS。
- XPOD 采用本地优先设计，目前不会在设备间同步 Room 数据库。

## 参与贡献

项目命令与工程规则见 [`AGENTS.md`](AGENTS.md)。持久化和外部 I/O 应由 Repository 负责，ViewModel 通过 `StateFlow` 暴露 UI 状态；修改 Feed 逻辑时应保持稳定标识，并为每项行为变更运行针对性的测试。

## 许可证

XPOD 使用 [Apache License 2.0](LICENSE)。
