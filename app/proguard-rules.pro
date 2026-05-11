# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# WebView JS Bridge
-keepclassmembers class com.lazylines.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.lazylines.** { *; }
