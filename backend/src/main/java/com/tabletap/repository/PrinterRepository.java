package com.tabletap.repository;

import com.tabletap.domain.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    List<Printer> findByRestaurantIdOrderByName(Long restaurantId);
    List<Printer> findByRestaurantIdAndActiveTrue(Long restaurantId);
}
