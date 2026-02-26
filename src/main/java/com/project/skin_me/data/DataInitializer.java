package com.project.skin_me.data;

import com.project.skin_me.model.Role;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.RoleRepository;
import com.project.skin_me.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_ADMIN_EMAIL = "admin@skinme.com";
    private static final String DEFAULT_PASSWORD = "password";

    private static final List<String[]> DEFAULT_USERS = List.of(
            new String[] { "Veha", "Seng" },
            new String[] { "Bunroen", "Has" },
            new String[] { "Sokha", "Kim" },
            new String[] { "Jennie", "Kim" },
            new String[] { "Rosie", "Park" });

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener
    @Transactional
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("Initializing default roles, admin and sample users");
        createDefaultRolesIfNotExists();
        createDefaultAdminIfNotExists();
        createDefaultUsersIfNotExists();
    }

    private void createDefaultRolesIfNotExists() {
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            Role userRole = new Role();
            userRole.setName("ROLE_USER");
            roleRepository.save(userRole);
            logger.info("Created ROLE_USER");
        }
        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");
            roleRepository.save(adminRole);
            logger.info("Created ROLE_ADMIN");
        }
    }

    private void createDefaultAdminIfNotExists() {
        if (userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
            logger.info("Admin user already exists: {}", DEFAULT_ADMIN_EMAIL);
            return;
        }
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));

        User admin = new User();
        admin.setFirstName("Administrator");
        admin.setLastName("Administrator");
        admin.setEmail(DEFAULT_ADMIN_EMAIL);
        String encoded = passwordEncoder.encode(DEFAULT_PASSWORD);
        admin.setPassword(encoded);
        admin.setConfirmPassword(encoded);
        admin.setEnabled(true);
        admin.setRegistrationDate(LocalDateTime.now());
        admin.setIsOnline(false);
        admin.setRoles(new HashSet<>(Collections.singletonList(adminRole)));

        userRepository.save(admin);
        logger.info("Created default admin: {} (email: {}, password: {})", admin.getFirstName(), DEFAULT_ADMIN_EMAIL,
                DEFAULT_PASSWORD);
    }

    private void createDefaultUsersIfNotExists() {
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        for (int i = 0; i < DEFAULT_USERS.size(); i++) {  
            String[] name = DEFAULT_USERS.get(i);
            String firstName = name[0];
            String lastName = name[1];

            String email = firstName.toLowerCase() + "@skinme.com";

            if (userRepository.existsByEmail(email)) {
                logger.info("User already exists: {}", email);
                continue;
            }

            User user = new User();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);

            String encoded = passwordEncoder.encode(DEFAULT_PASSWORD);
            user.setPassword(encoded);
            user.setConfirmPassword(encoded);

            user.setEnabled(true);
            user.setRegistrationDate(LocalDateTime.now());
            user.setIsOnline(false);
            user.setRoles(new HashSet<>(Collections.singletonList(userRole)));

            userRepository.save(user);

            logger.info("Created user: {} {} (email: {}, default password: {})",
                    firstName, lastName, email, DEFAULT_PASSWORD);
        }
    }
}