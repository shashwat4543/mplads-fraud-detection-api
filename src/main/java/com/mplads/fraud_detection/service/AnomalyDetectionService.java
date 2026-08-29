package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import com.mplads.fraud_detection.repository.AnomalyRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final ProjectRepository projectRepository;
    private final AnomalyRepository anomalyRepository;

    public AnomalyDetectionService(ProjectRepository projectRepository,
                                   AnomalyRepository anomalyRepository) {
        this.projectRepository = projectRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional
    public List<Anomaly> runRulesEngine() {
        anomalyRepository.deleteAllInBatch();
        List<Anomaly> detectedAnomalies = new ArrayList<>();

        List<Project> allProjects = projectRepository.findAll();
        log.info("Starting Anomaly Scan on {} total projects...", allProjects.size());

        if (allProjects.isEmpty()) {
            log.warn("Database is EMPTY. Please import CSV first.");
            return detectedAnomalies;
        }

        Map<String, List<Project>> duplicateClusterMap = new HashMap<>();

        for (Project p : allProjects) {
            BigDecimal exp = p.getExpenditureAmount() != null ? p.getExpenditureAmount() : BigDecimal.ZERO;
            BigDecimal sanc = p.getSanctionedAmount() != null ? p.getSanctionedAmount() : BigDecimal.ZERO;
            Long mpId = p.getMp() != null ? p.getMp().getId() : null;
            String status = p.getStatus() != null ? p.getStatus().trim().toLowerCase() : "";

            // 1. Cost Overrun (totalPaid > budget/recommended)
            if (sanc.compareTo(BigDecimal.ZERO) > 0 && exp.compareTo(sanc) > 0) {
                BigDecimal diff = exp.subtract(sanc);
                detectedAnomalies.add(new Anomaly(
                        "COST_OVERRUN",
                        "Total Paid (₹" + exp.toPlainString() + ") exceeded sanctioned budget (₹" + sanc.toPlainString() + ") by ₹" + diff.toPlainString(),
                        "HIGH",
                        p.getId(),
                        mpId,
                        LocalDateTime.now()
                ));
            }

            // 2. Ghost Work on Completed Projects (Completed with > ₹50,000 budget but 0 totalPaid)
            if (status.contains("complete") && sanc.compareTo(new BigDecimal("50000")) > 0 && exp.compareTo(BigDecimal.ZERO) == 0) {
                detectedAnomalies.add(new Anomaly(
                        "ZERO_EXPENDITURE_COMPLETED",
                        "Project status is '" + p.getStatus() + "' with budget ₹" + sanc.toPlainString() + " but has ₹0 recorded payout",
                        "MEDIUM",
                        p.getId(),
                        mpId,
                        LocalDateTime.now()
                ));
            }

            // 3. Duplicate Work Descriptions proposed under the same MP
            if (p.getTitle() != null && mpId != null && sanc.compareTo(BigDecimal.ZERO) > 0) {
                String cleanDesc = p.getTitle().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (cleanDesc.length() >= 30) {
                    // Group by MP + First 60 characters of description + exact budget amount
                    String key = mpId + "::" + cleanDesc.substring(0, Math.min(cleanDesc.length(), 60)) + "::" + sanc.toPlainString();
                    duplicateClusterMap.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                }
            }
        }

        // Flag duplicate clusters
        for (List<Project> duplicates : duplicateClusterMap.values()) {
            if (duplicates.size() > 1) {
                for (Project dup : duplicates) {
                    detectedAnomalies.add(new Anomaly(
                            "DUPLICATE_WORK_PROPOSAL",
                            "Identical work proposal and budget (₹" + dup.getSanctionedAmount() + ") duplicated " + duplicates.size() + " times under this MP",
                            "HIGH",
                            dup.getId(),
                            dup.getMp() != null ? dup.getMp().getId() : null,
                            LocalDateTime.now()
                    ));
                }
            }
        }

        log.info("Scan Finished. Indexed {} anomalies into database.", detectedAnomalies.size());
        return anomalyRepository.saveAll(detectedAnomalies);
    }
}