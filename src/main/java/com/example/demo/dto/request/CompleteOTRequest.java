package com.example.demo.dto.request;

public class CompleteOTRequest {

    @jakarta.validation.constraints.Size(max = 1000)
    private String outcome;

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
