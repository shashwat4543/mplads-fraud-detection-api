package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByMpId(Long mpId);
    Page<Project> findByMp_Id(Long mpId, Pageable pageable);
    int countByMp_Id(Long mpId);
}