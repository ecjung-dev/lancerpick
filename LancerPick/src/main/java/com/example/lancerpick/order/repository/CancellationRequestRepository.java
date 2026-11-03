package com.example.lancerpick.order.repository;
import com.example.lancerpick.order.entity.CancellationRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest,Long>{List<CancellationRequest> findByOrderIdAndStatus(long orderId,String status);}
