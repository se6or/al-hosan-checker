<div align="center">

<img src="app/src/main/res/drawable/ic_alhosan_logo.png" width="120" alt="AlHosan Logo" />

# الحصان الفاحص — AlHosan Checker

تطبيق أندرويد احترافي لفحص اشتراكات **Xtream Codes** و **روابط M3U** بسرعة ودقة، مع واجهة عربية/إنجليزية أنيقة.

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blueviolet.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-blue.svg)](https://developer.android.com/jetpack/compose)
[![Stars](https://img.shields.io/github/stars/se6or/al-hosan-checker?style=flat&color=gold&label=⭐%20نجوم)](https://github.com/se6or/al-hosan-checker/stargazers)
[![Downloads](https://img.shields.io/github/downloads/se6or/al-hosan-checker/total?style=flat&color=brightgreen&label=⬇️%20تنزيل)](https://github.com/se6or/al-hosan-checker/releases)

</div>

---

## 🖼️ لقطات الشاشة

<table>
  <tr>
    <td align="center"><b>شاشة تسجيل الدخول (Xtream)</b></td>
    <td align="center"><b>شاشة تسجيل الدخول (M3U)</b></td>
    <td align="center"><b>شاشة النتيجة</b></td>
    <td align="center"><b>سجل المحفوظات</b></td>
  </tr>
  <tr>
    <td><img src="screenshots/Screenshot_2026-07-12-15-30-25-888_com.alhosan.checker.jpg" width="200" alt="Xtream login" /></td>
    <td><img src="screenshots/Screenshot_2026-07-12-15-33-47-621_com.alhosan.checker.jpg" width="200" alt="M3U login" /></td>
    <td><img src="screenshots/Screenshot_2026-07-12-15-30-50-986_com.alhosan.checker-edit.jpg" width="200" alt="Result screen" /></td>
    <td><img src="screenshots/Screenshot_2026-07-12-15-32-53-450_com.alhosan.checker-edit.jpg" width="200" alt="History screen" /></td>
  </tr>
</table>

---

## ✨ المميزات الرئيسية

- **فحص اشتراكات Xtream** مباشرة (حالة الاشتراك، تاريخ الانتهاء، الأجهزة المتصلة، عدد القنوات/الأفلام/المسلسلات)
- **دعم كامل لروابط M3U** (استخراج تلقائي للبيانات أو حساب عدد القنوات)
- **تصدير النتيجة كصورة PNG** (للمشاركة أو الحفظ)
- **إنشاء رابط M3U** جاهز للنسخ
- **سجل محفوظات** (حفظ + استرجاع + حذف)
- **واجهة عربية كاملة** مع دعم RTL
- **تبديل اللغة** في أي لحظة
- **شاشة بداية فورية** (Zero-delay splash)
- **تشخيص أخطاء دقيق** (DNS، SSL، مهلة، بيانات خاطئة...)

---

## 🛠️ التقنيات المستخدمة

| الطبقة          | التقنية                          | الوصف |
|----------------|----------------------------------|------|
| **UI**         | Jetpack Compose + Material 3    | واجهة حديثة وسريعة |
| **Networking** | OkHttp 4 + Kotlin Coroutines    | الطريقة الرئيسية (مستقرة) |
| **Fallback**   | Rust (اختياري)                  | معالجة ثقيلة عبر JNI |
| **Data**       | kotlinx.serialization           | JSON سريع وآمن |
| **Images**     | Coil                            | تحميل الصور |

---

## 📱 كيفية الاستخدام

1. حمل آخر إصدار من [Releases](https://github.com/se6or/al-hosan-checker/releases)
2. فعّل "تثبيت من مصادر غير معروفة"
3. افتح التطبيق وأدخل بيانات الاشتراك أو رابط M3U
4. اضغط **بدء الفحص**

---

## 📁 هيكل المشروع

```
al-hosan-checker/
├── app/
│   ├── src/main/java/com/alhosan/checker/
│   │   ├── ui/               # الشاشات (Login, Result, History...)
│   │   ├── viewmodel/
│   │   ├── data/repository/  # OkHttp + منطق الفحص
│   │   ├── bridge/           # RustBridge (اختياري)
│   │   └── util/
│   └── src/main/res/
├── rust/                     # (اختياري) الكود الأصلي
├── screenshots/
├── .github/workflows/
│   └── build.yml             # بناء + إصدار تلقائي
└── README.md
```

---

## 📋 المتطلبات

- Android 8.0 (API 26) أو أحدث
- اتصال بالإنترنت

---

## 📜 الرخصة

هذا المشروع مفتوح المصدر للاستخدام الشخصي والتعليمي.

---

## 🤝 المساهمة

المساهمات مرحب بها! يمكنك فتح Issue أو Pull Request.

---

## 👥 المساهمون

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/se6or">
        <img src="screenshots/contrib-se6or.png" width="120" alt="se6or" /><br/>
        <b>se6or</b>
      </a>
    </td>
    <td align="center">
      <a href="https://z.ai">
        <img src="screenshots/contrib-super-z.jpg" width="120" alt="Super Z" /><br/>
        <b>Super Z</b>
      </a>
    </td>
    <td align="center">
      <a href="https://lmarena.ai">
        <img src="screenshots/contrib-lm-arena.jpg" width="120" alt="lm Arena" /><br/>
        <b>lm Arena</b>
      </a>
    </td>
  </tr>
</table>

---

**صُنع بحب ❤️ لمجتمع IPTV العربي**

