# الحصان الفاحص — AlHosan Checker

تطبيق فحص اشتراكات Xtream و M3U بسرعة وسهولة، مع واجهة مستخدم احترافية باللغة العربية والإنجليزية.

**التقنولوجيا:** Kotlin + Jetpack Compose (الواجهة) + Rust (المعالجة الثقيلة)

---

## البنية المعمارية

```
┌──────────────────────────────────────────────────┐
│           Jetpack Compose UI                      │
│  (LoginScreen → ResultScreen + Navigation)        │
├──────────────────────────────────────────────────┤
│              ViewModel                             │
│  (CheckerViewModel - State Management)            │
├──────────────────────────────────────────────────┤
│            Repository Layer                        │
│  (CheckerRepository - IO Dispatcher)              │
├──────────────────────┬───────────────────────────┤
│     RustBridge       │   OkHttp (fallback)        │
│  (JNI Interface)     │                            │
├──────────────────────┼───────────────────────────┤
│     libalhosan_core.so (Rust)                     │
│  ┌──────────────┬──────────────┐                  │
│  │  xtream.rs   │   m3u.rs     │                  │
│  │  HTTP +      │  Parsing +   │                  │
│  │  JSON +      │  Filtering   │                  │
│  │  Batch       │  (rayon)     │                  │
│  └──────────────┴──────────────┘                  │
└──────────────────────────────────────────────────┘
```

### لماذا Kotlin + Rust؟

| الميزة | Kotlin (UI) | Rust (Core) |
|--------|-------------|-------------|
| الأداء | سريع لتطوير الواجهة | أسرع لغة للمعالجة |
| الأمان | Null-safety | Memory-safety بدون GC |
| التوازي | Coroutines | Rayon (multithreading) |
| حجم المكتبة | ~2MB | ~500KB (مضغوط) |
| استهلاك الذاكرة | متوسط | ضئيل جداً |

---

## هيكل المشروع

```
al-hosan-checker/
├── app/
│   ├── build.gradle.kts          # Gradle config مع Compose + Rust
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── jniLibs/              # Rust .so files (generated)
│       │   ├── arm64-v8a/
│       │   ├── armeabi-v7a/
│       │   └── x86_64/
│       ├── java/com/alhosan/checker/
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── theme/
│       │   │   ├── i18n/
│       │   │   ├── screens/
│       │   │   │   ├── SplashScreen.kt
│       │   │   │   ├── LoginScreen.kt
│       │   │   │   ├── ResultScreen.kt
│       │   │   │   └── HistoryScreen.kt
│       │   │   └── components/
│       │   ├── data/
│       │   ├── bridge/
│       │   ├── util/
│       │   └── viewmodel/
│       └── res/
│           ├── drawable/
│           │   └── ic_alhosan_logo.png
│           ├── mipmap-*/
│           └── values/
│               ├── strings.xml
│               └── themes.xml
├── rust/
│   ├── Cargo.toml
│   ├── build_android.sh
│   └── src/
│       ├── lib.rs
│       ├── xtream.rs
│       ├── m3u.rs
│       └── jni_bridge.rs
├── .github/workflows/
│   └── build.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## البناء

### المتطلبات

1. **Android Studio** (Hedgehog أو أحدث)
2. **JDK 17**
3. **Android NDK** 27.0.12077973
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
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
./build_android.sh
```

### بناء التطبيق

```bash
./gradlew assembleRelease
```

---

## الوظائف

### فحص اشتراكات Xtream Codes
- فحص حالة الاشتراك (نشط / معطل)
- تاريخ الانتهاء والإنشاء
- عدد الأجهزة المتصلة والحد الأقصى
- حساب عدد القنوات المباشرة والأفلام والمسلسلات
- معلومات السيرفر والبروتوكول

### تحليل قوائم M3U (عبر Rust)
- تحليل سريع للقوائم الكبيرة (rayon parallelism)
- فلترة حسب المجموعة أو الاسم
- إزالة التكرارات

### فحص دفعي (Batch Check)
- فحص عدة اشتراكات بالتوازي عبر rayon

### تصدير النتيجة كصورة
- التقاط كارد النتيجة عبر Android Canvas
- حفظ PNG في `Pictures/AlHosan` عبر MediaStore
- يعمل على Android 10+ بدون أذونات، وأقل عبر `WRITE_EXTERNAL_STORAGE`

### إطلاق فوري
- شاشة البداية تظهر لحظة النقر على أيقونة التطبيق عبر `core-splashscreen`
- لا انتظار، لا شاشة سوداء

### واجهة المستخدم
- تبديل اللغة (عربي / إنجليزي) في أي وقت
- إخفاء زر "حفظ" عند فتح عنصر محفوظ من السجل
- معالجة زر الرجوع للنظام عبر `BackHandler`

---

## التصميم

- **خلفية سوداء** `#000000`
- **لون ذهبي** `#D4AF37` للعناصر الرئيسية
- **سطح داكن** `#0A0A0A` للبطاقات
- **حدود ذهبية داكنة** `#1F1A0F`
- واجهة عربية مع RTL كامل

---

## المتطلبات

- Android 8.0 (API 26) أو أحدث
- اتصال بالإنترنت

---

## الرخصة

هذا المشروع مفتوح المصدر.
