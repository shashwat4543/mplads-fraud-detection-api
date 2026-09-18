package com.mplads.fraud_detection.config;

import com.mplads.fraud_detection.entity.ImportStatus;
import com.mplads.fraud_detection.repository.ImportStatusRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import com.mplads.fraud_detection.service.AnomalyDetectionService;
import com.mplads.fraud_detection.service.CsvImportService;
import com.mplads.fraud_detection.service.SummaryImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;

@Component
public class DataAutoLoader {

    private static final Logger log = LoggerFactory.getLogger(DataAutoLoader.class);

    private final CsvImportService csvImportService;
    private final SummaryImportService summaryImportService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final ProjectRepository projectRepository;
    private final ImportStatusRepository importStatusRepository;

    public DataAutoLoader(CsvImportService csvImportService,
                          SummaryImportService summaryImportService,
                          AnomalyDetectionService anomalyDetectionService,
                          ProjectRepository projectRepository,
                          ImportStatusRepository importStatusRepository) {
        this.csvImportService = csvImportService;
        this.summaryImportService = summaryImportService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.projectRepository = projectRepository;
        this.importStatusRepository = importStatusRepository;
    }

    // Runs AFTER the app is fully up (port bound, Render already sees it as live),
    // and off the main thread — so a redeploy/restart signal arriving mid-import
    // no longer races with Render's startup health check.
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            ImportStatus status = importStatusRepository.findById(1L).orElseGet(ImportStatus::new);

            if (status.isCompleted()) {
                log.info("==> [STARTUP] Import already completed on {}. Skipping bootstrap. " +
                                "Use POST /api/mps/detect-anomalies to re-run detection after a data update.",
                        status.getCompletedAt());
                return;
            }

            log.info("==> [STARTUP] Import not yet completed. Beginning automated data bootstrap...");

            ClassPathResource projectResource = new ClassPathResource("data/all_india_mplads_projects.csv");
            if (projectResource.exists()) {
                try (InputStream is = projectResource.getInputStream()) {
                    csvImportService.importCsv(is);
                    log.info("==> [STARTUP] Projects CSV imported successfully.");
                }
            } else {
                log.warn("==> [STARTUP] data/all_india_mplads_projects.csv not found.");
            }

            ClassPathResource summaryResource = new ClassPathResource("data/result.csv");
            if (summaryResource.exists()) {
                try (InputStream is = summaryResource.getInputStream()) {
                    summaryImportService.importSummaryCsv(is);
                    log.info("==> [STARTUP] Summary CSV imported successfully.");
                }
            } else {
                log.warn("==> [STARTUP] data/result.csv not found.");
            }

            log.info("==> [STARTUP] Running Anomaly Detection scan...");
            anomalyDetectionService.runRulesEngine();
            log.info("==> [STARTUP] Anomaly Scan complete.");

            status.setCompleted(true);
            status.setCompletedAt(Instant.now());
            importStatusRepository.save(status);
            log.info("==> [STARTUP] Bootstrap fully completed and marked done. Total projects: {}",
                    projectRepository.count());

        } catch (Exception e) {
            log.error("==> [STARTUP] Automated loading failed. Will retry on next restart " +
                    "since completion was not marked: ", e);
        }
    }
}