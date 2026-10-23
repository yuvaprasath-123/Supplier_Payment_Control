package com.ofss.service;

import com.ofss.dto.ApprovalAuthorityRequest;
import com.ofss.dto.ApprovalAuthorityResponse;
import com.ofss.dto.AssignRolesRequest;
import com.ofss.dto.CreateUserRequest;

import com.ofss.entity.ApprovalAuthority;
import com.ofss.entity.ApprovalLevel;
import com.ofss.entity.Role;
import com.ofss.entity.RoleCode;
import com.ofss.entity.User;
import com.ofss.entity.UserRole;
import com.ofss.entity.UserStatus;

import com.ofss.exception.BadRequestException;

import com.ofss.repository.ApprovalAuthorityRepository;
import com.ofss.repository.RoleRepository;
import com.ofss.repository.UserRepository;
import com.ofss.repository.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private ApprovalAuthorityRepository approvalAuthorityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {

        userService =
                new UserService(
                        userRepository,
                        roleRepository,
                        userRoleRepository,
                        approvalAuthorityRepository,
                        passwordEncoder
                );
    }

    @Test
    void createUser_shouldCreateActiveUser() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "requester1",
                        "password123",
                        "Requester One",
                        "requester1@test.com"
                );

        when(
                userRepository
                        .existsByUsername(
                                "requester1"
                        )
        ).thenReturn(false);

        when(
                userRepository
                        .existsByEmail(
                                "requester1@test.com"
                        )
        ).thenReturn(false);

        when(
                passwordEncoder
                        .encode(
                                "password123"
                        )
        ).thenReturn(
                "encoded-password"
        );

        when(
                userRepository
                        .save(
                                any(User.class)
                        )
        ).thenAnswer(invocation -> {

            User user =
                    invocation.getArgument(0);

            user.setId(1L);

            return user;
        });

        when(
                userRoleRepository
                        .findByUserId(1L)
        ).thenReturn(
                List.of()
        );

        var response =
                userService.createUser(
                        request
                );

        assertEquals(
                1L,
                response.userId()
        );

        assertEquals(
                "requester1",
                response.username()
        );

        assertEquals(
                UserStatus.ACTIVE,
                response.status()
        );

        verify(
                passwordEncoder
        ).encode(
                "password123"
        );
    }

    @Test
    void createUser_shouldRejectDuplicateUsername() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "requester1",
                        "password123",
                        "Requester One",
                        "requester1@test.com"
                );

        when(
                userRepository
                        .existsByUsername(
                                "requester1"
                        )
        ).thenReturn(true);

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                userService
                                        .createUser(
                                                request
                                        )
                );

        assertEquals(
                "Username already exists",
                exception.getMessage()
        );

        verify(
                userRepository,
                never()
        ).save(
                any(User.class)
        );
    }

    @Test
    void createUser_shouldRejectDuplicateEmail() {

        CreateUserRequest request =
                new CreateUserRequest(
                        "requester1",
                        "password123",
                        "Requester One",
                        "requester1@test.com"
                );

        when(
                userRepository
                        .existsByUsername(
                                "requester1"
                        )
        ).thenReturn(false);

        when(
                userRepository
                        .existsByEmail(
                                "requester1@test.com"
                        )
        ).thenReturn(true);

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                userService
                                        .createUser(
                                                request
                                        )
                );

        assertEquals(
                "Email already exists",
                exception.getMessage()
        );

        verify(
                userRepository,
                never()
        ).save(
                any(User.class)
        );
    }

    @Test
    void assignApprovalAuthority_shouldAllowL1ForFinanceApprover() {

        User user =
                createUserEntity(
                        2L,
                        "approver1"
                );

        Role role =
                createRole(
                        10L,
                        RoleCode.FINANCE_APPROVER
                );

        UserRole userRole =
                createUserRole(
                        2L,
                        10L
                );

        when(
                userRepository
                        .findById(2L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(2L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(10L)
        ).thenReturn(
                Optional.of(role)
        );

        when(
                approvalAuthorityRepository
                        .findByUserId(2L)
        ).thenReturn(
                Optional.empty()
        );

        when(
                approvalAuthorityRepository
                        .save(
                                any(ApprovalAuthority.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L1
                );

        ApprovalAuthorityResponse response =
                userService
                        .assignApprovalAuthority(
                                2L,
                                request
                        );

        assertEquals(
                ApprovalLevel.L1,
                response.approvalLevel()
        );

        assertTrue(
                response.active()
        );
    }

    @Test
    void assignApprovalAuthority_shouldAllowL2ForFinanceApprover() {

        User user =
                createUserEntity(
                        2L,
                        "approver2"
                );

        Role role =
                createRole(
                        10L,
                        RoleCode.FINANCE_APPROVER
                );

        UserRole userRole =
                createUserRole(
                        2L,
                        10L
                );

        when(
                userRepository
                        .findById(2L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(2L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(10L)
        ).thenReturn(
                Optional.of(role)
        );

        when(
                approvalAuthorityRepository
                        .findByUserId(2L)
        ).thenReturn(
                Optional.empty()
        );

        when(
                approvalAuthorityRepository
                        .save(
                                any(ApprovalAuthority.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L2
                );

        ApprovalAuthorityResponse response =
                userService
                        .assignApprovalAuthority(
                                2L,
                                request
                        );

        assertEquals(
                ApprovalLevel.L2,
                response.approvalLevel()
        );
    }

    @Test
    void assignApprovalAuthority_shouldAllowL3ForFinanceManager() {

        User user =
                createUserEntity(
                        3L,
                        "manager1"
                );

        Role role =
                createRole(
                        20L,
                        RoleCode.FINANCE_MANAGER
                );

        UserRole userRole =
                createUserRole(
                        3L,
                        20L
                );

        when(
                userRepository
                        .findById(3L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(3L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(20L)
        ).thenReturn(
                Optional.of(role)
        );

        when(
                approvalAuthorityRepository
                        .findByUserId(3L)
        ).thenReturn(
                Optional.empty()
        );

        when(
                approvalAuthorityRepository
                        .save(
                                any(ApprovalAuthority.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L3
                );

        ApprovalAuthorityResponse response =
                userService
                        .assignApprovalAuthority(
                                3L,
                                request
                        );

        assertEquals(
                ApprovalLevel.L3,
                response.approvalLevel()
        );

        assertTrue(
                response.active()
        );
    }

    @Test
    void assignApprovalAuthority_shouldRejectL1ForPaymentRequester() {

        User user =
                createUserEntity(
                        1L,
                        "requester1"
                );

        Role role =
                createRole(
                        30L,
                        RoleCode.PAYMENT_REQUESTER
                );

        UserRole userRole =
                createUserRole(
                        1L,
                        30L
                );

        when(
                userRepository
                        .findById(1L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(1L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(30L)
        ).thenReturn(
                Optional.of(role)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L1
                );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                userService
                                        .assignApprovalAuthority(
                                                1L,
                                                request
                                        )
                );

        assertEquals(
                "L1 or L2 requires FINANCE_APPROVER role",
                exception.getMessage()
        );

        verify(
                approvalAuthorityRepository,
                never()
        ).save(
                any(ApprovalAuthority.class)
        );
    }

    @Test
    void assignApprovalAuthority_shouldRejectL3ForFinanceApprover() {

        User user =
                createUserEntity(
                        2L,
                        "approver1"
                );

        Role role =
                createRole(
                        10L,
                        RoleCode.FINANCE_APPROVER
                );

        UserRole userRole =
                createUserRole(
                        2L,
                        10L
                );

        when(
                userRepository
                        .findById(2L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(2L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(10L)
        ).thenReturn(
                Optional.of(role)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L3
                );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                userService
                                        .assignApprovalAuthority(
                                                2L,
                                                request
                                        )
                );

        assertEquals(
                "L3 requires FINANCE_MANAGER role",
                exception.getMessage()
        );

        verify(
                approvalAuthorityRepository,
                never()
        ).save(
                any(ApprovalAuthority.class)
        );
    }

    @Test
    void assignApprovalAuthority_shouldRejectL1ForFinanceManager() {

        User user =
                createUserEntity(
                        3L,
                        "manager1"
                );

        Role role =
                createRole(
                        20L,
                        RoleCode.FINANCE_MANAGER
                );

        UserRole userRole =
                createUserRole(
                        3L,
                        20L
                );

        when(
                userRepository
                        .findById(3L)
        ).thenReturn(
                Optional.of(user)
        );

        when(
                userRoleRepository
                        .findByUserId(3L)
        ).thenReturn(
                List.of(userRole)
        );

        when(
                roleRepository
                        .findById(20L)
        ).thenReturn(
                Optional.of(role)
        );

        ApprovalAuthorityRequest request =
                new ApprovalAuthorityRequest(
                        ApprovalLevel.L1
                );

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () ->
                                userService
                                        .assignApprovalAuthority(
                                                3L,
                                                request
                                        )
                );

        assertEquals(
                "L1 or L2 requires FINANCE_APPROVER role",
                exception.getMessage()
        );

        verify(
                approvalAuthorityRepository,
                never()
        ).save(
                any(ApprovalAuthority.class)
        );
    }

    private User createUserEntity(
            Long id,
            String username
    ) {

        User user =
                new User();

        user.setId(id);
        user.setUsername(username);
        user.setFullName(
                username + " Full Name"
        );
        user.setEmail(
                username + "@test.com"
        );
        user.setStatus(
                UserStatus.ACTIVE
        );

        return user;
    }

    private Role createRole(
            Long id,
            RoleCode roleCode
    ) {

        Role role =
                new Role();

        role.setId(id);
        role.setRoleCode(
                roleCode
        );
        role.setRoleName(
                roleCode.name()
        );

        return role;
    }

    private UserRole createUserRole(
            Long userId,
            Long roleId
    ) {

        UserRole userRole =
                new UserRole();

        userRole.setUserId(
                userId
        );

        userRole.setRoleId(
                roleId
        );

        return userRole;
    }
}