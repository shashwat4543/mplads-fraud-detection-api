package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.Project;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for RiskScoringService — no Spring context needed, since the
 * service has no external dependencies.
 */
class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService();

    private Project sampleProject() {
        Project p = new Project();
        p.setId(1L);
        p.setSanctionedAmount(new BigDecimal("400000"));
        p.setExpenditureAmount(new BigDecimal("520000")); // 30% overrun
        return p;
    }

    private Anomaly anomaly(String type, String description) {
        return new Anomaly(type, description, "HIGH", 1L, 1L, LocalDateTime.now());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reasonsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("reasons");
    }

    @Test
    void noAnomalies_returnsZeroScoreAndLow() {
        Map<String, Object> result = service.score(sampleProject(), List.of());
        assertEquals(0, result.get("riskScore"));
        assertEquals("LOW", result.get("riskLevel"));
        assertTrue(reasonsOf(result).isEmpty());
    }

    @Test
    void oneAnomaly_scoresItsWeight() {
        Map<String, Object> result = service.score(sampleProject(),
                List.of(anomaly("PROJECT_CHRONIC_DELAY", "Sanctioned 800 days ago, still recommended")));
        assertEquals(20, result.get("riskScore"));
        assertEquals("LOW", result.get("riskLevel"));
        assertEquals(1, reasonsOf(result).size());
    }

    @Test
    void multipleAnomalies_sumWeights() {
        Map<String, Object> result = service.score(sampleProject(), List.of(
                anomaly("PROJECT_CHRONIC_DELAY", "desc1"),
                anomaly("DUPLICATE_WORK_PROPOSAL", "desc2")
        ));
        assertEquals(45, result.get("riskScore")); // 20 + 25
        assertEquals("MEDIUM", result.get("riskLevel"));
    }

    @Test
    void highRiskProject_classifiesAsHigh() {
        Map<String, Object> result = service.score(sampleProject(), List.of(
                anomaly("COST_OVERRUN_EXCEEDED", "desc1"),
                anomaly("DUPLICATE_WORK_PROPOSAL", "desc2"),
                anomaly("PROJECT_CHRONIC_DELAY", "desc3")
        ));
        assertEquals(75, result.get("riskScore")); // 30 + 25 + 20
        assertEquals("HIGH", result.get("riskLevel"));
    }

    @Test
    void scoreIsCappedAt100() {
        Map<String, Object> result = service.score(sampleProject(), List.of(
                anomaly("COST_OVERRUN_EXCEEDED", "d1"),
                anomaly("ZERO_EXPENDITURE_COMPLETED", "d2"),
                anomaly("DUPLICATE_WORK_PROPOSAL", "d3"),
                anomaly("SUSPICIOUS_UNIFORM_ALLOCATION", "d4"),
                anomaly("PROJECT_CHRONIC_DELAY", "d5")
        ));
        // Raw sum would be 30+25+25+20+20 = 120, must cap at 100
        assertEquals(100, result.get("riskScore"));
        assertEquals("HIGH", result.get("riskLevel"));
    }

    @Test
    void correctLowMediumHighBoundaries() {
        // 20 -> LOW (<= 39)
        assertEquals("LOW", service.score(sampleProject(),
                List.of(anomaly("PROJECT_CHRONIC_DELAY", "d"))).get("riskLevel"));

        // 30 + 20 = 50 -> MEDIUM (40-69)
        assertEquals("MEDIUM", service.score(sampleProject(), List.of(
                anomaly("COST_OVERRUN_EXCEEDED", "d1"),
                anomaly("PROJECT_CHRONIC_DELAY", "d2")
        )).get("riskLevel"));

        // 20 + 25 + 25 = 70 -> HIGH (exact lower boundary, 70-100)
        assertEquals("HIGH", service.score(sampleProject(), List.of(
                anomaly("SUSPICIOUS_UNIFORM_ALLOCATION", "d1"),
                anomaly("DUPLICATE_WORK_PROPOSAL", "d2"),
                anomaly("ZERO_EXPENDITURE_COMPLETED", "d3")
        )).get("riskLevel"));
    }

    @Test
    void costOverrunExplanation_usesRealProjectValuesNotInventedEvidence() {
        Map<String, Object> result = service.score(sampleProject(),
                List.of(anomaly("COST_OVERRUN_EXCEEDED", "generic fallback description")));
        String explanation = (String) reasonsOf(result).get(0).get("explanation");
        assertTrue(explanation.contains("Sanctioned Amount"));
        assertTrue(explanation.contains("400000"));
        assertTrue(explanation.contains("Expenditure"));
        assertTrue(explanation.contains("520000"));
        assertTrue(explanation.contains("Overrun"));
        assertTrue(explanation.contains("30.0%"));
    }

    @Test
    void otherRuleTypes_reuseTheAnomalysOwnDescription() {
        Map<String, Object> result = service.score(sampleProject(),
                List.of(anomaly("PROJECT_CHRONIC_DELAY", "Sanctioned 900 days ago, still recommended")));
        assertEquals("Sanctioned 900 days ago, still recommended",
                reasonsOf(result).get(0).get("explanation"));
    }

    @Test
    void duplicateRuleTypesAreNotDoubleCounted() {
        Map<String, Object> result = service.score(sampleProject(), List.of(
                anomaly("DUPLICATE_WORK_PROPOSAL", "instance A"),
                anomaly("DUPLICATE_WORK_PROPOSAL", "instance B") // same rule type twice
        ));
        assertEquals(25, result.get("riskScore")); // counted once, not 50
        assertEquals(1, reasonsOf(result).size());
    }
}