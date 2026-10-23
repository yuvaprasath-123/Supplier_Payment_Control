package com.ofss.repository;

import com.ofss.entity.SupplierStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierStatusHistoryRepository
        extends JpaRepository<SupplierStatusHistory, Long> {

    List<SupplierStatusHistory>
    findBySupplierIdOrderByChangedAtDesc(
            Long supplierId
    );
}