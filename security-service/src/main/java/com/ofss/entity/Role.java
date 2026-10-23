package com.ofss.entity;

import jakarta.persistence.*;


@Entity
@Table(name = "SEC_ROLES")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROLE_ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_CODE", nullable = false, unique = true)
    private RoleCode roleCode;

    @Column(name = "ROLE_NAME", nullable = false)
    private String roleName;

    @Column(name = "DESCRIPTION")
    private String description;

	public Role() {
		super();
		// TODO Auto-generated constructor stub
	}

	public Role(Long id, RoleCode roleCode, String roleName, String description) {
		super();
		this.id = id;
		this.roleCode = roleCode;
		this.roleName = roleName;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public RoleCode getRoleCode() {
		return roleCode;
	}

	public void setRoleCode(RoleCode roleCode) {
		this.roleCode = roleCode;
	}

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
    
    
}