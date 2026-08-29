package com.mplads.fraud_detection.dto;

import com.mplads.fraud_detection.entity.MP;
import java.math.BigDecimal;

public class MPDashboardDTO {
    private Long id;
    private String name;
    private String constituency;
    private String state;
    private String party;

    // Financial KPIs
    private BigDecimal allocatedAmount;
    private BigDecimal totalExpenditure;
    private Double utilizationPercentage;
    private BigDecimal unspentAmount;
    private Double completionRate;
    private Integer totalWorksCount;

    // Anomaly Metric Badges
    private long totalAnomalies;
    private long highSeverityCount;
    private long mediumSeverityCount;

    public MPDashboardDTO(MP mp, int totalWorks, long totalAnomalies, long highSeverity, long mediumSeverity) {
        this.id = mp.getId();
        this.name = mp.getName();
        this.constituency = mp.getConstituency();
        this.state = mp.getState();
        this.party = mp.getParty();
        this.allocatedAmount = mp.getAllocatedAmount();
        this.totalExpenditure = mp.getTotalExpenditure();
        this.utilizationPercentage = mp.getUtilizationPercentage();
        this.unspentAmount = mp.getUnspentAmount();
        this.completionRate = mp.getCompletionRate();
        this.totalWorksCount = totalWorks;
        this.totalAnomalies = totalAnomalies;
        this.highSeverityCount = highSeverity;
        this.mediumSeverityCount = mediumSeverity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getConstituency() { return constituency; }
    public String getState() { return state; }
    public String getParty() { return party; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public BigDecimal getTotalExpenditure() { return totalExpenditure; }
    public Double getUtilizationPercentage() { return utilizationPercentage; }
    public BigDecimal getUnspentAmount() { return unspentAmount; }
    public Double getCompletionRate() { return completionRate; }
    public Integer getTotalWorksCount() { return totalWorksCount; }
    public long getTotalAnomalies() { return totalAnomalies; }
    public long getHighSeverityCount() { return highSeverityCount; }
    public long getMediumSeverityCount() { return mediumSeverityCount; }
}