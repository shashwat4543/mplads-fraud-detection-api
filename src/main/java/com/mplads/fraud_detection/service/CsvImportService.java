package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.MP;
import com.mplads.fraud_detection.entity.Project;
import com.mplads.fraud_detection.repository.MPRepository;
import com.mplads.fraud_detection.repository.ProjectRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final MPRepository mpRepository;
    private final ProjectRepository projectRepository;

    public CsvImportService(MPRepository mpRepository, ProjectRepository projectRepository) {
        this.mpRepository = mpRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void importCsv(MultipartFile file) throws Exception {
        importCsv(file.getInputStream());
    }

    @Transactional
    public void importCsv(InputStream inputStream) throws Exception {
        Map<String, MP> mpCache = new HashMap<>();
        List<Project> batchList = new ArrayList<>();
        int totalImported = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            for (CSVRecord record : csvParser) {
                String mpName = getField(record, "mpName", "Unknown MP");
                String constituency = getField(record, "constituency", "Unknown");
                String state = getField(record, "state", "Unknown");
                String house = getField(record, "house", "Unknown");

                String key = mpName + "|" + constituency;
                MP mp = mpCache.computeIfAbsent(key, k ->
                        mpRepository.findByNameAndConstituency(mpName, constituency)
                                .orElseGet(() -> mpRepository.save(new MP(mpName, constituency, state, house)))
                );

                Project project = new Project();
                project.setProjectCode(getField(record, "workId", getField(record, "_id", "PRJ-" + UUID.randomUUID().toString().substring(0, 8))));

                String title = getField(record, "workDescription", "Work Proposal");
                project.setTitle(title);

                project.setCategory(getField(record, "workCategory", "General"));
                project.setAgencyName(getField(record, "ida", "District Collector IDA"));

                BigDecimal recAmount = parseBigDecimal(getField(record, "recommendedAmount", "0"));
                BigDecimal finAmount = parseBigDecimal(getField(record, "finalAmount", "0"));
                BigDecimal paidAmount = parseBigDecimal(getField(record, "totalPaid", "0"));

                project.setRecommendedAmount(recAmount);

                BigDecimal sanctioned = recAmount.compareTo(BigDecimal.ZERO) > 0 ? recAmount : finAmount;
                project.setSanctionedAmount(sanctioned);

                BigDecimal expenditure = paidAmount.compareTo(BigDecimal.ZERO) > 0 ? paidAmount : finAmount;
                project.setExpenditureAmount(expenditure);

                project.setRecommendationDate(parseIsoDate(getField(record, "recommendationDate", null)));
                project.setSanctionDate(parseIsoDate(getField(record, "date", null)));
                project.setCompletionDate(parseIsoDate(getField(record, "completedDate", null)));
                project.setStatus(getField(record, "status", "recommended").toLowerCase());
                project.setMp(mp);

                batchList.add(project);
                if (batchList.size() >= 1000) {
                    projectRepository.saveAll(batchList);
                    totalImported += batchList.size();
                    log.info("==> [IMPORT] {} rows imported so far...", totalImported);
                    batchList.clear();
                }
            }
            if (!batchList.isEmpty()) {
                projectRepository.saveAll(batchList);
                totalImported += batchList.size();
            }
            log.info("==> [IMPORT] Finished. Total rows imported: {}", totalImported);
        }
    }

    private String getField(CSVRecord record, String column, String fallback) {
        return record.isMapped(column) && record.get(column) != null && !record.get(column).isBlank()
                ? record.get(column).trim() : fallback;
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        try {
            String clean = val.replace(",", "").trim();
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseIsoDate(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            String datePart = val.trim().substring(0, 10);
            return LocalDate.parse(datePart);
        } catch (Exception e) {
            return null;
        }
    }
}