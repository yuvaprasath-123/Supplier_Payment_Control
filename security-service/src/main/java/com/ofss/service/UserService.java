package com.ofss.service;

import com.ofss.dto.ApprovalAuthorityRequest;
import com.ofss.dto.ApprovalAuthorityResponse;
import com.ofss.dto.AssignRolesRequest;
import com.ofss.dto.CreateRequesterRequest;
import com.ofss.dto.CreateUserRequest;
import com.ofss.dto.UserResponse;

import com.ofss.entity.ApprovalAuthority;
import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.Role;
import com.ofss.entity.RoleCode;
import com.ofss.entity.User;
import com.ofss.entity.UserRole;
import com.ofss.entity.UserStatus;

import com.ofss.exception.BadRequestException;
import com.ofss.exception.ResourceNotFoundException;

import com.ofss.repository.ApprovalAuthorityRepository;
import com.ofss.repository.RoleRepository;
import com.ofss.repository.UserRepository;
import com.ofss.repository.UserRoleRepository;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository
            userRepository;

    private final RoleRepository
            roleRepository;

    private final UserRoleRepository
            userRoleRepository;

    private final ApprovalAuthorityRepository
            approvalAuthorityRepository;

    private final PasswordEncoder
            passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            ApprovalAuthorityRepository approvalAuthorityRepository,
            PasswordEncoder passwordEncoder
    ) {

        this.userRepository =
                userRepository;

        this.roleRepository =
                roleRepository;

        this.userRoleRepository =
                userRoleRepository;

        this.approvalAuthorityRepository =
                approvalAuthorityRepository;

        this.passwordEncoder =
                passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(
            CreateUserRequest request
    ) {

        if (
                userRepository
                        .existsByUsername(
                                request.username()
                        )
        ) {

            throw new BadRequestException(
                    "Username already exists"
            );
        }

        if (
                userRepository
                        .existsByEmail(
                                request.email()
                        )
        ) {

            throw new BadRequestException(
                    "Email already exists"
            );
        }

        User user =
                new User();

        user.setUsername(
                request.username()
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()
                )
        );

        user.setFullName(
                request.fullName()
        );

        user.setEmail(
                request.email()
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        user =
                userRepository.save(
                        user
                );

        return mapToResponse(
                user
        );
    }

    @Transactional
    public UserResponse createRequester(
            CreateRequesterRequest request
    ) {

        if (
                userRepository
                        .existsByUsername(
                                request.username()
                        )
        ) {

            throw new BadRequestException(
                    "Username already exists"
            );
        }

        if (
                userRepository
                        .existsByEmail(
                                request.email()
                        )
        ) {

            throw new BadRequestException(
                    "Email already exists"
            );
        }

        User user =
                new User();

        user.setUsername(
                request.username()
        );

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()
                )
        );

        user.setFullName(
                request.fullName()
        );

        user.setEmail(
                request.email()
        );

        user.setStatus(
                UserStatus.ACTIVE
        );

        user =
                userRepository.save(
                        user
                );

        Role requesterRole =
                roleRepository
                        .findByRoleCode(
                                RoleCode.PAYMENT_REQUESTER
                        )
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Role",
                                                RoleCode.PAYMENT_REQUESTER
                                                        .name()
                                        )
                        );

        UserRole userRole =
                new UserRole();

        userRole.setUserId(
                user.getId()
        );

        userRole.setRoleId(
                requesterRole.getId()
        );

        userRoleRepository.save(
                userRole
        );

        return mapToResponse(
                user
        );
    }

    public User getUserEntity(
            Long userId
    ) {

        return userRepository
                .findById(
                        userId
                )
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "User",
                                        String.valueOf(
                                                userId
                                        )
                                )
                );
    }

    @Transactional(
            readOnly = true
    )
    public UserResponse getUser(
            Long userId
    ) {

        return mapToResponse(
                getUserEntity(
                        userId
                )
        );
    }

    @Transactional(
            readOnly = true
    )
    public List<UserResponse> getUsers() {

        return userRepository
                .findAll()
                .stream()
                .map(
                        this::mapToResponse
                )
                .toList();
    }

    @Transactional
    public UserResponse assignRoles(
            Long userId,
            AssignRolesRequest request
    ) {

        User user =
                getUserEntity(
                        userId
                );

        userRoleRepository
                .deleteByUserId(
                        userId
                );

        for (
                RoleCode roleCode :
                request.roles()
        ) {

            Role role =
                    roleRepository
                            .findByRoleCode(
                                    roleCode
                            )
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Role",
                                                    roleCode.name()
                                            )
                            );

            UserRole userRole =
                    new UserRole();

            userRole.setUserId(
                    userId
            );

            userRole.setRoleId(
                    role.getId()
            );

            userRoleRepository
                    .save(
                            userRole
                    );
        }

        return mapToResponse(
                user
        );
    }

    @Transactional(
            readOnly = true
    )
    public List<String> getRoles(
            Long userId
    ) {

        getUserEntity(
                userId
        );

        return loadRoles(
                userId
        );
    }

    @Transactional
    public ApprovalAuthorityResponse
    assignApprovalAuthority(
            Long userId,
            ApprovalAuthorityRequest request
    ) {

        User user =
                getUserEntity(
                        userId
                );

        List<String> roles =
                loadRoles(
                        userId
                );

        if (
                request.approvalLevel()
                        == ApprovalLevel.L1
                ||
                request.approvalLevel()
                        == ApprovalLevel.L2
        ) {

            if (
                    !roles.contains(
                            RoleCode
                                    .FINANCE_APPROVER
                                    .name()
                    )
            ) {

                throw new BadRequestException(
                        "L1 or L2 requires FINANCE_APPROVER role"
                );
            }
        }

        if (
                request.approvalLevel()
                        == ApprovalLevel.L3
        ) {

            if (
                    !roles.contains(
                            RoleCode
                                    .FINANCE_MANAGER
                                    .name()
                    )
            ) {

                throw new BadRequestException(
                        "L3 requires FINANCE_MANAGER role"
                );
            }
        }

        ApprovalAuthority authority =
                approvalAuthorityRepository
                        .findByUserId(
                                userId
                        )
                        .orElseGet(
                                () -> {

                                    ApprovalAuthority
                                            newAuthority =
                                                new ApprovalAuthority();

                                    newAuthority
                                            .setUserId(
                                                    userId
                                            );

                                    newAuthority
                                            .setActive(
                                                    true
                                            );

                                    return newAuthority;
                                }
                        );

        authority.setApprovalLevel(
                request.approvalLevel()
        );

        authority.setActive(
                true
        );

        authority =
                approvalAuthorityRepository
                        .save(
                                authority
                        );

        return new ApprovalAuthorityResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                authority.getApprovalLevel(),
                authority.getActive()
        );
    }

    @Transactional(
            readOnly = true
    )
    public List<ApprovalAuthorityResponse>
    findApprovers(
            ApprovalLevel approvalLevel
    ) {

        return approvalAuthorityRepository
                .findByApprovalLevelAndActiveTrue(
                        approvalLevel
                )
                .stream()
                .map(
                        authority -> {

                            User user =
                                    getUserEntity(
                                            authority
                                                    .getUserId()
                                    );

                            return new ApprovalAuthorityResponse(
                                    user.getId(),
                                    user.getUsername(),
                                    user.getFullName(),
                                    authority
                                            .getApprovalLevel(),
                                    authority
                                            .getActive()
                            );
                        }
                )
                .toList();
    }

    private List<String> loadRoles(
            Long userId
    ) {

        return userRoleRepository
                .findByUserId(
                        userId
                )
                .stream()
                .map(
                        userRole ->
                                roleRepository
                                        .findById(
                                                userRole
                                                        .getRoleId()
                                        )
                                        .orElseThrow(
                                                () ->
                                                        new ResourceNotFoundException(
                                                                "Role",
                                                                String.valueOf(
                                                                        userRole
                                                                                .getRoleId()
                                                                )
                                                        )
                                        )
                                        .getRoleCode()
                                        .name()
                )
                .toList();
    }

    private UserResponse mapToResponse(
            User user
    ) {

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getStatus(),
                loadRoles(
                        user.getId()
                )
        );
    }
}