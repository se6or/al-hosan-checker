# Rust JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep data models
-keep class com.alhosan.checker.data.model.** { *; }

# Keep Rust bridge
-keep class com.alhosan.checker.bridge.RustBridge { *; }
