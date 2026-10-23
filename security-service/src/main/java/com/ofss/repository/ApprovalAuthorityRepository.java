package com.ofss.repository;

import com.ofss.entity.ApprovalAuthority;
import com.ofss.entity.ApprovalLevel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalAuthorityRepository
        extends JpaRepository<
                ApprovalAuthority,
                Long
        > {

    Optional<ApprovalAuthority>
    findByUserId(
            Long userId
    );

    List<ApprovalAuthority>
    findByApprovalLevelAndActiveTrue(
            ApprovalLevel approvalLevel
    );
}