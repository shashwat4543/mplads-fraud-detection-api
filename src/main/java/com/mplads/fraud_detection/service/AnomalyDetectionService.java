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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    // Matches ARCHITECTURE_AND_RULES.md Rule 1: Expenditure > Sanctioned * 1.10
    private static final BigDecimal OVERRUN_MULTIPLIER = new BigDecimal("1.10");

    // Matches ARCHITECTURE_AND_RULES.md Rule 3: sanctioned > 730 days ago, not completed
    private static final long CHRONIC_DELAY_DAYS = 730;

    // A repeated exact amount is only "suspicious" if it isn't a round figure
    // e.g. flags 4,99,999 reused across sites; ignores a legitimately common 5,00,000 budget
    private static final BigDecimal ROUND_NUMBER_UNIT = new BigDecimal("1000");
    private static final int MIN_UNIFORM_ALLOCATION_CLUSTER_SIZE = 3;

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
        Map<BigDecimal, List<Project>> amountClusterMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (Project p : allProjects) {
            BigDecimal exp = p.getExpenditureAmount() != null ? p.getExpenditureAmount() : BigDecimal.ZERO;
            BigDecimal sanc = p.getSanctionedAmount() != null ? p.getSanctionedAmount() : BigDecimal.ZERO;
            Long mpId = p.getMp() != null ? p.getMp().getId() : null;
            String status = p.getStatus() != null ? p.getStatus().trim().toLowerCase() : "";

            // Rule 1: Financial Budget Overrun (COST_OVERRUN_EXCEEDED)
            // Trigger: Expenditure > Sanctioned * 1.10
            if (sanc.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal threshold = sanc.multiply(OVERRUN_MULTIPLIER);
                if (exp.compareTo(threshold) > 0) {
                    BigDecimal diff = exp.subtract(sanc);
                    double pctOver = exp.subtract(sanc)
                            .divide(sanc, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")).doubleValue();
                    detectedAnomalies.add(new Anomaly(
                            "COST_OVERRUN_EXCEEDED",
                            String.format("Expenditure (₹%s) exceeds sanctioned amount (₹%s) by %.1f%% (threshold is 10%%)",
                                    exp.toPlainString(), sanc.toPlainString(), pctOver),
                            "HIGH",
                            p.getId(),
                            mpId,
                            LocalDateTime.now()
                    ));
                }
            }

            // Rule (proxy for doc's Rule 2 — no physical-progress field is available in source data):
            // Completed project with a meaningful budget but ₹0 recorded expenditure.
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

            // Rule 3: Extreme Project Stagnation & Delays (PROJECT_CHRONIC_DELAY)
            // Trigger: sanctioned > 730 days ago AND status != completed
            if (p.getSanctionDate() != null && !status.contains("complete")) {
                long daysSinceSanction = ChronoUnit.DAYS.between(p.getSanctionDate(), today);
                if (daysSinceSanction > CHRONIC_DELAY_DAYS) {
                    detectedAnomalies.add(new Anomaly(
                            "PROJECT_CHRONIC_DELAY",
                            String.format("Project sanctioned on %s (%d days ago) and still marked '%s'",
                                    p.getSanctionDate(), daysSinceSanction, p.getStatus()),
                            "MEDIUM",
                            p.getId(),
                            mpId,
                            LocalDateTime.now()
                    ));
                }
            }

            // Duplicate Work Proposal (near-identical title + identical budget under one MP)
            if (p.getTitle() != null && mpId != null && sanc.compareTo(BigDecimal.ZERO) > 0) {
                String cleanDesc = p.getTitle().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (cleanDesc.length() >= 30) {
                    String key = mpId + "::" + cleanDesc.substring(0, Math.min(cleanDesc.length(), 60)) + "::" + sanc.toPlainString();
                    duplicateClusterMap.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                }
            }

            // Rule 5: Repetitive Exact-Amount Allocations (SUSPICIOUS_UNIFORM_ALLOCATION)
            // Cluster by exact sanctioned amount across the *entire* dataset (not just one MP),
            // restricted to non-round figures so legitimate common budgets aren't flagged.
            if (sanc.compareTo(BigDecimal.ZERO) > 0 && sanc.remainder(ROUND_NUMBER_UNIT).compareTo(BigDecimal.ZERO) != 0) {
                amountClusterMap.computeIfAbsent(sanc, k -> new ArrayList<>()).add(p);
            }
        }

        // Flag duplicate title+amount clusters (existing behavior, unchanged)
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

        // Flag suspicious uniform allocation clusters (new — Rule 5)
        for (Map.Entry<BigDecimal, List<Project>> entry : amountClusterMap.entrySet()) {
            List<Project> cluster = entry.getValue();
            if (cluster.size() >= MIN_UNIFORM_ALLOCATION_CLUSTER_SIZE) {
                // Only meaningful if the cluster spans more than one MP/location —
                // repeated identical amounts from a single MP are already covered above.
                long distinctMps = cluster.stream()
                        .map(p -> p.getMp() != null ? p.getMp().getId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                if (distinctMps > 1) {
                    for (Project p : cluster) {
                        detectedAnomalies.add(new Anomaly(
                                "SUSPICIOUS_UNIFORM_ALLOCATION",
                                String.format("Non-standard amount ₹%s reused identically across %d projects spanning %d different MPs",
                                        entry.getKey().toPlainString(), cluster.size(), distinctMps),
                                "MEDIUM",
                                p.getId(),
                                p.getMp() != null ? p.getMp().getId() : null,
                                LocalDateTime.now()
                        ));
                    }
                }
            }
        }

        log.info("Scan Finished. Indexed {} anomalies into database.", detectedAnomalies.size());
        return anomalyRepository.saveAll(detectedAnomalies);
    }
}