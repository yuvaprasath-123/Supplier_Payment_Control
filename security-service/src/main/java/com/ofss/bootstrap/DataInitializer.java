package com.ofss.bootstrap;

import com.ofss.entity.Role;
import com.ofss.entity.RoleCode;
import com.ofss.repository.RoleRepository;



import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    
    public DataInitializer(RoleRepository roleRepository) {
    	this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {

        createRoleIfMissing(
                RoleCode.PAYMENT_REQUESTER,
                "Payment Requester",
                "Creates and tracks supplier payment requests"
        );

        createRoleIfMissing(
                RoleCode.FINANCE_APPROVER,
                "Finance Approver",
                "Reviews and approves or rejects supplier payment requests"
        );

        createRoleIfMissing(
                RoleCode.FINANCE_MANAGER,
                "Finance Manager",
                "Handles high-value and high-risk supplier payment requests"
        );
    }

    private void createRoleIfMissing(
            RoleCode roleCode,
            String roleName,
            String description
    ) {

        boolean roleExists =
                roleRepository
                        .findByRoleCode(roleCode)
                        .isPresent();

        if (!roleExists) {

            Role role = new Role();
            
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setDescription(description);
             

            roleRepository.save(role);
        }
    }
}