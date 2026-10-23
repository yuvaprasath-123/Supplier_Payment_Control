package com.ofss.repository;
import com.ofss.entity.ApprovalWorkflow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow,Long>{Optional<ApprovalWorkflow> findByPaymentId(Long paymentId);}
