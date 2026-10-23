package com.ofss.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "SEC_USER_ROLES",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_SEC_USER_ROLE",
                        columnNames = {"USER_ID", "ROLE_ID"}
                )
        }
)

public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ROLE_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ROLE_ID", nullable = false)
    private Long roleId;

    @Column(name = "ASSIGNED_AT")
    private LocalDateTime assignedAt;

    @PrePersist
    public void prePersist() {
        assignedAt = LocalDateTime.now();
    }

	public UserRole() {
		super();
		// TODO Auto-generated constructor stub
	}

	public UserRole(Long id, Long userId, Long roleId, LocalDateTime assignedAt) {
		super();
		this.id = id;
		this.userId = userId;
		this.roleId = roleId;
		this.assignedAt = assignedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Long getRoleId() {
		return roleId;
	}

	public void setRoleId(Long roleId) {
		this.roleId = roleId;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}
    
    
}