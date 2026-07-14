//! JNI Bridge - Connects Rust core to Kotlin/JVM
//! This module exposes Rust functions to the Android app via JNI

use jni::objects::{JClass, JString, JObject, JValue};
use jni::sys::{jstring, jobject, jint};
use jni::JNIEnv;

use crate::xtream::XtreamChecker;
use crate::m3u::M3uParser;

// ─── Xtream Check (Single) ───────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeCheckXtream(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    username: JString,
    password: JString,
) -> jstring {
    let host: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_host").unwrap().into_raw(),
    };
    let username: String = match env.get_string(&username) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_username").unwrap().into_raw(),
    };
    let password: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_password").unwrap().into_raw(),
    };

    let checker = XtreamChecker::new();

    // Use tokio runtime for async check
    let rt = tokio::runtime::Runtime::new().unwrap();
    let result = rt.block_on(async {
        checker.check_subscription(&host, &username, &password).await
    });

    match result {
        Ok(sub) => {
            let json = serde_json::to_string(&sub).unwrap_or_else(|_| "error:serialize".to_string());
            env.new_string(&json).unwrap().into_raw()
        }
        Err(e) => {
            let err_json = serde_json::json!({
                "error": e,
                "status": "Error"
            }).to_string();
            env.new_string(&err_json).unwrap().into_raw()
        }
    }
}

// ─── Xtream Check with Counts ────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeCheckXtreamFull(
    mut env: JNIEnv,
    _class: JClass,
    host: JString,
    username: JString,
    password: JString,
) -> jstring {
    let host: String = match env.get_string(&host) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_host").unwrap().into_raw(),
    };
    let username: String = match env.get_string(&username) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_username").unwrap().into_raw(),
    };
    let password: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_password").unwrap().into_raw(),
    };

    let checker = XtreamChecker::new();
    let rt = tokio::runtime::Runtime::new().unwrap();

    let result = rt.block_on(async {
        // First check auth
        let sub = checker.check_subscription(&host, &username, &password).await?;

        // Then fetch counts if auth succeeded
        let live_count = checker.get_live_count(&host, &username, &password).await.unwrap_or("?".to_string());
        let movie_count = checker.get_movie_count(&host, &username, &password).await.unwrap_or("?".to_string());
        let series_count = checker.get_series_count(&host, &username, &password).await.unwrap_or("?".to_string());

        let mut sub = sub;
        sub.live_count = live_count;
        sub.movie_count = movie_count;
        sub.series_count = series_count;

        Ok::<crate::xtream::SubscriptionResult, String>(sub)
    });

    match result {
        Ok(sub) => {
            let json = serde_json::to_string(&sub).unwrap_or_else(|_| "error:serialize".to_string());
            env.new_string(&json).unwrap().into_raw()
        }
        Err(e) => {
            let err_json = serde_json::json!({
                "error": e,
                "status": "Error"
            }).to_string();
            env.new_string(&err_json).unwrap().into_raw()
        }
    }
}

// ─── Batch Check ─────────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeBatchCheck(
    mut env: JNIEnv,
    _class: JClass,
    json_accounts: JString,
) -> jstring {
    let json_str: String = match env.get_string(&json_accounts) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_input").unwrap().into_raw(),
    };

    let accounts: Vec<(String, String, String)> = match serde_json::from_str(&json_str) {
        Ok(a) => a,
        Err(e) => {
            let err = format!("error:parse_accounts:{}", e);
            return env.new_string(&err).unwrap().into_raw();
        }
    };

    let checker = XtreamChecker::new();
    let rt = tokio::runtime::Runtime::new().unwrap();

    let results = rt.block_on(async {
        checker.batch_check(accounts).await
    });

    let json = serde_json::to_string(&results).unwrap_or_else(|_| "error:serialize".to_string());
    env.new_string(&json).unwrap().into_raw()
}

// ─── M3U Parse ──────────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeParseM3u(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    let content: String = match env.get_string(&content) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_content").unwrap().into_raw(),
    };

    let result = M3uParser::parse(&content);
    let json = serde_json::to_string(&result).unwrap_or_else(|_| "error:serialize".to_string());
    env.new_string(&json).unwrap().into_raw()
}

// ─── M3U Filter ─────────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeFilterChannels(
    mut env: JNIEnv,
    _class: JClass,
    json_channels: JString,
    filter_type: JString,
    filter_value: JString,
) -> jstring {
    let json_channels: String = match env.get_string(&json_channels) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_channels").unwrap().into_raw(),
    };
    let filter_type: String = match env.get_string(&filter_type) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_filter_type").unwrap().into_raw(),
    };
    let filter_value: String = match env.get_string(&filter_value) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("error:invalid_filter_value").unwrap().into_raw(),
    };

    let channels: Vec<crate::m3u::M3uChannel> = match serde_json::from_str(&json_channels) {
        Ok(c) => c,
        Err(e) => {
            let err = format!("error:parse_channels:{}", e);
            return env.new_string(&err).unwrap().into_raw();
        }
    };

    let filtered = match filter_type.as_str() {
        "group" => M3uParser::filter_by_group(&channels, &filter_value),
        "name" => M3uParser::filter_by_name(&channels, &filter_value),
        _ => channels,
    };

    let json = serde_json::to_string(&filtered).unwrap_or_else(|_| "error:serialize".to_string());
    env.new_string(&json).unwrap().into_raw()
}

// ─── Version Info ────────────────────────────────────────────────────────

#[no_mangle]
pub extern "system" fn Java_com_alhosan_checker_bridge_RustBridge_nativeGetVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = env.new_string("1.0.0-rust").unwrap();
    version.into_raw()
}
