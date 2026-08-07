[English](README.md) | **简体中文**

# XPOD

XPOD 是一款面向 Android 13+ 的本地优先播客与文章阅读器。它将播客 RSS、文章 RSS/Atom、离线播放、原生文章阅读和用户指定的本地音乐目录整合在同一个 Jetpack Compose 应用中。

当前版本：**0.9.4** · Android **13+** · **arm64-v8a** · [Apache-2.0](LICENSE)

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
- 按订阅源、未读状态或收藏筛选文章，并支持单篇或批量更新已读状态。
- 在原生 Compose 阅读器中展示标题、图片、引用、列表、代码和表格等结构化内容。
- 当 Feed 正文不足时，可在应用内打开原始网页。

### 本地音乐

- 通过 Android 存储访问框架选择文件夹；XPOD 不申请广泛存储权限。
- 递归建立受支持音频文件的索引，原始文件仍保留在用户选择的位置。
- 按标题、艺术家或专辑搜索，并支持播放全部、队列、随机播放和循环模式。
- 可识别 AAC、AMR、FLAC、M4A、MP3、OGA、OGG、Opus、WAV 和 WMA 扩展名，实际播放能力取决于设备编解码器。

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
- 使用 Android Keystore 加密保存 API Token；断开连接时删除已保存的凭据和密钥。

## 本地优先与隐私

XPOD 不要求注册 XPOD 账号。播客、单集、文章、播放记录、队列、偏好设置和本地音乐索引通过 Room 或 DataStore 保存在设备上，应用备份默认关闭。

只有本身需要联网的操作才会访问网络，包括获取 Feed 与配图、串流或下载音频、打开原始网页，以及访问用户主动配置的 Cloud Memos 实例。下载文件保存在应用专属目录，本地音乐权限仅覆盖用户通过系统选择器明确授权的文件夹。

Cloud Memos 完全可选，也不是 XPOD 本地数据库的通用跨设备同步服务。

## 环境要求

- JDK 17
- Android SDK Platform 36.1
- 用于安装的 Android 13+ ARM64 真机或模拟器

仓库已包含 Gradle 9.6.1 Wrapper，无需单独安装 Gradle。

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

连接真机或模拟器后运行：

```bash
./gradlew connectedDebugAndroidTest
```

## 架构

| 分层 | 职责 | 主要技术 |
| --- | --- | --- |
| UI | 自适应 Compose 页面；操作通过 `MainViewModel` 下发；状态通过 `StateFlow` 暴露 | Jetpack Compose、Material 3、Lifecycle |
| 数据 | Feed 解析、稳定实体、持久化、设置、OPML、本地音乐与 Cloud Memos | Room、DataStore、OkHttp、Android Keystore、SAF |
| 播放 | 后台播放、状态恢复、队列、随机/循环与媒体库集成 | Media3 `MediaLibraryService` |
| 下载 | 具有可配置网络要求的应用私有单集下载 | Media3 `DownloadService` 和 `DownloadManager` |
| 后台任务 | 带有网络约束和重试机制的每日播客、文章刷新 | WorkManager |
| 依赖注入 | 应用级 Repository、数据库、网络客户端与时钟 | Hilt |

常规数据流为：Compose UI → `MainViewModel` → Repository/Controller → Room、DataStore、Media3、SAF 或明确的外部 I/O。

## 当前边界

- 最低支持 Android 13 / API 33。
- 当前 APK 仅面向 `arm64-v8a`。
- Feed 地址和播客音频地址必须使用 HTTPS。
- XPOD 采用本地优先设计，目前不会在设备间同步 Room 数据库。

## 参与贡献

项目命令与工程规则见 [`AGENTS.md`](AGENTS.md)。持久化和外部 I/O 应由 Repository 负责，ViewModel 通过 `StateFlow` 暴露 UI 状态；修改 Feed 逻辑时应保持稳定标识，并为每项行为变更运行针对性的测试。

## 许可证

XPOD 使用 [Apache License 2.0](LICENSE)。
