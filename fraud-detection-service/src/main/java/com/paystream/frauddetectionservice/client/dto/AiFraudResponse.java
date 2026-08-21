package com.paystream.frauddetectionservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiFraudResponse {

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("risk_score")
    private Float riskScore;

    @JsonProperty("fraud_indicators")
    private List<String> fraudIndicators;

    private String recommendation;

    private String explanation;
}
