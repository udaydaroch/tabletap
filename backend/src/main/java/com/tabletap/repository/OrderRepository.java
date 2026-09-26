package com.tabletap.repository;

import com.tabletap.domain.CustomerOrder;
import com.tabletap.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    java.util.Optional<CustomerOrder> findByClientRequestId(String clientRequestId);

    List<CustomerOrder> findByRestaurantIdAndStatusInOrderByCreatedAtAsc(Long restaurantId, Collection<OrderStatus> statuses);
    List<CustomerOrder> findTop50ByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    @Query("select count(o) from CustomerOrder o where o.restaurant.owner.id = :ownerId and o.createdAt >= :since")
    long countForOwnerSince(Long ownerId, Instant since);

    @Query("select o.restaurant.id, count(o) from CustomerOrder o where o.restaurant.owner.id = :ownerId and o.createdAt >= :since group by o.restaurant.id")
    List<Object[]> countPerRestaurantForOwnerSince(Long ownerId, Instant since);

    long countByRestaurantIdAndStatusIn(Long restaurantId, Collection<OrderStatus> statuses);

    List<CustomerOrder> findByRestaurantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
        Long restaurantId, Instant from, Instant to);

    @Query("select o from CustomerOrder o where o.restaurant.id = :restaurantId and o.diningTable is not null and o.status in :statuses")
    List<CustomerOrder> findWithTableByStatus(Long restaurantId, Collection<OrderStatus> statuses);

    /** Keep order history when a table is removed from the floor plan. */
    @Modifying
    @Query("update CustomerOrder o set o.diningTable = null where o.diningTable.id in :tableIds")
    int detachTables(Collection<Long> tableIds);

    long countByWaiterIdAndCreatedAtAfter(Long waiterId, Instant since);
}
