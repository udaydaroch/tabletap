package com.tabletap.repository;

import com.tabletap.domain.BillingRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingRunRepository extends JpaRepository<BillingRun, Long> {
    Optional<BillingRun> findByOwnerIdAndBillingMonth(Long ownerId, String billingMonth);
    List<BillingRun> findByBillingMonthOrderByIdAsc(String billingMonth);
}
