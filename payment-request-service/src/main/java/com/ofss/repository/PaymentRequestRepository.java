
package com.ofss.repository;

import com.ofss.entity.PaymentRequest;
import com.ofss.entity.PaymentStatus;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRequestRepository
        extends JpaRepository<PaymentRequest, Long> {

    boolean existsByInvoiceId(
            Long invoiceId
    );

    List<PaymentRequest> findByRequesterId(
            Long requesterId
    );

    List<PaymentRequest> findByRequesterIdAndSupplierId(
            Long requesterId,
            Long supplierId
    );

    List<PaymentRequest> findByRequesterIdAndStatus(
            Long requesterId,
            PaymentStatus status
    );

    List<PaymentRequest>
    findByRequesterIdAndSupplierIdAndStatus(
            Long requesterId,
            Long supplierId,
            PaymentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PaymentRequest p
            WHERE p.id = :paymentId
            """)
    Optional<PaymentRequest> findByIdForUpdate(
            @Param("paymentId") Long paymentId
    );
}
