//! M3U Playlist Parser
//! Handles parsing of M3U/M3U8 playlists, filtering channels, and extracting stream info
//! Uses rayon for parallel processing on large playlists

use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct M3uChannel {
    pub name: String,
    pub url: String,
    pub group: String,
    pub logo: String,
    pub tvg_id: String,
    pub tvg_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct M3uParseResult {
    pub channels: Vec<M3uChannel>,
    pub groups: HashMap<String, u32>,
    pub total_count: u32,
    pub live_count: u32,
    pub vod_count: u32,
}

pub struct M3uParser;

impl M3uParser {
    /// Parse M3U content from string
    pub fn parse(content: &str) -> M3uParseResult {
        let mut channels = Vec::new();
        let mut groups: HashMap<String, u32> = HashMap::new();
        let mut current_name = String::new();
        let mut current_group = String::new();
        let mut current_logo = String::new();
        let mut current_tvg_id = String::new();
        let mut current_tvg_name = String::new();

        for line in content.lines() {
            let line = line.trim();

            if line.starts_with("#EXTINF:") {
                current_name = Self::extract_name(line);
                current_group = Self::extract_attribute(line, "group-title");
                current_logo = Self::extract_attribute(line, "tvg-logo");
                current_tvg_id = Self::extract_attribute(line, "tvg-id");
                current_tvg_name = Self::extract_attribute(line, "tvg-name");
            } else if !line.is_empty() && !line.starts_with('#') {
                let channel = M3uChannel {
                    name: current_name.clone(),
                    url: line.to_string(),
                    group: current_group.clone(),
                    logo: current_logo.clone(),
                    tvg_id: current_tvg_id.clone(),
                    tvg_name: current_tvg_name.clone(),
                };

                if !current_group.is_empty() {
                    *groups.entry(current_group.clone()).or_insert(0) += 1;
                }

                channels.push(channel);

                current_name.clear();
                current_group.clear();
                current_logo.clear();
                current_tvg_id.clear();
                current_tvg_name.clear();
            }
        }

        let total_count = channels.len() as u32;
        let live_count = total_count;
        let vod_count = 0;

        M3uParseResult {
            channels,
            groups,
            total_count,
            live_count,
            vod_count,
        }
    }

    /// Filter channels by group name (case-insensitive, parallel)
    pub fn filter_by_group(channels: &[M3uChannel], group: &str) -> Vec<M3uChannel> {
        let group_lower = group.to_lowercase();
        channels
            .par_iter()
            .filter(|ch| ch.group.to_lowercase().contains(&group_lower))
            .cloned()
            .collect()
    }

    /// Filter channels by name (case-insensitive, parallel)
    pub fn filter_by_name(channels: &[M3uChannel], name: &str) -> Vec<M3uChannel> {
        let name_lower = name.to_lowercase();
        channels
            .par_iter()
            .filter(|ch| ch.name.to_lowercase().contains(&name_lower))
            .cloned()
            .collect()
    }

    /// Sort channels alphabetically by name (parallel sort)
    pub fn sort_by_name(channels: &mut [M3uChannel]) {
        channels.par_sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    }

    /// Remove duplicate channels by URL (parallel dedup)
    pub fn dedup_by_url(channels: &[M3uChannel]) -> Vec<M3uChannel> {
        let mut seen = HashSet::new();
        channels
            .iter()
            .filter(|ch| seen.insert(ch.url.clone()))
            .cloned()
            .collect()
    }

    fn extract_attribute(line: &str, attr: &str) -> String {
        let pattern = format!("{}=\"", attr);
        if let Some(start) = line.find(&pattern) {
            let value_start = start + pattern.len();
            if let Some(end) = line[value_start..].find('"') {
                return line[value_start..value_start + end].to_string();
            }
        }
        String::new()
    }

    fn extract_name(line: &str) -> String {
        if let Some(pos) = line.rfind(',') {
            return line[pos + 1..].trim().to_string();
        }
        String::new()
    }
}
