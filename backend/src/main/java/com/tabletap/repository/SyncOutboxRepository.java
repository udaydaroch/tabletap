package com.tabletap.repository;

import com.tabletap.domain.SyncOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncOutboxRepository extends JpaRepository<SyncOutbox, Long> {
    List<SyncOutbox> findTop200BySentAtIsNullOrderByIdAsc();
    long countBySentAtIsNull();
    Optional<SyncOutbox> findTopBySentAtIsNotNullOrderBySentAtDesc();
    Optional<SyncOutbox> findTopBySentAtIsNullAndLastErrorIsNotNullOrderByIdAsc();
}
