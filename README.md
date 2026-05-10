# CarBrowser — 车机浏览器

为掌讯5760b车载终端开发的轻量级浏览器应用。

## 特性

- 🌐 基于 WebView 的网页浏览
- 🎬 ExoPlayer 视频增强播放（倍速/浮窗/全屏）
- 🛡️ 内置广告拦截（URL + DOM 双层）
- 🏠 离线主页（时钟/搜索/快捷链接）
- 📑 多标签页（上限5个）
- 🚗 车机物理按键适配
- 📱 适配 1024×600 分辨率
- 💾 低内存优化（1-2GB RAM 设备）

## 技术栈

- Java 8 + Kotlin
- Android minSdk 21 / targetSdk 28
- WebView + ExoPlayer 2.19.x
- 自实现广告拦截（无第三方 SDK）
- SQLite + SharedPreferences

## 构建

```bash
./gradlew assembleRelease
```

构建产物：`app/build/outputs/apk/release/`

## 许可证

MIT
