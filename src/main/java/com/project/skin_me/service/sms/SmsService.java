package com.project.skin_me.service.sms;

/**
 * Sends SMS (e.g. OTP). Implement with Twilio, local provider, or stub for development.
 */
public interface SmsService {

    /**
     * Send an OTP code to the given phone number.
     * @param phone E.164 or national format
     * @param code OTP code (e.g. 6 digits)
     * @return true if send was accepted (delivery not guaranteed)
     */
    boolean sendOtp(String phone, String code);
}
