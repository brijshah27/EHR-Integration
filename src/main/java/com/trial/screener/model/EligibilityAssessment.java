package com.trial.screener.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EligibilityAssessment {
    private String patientId;
    private EligibilityStatus overallStatus;
    private List<EligibilityCriterion> criteria;
    private String ineligibilityReason;
    private List<String> missingDataElements;

    public EligibilityAssessment() {
        this.criteria = new ArrayList<>();
        this.missingDataElements = new ArrayList<>();
    }

    public EligibilityAssessment(String patientId) {
        this.patientId = patientId;
        this.criteria = new ArrayList<>();
        this.missingDataElements = new ArrayList<>();
    }

    /**
     * Add a criterion to the assessment
     * @param criterion The criterion to add
     */
    public void addCriterion(EligibilityCriterion criterion) {
        if (this.criteria == null) {
            this.criteria = new ArrayList<>();
        }
        this.criteria.add(criterion);
    }

    /**
     * Determine overall eligibility status based on all criteria
     * Logic:
     * - ELIGIBLE: All inclusion criteria MET and all exclusion criteria MET (meaning exclusions not present)
     * - NOT_ELIGIBLE: Any inclusion criterion NOT_MET or any exclusion criterion NOT_MET (meaning exclusion present)
     * - POTENTIALLY_ELIGIBLE: All known criteria pass but some are UNKNOWN
     */
    public void determineOverallStatus() {
        if (criteria == null || criteria.isEmpty()) {
            this.overallStatus = EligibilityStatus.POTENTIALLY_ELIGIBLE;
            this.ineligibilityReason = "No criteria evaluated";
            return;
        }

        List<EligibilityCriterion> inclusionCriteria = criteria.stream()
                .filter(c -> c.getType() == CriterionType.INCLUSION)
                .collect(Collectors.toList());

        List<EligibilityCriterion> exclusionCriteria = criteria.stream()
                .filter(c -> c.getType() == CriterionType.EXCLUSION)
                .collect(Collectors.toList());

        // Check for any NOT_MET inclusion criteria
        for (EligibilityCriterion criterion : inclusionCriteria) {
            if (criterion.getStatus() == CriterionStatus.NOT_MET) {
                this.overallStatus = EligibilityStatus.NOT_ELIGIBLE;
                this.ineligibilityReason = "Inclusion criterion not met: " + criterion.getName();
                return;
            }
        }

        // Check for any NOT_MET exclusion criteria (meaning the exclusion is present)
        // For exclusion criteria: NOT_MET means the exclusion IS present (patient is ineligible)
        for (EligibilityCriterion criterion : exclusionCriteria) {
            if (criterion.getStatus() == CriterionStatus.NOT_MET) {
                this.overallStatus = EligibilityStatus.NOT_ELIGIBLE;
                this.ineligibilityReason = "Exclusion criterion present: " + criterion.getName();
                return;
            }
        }

        // Collect all missing data elements from UNKNOWN criteria
        this.missingDataElements = new ArrayList<>();
        boolean hasUnknown = false;
        
        for (EligibilityCriterion criterion : criteria) {
            if (criterion.getStatus() == CriterionStatus.UNKNOWN) {
                hasUnknown = true;
                if (criterion.getMissingData() != null) {
                    this.missingDataElements.addAll(criterion.getMissingData());
                }
                // Also add the criterion name itself as missing data
                this.missingDataElements.add(criterion.getName());
            }
        }

        // If we have unknown criteria but no failures, mark as potentially eligible
        if (hasUnknown) {
            this.overallStatus = EligibilityStatus.POTENTIALLY_ELIGIBLE;
            this.ineligibilityReason = null;
        } else {
            // All criteria are MET (inclusions met, exclusions not present)
            this.overallStatus = EligibilityStatus.ELIGIBLE;
            this.ineligibilityReason = null;
        }
    }

    // Getters and setters
    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public EligibilityStatus getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(EligibilityStatus overallStatus) {
        this.overallStatus = overallStatus;
    }

    public List<EligibilityCriterion> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<EligibilityCriterion> criteria) {
        this.criteria = criteria != null ? criteria : new ArrayList<>();
    }

    public String getIneligibilityReason() {
        return ineligibilityReason;
    }

    public void setIneligibilityReason(String ineligibilityReason) {
        this.ineligibilityReason = ineligibilityReason;
    }

    public List<String> getMissingDataElements() {
        return missingDataElements;
    }

    public void setMissingDataElements(List<String> missingDataElements) {
        this.missingDataElements = missingDataElements != null ? missingDataElements : new ArrayList<>();
    }
}
