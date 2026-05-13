# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minified + shrunk)
./gradlew assembleRelease

# Run unit tests (JUnit 4)
./gradlew test

# Run a single test class
./gradlew test --tests com.carbrowser.adblock.AdBlockerTest

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Project Overview

CarBrowser (app name: LazyLines) — a lightweight WebView-based browser for the 掌讯5760b Android car head unit (1024x600, 1-2GB RAM). Written in Java 8, Gradle 7.4.2, targetSdk 28, compileSdk 33.

### Key constraints
- **Low-memory target**: 1-2GB RAM devices. Max 5 browser tabs. `MemoryOptimizer` destroys inactive tabs on `onTrimMemory`.
- **Car head unit**: Physical button mapping (`KeyHandler`), immersive fullscreen (`SYSTEM_UI_FLAG_IMMERSIVE_STICKY`), Chinese-market defaults (Baidu search, Bilibili/Youku quick links).
- **Scoped storage**: Android 11+ downloads use app-specific external dir (no WRITE_EXTERNAL_STORAGE permission needed); older Android uses `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/CarBrowser`.

### Architecture

```
App.java  (Application — crash handler, init DB + ad blocker)
  |
  +-- HomeActivity.java  (Launcher — WebView with offline homepage + CarBridge JS bridge)
  |     +-- CarBridge.java  (@JavascriptInterface for search, quick links, adblock status)
  |
  +-- BrowserActivity.java  (Main browser — address bar, nav buttons, tab management)
  |     +-- TabManager.java  (5-tab max, FrameLayout visibility-based switching)
  |     +-- WebViewContainer.java  (WebView wrapper: ad blocking, video detection, settings)
  |     +-- UrlBarHandler.java  (URL vs search query auto-detection)
  |     +-- VideoDetector.java  (@JavascriptInterface — finds <video> elements via JS injection)
  |     +-- VideoPlayerService.java  (ExoPlayer: normal/float/fullscreen + speed: 0.5x-5x)
  |     +-- DownloadManager.java  (HttpURLConnection + SQLite tracking + redirect handling)
  |     +-- MemoryOptimizer.java  (TRIM_MEMORY listener, destroys inactive tabs)
  |     +-- KeyHandler.java  (Physical button mapping: BACK=goBack, MENU=tab switcher)
  |
  +-- VideoActivity.java  (ExoPlayer in fullscreen — speed button, keepScreenOn)
  +-- SettingsActivity.java  (AdBlock toggle, search engine selector)
  +-- BookmarkActivity.java  (ListView of bookmarks, click=open, long-press=delete)
```

### Data layer
- **SQLite** via `DatabaseHelper` (tables: bookmarks, history, ad_rules)
- **DAOs**: `BookmarkDao`, `HistoryDao` (direct SQLite queries, no ORM)
- **Download tracking**: Separate SQLite DB (`carbrowser_downloads.db`) in `DownloadManager.DownloadDb`
- **Preferences**: `carbrowser_prefs` SharedPreferences (search engine, adblock state, home URL)

### Ad blocking (`AdBlocker.java`)
- **URL blocking**: Trie-based domain suffix matching (EasyList Lite format from `assets/adblock/easylist_lite.txt`)
- **DOM hiding**: Injects CSS `display:none!important` for ad selectors
- **Whitelist**: Major Chinese sites (baidu, bilibili, zhihu, taobao, etc.)
- **Thread-safe**: `shouldInterceptRequest` runs on background thread; uses cached `currentUrl` (never calls `WebView.getUrl()` off main thread — Android 16 API 36 enforces this)

### Video
- **Detection**: JS injection on `onPageFinished` + `MutationObserver` for SPA content
- **Playback**: ExoPlayer 2.19.1 (core + ui only, no DASH/HLS extensions)
- **Modes**: Activity (normal/fullscreen) + float window (SYSTEM_ALERT_WINDOW permission)
- **Speed**: 0.5x → 0.75x → 1.0x → 1.25x → 1.5x → 2.0x → 3.0x → 5.0x (cyclic)

### Download (`DownloadManager.java`)
- `HttpURLConnection` with manual redirect handling (cross-protocol HTTP→HTTPS)
- Cookie forwarding from WebView session
- 3 concurrent threads, 10 max redirects, 30s connect / 60s read timeout
- Chrome-like User-Agent + Referer header for anti-hotlink bypass
- Duplicate filename handling: `file (1).ext`, `file (2).ext`, etc.

### Testing
- **Unit tests** (`src/test/`): JUnit 4, test non-Android logic (`AdBlockerTest`, `TabManagerTest`, `UrlBarHandlerTest`, `KeyHandlerTest`, `VideoPlayerServiceTest`)
- **Instrumented tests** (`src/androidTest/`): Espresso, test DB + adblock on device

### CI/CD (GitHub Actions)
- On push to `main`/`master`: `gradle assembleDebug` with JDK 17, uploads APK as artifact
