package com.mplads.fraud_detection.controller;

import com.mplads.fraud_detection.entity.MP;
import com.mplads.fraud_detection.repository.AnomalyRepository;
import com.mplads.fraud_detection.repository.MPRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constituency/MP-level analytics for map and dashboard aggregation views.
 *
 * IMPORTANT DATA LIMITATION: there is no stable "constituency_id" in this
 * dataset, and constituency names are free-text (inconsistent casing, no
 * canonical spelling guaranteed). The "mpId" field is the only genuinely
 * stable identifier available — use it as the join key, not constituencyName.
 *
 * IMPORTANT: Rajya Sabha MPs do not represent a geographic constituency at
 * all (their `constituency` field may read e.g. "Sitting Rajya Sabha").
 * Every entry includes a `mappable` flag — false for these rows — so a
 * consuming map UI can exclude them from polygon rendering while still
 * showing them in list/table views.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final MPRepository mpRepository;
    private final ProjectRepository projectRepository;
    private final AnomalyRepository anomalyRepository;

    public AnalyticsController(MPRepository mpRepository,
                               ProjectRepository projectRepository,
                               AnomalyRepository anomalyRepository) {
        this.mpRepository = mpRepository;
        this.projectRepository = projectRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @GetMapping("/constituencies")
    public ResponseEntity<List<Map<String, Object>>> getConstituencyAnalytics(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String riskLevel) {

        // Two grouped aggregate queries total — NOT a per-MP loop. This is the
        // efficient pattern; do not replace with repeated per-MP repository calls.
        Map<Long, Object[]> projectAgg = projectRepository.aggregateByMp().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        Map<Long, Object[]> anomalyAgg = anomalyRepository.aggregateByMp().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        // ~800 MP rows — small, safe to load in full (unlike Project/Anomaly tables).
        List<MP> allMps = mpRepository.findAll();

        List<Map<String, Object>> result = new ArrayList<>();

        for (MP mp : allMps) {
            if (state != null && !state.equalsIgnoreCase(mp.getState())) {
                continue;
            }

            Object[] pRow = projectAgg.get(mp.getId());
            Object[] aRow = anomalyAgg.get(mp.getId());

            long projectCount = pRow != null ? (Long) pRow[1] : 0L;
            BigDecimal totalSanctioned = pRow != null ? (BigDecimal) pRow[2] : BigDecimal.ZERO;
            BigDecimal totalExpenditure = pRow != null ? (BigDecimal) pRow[3] : BigDecimal.ZERO;
            long anomalyCount = aRow != null ? (Long) aRow[1] : 0L;
            long highRiskCount = aRow != null ? (Long) aRow[2] : 0L;
            long mediumRiskCount = anomalyCount - highRiskCount;

            // Simple, transparent constituency-level aggregate score — NOT the
            // same calculation as the per-project RiskScoringService (that needs
            // individual Anomaly objects; this uses only the grouped counts
            // above, to stay efficient at this scale). Documented as distinct.
            int riskScore = (int) Math.min(100, highRiskCount * 15 + mediumRiskCount * 8);
            String level = riskScore <= 39 ? "LOW" : (riskScore <= 69 ? "MEDIUM" : "HIGH");

            if (riskLevel != null && !riskLevel.equalsIgnoreCase(level)) {
                continue;
            }

            String constituency = mp.getConstituency();
            boolean mappable = constituency != null
                    && !constituency.toLowerCase().contains("rajya sabha");

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("mpId", mp.getId());
            entry.put("constituencyName", constituency);
            entry.put("state", mp.getState());
            entry.put("mpName", mp.getName());
            entry.put("mappable", mappable); // false for Rajya Sabha / non-geographic entries
            entry.put("projectCount", projectCount);
            entry.put("totalSanctionedAmount", totalSanctioned);
            entry.put("totalExpenditure", totalExpenditure);
            entry.put("anomalyCount", anomalyCount);
            entry.put("highRiskCount", highRiskCount);
            entry.put("riskScore", riskScore);
            entry.put("riskLevel", level);

            result.add(entry);
        }

        return ResponseEntity.ok(result);
    }
}