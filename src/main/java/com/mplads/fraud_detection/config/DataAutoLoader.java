package com.mplads.fraud_detection.config;

import com.mplads.fraud_detection.repository.ProjectRepository;
import com.mplads.fraud_detection.service.AnomalyDetectionService;
import com.mplads.fraud_detection.service.CsvImportService;
import com.mplads.fraud_detection.service.SummaryImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class DataAutoLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataAutoLoader.class);

    private final CsvImportService csvImportService;
    private final SummaryImportService summaryImportService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final ProjectRepository projectRepository;

    public DataAutoLoader(CsvImportService csvImportService,
                          SummaryImportService summaryImportService,
                          AnomalyDetectionService anomalyDetectionService,
                          ProjectRepository projectRepository) {
        this.csvImportService = csvImportService;
        this.summaryImportService = summaryImportService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.projectRepository = projectRepository;
    }

    @Override
    public void run(String... args) {
        try {
            // FIX: with Postgres (Neon), data now genuinely persists across restarts.
            // Re-running the full 53k-row import + anomaly scan on every boot would
            // create duplicate rows and repeat the exact memory spike we've been
            // fighting on H2 — for no benefit, since the data hasn't changed.
            long existingProjects = projectRepository.count();
            if (existingProjects > 0) {
                log.info("==> [STARTUP] Found {} existing projects in database — skipping CSV import and anomaly scan. " +
                        "Use POST /api/mps/detect-anomalies to manually re-run detection after a data update.", existingProjects);
                return;
            }

            log.info("==> [STARTUP] Database is empty. Beginning automated data bootstrap...");

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

        } catch (Exception e) {
            log.error("==> [STARTUP] Automated loading failed: ", e);
        }
    }
}