package com.tabletap.repository;

import com.tabletap.domain.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {
    Optional<Site> findByKeyHash(String keyHash);
    List<Site> findAllByOrderByName();
}
