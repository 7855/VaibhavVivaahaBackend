package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.CashfreeService;
import com.uravugal.matrimony.services.PaymentLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * Cashfree Payment Gateway endpoints.
 *
 *   POST /payments/cashfree/createOrder/{encodedUserId}   body {planId, purpose?}        member
 *   GET  /payments/cashfree/order/{orderId}/status?requesterId=<encoded>                 member / anonymous
 *   POST /payments/cashfree/webhook                        raw JSON from Cashfree          PUBLIC (HMAC-verified)
 *   GET  /payments/cashfree/admin/orders?status&userId&page&size                          admin
 *   POST /payments/cashfree/admin/orders/{orderId}/reverify                               admin
 *   GET  /payments/cashfree/admin/orders/{orderId}/events                                 admin
 *   GET  /payments/cashfree/admin/orders/{orderId}/log                                    admin — payment_log trail
 *   GET  /payments/cashfree/admin/log?day=YYYY-MM-DD&limit=500                            admin — one day's rows
 *
 * ⚠ When the JWT filter in SecurityConfig is re-enabled, /payments/cashfree/webhook MUST be
 *   on the permit-all list — Cashfree carries no Bearer token; the HMAC signature is its auth.
 */
@RestController
@RequestMapping("/payments/cashfree")
@CrossOrigin(origins = "*")
public class CashfreeController {

    /*
     * One line per call, in the process log AND on stdout, so every webhook and every
     * status check can be traced with a plain `grep WEBHOOK` / `grep CALLBACK` on the server.
     * Logged here, at the controller, so no early return inside the service is missed.
     * Only ids and statuses are printed — never the member's name, phone or e-mail.
     *
     * CALLBACK = the server side of the member coming back from checkout: the app's
     * PaymentReturn screen (after the SDK's onVerify/onError) and the website's
     * /payment/return page both land on GET /order/{orderId}/status. The SDK callback
     * itself runs on the phone and never reaches the server on its own.
     */
    private static final Logger WEBHOOK_LOG = LoggerFactory.getLogger("WEBHOOK");
    private static final Logger CALLBACK_LOG = LoggerFactory.getLogger("CALLBACK");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private CashfreeService cashfreeService;

    @PostMapping("/createOrder/{encodedUserId}")
    public ResultResponse createOrder(@PathVariable String encodedUserId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        ResultResponse response = new ResultResponse();
        try {
            Long userId = decodeId(encodedUserId);
            Long planId = null;
            String purpose = null;
            // "SDK" suppresses return_url — the native checkout's browser cannot open a custom
            // scheme, so a redirect strands the member on a broken page after a successful
            // payment. Absent = the hosted web flow, which still needs it.
            String channel = CashfreeService.CHANNEL_WEB;
            if (body != null) {
                Object p = body.get("planId");
                if (p != null && !String.valueOf(p).isBlank() && !"0".equals(String.valueOf(p))) {
                    planId = Long.parseLong(String.valueOf(p).trim());
                }
                Object pu = body.get("purpose");
                if (pu != null) purpose = String.valueOf(pu);
                Object ch = body.get("channel");
                if (ch != null) channel = String.valueOf(ch);
            }
            response = cashfreeService.createOrder(userId, planId, purpose, channel);
        } catch (Exception e) {
            response.setCode(400);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Invalid request: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/order/{orderId}/status")
    public ResultResponse getOrderStatus(@PathVariable String orderId,
                                         @RequestParam(required = false) String requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            Long requester = (requesterId == null || requesterId.isBlank()) ? null : decodeId(requesterId);
            response = cashfreeService.getOrderStatus(orderId, requester);
        } catch (Exception e) {
            response.setCode(400);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Invalid request: " + e.getMessage());
        }
        String orderStatus = null, paymentStatus = null;
        if (response.getData() instanceof Map<?, ?> m) {
            orderStatus = String.valueOf(m.get("orderStatus"));
            paymentStatus = String.valueOf(m.get("paymentStatus"));
        }
        trace(CALLBACK_LOG, "CALLBACK order=" + orderId + " requester=" + requesterId
                + " code=" + response.getCode() + " orderStatus=" + orderStatus
                + " paymentStatus=" + paymentStatus
                + (response.getCode() == 200 ? "" : " message=" + response.getMessage()));
        return response;
    }

    /**
     * Body is taken as raw bytes on purpose: the HMAC is computed over exactly what Cashfree
     * sent. Letting Jackson parse-and-reserialise would turn 4999.00 into 4999 and every
     * signature would fail.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody(required = false) byte[] rawBody,
                                          @RequestHeader(value = "x-webhook-signature", required = false) String signature,
                                          @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
                                          @RequestHeader(value = "x-idempotency-key", required = false) String idempotencyKey,
                                          @RequestHeader(value = "x-webhook-version", required = false) String version,
                                          @RequestHeader(value = "x-webhook-attempt", required = false) String attempt,
                                          HttpServletRequest request) {
        String ip = PaymentLogService.clientIp(request);
        String type = null, orderId = null, paymentStatus = null, cfPaymentId = null, amount = null;
        try {
            // Read for the log line only — the service verifies the signature over the RAW bytes.
            JsonNode j = MAPPER.readTree(rawBody == null ? new byte[0] : rawBody);
            type = j.path("type").asText(null);
            orderId = j.path("data").path("order").path("order_id").asText(null);
            paymentStatus = j.path("data").path("payment").path("payment_status").asText(null);
            cfPaymentId = j.path("data").path("payment").path("cf_payment_id").asText(null);
            amount = j.path("data").path("payment").path("payment_amount").asText(null);
        } catch (Exception ignored) { /* unparseable body — the service rejects it */ }
        trace(WEBHOOK_LOG, "WEBHOOK in type=" + type + " order=" + orderId + " paymentStatus=" + paymentStatus
                + " amount=" + amount + " cfPaymentId=" + cfPaymentId + " attempt=" + attempt
                + " bytes=" + (rawBody == null ? 0 : rawBody.length) + " ip=" + ip);
        int code = cashfreeService.handleWebhook(rawBody, signature, timestamp, idempotencyKey, version, attempt, ip);
        trace(WEBHOOK_LOG, "WEBHOOK out order=" + orderId + " type=" + type + " http=" + code
                + (code == 200 ? " (accepted)" : code == 401 ? " (signature rejected)"
                   : code == 500 ? " (failed, Cashfree will retry)" : ""));
        return ResponseEntity.status(code).body(code == 200 ? "OK" : "REJECTED");
    }

    @GetMapping("/admin/orders")
    public PaginatedResultResponse adminListOrders(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long userId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return cashfreeService.adminListOrders(status, userId, page, size);
    }

    /** Member: download their own receipt PDF (requesterId = base64 user id, as for /status). */
    @GetMapping(value = "/order/{orderId}/receipt.pdf", produces = "application/pdf")
    public org.springframework.http.ResponseEntity<byte[]> receiptPdf(@PathVariable String orderId,
                                                                      @RequestParam String requesterId) {
        try {
            byte[] pdf = cashfreeService.receiptPdf(orderId, decodeId(requesterId));
            if (pdf == null) return org.springframework.http.ResponseEntity.notFound().build();
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"receipt-" + orderId + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/admin/orders/{orderId}/receipt/resend")
    public ResultResponse adminResendReceipt(@PathVariable String orderId) {
        return cashfreeService.adminResendReceipt(orderId);
    }

    @PostMapping("/admin/orders/{orderId}/reverify")
    public ResultResponse adminReverify(@PathVariable String orderId) {
        return cashfreeService.adminReverify(orderId);
    }

    @GetMapping("/admin/orders/{orderId}/events")
    public ResultResponse adminListEvents(@PathVariable String orderId) {
        return cashfreeService.adminListEvents(orderId);
    }

    @GetMapping("/admin/orders/{orderId}/log")
    public ResultResponse adminOrderLog(@PathVariable String orderId) {
        return cashfreeService.adminOrderLog(orderId);
    }

    @GetMapping("/admin/log")
    public ResultResponse adminDayLog(@RequestParam(required = false) String day,
                                      @RequestParam(defaultValue = "500") int limit) {
        return cashfreeService.adminDayLog(day, limit);
    }

    /** Accepts the app's base64-encoded id, or a plain numeric id. */
    private static Long decodeId(String raw) {
        String s = raw.trim();
        try {
            return Long.parseLong(new String(Base64.getDecoder().decode(s)).trim());
        } catch (Exception e) {
            return Long.parseLong(s);
        }
    }

    private static void trace(Logger logger, String line) {
        logger.info(line);
        System.out.println(LocalDateTime.now() + " " + line);
    }
}
