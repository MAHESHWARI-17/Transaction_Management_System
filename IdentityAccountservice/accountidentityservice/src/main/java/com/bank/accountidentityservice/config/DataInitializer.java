package com.bank.accountidentityservice.config;

import com.bank.accountidentityservice.entity.Role;
import com.bank.accountidentityservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {

        for (Role.RoleName roleName : Role.RoleName.values()) {

            if (roleRepository.findByRoleName(roleName).isEmpty()) {

                roleRepository.save(Role.builder().roleName(roleName).build());
                log.info("Seeded role: {}", roleName);
            }
        }
        log.info("DataInitializer complete — roles CUSTOMER, COMPLIANCE, ADMIN are ready.");
    }
}
