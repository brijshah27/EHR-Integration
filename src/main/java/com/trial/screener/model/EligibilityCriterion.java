package com.trial.screener.model;

import java.util.ArrayList;
import java.util.List;

public class EligibilityCriterion {
    private String name;
    private CriterionType type;
    private CriterionStatus status;
    private String details;
    private List<String> missingData;

    public EligibilityCriterion() {
        this.missingData = new ArrayList<>();
    }

    public EligibilityCriterion(String name, CriterionType type, CriterionStatus status, 
                                String details) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.details = details;
        this.missingData = new ArrayList<>();
    }

    public EligibilityCriterion(String name, CriterionType type, CriterionStatus status, 
                                String details, List<String> missingData) {
        this.name = name;
        this.type = type;
        this.status = status;
        this.details = details;
        this.missingData = missingData != null ? missingData : new ArrayList<>();
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CriterionType getType() {
        return type;
    }

    public void setType(CriterionType type) {
        this.type = type;
    }

    public CriterionStatus getStatus() {
        return status;
    }

    public void setStatus(CriterionStatus status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public List<String> getMissingData() {
        return missingData;
    }

    public void setMissingData(List<String> missingData) {
        this.missingData = missingData != null ? missingData : new ArrayList<>();
    }

    public void addMissingData(String data) {
        if (this.missingData == null) {
            this.missingData = new ArrayList<>();
        }
        this.missingData.add(data);
    }
}
