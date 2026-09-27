package com.uravugal.matrimony.services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.uravugal.matrimony.models.*;
import com.uravugal.matrimony.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Payment RECEIPTS — issued once per paid order and e-mailed to the member as a PDF.
 *
 * Why "receipt" and not "invoice": the business is not registered under GST. An unregistered
 * supplier must not issue a document titled "Tax Invoice", must not charge or show GST, and
 * must not print HSN/SAC tax lines. So the document is a Payment Receipt: business name and
 * contact, receipt number, member, what was bought, the amount paid, how it was paid, and the
 * gateway reference — with a plain statement that no GST was charged. That is also what a
 * member needs to prove they paid. If a GSTIN is obtained later, set invoice.business.gstin
 * and the header prints it; the tax lines would then be a separate change.
 *

 * ⚠ ZONE: this server's JVM default zone is UTC while its system clock already holds IST wall
 * time, so LocalDateTime.now(IST) double-shifts by 5h30m (a receipt issued 22 Sep 21:53 was
 * stamped 23 Sep 03:23). Timestamps here use LocalDateTime.now(), which renders the same wall
 * time as GenericEntity.createdAt. The "Payment Date" line prints PaymentOrder.paymentTime,
 * which is written with the same double shift upstream and will only be right once the
 * payment_orders timezone bug is fixed.
 *
 * Trigger: CashfreeService.fulfil() — the ONE place a payment is confirmed, for all three
 * paths (webhook, status-poll verify, nightly reconcile). fulfil() wins exactly once per
 * order, and paymentOrderId is UNIQUE here, so a duplicate webhook can never produce a second
 * receipt. Failure here never fails the payment: everything is best-effort and logged.
 */
@Slf4j
@Service
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private static final Color MAROON = new Color(0x42, 0x00, 0x01);
    private static final Color GOLD = new Color(0xF6, 0xB7, 0x33);
    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color SLATE = new Color(0x64, 0x74, 0x8b);
    private static final Color RULE = new Color(0xe5, 0xe7, 0xeb);
    private static final Color TINT = new Color(0xfd, 0xf6, 0xe7);

    private final PaymentInvoiceRepository invoiceRepository;
    private final PaymentOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionsRepository subscriptionsRepository;
    private final EmailService emailService;

    @Value("${invoice.business.name:Vaibhav Vivaaha Matrimony}") private String bizName;
    @Value("${invoice.business.address:}") private String bizAddress;
    @Value("${invoice.business.email:support@vaibhavvivaahamatrimony.com}") private String bizEmail;
    @Value("${invoice.business.phone:+91 79045 47565}") private String bizPhone;
    @Value("${invoice.business.website:www.vaibhavvivaahamatrimony.com}") private String bizWebsite;
    @Value("${invoice.business.gstin:}") private String bizGstin;
    @Value("${invoice.number.prefix:VVM-RCPT-}") private String numberPrefix;

    public InvoiceService(PaymentInvoiceRepository invoiceRepository, PaymentOrderRepository orderRepository,
                          UserRepository userRepository, SubscriptionPlanRepository planRepository,
                          UserSubscriptionsRepository subscriptionsRepository, EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionsRepository = subscriptionsRepository;
        this.emailService = emailService;
    }

    // ───────────────────────────── issue + send ─────────────────────────────

    /**
     * Called from fulfil(). Issues the receipt (idempotent — an existing row is reused) and
     * e-mails it. Never throws.
     */
    public void issueAndEmail(String orderId) {
        try {
            PaymentInvoice inv = issue(orderId);
            if (inv != null) email(inv);
        } catch (Exception e) {
            log.error("Receipt for order {} could not be issued: {}", orderId, e.getMessage(), e);
        }
    }

    /** Admin / support: rebuild the same PDF and send it again (to the member's current e-mail). */
    public boolean resend(String orderId) {
        PaymentInvoice inv = invoiceRepository.findByOrderId(orderId).orElse(null);
        if (inv == null) inv = issue(orderId);
        if (inv == null) return false;
        return email(inv);
    }

    /** The PDF bytes for a paid order — for a download endpoint. Null if the order is not paid. */
    public byte[] pdfFor(String orderId) {
        PaymentInvoice inv = invoiceRepository.findByOrderId(orderId).orElse(null);
        if (inv == null) inv = issue(orderId);
        if (inv == null) return null;
        PaymentOrder order = orderRepository.findByOrderId(orderId).orElse(null);
        UserEntity user = order == null ? null : userRepository.findById(order.getUserId()).orElse(null);
        return order == null || user == null ? null : buildPdf(inv, order, user);
    }

    /** Creates the receipt row for a PAID order, or returns the existing one. Null if not paid. */
    public PaymentInvoice issue(String orderId) {
        PaymentInvoice existing = invoiceRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) return existing;

        PaymentOrder order = orderRepository.findByOrderId(orderId).orElse(null);
        if (order == null || !PaymentOrder.STATUS_PAID.equals(order.getOrderStatus())) {
            log.warn("Receipt requested for order {} which is not PAID", orderId);
            return null;
        }

        PaymentInvoice inv = new PaymentInvoice();
        inv.setPaymentOrderId(order.getId());
        inv.setOrderId(order.getOrderId());
        inv.setUserId(order.getUserId());
        inv.setAmount(order.getAmount());
        inv.setPaymentMode(modeLabel(order.getPaymentGroup()));
        inv.setCfPaymentId(order.getCfPaymentId());
        inv.setIssuedAt(LocalDateTime.now()); // see ZONE note
        inv.setEmailStatus(null);

        if (PaymentOrder.PURPOSE_BOOST.equals(order.getPurpose())) {
            inv.setDescription("Profile Boost credit (1) - 24-hour visibility boost");
            inv.setValidity(null);
        } else {
            SubscriptionPlan plan = order.getPlanId() == null ? null : planRepository.findById(order.getPlanId()).orElse(null);
            String title = plan == null ? "Membership" : plan.getTitle() + " Membership";
            inv.setDescription(title + " - " + bizName);
            UserSubscriptions sub = order.getSubscriptionId() == null ? null
                    : subscriptionsRepository.findById(order.getSubscriptionId()).orElse(null);
            inv.setValidity(validityLabel(sub, plan));
        }

        // Two-step save: the number is derived from the row id so it is unique and monotonic
        // without a separate sequence table. The UNIQUE(paymentOrderId) constraint is the
        // real guard — a concurrent second issue() for the same order fails on save.
        try {
            inv = invoiceRepository.save(inv);
        } catch (Exception dup) {
            return invoiceRepository.findByOrderId(orderId).orElse(null);
        }
        inv.setInvoiceNo(numberPrefix + String.format("%06d", inv.getId()));
        return invoiceRepository.save(inv);
    }

    /** Builds the PDF and sends it. Records the outcome on the row. */
    public boolean email(PaymentInvoice inv) {
        PaymentOrder order = orderRepository.findByOrderId(inv.getOrderId()).orElse(null);
        UserEntity user = order == null ? null : userRepository.findById(order.getUserId()).orElse(null);
        if (order == null || user == null) return false;

        inv.setSendAttempts((inv.getSendAttempts() == null ? 0 : inv.getSendAttempts()) + 1);
        String to = user.getEmail();
        if (to == null || to.isBlank() || !to.contains("@")) {
            inv.setEmailStatus(PaymentInvoice.EMAIL_NO_ADDRESS);
            invoiceRepository.save(inv);
            log.warn("Receipt {} not e-mailed: user {} has no e-mail address", inv.getInvoiceNo(), user.getUserId());
            return false;
        }

        byte[] pdf = buildPdf(inv, order, user);
        String subject = "Payment receipt " + inv.getInvoiceNo() + " - " + bizName;
        boolean ok = emailService.sendHtmlWithPdf(to, fullName(user), subject, emailBody(inv, user), pdf,
                inv.getInvoiceNo() + ".pdf", "Receipt " + inv.getInvoiceNo());

        inv.setEmailedTo(to);
        inv.setEmailedAt(LocalDateTime.now()); // see ZONE note
        inv.setEmailStatus(ok ? PaymentInvoice.EMAIL_SENT : PaymentInvoice.EMAIL_FAILED);
        invoiceRepository.save(inv);
        return ok;
    }

    // ───────────────────────────── the PDF ─────────────────────────────

    private byte[] buildPdf(PaymentInvoice inv, PaymentOrder order, UserEntity user) {
        Document doc = new Document(PageSize.A4, 40, 40, 32, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Payment Receipt " + inv.getInvoiceNo());
            doc.addAuthor(bizName);
            doc.open();

            Font brand = new Font(Font.HELVETICA, 17, Font.BOLD, GOLD);
            Font brandSub = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, new Color(0xf3, 0xe4, 0xc4));
            Font h1 = new Font(Font.HELVETICA, 16, Font.BOLD, MAROON);
            Font label = new Font(Font.HELVETICA, 8, Font.BOLD, SLATE);
            Font value = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, INK);
            Font valueB = new Font(Font.HELVETICA, 9.5f, Font.BOLD, INK);
            Font small = new Font(Font.HELVETICA, 8, Font.NORMAL, SLATE);
            Font th = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
            Font total = new Font(Font.HELVETICA, 13, Font.BOLD, MAROON);
            Font paid = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);

            // Header band
            PdfPTable head = new PdfPTable(new float[] { 3f, 1.2f });
            head.setWidthPercentage(100);
            PdfPCell hl = cell(null, 0);
            hl.setBackgroundColor(MAROON); hl.setPadding(14);
            Paragraph bp = new Paragraph();
            bp.add(new Chunk(bizName, brand));
            bp.add(Chunk.NEWLINE);
            bp.add(new Chunk(bizWebsite, brandSub));
            bp.add(Chunk.NEWLINE);
            bp.add(new Chunk(bizEmail + "  |  " + bizPhone, brandSub));
            if (!blank(bizAddress)) { bp.add(Chunk.NEWLINE); bp.add(new Chunk(bizAddress, brandSub)); }
            if (!blank(bizGstin)) { bp.add(Chunk.NEWLINE); bp.add(new Chunk("GSTIN: " + bizGstin, brandSub)); }
            hl.addElement(bp);
            head.addCell(hl);
            PdfPCell hr = cell(null, 0);
            hr.setBackgroundColor(MAROON); hr.setPadding(14);
            hr.setHorizontalAlignment(Element.ALIGN_RIGHT); hr.setVerticalAlignment(Element.ALIGN_MIDDLE);
            PdfPTable badge = new PdfPTable(1); badge.setWidthPercentage(60); badge.setHorizontalAlignment(Element.ALIGN_RIGHT);
            PdfPCell bc = new PdfPCell(new Phrase("PAID", paid));
            bc.setBackgroundColor(new Color(0x16, 0xa3, 0x4a)); bc.setBorder(0); bc.setPadding(6);
            bc.setHorizontalAlignment(Element.ALIGN_CENTER);
            badge.addCell(bc);
            hr.addElement(badge);
            head.addCell(hr);
            doc.add(head);

            doc.add(spacer(10));
            Paragraph title = new Paragraph("PAYMENT RECEIPT", h1);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(spacer(10));

            // Meta grid
            PdfPTable meta = new PdfPTable(new float[] { 1f, 1f });
            meta.setWidthPercentage(100);
            meta.addCell(kv("Receipt No", inv.getInvoiceNo(), label, valueB));
            meta.addCell(kv("Receipt Date", inv.getIssuedAt().format(D), label, value));
            meta.addCell(kv("Order ID", order.getOrderId(), label, value));
            meta.addCell(kv("Payment Reference", nz(inv.getCfPaymentId(), "-"), label, value));
            meta.addCell(kv("Payment Mode", nz(inv.getPaymentMode(), "Online"), label, value));
            meta.addCell(kv("Payment Date", order.getPaymentTime() != null ? order.getPaymentTime().format(DT) + " IST"
                    : inv.getIssuedAt().format(DT) + " IST", label, value));
            doc.add(meta);
            doc.add(spacer(12));

            // Parties
            PdfPTable parties = new PdfPTable(new float[] { 1f, 1f });
            parties.setWidthPercentage(100);
            parties.addCell(block("RECEIVED FROM", new String[] {
                    fullName(user),
                    "Member ID: " + nz(user.getMemberId(), "-"),
                    "Email: " + nz(user.getEmail(), "-"),
                    "Mobile: " + nz(user.getMobile(), "-") }, label, valueB, value));
            parties.addCell(block("RECEIVED BY", new String[] {
                    bizName,
                    blank(bizAddress) ? bizWebsite : bizAddress,
                    bizEmail,
                    bizPhone }, label, valueB, value));
            doc.add(parties);
            doc.add(spacer(14));

            // Line item
            PdfPTable items = new PdfPTable(new float[] { 0.5f, 3.6f, 1.6f, 0.6f, 1.3f });
            items.setWidthPercentage(100);
            for (String h : new String[] { "#", "DESCRIPTION", "VALIDITY", "QTY", "AMOUNT (INR)" }) {
                PdfPCell c = new PdfPCell(new Phrase(h, th));
                c.setBackgroundColor(MAROON); c.setPadding(7); c.setBorder(0);
                if (h.startsWith("AMOUNT") || h.equals("QTY")) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
                items.addCell(c);
            }
            items.addCell(td("1", value, Element.ALIGN_LEFT));
            items.addCell(td(inv.getDescription(), value, Element.ALIGN_LEFT));
            items.addCell(td(nz(inv.getValidity(), "-"), value, Element.ALIGN_LEFT));
            items.addCell(td("1", value, Element.ALIGN_RIGHT));
            items.addCell(td(money(inv.getAmount()), valueB, Element.ALIGN_RIGHT));
            doc.add(items);
            doc.add(spacer(8));

            // Totals
            PdfPTable totals = new PdfPTable(new float[] { 3f, 1.6f, 1.3f });
            totals.setWidthPercentage(100);
            totals.addCell(empty());
            totals.addCell(td("Amount Paid", label, Element.ALIGN_RIGHT));
            totals.addCell(td("Rs. " + money(inv.getAmount()), total, Element.ALIGN_RIGHT));
            totals.addCell(empty());
            PdfPCell gst = td("GST", label, Element.ALIGN_RIGHT); totals.addCell(gst);
            totals.addCell(td(blank(bizGstin) ? "Not applicable" : "See tax lines", value, Element.ALIGN_RIGHT));
            doc.add(totals);
            Paragraph words = new Paragraph("Amount in words: Rupees " + inWords(inv.getAmount()) + " only", small);
            words.setAlignment(Element.ALIGN_RIGHT);
            doc.add(words);
            doc.add(spacer(14));

            // Settlement
            PdfPTable settle = new PdfPTable(new float[] { 3f, 1.6f });
            settle.setWidthPercentage(100);
            PdfPCell st = new PdfPCell(new Phrase("PAYMENT SETTLEMENT", th));
            st.setColspan(2); st.setBackgroundColor(MAROON); st.setPadding(7); st.setBorder(0);
            settle.addCell(st);
            settle.addCell(tdTint("Paid online via " + nz(inv.getPaymentMode(), "online payment") + " (Cashfree Payments)", value));
            settle.addCell(tdTintR("Rs. " + money(inv.getAmount()), valueB));
            settle.addCell(tdTint("Total received", valueB));
            settle.addCell(tdTintR("Rs. " + money(inv.getAmount()), valueB));
            doc.add(settle);
            doc.add(spacer(12));

            // Terms
            Paragraph termsH = new Paragraph("TERMS & NOTES", label);
            doc.add(termsH);
            doc.add(spacer(3));
            String[] terms = {
                "This is a receipt for a payment received, not a tax invoice. " + bizName
                        + (blank(bizGstin) ? " is not registered under GST; no GST has been charged or collected on this payment."
                                           : " is registered under GST (GSTIN " + bizGstin + ")."),
                "Membership and add-on services are digital services provided through the " + bizName
                        + " app. They begin on activation, are linked to the member account above, and are non-transferable.",
                "Cancellations and refunds are governed by the Terms of Use and Refund Policy published at "
                        + bizWebsite + ". Payments are processed by Cashfree Payments; no card or UPI details are stored by us.",
                "For queries about this payment write to " + bizEmail + " or call " + bizPhone
                        + " quoting the Receipt No. and Order ID above.",
                "This is a computer-generated document and does not require a signature."
            };
            for (String t : terms) {
                Paragraph p = new Paragraph("- " + t, small);
                p.setSpacingAfter(1.5f); p.setLeading(10.5f);
                doc.add(p);
            }

            doc.add(spacer(6));
            PdfPTable foot = new PdfPTable(1); foot.setWidthPercentage(100);
            PdfPCell fc = new PdfPCell(new Phrase("Thank you for choosing " + bizName + ".", small));
            fc.setBorder(Rectangle.TOP); fc.setBorderColor(GOLD); fc.setBorderWidth(1.5f);
            fc.setPaddingTop(8); fc.setHorizontalAlignment(Element.ALIGN_CENTER);
            foot.addCell(fc);
            doc.add(foot);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            if (doc.isOpen()) doc.close();
            throw new IllegalStateException("PDF build failed: " + e.getMessage(), e);
        }
    }

    private String emailBody(PaymentInvoice inv, UserEntity user) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;"
                + "border:1px solid #eee;border-radius:12px;overflow:hidden\">"
                + "<div style=\"background:#420001;padding:20px 24px\">"
                + "<span style=\"color:#F6B733;font-size:18px;font-weight:bold\">" + esc(bizName) + "</span></div>"
                + "<div style=\"padding:24px\">"
                + "<p style=\"font-size:15px;color:#222\">Dear " + esc(firstName(user)) + ",</p>"
                + "<p style=\"font-size:14px;color:#444;line-height:1.6\">We have received your payment of "
                + "<b style=\"color:#420001\">Rs. " + money(inv.getAmount()) + "</b> for "
                + "<b>" + esc(inv.getDescription()) + "</b>."
                + (inv.getValidity() != null ? " Validity: <b>" + esc(inv.getValidity()) + "</b>." : "") + "</p>"
                + "<table style=\"font-size:13px;color:#444;border-collapse:collapse;margin:12px 0\">"
                + "<tr><td style=\"padding:4px 12px 4px 0;color:#888\">Receipt No</td><td><b>" + esc(inv.getInvoiceNo()) + "</b></td></tr>"
                + "<tr><td style=\"padding:4px 12px 4px 0;color:#888\">Order ID</td><td>" + esc(inv.getOrderId()) + "</td></tr>"
                + "<tr><td style=\"padding:4px 12px 4px 0;color:#888\">Payment mode</td><td>" + esc(nz(inv.getPaymentMode(), "Online")) + "</td></tr>"
                + "</table>"
                + "<p style=\"font-size:14px;color:#444;line-height:1.6\">Your receipt is attached as a PDF. "
                + "Please keep it for your records.</p>"
                + "<p style=\"font-size:12px;color:#888;line-height:1.5\">This is a receipt for a payment received, not a tax invoice."
                + (blank(bizGstin) ? " No GST has been charged." : "") + "</p>"
                + "<p style=\"font-size:13px;color:#888;margin-top:24px\">Warm regards,<br/>Team " + esc(bizName) + "</p>"
                + "</div></div>";
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static String validityLabel(UserSubscriptions sub, SubscriptionPlan plan) {
        if (sub == null || sub.getStartDate() == null) return null;
        boolean lifetime = sub.getEndDate() == null
                || (plan != null && plan.getDurationDays() != null && plan.getDurationDays() >= 9999)
                || sub.getEndDate().isAfter(LocalDate.now().plusYears(20));
        if (lifetime) return "From " + sub.getStartDate().format(D) + " until marriage";
        return sub.getStartDate().format(D) + " to " + sub.getEndDate().format(D);
    }

    static String modeLabel(String group) {
        if (group == null) return "Online";
        switch (group.toLowerCase(Locale.ROOT)) {
            case "upi": return "UPI";
            case "credit_card": return "Credit Card";
            case "debit_card": return "Debit Card";
            case "net_banking": return "Net Banking";
            case "wallet": return "Wallet";
            case "app": return "Wallet";
            default: return group.replace('_', ' ');
        }
    }

    static String money(BigDecimal amt) {
        if (amt == null) return "0.00";
        BigDecimal a = amt.setScale(2, RoundingMode.HALF_UP);
        long rupees = a.longValue();
        String paise = a.remainder(BigDecimal.ONE).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
        if (paise.length() < 2) paise = "0" + paise;
        return indianGroup(rupees) + "." + paise;
    }

    /** 1234567 → 12,34,567 (Indian grouping). */
    static String indianGroup(long n) {
        String s = Long.toString(Math.abs(n));
        if (s.length() <= 3) return (n < 0 ? "-" : "") + s;
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        while (rest.length() > 2) {
            sb.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        sb.insert(0, rest);
        return (n < 0 ? "-" : "") + sb + "," + last3;
    }

    private static final String[] ONES = { "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen" };
    private static final String[] TENS = { "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety" };

    /** Indian numbering: crore / lakh / thousand / hundred. Paise appended when non-zero. */
    static String inWords(BigDecimal amt) {
        if (amt == null) return "Zero";
        BigDecimal a = amt.setScale(2, RoundingMode.HALF_UP);
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String w = rupees == 0 ? "Zero" : words(rupees).trim();
        if (paise > 0) w += " and " + words(paise).trim() + " Paise";
        return w;
    }

    private static String words(long n) {
        if (n == 0) return "";
        if (n < 20) return ONES[(int) n] + " ";
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 != 0 ? " " + ONES[(int) (n % 10)] : "") + " ";
        if (n < 1000) return ONES[(int) (n / 100)] + " Hundred " + words(n % 100);
        if (n < 100000) return words(n / 1000) + "Thousand " + words(n % 1000);
        if (n < 10000000) return words(n / 100000) + "Lakh " + words(n % 100000);
        return words(n / 10000000) + "Crore " + words(n % 10000000);
    }

    private static String fullName(UserEntity u) {
        String f = nz(u.getFirstName(), ""), l = nz(u.getLastName(), "");
        String n = (f + " " + l).trim();
        return n.isEmpty() ? "Member" : n;
    }
    private static String firstName(UserEntity u) { return blank(u.getFirstName()) ? "Member" : u.getFirstName(); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String nz(String s, String d) { return blank(s) ? d : s; }
    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Paragraph spacer(float pt) { Paragraph p = new Paragraph(" "); p.setLeading(pt); return p; }
    private static PdfPCell cell(Phrase p, float pad) { PdfPCell c = p == null ? new PdfPCell() : new PdfPCell(p); c.setBorder(0); c.setPadding(pad); return c; }
    private static PdfPCell empty() { PdfPCell c = new PdfPCell(new Phrase("")); c.setBorder(0); return c; }
    private static PdfPCell kv(String k, String v, Font kf, Font vf) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(RULE); c.setPadding(6);
        Paragraph p = new Paragraph();
        p.add(new Chunk(k.toUpperCase(Locale.ROOT) + "\n", kf));
        p.add(new Chunk(nz(v, "-"), vf));
        c.addElement(p);
        return c;
    }
    private static PdfPCell block(String heading, String[] lines, Font hf, Font firstF, Font f) {
        PdfPCell c = new PdfPCell();
        c.setBorder(0); c.setPadding(6);
        Paragraph p = new Paragraph();
        p.add(new Chunk(heading + "\n", hf));
        for (int i = 0; i < lines.length; i++) p.add(new Chunk(lines[i] + "\n", i == 0 ? firstF : f));
        p.setLeading(12.5f);
        c.addElement(p);
        return c;
    }
    private static PdfPCell td(String s, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(RULE); c.setPadding(7);
        c.setHorizontalAlignment(align);
        return c;
    }
    private static PdfPCell tdTint(String s, Font f) { PdfPCell c = td(s, f, Element.ALIGN_LEFT); c.setBackgroundColor(TINT); return c; }
    private static PdfPCell tdTintR(String s, Font f) { PdfPCell c = td(s, f, Element.ALIGN_RIGHT); c.setBackgroundColor(TINT); return c; }
}
