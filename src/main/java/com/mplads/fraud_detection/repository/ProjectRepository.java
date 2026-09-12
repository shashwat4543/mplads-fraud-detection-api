package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByMpId(Long mpId);
    Page<Project> findByMp_Id(Long mpId, Pageable pageable);
    int countByMp_Id(Long mpId);

    // FIX: pushes these computations to the database instead of loading all 53k+
    // Project entities into Java memory just to filter/count/distinct them.
    @Query("SELECT COUNT(p) FROM Project p WHERE p.expenditureAmount IS NOT NULL AND p.sanctionedAmount IS NOT NULL AND p.expenditureAmount > p.sanctionedAmount")
    long countCostOverruns();

    @Query("SELECT DISTINCT p.status FROM Project p WHERE p.status IS NOT NULL")
    List<String> findDistinctStatuses();

    // For constituency/MP-level analytics: one grouped query covering every MP's
    // project totals at once, instead of looping and calling countByMp_Id per MP
    // (that per-MP-loop pattern is what crashed the State Explorer feature earlier).
    @Query("SELECT p.mp.id, COUNT(p), COALESCE(SUM(p.sanctionedAmount), 0), COALESCE(SUM(p.expenditureAmount), 0) " +
            "FROM Project p WHERE p.mp IS NOT NULL GROUP BY p.mp.id")
    List<Object[]> aggregateByMp();
}