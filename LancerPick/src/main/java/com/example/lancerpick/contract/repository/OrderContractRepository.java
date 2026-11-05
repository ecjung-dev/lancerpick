package com.example.lancerpick.contract.repository;

import com.example.lancerpick.contract.entity.OrderContract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderContractRepository extends JpaRepository<OrderContract, Long> {
  // All mutation callers acquire the parent order lock first.
  Optional<OrderContract> findByOrderIdAndActiveSlot(long orderId, Integer activeSlot);

  Optional<OrderContract> findByOrderIdAndVersion(long orderId, int version);

  boolean existsByFileStorageKey(String fileStorageKey);
}
