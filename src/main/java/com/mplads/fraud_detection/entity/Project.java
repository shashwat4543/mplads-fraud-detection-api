package com.mplads.fraud_detection.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String projectCode;

    @Column(length = 1000)
    private String title;
    private String category;
    private String agencyName;

    private BigDecimal recommendedAmount;
    private BigDecimal sanctionedAmount;
    private BigDecimal expenditureAmount;

    private LocalDate recommendationDate;
    private LocalDate sanctionDate;
    private LocalDate completionDate;
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mp_id")
    private MP mp;

    public Project() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }
    public BigDecimal getRecommendedAmount() { return recommendedAmount; }
    public void setRecommendedAmount(BigDecimal recommendedAmount) { this.recommendedAmount = recommendedAmount; }
    public BigDecimal getSanctionedAmount() { return sanctionedAmount; }
    public void setSanctionedAmount(BigDecimal sanctionedAmount) { this.sanctionedAmount = sanctionedAmount; }
    public BigDecimal getExpenditureAmount() { return expenditureAmount; }
    public void setExpenditureAmount(BigDecimal expenditureAmount) { this.expenditureAmount = expenditureAmount; }
    public LocalDate getRecommendationDate() { return recommendationDate; }
    public void setRecommendationDate(LocalDate recommendationDate) { this.recommendationDate = recommendationDate; }
    public LocalDate getSanctionDate() { return sanctionDate; }
    public void setSanctionDate(LocalDate sanctionDate) { this.sanctionDate = sanctionDate; }
    public LocalDate getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public MP getMp() { return mp; }
    public void setMp(MP mp) { this.mp = mp; }
}