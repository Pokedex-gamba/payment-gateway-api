package com.example.paymentgateway.repository;

import com.example.paymentgateway.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGatewayRepository extends JpaRepository<PaymentHistory, String> {

    List<PaymentHistory> findAllByUserId(String userId);
}
