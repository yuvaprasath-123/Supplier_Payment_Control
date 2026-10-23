package com.ofss.repository;
import com.ofss.entity.*;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask,Long>, JpaSpecificationExecutor<ApprovalTask> {
    Optional<ApprovalTask> findByPaymentId(Long paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from ApprovalTask t where t.id=:id")
    Optional<ApprovalTask> findForUpdate(@Param("id") Long id);
}
