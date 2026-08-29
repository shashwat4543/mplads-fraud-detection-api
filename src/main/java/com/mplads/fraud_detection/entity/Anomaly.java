package com.mplads.fraud_detection.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies", indexes = {
        @Index(name = "idx_anomaly_mp", columnList = "relatedMpId"),
        @Index(name = "idx_anomaly_project", columnList = "relatedProjectId"),
        @Index(name = "idx_anomaly_type", columnList = "anomalyType")
})
public class Anomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String anomalyType;
    private String description;
    private String severity; // HIGH, MEDIUM, LOW
    private Long relatedProjectId;
    private Long relatedMpId;
    private LocalDateTime detectedAt;

    public Anomaly() {}

    public Anomaly(String anomalyType, String description, String severity, Long relatedProjectId, Long relatedMpId, LocalDateTime detectedAt) {
        this.anomalyType = anomalyType;
        this.description = description;
        this.severity = severity;
        this.relatedProjectId = relatedProjectId;
        this.relatedMpId = relatedMpId;
        this.detectedAt = detectedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    // FIX: JSON key is now "ruleCode" to match API_DOCUMENTATION.md and the frontend,
    // which both expect `ruleCode`. The Java field/column name (anomalyType) is
    // unchanged, so no DB migration or internal logic elsewhere needs to change.
    @JsonProperty("ruleCode")
    public String getAnomalyType() { return anomalyType; }
    public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Long getRelatedProjectId() { return relatedProjectId; }
    public void setRelatedProjectId(Long relatedProjectId) { this.relatedProjectId = relatedProjectId; }

    public Long getRelatedMpId() { return relatedMpId; }
    public void setRelatedMpId(Long relatedMpId) { this.relatedMpId = relatedMpId; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
}