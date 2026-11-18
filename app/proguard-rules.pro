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

# Keep osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OkHttp optional TLS providers
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
