# Yumigram Proguard & R8 Rules

# Preserve all native JNI methods in YumigramBridge
-keepclasseswithmembernames class org.yumigram.app.bridge.YumigramBridge {
    native <methods>;
}

# Preserve serialization models for JNI JSON boundary
-keep class org.yumigram.app.bridge.** { *; }
-keepclassmembers class org.yumigram.app.bridge.** {
    *** Companion;
    *** serializer();
}

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses,Signature
-dontwarn kotlinx.serialization.**

# AndroidX Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loader
-dontwarn coil.**
-keep class coil.** { *; }
