package com.mplads.fraud_detection.config;

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

    public DataAutoLoader(CsvImportService csvImportService,
                          SummaryImportService summaryImportService,
                          AnomalyDetectionService anomalyDetectionService) {
        this.csvImportService = csvImportService;
        this.summaryImportService = summaryImportService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("==> [STARTUP] Beginning automated data bootstrap...");

            // 1. Load Main Projects CSV from resources/data/
            ClassPathResource projectResource = new ClassPathResource("data/all_india_mplads_projects.csv");
            if (projectResource.exists()) {
                try (InputStream is = projectResource.getInputStream()) {
                    csvImportService.importCsv(is); // Calls importCsv(InputStream)
                    log.info("==> [STARTUP] Projects CSV imported successfully.");
                }
            } else {
                log.warn("==> [STARTUP] data/all_india_mplads_projects.csv not found.");
            }

            // 2. Load Summary CSV from resources/data/
            ClassPathResource summaryResource = new ClassPathResource("data/result.csv");
            if (summaryResource.exists()) {
                try (InputStream is = summaryResource.getInputStream()) {
                    summaryImportService.importSummaryCsv(is); // Calls summary import service
                    log.info("==> [STARTUP] Summary CSV imported successfully.");
                }
            } else {
                log.warn("==> [STARTUP] data/result.csv not found.");
            }

            // 3. Trigger Anomaly Rules Engine
            log.info("==> [STARTUP] Running Anomaly Detection scan...");
            anomalyDetectionService.runRulesEngine(); // Calls detectAnomalies() or runAnomalyDetection()
            log.info("==> [STARTUP] Anomaly Scan complete.");

        } catch (Exception e) {
            log.error("==> [STARTUP] Automated loading failed: ", e);
        }
    }
}