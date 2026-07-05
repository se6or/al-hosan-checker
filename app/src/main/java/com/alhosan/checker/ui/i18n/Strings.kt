package com.alhosan.checker.ui.i18n

import com.alhosan.checker.data.model.AppLang

/**
 * Bilingual string resources matching the HTML reference exactly.
 * Each key maps to both Arabic and English translations.
 */

// Language toggle button
val AppLang.lBtn: String get() = when (this) {
    AppLang.AR -> "English"
    AppLang.EN -> "العربية"
}

// Splash screen
val AppLang.splash: String get() = when (this) {
    AppLang.AR -> "محرك الحصان الفاحص"
    AppLang.EN -> "HORSE CHECKER ENGINE"
}

// Check button
val AppLang.check: String get() = when (this) {
    AppLang.AR -> "بدء فحص الحصان"
    AppLang.EN -> "START CHECK"
}

// History button
val AppLang.hist: String get() = when (this) {
    AppLang.AR -> "سجل المحفوظات"
    AppLang.EN -> "HISTORY LOGS"
}

// Result title
val AppLang.resTitle: String get() = when (this) {
    AppLang.AR -> "تفاصيل الاشتراك"
    AppLang.EN -> "Subscription Details"
}

// Placeholders
val AppLang.hPl: String get() = when (this) {
    AppLang.AR -> "السيرفر (Host)"
    AppLang.EN -> "Server (Host)"
}
val AppLang.uPl: String get() = when (this) {
    AppLang.AR -> "اسم المستخدم"
    AppLang.EN -> "Username"
}
val AppLang.pPl: String get() = when (this) {
    AppLang.AR -> "كلمة المرور"
    AppLang.EN -> "Password"
}
val AppLang.mPl: String get() = when (this) {
    AppLang.AR -> "رابط M3U الكامل"
    AppLang.EN -> "Full M3U Link"
}

// Labels
val AppLang.lHost: String get() = when (this) {
    AppLang.AR -> "السيرفر"
    AppLang.EN -> "Server Address"
}
val AppLang.lUser: String get() = when (this) {
    AppLang.AR -> "اسم المستخدم"
    AppLang.EN -> "User ID"
}
val AppLang.lPass: String get() = when (this) {
    AppLang.AR -> "كلمة المرور"
    AppLang.EN -> "Password"
}
val AppLang.lStatus: String get() = when (this) {
    AppLang.AR -> "الحالة"
    AppLang.EN -> "Status"
}
val AppLang.lCreated: String get() = when (this) {
    AppLang.AR -> "تم الإنشـاء"
    AppLang.EN -> "Created Date"
}
val AppLang.lExpiry: String get() = when (this) {
    AppLang.AR -> "تاريخ الانتهاء"
    AppLang.EN -> "Expiry Date"
}
val AppLang.lDevices: String get() = when (this) {
    AppLang.AR -> "الأجهزة المتصلة"
    AppLang.EN -> "Active Connections"
}
val AppLang.lMaxCons: String get() = when (this) {
    AppLang.AR -> "الحد الأقصى للأجهزة"
    AppLang.EN -> "Max Connections"
}
val AppLang.lTrial: String get() = when (this) {
    AppLang.AR -> "تجريبي"
    AppLang.EN -> "Trial Account"
}

// Action buttons
val AppLang.btnS: String get() = when (this) {
    AppLang.AR -> "حفظ"
    AppLang.EN -> "Save"
}
val AppLang.btnE: String get() = when (this) {
    AppLang.AR -> "صورة"
    AppLang.EN -> "Image"
}
val AppLang.btnM3U: String get() = when (this) {
    AppLang.AR -> "M3U"
    AppLang.EN -> "M3U"
}

// History
val AppLang.hTitle: String get() = when (this) {
    AppLang.AR -> "سجل المحفوظات"
    AppLang.EN -> "Saved Logs"
}
val AppLang.hSub: String get() = when (this) {
    AppLang.AR -> "القائمة المحفوظة"
    AppLang.EN -> "Saved List"
}
val AppLang.clearAll: String get() = when (this) {
    AppLang.AR -> "مسح الكل"
    AppLang.EN -> "Clear All"
}

// Status values
val AppLang.on: String get() = when (this) {
    AppLang.AR -> "نشط"
    AppLang.EN -> "Active"
}
val AppLang.off: String get() = when (this) {
    AppLang.AR -> "منتهي"
    AppLang.EN -> "Expired"
}
val AppLang.yes: String get() = when (this) {
    AppLang.AR -> "نعم"
    AppLang.EN -> "Yes"
}
val AppLang.no: String get() = when (this) {
    AppLang.AR -> "لا"
    AppLang.EN -> "No"
}

// Toasts
val AppLang.tP: String get() = when (this) {
    AppLang.AR -> "تم اللصق بنجاح"
    AppLang.EN -> "Pasted Successfully"
}
val AppLang.tC: String get() = when (this) {
    AppLang.AR -> "الحصان يفحص الآن..."
    AppLang.EN -> "Horse is checking..."
}
val AppLang.tD: String get() = when (this) {
    AppLang.AR -> "تم الفحص بنجاح"
    AppLang.EN -> "Check complete"
}
val AppLang.tS: String get() = when (this) {
    AppLang.AR -> "تم حفظ الاشتراك"
    AppLang.EN -> "Subscription Saved"
}
val AppLang.tCopied: String get() = when (this) {
    AppLang.AR -> "تم النسخ بنجاح"
    AppLang.EN -> "Copied Successfully"
}
val AppLang.tCopiedM3U: String get() = when (this) {
    AppLang.AR -> "تم نسخ رابط M3U"
    AppLang.EN -> "M3U Link copied"
}
val AppLang.tClear: String get() = when (this) {
    AppLang.AR -> "تم مسح السجل"
    AppLang.EN -> "History cleared"
}
val AppLang.tDelLog: String get() = when (this) {
    AppLang.AR -> "تم حذف الاشتراك"
    AppLang.EN -> "Subscription deleted"
}
val AppLang.tNoClip: String get() = when (this) {
    AppLang.AR -> "تأكد من صلاحيات اللصق، أو أدخل يدوياً"
    AppLang.EN -> "Clipboard restricted, enter manually"
}
val AppLang.tVal: String get() = when (this) {
    AppLang.AR -> "يرجى ملء جميع الحقول المطلوبة"
    AppLang.EN -> "Please fill all required fields"
}
val AppLang.tValM3u: String get() = when (this) {
    AppLang.AR -> "يرجى إدخال رابط M3U صحيح"
    AppLang.EN -> "Please enter a valid M3U link"
}
val AppLang.tAlreadySaved: String get() = when (this) {
    AppLang.AR -> "الاشتراك محفوظ مسبقاً"
    AppLang.EN -> "Subscription already saved"
}

// Error messages
val AppLang.tErrTimeout: String get() = when (this) {
    AppLang.AR -> "انتهى وقت الاتصال، السيرفر بطيء أو مغلق"
    AppLang.EN -> "Connection timed out, server is slow or down"
}
val AppLang.tErrAuth: String get() = when (this) {
    AppLang.AR -> "بيانات الدخول خاطئة أو الاشتراك محظور"
    AppLang.EN -> "Wrong credentials or subscription blocked"
}
val AppLang.tErrNotXtream: String get() = when (this) {
    AppLang.AR -> "الاستجابة غير صحيحة، تأكد من رابط السيرفر"
    AppLang.EN -> "Invalid response, check server link"
}
val AppLang.tErrNetwork: String get() = when (this) {
    AppLang.AR -> "لا يوجد اتصال بالإنترنت أو السيرفر مغلق"
    AppLang.EN -> "No internet connection or server is down"
}

// Progress labels
val AppLang.prog1: String get() = when (this) {
    AppLang.AR -> "جارِ الاتصال بالسيرفر..."
    AppLang.EN -> "Connecting to server..."
}
val AppLang.prog2: String get() = when (this) {
    AppLang.AR -> "التحقق من حالة الاشتراك..."
    AppLang.EN -> "Verifying subscription..."
}
val AppLang.prog3: String get() = when (this) {
    AppLang.AR -> "جارِ العد..."
    AppLang.EN -> "Counting media..."
}
val AppLang.prog4: String get() = when (this) {
    AppLang.AR -> "جارِ الانتهاء..."
    AppLang.EN -> "Finalizing..."
}
val AppLang.countingText: String get() = when (this) {
    AppLang.AR -> "جارِ حساب المحتوى"
    AppLang.EN -> "Calculating content"
}

// History
val AppLang.noHistory: String get() = when (this) {
    AppLang.AR -> "لا توجد اشتراكات محفوظة"
    AppLang.EN -> "No saved subscriptions"
}

// Modal
val AppLang.modalCancel: String get() = when (this) {
    AppLang.AR -> "إلغاء"
    AppLang.EN -> "Cancel"
}
val AppLang.modalConfirm: String get() = when (this) {
    AppLang.AR -> "موافق"
    AppLang.EN -> "Confirm"
}
val AppLang.delLogMsg: String get() = when (this) {
    AppLang.AR -> "هل تريد بالتأكيد حذف هذا الاشتراك؟"
    AppLang.EN -> "Are you sure you want to delete this subscription?"
}
val AppLang.clearAllMsg: String get() = when (this) {
    AppLang.AR -> "هل أنت متأكد من مسح كل السجل؟"
    AppLang.EN -> "Are you sure you want to clear all history?"
}

// Content section labels
val AppLang.lChannels: String get() = when (this) {
    AppLang.AR -> "القنوات"
    AppLang.EN -> "Channels"
}
val AppLang.lMovies: String get() = when (this) {
    AppLang.AR -> "الأفلام"
    AppLang.EN -> "Movies"
}
val AppLang.lSeries: String get() = when (this) {
    AppLang.AR -> "المسلسلات"
    AppLang.EN -> "Series"
}
