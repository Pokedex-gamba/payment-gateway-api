package com.example.paymentgateway.service;

import com.example.paymentgateway.entity.PaymentHistory;
import com.example.paymentgateway.repository.PaymentGatewayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentGatewayService {

    private final PaymentGatewayRepository paymentGatewayRepository;

    public List<PaymentHistory> findAllByUserId(String userId) {
        return paymentGatewayRepository.findAllByUserId(userId);
    }

    public void insertPaymentHistory(PaymentHistory paymentHistory) {
        paymentGatewayRepository.save(paymentHistory);
    }

    @Autowired
    public PaymentGatewayService(PaymentGatewayRepository paymentGatewayRepository) {
        this.paymentGatewayRepository = paymentGatewayRepository;
    }
}
