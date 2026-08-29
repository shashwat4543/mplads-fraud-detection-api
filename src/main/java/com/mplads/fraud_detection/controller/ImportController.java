package com.mplads.fraud_detection.controller;

import com.mplads.fraud_detection.service.CsvImportService;
import com.mplads.fraud_detection.service.SummaryImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
public class ImportController {

    private final CsvImportService csvImportService;
    private final SummaryImportService summaryImportService;

    public ImportController(CsvImportService csvImportService, SummaryImportService summaryImportService) {
        this.csvImportService = csvImportService;
        this.summaryImportService = summaryImportService;
    }

    @PostMapping("/csv")
    public ResponseEntity<String> uploadProjectCsv(@RequestParam("file") MultipartFile file) {
        try {
            csvImportService.importCsv(file);
            return ResponseEntity.ok("Project CSV uploaded and ingested successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error importing project CSV: " + e.getMessage());
        }
    }

    @PostMapping("/summary")
    public ResponseEntity<String> uploadSummaryCsv(@RequestParam("file") MultipartFile file) {
        try {
            summaryImportService.importSummaryCsv(file);
            return ResponseEntity.ok("Summary CSV uploaded and ingested successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error importing summary CSV: " + e.getMessage());
        }
    }
}