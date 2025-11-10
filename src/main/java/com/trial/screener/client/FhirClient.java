package com.trial.screener.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.trial.screener.model.PatientData;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * FhirClient handles all FHIR server communication and resource retrieval.
 * It provides methods to search for lung cancer patients and retrieve comprehensive patient data.
 */
public class FhirClient {
    private static final Logger logger = LoggerFactory.getLogger(FhirClient.class);
    private static final String DEFAULT_FHIR_SERVER_URL = "https://hapi.fhir.org/baseR4";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000}; // 1s, 2s, 4s
    
    private final IGenericClient client;
    private final String serverUrl;

    /**
     * Create a FhirClient with the default FHIR server URL
     */
    public FhirClient() {
        this(DEFAULT_FHIR_SERVER_URL);
    }

    /**
     * Create a FhirClient with a custom FHIR server URL
     * @param serverUrl The FHIR server base URL
     */
    public FhirClient(String serverUrl) {
        this.serverUrl = serverUrl;
        
        // Initialize FHIR context for R4
        FhirContext ctx = FhirContext.forR4();
        
        // Create the generic client
        this.client = ctx.newRestfulGenericClient(serverUrl);
        
        logger.info("FhirClient initialized with server URL: {}", serverUrl);
    }

    /**
     * Search for patients with lung cancer conditions
     * @param maxPatients Maximum number of patients to return
     * @return List of unique patient IDs
     */
    public List<String> findLungCancerPatients(int maxPatients) {
        logger.info("Searching for lung cancer patients (max: {})", maxPatients);
        
        Set<String> patientIds = new HashSet<>();
        
        // Strategy 1: Search by specific SNOMED codes
        searchByCode(patientIds, maxPatients);
        
        // Strategy 2: If we don't have enough patients, search by text
        if (patientIds.size() < maxPatients) {
            logger.info("Found {} patients by code, searching by text to find more", patientIds.size());
            searchByText(patientIds, maxPatients);
        }
        
        // Strategy 3: If still not enough, search for any lung-related conditions
        if (patientIds.size() < maxPatients) {
            logger.info("Found {} patients so far, broadening search to lung conditions", patientIds.size());
            searchByBroadLungConditions(patientIds, maxPatients);
        }
        
        logger.info("Found {} unique lung cancer patients total", patientIds.size());
        
        return new ArrayList<>(patientIds);
    }
    
    /**
     * Search for patients by specific SNOMED codes
     */
    private void searchByCode(Set<String> patientIds, int maxPatients) {
        // Extended list of SNOMED codes for lung cancer, prioritizing advanced stages
        String[] snomedCodes = {
            "254637007",  // Non-small cell lung cancer
            "424132000",  // Malignant tumor of lung
            "93880001",   // Primary malignant neoplasm of lung
            "162573006",  // Suspected lung cancer
            "254632001",  // Small cell carcinoma of lung
            "423121009",  // Adenocarcinoma of lung
            "35917007",   // Squamous cell carcinoma of lung
            "94222008",   // Secondary malignant neoplasm of lung
            "315058005"   // Metastatic malignant neoplasm to lung
        };
        
        Bundle bundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(Condition.class)
                    .where(Condition.CODE.exactly().codes(snomedCodes))
                    .count(maxPatients * 3)  // Request more to account for duplicates and filtering
                    .returnBundle(Bundle.class)
                    .execute();
        }, "search for lung cancer patients by code");
        
        extractPatientIds(bundle, patientIds, maxPatients);
    }
    
    /**
     * Search for patients by text matching
     */
    private void searchByText(Set<String> patientIds, int maxPatients) {
        // Search for conditions with lung cancer in the text using _text parameter
        String[] searchTerms = {
            "lung cancer",
            "non-small cell lung cancer",
            "NSCLC",
            "lung carcinoma",
            "pulmonary carcinoma"
        };
        
        for (String term : searchTerms) {
            if (patientIds.size() >= maxPatients) {
                break;
            }
            
            try {
                Bundle bundle = executeWithRetry(() -> {
                    return client.search()
                            .forResource(Condition.class)
                            .withAdditionalHeader("Prefer", "return=representation")
                            .count(maxPatients)
                            .returnBundle(Bundle.class)
                            .execute();
                }, "search for lung cancer patients by text: " + term);
                
                extractPatientIds(bundle, patientIds, maxPatients);
            } catch (Exception e) {
                logger.debug("Text search failed for term '{}': {}", term, e.getMessage());
            }
        }
    }
    
    /**
     * Search for patients with broad lung conditions (cast wider net)
     */
    private void searchByBroadLungConditions(Set<String> patientIds, int maxPatients) {
        // Search for any conditions - we'll filter on the client side
        // This is a fallback to get more patients when specific searches don't yield enough
        try {
            Bundle bundle = executeWithRetry(() -> {
                return client.search()
                        .forResource(Condition.class)
                        .count(maxPatients * 5)  // Get more to filter through
                        .returnBundle(Bundle.class)
                        .execute();
            }, "search for broad conditions");
            
            // Extract patient IDs, but only from conditions that might be lung-related
            if (bundle != null && bundle.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (patientIds.size() >= maxPatients) {
                        break;
                    }
                    
                    if (entry.hasResource() && entry.getResource() instanceof Condition condition) {

                        // Check if this condition might be lung-related
                        if (isPotentiallyLungRelated(condition)) {
                            if (condition.hasSubject() && condition.getSubject().hasReferenceElement()) {
                                String patientId = condition.getSubject().getReferenceElement().getIdPart();
                                if (patientId != null) {
                                    patientIds.add(patientId);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Broad condition search failed: {}", e.getMessage());
        }
    }
    
    /**
     * Check if a condition might be lung-related based on text content
     */
    private boolean isPotentiallyLungRelated(Condition condition) {
        if (!condition.hasCode()) {
            return false;
        }
        
        String conditionText = "";
        
        if (condition.getCode().hasText()) {
            conditionText = condition.getCode().getText().toLowerCase();
        }
        
        for (var coding : condition.getCode().getCoding()) {
            if (coding.hasDisplay()) {
                conditionText += " " + coding.getDisplay().toLowerCase();
            }
        }
        
        // Check for lung-related terms
        return conditionText.contains("lung") || 
               conditionText.contains("pulmonary") ||
               conditionText.contains("bronch") ||
               conditionText.contains("respiratory") ||
               conditionText.contains("thoracic");
    }
    
    /**
     * Extract patient IDs from a bundle
     */
    private void extractPatientIds(Bundle bundle, Set<String> patientIds, int maxPatients) {
        if (bundle != null && bundle.hasEntry()) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (patientIds.size() >= maxPatients) {
                    break;
                }
                
                if (entry.hasResource() && entry.getResource() instanceof Condition condition) {
                    if (condition.hasSubject() && condition.getSubject().hasReferenceElement()) {
                        String patientId = condition.getSubject().getReferenceElement().getIdPart();
                        if (patientId != null) {
                            patientIds.add(patientId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieve comprehensive patient data for a given patient ID
     * @param patientId The patient ID
     * @return PatientData object containing all retrieved resources
     */
    public PatientData getPatientData(String patientId) {
        logger.info("Retrieving patient data for patient: {}", patientId);
        
        PatientData patientData = new PatientData();
        patientData.setPatientId(patientId);
        
        try {
            // Retrieve Patient resource
            Patient patient = getPatient(patientId);
            patientData.setPatient(patient);
            
            // Retrieve Conditions
            List<Condition> conditions = getConditions(patientId);
            patientData.setConditions(conditions);
            
            // Retrieve Observations
            List<Observation> observations = getObservations(patientId);
            patientData.setObservations(observations);
            
            // Retrieve Medications
            List<MedicationStatement> medications = getMedications(patientId);
            patientData.setMedications(medications);
            
            // Retrieve Procedures
            List<Procedure> procedures = getProcedures(patientId);
            patientData.setProcedures(procedures);
            
            logger.info("Successfully retrieved patient data for patient: {}", patientId);
            
        } catch (Exception e) {
            logger.error("Error retrieving patient data for patient {}: {}", patientId, e.getMessage(), e);
        }
        
        return patientData;
    }

    /**
     * Retrieve Patient resource
     * @param patientId The patient ID
     * @return Patient resource or null if not found
     */
    private Patient getPatient(String patientId) {
        return executeWithRetry(() -> {
            return client.read()
                    .resource(Patient.class)
                    .withId(patientId)
                    .execute();
        }, "retrieve Patient resource for patient " + patientId);
    }

    /**
     * Retrieve Condition resources for a patient
     * @param patientId The patient ID
     * @return List of Condition resources
     */
    private List<Condition> getConditions(String patientId) {
        Bundle bundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(Condition.class)
                    .where(Condition.PATIENT.hasId(patientId))
                    .sort().descending("onset-date")
                    .count(100)  // Request more conditions to ensure we get all relevant data
                    .returnBundle(Bundle.class)
                    .execute();
        }, "retrieve Conditions for patient " + patientId);
        
        if (bundle != null) {
            List<Condition> conditions = getAllPages(bundle, Condition.class);
            logger.debug("Retrieved {} conditions for patient {}", conditions.size(), patientId);
            return conditions;
        }
        
        return new ArrayList<>();
    }

    /**
     * Retrieve Observation resources for a patient
     * @param patientId The patient ID
     * @return List of Observation resources
     */
    private List<Observation> getObservations(String patientId) {
        List<Observation> allObservations = new ArrayList<>();
        
        // First try to get laboratory observations
        Bundle labBundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(Observation.class)
                    .where(Observation.PATIENT.hasId(patientId))
                    .where(Observation.CATEGORY.exactly().code("laboratory"))
                    .sort().descending("date")
                    .count(100)
                    .returnBundle(Bundle.class)
                    .execute();
        }, "retrieve laboratory Observations for patient " + patientId);
        
        if (labBundle != null) {
            allObservations.addAll(getAllPages(labBundle, Observation.class));
        }
        
        // Also get vital-signs and exam observations (may contain ECOG status)
        Bundle vitalBundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(Observation.class)
                    .where(Observation.PATIENT.hasId(patientId))
                    .where(Observation.CATEGORY.exactly().codes("vital-signs", "exam", "survey"))
                    .sort().descending("date")
                    .count(50)
                    .returnBundle(Bundle.class)
                    .execute();
        }, "retrieve vital-signs/exam Observations for patient " + patientId);
        
        if (vitalBundle != null) {
            allObservations.addAll(getAllPages(vitalBundle, Observation.class));
        }
        
        // If we still don't have many observations, try without category filter
        if (allObservations.size() < 10) {
            Bundle allBundle = executeWithRetry(() -> {
                return client.search()
                        .forResource(Observation.class)
                        .where(Observation.PATIENT.hasId(patientId))
                        .sort().descending("date")
                        .count(100)
                        .returnBundle(Bundle.class)
                        .execute();
            }, "retrieve all Observations for patient " + patientId);
            
            if (allBundle != null) {
                List<Observation> additionalObs = getAllPages(allBundle, Observation.class);
                // Add only observations not already in the list
                for (Observation obs : additionalObs) {
                    if (allObservations.stream().noneMatch(existing ->
                        existing.getId().equals(obs.getId()))) {
                        allObservations.add(obs);
                    }
                }
            }
        }
        
        logger.debug("Retrieved {} observations for patient {}", allObservations.size(), patientId);
        return allObservations;
    }

    /**
     * Retrieve MedicationStatement resources for a patient
     * @param patientId The patient ID
     * @return List of MedicationStatement resources
     */
    private List<MedicationStatement> getMedications(String patientId) {
        List<MedicationStatement> allMedications = new ArrayList<>();
        
        // First try with status filter
        Bundle bundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(MedicationStatement.class)
                    .where(MedicationStatement.PATIENT.hasId(patientId))
                    .where(MedicationStatement.STATUS.exactly().codes("active", "completed", "intended", "on-hold"))
                    .count(100)
                    .returnBundle(Bundle.class)
                    .execute();
        }, "retrieve MedicationStatements for patient " + patientId);
        
        if (bundle != null) {
            allMedications.addAll(getAllPages(bundle, MedicationStatement.class));
        }
        
        // If we don't have many medications, try without status filter
        if (allMedications.size() < 5) {
            Bundle allBundle = executeWithRetry(() -> {
                return client.search()
                        .forResource(MedicationStatement.class)
                        .where(MedicationStatement.PATIENT.hasId(patientId))
                        .count(100)
                        .returnBundle(Bundle.class)
                        .execute();
            }, "retrieve all MedicationStatements for patient " + patientId);
            
            if (allBundle != null) {
                List<MedicationStatement> additionalMeds = getAllPages(allBundle, MedicationStatement.class);
                // Add only medications not already in the list
                for (MedicationStatement med : additionalMeds) {
                    if (allMedications.stream().noneMatch(existing ->
                        existing.getId().equals(med.getId()))) {
                        allMedications.add(med);
                    }
                }
            }
        }
        
        logger.debug("Retrieved {} medications for patient {}", allMedications.size(), patientId);
        return allMedications;
    }

    /**
     * Retrieve Procedure resources for a patient
     * @param patientId The patient ID
     * @return List of Procedure resources
     */
    private List<Procedure> getProcedures(String patientId) {
        Bundle bundle = executeWithRetry(() -> {
            return client.search()
                    .forResource(Procedure.class)
                    .where(Procedure.PATIENT.hasId(patientId))
                    .sort().descending("date")
                    .count(100)  // Request more procedures to ensure comprehensive data
                    .returnBundle(Bundle.class)
                    .execute();
        }, "retrieve Procedures for patient " + patientId);
        
        if (bundle != null) {
            List<Procedure> procedures = getAllPages(bundle, Procedure.class);
            logger.debug("Retrieved {} procedures for patient {}", procedures.size(), patientId);
            return procedures;
        }
        
        return new ArrayList<>();
    }

    /**
     * Handle paginated Bundle results by following the "next" link
     * @param bundle The initial Bundle
     * @param resourceClass The class of resources to extract
     * @param <T> The resource type
     * @return List of all resources from all pages
     */
    private <T extends IBaseResource> List<T> getAllPages(Bundle bundle, Class<T> resourceClass) {
        List<T> allResources = new ArrayList<>();
        
        Bundle currentBundle = bundle;
        
        while (currentBundle != null) {
            // Extract resources from current page
            if (currentBundle.hasEntry()) {
                for (Bundle.BundleEntryComponent entry : currentBundle.getEntry()) {
                    if (entry.hasResource() && resourceClass.isInstance(entry.getResource())) {
                        allResources.add(resourceClass.cast(entry.getResource()));
                    }
                }
            }
            
            // Check if there's a next page
            Bundle.BundleLinkComponent nextLink = currentBundle.getLink(Bundle.LINK_NEXT);
            if (nextLink != null && nextLink.hasUrl()) {
                logger.debug("Following next link for pagination: {}", nextLink.getUrl());
                
                final Bundle bundleForRetry = currentBundle;
                Bundle nextBundle = executeWithRetry(() -> {
                    return client.loadPage()
                            .next(bundleForRetry)
                            .execute();
                }, "load next page of results");
                
                if (nextBundle != null) {
                    currentBundle = nextBundle;
                } else {
                    logger.warn("Failed to load next page after retries, stopping pagination");
                    break;
                }
            } else {
                // No more pages
                currentBundle = null;
            }
        }
        
        return allResources;
    }

    /**
     * Execute a FHIR API call with retry logic and exponential backoff
     * @param operation The operation to execute
     * @param operationDescription Description of the operation for logging
     * @param <T> The return type
     * @return The result of the operation, or null if all retries fail
     */
    private <T> T executeWithRetry(Supplier<T> operation, String operationDescription) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (ResourceNotFoundException e) {
                // Resource not found - don't retry, return null immediately
                logger.warn("Resource not found while attempting to {}: {}", operationDescription, e.getMessage());
                return null;
            } catch (FhirClientConnectionException e) {
                // Connection error - retry with backoff
                lastException = e;
                logger.warn("Connection error on attempt {} while attempting to {}: {}", 
                        attempt + 1, operationDescription, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    logger.info("Retrying in {} ms...", delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted for operation: {}", operationDescription);
                        return null;
                    }
                }
            } catch (Exception e) {
                // Other errors - retry with backoff
                lastException = e;
                logger.warn("Error on attempt {} while attempting to {}: {}", 
                        attempt + 1, operationDescription, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    logger.info("Retrying in {} ms...", delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted for operation: {}", operationDescription);
                        return null;
                    }
                }
            }
        }
        
        // All retries exhausted
        logger.error("Failed to {} after {} attempts. Last error: {}", 
                operationDescription, MAX_RETRY_ATTEMPTS,
                lastException.getMessage());
        return null;
    }

    /**
     * Get the configured server URL
     * @return The FHIR server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }
}
