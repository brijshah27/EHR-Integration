package com.trial.screener.report;

import com.trial.screener.model.CriterionStatus;
import com.trial.screener.model.CriterionType;
import com.trial.screener.model.EligibilityAssessment;
import com.trial.screener.model.EligibilityCriterion;
import com.trial.screener.model.EligibilityStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates formatted eligibility screening reports
 */
public class ReportGenerator {

    private static final String SEPARATOR = "================================================================================";
    private static final String SUB_SEPARATOR = "--------------------------------------------------------------------------------";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generate complete screening report for all assessed patients
     * @param assessments List of patient eligibility assessments
     * @return Formatted report string
     */
    public String generateReport(List<EligibilityAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return "No patients assessed.";
        }

        StringBuilder report = new StringBuilder();

        // Header
        report.append(SEPARATOR).append("\n");
        report.append("CLINICAL TRIAL ELIGIBILITY SCREENING REPORT\n");
        report.append("Trial: Phase II Advanced NSCLC Study\n");
        report.append("Date: ").append(LocalDate.now().format(DATE_FORMATTER)).append("\n");
        report.append(SEPARATOR).append("\n\n");

        // Patient screening results
        report.append("PATIENT SCREENING RESULTS\n");
        report.append(SUB_SEPARATOR).append("\n\n");

        for (EligibilityAssessment assessment : assessments) {
            report.append(formatPatientAssessment(assessment));
            report.append("\n").append(SUB_SEPARATOR).append("\n\n");
        }

        // Summary
        report.append(generateSummary(assessments));

        // Missing data summary
        report.append("\n");
        report.append(generateMissingDataSummary(assessments));

        report.append(SEPARATOR).append("\n");

        return report.toString();
    }

    /**
     * Format individual patient assessment with criteria details
     * @param assessment Patient eligibility assessment
     * @return Formatted patient assessment string
     */
    private String formatPatientAssessment(EligibilityAssessment assessment) {
        StringBuilder sb = new StringBuilder();

        // Patient header
        sb.append("Patient ID: ").append(assessment.getPatientId()).append("\n");
        sb.append("Overall Status: ").append(formatStatus(assessment.getOverallStatus()));

        if (assessment.getIneligibilityReason() != null && !assessment.getIneligibilityReason().isEmpty()) {
            sb.append("\nReason: ").append(assessment.getIneligibilityReason());
        }
        sb.append("\n\n");

        // Inclusion criteria
        List<EligibilityCriterion> inclusionCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getType() == CriterionType.INCLUSION)
                .toList();

        if (!inclusionCriteria.isEmpty()) {
            sb.append("Inclusion Criteria:\n");
            for (EligibilityCriterion criterion : inclusionCriteria) {
                sb.append(formatCriterion(criterion));
            }
            sb.append("\n");
        }

        // Exclusion criteria
        List<EligibilityCriterion> exclusionCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getType() == CriterionType.EXCLUSION)
                .toList();

        if (!exclusionCriteria.isEmpty()) {
            sb.append("Exclusion Criteria:\n");
            for (EligibilityCriterion criterion : exclusionCriteria) {
                sb.append(formatCriterion(criterion));
            }
            sb.append("\n");
        }

        // Missing data
        if (assessment.getMissingDataElements() != null && !assessment.getMissingDataElements().isEmpty()) {
            sb.append("Missing Data: ");
            sb.append(String.join(", ", assessment.getMissingDataElements()));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format a single criterion with status symbol and details
     * @param criterion Eligibility criterion
     * @return Formatted criterion string
     */
    private String formatCriterion(EligibilityCriterion criterion) {
        StringBuilder sb = new StringBuilder();
        
        // Status symbol
        String symbol = getStatusSymbol(criterion.getStatus());
        sb.append("  ").append(symbol).append(" ");
        
        // Criterion name
        sb.append(criterion.getName()).append(": ");
        
        // Status
        sb.append(criterion.getStatus());
        
        // Details
        if (criterion.getDetails() != null && !criterion.getDetails().isEmpty()) {
            sb.append(" (").append(criterion.getDetails()).append(")");
        }
        
        sb.append("\n");
        
        return sb.toString();
    }

    /**
     * Get status symbol for criterion status
     * @param status Criterion status
     * @return Symbol string
     */
    private String getStatusSymbol(CriterionStatus status) {
        return switch (status) {
            case MET -> "✓";
            case NOT_MET -> "✗";
            case UNKNOWN -> "?";
            default -> " ";
        };
    }

    /**
     * Format eligibility status for display
     * @param status Eligibility status
     * @return Formatted status string
     */
    private String formatStatus(EligibilityStatus status) {
        return switch (status) {
            case ELIGIBLE -> "ELIGIBLE";
            case NOT_ELIGIBLE -> "NOT ELIGIBLE";
            case POTENTIALLY_ELIGIBLE -> "POTENTIALLY ELIGIBLE - DATA MISSING";
            default -> "UNKNOWN";
        };
    }

    /**
     * Generate summary statistics for all assessments
     * @param assessments List of patient eligibility assessments
     * @return Formatted summary string
     */
    private String generateSummary(List<EligibilityAssessment> assessments) {
        StringBuilder sb = new StringBuilder();

        sb.append(SEPARATOR).append("\n");
        sb.append("SUMMARY\n");
        sb.append(SEPARATOR).append("\n\n");

        int totalScreened = assessments.size();
        long eligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.ELIGIBLE)
                .count();
        long notEligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.NOT_ELIGIBLE)
                .count();
        long potentiallyEligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.POTENTIALLY_ELIGIBLE)
                .count();

        sb.append("Total Patients Screened: ").append(totalScreened).append("\n");
        sb.append("  - Eligible: ").append(eligible).append("\n");
        sb.append("  - Not Eligible: ").append(notEligible).append("\n");
        sb.append("  - Potentially Eligible (Data Missing): ").append(potentiallyEligible).append("\n");

        return sb.toString();
    }

    /**
     * Generate summary of missing data elements across all patients
     * @param assessments List of patient eligibility assessments
     * @return Formatted missing data summary string
     */
    private String generateMissingDataSummary(List<EligibilityAssessment> assessments) {
        StringBuilder sb = new StringBuilder();

        // Count occurrences of each missing data element
        Map<String, Integer> missingDataCounts = new HashMap<>();
        
        for (EligibilityAssessment assessment : assessments) {
            if (assessment.getMissingDataElements() != null) {
                for (String missingData : assessment.getMissingDataElements()) {
                    missingDataCounts.put(missingData, 
                        missingDataCounts.getOrDefault(missingData, 0) + 1);
                }
            }
        }

        if (missingDataCounts.isEmpty()) {
            return "";
        }

        sb.append("\nCommon Missing Data Elements:\n");
        
        // Sort by count (descending) and display
        missingDataCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .forEach(entry -> {
                    sb.append("  - ").append(entry.getKey())
                      .append(": ").append(entry.getValue())
                      .append(entry.getValue() == 1 ? " patient" : " patients")
                      .append("\n");
                });

        return sb.toString();
    }
}
