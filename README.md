# FHIR Clinical Trial Eligibility Screener

A Java-based command-line application that queries FHIR R4 servers to identify patients eligible for a Phase II oncology clinical trial targeting advanced non-small cell lung cancer (NSCLC).

## Approach Overview

This screener implements a modular, production-ready architecture with three core components:

1. **FHIR Client** - Handles all communication with FHIR servers, including multi-strategy patient discovery, comprehensive data retrieval, pagination handling, and retry logic with exponential backoff
2. **Eligibility Evaluator** - Evaluates patients against 9 trial-specific criteria (7 inclusion, 2 exclusion) using standard medical terminologies (LOINC, SNOMED CT)
3. **Report Generator** - Produces detailed eligibility reports with criterion-by-criterion assessment and missing data analysis

The system uses a three-state evaluation model (MET, NOT_MET, UNKNOWN) to gracefully handle incomplete data, which is common in real-world EHR systems. Patients are classified as ELIGIBLE, NOT ELIGIBLE, or POTENTIALLY ELIGIBLE (when data is missing but all known criteria pass).

Key design decisions:
- **Parallel processing** using Java CompletableFuture for concurrent patient evaluation (configurable thread pool, default 10 threads)
- **Multi-strategy patient discovery** combining code-based search, text search, and broad filtering to maximize patient identification
- **Robust error handling** with retry logic, graceful degradation, and comprehensive logging
- **Clinical accuracy** using standard LOINC codes for labs and extended SNOMED CT codes for diagnoses
- **Extensibility** through clear separation of concerns and modular architecture

## Setup and Run Instructions

### Prerequisites

- **Java 17** or higher
- **Maven 3.8+**
- Internet connection to access FHIR test server

### Quick Start

1. **Verify Java and Maven installation:**
```bash
java -version  # Should show Java 17+
mvn -version   # Should show Maven 3.8+
```

2. **Build the project:**
```bash
mvn clean install
```

This downloads dependencies (HAPI FHIR 6.10.0, SLF4J, JUnit, Mockito), compiles the code, and runs unit tests.

3. **Run the screener:**
```bash
# Screen up to 100 patients (default)
mvn exec:java -Dexec.mainClass="com.trial.screener.main.TrialScreenerApp"

# Screen a specific number of patients
mvn exec:java -Dexec.mainClass="com.trial.screener.main.TrialScreenerApp" -Dexec.args="25"
```

4. **Alternative - Run compiled JAR:**
```bash
mvn package
java -jar target/fhir-trial-screener-1.0.0.jar
# Or with patient limit: java -jar target/fhir-trial-screener-1.0.0.jar 25
```

### Configuration Options

**Change FHIR Server:**
Edit `src/main/java/com/trial/screener/main/TrialScreenerApp.java`:
```java
private static final String DEFAULT_FHIR_SERVER_URL = "https://your-server.com/fhir";
```

**Adjust Parallel Processing:**
Edit thread pool size in `TrialScreenerApp.java`:
```java
private static final int THREAD_POOL_SIZE = 10; // Increase for faster processing
```

**Run Tests:**
```bash
mvn test
```

## FHIR Server Used

**Server:** HAPI FHIR Public Test Server  
**URL:** https://hapi.fhir.org/baseR4  
**FHIR Version:** R4  
**Authentication:** None (public test server)

This is a publicly accessible FHIR test server maintained by the HAPI FHIR project. It contains synthetic patient data for testing and development purposes. The server supports standard FHIR R4 operations including search, read, and pagination.

The application can be configured to use any FHIR R4-compliant server by modifying the `DEFAULT_FHIR_SERVER_URL` constant in `TrialScreenerApp.java`. For production servers requiring authentication, the `FhirClient` class can be extended to add OAuth2 or basic authentication interceptors.

## Assumptions and Limitations

### Assumptions

1. **FHIR R4 Compliance** - Target server implements FHIR R4 standard with support for Patient, Condition, Observation, MedicationStatement, and Procedure resources
2. **Standard Terminologies** - Clinical data uses standard coding systems:
   - LOINC codes for laboratory observations (718-7 for hemoglobin, 751-8 for neutrophils, 777-3 for platelets, 89247-1 for ECOG)
   - SNOMED CT codes for diagnoses (254637007 for NSCLC, 424132000 for lung cancer, etc.)
3. **Lab Result Recency** - Laboratory results within 30 days are considered current for eligibility assessment
4. **Staging Format** - Disease staging is recorded in Condition.stage using standard formats (IIIB, 3B, IV, 4, etc.)
5. **Medication Completeness** - MedicationStatement resources accurately reflect patient's therapy history
6. **Screening Purpose** - This tool provides initial screening only; manual chart review is required for final eligibility determination

### Limitations

**Data Quality:**
- Test server contains synthetic data that may not reflect real-world completeness or accuracy
- Missing data is common in real EHR systems; system handles this by marking criteria as UNKNOWN and flagging patients as POTENTIALLY ELIGIBLE
- Terminology variations across EHR systems may cause some diagnoses or labs to be missed if non-standard codes are used

**Clinical Logic:**
- ECOG status must be recorded as structured observations; cannot parse from clinical notes
- Staging information must be in Condition.stage; staging documented only in notes will be missed
- Prior therapy detection relies on medication records; treatments not documented as MedicationStatements may be missed
- Brain metastases detection requires structured Condition resources; imaging reports in unstructured notes are not analyzed

**Technical Constraints:**
- Memory-based processing suitable for up to ~1,000 patients; larger datasets would require streaming or database storage
- No persistent storage; results output to console only (not saved to file or database)
- No authentication implemented; works with public test servers only (easily extensible for OAuth2/basic auth)
- Single FHIR server per execution; cannot aggregate data from multiple sources

**Scope:**
- Provides decision support only; does not replace clinical judgment
- Initial screening tool; manual verification of all criteria required before enrollment
- Does not analyze unstructured clinical notes or imaging reports
- Does not handle real-time updates; point-in-time assessment only

### Known Data Quality Issues

When running against the HAPI FHIR test server:
- ECOG performance status is frequently missing (not commonly recorded in test data)
- Disease staging may be incomplete or absent
- Laboratory results may be older than 30 days or missing entirely
- Medication histories may be incomplete

The system handles these issues gracefully by:
- Marking individual criteria as UNKNOWN when data is missing
- Classifying patients as POTENTIALLY ELIGIBLE when all available data passes but some criteria cannot be evaluated
- Providing detailed missing data reports to guide additional data collection
- Continuing to process all patients even when individual data retrieval fails

## Trial Eligibility Criteria

**Inclusion Criteria** (all must be met):
1. Age ≥18 years
2. NSCLC diagnosis (confirmed non-small cell lung cancer)
3. Stage IIIB or IV disease
4. ECOG Performance Status 0-2
5. Hemoglobin ≥9.0 g/dL
6. Absolute Neutrophil Count ≥1,500/µL
7. Platelet Count ≥100,000/µL

**Exclusion Criteria** (none can be present):
1. Prior systemic therapy for advanced disease
2. Active brain metastases

**Eligibility Status:**
- **ELIGIBLE** - All inclusion criteria met, no exclusion criteria
- **NOT ELIGIBLE** - One or more inclusion criteria failed, or exclusion criteria present
- **POTENTIALLY ELIGIBLE** - All known criteria pass, but some data missing

## Example Output

```
================================================================================
CLINICAL TRIAL ELIGIBILITY SCREENING REPORT
Trial: Phase II Advanced NSCLC Study
Date: 2025-11-10
================================================================================

Processing patients in parallel (10 threads)...
Progress: 25/25 patients processed

PATIENT SCREENING RESULTS
--------------------------------------------------------------------------------

Patient ID: patient-123
Overall Status: ELIGIBLE

Inclusion Criteria:
  ✓ Age ≥18 years: MET (Age: 62 years)
  ✓ NSCLC Diagnosis: MET (Non-small cell lung cancer)
  ✓ Stage IIIB/IV: MET (Stage IV)
  ✓ ECOG Status 0-2: MET (ECOG 1)
  ✓ Hemoglobin ≥9.0 g/dL: MET (11.2 g/dL on 2025-10-15)
  ✓ Neutrophils ≥1,500/µL: MET (2,100/µL on 2025-10-15)
  ✓ Platelets ≥100,000/µL: MET (180,000/µL on 2025-10-15)

Exclusion Criteria:
  ✓ No prior systemic therapy: MET
  ✓ No active brain metastases: MET

Missing Data: None

--------------------------------------------------------------------------------

Patient ID: patient-456
Overall Status: NOT ELIGIBLE
Reason: Prior systemic therapy for advanced disease

Inclusion Criteria:
  ✓ Age ≥18 years: MET (Age: 58 years)
  ✓ NSCLC Diagnosis: MET (Non-small cell lung cancer)
  ✓ Stage IIIB/IV: MET (Stage IV)
  ...

Exclusion Criteria:
  ✗ No prior systemic therapy: NOT MET (Pembrolizumab started 2025-09-01)
  ✓ No active brain metastases: MET

--------------------------------------------------------------------------------

Patient ID: patient-789
Overall Status: POTENTIALLY ELIGIBLE - Data Missing

Inclusion Criteria:
  ✓ Age ≥18 years: MET (Age: 55 years)
  ✓ NSCLC Diagnosis: MET (Non-small cell lung cancer)
  ✓ Stage IIIB/IV: MET (Stage IIIB)
  ? ECOG Status 0-2: UNKNOWN (No ECOG status recorded)
  ? Hemoglobin ≥9.0 g/dL: UNKNOWN (No recent lab results)
  ? Neutrophils ≥1,500/µL: UNKNOWN (No recent lab results)
  ? Platelets ≥100,000/µL: UNKNOWN (No recent lab results)

Exclusion Criteria:
  ✓ No prior systemic therapy: MET
  ✓ No active brain metastases: MET

Missing Data: ECOG performance status, Recent hemoglobin, Recent neutrophil count, Recent platelet count

================================================================================
SUMMARY
================================================================================

Total Patients Screened: 25
  - Eligible: 3
  - Not Eligible: 18
  - Potentially Eligible (Data Missing): 4

Common Missing Data Elements:
  - ECOG performance status: 15 patients
  - Recent laboratory results: 8 patients
  - Disease staging information: 5 patients

Processing completed in 12.3 seconds
================================================================================
```

## Project Architecture

```
src/main/java/com/trial/screener/
├── client/          # FHIR server communication
│   └── FhirClient.java
├── evaluator/       # Eligibility evaluation logic
│   └── EligibilityEvaluator.java
├── model/           # Domain models
│   ├── PatientData.java
│   ├── EligibilityCriterion.java
│   ├── EligibilityAssessment.java
│   └── [enums]
├── report/          # Report generation
│   └── ReportGenerator.java
└── main/            # Application entry point
    └── TrialScreenerApp.java
```

**Key Design Features:**
- Multi-strategy patient discovery (code-based, text-based, broad search with client-side filtering)
- Parallel processing with configurable thread pool for performance
- Retry logic with exponential backoff for API resilience
- Three-state evaluation (MET/NOT_MET/UNKNOWN) for handling missing data
- Comprehensive use of standard terminologies (LOINC, SNOMED CT)
- Modular architecture for testability and extensibility

## Dependencies

- **HAPI FHIR 6.10.0** - Industry-standard Java library for FHIR R4
- **SLF4J 2.0.9** - Logging framework
- **JUnit 5.10.0** - Unit testing
- **Mockito 5.5.0** - Mocking for tests

## Limitations and Assumptions

### Data Quality

- **Test Server Data**: The public HAPI FHIR test server contains synthetic/test data that may not reflect real-world completeness
- **Data Completeness**: Real EHR systems often have incomplete data; this screener is designed to handle such scenarios
- **Terminology Variations**: Different EHR systems may use different coding systems; this implementation focuses on standard LOINC and SNOMED codes

### Clinical Assumptions

- **Lab Result Recency**: Lab results must be within 30 days to be considered current
- **Medication History**: Relies on MedicationStatement resources being complete and accurate
- **Staging Information**: Assumes staging is recorded in Condition.stage; may miss staging in clinical notes
- **ECOG Status**: Requires structured ECOG observations; doesn't parse from clinical notes

### Technical Limitations

- **Memory-Based**: All results held in memory (suitable for screening up to ~1000 patients)
- **No Persistence**: Results not saved to database (output to console only)
- **Public Server Only**: Current implementation doesn't include authentication (easily extensible)

### Scope

- **Screening Only**: This tool identifies potentially eligible patients but doesn't replace manual chart review
- **Initial Assessment**: Clinical coordinators should verify all eligibility criteria with source documents
- **Not Diagnostic**: Does not make clinical decisions; provides decision support only

## Running Tests

Execute unit tests:
```bash
mvn test
```

Run tests with coverage report:
```bash
mvn clean test jacoco:report
```
