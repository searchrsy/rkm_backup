# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Moshi JSON models
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.data.model.** { *; }
-keep class com.example.update.UpdateCheckResponse { *; }
-keep class com.example.update.UpdateCheckResponseJsonAdapter { *; }
-keep class com.example.update.UpdateData { *; }
-keep class com.example.update.UpdateDataJsonAdapter { *; }

# Keep osmdroid (hanya konstruktor arsip refleksi yang dipertahankan; selebihnya dipangkas R8)
-keepclassmembers class * implements org.osmdroid.tileprovider.modules.IArchiveFile {
    public <init>();
}
-dontwarn org.osmdroid.**

# OkHttp optional TLS providers
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
