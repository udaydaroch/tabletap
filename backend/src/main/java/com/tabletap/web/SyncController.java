package com.tabletap.web;

import com.tabletap.security.CurrentUser;
import com.tabletap.sync.SyncDtos.*;
import com.tabletap.sync.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService sync;
    private final CurrentUser current;

    /** Called by restaurant computers. Authenticated by X-Site-Key (not a user login). */
    @PostMapping("/sync/ingest")
    public IngestResult ingest(@RequestHeader(value = "X-Site-Key", required = false) String key,
                               @Valid @RequestBody Batch batch, HttpServletRequest http) {
        return sync.ingest(key, http.getRemoteAddr(), batch);
    }

    @GetMapping("/admin/sync/status")
    public Status status() { return sync.status(); }

    @GetMapping("/admin/sites")
    public List<SiteView> sites() { return sync.listSites(); }

    @PostMapping("/admin/sites")
    public NewSite createSite(@Valid @RequestBody SiteRequest req) {
        current.get();
        return sync.createSite(req);
    }

    @DeleteMapping("/admin/sites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSite(@PathVariable Long id) { sync.deleteSite(id); }
}
