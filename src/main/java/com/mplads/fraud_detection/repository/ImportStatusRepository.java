package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportStatusRepository extends JpaRepository<ImportStatus, Long> {
}