package com.ofss.repository;
import com.ofss.entity.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findByResourceTypeAndResourceIdOrderByOccurredAtDesc(String resourceType,String resourceId);
    List<AuditEvent> findByCorrelationIdOrderByOccurredAtDesc(String correlationId);
    @Query("select e from AuditEvent e where e.paymentId=:paymentId or (e.resourceId=:resourceId and e.resourceType in ('PAYMENT','PAYMENT_REQUEST')) order by e.occurredAt asc, e.id asc")
    List<AuditEvent> findPaymentTimeline(@Param("paymentId") Long paymentId,@Param("resourceId") String resourceId);
}
