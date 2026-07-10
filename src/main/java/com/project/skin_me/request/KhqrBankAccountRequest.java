package com.project.skin_me.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input for creating/updating KHQR bank accounts (admin API and web form).
 * Normalization and validation happen in {@link com.project.skin_me.service.payment.KhqrBankAccountService}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KhqrBankAccountRequest {

    private String gateway;
    private String account;
    private String merchantName;
    private String city;
    /** Merchant category code; defaults to 5999 when blank. */
    private String categoryCode;
    private String testAccountUsd;
    private String testAccountKhr;
    private boolean useTestWhenEmpty;
    private boolean active;
    private int displayOrder;

    /**
     * Maps traditional Spring MVC form / explicit parameters to a request DTO (same fields as HTML form).
     */
    public static KhqrBankAccountRequest fromWebForm(
            String gateway,
            String account,
            String merchantName,
            String city,
            String categoryCode,
            String testAccountUsd,
            String testAccountKhr,
            boolean useTestWhenEmpty,
            boolean active,
            int displayOrder) {
        KhqrBankAccountRequest r = new KhqrBankAccountRequest();
        r.setGateway(gateway);
        r.setAccount(account);
        r.setMerchantName(merchantName);
        r.setCity(city);
        r.setCategoryCode(categoryCode);
        r.setTestAccountUsd(testAccountUsd);
        r.setTestAccountKhr(testAccountKhr);
        r.setUseTestWhenEmpty(useTestWhenEmpty);
        r.setActive(active);
        r.setDisplayOrder(displayOrder);
        return r;
    }
}
