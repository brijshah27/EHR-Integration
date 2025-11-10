package com.trial.screener.report;

import com.trial.screener.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReportGenerator
 */
public class ReportGeneratorTest {

    private ReportGenerator reportGenerator;

    @BeforeEach
    public void setUp() {
        reportGenerator = new ReportGenerator();
    }

    // ========== formatPatientAssessment Tests ==========

    @Test
    public void testGenerateReport_EligiblePatient() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-123");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertNotNull(report);
        assertTrue(report.contains("Patient ID: patient-123"));
        assertTrue(report.contains("Eligibility: ELIGIBLE"));
        assertTrue(report.contains("✓"));
        assertTrue(report.contains("Criteria Matched:"));
        assertFalse(report.contains("Reason:"));
    }

    @Test
    public void testGenerateReport_NotEligiblePatient() {
        EligibilityAssessment assessment = createNotEligibleAssessment("patient-456");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertNotNull(report);
        assertTrue(report.contains("Patient ID: patient-456"));
        assertTrue(report.contains("Eligibility: NOT ELIGIBLE"));
        assertTrue(report.contains("Reason: Age below 18"));
        assertTrue(report.contains("Criteria Not Matched:"));
        assertTrue(report.contains("✗"));
    }

    @Test
    public void testGenerateReport_PotentiallyEligiblePatient() {
        EligibilityAssessment assessment = createPotentiallyEligibleAssessment("patient-789");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertNotNull(report);
        assertTrue(report.contains("Patient ID: patient-789"));
        assertTrue(report.contains("Eligibility: POTENTIALLY ELIGIBLE - DATA MISSING"));
        assertTrue(report.contains("?"));
        assertTrue(report.contains("Criteria Unknown:"));
        assertTrue(report.contains("Missing Data Elements:"));
        assertTrue(report.contains("ECOG performance status"));
    }

    @Test
    public void testGenerateReport_InclusionAndExclusionCriteria() {
        EligibilityAssessment assessment = new EligibilityAssessment("patient-100");
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 50 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "No Prior Therapy", CriterionType.EXCLUSION, CriterionStatus.MET, "No prior therapy"));
        
        assessment.determineOverallStatus();
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertTrue(report.contains("Criteria Matched:"));
        assertTrue(report.contains("Age ≥18 years"));
        assertTrue(report.contains("No Prior Therapy"));
    }

    // ========== generateSummary Tests ==========

    @Test
    public void testGenerateSummary_SinglePatient() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-1");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertTrue(report.contains("SUMMARY"));
        assertTrue(report.contains("Total Patients Screened: 1"));
        assertTrue(report.contains("Eligible: 1"));
        assertTrue(report.contains("Not Eligible: 0"));
        assertTrue(report.contains("Potentially Eligible (Data Missing): 0"));
    }

    @Test
    public void testGenerateSummary_MultiplePatients() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        assessments.add(createEligibleAssessment("patient-1"));
        assessments.add(createEligibleAssessment("patient-2"));
        assessments.add(createNotEligibleAssessment("patient-3"));
        assessments.add(createNotEligibleAssessment("patient-4"));
        assessments.add(createNotEligibleAssessment("patient-5"));
        assessments.add(createPotentiallyEligibleAssessment("patient-6"));
        
        String report = reportGenerator.generateReport(assessments);
        
        assertTrue(report.contains("Total Patients Screened: 6"));
        assertTrue(report.contains("Eligible: 2"));
        assertTrue(report.contains("Not Eligible: 3"));
        assertTrue(report.contains("Potentially Eligible (Data Missing): 1"));
    }

    @Test
    public void testGenerateSummary_AllEligible() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        assessments.add(createEligibleAssessment("patient-1"));
        assessments.add(createEligibleAssessment("patient-2"));
        assessments.add(createEligibleAssessment("patient-3"));
        
        String report = reportGenerator.generateReport(assessments);
        
        assertTrue(report.contains("Total Patients Screened: 3"));
        assertTrue(report.contains("Eligible: 3"));
        assertTrue(report.contains("Not Eligible: 0"));
    }

    @Test
    public void testGenerateSummary_AllNotEligible() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        assessments.add(createNotEligibleAssessment("patient-1"));
        assessments.add(createNotEligibleAssessment("patient-2"));
        
        String report = reportGenerator.generateReport(assessments);
        
        assertTrue(report.contains("Total Patients Screened: 2"));
        assertTrue(report.contains("Eligible: 0"));
        assertTrue(report.contains("Not Eligible: 2"));
    }

    // ========== generateMissingDataSummary Tests ==========

    @Test
    public void testGenerateMissingDataSummary_SingleMissingElement() {
        EligibilityAssessment assessment = new EligibilityAssessment("patient-1");
        EligibilityCriterion criterion = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
        criterion.addMissingData("ECOG performance status");
        assessment.addCriterion(criterion);
        assessment.determineOverallStatus();
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        // The new format shows missing data in the patient section
        assertTrue(report.contains("Missing Data Elements:"));
        assertTrue(report.contains("ECOG performance status"));
    }

    @Test
    public void testGenerateMissingDataSummary_MultipleMissingElements() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        
        // Patient 1 - missing ECOG
        EligibilityAssessment assessment1 = new EligibilityAssessment("patient-1");
        EligibilityCriterion criterion1 = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
        criterion1.addMissingData("ECOG performance status");
        assessment1.addCriterion(criterion1);
        assessment1.determineOverallStatus();
        
        // Patient 2 - missing ECOG and Hemoglobin
        EligibilityAssessment assessment2 = new EligibilityAssessment("patient-2");
        EligibilityCriterion criterion2a = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
        criterion2a.addMissingData("ECOG performance status");
        assessment2.addCriterion(criterion2a);
        EligibilityCriterion criterion2b = new EligibilityCriterion(
            "Hemoglobin", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not available");
        criterion2b.addMissingData("Hemoglobin lab result");
        assessment2.addCriterion(criterion2b);
        assessment2.determineOverallStatus();
        
        // Patient 3 - missing Hemoglobin
        EligibilityAssessment assessment3 = new EligibilityAssessment("patient-3");
        EligibilityCriterion criterion3 = new EligibilityCriterion(
            "Hemoglobin", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not available");
        criterion3.addMissingData("Hemoglobin lab result");
        assessment3.addCriterion(criterion3);
        assessment3.determineOverallStatus();
        
        assessments.add(assessment1);
        assessments.add(assessment2);
        assessments.add(assessment3);
        
        String report = reportGenerator.generateReport(assessments);
        
        // The new format shows missing data for each patient
        assertTrue(report.contains("Missing Data Elements:"));
        assertTrue(report.contains("ECOG performance status"));
        assertTrue(report.contains("Hemoglobin lab result"));
    }

    @Test
    public void testGenerateMissingDataSummary_NoMissingData() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-1");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertFalse(report.contains("Common Missing Data Elements:"));
    }

    @Test
    public void testGenerateMissingDataSummary_SortedByCount() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        
        // Create 3 patients missing ECOG
        for (int i = 1; i <= 3; i++) {
            EligibilityAssessment assessment = new EligibilityAssessment("patient-" + i);
            EligibilityCriterion criterion = new EligibilityCriterion(
                "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
            criterion.addMissingData("ECOG performance status");
            assessment.addCriterion(criterion);
            assessment.determineOverallStatus();
            assessments.add(assessment);
        }
        
        // Create 1 patient missing Hemoglobin
        EligibilityAssessment assessment = new EligibilityAssessment("patient-4");
        EligibilityCriterion criterion = new EligibilityCriterion(
            "Hemoglobin", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not available");
        criterion.addMissingData("Hemoglobin lab result");
        assessment.addCriterion(criterion);
        assessment.determineOverallStatus();
        assessments.add(assessment);
        
        String report = reportGenerator.generateReport(assessments);
        
        // The new format shows missing data for each patient
        assertTrue(report.contains("ECOG performance status"));
        assertTrue(report.contains("Hemoglobin lab result"));
    }

    // ========== Report Formatting Tests ==========

    @Test
    public void testReportFormatting_ContainsHeader() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-1");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertTrue(report.contains("CLINICAL TRIAL ELIGIBILITY SCREENING REPORT"));
        assertTrue(report.contains("Trial: Phase II Advanced NSCLC Study"));
        assertTrue(report.contains("Date:"));
    }

    @Test
    public void testReportFormatting_ContainsSeparators() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-1");
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertTrue(report.contains("================================================================================"));
        assertTrue(report.contains("--------------------------------------------------------------------------------"));
    }

    @Test
    public void testReportFormatting_SpecialCharacters() {
        EligibilityAssessment assessment = new EligibilityAssessment("patient-1");
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 50 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "Hemoglobin ≥9.0 g/dL", CriterionType.INCLUSION, CriterionStatus.NOT_MET, "Hemoglobin: 8.5 g/dL"));
        
        assessment.determineOverallStatus();
        
        String report = reportGenerator.generateReport(List.of(assessment));
        
        assertTrue(report.contains("✓"));
        assertTrue(report.contains("✗"));
        assertTrue(report.contains("≥"));
    }

    @Test
    public void testGenerateReport_EmptyList() {
        String report = reportGenerator.generateReport(new ArrayList<>());
        
        assertEquals("No patients assessed.", report);
    }

    @Test
    public void testGenerateReport_NullList() {
        String report = reportGenerator.generateReport(null);
        
        assertEquals("No patients assessed.", report);
    }

    // ========== Structured Report Tests ==========

    @Test
    public void testGenerateStructuredReport_SinglePatient() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-123");
        
        String report = reportGenerator.generateStructuredReport(List.of(assessment));
        
        assertNotNull(report);
        assertTrue(report.contains("\"patient-123\""));
        assertTrue(report.contains("\"eligibility\": \"ELIGIBLE\""));
        assertTrue(report.contains("\"criteriasMatched\""));
        assertTrue(report.contains("\"criteriasNotMatched\""));
        assertTrue(report.contains("\"criteriasUnknown\""));
        assertTrue(report.contains("\"missingDataElements\""));
    }

    @Test
    public void testGenerateStructuredReport_MultiplePatients() {
        List<EligibilityAssessment> assessments = new ArrayList<>();
        assessments.add(createEligibleAssessment("patient-1"));
        assessments.add(createNotEligibleAssessment("patient-2"));
        assessments.add(createPotentiallyEligibleAssessment("patient-3"));
        
        String report = reportGenerator.generateStructuredReport(assessments);
        
        assertTrue(report.contains("\"patient-1\""));
        assertTrue(report.contains("\"patient-2\""));
        assertTrue(report.contains("\"patient-3\""));
        assertTrue(report.contains("\"eligibility\": \"ELIGIBLE\""));
        assertTrue(report.contains("\"eligibility\": \"NOT ELIGIBLE\""));
        assertTrue(report.contains("\"eligibility\": \"POTENTIALLY ELIGIBLE - DATA MISSING\""));
    }

    @Test
    public void testGenerateStructuredReport_EmptyList() {
        String report = reportGenerator.generateStructuredReport(new ArrayList<>());
        
        assertEquals("{}", report);
    }

    @Test
    public void testGenerateStructuredReport_NullList() {
        String report = reportGenerator.generateStructuredReport(null);
        
        assertEquals("{}", report);
    }

    @Test
    public void testGenerateStructuredReport_CriteriaCategories() {
        EligibilityAssessment assessment = new EligibilityAssessment("patient-100");
        
        // Add MET criteria
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 50 years"));
        
        // Add NOT_MET criteria
        assessment.addCriterion(new EligibilityCriterion(
            "Hemoglobin ≥9.0 g/dL", CriterionType.INCLUSION, CriterionStatus.NOT_MET, "Hemoglobin: 8.5 g/dL"));
        
        // Add UNKNOWN criteria
        EligibilityCriterion unknownCriterion = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
        unknownCriterion.addMissingData("ECOG performance status");
        assessment.addCriterion(unknownCriterion);
        
        assessment.determineOverallStatus();
        
        String report = reportGenerator.generateStructuredReport(List.of(assessment));
        
        // Check that the report is valid JSON structure
        assertNotNull(report);
        assertTrue(report.contains("patient-100"));
        assertTrue(report.contains("eligibility"));
        assertTrue(report.contains("criteriasMatched"));
        assertTrue(report.contains("criteriasNotMatched"));
        assertTrue(report.contains("criteriasUnknown"));
        assertTrue(report.contains("missingDataElements"));
    }

    @Test
    public void testGenerateStructuredReport_JsonFormatting() {
        EligibilityAssessment assessment = createEligibleAssessment("patient-1");
        
        String report = reportGenerator.generateStructuredReport(List.of(assessment));
        
        // Check JSON structure
        assertTrue(report.startsWith("{"));
        assertTrue(report.endsWith("}\n"));
        assertTrue(report.contains("["));
        assertTrue(report.contains("]"));
        assertTrue(report.contains(":"));
    }

    // ========== Helper Methods ==========

    private EligibilityAssessment createEligibleAssessment(String patientId) {
        EligibilityAssessment assessment = new EligibilityAssessment(patientId);
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 50 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "NSCLC Diagnosis", CriterionType.INCLUSION, CriterionStatus.MET, "Confirmed NSCLC"));
        assessment.addCriterion(new EligibilityCriterion(
            "Stage IIIB/IV", CriterionType.INCLUSION, CriterionStatus.MET, "Stage IV"));
        assessment.addCriterion(new EligibilityCriterion(
            "No Prior Therapy", CriterionType.EXCLUSION, CriterionStatus.MET, "No prior therapy"));
        assessment.addCriterion(new EligibilityCriterion(
            "No Brain Mets", CriterionType.EXCLUSION, CriterionStatus.MET, "No brain metastases"));
        
        assessment.determineOverallStatus();
        
        return assessment;
    }

    private EligibilityAssessment createNotEligibleAssessment(String patientId) {
        EligibilityAssessment assessment = new EligibilityAssessment(patientId);
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.NOT_MET, "Age: 16 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "NSCLC Diagnosis", CriterionType.INCLUSION, CriterionStatus.MET, "Confirmed NSCLC"));
        
        assessment.determineOverallStatus();
        assessment.setIneligibilityReason("Age below 18");
        
        return assessment;
    }

    private EligibilityAssessment createPotentiallyEligibleAssessment(String patientId) {
        EligibilityAssessment assessment = new EligibilityAssessment(patientId);
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 50 years"));
        
        EligibilityCriterion unknownCriterion = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Not recorded");
        unknownCriterion.addMissingData("ECOG performance status");
        assessment.addCriterion(unknownCriterion);
        
        assessment.determineOverallStatus();
        
        return assessment;
    }
}
