package com.tabletap.sync;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SyncDtos {
    private SyncDtos() {}

    public record SyncRecord(@NotNull Long id, @NotBlank @Size(max = 40) String type, @NotNull JsonNode payload, Instant createdAt) {}

    public record Batch(@NotNull @Size(max = 500) @Valid List<SyncRecord> records) {}

    public record IngestResult(int received, int stored, int duplicates) {}

    public record Status(boolean enabled, String target, long pending, Instant lastSentAt, String lastError) {}

    public record SiteRequest(@NotBlank @Size(max = 80) String name) {}

    public record SiteView(Long id, String name, java.time.Instant createdAt, Instant lastSyncAt, long recordCount) {}

    /** Returned once, when the site is created. The key is never shown again. */
    public record NewSite(SiteView site, String key) {}
}
