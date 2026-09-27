package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One payment RECEIPT per paid order — the document e-mailed to the member as a PDF.
 *
 * Deliberately a receipt, not a "tax invoice": Vaibhav Vivaaha Matrimony is not registered
 * under GST, so it may not issue a tax invoice, charge GST or print an HSN/SAC breakup. The
 * PDF states that in words. If the business registers later, the number series here stays
 * continuous and the document gains a GSTIN + tax lines — nothing else changes.
 *
 * The PDF is NOT stored; it is rebuilt from this row + the order on every send, so a resend
 * always reproduces the same document. paymentOrderId is UNIQUE = one receipt per order.
 */
@Data
@Entity
@Table(name = "payment_invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_order", columnNames = "paymentOrderId"),
        indexes = { @Index(name = "idx_invoice_user", columnList = "userId"),
                    @Index(name = "idx_invoice_no", columnList = "invoiceNo") })
public class PaymentInvoice extends GenericEntity {

    public static final String EMAIL_SENT = "SENT";
    public static final String EMAIL_FAILED = "FAILED";
    public static final String EMAIL_NO_ADDRESS = "NO_EMAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** VVM-RCPT-000012 — allocated from the row id, so it is unique and monotonic. */
    @Column(name = "invoiceNo", length = 32)
    private String invoiceNo;

    @Column(name = "paymentOrderId", nullable = false)
    private Long paymentOrderId;

    @Column(name = "orderId", length = 45, nullable = false)
    private String orderId;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    /** What was bought, as printed: "Platinum Membership" / "Profile Boost credit (1)". */
    @Column(name = "description", length = 160)
    private String description;

    /** Validity as printed, e.g. "22 Sep 2026 to 21 Mar 2027" — null for a boost. */
    @Column(name = "validity", length = 80)
    private String validity;

    @Column(name = "paymentMode", length = 32)
    private String paymentMode;

    @Column(name = "cfPaymentId", length = 64)
    private String cfPaymentId;

    @Column(name = "issuedAt", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "emailedTo", length = 160)
    private String emailedTo;

    @Column(name = "emailedAt")
    private LocalDateTime emailedAt;

    /** SENT / FAILED / NO_EMAIL — the last attempt's outcome (resends overwrite it). */
    @Column(name = "emailStatus", length = 16)
    private String emailStatus;

    @Column(name = "sendAttempts")
    private Integer sendAttempts = 0;
}
