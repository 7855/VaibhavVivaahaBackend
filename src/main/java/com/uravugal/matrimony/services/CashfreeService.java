package com.uravugal.matrimony.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PaymentLog;
import com.uravugal.matrimony.models.PaymentOrder;
import com.uravugal.matrimony.models.PaymentWebhookEvent;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.PaymentOrderRepository;
import com.uravugal.matrimony.repositories.PaymentWebhookEventRepository;
import com.uravugal.matrimony.repositories.SubscriptionPlanRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.utils.PaymentRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cashfree Payment Gateway — web redirect checkout + payment webhooks.
 *
 * Flow (see CASHFREE_INTEGRATION_PLAN.md):
 *   1. createOrder(): server-side Create Order (amount from the plan, never the client) →
 *      payment_orders row + payment_session_id back to the client.
 *   2. Client opens Cashfree checkout with that session id (website /pay/:orderId page).
 *   3. Cashfree POSTs PAYMENT_SUCCESS_WEBHOOK etc. to handleWebhook() — the fulfilment trigger.
 *      getOrderStatus() (return page / app poll) may fast-path via Get Order if the webhook lags.
 *   Fulfilment is idempotent through PaymentOrderRepository.markPaid — one winner per order.
 *
 * ⚠ Never repo.save() a PaymentOrder instance that was loaded BEFORE markPaid ran on it:
 *   merge would write its stale null fulfilledAt back and re-open the order for a second
 *   activation. Reload after every claim (see fulfil / activate).
 */
@Service
public class CashfreeService {

    private static final Logger log = LoggerFactory.getLogger(CashfreeService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * The ONE clock for payment_orders. expiresAt and paymentTime were already IST wall-clock
     * while fulfilledAt came from the database and createdAt from the JVM — three zones in one
     * row. Everything this service writes now goes through here.
     */
    static LocalDateTime istNow() {
        return LocalDateTime.now(IST);
    }
    private static final String ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    /** Checkout channel. SDK = native Drop-in (no return_url); WEB = hosted redirect. */
    public static final String CHANNEL_SDK = "SDK";
    public static final String CHANNEL_WEB = "WEB";

    /**
     * Payment-attempt states with nothing further to learn by asking Cashfree again.
     * SUCCESS is included for completeness; a successful order is PAID and never reaches
     * the poll branch. A member CAN start a fresh attempt on a still-ACTIVE order after a
     * failure — that arrives by webhook, which is the fulfilment path regardless.
     */
    private static boolean isTerminalPaymentStatus(String paymentStatus) {
        if (paymentStatus == null) return false;
        switch (paymentStatus.trim().toUpperCase()) {
            case "SUCCESS":
            case "FAILED":
            case "USER_DROPPED":
            case "CANCELLED":
            case "VOID":
                return true;
            default:
                return false;
        }
    }

    public static final String EVT_SUCCESS = "PAYMENT_SUCCESS_WEBHOOK";
    public static final String EVT_FAILED = "PAYMENT_FAILED_WEBHOOK";
    public static final String EVT_DROPPED = "PAYMENT_USER_DROPPED_WEBHOOK";
    public static final String EVT_CHARGES = "PAYMENT_CHARGES_WEBHOOK";

    /**
     * The Cashfree account is shared with Litup Story (order ids "LS-…"). Every order we
     * create carries order_tags.business so the two can be told apart in the dashboard and
     * in settlement exports, and a webhook for a foreign prefix is acknowledged untouched.
     */
    public static final String BUSINESS = "vaibhavvivaaha";
    public static final String ORDER_PREFIX = "VVM-";

    public static boolean isOurOrder(String orderId) {
        return orderId != null && orderId.startsWith(ORDER_PREFIX);
    }

    @Value("${cashfree.enabled:false}") private boolean enabled;
    @Value("${cashfree.env:sandbox}") private String env;
    @Value("${cashfree.base.url:https://sandbox.cashfree.com/pg}") private String baseUrl;
    @Value("${cashfree.client.id:}") private String clientId;
    @Value("${cashfree.client.secret:}") private String clientSecret;
    @Value("${cashfree.api.version:2025-01-01}") private String apiVersion;
    @Value("${cashfree.payment.methods:upi,dc,cc,nb}") private String paymentMethods;
    @Value("${cashfree.return.url:}") private String returnUrl;
    @Value("${cashfree.notify.url:}") private String notifyUrl;
    @Value("${cashfree.order.expiry.minutes:15}") private int orderExpiryMinutes;
    @Value("${cashfree.webhook.max.skew.minutes:10}") private long webhookMaxSkewMinutes;
    @Value("${payments.boost.price:149}") private BigDecimal boostPrice;

    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private PaymentWebhookEventRepository webhookEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private SubscriptionActivationService activationService;
    @Autowired private PaymentLogService paymentLog;
    @Autowired private InvoiceService invoiceService;
    @Autowired private WhatsAppService whatsAppService;

    // Same timeout discipline as EmailService (2026-09-04): a slow gateway must never pin a
    // request thread + Hikari connection indefinitely.
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isConfigured() {
        return enabled && notBlank(clientId) && notBlank(clientSecret);
    }

    // ───────────────────────────── 1. CREATE ORDER ─────────────────────────────

    /** Back-compat: callers that do not say default to the hosted web flow. */
    public ResultResponse createOrder(Long userId, Long planIdIn, String purposeIn) {
        return createOrder(userId, planIdIn, purposeIn, CHANNEL_WEB);
    }

    public ResultResponse createOrder(Long userId, Long planIdIn, String purposeIn, String channelIn) {
        final String channel = CHANNEL_SDK.equalsIgnoreCase(String.valueOf(channelIn)) ? CHANNEL_SDK : CHANNEL_WEB;
        ResultResponse r = new ResultResponse();
        try {
            if (!isConfigured()) return fail(r, 503, "PAYMENT_GATEWAY_DISABLED");

            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) return fail(r, 404, "User not found");
            if (user.getUserStatus() != ApprovalStatus.APPROVED) return fail(r, 403, "PROFILE_NOT_APPROVED");

            String purpose = PaymentOrder.PURPOSE_BOOST.equalsIgnoreCase(purposeIn)
                    ? PaymentOrder.PURPOSE_BOOST : PaymentOrder.PURPOSE_SUBSCRIPTION;

            Long planId = planIdIn;
            BigDecimal amount;
            String note;
            if (PaymentOrder.PURPOSE_SUBSCRIPTION.equals(purpose)) {
                if (planId == null) return fail(r, 400, "planId is required");
                SubscriptionPlan plan = subscriptionPlanRepository.findById(planId).orElse(null);
                if (plan == null) return fail(r, 404, "Subscription Plan not found with ID: " + planId);
                if (plan.getIsActive() != ActiveStatus.Y)
                    return fail(r, 400, "SUBSCRIPTION_PLAN_INACTIVE: '" + plan.getTitle() + "' is no longer offered.");
                if (plan.getPrice() == null || plan.getPrice().signum() <= 0)
                    return fail(r, 400, "PLAN_NOT_PURCHASABLE");
                amount = plan.getPrice();
                note = plan.getTitle() + " plan (" + plan.getDurationDays() + " days)";
            } else {
                planId = null;
                amount = boostPrice;
                note = "Profile Boost add-on";
            }
            amount = amount.setScale(2, RoundingMode.HALF_UP);

// ── NO ORDER REUSE (client decision, 2026-09-28) ──────────────────────────────
            // Every "Continue with this plan" creates a FRESH Cashfree order and a fresh
            // payment_session_id. This deliberately replaces a 28-minute dedupe window that
            // returned the EXISTING order (and its ORIGINAL session) for the same
            // user+purpose+plan+amount+channel.
            //
            // 🔴 Why that had to go. A payment_session_id is minted once, when the order is
            // created — nothing about backing out of checkout and tapping again produces a new
            // one. So if that single session could not open a payment page, every retry for the
            // next 28 minutes was handed the same dead session and failed identically, then
            // "suddenly worked" when the window lapsed and a fresh order was finally minted.
            // Measured on production for user 936: five deliberate attempts produced exactly two
            // rows, 28m10s apart — the first attempt, then the one that just missed the window;
            // the three in between reused the first order and wrote NOTHING, not even a
            // payment_log row (the order_created row is written below, past the old early
            // return), so the trail understated what the member actually did.
            //
            // Double-submit protection now lives on the BUTTON, where the intent is actually
            // known: PremiumTab/GatewayCheckoutView block re-entry while a checkout is starting
            // and cover the screen, so the server cannot tell a double-tap from a deliberate
            // retry and no longer tries to. The residual exposure is two ACTIVE orders if a
            // duplicate ever slips through — harmless on its own, since an unpaid order simply
            // expires; a double CHARGE would require completing payment twice.
            //
            // ⚠️ Do not reintroduce reuse here. If duplicate orders ever become a real problem,
            // fix it at the tap, or dedupe on an idempotency key the CLIENT sends per tap —
            // never by handing back a session whose health we have not checked.

                        String orderId = newOrderId(userId);
            // Cashfree refuses an order_expiry_time less than 15 minutes from when IT receives
            // the request. At exactly 15, network latency plus the seconds truncation below
            // would land just under that floor and fail every order — so a window at or under
            // the minimum gets a 30-second cushion (15 min → 15m30s at Cashfree). Values
            // below 15 are raised to 15 for the same reason.
            int expiryMinutes = Math.max(15, orderExpiryMinutes);
            OffsetDateTime expiry = OffsetDateTime.now(IST).plusMinutes(expiryMinutes)
                    .plusSeconds(expiryMinutes <= 15 ? 30 : 0).truncatedTo(ChronoUnit.SECONDS);

            ObjectNode body = mapper.createObjectNode();
            body.put("order_id", orderId);
            body.put("order_amount", amount);
            body.put("order_currency", "INR");
            ObjectNode cust = body.putObject("customer_details");
            cust.put("customer_id", String.valueOf(userId));
            cust.put("customer_name", SubscriptionActivationService.fullName(user));
            cust.put("customer_phone", normalisePhone(user.getMobile()));
            if (notBlank(user.getEmail())) cust.put("customer_email", user.getEmail().trim());
            ObjectNode meta = body.putObject("order_meta");
            // 🔴 return_url ONLY for the web redirect flow.
            // The native SDK opens checkout in its own in-app browser. A return_url makes that
            // browser navigate to the website return page, which then tries to open
            // vaibhavvivaaha:// — a custom scheme that browser cannot handle. On a wallet/UPI
            // success the money is taken and the credit granted, but the member is left on
            // "Webpage not available", then an "Exit payment?" prompt, then checkout again for
            // an order already paid. With no return_url the SDK closes itself and its callback
            // (useCashfreeCallback) drives the app to PaymentReturn, which is the real path.
            // Fulfilment never depended on the redirect anyway — the webhook does that.
            if (notBlank(returnUrl) && !CHANNEL_SDK.equals(channel)) meta.put("return_url", returnUrl);
            if (notBlank(notifyUrl)) meta.put("notify_url", notifyUrl);
            // UPI only (client ask 2026-09-21). Set HERE rather than in the JS SDK because
            // order_meta travels with the order: the hosted page, a resumed link and any
            // retry all inherit it, and a modified client cannot re-enable cards.
            if (notBlank(paymentMethods)) meta.put("payment_methods", paymentMethods);
            body.put("order_expiry_time", expiry.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            body.put("order_note", note);
            ObjectNode tags = body.putObject("order_tags");
            tags.put("business", BUSINESS);
            tags.put("userId", String.valueOf(userId));
            tags.put("purpose", purpose);
            if (planId != null) tags.put("planId", String.valueOf(planId));

            HttpResponse<String> resp = send("POST", "/orders", body.toString(), scope(orderId, null, userId, null));
            JsonNode json = parseOrEmpty(resp.body());
            if (resp.statusCode() >= 300) {
                log.warn("Cashfree create order failed {} {}", resp.statusCode(), resp.body());
                return fail(r, 502, "CASHFREE_CREATE_ORDER_FAILED: " + json.path("message").asText("HTTP " + resp.statusCode()));
            }

            PaymentOrder order = new PaymentOrder();
            order.setOrderId(orderId);
            order.setCfOrderId(json.path("cf_order_id").asText(null));
            order.setPaymentSessionId(json.path("payment_session_id").asText(null));
            order.setUserId(userId);
            order.setPlanId(planId);
            order.setPurpose(purpose);
            order.setChannel(channel);
            order.setAmount(amount);
            order.setCurrency("INR");
            order.setOrderStatus(json.path("order_status").asText(PaymentOrder.STATUS_ACTIVE));
            order.setExpiresAt(expiry.toLocalDateTime());
            order.setCreatedBy(String.valueOf(userId));
            order = paymentOrderRepository.save(order);

            r.setCode(200);
            r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage("Order created");
            r.setData(clientPayload(order));

            // First local row of the order's trail: what the member asked for and what we
            // handed the app (session id masked by the redactor). Fenced: nothing about
            // logging may change the outcome of an order that has already been created.
            try {
                PaymentLog e = scope(order.getOrderId(), order.getId(), userId, order.getCfOrderId());
                e.setStage("order_created");
                e.setDirection(PaymentLog.DIR_LOCAL);
                e.setMethod("POST");
                e.setUrl("/payments/cashfree/createOrder");
                ObjectNode req = mapper.createObjectNode();
                req.put("userId", userId);
                req.put("planId", planId);
                req.put("purpose", purpose);
                req.put("amount", amount);
                ObjectNode res = mapper.createObjectNode();
                res.put("orderId", order.getOrderId());
                res.put("cfOrderId", order.getCfOrderId());
                res.put("paymentSessionId", order.getPaymentSessionId());
                res.put("orderStatus", order.getOrderStatus());
                res.put("amount", order.getAmount());
                res.put("expiresAt", String.valueOf(order.getExpiresAt()));
                e.setRequestBody(PaymentRedactor.redact(req.toString()));
                e.setResponseBody(PaymentRedactor.redact(res.toString()));
                e.setOutcome("created");
                paymentLog.record(e);
            } catch (Exception logEx) {
                log.warn("PAYLOG order_created row for {} not written: {}", order.getOrderId(), logEx.getMessage());
            }
        } catch (Exception e) {
            log.error("createOrder failed", e);
            fail(r, 500, "Error creating payment order: " + e.getMessage());
        }
        return r;
    }

    // ───────────────────────────── 2. STATUS / VERIFY ─────────────────────────────

    /**
     * @param requesterId  decoded caller id, or null when the caller is anonymous (the website's
     *                     return page inside a Custom Tab has no login). The payload never carries
     *                     personal data, so anonymous reads only reveal status/plan/amount for an
     *                     order id that already contains 8 random characters.
     */
    public ResultResponse getOrderStatus(String orderId, Long requesterId) {
        ResultResponse r = new ResultResponse();
        try {
            PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElse(null);
            if (order == null) return fail(r, 404, "Order not found");
            if (requesterId != null && !requesterId.equals(order.getUserId())) return fail(r, 403, "FORBIDDEN");

            // A poll only consults Cashfree while there is something left to learn. The gate
            // used to be orderStatus alone — but Cashfree leaves an order ACTIVE after a FAILED
            // attempt (it can still be retried), so the confirm screen's 40 seconds of polling
            // turned one failed payment into ~29 Get Order calls. The webhook remains the
            // fulfilment path if the member does retry the same order.
            boolean attemptSettled = order.getFulfilledAt() != null
                    || isTerminalPaymentStatus(order.getPaymentStatus());
            if (PaymentOrder.STATUS_ACTIVE.equals(order.getOrderStatus()) && !attemptSettled && isConfigured()) {
                JsonNode cf = fetchOrder(order);
                String action = "cashfree_unreachable";
                if (cf != null) {
                    String st = cf.path("order_status").asText("");
                    action = "cashfree_" + st.toLowerCase();
                    if (PaymentOrder.STATUS_PAID.equals(st)) {
                        action = fulfil(orderId, "VERIFY") ? "fulfilled" : "already_fulfilled";
                    } else if (PaymentOrder.STATUS_EXPIRED.equals(st) || PaymentOrder.STATUS_TERMINATED.equals(st)) {
                        order.setOrderStatus(st);
                        paymentOrderRepository.save(order);
                    }
                    order = paymentOrderRepository.findByOrderId(orderId).orElse(order);
                }
                // Only when Cashfree was consulted — a poll on a settled order writes nothing.
                PaymentLog e = scope(order);
                e.setStage("status");
                e.setDirection(PaymentLog.DIR_LOCAL);
                e.setMethod("GET");
                e.setUrl("/payments/cashfree/order/" + orderId + "/status");
                e.setOutcome(action);
                paymentLog.record(e);
            }

            r.setCode(200);
            r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage("Order status fetched");
            r.setData(statusPayload(order));
        } catch (Exception e) {
            log.error("getOrderStatus failed for {}", orderId, e);
            fail(r, 500, "Error fetching order status: " + e.getMessage());
        }
        return r;
    }

    // ───────────────────────────── 3. WEBHOOK ─────────────────────────────

    /**
     * @return the HTTP status the controller must answer with. 200 = accepted (also for
     *         duplicates and for events we deliberately ignore); 401 = signature/timestamp
     *         rejected; 500 = we failed mid-processing and WANT Cashfree to retry.
     */
    public int handleWebhook(byte[] rawBody, String signature, String timestamp,
                             String idempotencyKey, String version, String attempt, String sourceIp) {
        String raw = rawBody == null ? "" : new String(rawBody, StandardCharsets.UTF_8);
        if (!notBlank(clientSecret)) {
            log.error("Webhook received but cashfree.client.secret is not configured");
            return 503;
        }
        // Source address first, before a byte of the body is trusted. Rejections go to the
        // process log only — never to a table, so the endpoint cannot be used to fill one.
        if (paymentLog.ipCheckEnabled() && !paymentLog.allows(sourceIp)) {
            log.warn("WEBHOOK rejected: source {} is not on cashfree.webhook.allowed.ips", sourceIp);
            return 403;
        }
        if (rawBody != null && rawBody.length > 1024 * 1024) {
            log.warn("WEBHOOK rejected: body over 1 MB from {}", sourceIp);
            return 413;
        }
        log.info("WEBHOOK received {} bytes from {}", rawBody == null ? 0 : rawBody.length, sourceIp);
        if (!notBlank(signature) || !notBlank(timestamp)) {
            log.warn("Webhook rejected: missing signature/timestamp headers");
            return 401;
        }

        JsonNode json = parseOrEmpty(raw);
        String type = json.path("type").asText(null);
        JsonNode data = json.path("data");
        String orderId = data.path("order").path("order_id").asText(null);
        String cfPaymentId = data.path("payment").path("cf_payment_id").asText(null);

        // Replay guard + signature (raw bytes — decimals like 170.00 must stay untouched).
        // 🔴 SIGNATURE FIRST, skew second — the order matters.
        // This used to check skew first and answer 401 when it failed. Cashfree retries a 401,
        // and every retry carries the ORIGINAL timestamp, so the skew only grows: the same
        // event is rejected again and again, forever. A genuine-but-late delivery must be
        // ACKNOWLEDGED (200) so the retries stop; it simply is not acted on.
        boolean valid;
        boolean tooOld = false;
        String reason = null;
        try {
            String computed = computeSignature(timestamp.trim(), rawBody == null ? new byte[0] : rawBody);
            valid = MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
            if (!valid) {
                reason = "SIGNATURE_MISMATCH";
            } else {
                long skewMs = Math.abs(System.currentTimeMillis() - Long.parseLong(timestamp.trim()));
                if (skewMs > webhookMaxSkewMinutes * 60_000L) {
                    tooOld = true;
                    reason = "TIMESTAMP_SKEW " + skewMs + "ms";
                }
            }
        } catch (Exception e) {
            valid = false;
            reason = "SIGNATURE_ERROR " + e.getMessage();
        }

        // Signed by Cashfree, just late. Record it and answer 200 — acting on it is unsafe
        // (it may be a replay), but retrying it forever is worse.
        if (valid && tooOld) {
            log.warn("WEBHOOK {} genuine but outside the {}-minute window ({}) — acknowledged, not processed",
                    orderId, webhookMaxSkewMinutes, reason);
            PaymentWebhookEvent stale = newEvent("STALE-" + System.nanoTime() + "-" + RNG.nextInt(1_000_000),
                    type, orderId, cfPaymentId, version, attempt, raw);
            stale.setSignatureValid(ActiveStatus.Y);
            stale.setProcessingError(reason);
            try { webhookEventRepository.save(stale); } catch (Exception ignored) { }
            return 200;
        }

        if (!valid) {
            log.warn("Webhook rejected for order {}: {}", orderId, reason);
            PaymentWebhookEvent bad = newEvent("INVALID-" + System.nanoTime() + "-" + RNG.nextInt(1_000_000),
                    type, orderId, cfPaymentId, version, attempt, raw);
            bad.setSignatureValid(ActiveStatus.N);
            bad.setProcessingError(reason);
            try { webhookEventRepository.save(bad); } catch (Exception ignored) { }
            return 401;
        }

        // Genuine, but for the other business on this Cashfree account (Litup Story, "LS-…").
        // Answer 200 so Cashfree stops retrying, and write nothing — not even an event row.
        if (orderId != null && !isOurOrder(orderId)) {
            log.info("WEBHOOK {} belongs to another business on this Cashfree account, ignored", orderId);
            return 200;
        }

        String key = notBlank(idempotencyKey) ? idempotencyKey.trim()
                : (type + ":" + orderId + ":" + cfPaymentId + ":" + timestamp.trim());
        if (key.length() > 128) key = key.substring(0, 128);

        PaymentWebhookEvent event = webhookEventRepository.findByIdempotencyKey(key).orElse(null);
        if (event != null && event.getProcessed() == ActiveStatus.Y) {
            log.info("Webhook duplicate ignored key={} type={} order={}", key, type, orderId);
            return 200;
        }
        if (event == null) event = newEvent(key, type, orderId, cfPaymentId, version, attempt, raw);
        event.setSignatureValid(ActiveStatus.Y);

        int code;
        try {
            process(type, data, orderId, event);
            event.setProcessed(ActiveStatus.Y);
            webhookEventRepository.save(event);
            code = 200;
        } catch (Exception e) {
            log.error("Webhook processing failed key={} type={} order={}", key, type, orderId, e);
            event.setProcessed(ActiveStatus.N);
            event.setProcessingError(trunc("EXCEPTION " + e.getMessage(), 1000));
            try { webhookEventRepository.save(event); } catch (Exception ignored) { }
            code = 500; // make Cashfree retry
        }
        // The verified webhook itself: Cashfree's body (redacted) and what we made of it.
        PaymentLog e = scope(orderId == null ? null : paymentOrderRepository.findByOrderId(orderId).orElse(null));
        if (e.getOrderId() == null) e.setOrderId(orderId);
        e.setStage("webhook");
        e.setDirection(PaymentLog.DIR_IN);
        e.setMethod("POST");
        e.setUrl("/payments/cashfree/webhook");
        e.setSourceIp(sourceIp);
        e.setRequestBody(PaymentRedactor.redact(raw));
        e.setResponseBody("{\"status\":" + code + "}");
        e.setHttpStatus(code);
        e.setOutcome(event.getProcessingError() == null ? (type == null ? "acknowledged" : type.toLowerCase()) : "issue");
        e.setError(event.getProcessingError());
        paymentLog.record(e);
        return code;
    }

    private void process(String type, JsonNode data, String orderId, PaymentWebhookEvent event) {
        if (type == null) { event.setProcessingError("NO_TYPE"); return; }
        switch (type) {
            case EVT_SUCCESS -> onPaymentSuccess(data, orderId, event);
            case EVT_FAILED, EVT_DROPPED -> onPaymentNotSuccessful(type, data, orderId, event);
            case EVT_CHARGES -> onCharges(data, orderId, event);
            default -> event.setProcessingError("IGNORED_TYPE " + type);
        }
    }

    private void onPaymentSuccess(JsonNode data, String orderId, PaymentWebhookEvent event) {
        PaymentOrder order = orderId == null ? null : paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null) { event.setProcessingError("UNKNOWN_ORDER"); return; }

        JsonNode payment = data.path("payment");
        String status = payment.path("payment_status").asText("");
        if (!"SUCCESS".equalsIgnoreCase(status)) { event.setProcessingError("STATUS_NOT_SUCCESS " + status); return; }

        BigDecimal paid = toDecimal(payment.path("payment_amount"));
        String currency = payment.path("payment_currency").asText("INR");
        if (paid == null || order.getAmount() == null || paid.compareTo(order.getAmount()) != 0
                || !"INR".equalsIgnoreCase(currency)) {
            event.setProcessingError("AMOUNT_MISMATCH expected " + order.getAmount() + " INR got " + paid + " " + currency);
            log.error("Webhook AMOUNT_MISMATCH for order {}: expected {} got {} {}", orderId, order.getAmount(), paid, currency);
            return;
        }

        if (order.getFulfilledAt() == null) {
            // Record the attempt BEFORE the claim; never save this instance after markPaid.
            applyPaymentFields(order, payment, "SUCCESS");
            paymentOrderRepository.save(order);
        }
        fulfil(orderId, "WEBHOOK");
    }

    private void onPaymentNotSuccessful(String type, JsonNode data, String orderId, PaymentWebhookEvent event) {
        PaymentOrder order = orderId == null ? null : paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null) { event.setProcessingError("UNKNOWN_ORDER"); return; }
        if (order.getFulfilledAt() != null) return; // a late FAILED for an earlier attempt — order already paid
        JsonNode payment = data.path("payment");
        String status = payment.path("payment_status").asText(EVT_DROPPED.equals(type) ? "USER_DROPPED" : "FAILED");
        applyPaymentFields(order, payment, status);
        String reason = data.path("error_details").path("error_description").asText(null);
        if (!notBlank(reason)) reason = payment.path("payment_message").asText(null);
        order.setFailureReason(trunc(reason, 500));
        paymentOrderRepository.save(order);
    }

    private void onCharges(JsonNode data, String orderId, PaymentWebhookEvent event) {
        PaymentOrder order = orderId == null ? null : paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null) { event.setProcessingError("UNKNOWN_ORDER"); return; }
        JsonNode c = data.path("charges_details");
        if (c.isMissingNode()) return;
        order.setServiceCharge(toDecimal(c.path("service_charge")));
        order.setServiceTax(toDecimal(c.path("service_tax")));
        order.setSettlementAmount(toDecimal(c.path("settlement_amount")));
        paymentOrderRepository.save(order);
    }

    private void applyPaymentFields(PaymentOrder order, JsonNode payment, String status) {
        order.setCfPaymentId(payment.path("cf_payment_id").asText(order.getCfPaymentId()));
        order.setPaymentStatus(status);
        order.setPaymentGroup(payment.path("payment_group").asText(order.getPaymentGroup()));
        order.setBankReference(payment.path("bank_reference").asText(order.getBankReference()));
        String t = payment.path("payment_time").asText(null);
        if (notBlank(t)) {
            try { order.setPaymentTime(OffsetDateTime.parse(t).atZoneSameInstant(IST).toLocalDateTime()); }
            catch (Exception ignored) { }
        }
    }

    // ───────────────────────────── 4. FULFILMENT ─────────────────────────────

    /** Claims the order (one winner) and activates. Returns true only for the winner. */
    private boolean fulfil(String orderId, String source) {
        int claimed = paymentOrderRepository.markPaid(orderId, source, istNow());
        if (claimed == 0) return false;
        log.info("Order {} claimed by {}", orderId, source);
        PaymentLog e = scope(paymentOrderRepository.findByOrderId(orderId).orElse(null));
        e.setStage("fulfilled");
        e.setDirection(PaymentLog.DIR_LOCAL);
        e.setOutcome("paid_by_" + source.toLowerCase());
        try {
            activate(orderId);
            // Receipt PDF by e-mail — best effort, after the plan/credit is on. fulfil() wins once
            // per order, so this is exactly one receipt whichever path (webhook / verify /
            // nightly) got here first. A mail failure must never look like a failed payment.
            try { invoiceService.issueAndEmail(orderId); }
            catch (Exception mailEx) { log.error("Order {} paid but receipt e-mail failed", orderId, mailEx); }
        } catch (Exception ex) {
            // Order is PAID but the plan didn't switch on — visible in the admin list as
            // PAID with no subscriptionId; POST /admin/orders/{id}/reverify retries it.
            log.error("Order {} PAID but activation failed", orderId, ex);
            e.setOutcome("paid_activation_failed");
            e.setError(trunc(ex.getMessage(), 512));
        }
        paymentLog.record(e);
        return true;
    }

    private void activate(String orderId) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElseThrow();
        UserEntity user = userRepository.findById(order.getUserId()).orElseThrow();
        String ref = "CF_" + (notBlank(order.getCfPaymentId()) ? order.getCfPaymentId() : order.getOrderId());

        if (PaymentOrder.PURPOSE_BOOST.equals(order.getPurpose())) {
            boolean credited = activationService.addBoostCredit(user.getUserId());
            if (!credited) log.warn("Boost paid by user {} but no ACTIVE subscription to credit (order {})", user.getUserId(), orderId);
            try { whatsAppService.paymentReceipt(user, order.getAmount(), "Profile Boost", orderId, istNow()); } catch (Exception ignored) { }
            return;
        }
        SubscriptionPlan plan = subscriptionPlanRepository.findById(order.getPlanId()).orElseThrow();
        UserSubscriptions sub = activationService.activatePlan(user, plan, ref, "PAYMENT_SUCCESS");
        try { whatsAppService.paymentReceipt(user, order.getAmount(), plan.getTitle(), orderId, istNow()); } catch (Exception ignored) { }
        order = paymentOrderRepository.findByOrderId(orderId).orElseThrow(); // reload — see class Javadoc
        order.setSubscriptionId(sub.getId());
        paymentOrderRepository.save(order);
    }

    /** Admin/support: e-mail the receipt again (rebuilds the same PDF). */
    public ResultResponse adminResendReceipt(String orderId) {
        ResultResponse r = new ResultResponse();
        if (invoiceService.resend(orderId)) {
            r.setCode(200); r.setStatus(ResponseStatus.SUCCESS); r.setMessage("Receipt sent");
            return r;
        }
        return fail(r, 400, "Receipt could not be sent (order not PAID, or member has no e-mail)");
    }

    /** Member download: the receipt PDF for their own paid order, or null. */
    public byte[] receiptPdf(String orderId, Long requesterId) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null || requesterId == null || !requesterId.equals(order.getUserId())) return null;
        return invoiceService.pdfFor(orderId);
    }

    // ───────────────────────────── 5. ADMIN ─────────────────────────────

    public PaginatedResultResponse adminListOrders(String status, Long userId, int page, int size) {
        PaginatedResultResponse r = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
            Page<PaymentOrder> p;
            boolean hasStatus = notBlank(status);
            if (userId != null && hasStatus) p = paymentOrderRepository.findByUserIdAndOrderStatusOrderByIdDesc(userId, status.trim().toUpperCase(), pageable);
            else if (userId != null) p = paymentOrderRepository.findByUserIdOrderByIdDesc(userId, pageable);
            else if (hasStatus) p = paymentOrderRepository.findByOrderStatusOrderByIdDesc(status.trim().toUpperCase(), pageable);
            else p = paymentOrderRepository.findAllByOrderByIdDesc(pageable);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (PaymentOrder o : p.getContent()) {
                Map<String, Object> row = statusPayload(o);
                row.put("id", o.getId());
                row.put("userId", o.getUserId());
                row.put("cfOrderId", o.getCfOrderId());
                row.put("cfPaymentId", o.getCfPaymentId());
                row.put("bankReference", o.getBankReference());
                row.put("paymentTime", o.getPaymentTime());
                row.put("fulfilledAt", o.getFulfilledAt());
                row.put("fulfilledBy", o.getFulfilledBy());
                row.put("settlementAmount", o.getSettlementAmount());
                row.put("createdAt", o.getCreatedAt());
                row.remove("paymentSessionId");
                userRepository.findById(o.getUserId()).ifPresent(u -> {
                    row.put("userName", SubscriptionActivationService.fullName(u));
                    row.put("userMobile", u.getMobile());
                    row.put("userEmail", u.getEmail());
                });
                rows.add(row);
            }
            r.setCode(200);
            r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage("Payment orders fetched");
            r.setData(rows);
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(page);
            pg.setPageSize(size);
            r.setPaginationData(pg);
        } catch (Exception e) {
            r.setCode(500);
            r.setStatus(ResponseStatus.FAILURE);
            r.setMessage("Error fetching payment orders: " + e.getMessage());
        }
        return r;
    }

    /** Support tool for a lost webhook or a PAID order whose activation threw. */
    public ResultResponse adminReverify(String orderId) {
        ResultResponse r = new ResultResponse();
        try {
            PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElse(null);
            if (order == null) return fail(r, 404, "Order not found");
            if (!isConfigured()) return fail(r, 503, "PAYMENT_GATEWAY_DISABLED");
            JsonNode cf = fetchOrder(order);
            if (cf == null) return fail(r, 502, "Could not fetch order from Cashfree");
            String st = cf.path("order_status").asText("");
            String action;
            if (PaymentOrder.STATUS_PAID.equals(st)) {
                if (order.getFulfilledAt() == null) {
                    action = fulfil(orderId, "ADMIN") ? "FULFILLED" : "ALREADY_CLAIMED";
                } else if (order.getSubscriptionId() == null && PaymentOrder.PURPOSE_SUBSCRIPTION.equals(order.getPurpose())) {
                    activate(orderId);
                    action = "ACTIVATION_RETRIED";
                } else {
                    action = "ALREADY_FULFILLED";
                }
            } else {
                if (!st.isEmpty() && !st.equals(order.getOrderStatus()) && order.getFulfilledAt() == null) {
                    order.setOrderStatus(st);
                    paymentOrderRepository.save(order);
                }
                action = "CASHFREE_STATUS_" + st;
            }
            order = paymentOrderRepository.findByOrderId(orderId).orElse(order);
            Map<String, Object> data = statusPayload(order);
            data.put("action", action);
            data.put("cashfreeOrderStatus", st);
            PaymentLog e = scope(order);
            e.setStage("reverify");
            e.setDirection(PaymentLog.DIR_LOCAL);
            e.setMethod("POST");
            e.setUrl("/payments/cashfree/admin/orders/" + orderId + "/reverify");
            e.setOutcome(action.toLowerCase());
            paymentLog.record(e);
            r.setCode(200);
            r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage(action);
            r.setData(data);
        } catch (Exception e) {
            log.error("adminReverify failed for {}", orderId, e);
            fail(r, 500, "Error re-verifying order: " + e.getMessage());
        }
        return r;
    }

    public ResultResponse adminListEvents(String orderId) {
        ResultResponse r = new ResultResponse();
        r.setCode(200);
        r.setStatus(ResponseStatus.SUCCESS);
        r.setMessage("Webhook events fetched");
        r.setData(webhookEventRepository.findByOrderIdOrderByIdDesc(orderId));
        return r;
    }

    public ResultResponse adminOrderLog(String orderId) {
        ResultResponse r = new ResultResponse();
        r.setCode(200);
        r.setStatus(ResponseStatus.SUCCESS);
        r.setMessage("Payment log fetched");
        r.setData(paymentLog.forOrder(orderId));
        return r;
    }

    public ResultResponse adminDayLog(String day, int limit) {
        ResultResponse r = new ResultResponse();
        try {
            java.time.LocalDate d = notBlank(day) ? java.time.LocalDate.parse(day.trim()) : java.time.LocalDate.now(IST);
            r.setCode(200);
            r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage("Payment log for " + d);
            r.setData(paymentLog.forDay(d, limit));
        } catch (Exception e) {
            fail(r, 400, "day must be YYYY-MM-DD");
        }
        return r;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** A log entry filed under an order and attempt. Any argument may be null. */
    private PaymentLog scope(String orderId, Long paymentOrderId, Long userId, String cfOrderId) {
        PaymentLog e = paymentLog.entry("cashfree", PaymentLog.DIR_OUT);
        e.setOrderId(orderId);
        e.setPaymentOrderId(paymentOrderId);
        e.setUserId(userId);
        e.setCfOrderId(cfOrderId);
        return e;
    }

    private PaymentLog scope(PaymentOrder o) {
        if (o == null) return scope(null, null, null, null);
        return scope(o.getOrderId(), o.getId(), o.getUserId(), o.getCfOrderId());
    }

    private JsonNode fetchOrder(PaymentOrder order) {
        String orderId = order.getOrderId();
        try {
            HttpResponse<String> resp = send("GET", "/orders/" + orderId, null, scope(order));
            if (resp.statusCode() >= 300) {
                log.warn("Cashfree get order {} failed {} {}", orderId, resp.statusCode(), resp.body());
                return null;
            }
            return parseOrEmpty(resp.body());
        } catch (Exception e) {
            log.warn("Cashfree get order {} error {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * One call to Cashfree, recorded in the payment log: URL, request body, response body,
     * status and duration — never the headers, which carry the API secret. Filed under
     * whatever {@code scope} names; the stage is derived from the path.
     */
    private HttpResponse<String> send(String method, String path, String body, PaymentLog scope) throws Exception {
        String url = baseUrl.replaceAll("/$", "") + path;
        long started = System.nanoTime();
        scope.setStage(stageFor(method, path));
        scope.setDirection(PaymentLog.DIR_OUT);
        scope.setMethod(method);
        scope.setUrl(url);
        scope.setRequestBody(PaymentRedactor.redact(body));
        try {
            HttpResponse<String> resp = sendRaw(method, url, body);
            scope.setDurationMs((int) ((System.nanoTime() - started) / 1_000_000));
            scope.setHttpStatus(resp.statusCode());
            scope.setResponseBody(PaymentRedactor.redact(resp.body()));
            if (resp.statusCode() >= 300) {
                scope.setOutcome("error");
                scope.setError(trunc("HTTP " + resp.statusCode() + ": " + parseOrEmpty(resp.body()).path("message").asText(""), 512));
            } else {
                scope.setOutcome("ok");
            }
            return resp;
        } catch (Exception e) {
            scope.setDurationMs((int) ((System.nanoTime() - started) / 1_000_000));
            scope.setOutcome("error");
            scope.setError(trunc(e.toString(), 512));
            throw e;
        } finally {
            paymentLog.record(scope);
        }
    }

    private static String stageFor(String method, String path) {
        if ("POST".equals(method) && "/orders".equals(path)) return "create_order";
        if ("GET".equals(method) && path.endsWith("/payments")) return "get_payments";
        if ("GET".equals(method) && path.startsWith("/orders/")) return "get_order";
        return "cashfree";
    }

    private HttpResponse<String> sendRaw(String method, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("x-client-id", clientId)
                .header("x-client-secret", clientSecret)
                .header("x-api-version", apiVersion)
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            b.header("Content-Type", "application/json")
             .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body, StandardCharsets.UTF_8));
        } else {
            b.GET();
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    String computeSignature(String timestamp, byte[] rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update(rawBody);
        return Base64.getEncoder().encodeToString(mac.doFinal());
    }

    private Map<String, Object> clientPayload(PaymentOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getOrderId());
        m.put("cfOrderId", o.getCfOrderId());
        m.put("paymentSessionId", o.getPaymentSessionId());
        m.put("orderStatus", o.getOrderStatus());
        m.put("purpose", o.getPurpose());
        m.put("planId", o.getPlanId());
        m.put("amount", o.getAmount());
        m.put("currency", o.getCurrency());
        m.put("expiresAt", o.getExpiresAt());
        m.put("env", env);
        return m;
    }

    private Map<String, Object> statusPayload(PaymentOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getOrderId());
        m.put("orderStatus", o.getOrderStatus());
        m.put("paymentStatus", o.getPaymentStatus());
        m.put("paymentGroup", o.getPaymentGroup());
        m.put("failureReason", o.getFailureReason());
        m.put("purpose", o.getPurpose());
        m.put("planId", o.getPlanId());
        if (o.getPlanId() != null) {
            Optional<SubscriptionPlan> plan = subscriptionPlanRepository.findById(o.getPlanId());
            m.put("planTitle", plan.map(SubscriptionPlan::getTitle).orElse(null));
            m.put("planDurationDays", plan.map(SubscriptionPlan::getDurationDays).orElse(null));
        } else {
            m.put("planTitle", PaymentOrder.PURPOSE_BOOST.equals(o.getPurpose()) ? "Profile Boost" : null);
        }
        m.put("amount", o.getAmount());
        m.put("currency", o.getCurrency());
        m.put("fulfilled", o.getFulfilledAt() != null);
        m.put("subscriptionId", o.getSubscriptionId());
        m.put("expiresAt", o.getExpiresAt());
        m.put("env", env);
        // Only while the order is still payable — it's what /pay/:orderId needs to open checkout.
        m.put("paymentSessionId", PaymentOrder.STATUS_ACTIVE.equals(o.getOrderStatus()) ? o.getPaymentSessionId() : null);
        return m;
    }

    private PaymentWebhookEvent newEvent(String key, String type, String orderId, String cfPaymentId,
                                         String version, String attempt, String raw) {
        PaymentWebhookEvent e = new PaymentWebhookEvent();
        e.setIdempotencyKey(key);
        e.setEventType(trunc(type, 64));
        e.setOrderId(trunc(orderId, 45));
        e.setCfPaymentId(trunc(cfPaymentId, 64));
        e.setWebhookVersion(trunc(version, 16));
        e.setAttempt(trunc(attempt, 8));
        e.setRawPayload(raw);
        e.setCreatedBy("cashfree");
        return e;
    }

    private static String newOrderId(Long userId) {
        StringBuilder sb = new StringBuilder("VVM-").append(userId).append('-')
                .append(Long.toString(System.currentTimeMillis(), 36).toUpperCase()).append('-');
        for (int i = 0; i < 8; i++) sb.append(ALNUM.charAt(RNG.nextInt(ALNUM.length())));
        return sb.toString();
    }

    static String normalisePhone(String mobile) {
        String d = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (d.length() == 10) return "+91" + d;
        if (d.length() == 11 && d.startsWith("0")) return "+91" + d.substring(1);
        if (d.length() == 12 && d.startsWith("91")) return "+" + d;
        return d.isEmpty() ? "" : "+" + d;
    }

    private JsonNode parseOrEmpty(String s) {
        try { return s == null || s.isBlank() ? mapper.createObjectNode() : mapper.readTree(s); }
        catch (Exception e) { return mapper.createObjectNode(); }
    }

    private static BigDecimal toDecimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try { return new BigDecimal(n.asText()).setScale(2, RoundingMode.HALF_UP); }
        catch (Exception e) { return null; }
    }

    private static ResultResponse fail(ResultResponse r, int code, String msg) {
        r.setCode(code);
        r.setStatus(ResponseStatus.FAILURE);
        r.setMessage(msg);
        return r;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
