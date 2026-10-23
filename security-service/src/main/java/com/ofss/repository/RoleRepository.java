package com.ofss.repository;

import com.ofss.entity.Role;
import com.ofss.entity.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(RoleCode roleCode);
}