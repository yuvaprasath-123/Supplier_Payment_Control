package com.ofss.repository;

import com.ofss.entity.Supplier;
import com.ofss.entity.SupplierStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository
        extends JpaRepository<Supplier, Long> {

    List<Supplier> findByStatus(
            SupplierStatus status
    );
}