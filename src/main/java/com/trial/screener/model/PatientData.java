package com.trial.screener.model;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PatientData {
    private String patientId;
    private Patient patient;
    private List<Condition> conditions;
    private List<Observation> observations;
    private List<MedicationStatement> medications;
    private List<Procedure> procedures;

    public PatientData() {
        this.conditions = new ArrayList<>();
        this.observations = new ArrayList<>();
        this.medications = new ArrayList<>();
        this.procedures = new ArrayList<>();
    }

    public PatientData(String patientId, Patient patient, List<Condition> conditions,
                       List<Observation> observations, List<MedicationStatement> medications,
                       List<Procedure> procedures) {
        this.patientId = patientId;
        this.patient = patient;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
        this.observations = observations != null ? observations : new ArrayList<>();
        this.medications = medications != null ? medications : new ArrayList<>();
        this.procedures = procedures != null ? procedures : new ArrayList<>();
    }

    /**
     * Calculate patient age from birth date
     * @return Optional containing age in years, or empty if birth date is not available
     */
    public Optional<Integer> getAge() {
        if (patient == null || !patient.hasBirthDate()) {
            return Optional.empty();
        }
        
        Date birthDate = patient.getBirthDate();
        LocalDate birthLocalDate = birthDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate now = LocalDate.now();
        
        return Optional.of(Period.between(birthLocalDate, now).getYears());
    }

    /**
     * Filter conditions to return only lung cancer related conditions
     * @return List of lung cancer conditions
     */
    public List<Condition> getLungCancerConditions() {
        return conditions.stream()
                .filter(this::isLungCancerCondition)
                .collect(Collectors.toList());
    }

    private boolean isLungCancerCondition(Condition condition) {
        if (!condition.hasCode() || !condition.getCode().hasCoding()) {
            return false;
        }
        
        // Check for SNOMED codes: 254637007 (NSCLC), 424132000 (Lung cancer)
        return condition.getCode().getCoding().stream()
                .anyMatch(coding -> 
                    "254637007".equals(coding.getCode()) || 
                    "424132000".equals(coding.getCode()) ||
                    (coding.hasDisplay() && coding.getDisplay().toLowerCase()
                            .contains("non-small cell lung cancer")) ||
                    (coding.hasDisplay() && coding.getDisplay().toLowerCase()
                            .contains("lung cancer"))
                );
    }

    /**
     * Get the most recent lab result for a specific LOINC code
     * @param loincCode The LOINC code to search for
     * @return Optional containing the most recent observation, or empty if not found
     */
    public Optional<Observation> getLatestLabResult(String loincCode) {
        return observations.stream()
                .filter(obs -> hasLoincCode(obs, loincCode))
                .filter(Observation::hasEffectiveDateTimeType)
                .max(Comparator.comparing(obs -> obs.getEffectiveDateTimeType().getValue()));
    }

    private boolean hasLoincCode(Observation observation, String loincCode) {
        if (!observation.hasCode() || !observation.getCode().hasCoding()) {
            return false;
        }
        
        return observation.getCode().getCoding().stream()
                .anyMatch(coding -> 
                    "http://loinc.org".equals(coding.getSystem()) && 
                    loincCode.equals(coding.getCode())
                );
    }

    // Getters and setters
    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public List<Observation> getObservations() {
        return observations;
    }

    public void setObservations(List<Observation> observations) {
        this.observations = observations != null ? observations : new ArrayList<>();
    }

    public List<MedicationStatement> getMedications() {
        return medications;
    }

    public void setMedications(List<MedicationStatement> medications) {
        this.medications = medications != null ? medications : new ArrayList<>();
    }

    public List<Procedure> getProcedures() {
        return procedures;
    }

    public void setProcedures(List<Procedure> procedures) {
        this.procedures = procedures != null ? procedures : new ArrayList<>();
    }
}
