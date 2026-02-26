package com.project.skin_me.scheduler;

import com.project.skin_me.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class UserOnlineStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(UserOnlineStatusScheduler.class);
    private final UserRepository userRepository;

    // Inactivity threshold: 15 minutes
    private static final long INACTIVITY_THRESHOLD_MINUTES = 15;

    public UserOnlineStatusScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Check for inactive users and mark them as offline
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    @Transactional
    public void checkInactiveUsers() {
        try {
            LocalDateTime thresholdTime = LocalDateTime.now().minus(INACTIVITY_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
            
            // Find all users who are marked as online but haven't been active recently
            userRepository.findAll().forEach(user -> {
                if (user.isOnline()) {
                    LocalDateTime lastActivity = user.getLastActivity();
                    
                    // If no last activity or last activity is older than threshold, mark as offline
                    if (lastActivity == null || lastActivity.isBefore(thresholdTime)) {
                        user.setIsOnline(false);
                        user.setLastIpAddress(null); 
                        userRepository.save(user);
                        logger.info("Marked user {} as offline due to inactivity (last activity: {})", 
                                user.getEmail(), lastActivity);
                    }
                }
            });
            
            logger.debug("Completed inactive user check at {}", LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Error checking inactive users: {}", e.getMessage(), e);
        }
    }
}
