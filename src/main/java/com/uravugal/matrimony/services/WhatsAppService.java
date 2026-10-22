package com.uravugal.matrimony.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * WhatsApp messages through Zoho CPaaS (the same Zoho account as ZeptoMail,
 * same enczapikey, but the INDIA endpoint — cpaas.zoho.com rejects the key).
 * Today it sends one approved template: the OTP ("{{code}} is your
 * verification code…", footer "expires in 5 minutes", a Copy Code button).
 *
 * Every send is a paid message, so callers rate-limit per number. Timeouts
 * are bounded for the same reason EmailService's are: this runs inside the
 * request thread.
 */
@Service
public class WhatsAppService {

    @Value("${zoho.cpaas.enabled:true}")
    private boolean enabled;

    @Value("${zoho.cpaas.api.url:https://cpaas.zoho.in/v1.1/whatsapp}")
    private String apiUrl;

    @Value("${zoho.cpaas.api.key:${zeptomail.api.key:}}")
    private String apiKey;

    @Value("${zoho.cpaas.from:+917200858854}")
    private String fromNumber;

    @Value("${zoho.cpaas.template.otp:3fa7ab80-c6fb-11f1-a810-525400c92439}")
    private String otpTemplateKey;

    // Notification templates (approved in Zoho 2026-10-15). Merge keys per template:
    //   welcome            name, member_id
    //   profile_approved   name, member_id
    //   profile_rejected   name, member_id, reason
    //   plan_activated     name, plan, valid_until
    //   payment_receipt    name, amount, plan, order_id, paid_on
    //   permission_request name (owner), requester, field
    //   permission_decision name (requester), owner, decision, field, "1" (owner's base64 id → URL button)
    @Value("${zoho.cpaas.template.forgot-pin:36cd31e0-c950-11f1-85c1-8e9a6c33ddc2}")
    private String tplForgotPin;

    @Value("${zoho.cpaas.template.welcome:e6943cd1-c866-11f1-85c1-8e9a6c33ddc2}")
    private String tplWelcome;
    @Value("${zoho.cpaas.template.profile-approved:5eccc1e0-c867-11f1-a8d1-525400c92439}")
    private String tplProfileApproved;
    @Value("${zoho.cpaas.template.profile-rejected:f0258220-c868-11f1-9299-ae9c7e0b6a9f}")
    private String tplProfileRejected;
    @Value("${zoho.cpaas.template.plan-activated:50dcb980-c869-11f1-a992-d2cf08f4ca8c}")
    private String tplPlanActivated;
    @Value("${zoho.cpaas.template.payment-receipt:943cf460-c869-11f1-85c1-8e9a6c33ddc2}")
    private String tplPaymentReceipt;
    @Value("${zoho.cpaas.template.permission-request:5e18d3d1-c86a-11f1-8da8-cabf48e1bf81}")
    private String tplPermissionRequest;
    @Value("${zoho.cpaas.template.permission-decision:41e94f40-c889-11f1-9299-ae9c7e0b6a9f}")
    private String tplPermissionDecision;

    /**
     * Notifications go out on a small background pool: they are best-effort and
     * must never slow an admin action or a checkout. The OTP stays synchronous
     * because the member is waiting for its result.
     */
    private final java.util.concurrent.ExecutorService notifier =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "whatsapp-notify");
                t.setDaemon(true);
                return t;
            });

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** The last failure reason, for the API's error message and the log. */
    private volatile String lastError = "";

    public String getLastError() {
        return lastError;
    }

    // ── Member notifications ────────────────────────────────────────────

    private static String firstName(com.uravugal.matrimony.models.UserEntity u) {
        String n = u == null || u.getFirstName() == null ? "" : u.getFirstName().trim();
        return n.isEmpty() ? "there" : n;
    }

    private static String mobileOf(com.uravugal.matrimony.models.UserEntity u) {
        String m = u == null || u.getMobile() == null ? "" : u.getMobile().replaceAll("\\D", "");
        if (m.length() == 12 && m.startsWith("91")) m = m.substring(2);
        return m.matches("^[6-9]\\d{9}$") ? m : null;
    }

    public void welcome(com.uravugal.matrimony.models.UserEntity u) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(u));
        m.put("member_id", safe(u.getMemberId()));
        notify(u, tplWelcome, m, "welcome");
    }

    public void profileApproved(com.uravugal.matrimony.models.UserEntity u) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(u));
        m.put("member_id", safe(u.getMemberId()));
        notify(u, tplProfileApproved, m, "approved");
    }

    public void profileRejected(com.uravugal.matrimony.models.UserEntity u, String reason) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(u));
        m.put("member_id", safe(u.getMemberId()));
        m.put("reason", safe(reason).isEmpty() ? "Please check your profile details." : safe(reason));
        notify(u, tplProfileRejected, m, "rejected");
    }

    /** validUntil already reads "until 12 Apr 2027" or "until marriage". */
    public void planActivated(com.uravugal.matrimony.models.UserEntity u, String plan, String validUntil) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(u));
        m.put("plan", safe(plan));
        m.put("valid_until", safe(validUntil));
        notify(u, tplPlanActivated, m, "plan");
    }

    public void paymentReceipt(com.uravugal.matrimony.models.UserEntity u, java.math.BigDecimal amount,
                               String plan, String orderId, java.time.LocalDateTime paidOn) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(u));
        m.put("amount", amount == null ? "0" : inr(amount));
        m.put("plan", safe(plan));
        m.put("order_id", safe(orderId));
        m.put("paid_on", (paidOn == null ? java.time.LocalDateTime.now() : paidOn)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")));
        notify(u, tplPaymentReceipt, m, "receipt");
    }

    /** To the profile OWNER: someone asked to see a field. requester is already plan-masked. */
    public void permissionRequest(com.uravugal.matrimony.models.UserEntity owner, String requester, String field) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(owner));
        m.put("requester", "Someone".equalsIgnoreCase(safe(requester)) || safe(requester).isEmpty() ? "A member" : requester.trim());
        m.put("field", safe(field));
        notify(owner, tplPermissionRequest, m, "perm-req");
    }

    /** To the REQUESTER: the owner decided. The URL button carries the owner's base64 id. */
    public void permissionDecision(com.uravugal.matrimony.models.UserEntity requester, com.uravugal.matrimony.models.UserEntity owner,
                                   String ownerName, boolean approved, String field) {
        Map<String, String> m = new HashMap<>();
        m.put("name", firstName(requester));
        m.put("owner", "Someone".equalsIgnoreCase(safe(ownerName)) || safe(ownerName).isEmpty() ? "A member" : ownerName.trim());
        m.put("decision", approved ? "accepted" : "declined");
        m.put("field", safe(field));
        m.put("1", owner == null ? "" : java.util.Base64.getEncoder().encodeToString(String.valueOf(owner.getUserId()).getBytes()));
        notify(requester, tplPermissionDecision, m, "perm-dec");
    }

    private void notify(com.uravugal.matrimony.models.UserEntity to, String templateKey, Map<String, String> merge, String what) {
        String mobile = mobileOf(to);
        if (mobile == null || templateKey == null || templateKey.isBlank()) return;
        String ref = "vvm-" + what + "-" + (to.getUserId() == null ? "0" : to.getUserId());
        try {
            notifier.submit(() -> sendTemplate(mobile, templateKey, merge, ref));
        } catch (Exception e) {
            System.out.println("[WhatsApp] could not queue " + what + ": " + e.getMessage());
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    /** "4,999" / "1,00,000" — Indian grouping, no decimals when whole. */
    static String inr(java.math.BigDecimal amount) {
        java.math.BigDecimal a = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        boolean whole = a.stripTrailingZeros().scale() <= 0;
        long rupees = a.longValue();
        String s = String.valueOf(Math.abs(rupees));
        if (s.length() > 3) {
            String head = s.substring(0, s.length() - 3), tail = s.substring(s.length() - 3);
            StringBuilder b = new StringBuilder();
            while (head.length() > 2) {
                b.insert(0, "," + head.substring(head.length() - 2));
                head = head.substring(0, head.length() - 2);
            }
            s = head + b + "," + tail;
        }
        if (!whole) s += a.toPlainString().substring(a.toPlainString().indexOf('.'));
        return (rupees < 0 ? "-" : "") + s;
    }

    /**
     * Sends the OTP template to a 10-digit Indian mobile. One automatic retry
     * on a network error; a 4xx/5xx from Zoho (bad token, no credit) is final.
     * @return true when Zoho accepted the message.
     */
    public boolean sendOtp(String mobile10, String code, String reference) {
        Map<String, String> merge = new HashMap<>();
        merge.put("code", code);
        return sendTemplate(mobile10, otpTemplateKey, merge, reference);
    }

    /** The forgot-PIN code uses its own template ("…code to reset your PIN"). */
    public boolean sendForgotPinOtp(String mobile10, String code, String reference) {
        Map<String, String> merge = new HashMap<>();
        merge.put("code", code);
        return sendTemplate(mobile10, tplForgotPin, merge, reference);
    }

    /** Sends any approved template. Synchronous; see notify() for the fire-and-forget path. */
    public boolean sendTemplate(String mobile10, String templateKey, Map<String, String> merge, String reference) {
        if (!enabled) {
            System.out.println("[WhatsApp] disabled — " + templateKey + " for " + mobile10 + " not sent: " + merge);
            return true;
        }
        if (apiKey == null || apiKey.isBlank()) {
            lastError = "WhatsApp is not configured";
            return false;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("from", fromNumber);
        body.put("to", "+91" + mobile10);
        body.put("template_key", templateKey);
        body.put("merge_info", merge);
        body.put("client_reference", reference);

        String auth = apiKey.trim().startsWith("Zoho-enczapikey") ? apiKey.trim() : "Zoho-enczapikey " + apiKey.trim();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", auth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    lastError = "";
                    return true;
                }
                lastError = describe(res.statusCode(), res.body());
                System.out.println("[WhatsApp] " + res.statusCode() + " for " + mobile10 + ": " + res.body());
                return false; // Zoho answered; retrying the same request will not change it
            } catch (Exception e) {
                lastError = "network: " + e.getMessage();
                System.out.println("[WhatsApp] attempt " + attempt + " failed for " + mobile10 + ": " + e.getMessage());
            }
        }
        return false;
    }

    private static String describe(int status, String body) {
        String b = body == null ? "" : body;
        if (b.contains("Credit exhausted") || b.contains("LE_102")) return "WhatsApp credits are exhausted";
        if (b.contains("Invalid API Token") || status == 401) return "WhatsApp API key rejected";
        if (status == 429) return "WhatsApp rate limit";
        return "WhatsApp error " + status;
    }
}
