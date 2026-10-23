package com.ofss.repository;
import com.ofss.entity.ApprovalRoute;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRouteRepository extends JpaRepository<ApprovalRoute,Long> {
    Optional<ApprovalRoute> findByPaymentId(Long paymentId);
}
