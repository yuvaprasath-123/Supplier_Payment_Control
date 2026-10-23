package com.ofss.repository;
import com.ofss.entity.ApprovalDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision,Long> {
    List<ApprovalDecision> findByPaymentIdOrderByDecidedAtAsc(Long paymentId);
}
