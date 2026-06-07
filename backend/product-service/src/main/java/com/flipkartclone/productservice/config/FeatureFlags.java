package com.flipkartclone.productservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope                          // picks up changes via /actuator/busrefresh — no restart needed
@ConfigurationProperties(prefix = "features")
public class FeatureFlags {

    private boolean bulkUploadEnabled     = false;
    private boolean aiPricingEnabled      = false;
    private boolean newReviewFlowEnabled  = false;
}
