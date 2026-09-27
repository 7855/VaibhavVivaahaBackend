package com.uravugal.matrimony.payments;

import com.uravugal.matrimony.services.CashfreeService;
import com.uravugal.matrimony.services.PaymentLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Security validation for the webhook's source check — no Spring context, no database. */
class WebhookSourceTest {

    private static HttpServletRequest req(String remote, String... headers) {
        HttpServletRequest r = Mockito.mock(HttpServletRequest.class);
        when(r.getRemoteAddr()).thenReturn(remote);
        for (int i = 0; i + 1 < headers.length; i += 2) when(r.getHeader(headers[i])).thenReturn(headers[i + 1]);
        return r;
    }

    @Test
    void proxyHeadersAreTrustedOnlyFromLocalPeers() {
        // Direct from the internet: headers ignored.
        assertEquals("203.0.113.9", PaymentLogService.clientIp(req("203.0.113.9",
                "CF-Connecting-IP", "52.66.101.190", "X-Forwarded-For", "52.66.101.190")));
        // Via nginx on the same box: Cloudflare header wins.
        assertEquals("52.66.101.190", PaymentLogService.clientIp(req("127.0.0.1",
                "CF-Connecting-IP", "52.66.101.190", "X-Forwarded-For", "1.2.3.4, 10.0.0.1")));
        // Via nginx: first X-Forwarded-For hop.
        assertEquals("52.66.101.190", PaymentLogService.clientIp(req("127.0.0.1",
                "X-Forwarded-For", "52.66.101.190, 10.0.0.1")));
        // Via a private-network proxy: X-Real-IP.
        assertEquals("52.66.101.190", PaymentLogService.clientIp(req("10.1.2.3", "X-Real-IP", "52.66.101.190")));
        // Local, no headers.
        assertEquals("0:0:0:0:0:0:0:1", PaymentLogService.clientIp(req("0:0:0:0:0:0:0:1")));
    }

    @Test
    void allowListParsesIpsAndCidrsAndRefusesGarbage() throws Exception {
        assertNotNull(PaymentLogService.IpRule.parse("52.66.101.190"));
        assertNotNull(PaymentLogService.IpRule.parse("3.109.0.0/16"));
        assertNotNull(PaymentLogService.IpRule.parse("2406:da1a::/32"));
        assertNull(PaymentLogService.IpRule.parse("not-an-ip"));
        assertNull(PaymentLogService.IpRule.parse("300.1.1.1"));
        assertNull(PaymentLogService.IpRule.parse("example.com"), "hostnames must never resolve");
        assertNull(PaymentLogService.IpRule.parse("10.0.0.0/33"));

        PaymentLogService.IpRule cidr = PaymentLogService.IpRule.parse("3.109.0.0/16");
        assertTrue(cidr.matches(InetAddress.getByName("3.109.102.144")));
        assertFalse(cidr.matches(InetAddress.getByName("3.110.0.1")));
        PaymentLogService.IpRule single = PaymentLogService.IpRule.parse("52.66.101.190");
        assertTrue(single.matches(InetAddress.getByName("52.66.101.190")));
        assertFalse(single.matches(InetAddress.getByName("52.66.101.191")));
    }

    @Test
    void onlyOurPrefixIsOurs() {
        assertTrue(CashfreeService.isOurOrder("VVM-702-LX3K9A2F-7H2KQ9ZP"));
        assertFalse(CashfreeService.isOurOrder("LS-2026-0023-1"), "a Litup Story order must be ignored");
        assertFalse(CashfreeService.isOurOrder(null));
        assertFalse(CashfreeService.isOurOrder(""));
    }
}
