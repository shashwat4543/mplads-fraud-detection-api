package com.mplads.fraud_detection.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "mps")
public class MP {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String constituency;
    private String state;
    private String party;

    private BigDecimal allocatedAmount;
    private BigDecimal totalExpenditure;
    private Double utilizationPercentage;
    private Integer completedWorksCount;
    private Integer recommendedWorksCount;
    private Double completionRate;
    private BigDecimal unspentAmount;

    @JsonIgnore
    @OneToMany(mappedBy = "mp", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> projects;

    public MP() {}

    public MP(String name, String constituency, String state, String party) {
        this.name = name;
        this.constituency = constituency;
        this.state = state;
        this.party = party;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getConstituency() { return constituency; }
    public void setConstituency(String constituency) { this.constituency = constituency; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public BigDecimal getTotalExpenditure() { return totalExpenditure; }
    public void setTotalExpenditure(BigDecimal totalExpenditure) { this.totalExpenditure = totalExpenditure; }
    public Double getUtilizationPercentage() { return utilizationPercentage; }
    public void setUtilizationPercentage(Double utilizationPercentage) { this.utilizationPercentage = utilizationPercentage; }
    public Integer getCompletedWorksCount() { return completedWorksCount; }
    public void setCompletedWorksCount(Integer completedWorksCount) { this.completedWorksCount = completedWorksCount; }
    public Integer getRecommendedWorksCount() { return recommendedWorksCount; }
    public void setRecommendedWorksCount(Integer recommendedWorksCount) { this.recommendedWorksCount = recommendedWorksCount; }
    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
    public BigDecimal getUnspentAmount() { return unspentAmount; }
    public void setUnspentAmount(BigDecimal unspentAmount) { this.unspentAmount = unspentAmount; }
    public List<Project> getProjects() { return projects; }
    public void setProjects(List<Project> projects) { this.projects = projects; }
}