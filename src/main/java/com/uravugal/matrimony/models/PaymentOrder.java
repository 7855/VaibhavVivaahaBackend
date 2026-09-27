package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One Cashfree Payment Gateway order (= one checkout attempt for a plan or add-on).
 *
 * Lifecycle mirrors Cashfree's order_status: ACTIVE -> PAID | EXPIRED | TERMINATED.
 * A single order can carry several payment attempts (a failed UPI then a successful
 * card, for example); the payment* columns always reflect the LATEST attempt.
 *
 * {@code fulfilledAt} is the idempotency guard: the plan is activated by exactly the
 * caller that manages to flip it from NULL (see PaymentOrderRepository.markPaid), no
 * matter whether the webhook, the return-URL status poll or an admin re-verify got
 * there first, and no matter how many duplicate webhook deliveries arrive.
 *
 * Deliberately NOT a row in PAYMENT_REQUESTS: that table is the manual UTR/screenshot
 * flow with admin verification columns that make no sense for a gateway order, and
 * getPaymentRequestsByUser would have shown these as "pending admin approval".
 */
@Data
@Entity
@Table(name = "payment_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_po_order", columnNames = "orderId"),
        indexes = {
                @Index(name = "idx_po_user", columnList = "userId, createdAt"),
                @Index(name = "idx_po_status", columnList = "orderStatus")
        })
public class PaymentOrder extends GenericEntity {

    public static final String PURPOSE_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String PURPOSE_BOOST = "BOOST_PURCHASE";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_TERMINATED = "TERMINATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Our merchant order id, sent to Cashfree as order_id. e.g. VVM-702-LX3K9A2F-7H2KQ9ZP */
    @Column(name = "orderId", nullable = false, length = 45)
    private String orderId;

    @Column(name = "cfOrderId", length = 64)
    private String cfOrderId;

    /** What the client needs to open checkout. Not a secret. */
    @Column(name = "paymentSessionId", length = 1024)
    private String paymentSessionId;

    @Column(name = "userId", nullable = false)
    private Long userId;

    /** Null for add-on purchases (BOOST_PURCHASE). */
    @Column(name = "planId")
    private Long planId;

    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose = PURPOSE_SUBSCRIPTION;

    /** Copied from subscription_plans.price at creation — never from the client. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "orderStatus", nullable = false, length = 24)
    private String orderStatus = STATUS_ACTIVE;

    @Column(name = "cfPaymentId", length = 64)
    private String cfPaymentId;

    /** Cashfree payment_status of the latest attempt: SUCCESS / FAILED / USER_DROPPED / ... */
    @Column(name = "paymentStatus", length = 24)
    private String paymentStatus;

    /** Cashfree payment_group: upi / credit_card / debit_card / net_banking / wallet ... */
    /**
     * Which checkout opened this order: SDK (native, no return_url) or WEB (hosted redirect).
     * Persisted ONLY so reuse can require a match — an SDK order carries no return_url, so
     * handing one back to a WEB request would strand the browser with nowhere to return to.
     */
    @Column(name = "channel", length = 8)
    private String channel;

    @Column(name = "paymentGroup", length = 32)
    private String paymentGroup;

    @Column(name = "bankReference", length = 64)
    private String bankReference;

    @Column(name = "paymentTime")
    private LocalDateTime paymentTime;

    @Column(name = "failureReason", length = 500)
    private String failureReason;

    /** The order_expiry_time we sent to Cashfree. */
    @Column(name = "expiresAt")
    private LocalDateTime expiresAt;

    /** NULL until the plan is activated. See class Javadoc. */
    @Column(name = "fulfilledAt")
    private LocalDateTime fulfilledAt;

    /** WEBHOOK | VERIFY | ADMIN — who won the fulfilment race. */
    @Column(name = "fulfilledBy", length = 16)
    private String fulfilledBy;

    /** user_subscriptions.id created by fulfilment (null for BOOST_PURCHASE). */
    @Column(name = "subscriptionId")
    private Long subscriptionId;

    // From PAYMENT_CHARGES_WEBHOOK — settlement figures, informational only.
    @Column(name = "serviceCharge", precision = 10, scale = 2)
    private BigDecimal serviceCharge;

    @Column(name = "serviceTax", precision = 10, scale = 2)
    private BigDecimal serviceTax;

    @Column(name = "settlementAmount", precision = 10, scale = 2)
    private BigDecimal settlementAmount;
}
