package com.project.skin_me.request;

import com.project.skin_me.enums.LogisticCompany;
import lombok.Data;

@Data
public class MarkDeliveredRequest {
    private LogisticCompany logisticCompany;
}
