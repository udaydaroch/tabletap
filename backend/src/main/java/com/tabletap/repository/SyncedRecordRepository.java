package com.tabletap.repository;

import com.tabletap.domain.SyncedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Set;

public interface SyncedRecordRepository extends JpaRepository<SyncedRecord, Long> {
    @org.springframework.data.jpa.repository.Query("select r.sourceId from SyncedRecord r where r.site.id = :siteId and r.sourceId in :ids")
    Set<Long> existingSourceIds(Long siteId, Collection<Long> ids);

    void deleteBySiteId(Long siteId);
}
