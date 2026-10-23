package com.ofss.repository;
import com.ofss.entity.ApprovalEscalation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalEscalationRepository extends JpaRepository<ApprovalEscalation,Long> {
    List<ApprovalEscalation> findByPaymentIdOrderByOccurredAtAsc(Long paymentId);
}
