package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Computes a per-project 0–100 investigation-priority score from the anomaly
 * signals already produced by AnomalyDetectionService.
 *
 * This score represents investigation priority, NOT a fraud determination or
 * a probabilistic "fraud percentage". A HIGH score means "this project shows
 * multiple/serious rule-based signals and warrants human review" — it never
 * means "this project is proven fraudulent".
 *
 * Returns plain Map<String, Object> rather than a dedicated DTO class,
 * consistent with how getDebugStats() already builds its response elsewhere
 * in this codebase.
 */
@Service
public class RiskScoringService {

    // Centralized weights — how much each occurrence of a given rule type
    // contributes to the composite score. Change these here, not inline
    // elsewhere in the codebase.
    private static final Map<String, Integer> RULE_WEIGHTS = Map.of(
            "COST_OVERRUN_EXCEEDED", 30,
            "ZERO_EXPENDITURE_COMPLETED", 25,
            "DUPLICATE_WORK_PROPOSAL", 25,
            "SUSPICIOUS_UNIFORM_ALLOCATION", 20,
            "PROJECT_CHRONIC_DELAY", 20
    );

    // Fallback weight for any rule type not explicitly listed above
    // (keeps this forward-compatible with future rules without a code change).
    private static final int DEFAULT_WEIGHT = 15;

    // Centralized risk level thresholds.
    private static final int LOW_MAX = 39;
    private static final int MEDIUM_MAX = 69;

    public Map<String, Object> score(Project project, List<Anomaly> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("riskScore", 0);
            result.put("riskLevel", "LOW");
            result.put("reasons", List.of());
            return result;
        }

        // Avoid double-counting: if the same rule type fired more than once for
        // this project, count it only once (take the first instance found).
        Map<String, Anomaly> distinctByRule = new LinkedHashMap<>();
        for (Anomaly a : anomalies) {
            distinctByRule.putIfAbsent(a.getAnomalyType(), a);
        }

        List<Map<String, Object>> reasons = new ArrayList<>();
        int total = 0;

        for (Anomaly a : distinctByRule.values()) {
            int weight = RULE_WEIGHTS.getOrDefault(a.getAnomalyType(), DEFAULT_WEIGHT);
            total += weight;

            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("rule", a.getAnomalyType());
            reason.put("impact", weight);
            reason.put("explanation", buildExplanation(project, a));
            reasons.add(reason);
        }

        int score = Math.min(100, total);
        String level = score <= LOW_MAX ? "LOW" : (score <= MEDIUM_MAX ? "MEDIUM" : "HIGH");

        // Most impactful reason first — more useful reading order for "why flagged".
        reasons.sort((r1, r2) -> Integer.compare((int) r2.get("impact"), (int) r1.get("impact")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskScore", score);
        result.put("riskLevel", level);
        result.put("reasons", reasons);
        return result;
    }

    private String buildExplanation(Project project, Anomaly anomaly) {
        // COST_OVERRUN_EXCEEDED has real numeric evidence available on the
        // Project entity — build a concrete, non-invented explanation from it.
        if ("COST_OVERRUN_EXCEEDED".equals(anomaly.getAnomalyType())
                && project.getSanctionedAmount() != null
                && project.getExpenditureAmount() != null
                && project.getSanctionedAmount().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal sanctioned = project.getSanctionedAmount();
            BigDecimal expenditure = project.getExpenditureAmount();
            double overrunPct = expenditure.subtract(sanctioned)
                    .divide(sanctioned, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            return String.format("Sanctioned Amount: ₹%s | Expenditure: ₹%s | Overrun: %.1f%%",
                    sanctioned.toPlainString(), expenditure.toPlainString(), overrunPct);
        }

        // For every other rule, the Anomaly's own description already contains
        // the real evidence generated at detection time — reuse it rather than
        // inventing a separate explanation format not backed by stored data.
        return anomaly.getDescription();
    }
}