# TinyHive Bridge ProGuard Rules

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.tinyhive.bridge.model.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Accessibility Service
-keep class com.tinyhive.bridge.service.TinyHiveAccessibilityService { *; }
