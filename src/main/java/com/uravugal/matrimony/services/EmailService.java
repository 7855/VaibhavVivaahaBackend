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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // ========= PUBLIC METHODS =========

    public void sendOtpEmail(String toEmail, String firstName, String otp, String purpose) {
        String templateKey = "registration".equalsIgnoreCase(purpose) ? tplOtpRegistration : tplOtpReset;
        Map<String, Object> merge = new HashMap<>();
        merge.put("firstName", safeName(firstName));
        merge.put("otp", otp);
        sendTemplate(templateKey, toEmail, firstName, merge, "OTP (" + purpose + ")");
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

    // ========= ZEPTOMAIL SENDER =========

    private void sendTemplate(String templateKey, String toEmail, String toName,
                              Map<String, Object> mergeInfo, String label) {
        if (!enabled || apiKey == null || apiKey.isBlank() || apiKey.startsWith("CHANGE_ME")) {
            log.info("[EmailService MOCK] {} → {} merge={}", label, toEmail, mergeInfo);
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
            payload.put("mail_template_key", templateKey);
            payload.put("from", fromObj);
            payload.put("to", new Object[] { toEntry });
            payload.put("merge_info", mergeInfo);

            String body = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
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