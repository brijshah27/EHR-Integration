package com.trial.screener.report;

import com.trial.screener.model.CriterionStatus;
import com.trial.screener.model.EligibilityAssessment;
import com.trial.screener.model.EligibilityCriterion;
import com.trial.screener.model.EligibilityStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

        // Summary statistics
        long eligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.ELIGIBLE)
                .count();
        long notEligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.NOT_ELIGIBLE)
                .count();
        long potentiallyEligible = assessments.stream()
                .filter(a -> a.getOverallStatus() == EligibilityStatus.POTENTIALLY_ELIGIBLE)
                .count();

        report.append("SCREENING STATISTICS\n");
        report.append(SUB_SEPARATOR).append("\n");
        report.append("Total Patients Queried: ").append(assessments.size()).append("\n");
        report.append("  - ELIGIBLE: ").append(eligible).append("\n");
        report.append("  - NOT ELIGIBLE: ").append(notEligible).append("\n");
        report.append("  - POTENTIALLY ELIGIBLE: ").append(potentiallyEligible).append("\n");
        report.append("\n");

        // Summary of patients screened
        report.append("1. LIST OF PATIENTS SCREENED\n");
        report.append(SUB_SEPARATOR).append("\n");
        report.append("Total Patients: ").append(assessments.size()).append("\n");
        for (EligibilityAssessment assessment : assessments) {
            report.append("  - Patient ID: ").append(assessment.getPatientId())
                  .append(" | Status: ").append(formatStatus(assessment.getOverallStatus()))
                  .append("\n");
        }
        report.append("\n");

        // Detailed patient screening results
        report.append("2. DETAILED PATIENT ASSESSMENTS\n");
        report.append(SUB_SEPARATOR).append("\n\n");

        for (EligibilityAssessment assessment : assessments) {
            report.append(formatDetailedPatientAssessment(assessment));
            report.append("\n").append(SUB_SEPARATOR).append("\n\n");
        }

        // Overall Summary
        report.append(generateSummary(assessments));

        report.append(SEPARATOR).append("\n");

        return report.toString();
    }

    /**
     * Format detailed patient assessment showing eligibility, criteria matched/not matched, and missing data
     * @param assessment Patient eligibility assessment
     * @return Formatted patient assessment string
     */
    private String formatDetailedPatientAssessment(EligibilityAssessment assessment) {
        StringBuilder sb = new StringBuilder();

        // Patient header
        sb.append("Patient ID: ").append(assessment.getPatientId()).append("\n");
        sb.append("Eligibility: ").append(formatStatus(assessment.getOverallStatus()));

        if (assessment.getIneligibilityReason() != null && !assessment.getIneligibilityReason().isEmpty()) {
            sb.append("\nReason: ").append(assessment.getIneligibilityReason());
        }
        sb.append("\n\n");

        // Criteria Matched (MET)
        List<EligibilityCriterion> metCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getStatus() == CriterionStatus.MET)
                .toList();

        sb.append("Criteria Matched:\n");
        if (metCriteria.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (EligibilityCriterion criterion : metCriteria) {
                sb.append("  ✓ ").append(criterion.getName());
                if (criterion.getDetails() != null && !criterion.getDetails().isEmpty()) {
                    sb.append(" (").append(criterion.getDetails()).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        // Criteria Not Matched (NOT_MET)
        List<EligibilityCriterion> notMetCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getStatus() == CriterionStatus.NOT_MET)
                .toList();

        sb.append("Criteria Not Matched:\n");
        if (notMetCriteria.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (EligibilityCriterion criterion : notMetCriteria) {
                sb.append("  ✗ ").append(criterion.getName());
                if (criterion.getDetails() != null && !criterion.getDetails().isEmpty()) {
                    sb.append(" (").append(criterion.getDetails()).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        // Criteria Unknown
        List<EligibilityCriterion> unknownCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getStatus() == CriterionStatus.UNKNOWN)
                .toList();

        sb.append("Criteria Unknown:\n");
        if (unknownCriteria.isEmpty()) {
            sb.append("  None\n");
        } else {
            for (EligibilityCriterion criterion : unknownCriteria) {
                sb.append("  ? ").append(criterion.getName());
                if (criterion.getDetails() != null && !criterion.getDetails().isEmpty()) {
                    sb.append(" (").append(criterion.getDetails()).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        // Missing Data Elements
        sb.append("Missing Data Elements:\n");
        if (assessment.getMissingDataElements() == null || assessment.getMissingDataElements().isEmpty()) {
            sb.append("  None\n");
        } else {
            for (String missingData : assessment.getMissingDataElements()) {
                sb.append("  - ").append(missingData).append("\n");
            }
        }

        return sb.toString();
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


}
