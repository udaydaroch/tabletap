package com.tabletap.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloud sync settings for a restaurant computer. Leave url empty to turn sync off.
 * CLOUD_SYNC_URL=https://your-cloud-tabletap  CLOUD_SYNC_KEY=<key shown when the admin added this site>
 */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(String url, String siteKey) {
    public boolean enabled() {
        return url != null && !url.isBlank() && siteKey != null && !siteKey.isBlank();
    }
}
