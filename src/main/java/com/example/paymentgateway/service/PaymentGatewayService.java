package com.example.paymentgateway.service;

import com.example.paymentgateway.DTO.PayPalExecuteRequestDTO;
import com.example.paymentgateway.entity.PaymentHistory;
import com.example.paymentgateway.repository.PaymentGatewayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentGatewayService {

    private final PaymentGatewayRepository paymentGatewayRepository;

    public List<PaymentHistory> findAllByUserId(String userId) {
        return paymentGatewayRepository.findAllByUserId(userId);
    }

    public void insertPaymentHistory(PaymentHistory paymentHistory) {
        paymentGatewayRepository.save(paymentHistory);
    }

    public Optional<PaymentHistory> findOneByPaymentId(PayPalExecuteRequestDTO payPalExecuteRequestDTO) {
        return paymentGatewayRepository.findOneByPaymentId(payPalExecuteRequestDTO.getPaymentId());
    }

    @Autowired
    public PaymentGatewayService(PaymentGatewayRepository paymentGatewayRepository) {
        this.paymentGatewayRepository = paymentGatewayRepository;
    }
}
