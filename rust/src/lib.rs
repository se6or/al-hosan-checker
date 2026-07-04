//! AlHosan Core - Rust Library
//! Handles heavy processing: Xtream API checking, M3U parsing, channel filtering, batch operations

mod xtream;
mod m3u;
mod jni_bridge;

pub use xtream::XtreamChecker;
pub use m3u::M3uParser;
