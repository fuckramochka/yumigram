# Yumigram Proguard & R8 Rules

-dontobfuscate
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Preserve all Yumigram classes and members
-keep class org.yumigram.app.** { *; }
-keepclassmembers class org.yumigram.app.** { *; }

# Preserve Kotlinx Serialization
-keepclassmembers class * {
    *** Companion;
    *** serializer();
}
-dontwarn kotlinx.serialization.**

# Preserve Compose Runtime, UI & Icons
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.compose.material.icons.** { *; }

# AndroidX Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**
