# CarBrowser - 车机浏览器

适配安卓车机（掌讯5760b，1024×600）的轻量套壳浏览器。

## 核心特性

### 🚀 流畅低耗
- WebView 硬件加速渲染
- 精简依赖（仅 ExoPlayer 核心）
- 内存优化：限制缓冲、及时释放
- APK 体积 < 10MB（R8 混淆压缩）

### 🎬 视频工具
- **自动检测**：JS 注入监控 DOM，实时发现 `<video>` 元素
- **悬浮窗播放**：独立窗口，可拖拽移动，不离开当前页面
- **倍速控制**：0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x / 3x / 4x / 5x
- **最小化模式**：悬浮窗可缩小为迷你画中画
- 支持 HLS 流媒体

### 🏠 简洁主页
- 实时时钟 + 日期
- 搜索框（自动识别网址/搜索词）
- 12个常用快捷入口（B站/腾讯/爱奇艺/优酷/YouTube等）
- 暗色主题，适配车机屏幕

### 🛡 广告拦截
- **URL 级拦截**：50+ 广告域名黑名单，拦截请求不消耗流量
- **元素级隐藏**：CSS + JS 注入，隐藏广告 DOM
- **白名单保护**：主流站点防误杀
- 零外部依赖，纯内存匹配

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| 最低SDK | 21 (Android 5.0) |
| 目标SDK | 28 |
| 浏览器内核 | WebView + 硬件加速 |
| 视频播放 | ExoPlayer 2.19 |
| 广告拦截 | 自研 URL + CSS 过滤 |
| UI | 原生 Android XML |

## 项目结构

```
app/src/main/java/com/carbrowser/
├── CarBrowserApp.kt         # Application 初始化
├── MainActivity.kt          # 主界面 + 浏览器
├── CarWebView.kt            # 自定义 WebView
├── BrowserJsBridge.kt       # JS 桥接（视频检测+广告隐藏）
├── AdBlocker.kt             # 广告拦截器
└── FloatingVideoService.kt  # 悬浮窗视频播放服务
```

## 构建

```bash
# Android Studio 打开项目直接构建
# 或命令行：
./gradlew assembleDebug
./gradlew assembleRelease
```

## 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# 或
adb install app/build/outputs/apk/release/app-release.apk
```

## 悬浮窗权限

首次使用视频悬浮窗需要授权：
设置 → 应用 → 车机浏览器 → 悬浮窗权限 → 允许

## 车机适配

- 固定 1024×600 分辨率布局
- 工具栏 40dp 高度，触摸友好
- 物理按键支持（返回键后退、菜单键切换视频面板）
- 屏幕常亮（视频播放时）
- 允许 HTTP 明文（车机网络环境）
