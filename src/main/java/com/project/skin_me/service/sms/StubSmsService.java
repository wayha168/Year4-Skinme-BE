package com.project.skin_me.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub SMS implementation: logs OTP to console instead of sending.
 * Used when Twilio is not configured (no app.sms.twilio.account-sid).
 */
@Service
public class StubSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(StubSmsService.class);

    @Override
    public boolean sendOtp(String phone, String code) {
        logger.info("SMS OTP (stub) -> phone: {}, code: {} (use this code for login-with-phone)", phone, code);
        return true;
    }
}
