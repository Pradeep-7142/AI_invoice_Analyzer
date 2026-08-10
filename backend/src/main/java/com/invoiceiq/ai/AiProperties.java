package com.invoiceiq.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "invoiceiq.ai")
public class AiProperties {

    private String provider = "mock";
    private String apiKey = "";
    private String baseUrl = "";
    private String model = "gpt-4o-mini";
    private double confidenceThreshold = 0.75;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public boolean isLlmEnabled() {
        return provider != null && !provider.equalsIgnoreCase("mock") && apiKey != null && !apiKey.isBlank();
    }
}
