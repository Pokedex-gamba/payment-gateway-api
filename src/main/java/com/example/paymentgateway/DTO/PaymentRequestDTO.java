package com.example.paymentgateway.DTO;

public class PaymentRequestDTO {

    private int amount;
    private String description;
    private String succesUrl;
    private String cancelUrl;

    public String getSuccesUrl() {
        return succesUrl;
    }

    public void setSuccesUrl(String succesUrl) {
        this.succesUrl = succesUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}