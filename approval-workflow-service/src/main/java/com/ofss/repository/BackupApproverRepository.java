package com.ofss.repository;
import com.ofss.entity.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BackupApproverRepository extends JpaRepository<BackupApproverMapping,Long> {
    Optional<BackupApproverMapping> findByApproverIdAndApprovalLevel(Long approverId,ApprovalLevel approvalLevel);
}
