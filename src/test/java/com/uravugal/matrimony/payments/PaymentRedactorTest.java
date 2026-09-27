package com.uravugal.matrimony.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.utils.PaymentRedactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRedactorTest {

    @Test
    void stripsCardholderDataAtAnyDepthAndMasksSessionAndUpi() throws Exception {
        String in = """
            {"order_id":"VVM-702-X-Y","payment_session_id":"session_abcdefghijklmnop1234",
             "customer_details":{"customer_phone":"9876543210","customer_email":"a@b.com"},
             "payment_method":{"card":{"card_number":"4111111111111111","card_cvv":"123","cardHolderName":"X"},
                               "upi":{"upi_id":"selva@okaxis"}},
             "attempts":[{"cvv":"999","cf_payment_id":12345}],
             "Authorization":"Bearer abc","x-client-secret":"cfsk_ma_test_000"}
            """;
        String out = PaymentRedactor.redact(in);
        for (String leaked : new String[]{"4111111111111111", "\"123\"", "\"999\"", "selva@okaxis", "cfsk_ma_test", "Bearer abc", "session_abcdefghijklmnop"}) {
            assertFalse(out.contains(leaked), "leaked " + leaked + " in " + out);
        }
        for (String kept : new String[]{"\"order_id\":\"VVM-702-X-Y\"", "\"customer_phone\":\"9876543210\"", "\"cf_payment_id\":12345", "\"***1234\"", "\"***axis\""}) {
            assertTrue(out.contains(kept), "expected " + kept + " in " + out);
        }
        assertDoesNotThrow(() -> new ObjectMapper().readTree(out), "output must be valid JSON");
    }

    @Test
    void keepsNonJsonAsTextAndHandlesEmpty() {
        assertEquals("{\"_text\":\"<html>502 Bad Gateway</html>\"}", PaymentRedactor.redact("<html>502 Bad Gateway</html>"));
        assertNull(PaymentRedactor.redact(null));
        assertNull(PaymentRedactor.redact("  "));
    }

    @Test
    void capsSize() {
        String big = "{\"blob\":\"" + "x".repeat(100 * 1024) + "\"}";
        String out = PaymentRedactor.redact(big);
        assertTrue(out.length() < 200, "not capped: " + out.length());
        assertTrue(out.contains("\"_truncated\":true"));
    }
}
