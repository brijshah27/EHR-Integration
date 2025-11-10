package com.trial.screener.evaluator;

import com.trial.screener.model.*;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EligibilityEvaluator
 */
public class EligibilityEvaluatorTest {

    private EligibilityEvaluator evaluator;

    @BeforeEach
    public void setUp() {
        evaluator = new EligibilityEvaluator();
    }

    // ========== Age Tests ==========

    @Test
    public void testEvaluateAge_Under18() {
        PatientData data = createPatientWithAge(17);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ageCriterion = findCriterion(assessment, "Age ≥18 years");
        assertNotNull(ageCriterion);
        assertEquals(CriterionStatus.NOT_MET, ageCriterion.getStatus());
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, assessment.getOverallStatus());
    }

    @Test
    public void testEvaluateAge_Exactly18() {
        PatientData data = createPatientWithAge(18);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ageCriterion = findCriterion(assessment, "Age ≥18 years");
        assertNotNull(ageCriterion);
        assertEquals(CriterionStatus.MET, ageCriterion.getStatus());
    }

    @Test
    public void testEvaluateAge_Over18() {
        PatientData data = createPatientWithAge(65);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ageCriterion = findCriterion(assessment, "Age ≥18 years");
        assertNotNull(ageCriterion);
        assertEquals(CriterionStatus.MET, ageCriterion.getStatus());
    }

    @Test
    public void testEvaluateAge_MissingBirthDate() {
        PatientData data = new PatientData();
        data.setPatient(new Patient()); // Patient without birth date
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ageCriterion = findCriterion(assessment, "Age ≥18 years");
        assertNotNull(ageCriterion);
        assertEquals(CriterionStatus.UNKNOWN, ageCriterion.getStatus());
        assertTrue(ageCriterion.getMissingData().contains("Patient birth date"));
    }

    // ========== Diagnosis Tests ==========

    @Test
    public void testEvaluateDiagnosis_NSCLCWithSnomedCode() {
        PatientData data = createPatientWithCondition("254637007", "Non-small cell lung cancer");
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion diagnosisCriterion = findCriterion(assessment, "NSCLC Diagnosis");
        assertNotNull(diagnosisCriterion);
        assertEquals(CriterionStatus.MET, diagnosisCriterion.getStatus());
    }

    @Test
    public void testEvaluateDiagnosis_OtherCancer() {
        PatientData data = createPatientWithCondition("363346000", "Breast cancer");
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion diagnosisCriterion = findCriterion(assessment, "NSCLC Diagnosis");
        assertNotNull(diagnosisCriterion);
        assertEquals(CriterionStatus.NOT_MET, diagnosisCriterion.getStatus());
    }

    @Test
    public void testEvaluateDiagnosis_NoConditions() {
        PatientData data = createPatientWithAge(50);
        data.setConditions(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion diagnosisCriterion = findCriterion(assessment, "NSCLC Diagnosis");
        assertNotNull(diagnosisCriterion);
        assertEquals(CriterionStatus.UNKNOWN, diagnosisCriterion.getStatus());
    }

    // ========== Staging Tests ==========

    @Test
    public void testEvaluateStaging_StageIIIB() {
        PatientData data = createPatientWithNSCLCAndStage("Stage IIIB");
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion stagingCriterion = findCriterion(assessment, "Stage IIIB/IV Disease");
        assertNotNull(stagingCriterion);
        assertEquals(CriterionStatus.MET, stagingCriterion.getStatus());
    }

    @Test
    public void testEvaluateStaging_StageIV() {
        PatientData data = createPatientWithNSCLCAndStage("Stage IV");
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion stagingCriterion = findCriterion(assessment, "Stage IIIB/IV Disease");
        assertNotNull(stagingCriterion);
        assertEquals(CriterionStatus.MET, stagingCriterion.getStatus());
    }

    @Test
    public void testEvaluateStaging_StageII() {
        PatientData data = createPatientWithNSCLCAndStage("Stage II");
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion stagingCriterion = findCriterion(assessment, "Stage IIIB/IV Disease");
        assertNotNull(stagingCriterion);
        assertEquals(CriterionStatus.NOT_MET, stagingCriterion.getStatus());
    }

    @Test
    public void testEvaluateStaging_MissingStaging() {
        PatientData data = createPatientWithCondition("254637007", "Non-small cell lung cancer");
        // No stage information added
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion stagingCriterion = findCriterion(assessment, "Stage IIIB/IV Disease");
        assertNotNull(stagingCriterion);
        assertEquals(CriterionStatus.UNKNOWN, stagingCriterion.getStatus());
    }

    // ========== ECOG Status Tests ==========

    @Test
    public void testEvaluateEcogStatus_Value0() {
        PatientData data = createPatientWithEcog(0);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ecogCriterion = findCriterion(assessment, "ECOG Performance Status 0-2");
        assertNotNull(ecogCriterion);
        assertEquals(CriterionStatus.MET, ecogCriterion.getStatus());
    }

    @Test
    public void testEvaluateEcogStatus_Value2() {
        PatientData data = createPatientWithEcog(2);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ecogCriterion = findCriterion(assessment, "ECOG Performance Status 0-2");
        assertNotNull(ecogCriterion);
        assertEquals(CriterionStatus.MET, ecogCriterion.getStatus());
    }

    @Test
    public void testEvaluateEcogStatus_Value3() {
        PatientData data = createPatientWithEcog(3);
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ecogCriterion = findCriterion(assessment, "ECOG Performance Status 0-2");
        assertNotNull(ecogCriterion);
        assertEquals(CriterionStatus.NOT_MET, ecogCriterion.getStatus());
    }

    @Test
    public void testEvaluateEcogStatus_Missing() {
        PatientData data = createPatientWithAge(50);
        data.setObservations(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion ecogCriterion = findCriterion(assessment, "ECOG Performance Status 0-2");
        assertNotNull(ecogCriterion);
        assertEquals(CriterionStatus.UNKNOWN, ecogCriterion.getStatus());
    }

    // ========== Lab Tests - Hemoglobin ==========

    @Test
    public void testEvaluateHemoglobin_AtBoundary() {
        PatientData data = createPatientWithLabResult("718-7", new BigDecimal("9.0"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion hemoglobinCriterion = findCriterion(assessment, "Hemoglobin ≥9.0 g/dL");
        assertNotNull(hemoglobinCriterion);
        assertEquals(CriterionStatus.MET, hemoglobinCriterion.getStatus());
    }

    @Test
    public void testEvaluateHemoglobin_AboveBoundary() {
        PatientData data = createPatientWithLabResult("718-7", new BigDecimal("11.5"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion hemoglobinCriterion = findCriterion(assessment, "Hemoglobin ≥9.0 g/dL");
        assertNotNull(hemoglobinCriterion);
        assertEquals(CriterionStatus.MET, hemoglobinCriterion.getStatus());
    }

    @Test
    public void testEvaluateHemoglobin_BelowBoundary() {
        PatientData data = createPatientWithLabResult("718-7", new BigDecimal("8.5"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion hemoglobinCriterion = findCriterion(assessment, "Hemoglobin ≥9.0 g/dL");
        assertNotNull(hemoglobinCriterion);
        assertEquals(CriterionStatus.NOT_MET, hemoglobinCriterion.getStatus());
    }

    @Test
    public void testEvaluateHemoglobin_Missing() {
        PatientData data = createPatientWithAge(50);
        data.setObservations(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion hemoglobinCriterion = findCriterion(assessment, "Hemoglobin ≥9.0 g/dL");
        assertNotNull(hemoglobinCriterion);
        assertEquals(CriterionStatus.UNKNOWN, hemoglobinCriterion.getStatus());
    }

    // ========== Lab Tests - Neutrophil Count ==========

    @Test
    public void testEvaluateNeutrophilCount_AtBoundary() {
        PatientData data = createPatientWithLabResult("751-8", new BigDecimal("1500"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion neutrophilCriterion = findCriterion(assessment, "Absolute Neutrophil Count ≥1,500/µL");
        assertNotNull(neutrophilCriterion);
        assertEquals(CriterionStatus.MET, neutrophilCriterion.getStatus());
    }

    @Test
    public void testEvaluateNeutrophilCount_AboveBoundary() {
        PatientData data = createPatientWithLabResult("751-8", new BigDecimal("2500"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion neutrophilCriterion = findCriterion(assessment, "Absolute Neutrophil Count ≥1,500/µL");
        assertNotNull(neutrophilCriterion);
        assertEquals(CriterionStatus.MET, neutrophilCriterion.getStatus());
    }

    @Test
    public void testEvaluateNeutrophilCount_BelowBoundary() {
        PatientData data = createPatientWithLabResult("751-8", new BigDecimal("1200"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion neutrophilCriterion = findCriterion(assessment, "Absolute Neutrophil Count ≥1,500/µL");
        assertNotNull(neutrophilCriterion);
        assertEquals(CriterionStatus.NOT_MET, neutrophilCriterion.getStatus());
    }

    // ========== Lab Tests - Platelet Count ==========

    @Test
    public void testEvaluatePlateletCount_AtBoundary() {
        PatientData data = createPatientWithLabResult("777-3", new BigDecimal("100000"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion plateletCriterion = findCriterion(assessment, "Platelet Count ≥100,000/µL");
        assertNotNull(plateletCriterion);
        assertEquals(CriterionStatus.MET, plateletCriterion.getStatus());
    }

    @Test
    public void testEvaluatePlateletCount_AboveBoundary() {
        PatientData data = createPatientWithLabResult("777-3", new BigDecimal("250000"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion plateletCriterion = findCriterion(assessment, "Platelet Count ≥100,000/µL");
        assertNotNull(plateletCriterion);
        assertEquals(CriterionStatus.MET, plateletCriterion.getStatus());
    }

    @Test
    public void testEvaluatePlateletCount_BelowBoundary() {
        PatientData data = createPatientWithLabResult("777-3", new BigDecimal("85000"));
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion plateletCriterion = findCriterion(assessment, "Platelet Count ≥100,000/µL");
        assertNotNull(plateletCriterion);
        assertEquals(CriterionStatus.NOT_MET, plateletCriterion.getStatus());
    }

    // ========== Prior Therapy Tests ==========

    @Test
    public void testEvaluatePriorTherapy_NoPriorTherapy() {
        PatientData data = createPatientWithAge(50);
        data.setMedications(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion priorTherapyCriterion = findCriterion(assessment, "No Prior Systemic Therapy");
        assertNotNull(priorTherapyCriterion);
        assertEquals(CriterionStatus.UNKNOWN, priorTherapyCriterion.getStatus());
    }

    @Test
    public void testEvaluatePriorTherapy_WithChemotherapy() {
        PatientData data = createPatientWithNSCLCAndStage("Stage IV");
        data.setMedications(List.of(createMedication("Cisplatin")));
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion priorTherapyCriterion = findCriterion(assessment, "No Prior Systemic Therapy");
        assertNotNull(priorTherapyCriterion);
        assertEquals(CriterionStatus.NOT_MET, priorTherapyCriterion.getStatus());
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, assessment.getOverallStatus());
    }

    @Test
    public void testEvaluatePriorTherapy_WithImmunotherapy() {
        PatientData data = createPatientWithNSCLCAndStage("Stage IV");
        data.setMedications(List.of(createMedication("Pembrolizumab")));
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion priorTherapyCriterion = findCriterion(assessment, "No Prior Systemic Therapy");
        assertNotNull(priorTherapyCriterion);
        assertEquals(CriterionStatus.NOT_MET, priorTherapyCriterion.getStatus());
    }

    // ========== Brain Metastases Tests ==========

    @Test
    public void testEvaluateBrainMetastases_ActiveMetastases() {
        PatientData data = createPatientWithAge(50);
        Condition brainMets = createCondition("94225005", "Brain metastases");
        brainMets.setClinicalStatus(createClinicalStatus("active"));
        data.setConditions(List.of(brainMets));
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion brainMetsCriterion = findCriterion(assessment, "No Active Brain Metastases");
        assertNotNull(brainMetsCriterion);
        assertEquals(CriterionStatus.NOT_MET, brainMetsCriterion.getStatus());
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, assessment.getOverallStatus());
    }

    @Test
    public void testEvaluateBrainMetastases_NoMetastases() {
        PatientData data = createPatientWithAge(50);
        data.setConditions(new ArrayList<>());
        data.setProcedures(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion brainMetsCriterion = findCriterion(assessment, "No Active Brain Metastases");
        assertNotNull(brainMetsCriterion);
        assertEquals(CriterionStatus.UNKNOWN, brainMetsCriterion.getStatus());
    }

    @Test
    public void testEvaluateBrainMetastases_TreatedMetastases() {
        PatientData data = createPatientWithAge(50);
        Condition brainMets = createCondition("94225005", "Brain metastases");
        brainMets.setClinicalStatus(createClinicalStatus("resolved"));
        data.setConditions(List.of(brainMets));
        data.setProcedures(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        EligibilityCriterion brainMetsCriterion = findCriterion(assessment, "No Active Brain Metastases");
        assertNotNull(brainMetsCriterion);
        assertEquals(CriterionStatus.MET, brainMetsCriterion.getStatus());
    }

    // ========== Overall Status Tests ==========

    @Test
    public void testDetermineOverallStatus_AllCriteriaMet() {
        PatientData data = createFullyEligiblePatient();
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        assertEquals(EligibilityStatus.ELIGIBLE, assessment.getOverallStatus());
        assertNull(assessment.getIneligibilityReason());
    }

    @Test
    public void testDetermineOverallStatus_WithUnknownCriteria() {
        PatientData data = createPatientWithAge(50);
        data.setConditions(List.of(createConditionWithStage("254637007", "NSCLC", "Stage IV")));
        data.setObservations(new ArrayList<>()); // No lab results
        data.setMedications(new ArrayList<>());
        data.setProcedures(new ArrayList<>());
        
        EligibilityAssessment assessment = evaluator.evaluate(data);
        
        assertEquals(EligibilityStatus.POTENTIALLY_ELIGIBLE, assessment.getOverallStatus());
        assertFalse(assessment.getMissingDataElements().isEmpty());
    }

    // ========== Helper Methods ==========

    private PatientData createPatientWithAge(int age) {
        PatientData data = new PatientData();
        Patient patient = new Patient();
        
        LocalDate birthDate = LocalDate.now().minusYears(age);
        Date birthDateAsDate = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        patient.setBirthDate(birthDateAsDate);
        
        data.setPatient(patient);
        data.setConditions(new ArrayList<>());
        data.setObservations(new ArrayList<>());
        data.setMedications(new ArrayList<>());
        data.setProcedures(new ArrayList<>());
        
        return data;
    }

    private PatientData createPatientWithCondition(String snomedCode, String display) {
        PatientData data = createPatientWithAge(50);
        Condition condition = createCondition(snomedCode, display);
        data.setConditions(List.of(condition));
        return data;
    }

    private Condition createCondition(String snomedCode, String display) {
        Condition condition = new Condition();
        CodeableConcept code = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode(snomedCode);
        coding.setDisplay(display);
        code.addCoding(coding);
        condition.setCode(code);
        return condition;
    }

    private PatientData createPatientWithNSCLCAndStage(String stage) {
        PatientData data = createPatientWithAge(50);
        Condition condition = createConditionWithStage("254637007", "Non-small cell lung cancer", stage);
        data.setConditions(List.of(condition));
        return data;
    }

    private Condition createConditionWithStage(String snomedCode, String display, String stage) {
        Condition condition = createCondition(snomedCode, display);
        Condition.ConditionStageComponent stageComponent = new Condition.ConditionStageComponent();
        CodeableConcept stageSummary = new CodeableConcept();
        stageSummary.setText(stage);
        stageComponent.setSummary(stageSummary);
        condition.addStage(stageComponent);
        return condition;
    }

    private PatientData createPatientWithEcog(int ecogValue) {
        PatientData data = createPatientWithAge(50);
        Observation ecogObs = createObservation("89247-1", ecogValue);
        data.setObservations(List.of(ecogObs));
        return data;
    }

    private Observation createObservation(String loincCode, int value) {
        Observation obs = new Observation();
        CodeableConcept code = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem("http://loinc.org");
        coding.setCode(loincCode);
        code.addCoding(coding);
        obs.setCode(code);
        obs.setValue(new IntegerType(value));
        obs.setEffective(new DateTimeType(new Date()));
        return obs;
    }

    private PatientData createPatientWithLabResult(String loincCode, BigDecimal value) {
        PatientData data = createPatientWithAge(50);
        Observation labObs = createLabObservation(loincCode, value);
        data.setObservations(List.of(labObs));
        return data;
    }

    private Observation createLabObservation(String loincCode, BigDecimal value) {
        Observation obs = new Observation();
        CodeableConcept code = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem("http://loinc.org");
        coding.setCode(loincCode);
        code.addCoding(coding);
        obs.setCode(code);
        
        Quantity quantity = new Quantity();
        quantity.setValue(value);
        quantity.setUnit("g/dL");
        obs.setValue(quantity);
        obs.setEffective(new DateTimeType(new Date()));
        
        return obs;
    }

    private MedicationStatement createMedication(String medicationName) {
        MedicationStatement med = new MedicationStatement();
        CodeableConcept medCode = new CodeableConcept();
        medCode.setText(medicationName);
        med.setMedication(medCode);
        return med;
    }

    private CodeableConcept createClinicalStatus(String status) {
        CodeableConcept clinicalStatus = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode(status);
        clinicalStatus.addCoding(coding);
        return clinicalStatus;
    }

    private PatientData createFullyEligiblePatient() {
        PatientData data = createPatientWithAge(50);
        
        // Add NSCLC diagnosis with Stage IV
        Condition nsclc = createConditionWithStage("254637007", "Non-small cell lung cancer", "Stage IV");
        data.setConditions(List.of(nsclc));
        
        // Add all required lab results
        List<Observation> observations = new ArrayList<>();
        observations.add(createObservation("89247-1", 1)); // ECOG
        observations.add(createLabObservation("718-7", new BigDecimal("11.0"))); // Hemoglobin
        observations.add(createLabObservation("751-8", new BigDecimal("2000"))); // Neutrophils
        observations.add(createLabObservation("777-3", new BigDecimal("150000"))); // Platelets
        data.setObservations(observations);
        
        // Add non-cancer medication to have medication data
        MedicationStatement nonCancerMed = new MedicationStatement();
        CodeableConcept medCode = new CodeableConcept();
        medCode.setText("Aspirin");
        nonCancerMed.setMedication(medCode);
        data.setMedications(List.of(nonCancerMed));
        
        // Add non-brain procedure to have procedure data
        Procedure nonBrainProc = new Procedure();
        CodeableConcept procCode = new CodeableConcept();
        procCode.setText("Chest X-ray");
        nonBrainProc.setCode(procCode);
        data.setProcedures(List.of(nonBrainProc));
        
        return data;
    }

    private EligibilityCriterion findCriterion(EligibilityAssessment assessment, String name) {
        return assessment.getCriteria().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
