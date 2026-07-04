# 🐎 الحصان الفاحص - AlHosan Checker

تطبيق فحص اشتراكات Xtream و M3U بسرعة وسهولة، مع واجهة مستخدم احترافية باللغة العربية.

**التكنولوجيا:** Kotlin + Jetpack Compose (الواجهة) + Rust (المعالجة الثقيلة)

---

## 🏗️ البنية المعمارية

```
┌─────────────────────────────────────────────┐
│           Jetpack Compose UI                │
│  (LoginScreen → ResultScreen + Navigation)  │
├─────────────────────────────────────────────┤
│              ViewModel                      │
│  (CheckerViewModel - State Management)     │
├─────────────────────────────────────────────┤
│            Repository Layer                 │
│  (CheckerRepository - IO Dispatcher)       │
├──────────────────────┬──────────────────────┤
│     RustBridge       │   OkHttp (fallback)   │
│  (JNI Interface)     │                      │
├──────────────────────┤──────────────────────┤
│     libalhosan_core.so (Rust)              │
│  ┌─────────────┬─────────────┐             │
│  │  xtream.rs  │   m3u.rs    │             │
│  │  HTTP +     │  Parsing +  │             │
│  │  JSON +     │  Filtering  │             │
│  │  Batch      │  (rayon)    │             │
│  └─────────────┴─────────────┘             │
└─────────────────────────────────────────────┘
```

### لماذا Kotlin + Rust؟

| الميزة | Kotlin (UI) | Rust (Core) |
|--------|-------------|-------------|
| الأداء | سريع لتطوير الواجهة | أسرع لغة للمعالجة |
| الأمان | Null-safety | Memory-safety بدون GC |
| التوازي | Coroutines | Rayon (multithreading) |
| حجم المكتبة | ~2MB | ~500KB (مضغوط) |
| استهلاك الذاكرة |适中 | ضئيل جداً |

---

## 📂 هيكل المشروع

```
al-hosan-checker-kotlin/
├── app/
│   ├── build.gradle.kts          # Gradle config مع Compose + Rust
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── jniLibs/              # Rust .so files (generated)
│   │   │   ├── arm64-v8a/
│   │   │   ├── armeabi-v7a/
│   │   │   └── x86_64/
│   │   ├── java/com/alhosan/checker/
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt       # Activity + Navigation
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt          # Gold-on-Black theme
│   │   │   │   │   └── Type.kt           # Arabic typography
│   │   │   │   ├── screens/
│   │   │   │   │   ├── LoginScreen.kt    # شاشة تسجيل الدخول
│   │   │   │   │   └── ResultScreen.kt   # شاشة النتائج
│   │   │   │   └── components/
│   │   │   │       └── AlHosanComponents.kt  # Reusable UI components
│   │   │   ├── data/
│   │   │   │   ├── model/
│   │   │   │   │   └── Subscription.kt   # Data model
│   │   │   │   └── repository/
│   │   │   │       └── CheckerRepository.kt  # Repository layer
│   │   │   ├── bridge/
│   │   │   │   └── RustBridge.kt         # JNI bridge to Rust
│   │   │   └── viewmodel/
│   │   │       └── CheckerViewModel.kt   # ViewModel
│   │   └── res/
│   │       ├── values/
│   │       │   ├── strings.xml
│   │       │   └── themes.xml
│   │       └── mipmap-*/                 # App icons
├── rust/
│   ├── Cargo.toml                # Rust dependencies
│   ├── build_android.sh          # Build script for Android
│   └── src/
│       ├── lib.rs                # Rust library root
│       ├── xtream.rs             # Xtream API checker
│       ├── m3u.rs                # M3U parser + filter (rayon)
│       └── jni_bridge.rs        # JNI bindings
├── .github/workflows/
│   └── build.yml                # CI/CD pipeline
├── build.gradle.kts             # Root Gradle config
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 🔨 البناء

### المتطلبات

1. **Android Studio** (Hedgehog أو أحدث)
2. **JDK 17**
3. **Android NDK** 26.1.10909125
4. **Rust toolchain** + `cargo-ndk`

```bash
# تثبيت Rust targets للأندرويد
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# تثبيت cargo-ndk
cargo install cargo-ndk
```

### بناء مكتبة Rust

```bash
cd rust
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
./build_android.sh
```

### بناء التطبيق

```bash
# من المجلد الرئيسي
./gradlew assembleRelease
```

---

## 🚀 الوظائف

### فحص اشتراكات Xtream Codes
- فحص حالة الاشتراك (نشط/معطل)
- تاريخ الانتهاء والإنشاء
- عدد الأجهزة المتصلة والحد الأقصى
- حساب عدد القنوات المباشرة والأفلام والمسلسلات
- معلومات السيرفر والبروتوكول

### تحليل قوائم M3U (عبر Rust)
- تحليل سريع للقوائم الكبيرة (rayon parallelism)
- فلترة حسب المجموعة أو الاسم
- إزالة التكرارات
- ترتيب أبجدي

### فحص دفعي (Batch Check)
- فحص عدة اشتراكات بالتوازي
- معالجة متوازية باستخدام rayon

---

## 🎨 التصميم

نفس التصميم الأصلي من تطبيق Flutter:
- **خلفية سوداء** (#000000)
- **لون ذهبي** (#D4AF37) للعناصر الرئيسية
- **سطح داكن** (#0A0A0A) للبطاقات
- **حدود ذهبية داكنة** (#1F1A0F)
- واجهة عربية بالكامل مع RTL

---

## 📱 المتطلبات

- Android 8.0 (API 26) أو أحدث
- اتصال بالإنترنت

---

## ⚖️ المقارنة مع النسخة الأصلية (Flutter/Dart)

| الميزة | Flutter (قديم) | Kotlin + Rust (جديد) |
|--------|----------------|---------------------|
| حجم APK | ~15MB | ~5MB |
| استهلاك الذاكرة | ~80MB | ~30MB |
| سرعة الفحص | 15s timeout | 10s timeout (أسرع HTTP) |
| Batch check | غير متوفر | متوفر (rayon) |
| M3U parsing | غير متوفر | متوفر (Rust) |
| فلترة القنوات | غير متوفرة | متوفرة (rayon) |
| تكلفة البناء | Flutter SDK كامل | Gradle + Cargo فقط |

---

## 📄 الرخصة

هذا المشروع مفتوح المصدر.
