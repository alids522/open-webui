# ProGuard rules for AdoetzGPT Flash

# Keep Capacitor
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * { *; }

# Keep JavaScript Interfaces (NativeBridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.adoetz.gpt.flash.NativeBridge { *; }

# Keep our service
-keep class com.adoetz.gpt.flash.service.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# WebView JS interface needs to be visible
-keepattributes JavascriptInterface

# General Android
-keepattributes *Annotation*
-keepclassmembers class * extends android.content.BroadcastReceiver {
    <init>();
}
