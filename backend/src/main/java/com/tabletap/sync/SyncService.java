package com.tabletap.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tabletap.domain.Site;
import com.tabletap.domain.SyncOutbox;
import com.tabletap.domain.SyncedRecord;
import com.tabletap.repository.SiteRepository;
import com.tabletap.repository.SyncOutboxRepository;
import com.tabletap.repository.SyncedRecordRepository;
import com.tabletap.security.LoginThrottle;
import com.tabletap.service.ApiException;
import com.tabletap.sync.SyncDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Both ends of cloud sync: status on the restaurant computer; sites + ingest in the cloud. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SyncService {
    private final SyncProperties props;
    private final SyncOutboxRepository outbox;
    private final SiteRepository sites;
    private final SyncedRecordRepository records;
    private final LoginThrottle throttle;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    // ---------------------------------------------------------------- restaurant computer side

    @Transactional(readOnly = true)
    public Status status() {
        return new Status(props.enabled(), props.enabled() ? props.url() : null, outbox.countBySentAtIsNull(),
            outbox.findTopBySentAtIsNotNullOrderBySentAtDesc().map(SyncOutbox::getSentAt).orElse(null),
            outbox.findTopBySentAtIsNullAndLastErrorIsNotNullOrderByIdAsc().map(SyncOutbox::getLastError).orElse(null));
    }

    // ---------------------------------------------------------------- cloud side

    public NewSite createSite(SiteRequest req) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String key = "tts_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Site s = new Site();
        s.setName(req.name().trim());
        s.setKeyHash(sha256(key));
        sites.save(s);
        log.info("AUDIT sync site {} '{}' created", s.getId(), s.getName());
        return new NewSite(view(s), key);
    }

    @Transactional(readOnly = true)
    public List<SiteView> listSites() {
        return sites.findAllByOrderByName().stream().map(SyncService::view).toList();
    }

    public void deleteSite(Long id) {
        Site s = sites.findById(id).orElseThrow(() -> ApiException.notFound("Site"));
        records.deleteBySiteId(id);
        sites.delete(s);
        log.info("AUDIT sync site {} deleted", id);
    }

    /** Receives a batch from a restaurant computer. Idempotent: records it already has are skipped. */
    public IngestResult ingest(String siteKey, String ip, Batch batch) {
        throttle.checkLogin("sync-site", ip);
        Site site = siteKey == null ? null : sites.findByKeyHash(sha256(siteKey)).orElse(null);
        if (site == null) {
            throttle.loginFailed("sync-site", ip);
            throw ApiException.unauthorized("Unknown site key");
        }
        Set<Long> have = records.existingSourceIds(site.getId(), batch.records().stream().map(SyncRecord::id).toList());
        int stored = 0;
        for (SyncRecord r : batch.records()) {
            if (have.contains(r.id())) continue;
            SyncedRecord rec = new SyncedRecord();
            rec.setSite(site);
            rec.setSourceId(r.id());
            rec.setEventType(r.type());
            try {
                rec.setPayload(json.writeValueAsString(r.payload()));
            } catch (JsonProcessingException e) {
                throw ApiException.badRequest("Bad payload");
            }
            rec.setOccurredAt(r.createdAt());
            records.save(rec);
            stored++;
        }
        site.setLastSyncAt(Instant.now());
        site.setRecordCount(site.getRecordCount() + stored);
        return new IngestResult(batch.records().size(), stored, batch.records().size() - stored);
    }

    private static SiteView view(Site s) {
        return new SiteView(s.getId(), s.getName(), s.getCreatedAt(), s.getLastSyncAt(), s.getRecordCount());
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
