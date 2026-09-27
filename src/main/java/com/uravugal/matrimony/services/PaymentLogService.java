package com.uravugal.matrimony.services;

import com.uravugal.matrimony.models.PaymentLog;
import com.uravugal.matrimony.repositories.PaymentLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Writes the payment log, prunes it, and answers the webhook's "who is calling" question.
 *
 * Two rules hold throughout: a log write that fails is reported, never allowed to fail the
 * payment it describes; and requests that failed authentication (bad signature, wrong
 * source) are never written here — the endpoint must not be usable to fill the table.
 */
@Service
public class PaymentLogService {

    private static final Logger log = LoggerFactory.getLogger(PaymentLogService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Value("${payments.log.retention.days:90}") private int retentionDays;
    /** Comma-separated IPs or CIDRs Cashfree webhooks may come from. Empty = no IP check (signature only). */
    @Value("${cashfree.webhook.allowed.ips:}") private String allowedIpsRaw;

    @Autowired private PaymentLogRepository repository;

    private List<IpRule> allowList = Collections.emptyList();

    @PostConstruct
    void init() {
        List<IpRule> rules = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        for (String raw : allowedIpsRaw.split(",")) {
            String e = raw.trim();
            if (e.isEmpty()) continue;
            IpRule r = IpRule.parse(e);
            if (r == null) bad.add(e); else rules.add(r);
        }
        if (!bad.isEmpty()) {
            // Fail loudly: a typo here must not silently open or close the webhook.
            throw new IllegalStateException("cashfree.webhook.allowed.ips has entries that are not an IP or CIDR: " + bad);
        }
        allowList = Collections.unmodifiableList(rules);
        if (allowList.isEmpty()) log.warn("webhook source check OFF (cashfree.webhook.allowed.ips unset) — signature check only");
        else log.info("webhook source check ON: {}", allowedIpsRaw.trim());
        log.info("payment log: every Cashfree exchange -> payment_log, kept {} days", retentionDays);
    }

    // ───────────────────────── writes ─────────────────────────

    /** A new entry stamped with today's date; fill and hand to {@link #record}. */
    public PaymentLog entry(String stage, String direction) {
        PaymentLog e = new PaymentLog();
        e.setStage(stage);
        e.setDirection(direction);
        e.setCreatedBy("system");
        return e;
    }

    /** Never throws. Own transaction, so a caller's rollback cannot erase the evidence. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentLog e) {
        try {
            e.setLogDate(LocalDate.now(IST));
            if (e.getError() != null && e.getError().length() > 512) e.setError(e.getError().substring(0, 512));
            if (e.getUrl() != null && e.getUrl().length() > 512) e.setUrl(e.getUrl().substring(0, 512));
            if (e.getOutcome() != null && e.getOutcome().length() > 64) e.setOutcome(e.getOutcome().substring(0, 64));
            repository.save(e);
        } catch (Exception ex) {
            log.warn("PAYLOG could not write {}/{} for {}: {}", e.getStage(), e.getDirection(), e.getOrderId(), ex.getMessage());
        }
    }

    // ───────────────────────── reads ─────────────────────────

    public List<PaymentLog> forOrder(String orderId) {
        return repository.findByOrderIdOrderByIdAsc(orderId);
    }

    public List<PaymentLog> forDay(LocalDate day, int limit) {
        int n = (limit <= 0 || limit > 2000) ? 500 : limit;
        return repository.findByLogDateOrderByIdDesc(day, PageRequest.of(0, n));
    }

    // ───────────────────────── retention ─────────────────────────

    /** Daily at 00:30 IST — after the subscription jobs at 00:00 and 00:15. */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    public void prune() {
        if (retentionDays <= 0) return;
        try {
            LocalDate cutoff = LocalDate.now(IST).minusDays(retentionDays);
            int n = repository.deleteOlderThan(cutoff);
            if (n > 0) log.info("pruned {} payment log rows older than {} days", n, retentionDays);
        } catch (Exception ex) {
            log.warn("prune payment log: {}", ex.getMessage());
        }
    }

    // ───────────────────────── webhook source ─────────────────────────

    public boolean ipCheckEnabled() { return !allowList.isEmpty(); }

    public boolean allows(String ip) {
        InetAddress a = IpRule.address(ip);
        if (a == null) return false;
        for (IpRule r : allowList) if (r.matches(a)) return true;
        return false;
    }

    /**
     * The address a request really came from.
     *
     * Behind nginx (and Cloudflare in front of it) the remote address is always the proxy,
     * and the real address is in CF-Connecting-IP or X-Forwarded-For. Those headers are only
     * believed when the direct peer is a loopback or private address — i.e. our own reverse
     * proxy. A request that reaches the port directly from the internet cannot spoof its way
     * past the allow-list by adding a header.
     */
    public static String clientIp(HttpServletRequest r) {
        String peer = r.getRemoteAddr();
        InetAddress p = IpRule.address(peer);
        boolean fromProxy = p != null && (p.isLoopbackAddress() || p.isSiteLocalAddress() || isUniqueLocalV6(p));
        if (fromProxy) {
            String v = r.getHeader("CF-Connecting-IP");
            if (v != null && !v.isBlank()) return v.trim();
            v = r.getHeader("X-Forwarded-For");
            if (v != null && !v.isBlank()) {
                String first = v.split(",")[0].trim();
                if (!first.isEmpty()) return first;
            }
            v = r.getHeader("X-Real-IP");
            if (v != null && !v.isBlank()) return v.trim();
        }
        return peer;
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    /** A single address or a CIDR block, IPv4 or IPv6. */
    public static final class IpRule {
        private final byte[] net;
        private final int bits;

        private IpRule(byte[] net, int bits) { this.net = net; this.bits = bits; }

        public static IpRule parse(String s) {
            String addr = s;
            int bits = -1;
            int slash = s.indexOf('/');
            if (slash >= 0) {
                addr = s.substring(0, slash);
                try { bits = Integer.parseInt(s.substring(slash + 1)); } catch (NumberFormatException e) { return null; }
            }
            InetAddress a = address(addr);
            if (a == null) return null;
            int len = a.getAddress().length * 8;
            if (bits < 0) bits = len;
            if (bits > len) return null;
            return new IpRule(a.getAddress(), bits);
        }

        public boolean matches(InetAddress a) {
            byte[] b = a.getAddress();
            if (b.length != net.length) return false;
            int full = bits / 8, rem = bits % 8;
            for (int i = 0; i < full; i++) if (b[i] != net[i]) return false;
            if (rem == 0) return true;
            int mask = 0xff << (8 - rem);
            return (b[full] & mask) == (net[full] & mask);
        }

        /** Parses a literal only — never a hostname, so no DNS lookup can be triggered by input. */
        static InetAddress address(String s) {
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty() || !(t.matches("[0-9.]+") || t.matches("[0-9a-fA-F:.]+"))) return null;
            try { return InetAddress.getByName(t); } catch (UnknownHostException e) { return null; }
        }
    }
}
