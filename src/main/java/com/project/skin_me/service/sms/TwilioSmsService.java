package com.project.skin_me.service.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Sends OTP via Twilio SMS.
 * Enable by setting in application.properties:
 *   app.sms.twilio.account-sid=ACxxxx
 *   app.sms.twilio.auth-token=your_token
 *   app.sms.twilio.from-number=+1234567890
 * Get these from https://console.twilio.com (Account SID, Auth Token, and buy a phone number).
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.sms.twilio.account-sid")
public class TwilioSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsService.class);

    @Value("${app.sms.twilio.account-sid}")
    private String accountSid;

    @Value("${app.sms.twilio.auth-token}")
    private String authToken;

    @Value("${app.sms.twilio.from-number}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        logger.info("Twilio SMS service initialized (from: {})", fromNumber);
    }

    @Override
    public boolean sendOtp(String phone, String code) {
        try {
            String body = "Your Skin.me verification code is: " + code + ". Valid for 10 minutes.";
            PhoneNumber to = new PhoneNumber(normalizeToE164(phone));
            PhoneNumber from = new PhoneNumber(fromNumber);
            Message.creator(to, from, body).create();
            logger.info("SMS OTP sent via Twilio to {}", phone);
            return true;
        } catch (Exception e) {
            logger.error("Twilio SMS failed for {}: {}", phone, e.getMessage(), e);
            logger.warn("For testing only: use this OTP in login-with-phone: {}", code);
            return false;
        }
    }

    /** Ensure number has + prefix for Twilio. */
    private static String normalizeToE164(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) digits = "855" + digits.substring(1); // Cambodia local
        if (!digits.startsWith("855")) digits = "855" + digits; // assume Cambodia if no country code
        return "+" + digits;
    }
}
