package com.uravugal.matrimony.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Email service using Zoho Zeptomail Template API.
 * Templates are created in Zoho console; we only send merge_info.
 * If zeptomail.enabled=false or api key missing, falls back to logging.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${zeptomail.enabled:false}")
    private boolean enabled;

    @Value("${zeptomail.api.url}")
    private String apiUrl;

    @Value("${zeptomail.api.key}")
    private String apiKey;

    @Value("${zeptomail.from.address}")
    private String fromAddress;

    @Value("${zeptomail.from.name}")
    private String fromName;

    @Value("${zeptomail.template.otp-registration}")
    private String tplOtpRegistration;

    @Value("${zeptomail.template.otp-reset}")
    private String tplOtpReset;

    @Value("${zeptomail.template.approval}")
    private String tplApproval;

    @Value("${zeptomail.template.rejection}")
    private String tplRejection;

    @Value("${app.link:#}")
    private String appLink;

    // ⚠️ Bounded timeouts are load-bearing: emails are sent SYNCHRONOUSLY inside
    // request threads, and with open-in-view each hung request pins a Hikari
    // connection (pool=20). An untimed client + slow ZeptoMail = intermittent
    // whole-API stalls (every endpoint timing out at once). Seen live 2026-09-04.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    // ========= PUBLIC METHODS =========

    /** @return true when ZeptoMail accepted the mail (after one automatic retry). */
    public boolean sendOtpEmail(String toEmail, String firstName, String otp, String purpose) {
        String templateKey = "registration".equalsIgnoreCase(purpose) ? tplOtpRegistration : tplOtpReset;
        Map<String, Object> merge = new HashMap<>();
        merge.put("firstName", safeName(firstName));
        merge.put("otp", otp);
        return sendTemplate(templateKey, toEmail, firstName, merge, "OTP (" + purpose + ")");
    }

    public void sendApprovalEmail(String toEmail, String firstName) {
        Map<String, Object> merge = new HashMap<>();
        merge.put("firstName", safeName(firstName));
        merge.put("appLink", appLink);
        sendTemplate(tplApproval, toEmail, firstName, merge, "Profile Approved");
    }

    public void sendRejectionEmail(String toEmail, String firstName, String reason) {
        Map<String, Object> merge = new HashMap<>();
        merge.put("firstName", safeName(firstName));
        merge.put("rejectionReason", safe(reason));
        merge.put("appLink", appLink);
        sendTemplate(tplRejection, toEmail, firstName, merge, "Profile Rejected");
    }

    /**
     * Admin assigned a plan to the member (Users -> Assign Plan). Sent through
     * ZeptoMail's PLAIN send API with inline HTML rather than the Template API,
     * so it works without first creating a template in the Zoho console.
     */
    public void sendPlanAssignedEmail(String toEmail, String firstName, String planTitle, String validUntil) {
        String subject = "Your " + planTitle + " membership is now active";
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;"
                + "border:1px solid #eee;border-radius:12px;overflow:hidden\">"
                + "<div style=\"background:#420001;padding:20px 24px\">"
                + "<span style=\"color:#F6B733;font-size:18px;font-weight:bold\">Vaibhav Vivaaha Matrimony</span></div>"
                + "<div style=\"padding:24px\">"
                + "<p style=\"font-size:15px;color:#222\">Dear " + safeName(firstName) + ",</p>"
                + "<p style=\"font-size:14px;color:#444;line-height:1.6\">Great news — your "
                + "<b style=\"color:#420001\">" + planTitle + "</b> membership has been activated on your profile."
                // `validUntil` already reads "until 26 Dec 2026" or "until marriage".
                + (validUntil != null ? " It is valid <b>" + validUntil + "</b>." : "") + "</p>"
                + "<p style=\"font-size:14px;color:#444;line-height:1.6\">Open the app to start enjoying your "
                + "membership benefits right away.</p>"
                + "<p style=\"font-size:13px;color:#888;margin-top:24px\">Warm regards,<br/>Team Vaibhav Vivaaha</p>"
                + "</div></div>";
        sendHtml(toEmail, firstName, subject, html, "Plan Assigned (" + planTitle + ")");
    }

    // ========= ZEPTOMAIL SENDER =========

    /**
     * One transparent retry on failure: transient ZeptoMail hiccups were the
     * "OTP sometimes never arrives" reports — the first attempt failed, nothing
     * retried, and the caller was told success anyway. Returns whether ANY
     * attempt was accepted, so critical paths (OTP) can tell the member the
     * truth instead of pretending the mail went out.
     */
    private boolean sendTemplate(String templateKey, String toEmail, String toName,
                              Map<String, Object> mergeInfo, String label) {
        if (!enabled || apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            log.info("[EmailService MOCK] {} → {} merge={}", label, toEmail, mergeInfo);
            return true;
        }
        if (sendTemplateOnce(templateKey, toEmail, toName, mergeInfo, label)) return true;
        log.warn("[EmailService] {} first attempt failed — retrying once", label);
        return sendTemplateOnce(templateKey, toEmail, toName, mergeInfo, label + " (retry)");
    }

    private boolean sendTemplateOnce(String templateKey, String toEmail, String toName,
                              Map<String, Object> mergeInfo, String label) {

        try {
            Map<String, Object> fromObj = new HashMap<>();
            fromObj.put("address", fromAddress);
            fromObj.put("name", fromName);

            Map<String, Object> toAddr = new HashMap<>();
            toAddr.put("address", toEmail);
            toAddr.put("name", safeName(toName));

            Map<String, Object> toEntry = new HashMap<>();
            toEntry.put("email_address", toAddr);

            Map<String, Object> payload = new HashMap<>();
            payload.put("mail_template_key", templateKey);
            payload.put("from", fromObj);
            payload.put("to", new Object[] { toEntry });
            payload.put("merge_info", mergeInfo);

            String body = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .uri(URI.create(apiUrl))
                    .header("Authorization", apiKey.startsWith("Zoho-enczapikey") ? apiKey : "Zoho-enczapikey " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EmailService] {} sent to {} (status {})", label, toEmail, response.statusCode());
                return true;
            }
            log.error("[EmailService] {} FAILED to {} — status {} body {}", label, toEmail, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("[EmailService] {} exception to {}: {}", label, toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Plain-send WITH a PDF attached (payment receipts). ZeptoMail's v1.1/email accepts
     * attachments inline as base64 — no upload step. Returns whether ZeptoMail accepted it,
     * so the caller can record SENT / FAILED against the receipt row. One transparent retry,
     * for the same reason sendTemplate has one.
     */
    public boolean sendHtmlWithPdf(String toEmail, String toName, String subject, String html,
                                   byte[] pdf, String fileName, String label) {
        if (!enabled || apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            log.info("[EmailService MOCK] {} → {} subject={} pdf={}B", label, toEmail, subject, pdf == null ? 0 : pdf.length);
            return true;
        }
        if (sendHtmlWithPdfOnce(toEmail, toName, subject, html, pdf, fileName, label)) return true;
        log.warn("[EmailService] {} first attempt failed — retrying once", label);
        return sendHtmlWithPdfOnce(toEmail, toName, subject, html, pdf, fileName, label + " (retry)");
    }

    private boolean sendHtmlWithPdfOnce(String toEmail, String toName, String subject, String html,
                                        byte[] pdf, String fileName, String label) {
        try {
            Map<String, Object> fromObj = new HashMap<>();
            fromObj.put("address", fromAddress);
            fromObj.put("name", fromName);
            Map<String, Object> toAddr = new HashMap<>();
            toAddr.put("address", toEmail);
            toAddr.put("name", safeName(toName));
            Map<String, Object> toEntry = new HashMap<>();
            toEntry.put("email_address", toAddr);

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("name", fileName);
            attachment.put("mime_type", "application/pdf");
            attachment.put("content", java.util.Base64.getEncoder().encodeToString(pdf));

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromObj);
            payload.put("to", new Object[] { toEntry });
            payload.put("subject", subject);
            payload.put("htmlbody", html);
            payload.put("attachments", new Object[] { attachment });

            String body = mapper.writeValueAsString(payload);
            String plainUrl = apiUrl.replace("/email/template", "/email");
            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(java.time.Duration.ofSeconds(15))
                    .uri(URI.create(plainUrl))
                    .header("Authorization", apiKey.startsWith("Zoho-enczapikey") ? apiKey : "Zoho-enczapikey " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EmailService] {} sent to {} (status {})", label, toEmail, response.statusCode());
                return true;
            }
            log.error("[EmailService] {} FAILED to {} — status {} body {}", label, toEmail, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("[EmailService] {} exception to {}: {}", label, toEmail, e.getMessage(), e);
            return false;
        }
    }

    /** ZeptoMail plain-send (v1.1/email) — the template URL minus its /template suffix. */
    private void sendHtml(String toEmail, String toName, String subject, String html, String label) {
        if (!enabled || apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            log.info("[EmailService MOCK] {} → {} subject={}", label, toEmail, subject);
            return;
        }
        try {
            Map<String, Object> fromObj = new HashMap<>();
            fromObj.put("address", fromAddress);
            fromObj.put("name", fromName);

            Map<String, Object> toAddr = new HashMap<>();
            toAddr.put("address", toEmail);
            toAddr.put("name", safeName(toName));
            Map<String, Object> toEntry = new HashMap<>();
            toEntry.put("email_address", toAddr);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", fromObj);
            payload.put("to", new Object[] { toEntry });
            payload.put("subject", subject);
            payload.put("htmlbody", html);

            String body = mapper.writeValueAsString(payload);
            String plainUrl = apiUrl.replace("/email/template", "/email");
            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .uri(URI.create(plainUrl))
                    .header("Authorization", apiKey.startsWith("Zoho-enczapikey") ? apiKey : "Zoho-enczapikey " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EmailService] {} sent to {} (status {})", label, toEmail, response.statusCode());
            } else {
                log.error("[EmailService] {} FAILED to {} — status {} body {}", label, toEmail, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[EmailService] {} exception to {}: {}", label, toEmail, e.getMessage(), e);
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "there";
        return name;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}