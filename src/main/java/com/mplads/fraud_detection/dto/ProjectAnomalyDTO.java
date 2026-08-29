package com.mplads.fraud_detection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import java.util.List;

public class ProjectAnomalyDTO {
    private Project project;
    private boolean isAnomaly;
    private List<Anomaly> anomalies;

    public ProjectAnomalyDTO(Project project, boolean isAnomaly, List<Anomaly> anomalies) {
        this.project = project;
        this.isAnomaly = isAnomaly;
        this.anomalies = anomalies;
    }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    // FIX: without @JsonProperty, Jackson strips the "is" prefix from isAnomaly()
    // and serializes the key as "anomaly" — not "isAnomaly" and not "flagged".
    // The frontend and API_DOCUMENTATION.md both expect "flagged", so pin it explicitly.
    @JsonProperty("flagged")
    public boolean isAnomaly() { return isAnomaly; }
    public void setAnomaly(boolean anomaly) { isAnomaly = anomaly; }

    public List<Anomaly> getAnomalies() { return anomalies; }
    public void setAnomalies(List<Anomaly> anomalies) { this.anomalies = anomalies; }
}