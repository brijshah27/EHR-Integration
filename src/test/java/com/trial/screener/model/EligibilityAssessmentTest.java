package com.trial.screener.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

/**
 * Test class for EligibilityAssessment.determineOverallStatus() logic
 */
public class EligibilityAssessmentTest {

    @Test
    public void testEligible_AllInclusionMetAllExclusionMet() {
        // All inclusion criteria MET and all exclusion criteria MET (exclusions not present)
        EligibilityAssessment assessment = new EligibilityAssessment("patient-123");
        
        // Add inclusion criteria - all MET
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 62 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "NSCLC Diagnosis", CriterionType.INCLUSION, CriterionStatus.MET, "Confirmed NSCLC"));
        
        // Add exclusion criteria - all MET (meaning exclusions not present)
        assessment.addCriterion(new EligibilityCriterion(
            "No Prior Therapy", CriterionType.EXCLUSION, CriterionStatus.MET, "No prior therapy found"));
        assessment.addCriterion(new EligibilityCriterion(
            "No Brain Mets", CriterionType.EXCLUSION, CriterionStatus.MET, "No brain metastases"));
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.ELIGIBLE, assessment.getOverallStatus());
        assertNull(assessment.getIneligibilityReason());
        assertTrue(assessment.getMissingDataElements().isEmpty());
    }

    @Test
    public void testNotEligible_InclusionNotMet() {
        // One inclusion criterion NOT_MET
        EligibilityAssessment assessment = new EligibilityAssessment("patient-456");
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.NOT_MET, "Age: 16 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "NSCLC Diagnosis", CriterionType.INCLUSION, CriterionStatus.MET, "Confirmed NSCLC"));
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, assessment.getOverallStatus());
        assertEquals("Inclusion criterion not met: Age ≥18 years", assessment.getIneligibilityReason());
    }

    @Test
    public void testNotEligible_ExclusionNotMet() {
        // Exclusion criterion NOT_MET (meaning exclusion is present)
        EligibilityAssessment assessment = new EligibilityAssessment("patient-789");
        
        // All inclusion criteria MET
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 62 years"));
        assessment.addCriterion(new EligibilityCriterion(
            "NSCLC Diagnosis", CriterionType.INCLUSION, CriterionStatus.MET, "Confirmed NSCLC"));
        
        // Exclusion criterion NOT_MET (exclusion present - patient ineligible)
        assessment.addCriterion(new EligibilityCriterion(
            "No Prior Therapy", CriterionType.EXCLUSION, CriterionStatus.NOT_MET, "Prior therapy found"));
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, assessment.getOverallStatus());
        assertEquals("Exclusion criterion present: No Prior Therapy", assessment.getIneligibilityReason());
    }

    @Test
    public void testPotentiallyEligible_WithUnknownCriteria() {
        // All known criteria pass but some are UNKNOWN
        EligibilityAssessment assessment = new EligibilityAssessment("patient-101");
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 62 years"));
        
        EligibilityCriterion unknownCriterion = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "ECOG not recorded");
        unknownCriterion.addMissingData("ECOG performance status");
        assessment.addCriterion(unknownCriterion);
        
        assessment.addCriterion(new EligibilityCriterion(
            "No Prior Therapy", CriterionType.EXCLUSION, CriterionStatus.MET, "No prior therapy"));
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.POTENTIALLY_ELIGIBLE, assessment.getOverallStatus());
        assertNull(assessment.getIneligibilityReason());
        assertFalse(assessment.getMissingDataElements().isEmpty());
        assertTrue(assessment.getMissingDataElements().contains("ECOG performance status"));
        assertTrue(assessment.getMissingDataElements().contains("ECOG Status"));
    }

    @Test
    public void testMissingDataCollection() {
        // Test that missing data elements are collected from UNKNOWN criteria
        EligibilityAssessment assessment = new EligibilityAssessment("patient-202");
        
        assessment.addCriterion(new EligibilityCriterion(
            "Age ≥18 years", CriterionType.INCLUSION, CriterionStatus.MET, "Age: 62 years"));
        
        EligibilityCriterion unknownCriterion1 = new EligibilityCriterion(
            "ECOG Status", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "ECOG not recorded");
        unknownCriterion1.addMissingData("ECOG performance status");
        assessment.addCriterion(unknownCriterion1);
        
        EligibilityCriterion unknownCriterion2 = new EligibilityCriterion(
            "Hemoglobin", CriterionType.INCLUSION, CriterionStatus.UNKNOWN, "Lab result not available");
        unknownCriterion2.addMissingData("Hemoglobin lab result");
        assessment.addCriterion(unknownCriterion2);
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.POTENTIALLY_ELIGIBLE, assessment.getOverallStatus());
        assertEquals(4, assessment.getMissingDataElements().size()); // 2 from missingData lists + 2 criterion names
    }

    @Test
    public void testEmptyCriteria() {
        // Test with no criteria
        EligibilityAssessment assessment = new EligibilityAssessment("patient-303");
        
        assessment.determineOverallStatus();
        
        assertEquals(EligibilityStatus.POTENTIALLY_ELIGIBLE, assessment.getOverallStatus());
        assertEquals("No criteria evaluated", assessment.getIneligibilityReason());
    }
}
