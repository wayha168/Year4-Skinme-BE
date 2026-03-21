package com.project.skin_me.service.sms;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Twilio bean is registered only when {@code app.sms.twilio.account-sid} is non-blank.
 * (Properties use {@code ${TWILIO_ACCOUNT_SID:}} so missing env vars do not break startup.)
 */
public class TwilioSmsEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String sid = context.getEnvironment().getProperty("app.sms.twilio.account-sid", "");
        return StringUtils.hasText(sid);
    }
}
