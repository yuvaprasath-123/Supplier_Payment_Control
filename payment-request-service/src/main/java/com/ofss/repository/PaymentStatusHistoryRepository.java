package com.ofss.repository;

import com.ofss.entity.PaymentStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentStatusHistoryRepository
        extends JpaRepository<PaymentStatusHistory, Long> {

    List<PaymentStatusHistory>
    findByPaymentIdOrderByChangedAtAsc(
            Long paymentId
    );
}