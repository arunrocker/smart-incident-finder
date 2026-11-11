package com.incident.finder.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "incident.search")
public class IncidentSearchProperties {

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private double minimumSimilarityPercentage = 10.0;

    @Min(1)
    @Max(5000)
    private int maximumDescriptionLength = 5000;

    @Min(1024)
    @Max(1048576)
    private int maximumRequestSizeBytes = 32768;

    public double getMinimumSimilarityPercentage() {
        return minimumSimilarityPercentage;
    }

    public void setMinimumSimilarityPercentage(double minimumSimilarityPercentage) {
        this.minimumSimilarityPercentage = minimumSimilarityPercentage;
    }

    public int getMaximumDescriptionLength() {
        return maximumDescriptionLength;
    }

    public void setMaximumDescriptionLength(int maximumDescriptionLength) {
        this.maximumDescriptionLength = maximumDescriptionLength;
    }

    public int getMaximumRequestSizeBytes() {
        return maximumRequestSizeBytes;
    }

    public void setMaximumRequestSizeBytes(int maximumRequestSizeBytes) {
        this.maximumRequestSizeBytes = maximumRequestSizeBytes;
    }
}
