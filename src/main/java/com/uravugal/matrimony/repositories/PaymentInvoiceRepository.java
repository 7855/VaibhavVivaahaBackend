package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.PaymentInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {
    Optional<PaymentInvoice> findByOrderId(String orderId);
    Optional<PaymentInvoice> findByPaymentOrderId(Long paymentOrderId);
    List<PaymentInvoice> findByUserIdOrderByIdDesc(Long userId);
}
