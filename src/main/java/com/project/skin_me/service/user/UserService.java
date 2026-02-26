package com.project.skin_me.service.user;

import com.project.skin_me.dto.UserDto;
import com.project.skin_me.enums.ActivityType;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Activity;
import com.project.skin_me.model.Role;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.repository.RoleRepository;
import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.request.CreateUserRequest;
import com.project.skin_me.request.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    @Override
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertUserToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public User createUser(CreateUserRequest request) {
        // 1. Password match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // 2. Email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException("Email already exists: " + request.getEmail());
        }

        // 3. Map request → entity
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setEmail(request.getEmail());
        // encode password *before* saving
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setConfirmPassword(passwordEncoder.encode(request.getConfirmPassword()));

        newUser.setEnabled(true);
        newUser.setIsOnline(false);
        newUser.setRegistrationDate(LocalDateTime.now());

        // 4. Role handling
        Role role;
        final String roleName;
        if (request.getRole() != null && request.getRole().getName() != null && !request.getRole().getName().isEmpty()) {
            roleName = request.getRole().getName();
            role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Role not found: " + roleName));
        } else {
            roleName = "ROLE_USER";
            role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new RuntimeException("Default role ROLE_USER not found."));
        }
        newUser.setRoles(new HashSet<>(Collections.singletonList(role)));

        // 5. Persist
        User savedUser = userRepository.save(newUser);

        // 6. Activity log (optional – you already have registerUser that does this)
        Activity activity = new Activity();
        activity.setUser(savedUser);
        activity.setActivityType(ActivityType.REGISTER);
        activity.setTimestamp(LocalDateTime.now());
        activity.setDetails("Admin created user: " + request.getEmail());
        activityRepository.save(activity);

        return savedUser;
    }

//    @Transactional
//    public User registerUser(User user) {
//        if (userRepository.existsByEmail(user.getEmail())) {
//            throw new AlreadyExistsException("Email already exists: " + user.getEmail());
//        }
//
//        // Encode password
//        if (user.getPassword() != null) {
//            user.setPassword(passwordEncoder.encode(user.getPassword()));
//        }
//        if (user.getRegistrationDate() == null) {
//            user.setRegistrationDate(LocalDateTime.now());
//        }
//        user.setIsOnline(false);
//
//        User savedUser = userRepository.save(user);
//
//        // Log registration activity
//        Activity activity = new Activity();
//        activity.setUser(savedUser);
//        activity.setActivityType(ActivityType.REGISTER);
//        activity.setTimestamp(LocalDateTime.now());
//        activity.setDetails("User registered with email: " + user.getEmail());
//        activityRepository.save(activity);
//
//        return savedUser;
//    }

    @Override
    @Transactional
    public User updateUser(UserUpdateRequest request, Long userId) {
        return userRepository.findById(userId).map(existingUser -> {
            if (request.getFirstName() != null) {
                existingUser.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                existingUser.setLastName(request.getLastName());
            }
            if (request.getEmail() != null) {
                // Check if email is already taken by another user
                if (!existingUser.getEmail().equals(request.getEmail()) && 
                    userRepository.existsByEmail(request.getEmail())) {
                    throw new AlreadyExistsException("Email already exists: " + request.getEmail());
                }
                existingUser.setEmail(request.getEmail());
            }
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            if (request.getEnabled() != null) {
                existingUser.setEnabled(request.getEnabled());
            }
            return userRepository.save(existingUser);
        }).orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    @Override
    @Transactional
    public User assignRole(Long userId, String roleName) {
        User user = getUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        
        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(role);
        
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setActivityType(ActivityType.REGISTER);
        activity.setTimestamp(LocalDateTime.now());
        activity.setDetails("Role " + roleName + " assigned to user: " + user.getEmail());
        activityRepository.save(activity);
        
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User removeRole(Long userId, String roleName) {
        User user = getUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
        
        if (user.getRoles() != null) {
            user.getRoles().remove(role);
        }
        
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setActivityType(ActivityType.REGISTER);
        activity.setTimestamp(LocalDateTime.now());
        activity.setDetails("Role " + roleName + " removed from user: " + user.getEmail());
        activityRepository.save(activity);
        
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        userRepository.findById(userId)
                .ifPresentOrElse(userRepository::delete,
                        () -> { throw new ResourceNotFoundException("User not found with ID: " + userId); });
    }

    @Override
    public UserDto convertUserToDto(User user) {
        UserDto dto = modelMapper.map(user, UserDto.class);
        // Role names from relationship (e.g. ROLE_ADMIN, ROLE_USER) for profile display
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
        }
        return dto;
    }

    @Override
    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public List<Activity> getUserActivityHistory(Long userId) {
        return activityRepository.findByUserId(userId);
    }

    @Override
    public boolean isUserOnline(Long userId) {
        return userRepository.findById(userId).map(User::isOnline).orElse(false);
    }

    @Override
    @Transactional
    public void recordPurchase(Long userId, String orderDetails) {
        if (orderDetails == null || orderDetails.trim().isEmpty()) {
            throw new IllegalArgumentException("Order details cannot be empty");
        }
        User user = getUserById(userId);
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setActivityType(ActivityType.PURCHASE);
        activity.setTimestamp(LocalDateTime.now());
        activity.setDetails("Purchase made: " + orderDetails);
        activityRepository.save(activity);
    }
}
