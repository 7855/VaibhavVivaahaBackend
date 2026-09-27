package com.uravugal.matrimony.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Redaction for the payment log.
 *
 * PCI DSS draws a hard line: sensitive authentication data (CVV, full track, PIN) may never
 * be stored after authorisation, and a PAN may only be stored unreadable and masked. Card
 * entry happens on Cashfree's hosted page, so card data should never reach this process.
 * This class exists so that "should never" is not the only safeguard: whatever body passes
 * through, those fields cannot land in the database.
 *
 * Beyond card data it strips anything that behaves like a credential and masks two things
 * that are not secrets but should not sit in a log in full: the payment session id, which
 * opens the checkout, and UPI handles, which are personal.
 *
 * Mirrors payments.Redact in the Litup Story backend — keep the two key lists in step.
 */
public final class PaymentRedactor {

    public static final String REDACTED = "[redacted]";
    public static final int MAX_BODY = 64 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Replaced by "[redacted]" wherever they appear, at any depth. Keys normalised: lower-case, no _ - or space. */
    private static final Set<String> DROP = Set.of(
            "cardnumber", "card", "pan", "cardpan", "cvv", "cardcvv", "cvc", "cvv2", "csc",
            "cardexpiry", "cardexpirymm", "cardexpiryyy", "expiry", "cardholdername", "cardpin", "pin",
            "track", "track1", "track2", "cardtoken", "instrumentid", "savedcardnumber",
            "secret", "secretkey", "clientsecret", "xclientsecret", "apikey", "password", "authorization",
            "token", "accesstoken", "refreshtoken", "xwebhooksignature", "signature");

    /** Kept only as "***" + last four characters. */
    private static final Set<String> MASK = Set.of(
            "paymentsessionid", "upiid", "vpa", "payervpa", "payeevpa", "bankaccount", "accountnumber", "ifsc");

    private PaymentRedactor() { }

    /**
     * Returns a JSON document safe to store. A body that is not JSON (an HTML error page,
     * say) is kept as {"_text": "..."} so the log still shows what came back. Always valid
     * JSON; null/empty in gives null out.
     */
    public static String redact(String body) {
        if (body == null || body.isBlank()) return null;
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception notJson) {
            String text = body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "...[truncated]" : body;
            ObjectNode o = MAPPER.createObjectNode();
            o.put("_text", text);
            return o.toString();
        }
        if (root == null || root.isMissingNode()) return null;
        JsonNode out = redactNode(root);
        String s = out.toString();
        if (s.length() > MAX_BODY) {
            ObjectNode o = MAPPER.createObjectNode();
            o.put("_truncated", true);
            o.put("_size", s.length());
            return o.toString();
        }
        return s;
    }

    private static JsonNode redactNode(JsonNode n) {
        if (n.isObject()) {
            ObjectNode o = (ObjectNode) n;
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            ObjectNode copy = MAPPER.createObjectNode();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String k = normKey(e.getKey());
                if (DROP.contains(k)) copy.put(e.getKey(), REDACTED);
                else if (MASK.contains(k)) copy.set(e.getKey(), mask(e.getValue()));
                else copy.set(e.getKey(), redactNode(e.getValue()));
            }
            return copy;
        }
        if (n.isArray()) {
            ArrayNode a = MAPPER.createArrayNode();
            for (JsonNode child : n) a.add(redactNode(child));
            return a;
        }
        return n;
    }

    private static String normKey(String k) {
        return k.toLowerCase().replace("_", "").replace("-", "").replace(" ", "");
    }

    private static JsonNode mask(JsonNode v) {
        if (!v.isTextual()) return MAPPER.getNodeFactory().textNode(REDACTED);
        String s = v.asText();
        if (s.length() <= 4) return MAPPER.getNodeFactory().textNode(REDACTED);
        return MAPPER.getNodeFactory().textNode("***" + s.substring(s.length() - 4));
    }
}
