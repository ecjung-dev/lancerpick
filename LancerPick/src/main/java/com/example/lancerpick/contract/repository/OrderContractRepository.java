package com.example.lancerpick.contract.repository;

import com.example.lancerpick.contract.entity.OrderContract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderContractRepository extends JpaRepository<OrderContract, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select c.orderId from OrderContract c where c.id=:id")
  Optional<Long> findOrderIdById(@org.springframework.data.repository.query.Param("id") long id);

  // All mutation callers acquire the parent order lock first.
  Optional<OrderContract> findByOrderIdAndActiveSlot(long orderId, Integer activeSlot);

  Optional<OrderContract> findByOrderIdAndVersion(long orderId, int version);

  boolean existsByFileStorageKey(String fileStorageKey);
}
