package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import com.mplads.fraud_detection.repository.AnomalyRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final BigDecimal OVERRUN_MULTIPLIER = new BigDecimal("1.10");
    private static final long CHRONIC_DELAY_DAYS = 730;
    private static final BigDecimal ROUND_NUMBER_UNIT = new BigDecimal("1000");
    private static final int MIN_UNIFORM_ALLOCATION_CLUSTER_SIZE = 3;

    // FIX: bounds how many Project entities are ever held in memory at once,
    // instead of loading all 53k+ via findAll() in a single List.
    private static final int SCAN_PAGE_SIZE = 2000;

    // Lightweight, entity-free record for cross-project clustering. Storing this
    // instead of the actual Project entity means the cluster maps never keep
    // JPA-managed objects (and their nested MP graphs) alive for the whole scan.
    private record LightProject(Long id, Long mpId, String titleKey, BigDecimal amount) {}

    private final ProjectRepository projectRepository;
    private final AnomalyRepository anomalyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AnomalyDetectionService(ProjectRepository projectRepository,
                                   AnomalyRepository anomalyRepository) {
        this.projectRepository = projectRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional
    public List<Anomaly> runRulesEngine() {
        anomalyRepository.deleteAllInBatch();
        List<Anomaly> detectedAnomalies = new ArrayList<>();
        Map<String, List<LightProject>> duplicateClusterMap = new HashMap<>();
        Map<BigDecimal, List<LightProject>> amountClusterMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        long totalProjects = projectRepository.count();
        log.info("Starting Anomaly Scan on {} total projects (paged, {} per page)...", totalProjects, SCAN_PAGE_SIZE);

        if (totalProjects == 0) {
            log.warn("Database is EMPTY. Please import CSV first.");
            return detectedAnomalies;
        }

        int pageNum = 0;
        Page<Project> page;
        do {
            page = projectRepository.findAll(PageRequest.of(pageNum, SCAN_PAGE_SIZE));

            for (Project p : page.getContent()) {
                BigDecimal exp = p.getExpenditureAmount() != null ? p.getExpenditureAmount() : BigDecimal.ZERO;
                BigDecimal sanc = p.getSanctionedAmount() != null ? p.getSanctionedAmount() : BigDecimal.ZERO;
                Long mpId = p.getMp() != null ? p.getMp().getId() : null;
                String status = p.getStatus() != null ? p.getStatus().trim().toLowerCase() : "";

                // Rule 1: Financial Budget Overrun (COST_OVERRUN_EXCEEDED)
                if (sanc.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal threshold = sanc.multiply(OVERRUN_MULTIPLIER);
                    if (exp.compareTo(threshold) > 0) {
                        double pctOver = exp.subtract(sanc)
                                .divide(sanc, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100")).doubleValue();
                        detectedAnomalies.add(new Anomaly(
                                "COST_OVERRUN_EXCEEDED",
                                String.format("Expenditure (₹%s) exceeds sanctioned amount (₹%s) by %.1f%% (threshold is 10%%)",
                                        exp.toPlainString(), sanc.toPlainString(), pctOver),
                                "HIGH", p.getId(), mpId, LocalDateTime.now()
                        ));
                    }
                }

                // Proxy for doc's Rule 2 (no physical-progress field available in source data)
                if (status.contains("complete") && sanc.compareTo(new BigDecimal("50000")) > 0 && exp.compareTo(BigDecimal.ZERO) == 0) {
                    detectedAnomalies.add(new Anomaly(
                            "ZERO_EXPENDITURE_COMPLETED",
                            "Project status is '" + p.getStatus() + "' with budget ₹" + sanc.toPlainString() + " but has ₹0 recorded payout",
                            "MEDIUM", p.getId(), mpId, LocalDateTime.now()
                    ));
                }

                // Rule 3: Extreme Project Stagnation & Delays (PROJECT_CHRONIC_DELAY)
                if (p.getSanctionDate() != null && !status.contains("complete")) {
                    long daysSinceSanction = ChronoUnit.DAYS.between(p.getSanctionDate(), today);
                    if (daysSinceSanction > CHRONIC_DELAY_DAYS) {
                        detectedAnomalies.add(new Anomaly(
                                "PROJECT_CHRONIC_DELAY",
                                String.format("Project sanctioned on %s (%d days ago) and still marked '%s'",
                                        p.getSanctionDate(), daysSinceSanction, p.getStatus()),
                                "MEDIUM", p.getId(), mpId, LocalDateTime.now()
                        ));
                    }
                }

                // Collect lightweight data for cross-project clustering (Duplicate + Rule 5)
                // — never store the Project entity itself here.
                if (p.getTitle() != null && mpId != null && sanc.compareTo(BigDecimal.ZERO) > 0) {
                    String cleanDesc = p.getTitle().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                    if (cleanDesc.length() >= 30) {
                        String titleKey = cleanDesc.substring(0, Math.min(cleanDesc.length(), 60));
                        String key = mpId + "::" + titleKey + "::" + sanc.toPlainString();
                        duplicateClusterMap.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(new LightProject(p.getId(), mpId, titleKey, sanc));
                    }
                }
                if (sanc.compareTo(BigDecimal.ZERO) > 0 && sanc.remainder(ROUND_NUMBER_UNIT).compareTo(BigDecimal.ZERO) != 0) {
                    amountClusterMap.computeIfAbsent(sanc, k -> new ArrayList<>())
                            .add(new LightProject(p.getId(), mpId, null, sanc));
                }
            }

            // Release this page's entities from memory before loading the next page.
            entityManager.flush();
            entityManager.clear();
            pageNum++;
            log.info("Scanned page {} ({} projects processed so far)", pageNum, (long) pageNum * SCAN_PAGE_SIZE);
        } while (page.hasNext());

        // Flag duplicate title+amount clusters (now using lightweight records)
        for (List<LightProject> duplicates : duplicateClusterMap.values()) {
            if (duplicates.size() > 1) {
                for (LightProject dup : duplicates) {
                    detectedAnomalies.add(new Anomaly(
                            "DUPLICATE_WORK_PROPOSAL",
                            "Identical work proposal and budget (₹" + dup.amount() + ") duplicated " + duplicates.size() + " times under this MP",
                            "HIGH", dup.id(), dup.mpId(), LocalDateTime.now()
                    ));
                }
            }
        }

        // Flag suspicious uniform allocation clusters (Rule 5)
        for (Map.Entry<BigDecimal, List<LightProject>> entry : amountClusterMap.entrySet()) {
            List<LightProject> cluster = entry.getValue();
            if (cluster.size() >= MIN_UNIFORM_ALLOCATION_CLUSTER_SIZE) {
                long distinctMps = cluster.stream().map(LightProject::mpId).filter(Objects::nonNull).distinct().count();
                if (distinctMps > 1) {
                    for (LightProject p : cluster) {
                        detectedAnomalies.add(new Anomaly(
                                "SUSPICIOUS_UNIFORM_ALLOCATION",
                                String.format("Non-standard amount ₹%s reused identically across %d projects spanning %d different MPs",
                                        entry.getKey().toPlainString(), cluster.size(), distinctMps),
                                "MEDIUM", p.id(), p.mpId(), LocalDateTime.now()
                        ));
                    }
                }
            }
        }

        // Free the cluster maps explicitly before the save phase — they're no longer needed
        // and can hold a non-trivial number of small records for a 53k-row dataset.
        duplicateClusterMap.clear();
        amountClusterMap.clear();

        log.info("Scan Finished. {} anomalies detected. Saving in batches...", detectedAnomalies.size());

        List<Anomaly> saved = new ArrayList<>();
        int batchSize = 2000;
        for (int i = 0; i < detectedAnomalies.size(); i += batchSize) {
            List<Anomaly> batch = detectedAnomalies.subList(i, Math.min(i + batchSize, detectedAnomalies.size()));
            saved.addAll(anomalyRepository.saveAll(batch));
            entityManager.flush();
            entityManager.clear();
        }

        log.info("Scan Finished. Indexed {} anomalies into database.", saved.size());
        return saved;
    }
}