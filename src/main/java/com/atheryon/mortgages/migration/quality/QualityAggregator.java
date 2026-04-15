package com.atheryon.mortgages.migration.quality;

import com.atheryon.mortgages.migration.entity.MigrationLoanStaging;
import com.atheryon.mortgages.migration.repository.MigrationLoanStagingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QualityAggregator {

    private static final Logger log = LoggerFactory.getLogger(QualityAggregator.class);

    private final MigrationLoanStagingRepository stagingRepository;
    private final QualityScorer qualityScorer;
    private final ObjectMapper objectMapper;

    public QualityAggregator(MigrationLoanStagingRepository stagingRepository,
                             QualityScorer qualityScorer,
                             ObjectMapper objectMapper) {
        this.stagingRepository = stagingRepository;
        this.qualityScorer = qualityScorer;
        this.objectMapper = objectMapper;
    }

    public JobQualityReport aggregate(UUID jobId) {
        List<MigrationLoanStaging> records = stagingRepository.findByJobId(jobId);

        if (records.isEmpty()) {
            return new JobQualityReport(
                jobId, 0, 0.0,
                new EnumMap<>(QualityDimension.class),
                Map.of("0-50", 0, "50-70", 0, "70-90", 0, "90-100", 0),
                List.of(),
                Map.of(),
                Map.of()
            );
        }

        Map<QualityDimension, List<Double>> allDimensionScores = new EnumMap<>(QualityDimension.class);
        for (QualityDimension dim : QualityDimension.values()) {
            allDimensionScores.put(dim, new ArrayList<>());
        }

        List<Double> compositeScores = new ArrayList<>();
        List<QualityIssue> allIssues = new ArrayList<>();
        Map<String, Integer> fieldPresenceCount = new LinkedHashMap<>();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Set<String> seenLoanRefs = new HashSet<>();
        int duplicateCount = 0;

        for (MigrationLoanStaging record : records) {
            // Count lifecycle states
            String state = record.getLifecycleState() != null ? record.getLifecycleState() : "STAGED";
            statusCounts.merge(state, 1, Integer::sum);

            // Parse mapped data
            Map<String, String> mappedData = parseMappedData(record);
            if (mappedData == null) {
                continue;
            }

            // Track field coverage
            for (Map.Entry<String, String> entry : mappedData.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    fieldPresenceCount.merge(entry.getKey(), 1, Integer::sum);
                } else {
                    fieldPresenceCount.putIfAbsent(entry.getKey(), 0);
                }
            }

            // Uniqueness check (duplicate loan references)
            String loanRef = mappedData.get("loanRef");
            if (loanRef != null && !loanRef.isBlank()) {
                if (!seenLoanRefs.add(loanRef)) {
                    duplicateCount++;
                    allIssues.add(new QualityIssue(
                        QualityDimension.UNIQUENESS, "loanRef",
                        "Duplicate loan reference: " + loanRef, "CRITICAL",
                        "Remove or merge duplicate records"
                    ));
                }
            }

            // Score the record
            QualityReport report = qualityScorer.scoreRecord(mappedData);
            compositeScores.add(report.compositeScore());
            allIssues.addAll(report.issues());

            for (QualityDimension dim : QualityDimension.values()) {
                Double score = report.dimensionScores().get(dim);
                if (score != null) {
                    allDimensionScores.get(dim).add(score);
                }
            }
        }

        // Compute uniqueness score at aggregate level
        int totalWithRef = seenLoanRefs.size() + duplicateCount;
        double uniquenessScore = totalWithRef > 0 ? (double) seenLoanRefs.size() / totalWithRef : 1.0;
        allDimensionScores.get(QualityDimension.UNIQUENESS).clear();
        allDimensionScores.get(QualityDimension.UNIQUENESS).add(uniquenessScore);

        // Dimension averages
        Map<QualityDimension, Double> dimensionAverages = new EnumMap<>(QualityDimension.class);
        for (QualityDimension dim : QualityDimension.values()) {
            List<Double> scores = allDimensionScores.get(dim);
            dimensionAverages.put(dim, scores.isEmpty() ? 0.0 : scores.stream().mapToDouble(d -> d).average().orElse(0.0));
        }

        // Average composite score
        double averageScore = compositeScores.stream().mapToDouble(d -> d).average().orElse(0.0);

        // Score distribution
        Map<String, Integer> scoreDistribution = new LinkedHashMap<>();
        scoreDistribution.put("0-50", 0);
        scoreDistribution.put("50-70", 0);
        scoreDistribution.put("70-90", 0);
        scoreDistribution.put("90-100", 0);
        for (double score : compositeScores) {
            double pct = score * 100.0;
            if (pct < 50) scoreDistribution.merge("0-50", 1, Integer::sum);
            else if (pct < 70) scoreDistribution.merge("50-70", 1, Integer::sum);
            else if (pct < 90) scoreDistribution.merge("70-90", 1, Integer::sum);
            else scoreDistribution.merge("90-100", 1, Integer::sum);
        }

        // Top 10 most common issues (group by field + message)
        List<JobQualityReport.IssueFrequency> topIssues = allIssues.stream()
            .collect(Collectors.groupingBy(
                issue -> issue.field() + "|" + issue.message() + "|" + issue.dimension(),
                Collectors.counting()
            ))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|", 3);
                return new JobQualityReport.IssueFrequency(
                    parts[0],
                    parts[1],
                    QualityDimension.valueOf(parts[2]),
                    entry.getValue().intValue()
                );
            })
            .toList();

        // Field coverage as percentage
        int totalRecords = records.size();
        Map<String, Double> fieldCoverage = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : fieldPresenceCount.entrySet()) {
            fieldCoverage.put(entry.getKey(), (double) entry.getValue() / totalRecords);
        }

        return new JobQualityReport(
            jobId,
            totalRecords,
            averageScore,
            dimensionAverages,
            scoreDistribution,
            topIssues,
            fieldCoverage,
            statusCounts
        );
    }

    private Map<String, String> parseMappedData(MigrationLoanStaging record) {
        String json = record.getMappedData();
        if (json == null || json.isBlank()) {
            json = record.getSourceData();
        }
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse mapped data for staging record {} in job {}: {}",
                record.getId(), record.getJobId(), e.getMessage());
            return null;
        }
    }
}
