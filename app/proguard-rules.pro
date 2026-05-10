# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# WebView JS Bridge
-keepclassmembers class com.carbrowser.home.CarBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.carbrowser.video.VideoDetector {
    @android.webkit.JavascriptInterface <methods>;
}

# General
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
