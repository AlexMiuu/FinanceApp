package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConsentRecordDto {

    private String consentType;
    private String version;
    private Instant grantedAt;
}
