package com.trial.screener.evaluator;

import com.trial.screener.model.CriterionStatus;
import com.trial.screener.model.CriterionType;
import com.trial.screener.model.EligibilityAssessment;
import com.trial.screener.model.EligibilityCriterion;
import com.trial.screener.model.PatientData;
import org.hl7.fhir.r4.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Evaluates patient data against clinical trial eligibility criteria
 */
public class EligibilityEvaluator {

    // SNOMED codes for NSCLC
    private static final String SNOMED_NSCLC = "254637007";
    private static final String SNOMED_LUNG_CANCER = "424132000";
    
    // SNOMED code for brain metastases
    private static final String SNOMED_BRAIN_METASTASES = "94225005";
    
    // LOINC code for ECOG performance status
    private static final String LOINC_ECOG = "89247-1";
    
    // LOINC codes for lab tests
    private static final String LOINC_HEMOGLOBIN = "718-7";
    private static final String LOINC_NEUTROPHIL = "751-8";
    private static final String LOINC_PLATELET = "777-3";
    
    // Lab result recency threshold (30 days)
    private static final int LAB_RECENCY_DAYS = 30;
    
    // Stage patterns for IIIB and IV
    private static final Pattern STAGE_IIIB_PATTERN = Pattern.compile(
        "(?i)(stage\\s*)?(IIIB|3B|three\\s*B)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAGE_IV_PATTERN = Pattern.compile(
        "(?i)(stage\\s*)?(IV|4|four|metastatic)", Pattern.CASE_INSENSITIVE);

    /**
     * Main evaluation method that assesses all eligibility criteria
     * @param patientData The patient data to evaluate
     * @return EligibilityAssessment with all criteria evaluated
     */
    public EligibilityAssessment evaluate(PatientData patientData) {
        if (patientData == null) {
            throw new IllegalArgumentException("PatientData cannot be null");
        }

        EligibilityAssessment assessment = new EligibilityAssessment(patientData.getPatientId());

        // Evaluate inclusion criteria
        assessment.addCriterion(evaluateAge(patientData));
        assessment.addCriterion(evaluateDiagnosis(patientData));
        assessment.addCriterion(evaluateStaging(patientData));
        assessment.addCriterion(evaluateEcogStatus(patientData));
        assessment.addCriterion(evaluateHemoglobin(patientData));
        assessment.addCriterion(evaluateNeutrophilCount(patientData));
        assessment.addCriterion(evaluatePlateletCount(patientData));

        // Evaluate exclusion criteria
        assessment.addCriterion(evaluatePriorTherapy(patientData));
        assessment.addCriterion(evaluateBrainMetastases(patientData));

        // Determine overall status based on all criteria
        assessment.determineOverallStatus();

        return assessment;
    }

    /**
     * Evaluate age criterion: Patient must be ≥18 years old
     * @param data Patient data
     * @return EligibilityCriterion for age
     */
    private EligibilityCriterion evaluateAge(PatientData data) {
        Optional<Integer> ageOpt = data.getAge();

        if (ageOpt.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Age ≥18 years",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Birth date not available"
            );
            criterion.addMissingData("Patient birth date");
            return criterion;
        }

        int age = ageOpt.get();
        if (age >= 18) {
            return new EligibilityCriterion(
                "Age ≥18 years",
                CriterionType.INCLUSION,
                CriterionStatus.MET,
                "Age: " + age + " years"
            );
        } else {
            return new EligibilityCriterion(
                "Age ≥18 years",
                CriterionType.INCLUSION,
                CriterionStatus.NOT_MET,
                "Age: " + age + " years (under 18)"
            );
        }
    }

    /**
     * Evaluate diagnosis criterion: Patient must have NSCLC diagnosis
     * @param data Patient data
     * @return EligibilityCriterion for diagnosis
     */
    private EligibilityCriterion evaluateDiagnosis(PatientData data) {
        List<Condition> conditions = data.getConditions();

        if (conditions == null || conditions.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "NSCLC Diagnosis",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "No condition data available"
            );
            criterion.addMissingData("Condition/diagnosis information");
            return criterion;
        }

        // Search for NSCLC diagnosis
        for (Condition condition : conditions) {
            if (isNsclcDiagnosis(condition)) {
                String diagnosisText = extractDiagnosisText(condition);
                return new EligibilityCriterion(
                    "NSCLC Diagnosis",
                    CriterionType.INCLUSION,
                    CriterionStatus.MET,
                    "Confirmed NSCLC: " + diagnosisText
                );
            }
        }

        // No NSCLC found
        return new EligibilityCriterion(
            "NSCLC Diagnosis",
            CriterionType.INCLUSION,
            CriterionStatus.NOT_MET,
            "No NSCLC diagnosis found in patient records"
        );
    }

    /**
     * Check if a condition represents NSCLC diagnosis
     */
    private boolean isNsclcDiagnosis(Condition condition) {
        if (!condition.hasCode() || !condition.getCode().hasCoding()) {
            return false;
        }

        // Check SNOMED codes
        boolean hasSnomedCode = condition.getCode().getCoding().stream()
            .anyMatch(coding -> 
                SNOMED_NSCLC.equals(coding.getCode()) || 
                SNOMED_LUNG_CANCER.equals(coding.getCode())
            );

        if (hasSnomedCode) {
            return true;
        }

        // Check text matching for "non-small cell lung cancer"
        if (condition.getCode().hasText()) {
            String text = condition.getCode().getText().toLowerCase();
            if (text.contains("non-small cell lung cancer") || 
                text.contains("nsclc")) {
                return true;
            }
        }

        // Check display text in codings
        return condition.getCode().getCoding().stream()
            .anyMatch(coding -> {
                if (coding.hasDisplay()) {
                    String display = coding.getDisplay().toLowerCase();
                    return display.contains("non-small cell lung cancer") || 
                           display.contains("nsclc");
                }
                return false;
            });
    }

    /**
     * Extract diagnosis text for display
     */
    private String extractDiagnosisText(Condition condition) {
        if (condition.getCode().hasText()) {
            return condition.getCode().getText();
        }
        
        return condition.getCode().getCoding().stream()
            .filter(Coding::hasDisplay)
            .map(Coding::getDisplay)
            .findFirst()
            .orElse("Non-small cell lung cancer");
    }

    /**
     * Evaluate staging criterion: Patient must have Stage IIIB or IV disease
     * @param data Patient data
     * @return EligibilityCriterion for staging
     */
    private EligibilityCriterion evaluateStaging(PatientData data) {
        List<Condition> lungCancerConditions = data.getLungCancerConditions();

        if (lungCancerConditions.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Stage IIIB/IV Disease",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "No lung cancer conditions found for staging"
            );
            criterion.addMissingData("Disease staging information");
            return criterion;
        }

        // Search for stage information in conditions
        for (Condition condition : lungCancerConditions) {
            Optional<String> stageOpt = extractStage(condition);
            
            if (stageOpt.isPresent()) {
                String stage = stageOpt.get();
                
                if (isAdvancedStage(stage)) {
                    return new EligibilityCriterion(
                        "Stage IIIB/IV Disease",
                        CriterionType.INCLUSION,
                        CriterionStatus.MET,
                        "Stage: " + stage
                    );
                } else {
                    return new EligibilityCriterion(
                        "Stage IIIB/IV Disease",
                        CriterionType.INCLUSION,
                        CriterionStatus.NOT_MET,
                        "Stage: " + stage + " (requires Stage IIIB or IV)"
                    );
                }
            }
        }

        // No staging information found
        EligibilityCriterion criterion = new EligibilityCriterion(
            "Stage IIIB/IV Disease",
            CriterionType.INCLUSION,
            CriterionStatus.UNKNOWN,
            "Staging information not available"
        );
        criterion.addMissingData("Disease stage");
        return criterion;
    }

    /**
     * Extract stage information from a condition
     */
    private Optional<String> extractStage(Condition condition) {
        if (!condition.hasStage() || condition.getStage().isEmpty()) {
            return Optional.empty();
        }

        // Get the first stage entry
        Condition.ConditionStageComponent stage = condition.getStage().get(0);
        
        if (stage.hasSummary() && stage.getSummary().hasText()) {
            return Optional.of(stage.getSummary().getText());
        }
        
        if (stage.hasSummary() && stage.getSummary().hasCoding()) {
            return stage.getSummary().getCoding().stream()
                .filter(Coding::hasDisplay)
                .map(Coding::getDisplay)
                .findFirst();
        }

        return Optional.empty();
    }

    /**
     * Check if stage represents advanced disease (IIIB or IV)
     */
    private boolean isAdvancedStage(String stage) {
        return STAGE_IIIB_PATTERN.matcher(stage).find() || 
               STAGE_IV_PATTERN.matcher(stage).find();
    }

    /**
     * Evaluate ECOG performance status: Must be 0-2
     * @param data Patient data
     * @return EligibilityCriterion for ECOG status
     */
    private EligibilityCriterion evaluateEcogStatus(PatientData data) {
        Optional<Observation> ecogObsOpt = data.getLatestLabResult(LOINC_ECOG);

        if (!ecogObsOpt.isPresent()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "ECOG Performance Status 0-2",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "ECOG performance status not recorded"
            );
            criterion.addMissingData("ECOG performance status");
            return criterion;
        }

        Observation ecogObs = ecogObsOpt.get();
        Optional<Integer> ecogValueOpt = extractEcogValue(ecogObs);

        if (ecogValueOpt.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "ECOG Performance Status 0-2",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "ECOG value could not be extracted"
            );
            criterion.addMissingData("ECOG performance status value");
            return criterion;
        }

        int ecogValue = ecogValueOpt.get();
        
        if (ecogValue >= 0 && ecogValue <= 2) {
            return new EligibilityCriterion(
                "ECOG Performance Status 0-2",
                CriterionType.INCLUSION,
                CriterionStatus.MET,
                "ECOG: " + ecogValue
            );
        } else if (ecogValue >= 3 && ecogValue <= 5) {
            return new EligibilityCriterion(
                "ECOG Performance Status 0-2",
                CriterionType.INCLUSION,
                CriterionStatus.NOT_MET,
                "ECOG: " + ecogValue + " (requires 0-2)"
            );
        } else {
            return new EligibilityCriterion(
                "ECOG Performance Status 0-2",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "ECOG: " + ecogValue + " (invalid value)"
            );
        }
    }

    /**
     * Extract ECOG value from observation
     */
    private Optional<Integer> extractEcogValue(Observation observation) {
        // Try valueInteger first
        if (observation.hasValueIntegerType()) {
            return Optional.of(observation.getValueIntegerType().getValue());
        }

        // Try valueCodeableConcept
        if (observation.hasValueCodeableConcept()) {
            if (observation.getValueCodeableConcept().hasCoding()) {
                return observation.getValueCodeableConcept().getCoding().stream()
                    .filter(Coding::hasCode)
                    .map(coding -> {
                        try {
                            return Integer.parseInt(coding.getCode());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst();
            }
        }

        return Optional.empty();
    }

    /**
     * Evaluate hemoglobin criterion: Must be ≥9.0 g/dL
     * @param data Patient data
     * @return EligibilityCriterion for hemoglobin
     */
    private EligibilityCriterion evaluateHemoglobin(PatientData data) {
        Optional<Observation> hemoglobinObs = data.getLatestLabResult(LOINC_HEMOGLOBIN);

        if (hemoglobinObs.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Hemoglobin ≥9.0 g/dL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Hemoglobin result not available"
            );
            criterion.addMissingData("Hemoglobin lab result");
            return criterion;
        }

        Observation obs = hemoglobinObs.get();
        
        // Check if result is recent (within 30 days)
        if (!isRecentLabResult(obs)) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Hemoglobin ≥9.0 g/dL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Hemoglobin result is older than 30 days"
            );
            criterion.addMissingData("Recent hemoglobin lab result");
            return criterion;
        }

        Optional<BigDecimal> valueOpt = extractLabValue(obs);
        
        if (valueOpt.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Hemoglobin ≥9.0 g/dL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Hemoglobin value could not be extracted"
            );
            criterion.addMissingData("Hemoglobin value");
            return criterion;
        }

        BigDecimal value = valueOpt.get();
        String dateStr = formatObservationDate(obs);
        
        if (value.compareTo(new BigDecimal("9.0")) >= 0) {
            return new EligibilityCriterion(
                "Hemoglobin ≥9.0 g/dL",
                CriterionType.INCLUSION,
                CriterionStatus.MET,
                String.format("Hemoglobin: %.1f g/dL on %s", value, dateStr)
            );
        } else {
            return new EligibilityCriterion(
                "Hemoglobin ≥9.0 g/dL",
                CriterionType.INCLUSION,
                CriterionStatus.NOT_MET,
                String.format("Hemoglobin: %.1f g/dL on %s (requires ≥9.0)", value, dateStr)
            );
        }
    }

    /**
     * Evaluate neutrophil count criterion: Must be ≥1,500/µL
     * @param data Patient data
     * @return EligibilityCriterion for neutrophil count
     */
    private EligibilityCriterion evaluateNeutrophilCount(PatientData data) {
        Optional<Observation> neutrophilObs = data.getLatestLabResult(LOINC_NEUTROPHIL);

        if (neutrophilObs.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Absolute Neutrophil Count ≥1,500/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Neutrophil count result not available"
            );
            criterion.addMissingData("Absolute neutrophil count lab result");
            return criterion;
        }

        Observation obs = neutrophilObs.get();
        
        // Check if result is recent (within 30 days)
        if (!isRecentLabResult(obs)) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Absolute Neutrophil Count ≥1,500/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Neutrophil count result is older than 30 days"
            );
            criterion.addMissingData("Recent neutrophil count lab result");
            return criterion;
        }

        Optional<BigDecimal> valueOpt = extractLabValue(obs);
        
        if (valueOpt.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Absolute Neutrophil Count ≥1,500/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Neutrophil count value could not be extracted"
            );
            criterion.addMissingData("Neutrophil count value");
            return criterion;
        }

        BigDecimal value = valueOpt.get();
        String dateStr = formatObservationDate(obs);
        
        if (value.compareTo(new BigDecimal("1500")) >= 0) {
            return new EligibilityCriterion(
                "Absolute Neutrophil Count ≥1,500/µL",
                CriterionType.INCLUSION,
                CriterionStatus.MET,
                String.format("Neutrophil count: %.0f/µL on %s", value, dateStr)
            );
        } else {
            return new EligibilityCriterion(
                "Absolute Neutrophil Count ≥1,500/µL",
                CriterionType.INCLUSION,
                CriterionStatus.NOT_MET,
                String.format("Neutrophil count: %.0f/µL on %s (requires ≥1,500)", value, dateStr)
            );
        }
    }

    /**
     * Evaluate platelet count criterion: Must be ≥100,000/µL
     * @param data Patient data
     * @return EligibilityCriterion for platelet count
     */
    private EligibilityCriterion evaluatePlateletCount(PatientData data) {
        Optional<Observation> plateletObs = data.getLatestLabResult(LOINC_PLATELET);

        if (plateletObs.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Platelet Count ≥100,000/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Platelet count result not available"
            );
            criterion.addMissingData("Platelet count lab result");
            return criterion;
        }

        Observation obs = plateletObs.get();
        
        // Check if result is recent (within 30 days)
        if (!isRecentLabResult(obs)) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Platelet Count ≥100,000/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Platelet count result is older than 30 days"
            );
            criterion.addMissingData("Recent platelet count lab result");
            return criterion;
        }

        Optional<BigDecimal> valueOpt = extractLabValue(obs);
        
        if (valueOpt.isEmpty()) {
            EligibilityCriterion criterion = new EligibilityCriterion(
                "Platelet Count ≥100,000/µL",
                CriterionType.INCLUSION,
                CriterionStatus.UNKNOWN,
                "Platelet count value could not be extracted"
            );
            criterion.addMissingData("Platelet count value");
            return criterion;
        }

        BigDecimal value = valueOpt.get();
        String dateStr = formatObservationDate(obs);
        
        if (value.compareTo(new BigDecimal("100000")) >= 0) {
            return new EligibilityCriterion(
                "Platelet Count ≥100,000/µL",
                CriterionType.INCLUSION,
                CriterionStatus.MET,
                String.format("Platelet count: %.0f/µL on %s", value, dateStr)
            );
        } else {
            return new EligibilityCriterion(
                "Platelet Count ≥100,000/µL",
                CriterionType.INCLUSION,
                CriterionStatus.NOT_MET,
                String.format("Platelet count: %.0f/µL on %s (requires ≥100,000)", value, dateStr)
            );
        }
    }

    /**
     * Extract lab value from observation, handling valueQuantity and unit conversion
     * @param observation The observation to extract value from
     * @return Optional containing the lab value, or empty if not extractable
     */
    private Optional<BigDecimal> extractLabValue(Observation observation) {
        if (!observation.hasValueQuantity()) {
            return Optional.empty();
        }

        if (!observation.getValueQuantity().hasValue()) {
            return Optional.empty();
        }

        BigDecimal value = observation.getValueQuantity().getValue();
        String unit = observation.getValueQuantity().hasUnit() ? 
            observation.getValueQuantity().getUnit() : "";

        // Handle unit conversions for hemoglobin (g/L to g/dL)
        if (unit.equalsIgnoreCase("g/L")) {
            value = value.divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
        }

        return Optional.of(value);
    }

    /**
     * Check if a lab result is recent (within 30 days)
     * @param observation The observation to check
     * @return true if the observation is within 30 days, false otherwise
     */
    private boolean isRecentLabResult(Observation observation) {
        if (!observation.hasEffectiveDateTimeType()) {
            return false;
        }

        Date effectiveDate = observation.getEffectiveDateTimeType().getValue();
        if (effectiveDate == null) {
            return false;
        }

        LocalDate obsDate = effectiveDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
        LocalDate now = LocalDate.now();
        
        long daysBetween = ChronoUnit.DAYS.between(obsDate, now);
        
        return daysBetween >= 0 && daysBetween <= LAB_RECENCY_DAYS;
    }

    /**
     * Format observation date for display
     * @param observation The observation
     * @return Formatted date string
     */
    private String formatObservationDate(Observation observation) {
        if (!observation.hasEffectiveDateTimeType()) {
            return "unknown date";
        }

        Date effectiveDate = observation.getEffectiveDateTimeType().getValue();
        if (effectiveDate == null) {
            return "unknown date";
        }

        LocalDate obsDate = effectiveDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
        
        return obsDate.toString();
    }

    /**
     * Evaluate prior therapy exclusion criterion: Patient must not have received prior systemic therapy
     * @param data Patient data
     * @return EligibilityCriterion for prior therapy (EXCLUSION)
     */
    private EligibilityCriterion evaluatePriorTherapy(PatientData data) {
        List<MedicationStatement> medications = data.getMedications();

        if (medications == null || medications.isEmpty()) {
            // No medication data - mark as UNKNOWN
            EligibilityCriterion criterion = new EligibilityCriterion(
                "No Prior Systemic Therapy",
                CriterionType.EXCLUSION,
                CriterionStatus.UNKNOWN,
                "Medication history not available"
            );
            criterion.addMissingData("Medication history");
            return criterion;
        }

        // Search for systemic cancer therapies
        for (MedicationStatement med : medications) {
            if (isSystemicCancerTherapy(med)) {
                // Check if this indicates therapy for advanced disease
                if (indicatesAdvancedDisease(med, data.getConditions())) {
                    String medName = extractMedicationName(med);
                    // Exclusion criterion MET means patient is INELIGIBLE
                    return new EligibilityCriterion(
                        "No Prior Systemic Therapy",
                        CriterionType.EXCLUSION,
                        CriterionStatus.NOT_MET,
                        "Prior systemic therapy found: " + medName
                    );
                }
            }
        }

        // No prior systemic therapy found - exclusion criterion NOT met (patient passes)
        return new EligibilityCriterion(
            "No Prior Systemic Therapy",
            CriterionType.EXCLUSION,
            CriterionStatus.MET,
            "No prior systemic therapy for advanced disease found"
        );
    }

    /**
     * Check if a medication represents systemic cancer therapy
     * @param med MedicationStatement to check
     * @return true if this is a systemic cancer therapy
     */
    private boolean isSystemicCancerTherapy(MedicationStatement med) {
        if (!med.hasMedicationCodeableConcept()) {
            return false;
        }

        // Check medication text and display for common cancer therapy terms
        String medText = "";
        
        if (med.getMedicationCodeableConcept().hasText()) {
            medText = med.getMedicationCodeableConcept().getText().toLowerCase();
        }

        // Also check coding displays
        for (var coding : med.getMedicationCodeableConcept().getCoding()) {
            if (coding.hasDisplay()) {
                medText += " " + coding.getDisplay().toLowerCase();
            }
        }

        // Common chemotherapy agents
        String[] chemoAgents = {
            "cisplatin", "carboplatin", "paclitaxel", "docetaxel", "gemcitabine",
            "pemetrexed", "etoposide", "vinorelbine", "irinotecan", "topotecan"
        };

        // Common immunotherapy agents
        String[] immunoAgents = {
            "pembrolizumab", "nivolumab", "atezolizumab", "durvalumab", "ipilimumab",
            "keytruda", "opdivo", "tecentriq", "imfinzi"
        };

        // Common targeted therapy agents
        String[] targetedAgents = {
            "erlotinib", "gefitinib", "afatinib", "osimertinib", "crizotinib",
            "alectinib", "ceritinib", "brigatinib", "tarceva", "iressa", "tagrisso"
        };

        // Check for chemotherapy
        for (String agent : chemoAgents) {
            if (medText.contains(agent)) {
                return true;
            }
        }

        // Check for immunotherapy
        for (String agent : immunoAgents) {
            if (medText.contains(agent)) {
                return true;
            }
        }

        // Check for targeted therapy
        for (String agent : targetedAgents) {
            if (medText.contains(agent)) {
                return true;
            }
        }

        // Check for general terms
        if (medText.contains("chemotherapy") || 
            medText.contains("immunotherapy") ||
            medText.contains("targeted therapy")) {
            return true;
        }

        return false;
    }

    /**
     * Check if medication timing indicates therapy for advanced disease
     * @param med MedicationStatement
     * @param conditions List of patient conditions
     * @return true if medication indicates therapy for advanced disease
     */
    private boolean indicatesAdvancedDisease(MedicationStatement med, List<Condition> conditions) {
        // If medication has context indicating metastatic/advanced disease treatment
        if (med.hasReasonCode()) {
            for (var reasonCode : med.getReasonCode()) {
                if (reasonCode.hasText()) {
                    String reasonText = reasonCode.getText().toLowerCase();
                    if (reasonText.contains("metastatic") || 
                        reasonText.contains("advanced") ||
                        reasonText.contains("stage iv") ||
                        reasonText.contains("stage 4")) {
                        return true;
                    }
                }
            }
        }

        // Conservative approach: assume systemic cancer therapy is for advanced disease
        // This is safer for trial screening (false negative better than false positive)
        return true;
    }

    /**
     * Extract medication name for display
     * @param med MedicationStatement
     * @return Medication name
     */
    private String extractMedicationName(MedicationStatement med) {
        if (med.hasMedicationCodeableConcept()) {
            if (med.getMedicationCodeableConcept().hasText()) {
                return med.getMedicationCodeableConcept().getText();
            }
            
            for (var coding : med.getMedicationCodeableConcept().getCoding()) {
                if (coding.hasDisplay()) {
                    return coding.getDisplay();
                }
            }
        }
        
        return "Unknown medication";
    }

    /**
     * Evaluate brain metastases exclusion criterion: Patient must not have active brain metastases
     * @param data Patient data
     * @return EligibilityCriterion for brain metastases (EXCLUSION)
     */
    private EligibilityCriterion evaluateBrainMetastases(PatientData data) {
        List<Condition> conditions = data.getConditions();
        List<Procedure> procedures = data.getProcedures();

        boolean hasConditionData = conditions != null && !conditions.isEmpty();
        boolean hasProcedureData = procedures != null && !procedures.isEmpty();

        if (!hasConditionData && !hasProcedureData) {
            // No data available
            EligibilityCriterion criterion = new EligibilityCriterion(
                "No Active Brain Metastases",
                CriterionType.EXCLUSION,
                CriterionStatus.UNKNOWN,
                "Condition and procedure data not available"
            );
            criterion.addMissingData("Brain metastases information");
            return criterion;
        }

        // Check conditions for brain metastases
        if (hasConditionData) {
            for (Condition condition : conditions) {
                if (isBrainMetastases(condition)) {
                    // Check if it's active
                    if (isActiveCondition(condition)) {
                        // Active brain metastases found - exclusion criterion NOT met (patient ineligible)
                        return new EligibilityCriterion(
                            "No Active Brain Metastases",
                            CriterionType.EXCLUSION,
                            CriterionStatus.NOT_MET,
                            "Active brain metastases found"
                        );
                    }
                }
            }
        }

        // Check procedures for brain metastases treatment
        if (hasProcedureData) {
            for (Procedure procedure : procedures) {
                if (isBrainMetastasesProcedure(procedure)) {
                    // Brain metastases treatment found - indicates metastases
                    // Check if recent (within last year)
                    if (isRecentProcedure(procedure)) {
                        return new EligibilityCriterion(
                            "No Active Brain Metastases",
                            CriterionType.EXCLUSION,
                            CriterionStatus.NOT_MET,
                            "Recent brain metastases treatment found: " + extractProcedureName(procedure)
                        );
                    }
                }
            }
        }

        // No active brain metastases found - exclusion criterion MET (patient passes)
        return new EligibilityCriterion(
            "No Active Brain Metastases",
            CriterionType.EXCLUSION,
            CriterionStatus.MET,
            "No active brain metastases found"
        );
    }

    /**
     * Check if a condition represents brain metastases
     * @param condition Condition to check
     * @return true if this is a brain metastases diagnosis
     */
    private boolean isBrainMetastases(Condition condition) {
        if (!condition.hasCode() || !condition.getCode().hasCoding()) {
            return false;
        }

        // Check for SNOMED code for brain metastases
        boolean hasSnomedCode = condition.getCode().getCoding().stream()
            .anyMatch(coding -> SNOMED_BRAIN_METASTASES.equals(coding.getCode()));

        if (hasSnomedCode) {
            return true;
        }

        // Check text matching for brain metastases
        if (condition.getCode().hasText()) {
            String text = condition.getCode().getText().toLowerCase();
            if (text.contains("brain metasta") || 
                text.contains("cerebral metasta") ||
                text.contains("brain met") ||
                text.contains("cns metasta")) {
                return true;
            }
        }

        // Check display text in codings
        return condition.getCode().getCoding().stream()
            .anyMatch(coding -> {
                if (coding.hasDisplay()) {
                    String display = coding.getDisplay().toLowerCase();
                    return display.contains("brain metasta") || 
                           display.contains("cerebral metasta") ||
                           display.contains("brain met") ||
                           display.contains("cns metasta");
                }
                return false;
            });
    }

    /**
     * Check if a condition is active
     * @param condition Condition to check
     * @return true if condition is active
     */
    private boolean isActiveCondition(Condition condition) {
        if (!condition.hasClinicalStatus()) {
            // If no status, assume active for safety
            return true;
        }

        // Check for active status
        return condition.getClinicalStatus().getCoding().stream()
            .anyMatch(coding -> 
                "active".equalsIgnoreCase(coding.getCode()) ||
                "recurrence".equalsIgnoreCase(coding.getCode()) ||
                "relapse".equalsIgnoreCase(coding.getCode())
            );
    }

    /**
     * Check if a procedure is related to brain metastases treatment
     * @param procedure Procedure to check
     * @return true if this is a brain metastases treatment procedure
     */
    private boolean isBrainMetastasesProcedure(Procedure procedure) {
        if (!procedure.hasCode()) {
            return false;
        }

        String procedureText = "";
        
        if (procedure.getCode().hasText()) {
            procedureText = procedure.getCode().getText().toLowerCase();
        }

        // Also check coding displays
        for (var coding : procedure.getCode().getCoding()) {
            if (coding.hasDisplay()) {
                procedureText += " " + coding.getDisplay().toLowerCase();
            }
        }

        // Look for brain radiation or surgery terms
        String[] brainProcedureTerms = {
            "brain radiation", "cranial radiation", "whole brain radiation",
            "stereotactic radiosurgery", "gamma knife", "cyberknife",
            "brain surgery", "craniotomy", "brain resection",
            "srs", "wbrt"
        };

        for (String term : brainProcedureTerms) {
            if (procedureText.contains(term)) {
                return true;
            }
        }

        // Check if procedure mentions brain/cranial and metastases
        if ((procedureText.contains("brain") || procedureText.contains("cranial") || procedureText.contains("cerebral")) &&
            (procedureText.contains("metasta") || procedureText.contains("met"))) {
            return true;
        }

        return false;
    }

    /**
     * Check if a procedure is recent (within last year)
     * @param procedure Procedure to check
     * @return true if procedure is within last year
     */
    private boolean isRecentProcedure(Procedure procedure) {
        if (!procedure.hasPerformedDateTimeType()) {
            // If no date, assume recent for safety
            return true;
        }

        Date performedDate = procedure.getPerformedDateTimeType().getValue();
        if (performedDate == null) {
            return true;
        }

        LocalDate procDate = performedDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
        LocalDate now = LocalDate.now();
        
        long daysBetween = ChronoUnit.DAYS.between(procDate, now);
        
        // Consider procedures within last year (365 days) as recent
        return daysBetween >= 0 && daysBetween <= 365;
    }

    /**
     * Extract procedure name for display
     * @param procedure Procedure
     * @return Procedure name
     */
    private String extractProcedureName(Procedure procedure) {
        if (procedure.hasCode()) {
            if (procedure.getCode().hasText()) {
                return procedure.getCode().getText();
            }
            
            for (var coding : procedure.getCode().getCoding()) {
                if (coding.hasDisplay()) {
                    return coding.getDisplay();
                }
            }
        }
        
        return "Unknown procedure";
    }
}
