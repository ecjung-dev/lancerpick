package com.example.lancerpick.settlement.repository;

import com.example.lancerpick.settlement.entity.Settlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByOrderId(long orderId);
}
