package com.tabletap.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tabletap.domain.SyncOutbox;
import com.tabletap.repository.SyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Relay half of the outbox pattern: every 20 s, if the internet is up, sends unsent rows to the cloud in
 * order and marks them sent. If the cloud can't be reached it simply tries again later — nothing is lost,
 * and the cloud ignores anything it already has, so a resend after a crash is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncWorker {
    private final SyncProperties props;
    private final SyncOutboxRepository outbox;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Scheduled(initialDelay = 15_000, fixedDelay = 20_000)
    public void run() {
        if (!props.enabled()) return;
        List<SyncOutbox> batch = outbox.findTop200BySentAtIsNullOrderByIdAsc();
        if (batch.isEmpty()) return;
        try {
            List<Map<String, Object>> records = new ArrayList<>();
            for (SyncOutbox r : batch) {
                records.add(Map.of("id", r.getId(), "type", r.getEventType(),
                    "payload", json.readTree(r.getPayload()), "createdAt", r.getCreatedAt()));
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.url().replaceAll("/+$", "") + "/api/sync/ingest"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Site-Key", props.siteKey())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("records", records))))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IllegalStateException("cloud answered " + res.statusCode());
            Instant now = Instant.now();
            tx.executeWithoutResult(s -> batch.forEach(r -> {
                SyncOutbox row = outbox.findById(r.getId()).orElseThrow();
                row.setSentAt(now);
                row.setLastError(null);
            }));
            log.info("Cloud sync: sent {} change(s)", batch.size());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Cloud sync failed, will retry: {}", msg);
            tx.executeWithoutResult(s -> {
                SyncOutbox first = outbox.findById(batch.get(0).getId()).orElseThrow();
                first.setAttempts(first.getAttempts() + 1);
                first.setLastError(msg.length() > 250 ? msg.substring(0, 250) : msg);
            });
        }
    }
}
