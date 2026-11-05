package com.example.lancerpick.payment.repository;

import com.example.lancerpick.payment.entity.OrderFunds;
import org.springframework.data.jpa.repository.JpaRepository;

/** All mutations first lock the parent order. */
public interface OrderFundsRepository extends JpaRepository<OrderFunds, Long> {}
