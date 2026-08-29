package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {
    List<Anomaly> findByRelatedMpId(Long mpId);
    List<Anomaly> findByRelatedProjectId(Long projectId);
}