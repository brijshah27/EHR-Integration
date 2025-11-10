package com.trial.screener.model;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PatientData convenience methods
 */
public class PatientDataTest {

    // ========== getAge() Tests ==========

    @Test
    public void testGetAge_ValidBirthDate() {
        PatientData data = new PatientData();
        Patient patient = new Patient();
        
        LocalDate birthDate = LocalDate.now().minusYears(45);
        Date birthDateAsDate = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        patient.setBirthDate(birthDateAsDate);
        
        data.setPatient(patient);
        
        Optional<Integer> age = data.getAge();
        assertTrue(age.isPresent());
        assertEquals(45, age.get());
    }

    @Test
    public void testGetAge_YoungPatient() {
        PatientData data = new PatientData();
        Patient patient = new Patient();
        
        LocalDate birthDate = LocalDate.now().minusYears(5);
        Date birthDateAsDate = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        patient.setBirthDate(birthDateAsDate);
        
        data.setPatient(patient);
        
        Optional<Integer> age = data.getAge();
        assertTrue(age.isPresent());
        assertEquals(5, age.get());
    }

    @Test
    public void testGetAge_ElderlyPatient() {
        PatientData data = new PatientData();
        Patient patient = new Patient();
        
        LocalDate birthDate = LocalDate.now().minusYears(85);
        Date birthDateAsDate = Date.from(birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        patient.setBirthDate(birthDateAsDate);
        
        data.setPatient(patient);
        
        Optional<Integer> age = data.getAge();
        assertTrue(age.isPresent());
        assertEquals(85, age.get());
    }

    @Test
    public void testGetAge_NoBirthDate() {
        PatientData data = new PatientData();
        Patient patient = new Patient();
        // No birth date set
        
        data.setPatient(patient);
        
        Optional<Integer> age = data.getAge();
        assertFalse(age.isPresent());
    }

    @Test
    public void testGetAge_NoPatient() {
        PatientData data = new PatientData();
        // No patient set
        
        Optional<Integer> age = data.getAge();
        assertFalse(age.isPresent());
    }

    // ========== getLungCancerConditions() Tests ==========

    @Test
    public void testGetLungCancerConditions_NSCLCWithSnomedCode() {
        PatientData data = new PatientData();
        
        Condition nsclc = createCondition("254637007", "Non-small cell lung cancer");
        Condition diabetes = createCondition("73211009", "Diabetes mellitus");
        
        data.setConditions(List.of(nsclc, diabetes));
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertEquals(1, lungCancerConditions.size());
        assertEquals("254637007", lungCancerConditions.get(0).getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testGetLungCancerConditions_LungCancerCode() {
        PatientData data = new PatientData();
        
        Condition lungCancer = createCondition("424132000", "Malignant tumor of lung");
        Condition hypertension = createCondition("38341003", "Hypertension");
        
        data.setConditions(List.of(lungCancer, hypertension));
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertEquals(1, lungCancerConditions.size());
        assertEquals("424132000", lungCancerConditions.get(0).getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testGetLungCancerConditions_TextMatching() {
        PatientData data = new PatientData();
        
        Condition nsclc = new Condition();
        CodeableConcept code = new CodeableConcept();
        Coding coding = new Coding();
        coding.setDisplay("Non-small cell lung cancer");
        code.addCoding(coding);
        nsclc.setCode(code);
        
        Condition other = createCondition("12345", "Other condition");
        
        data.setConditions(List.of(nsclc, other));
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertEquals(1, lungCancerConditions.size());
    }

    @Test
    public void testGetLungCancerConditions_NoLungCancer() {
        PatientData data = new PatientData();
        
        Condition diabetes = createCondition("73211009", "Diabetes mellitus");
        Condition hypertension = createCondition("38341003", "Hypertension");
        
        data.setConditions(List.of(diabetes, hypertension));
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertTrue(lungCancerConditions.isEmpty());
    }

    @Test
    public void testGetLungCancerConditions_EmptyConditions() {
        PatientData data = new PatientData();
        data.setConditions(new ArrayList<>());
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertTrue(lungCancerConditions.isEmpty());
    }

    @Test
    public void testGetLungCancerConditions_MultipleMatches() {
        PatientData data = new PatientData();
        
        Condition nsclc1 = createCondition("254637007", "Non-small cell lung cancer");
        Condition nsclc2 = createCondition("424132000", "Malignant tumor of lung");
        Condition diabetes = createCondition("73211009", "Diabetes mellitus");
        
        data.setConditions(List.of(nsclc1, nsclc2, diabetes));
        
        List<Condition> lungCancerConditions = data.getLungCancerConditions();
        assertEquals(2, lungCancerConditions.size());
    }

    // ========== getLatestLabResult() Tests ==========

    @Test
    public void testGetLatestLabResult_SingleObservation() {
        PatientData data = new PatientData();
        
        Observation hemoglobin = createLabObservation("718-7", new BigDecimal("11.5"), 
            LocalDate.now().minusDays(5));
        
        data.setObservations(List.of(hemoglobin));
        
        Optional<Observation> result = data.getLatestLabResult("718-7");
        assertTrue(result.isPresent());
        assertEquals("718-7", result.get().getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testGetLatestLabResult_MultipleObservations() {
        PatientData data = new PatientData();
        
        Observation older = createLabObservation("718-7", new BigDecimal("10.0"), 
            LocalDate.now().minusDays(20));
        Observation newer = createLabObservation("718-7", new BigDecimal("11.5"), 
            LocalDate.now().minusDays(5));
        Observation oldest = createLabObservation("718-7", new BigDecimal("9.5"), 
            LocalDate.now().minusDays(30));
        
        data.setObservations(List.of(older, newer, oldest));
        
        Optional<Observation> result = data.getLatestLabResult("718-7");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("11.5"), 
            ((Quantity) result.get().getValue()).getValue());
    }

    @Test
    public void testGetLatestLabResult_DifferentLoincCodes() {
        PatientData data = new PatientData();
        
        Observation hemoglobin = createLabObservation("718-7", new BigDecimal("11.5"), 
            LocalDate.now().minusDays(5));
        Observation neutrophil = createLabObservation("751-8", new BigDecimal("2000"), 
            LocalDate.now().minusDays(3));
        
        data.setObservations(List.of(hemoglobin, neutrophil));
        
        Optional<Observation> result = data.getLatestLabResult("718-7");
        assertTrue(result.isPresent());
        assertEquals("718-7", result.get().getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testGetLatestLabResult_NotFound() {
        PatientData data = new PatientData();
        
        Observation hemoglobin = createLabObservation("718-7", new BigDecimal("11.5"), 
            LocalDate.now().minusDays(5));
        
        data.setObservations(List.of(hemoglobin));
        
        Optional<Observation> result = data.getLatestLabResult("751-8");
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetLatestLabResult_EmptyObservations() {
        PatientData data = new PatientData();
        data.setObservations(new ArrayList<>());
        
        Optional<Observation> result = data.getLatestLabResult("718-7");
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetLatestLabResult_ObservationWithoutDate() {
        PatientData data = new PatientData();
        
        Observation withDate = createLabObservation("718-7", new BigDecimal("11.5"), 
            LocalDate.now().minusDays(5));
        
        Observation withoutDate = new Observation();
        CodeableConcept code = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem("http://loinc.org");
        coding.setCode("718-7");
        code.addCoding(coding);
        withoutDate.setCode(code);
        // No effective date set
        
        data.setObservations(List.of(withDate, withoutDate));
        
        Optional<Observation> result = data.getLatestLabResult("718-7");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("11.5"), 
            ((Quantity) result.get().getValue()).getValue());
    }

    // ========== Helper Methods ==========

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

    private Observation createLabObservation(String loincCode, BigDecimal value, LocalDate date) {
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
        
        Date effectiveDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        obs.setEffective(new DateTimeType(effectiveDate));
        
        return obs;
    }
}
