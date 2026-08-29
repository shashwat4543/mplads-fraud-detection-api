package com.mplads.fraud_detection.repository;

import com.mplads.fraud_detection.entity.MP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MPRepository extends JpaRepository<MP, Long> {

    Optional<MP> findByNameAndConstituency(String name, String constituency);

    @Query("SELECT m FROM MP m WHERE " +
            "LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(m.constituency) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(m.state) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<MP> searchMPs(@Param("query") String query);
}