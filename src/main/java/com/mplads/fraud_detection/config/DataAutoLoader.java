package com.mplads.fraud_detection.config;

import com.mplads.fraud_detection.service.AnomalyDetectionService;
import com.mplads.fraud_detection.service.CsvImportService;
import com.mplads.fraud_detection.service.SummaryImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
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
            log.info("Checking for CSV data files on boot...");

            // 1. Ingest Projects CSV (checks classpath resources first, then project root)
            InputStream projStream = getInputStream("data/all_india_mplads_projects.csv", "all_india_mplads_projects.csv");
            if (projStream != null) {
                try (projStream) {
                    csvImportService.importCsv(projStream);
                    log.info("Projects CSV auto-ingested successfully.");
                }
            } else {
                log.warn("Projects CSV not found in classpath (data/) or root directory.");
            }

            // 2. Ingest Summary CSV
            InputStream sumStream = getInputStream("data/result.csv", "result.csv");
            if (sumStream != null) {
                try (sumStream) {
                    summaryImportService.importSummaryCsv(sumStream);
                    log.info("Summary CSV auto-ingested successfully.");
                }
            } else {
                log.warn("Summary CSV not found in classpath (data/) or root directory.");
            }

            // 3. Run Rules Engine
            var anomalies = anomalyDetectionService.runRulesEngine();
            log.info("Startup scan complete. Indexed {} anomalies into H2 database.", anomalies.size());

        } catch (Exception e) {
            log.error("DataAutoLoader error during startup: {}", e.getMessage(), e);
        }
    }

    private InputStream getInputStream(String classpathRelPath, String rootFallbackPath) {
        try {
            ClassPathResource res = new ClassPathResource(classpathRelPath);
            if (res.exists()) {
                return res.getInputStream();
            }
            File rootFile = new File(rootFallbackPath);
            if (rootFile.exists()) {
                return new FileInputStream(rootFile);
            }
        } catch (Exception ignored) {}
        return null;
    }
}