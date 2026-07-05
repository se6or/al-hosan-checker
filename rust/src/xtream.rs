//! Xtream Codes API Checker
//! Handles authentication, subscription info retrieval, and batch checking

use chrono::NaiveDateTime;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XtreamUserInfo {
    pub username: String,
    pub password: String,
    pub message: String,
    pub auth: i32,
    pub status: String,
    pub exp_date: String,
    pub is_trial: String,
    pub active_cons: String,
    pub created_at: String,
    pub max_connections: String,
    pub allowed_output_formats: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XtreamServerInfo {
    pub url: String,
    pub port: String,
    pub https_port: String,
    pub server_protocol: String,
    pub rtmp_port: String,
    pub timezone: String,
    pub timestamp_now: i64,
    pub time_now: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct XtreamResponse {
    pub user_info: XtreamUserInfo,
    pub server_info: XtreamServerInfo,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubscriptionResult {
    pub host: String,
    pub username: String,
    pub password: String,
    pub status: String,
    pub expiry: String,
    pub created: String,
    pub active_cons: String,
    pub max_cons: String,
    pub is_trial: bool,
    pub live_count: String,
    pub movie_count: String,
    pub series_count: String,
    pub server_url: String,
    pub server_protocol: String,
    pub timezone: String,
    pub error: String,
}

pub struct XtreamChecker {
    client: reqwest::Client,
}

impl XtreamChecker {
    pub fn new() -> Self {
        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(15))
            .connect_timeout(std::time::Duration::from_secs(10))
            .redirect(reqwest::redirect::Policy::limited(5))
            .build()
            .unwrap_or_else(|_| reqwest::Client::new());

        Self { client }
    }

    /// Check a single Xtream subscription
    pub async fn check_subscription(
        &self,
        host: &str,
        username: &str,
        password: &str,
    ) -> Result<SubscriptionResult, String> {
        let host_clean = host.trim_end_matches('/');
        let url = format!(
            "{}/player_api.php?username={}&password={}",
            host_clean, username, password
        );

        let response = self
            .client
            .get(&url)
            .header("User-Agent", "AlHosanChecker/1.0")
            .send()
            .await
            .map_err(|e| format!("Connection error: {}", e))?;

        if !response.status().is_success() {
            return Err(format!("Server returned status: {}", response.status()));
        }

        let body = response
            .text()
            .await
            .map_err(|e| format!("Failed to read response: {}", e))?;

        let data: XtreamResponse = serde_json::from_str(&body)
            .map_err(|e| format!("Failed to parse response: {}", e))?;

        let expiry = Self::format_timestamp(&data.user_info.exp_date, "Unlimited");
        let created = Self::format_timestamp(&data.user_info.created_at, "Unknown");

        Ok(SubscriptionResult {
            host: host_clean.to_string(),
            username: username.to_string(),
            password: password.to_string(),
            status: data.user_info.status.clone(),
            expiry,
            created,
            active_cons: data.user_info.active_cons.clone(),
            max_cons: data.user_info.max_connections.clone(),
            is_trial: data.user_info.is_trial == "1",
            live_count: "?".to_string(),
            movie_count: "?".to_string(),
            series_count: "?".to_string(),
            server_url: data.server_info.url.clone(),
            server_protocol: data.server_info.server_protocol.clone(),
            timezone: data.server_info.timezone.clone(),
            error: String::new(),
        })
    }

    /// Fetch live stream count
    pub async fn get_live_count(
        &self,
        host: &str,
        username: &str,
        password: &str,
    ) -> Result<String, String> {
        self.get_stream_count(host, username, password, "get_live_streams").await
    }

    /// Fetch VOD count
    pub async fn get_movie_count(
        &self,
        host: &str,
        username: &str,
        password: &str,
    ) -> Result<String, String> {
        self.get_stream_count(host, username, password, "get_vod_streams").await
    }

    /// Fetch series count
    pub async fn get_series_count(
        &self,
        host: &str,
        username: &str,
        password: &str,
    ) -> Result<String, String> {
        self.get_stream_count(host, username, password, "get_series").await
    }

    /// Generic stream count fetcher
    async fn get_stream_count(
        &self,
        host: &str,
        username: &str,
        password: &str,
        action: &str,
    ) -> Result<String, String> {
        let url = format!(
            "{}/player_api.php?username={}&password={}&action={}",
            host.trim_end_matches('/'),
            username,
            password,
            action
        );

        let response = self
            .client
            .get(&url)
            .header("User-Agent", "AlHosanChecker/1.0")
            .send()
            .await
            .map_err(|e| format!("Error: {}", e))?;

        let body = response.text().await.map_err(|e| format!("Error: {}", e))?;

        if let Ok(arr) = serde_json::from_str::<Vec<serde_json::Value>>(&body) {
            return Ok(arr.len().to_string());
        }

        Ok("?".to_string())
    }

    /// Batch check multiple subscriptions concurrently
    pub async fn batch_check(
        &self,
        accounts: Vec<(String, String, String)>,
    ) -> Vec<SubscriptionResult> {
        let mut handles = Vec::with_capacity(accounts.len());

        for (host, user, pass) in accounts {
            let client = self.client.clone();
            let host = host.clone();
            let user = user.clone();
            let pass = pass.clone();

            handles.push(tokio::spawn(async move {
                let checker = XtreamChecker { client };
                checker.check_subscription(&host, &user, &pass).await
            }));
        }

        let mut results = Vec::with_capacity(handles.len());
        for (i, handle) in handles.into_iter().enumerate() {
            match handle.await {
                Ok(Ok(sub)) => results.push(sub),
                Ok(Err(e)) => {
                    results.push(SubscriptionResult {
                        status: "Error".to_string(),
                        error: e,
                        ..Default::default()
                    });
                }
                Err(e) => {
                    results.push(SubscriptionResult {
                        status: "Error".to_string(),
                        error: format!("Task failed: {}", e),
                        ..Default::default()
                    });
                }
            }
        }

        results
    }

    /// Format unix timestamp string to human-readable date
    fn format_timestamp(ts: &str, default: &str) -> String {
        if ts == "null" || ts.is_empty() {
            return default.to_string();
        }
        match ts.parse::<i64>() {
            Ok(timestamp) => {
                NaiveDateTime::from_timestamp_opt(timestamp, 0)
                    .map(|dt| dt.format("%Y-%m-%d").to_string())
                    .unwrap_or_else(|| ts.to_string())
            }
            Err(_) => ts.to_string(),
        }
    }
}

impl Default for SubscriptionResult {
    fn default() -> Self {
        Self {
            host: String::new(),
            username: String::new(),
            password: String::new(),
            status: "Unknown".to_string(),
            expiry: "--".to_string(),
            created: "--".to_string(),
            active_cons: "0".to_string(),
            max_cons: "0".to_string(),
            is_trial: false,
            live_count: "?".to_string(),
            movie_count: "?".to_string(),
            series_count: "?".to_string(),
            server_url: String::new(),
            server_protocol: String::new(),
            timezone: String::new(),
            error: String::new(),
        }
    }
}
