package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

/**
 * Payment log: every exchange in the life of an online payment — what we sent Cashfree,
 * what it answered, what it pushed to the webhook, and what we concluded. The same table
 * the Litup Story backend keeps, so both businesses on the shared Cashfree account can be
 * audited the same way.
 *
 * Bodies only, never headers: outbound headers carry the API secret, inbound ones the
 * webhook signature. Bodies are redacted before they land here ({@code PaymentRedactor}):
 * card data is never expected — payments run on Cashfree's hosted page — but the redactor
 * strips it regardless, and masks the payment session id and UPI handles, so this table can
 * never become cardholder-data storage under PCI DSS.
 *
 * {@code paymentOrderId} is NULL until a payment_orders row exists. No foreign keys on
 * purpose: an audit trail must outlive the rows it describes. {@code logDate} is the day
 * the row was written (IST) so day-wise reads and the retention sweep are an index range.
 */
@Data
@Entity
@Table(name = "payment_log",
        indexes = {
                @Index(name = "idx_pl_date", columnList = "logDate, id"),
                @Index(name = "idx_pl_order", columnList = "orderId, id"),
                @Index(name = "idx_pl_payment", columnList = "paymentOrderId"),
                @Index(name = "idx_pl_cf", columnList = "cfOrderId")
        })
public class PaymentLog extends GenericEntity {

    public static final String DIR_OUT = "out";     // us -> Cashfree
    public static final String DIR_IN = "in";       // Cashfree -> us
    public static final String DIR_LOCAL = "local"; // our own decision

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "logDate", nullable = false)
    private LocalDate logDate;

    /** Our merchant order id (VVM-…), empty when not tied to an order yet. */
    @Column(name = "orderId", length = 45)
    private String orderId;

    /** payment_orders.id — NULL before the row exists. */
    @Column(name = "paymentOrderId")
    private Long paymentOrderId;

    @Column(name = "userId")
    private Long userId;

    /** Cashfree's own cf_order_id, once known. */
    @Column(name = "cfOrderId", length = 64)
    private String cfOrderId;

    /** order_requested | create_order | order_created | get_order | status | webhook | fulfilled | reverify | settled */
    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "method", length = 8)
    private String method;

    /** URL called (out) or hit (in); query string kept, no headers. */
    @Column(name = "url", length = 512)
    private String url;

    @Column(name = "requestBody", columnDefinition = "LONGTEXT")
    private String requestBody;

    @Column(name = "responseBody", columnDefinition = "LONGTEXT")
    private String responseBody;

    @Column(name = "httpStatus")
    private Integer httpStatus;

    @Column(name = "outcome", length = 64)
    private String outcome;

    @Column(name = "error", length = 512)
    private String error;

    @Column(name = "durationMs")
    private Integer durationMs;

    /** Inbound only. */
    @Column(name = "sourceIp", length = 45)
    private String sourceIp;
}
