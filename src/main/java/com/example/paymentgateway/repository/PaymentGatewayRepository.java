package com.example.paymentgateway.repository;

import com.example.paymentgateway.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentGatewayRepository extends JpaRepository<PaymentHistory, String> {

    List<PaymentHistory> findAllByUserId(String userId);

    Optional<PaymentHistory> findOneByPaymentId(String paymentId);
}
