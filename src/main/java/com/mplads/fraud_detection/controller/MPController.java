package com.mplads.fraud_detection.controller;

import com.mplads.fraud_detection.dto.MPDashboardDTO;
import com.mplads.fraud_detection.dto.MPDropdownDTO;
import com.mplads.fraud_detection.dto.ProjectAnomalyDTO;
import com.mplads.fraud_detection.entity.Anomaly;
import com.mplads.fraud_detection.entity.MP;
import com.mplads.fraud_detection.entity.Project;
import com.mplads.fraud_detection.repository.AnomalyRepository;
import com.mplads.fraud_detection.repository.MPRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import com.mplads.fraud_detection.service.AnomalyDetectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mps")
@CrossOrigin(origins = "*")
public class MPController {

    private final MPRepository mpRepository;
    private final ProjectRepository projectRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final AnomalyRepository anomalyRepository;

    public MPController(MPRepository mpRepository,
                        ProjectRepository projectRepository,
                        AnomalyDetectionService anomalyDetectionService,
                        AnomalyRepository anomalyRepository) {
        this.mpRepository = mpRepository;
        this.projectRepository = projectRepository;
        this.anomalyDetectionService = anomalyDetectionService;
        this.anomalyRepository = anomalyRepository;
    }

    // 1. Search MP by name or constituency
    @GetMapping("/search")
    public List<MPDropdownDTO> searchMPs(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return mpRepository.searchMPs(query.trim()).stream()
                .map(m -> new MPDropdownDTO(m.getId(), m.getName(), m.getConstituency(), m.getState(), m.getParty()))
                .collect(Collectors.toList());
    }

    // 2. MP Dashboard Summary (KPI Cards, Financials, Anomaly Counts)
    @GetMapping("/{mpId}/dashboard")
    public ResponseEntity<MPDashboardDTO> getMPDashboard(@PathVariable Long mpId) {
        return mpRepository.findById(mpId).map(mp -> {
            List<Anomaly> mpAnomalies = anomalyRepository.findByRelatedMpId(mpId);
            int totalWorks = projectRepository.countByMp_Id(mpId);
            long highCount = mpAnomalies.stream().filter(a -> "HIGH".equalsIgnoreCase(a.getSeverity())).count();
            long mediumCount = mpAnomalies.stream().filter(a -> "MEDIUM".equalsIgnoreCase(a.getSeverity())).count();

            return ResponseEntity.ok(new MPDashboardDTO(mp, totalWorks, mpAnomalies.size(), highCount, mediumCount));
        }).orElse(ResponseEntity.notFound().build());
    }
    // Get Paginated List of All MPs
    @GetMapping
    public ResponseEntity<Page<MP>> getAllMPs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return ResponseEntity.ok(mpRepository.findAll(pageable));
    }

    // Get Anomalies for a specific MP
    @GetMapping("/{mpId}/anomalies")
    public ResponseEntity<List<Anomaly>> getMPAnomalies(@PathVariable Long mpId) {
        return ResponseEntity.ok(anomalyRepository.findByRelatedMpId(mpId));
    }
    // 3. Paginated Projects with Anomaly Flags & Reasons
    @GetMapping("/{mpId}/projects")
    public ResponseEntity<Page<ProjectAnomalyDTO>> getPaginatedProjects(
            @PathVariable Long mpId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        Page<Project> projectPage = projectRepository.findByMp_Id(mpId, pageable);

        List<ProjectAnomalyDTO> dtoList = new ArrayList<>();
        for (Project p : projectPage.getContent()) {
            List<Anomaly> anomalies = anomalyRepository.findByRelatedProjectId(p.getId());
            dtoList.add(new ProjectAnomalyDTO(p, !anomalies.isEmpty(), anomalies));
        }

        Page<ProjectAnomalyDTO> responsePage = new PageImpl<>(dtoList, pageable, projectPage.getTotalElements());
        return ResponseEntity.ok(responsePage);
    }

    // 4. Trigger / Re-run Anomaly Engine
    @RequestMapping(value = "/detect-anomalies", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<List<Anomaly>> runDetection() {
        return ResponseEntity.ok(anomalyDetectionService.runRulesEngine());
    }

    // 5. Paginated Global Anomalies Feed
    @GetMapping("/anomalies")
    public ResponseEntity<Page<Anomaly>> getAllAnomalies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ruleCode) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return ResponseEntity.ok(anomalyRepository.findByFilters(severity, ruleCode, pageable));
    }
    @GetMapping("/debug-stats")
    public Map<String, Object> getDebugStats() {
        // FIX: previously called projectRepository.findAll() THREE times and
        // anomalyRepository.findAll() once — loading 165,000+ full entities into
        // Java memory on every single call to this endpoint. Since the frontend's
        // Landing Page hits this on load, that was a severe, repeatable memory
        // spike independent of the startup scan we already fixed. Every value
        // below is now computed at the database level instead.
        Page<Project> samplePage = projectRepository.findAll(PageRequest.of(0, 10));
        List<Project> sample = samplePage.getContent();

        long totalProjects = projectRepository.count();
        long overruns = projectRepository.countCostOverruns();
        List<String> sampleStatuses = projectRepository.findDistinctStatuses();

        Map<String, Long> anomalyTypeBreakdown = new LinkedHashMap<>();
        for (Object[] row : anomalyRepository.countByAnomalyType()) {
            anomalyTypeBreakdown.put((String) row[0], (Long) row[1]);
        }

        return Map.of(
                "totalProjectsInDB", totalProjects,
                "rawCostOverrunCount", overruns,
                "distinctStatusSample", sampleStatuses,
                "anomalyTypeBreakdown", anomalyTypeBreakdown,
                "firstSampleProject", sample.isEmpty() ? "DB IS EMPTY" : sample.get(0)
        );
    }
}