use serde_json::{Value, json};
use std::time::{SystemTime, UNIX_EPOCH};

const VOLCENGINE_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-volcengine.png");
const PACKYCODE_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-packycode.png");
const TOKEN_BRIDGE_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-0029.svg");
const APIKEY_FUN_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-apikey-fun.png");
const RAWCHAT_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-rawchat.svg");
const RUNAPI_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-runapi.png");
const BAIKEWEI_AI_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-baikewei-ai.jpg");
const DEEPKEY_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-deepkey.png");
const APIMART_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-apimart.png");
const FENNO_AI_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-fenno-ai.png");
const QINIU_AI_IMAGE: &[u8] = include_bytes!("../../../docs/images/sponsor-qiniu-ai.png");

pub const DEFAULT_AD_LIST_URLS: [&str; 2] = [
    "https://raw.githubusercontent.com/BigPizzaV3/Ad-List/main/ads.json",
    "https://cdn.jsdelivr.net/gh/BigPizzaV3/Ad-List@main/ads.json",
];

pub fn normalize_ad_payload(payload: Value) -> Value {
    let version = payload.get("version").and_then(Value::as_u64).unwrap_or(1);
    let mut ads = payload
        .get("ads")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter(|ad| {
            let ad_type = ad.get("type").and_then(Value::as_str);
            let title = ad.get("title").and_then(Value::as_str);
            let description = ad.get("description").and_then(Value::as_str);
            let url = ad.get("url").and_then(Value::as_str);
            matches!(ad_type, Some("sponsor" | "normal"))
                && title.is_some_and(|value| !value.trim().is_empty())
                && description.is_some_and(|value| !value.trim().is_empty())
                && url.is_some_and(|value| !value.trim().is_empty())
                // Replace the previous JOJO Code promotion with the user's own relay.
                && !is_jojo_code_ad(ad)
        })
        .cloned()
        .collect::<Vec<_>>();
    fill_known_remote_logos(&mut ads);

    // The overview banner and recommendations page both use this topAd.
    // Keep the destination local to this build so a remote ad-list refresh cannot
    // silently restore the old JOJO Code card.
    let mozhe = json!({
        "id": "mozhe-world-relay",
        "type": "sponsor",
        "title": "墨者 AI 中转站",
        "description": "进入墨者 AI 中转站，管理 API Key，并查看可用模型与接入配置。",
        "url": "https://ai.mozhe.world/",
        "highlights": ["中转站入口", "模型与 Key", "自定义接入"]
    });
    let mut normalized = json!({ "version": version, "ads": ads });
    normalized["topAd"] = mozhe.clone();
    normalized["topAds"] = json!([mozhe]);
    normalized
}

fn is_jojo_code_ad(ad: &Value) -> bool {
    let id = ad.get("id").and_then(Value::as_str).unwrap_or_default().to_ascii_lowercase();
    let title = ad.get("title").and_then(Value::as_str).unwrap_or_default().to_ascii_lowercase();
    let url = ad.get("url").and_then(Value::as_str).unwrap_or_default().to_ascii_lowercase();
    id.contains("jojocode")
        || title.trim() == "jojo code"
        || url.contains("jojocode")
}

fn fill_known_remote_logos(ads: &mut [Value]) {
    for ad in ads {
        let Some(object) = ad.as_object_mut() else {
            continue;
        };
        let has_image = object
            .get("image")
            .and_then(Value::as_str)
            .is_some_and(|value| !value.trim().is_empty());
        if has_image {
            continue;
        }
        let Some(id) = object.get("id").and_then(Value::as_str) else {
            continue;
        };
        let Some((mime, image)) = known_remote_logo(id) else {
            continue;
        };
        object.insert("image".to_string(), json!(data_uri(mime, image)));
    }
}

fn known_remote_logo(id: &str) -> Option<(&'static str, &'static [u8])> {
    match id {
        "volcengine-ark-agent-plan" => Some(("image/png", VOLCENGINE_IMAGE)),
        "0029-token-bridge" => Some(("image/png", PACKYCODE_IMAGE)),
        "0055-token-bridge" => Some(("image/svg+xml", TOKEN_BRIDGE_IMAGE)),
        "apikey-fun-ai-relay" => Some(("image/png", APIKEY_FUN_IMAGE)),
        "rawchat-codex-relay" => Some(("image/svg+xml", RAWCHAT_IMAGE)),
        "runapi-openrouter-alternative" => Some(("image/png", RUNAPI_IMAGE)),
        "baikewei-ai" => Some(("image/jpeg", BAIKEWEI_AI_IMAGE)),
        "deepkey-api-key" => Some(("image/png", DEEPKEY_IMAGE)),
        "apimart" => Some(("image/png", APIMART_IMAGE)),
        "fenno-ai" => Some(("image/png", FENNO_AI_IMAGE)),
        "qiniu-ai" => Some(("image/png", QINIU_AI_IMAGE)),
        _ => None,
    }
}

fn data_uri(mime: &str, bytes: &[u8]) -> String {
    format!("data:{mime};base64,{}", base64_encode(bytes))
}

fn base64_encode(bytes: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut encoded = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let first = chunk[0];
        let second = *chunk.get(1).unwrap_or(&0);
        let third = *chunk.get(2).unwrap_or(&0);
        encoded.push(TABLE[(first >> 2) as usize] as char);
        encoded.push(TABLE[(((first & 0b0000_0011) << 4) | (second >> 4)) as usize] as char);
        if chunk.len() > 1 {
            encoded.push(TABLE[(((second & 0b0000_1111) << 2) | (third >> 6)) as usize] as char);
        } else {
            encoded.push('=');
        }
        if chunk.len() > 2 {
            encoded.push(TABLE[(third & 0b0011_1111) as usize] as char);
        } else {
            encoded.push('=');
        }
    }
    encoded
}

pub async fn fetch_ad_list() -> anyhow::Result<Value> {
    fetch_ad_list_from_urls(&DEFAULT_AD_LIST_URLS).await
}

pub fn cache_busted_ad_url(url: &str, version: u128) -> String {
    let separator = if url.contains('?') { '&' } else { '?' };
    format!("{url}{separator}v={version}")
}

pub async fn fetch_ad_list_from_urls<S>(urls: &[S]) -> anyhow::Result<Value>
where
    S: AsRef<str>,
{
    let client = crate::http_client::proxied_client("CodexPlusPlus")?;
    let cache_bust = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or_default();
    let mut last_error = None;
    for url in urls {
        let url = cache_busted_ad_url(url.as_ref(), cache_bust);
        let result = async {
            let response = client.get(url).send().await?.error_for_status()?;
            let payload = response.json::<Value>().await?;
            Ok::<_, anyhow::Error>(normalize_ad_payload(payload))
        }
        .await;
        match result {
            Ok(payload) => return Ok(payload),
            Err(error) => last_error = Some(error),
        }
    }
    Err(last_error.unwrap_or_else(|| anyhow::anyhow!("ad list unavailable")))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sponsored(id: &str) -> Value {
        json!({
            "id": id,
            "type": "sponsor",
            "title": id,
            "description": "描述",
            "url": "https://example.com/",
        })
    }

    #[test]
    fn mozhe_replaces_jojo_top_ad_and_removes_jojo_from_pool() {
        let normalized = normalize_ad_payload(json!({
            "version": 1,
            "ads": [
                {
                    "id": "jojocode-codex-relay",
                    "type": "sponsor",
                    "title": "JOJO Code",
                    "description": "旧推荐",
                    "url": "https://jojocode.example/"
                },
                sponsored("keep-this"),
            ],
            "top_ad": sponsored("jojocode-top"),
        }));
        assert_eq!(normalized["topAd"]["id"], json!("mozhe-world-relay"));
        assert_eq!(normalized["topAd"]["url"], json!("https://ai.mozhe.world/"));
        assert_eq!(normalized["topAds"].as_array().unwrap().len(), 1);
        let pool = normalized["ads"].as_array().unwrap();
        assert!(!pool.iter().any(is_jojo_code_ad));
        assert!(pool.iter().any(|ad| ad["id"] == json!("keep-this")));
    }

    #[test]
    fn mozhe_top_ad_is_stable_when_remote_top_ad_is_missing_or_changes() {
        for payload in [
            json!({ "version": 1, "ads": [] }),
            json!({ "version": 1, "ads": [], "topAd": sponsored("remote-a") }),
            json!({ "version": 1, "ads": [], "top_ad": [sponsored("remote-b"), sponsored("remote-c")] }),
        ] {
            let normalized = normalize_ad_payload(payload);
            assert_eq!(normalized["topAd"]["id"], json!("mozhe-world-relay"));
            assert_eq!(normalized["topAd"]["url"], json!("https://ai.mozhe.world/"));
            assert_eq!(normalized["topAds"].as_array().unwrap().len(), 1);
        }
    }

}
