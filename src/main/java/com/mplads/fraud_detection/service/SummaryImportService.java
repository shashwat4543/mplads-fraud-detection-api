package com.mplads.fraud_detection.service;

import com.mplads.fraud_detection.entity.MP;
import com.mplads.fraud_detection.repository.MPRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
public class SummaryImportService {

    private final MPRepository mpRepository;

    public SummaryImportService(MPRepository mpRepository) {
        this.mpRepository = mpRepository;
    }

    @Transactional
    public void importSummaryCsv(MultipartFile file) throws Exception {
        importSummaryCsv(file.getInputStream());
    }

    @Transactional
    public void importSummaryCsv(InputStream inputStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            for (CSVRecord record : csvParser) {
                String mpName = getField(record, "data__mpName", "Unknown MP");
                String constituency = getField(record, "data__constituency", "Unknown");

                MP mp = mpRepository.findByNameAndConstituency(mpName, constituency)
                        .orElseGet(() -> new MP(
                                mpName,
                                constituency,
                                getField(record, "data__state", "Unknown"),
                                getField(record, "data__party", getField(record, "data__house", "Unknown"))
                        ));

                mp.setAllocatedAmount(parseBigDecimal(getField(record, "data__totalAllocated", getField(record, "allocatedAmount", "0"))));
                mp.setTotalExpenditure(parseBigDecimal(getField(record, "data__totalExpenditure", getField(record, "totalExpenditure", "0"))));
                mp.setUtilizationPercentage(parseDouble(getField(record, "data__utilizationPercentage", getField(record, "utilizationPercentage", "0"))));
                mp.setCompletedWorksCount(parseInt(getField(record, "data__completedWorks", "0")));
                mp.setRecommendedWorksCount(parseInt(getField(record, "data__recommendedWorks", "0")));
                mp.setCompletionRate(parseDouble(getField(record, "data__completionPercentage", "0")));
                mp.setUnspentAmount(parseBigDecimal(getField(record, "data__unspentBalance", getField(record, "unspentAmount", "0"))));

                mpRepository.save(mp);
            }
        }
    }

    private String getField(CSVRecord record, String col, String fallback) {
        return record.isMapped(col) && record.get(col) != null && !record.get(col).isBlank() ? record.get(col).trim() : fallback;
    }

    private BigDecimal parseBigDecimal(String val) {
        try { return new BigDecimal(val.replaceAll("[^0-9.]", "")); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Double parseDouble(String val) {
        try { return Double.parseDouble(val.replaceAll("[^0-9.]", "")); } catch (Exception e) { return 0.0; }
    }

    private Integer parseInt(String val) {
        try { return Integer.parseInt(val.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }
}