package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.Anomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    List<Anomaly> findByRelatedMpId(Long mpId);
    List<Anomaly> findByRelatedProjectId(Long projectId);

    @Query("SELECT a.anomalyType, COUNT(a) FROM Anomaly a GROUP BY a.anomalyType")
    List<Object[]> countByAnomalyType();

    // FIX: the frontend already sends severity/ruleCode as query params on the
    // global feed — the backend just wasn't reading them. Null-safe filter query
    // so passing neither still returns everything, matching prior behavior.
    @Query("SELECT a FROM Anomaly a WHERE " +
            "(:severity IS NULL OR a.severity = :severity) AND " +
            "(:ruleCode IS NULL OR a.anomalyType = :ruleCode)")
    Page<Anomaly> findByFilters(@Param("severity") String severity, @Param("ruleCode") String ruleCode, Pageable pageable);

    // One grouped query for constituency/MP analytics — avoids calling
    // findByRelatedMpId per MP in a loop (~800 separate queries), which is the
    // exact pattern that crashed the State Explorer feature earlier.
    @Query("SELECT a.relatedMpId, COUNT(a), SUM(CASE WHEN a.severity = 'HIGH' THEN 1L ELSE 0L END) " +
            "FROM Anomaly a WHERE a.relatedMpId IS NOT NULL GROUP BY a.relatedMpId")
    List<Object[]> aggregateByMp();
}