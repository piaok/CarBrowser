# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# WebView JS Bridge
-keepclassmembers class com.carbrowser.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.carbrowser.** { *; }
