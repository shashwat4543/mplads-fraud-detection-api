package com.mplads.fraud_detection.dto;

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
    public boolean isAnomaly() { return isAnomaly; }
    public void setAnomaly(boolean anomaly) { isAnomaly = anomaly; }
    public List<Anomaly> getAnomalies() { return anomalies; }
    public void setAnomalies(List<Anomaly> anomalies) { this.anomalies = anomalies; }
}