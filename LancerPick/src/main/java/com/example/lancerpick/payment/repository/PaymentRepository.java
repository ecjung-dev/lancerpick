package com.example.lancerpick.payment.repository;
import com.example.lancerpick.payment.entity.Payment;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
/** All writes participate in the caller's order transaction. */
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    Optional<Payment> findByOrderId(long orderId);
    default long insert(long orderId,long amount,LocalDateTime at){return saveAndFlush(Payment.create(orderId,amount,at)).id();}
    default int markRefunded(long id,LocalDateTime at){var p=findById(id);if(p.isEmpty() || !"PAID".equals(p.get().status()))return 0;p.get().refund(at);flush();return 1;}
}
