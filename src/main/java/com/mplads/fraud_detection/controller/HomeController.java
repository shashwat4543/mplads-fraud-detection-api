package com.mplads.fraud_detection.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> welcome() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ONLINE");
        response.put("service", "MPLADS Fund Anomaly & Fraud Detection Engine");
        response.put("version", "1.0.0");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("search_mps", "GET /api/mps/search?q={name}");
        endpoints.put("all_mps", "GET /api/mps?page=0&size=20");
        endpoints.put("anomalies_feed", "GET /api/mps/anomalies?page=0&size=20");
        endpoints.put("mp_details_and_anomalies", "GET /api/mps/{id}/anomalies");
        endpoints.put("csv_import", "POST /api/import/csv");

        response.put("available_endpoints", endpoints);
        response.put("documentation", "Refer to API_DOCUMENTATION.md for payload models and query parameters.");

        return ResponseEntity.ok(response);
    }
}