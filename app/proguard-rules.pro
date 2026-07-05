# Rust JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep data models (used by kotlinx.serialization)
-keep class com.alhosan.checker.data.model.** { *; }

# Keep Rust bridge
-keep class com.alhosan.checker.bridge.RustBridge { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
    *** serializer();
}
-keep,includedescriptorclasses class com.alhosan.checker.data.model.**$$serializer { *; }
-keepclassmembers class com.alhosan.checker.data.model.** {
    *** Companion;
    *** serializer();
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
