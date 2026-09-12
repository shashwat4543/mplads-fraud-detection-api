package com.mplads.fraud_detection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import java.util.List;
import java.util.Map;

public class ProjectAnomalyDTO {
    private Project project;
    private boolean isAnomaly;
    private List<Anomaly> anomalies;

    // NEW — additive only. Existing fields/names above are untouched, so this
    // remains backward compatible with any frontend code already parsing this DTO.
    // Plain Map instead of a dedicated class, consistent with getDebugStats().
    private Map<String, Object> risk;

    public ProjectAnomalyDTO(Project project, boolean isAnomaly, List<Anomaly> anomalies, Map<String, Object> risk) {
        this.project = project;
        this.isAnomaly = isAnomaly;
        this.anomalies = anomalies;
        this.risk = risk;
    }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    @JsonProperty("flagged")
    public boolean isAnomaly() { return isAnomaly; }
    public void setAnomaly(boolean anomaly) { isAnomaly = anomaly; }

    public List<Anomaly> getAnomalies() { return anomalies; }
    public void setAnomalies(List<Anomaly> anomalies) { this.anomalies = anomalies; }

    public Map<String, Object> getRisk() { return risk; }
    public void setRisk(Map<String, Object> risk) { this.risk = risk; }
}