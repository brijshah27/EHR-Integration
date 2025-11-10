package com.trial.screener.main;

import com.trial.screener.client.FhirClient;
import com.trial.screener.evaluator.EligibilityEvaluator;
import com.trial.screener.model.EligibilityAssessment;
import com.trial.screener.model.PatientData;
import com.trial.screener.report.ReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main application for FHIR Clinical Trial Eligibility Screener
 * Orchestrates the screening workflow: finding patients, retrieving data, evaluating eligibility, and generating reports
 */
public class TrialScreenerApp {
    
    private static final Logger logger = LoggerFactory.getLogger(TrialScreenerApp.class);
    private static final String DEFAULT_FHIR_SERVER_URL = "https://hapi.fhir.org/baseR4";
    private static final int DEFAULT_MAX_PATIENTS = 100;  // Increased to find more eligible patients
    private static final int THREAD_POOL_SIZE = 10; // Number of concurrent patient processing threads

    public static void main(String[] args) {
        logger.info("Starting FHIR Clinical Trial Eligibility Screener");
        
        // Parse command-line arguments
        int maxPatients = parseMaxPatients(args);
        
        try {
            // Initialize components
            logger.info("Initializing FHIR client with server: {}", DEFAULT_FHIR_SERVER_URL);
            FhirClient fhirClient = new FhirClient(DEFAULT_FHIR_SERVER_URL);
            
            EligibilityEvaluator evaluator = new EligibilityEvaluator();
            ReportGenerator reportGenerator = new ReportGenerator();
            
            // Find candidate patients with lung cancer
            logger.info("Searching for lung cancer patients (max: {})", maxPatients);
            List<String> patientIds = fhirClient.findLungCancerPatients(maxPatients);
            
            if (patientIds.isEmpty()) {
                logger.warn("No lung cancer patients found on FHIR server");
                System.out.println("No lung cancer patients found for screening.");
                return;
            }
            
            logger.info("Found {} candidate patients. Beginning parallel eligibility evaluation...", patientIds.size());
            
            // Process patients in parallel
            List<EligibilityAssessment> assessments = processPatientsInParallel(
                patientIds, fhirClient, evaluator);
            
            logger.info("Completed evaluation of {} patients", assessments.size());
            
            // Generate and display report
            logger.info("Generating eligibility report");
            String report = reportGenerator.generateReport(assessments);
            
            System.out.println(report);
            
            logger.info("FHIR Clinical Trial Eligibility Screener completed successfully");
            
        } catch (Exception e) {
            logger.error("Fatal error in trial screener application: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Process patients in parallel using async processing with CompletableFuture
     * @param patientIds List of patient IDs to process
     * @param fhirClient FHIR client for data retrieval
     * @param evaluator Eligibility evaluator
     * @return List of eligibility assessments
     */
    private static List<EligibilityAssessment> processPatientsInParallel(
            List<String> patientIds, 
            FhirClient fhirClient, 
            EligibilityEvaluator evaluator) {
        
        // Create a fixed thread pool for parallel processing
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // Track progress
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalPatients = patientIds.size();
        
        try {
            // Create CompletableFuture for each patient
            List<CompletableFuture<EligibilityAssessment>> futures = patientIds.stream()
                .map(patientId -> CompletableFuture.supplyAsync(() -> {
                    try {
                        int currentCount = processedCount.incrementAndGet();
                        logger.info("Processing patient {}/{}: {}", 
                            currentCount, totalPatients, patientId);
                        
                        // Retrieve patient data
                        PatientData patientData = fhirClient.getPatientData(patientId);
                        
                        // Evaluate eligibility
                        EligibilityAssessment assessment = evaluator.evaluate(patientData);
                        
                        logger.info("Patient {} assessment complete: {}", 
                            patientId, assessment.getOverallStatus());
                        
                        return assessment;
                        
                    } catch (Exception e) {
                        logger.error("Error processing patient {}: {}", patientId, e.getMessage(), e);
                        // Return null for failed assessments
                        return null;
                    }
                }, executorService))
                .toList();
            
            // Wait for all futures to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            // Block until all complete
            allFutures.join();
            
            // Collect results, filtering out null values (failed assessments)
            return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } finally {
            // Shutdown executor service
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("Executor service did not terminate in time, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for executor service to terminate");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Parse command-line arguments to extract max patients parameter
     * @param args Command-line arguments
     * @return Maximum number of patients to screen
     */
    private static int parseMaxPatients(String[] args) {
        if (args.length > 0) {
            try {
                int maxPatients = Integer.parseInt(args[0]);
                if (maxPatients > 0) {
                    logger.info("Using max patients from command line: {}", maxPatients);
                    return maxPatients;
                } else {
                    logger.warn("Invalid max patients value: {}. Using default: {}", 
                        maxPatients, DEFAULT_MAX_PATIENTS);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid max patients argument: {}. Using default: {}", 
                    args[0], DEFAULT_MAX_PATIENTS);
            }
        }
        
        return DEFAULT_MAX_PATIENTS;
    }
}
