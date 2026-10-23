package com.ofss.service;

import com.ofss.dto.CreateSupplierRequest;
import com.ofss.dto.SupplierResponse;
import com.ofss.dto.SupplierStatusHistoryResponse;
import com.ofss.dto.UpdateSupplierRequest;
import com.ofss.dto.UpdateSupplierStatusRequest;

import com.ofss.entity.Supplier;
import com.ofss.entity.SupplierStatus;
import com.ofss.entity.SupplierStatusHistory;

import com.ofss.exception.BadRequestException;
import com.ofss.exception.ResourceNotFoundException;

import com.ofss.repository.SupplierRepository;
import com.ofss.repository.SupplierStatusHistoryRepository;
import com.ofss.security.AuthUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository
            supplierRepository;
    
    private final AuthUtil authUtil;

    private final SupplierStatusHistoryRepository
            supplierStatusHistoryRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierStatusHistoryRepository supplierStatusHistoryRepository,
            AuthUtil authUtil
    ) {

        this.supplierRepository =
                supplierRepository;

        this.supplierStatusHistoryRepository =
                supplierStatusHistoryRepository;

        this.authUtil =
                authUtil;
    }

    @Transactional
    public SupplierResponse createSupplier(
            CreateSupplierRequest request
    ) {

        Supplier supplier =
                new Supplier();

        supplier.setSupplierName(
                request.supplierName()
        );

        supplier.setStatus(
                request.status()
        );

        supplier =
                supplierRepository.save(
                        supplier
                );

        SupplierStatusHistory history =
                new SupplierStatusHistory();

        history.setSupplierId(
                supplier.getId()
        );

        history.setPreviousStatus(
                null
        );

        history.setNewStatus(
                supplier.getStatus()
        );

        history.setChangedBy(
                authUtil.getCurrentUserId()
        );

        supplierStatusHistoryRepository
                .save(history);

        return mapToResponse(
                supplier
        );
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse>
    getAllSuppliers(
            SupplierStatus status
    ) {

        List<Supplier> suppliers;

        if (status == null) {

            suppliers =
                    supplierRepository
                            .findAll();

        } else {

            suppliers =
                    supplierRepository
                            .findByStatus(
                                    status
                            );
        }

        return suppliers
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(
            Long supplierId
    ) {

        Supplier supplier =
                getSupplierEntity(
                        supplierId
                );

        return mapToResponse(
                supplier
        );
    }

    public Supplier getSupplierEntity(
            Long supplierId
    ) {

        return supplierRepository
                .findById(
                        supplierId
                )
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Supplier",
                                        String.valueOf(
                                                supplierId
                                        )
                                )
                );
    }

    @Transactional
    public SupplierResponse updateSupplier(
            Long supplierId,
            UpdateSupplierRequest request
    ) {

        Supplier supplier =
                getSupplierEntity(
                        supplierId
                );

        supplier.setSupplierName(
                request.supplierName()
        );

        supplier =
                supplierRepository.save(
                        supplier
                );

        return mapToResponse(
                supplier
        );
    }

    @Transactional
    public SupplierResponse updateSupplierStatus(
            Long supplierId,
            UpdateSupplierStatusRequest request
    ) {

        Supplier supplier =
                getSupplierEntity(
                        supplierId
                );

        SupplierStatus previousStatus =
                supplier.getStatus();

        SupplierStatus newStatus =
                request.status();

        if (previousStatus == newStatus) {

            throw new BadRequestException(
                    "Supplier is already in status "
                            + newStatus
            );
        }

        supplier.setStatus(
                newStatus
        );

        supplier =
                supplierRepository.save(
                        supplier
                );

        SupplierStatusHistory history =
                new SupplierStatusHistory();

        history.setSupplierId(
                supplierId
        );

        history.setPreviousStatus(
                previousStatus
        );

        history.setNewStatus(
                newStatus
        );

        history.setChangedBy(
                request.changedBy()
        );

        supplierStatusHistoryRepository
                .save(history);

        return mapToResponse(
                supplier
        );
    }

    @Transactional(readOnly = true)
    public List<SupplierStatusHistoryResponse>
    getStatusHistory(
            Long supplierId
    ) {

        getSupplierEntity(
                supplierId
        );

        return supplierStatusHistoryRepository
                .findBySupplierIdOrderByChangedAtDesc(
                        supplierId
                )
                .stream()
                .map(
                        history ->
                                new SupplierStatusHistoryResponse(
                                        history.getId(),
                                        history.getSupplierId(),
                                        history.getPreviousStatus(),
                                        history.getNewStatus(),
                                        history.getChangedAt(),
                                        history.getChangedBy()
                                )
                )
                .toList();
    }

    private SupplierResponse mapToResponse(
            Supplier supplier
    ) {

        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierName(),
                supplier.getStatus(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}