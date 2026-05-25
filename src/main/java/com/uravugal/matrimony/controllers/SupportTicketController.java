package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.SupportTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/support-ticket")
@CrossOrigin
public class SupportTicketController {

    @Autowired
    private SupportTicketService supportTicketService;

    @PostMapping("/create/{encodedUserId}")
    public ResponseEntity<ResultResponse> createTicket(
            @PathVariable String encodedUserId,
            @RequestBody Map<String, String> body) {
        Long userId = decodeUserId(encodedUserId);
        String subject = body.getOrDefault("subject", body.getOrDefault("category", "Support Request"));
        String category = body.getOrDefault("category", "OTHER");
        String message = body.get("message");

        if (message == null || message.trim().isEmpty()) {
            ResultResponse resp = new ResultResponse();
            resp.setCode(400);
            resp.setMessage("Message is required");
            resp.setStatus(ResponseStatus.FAILURE);
            return ResponseEntity.badRequest().body(resp);
        }

        return ResponseEntity.ok(supportTicketService.createTicket(userId, subject, category, message));
    }

    @GetMapping("/my/{encodedUserId}")
    public ResponseEntity<ResultResponse> getMyTickets(
            @PathVariable String encodedUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = decodeUserId(encodedUserId);
        return ResponseEntity.ok(supportTicketService.getMyTickets(userId, page, size));
    }

    @GetMapping("/{ticketId}/messages")
    public ResponseEntity<ResultResponse> getTicketMessages(@PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.getTicketMessages(ticketId));
    }

    @PostMapping("/{ticketId}/reply/{encodedUserId}")
    public ResponseEntity<ResultResponse> replyToTicket(
            @PathVariable Long ticketId,
            @PathVariable String encodedUserId,
            @RequestBody Map<String, String> body) {
        Long userId = decodeUserId(encodedUserId);
        String message = body.get("message");

        if (message == null || message.trim().isEmpty()) {
            ResultResponse resp = new ResultResponse();
            resp.setCode(400);
            resp.setMessage("Message is required");
            resp.setStatus(ResponseStatus.FAILURE);
            return ResponseEntity.badRequest().body(resp);
        }

        return ResponseEntity.ok(supportTicketService.replyToTicket(ticketId, userId, message));
    }

    @GetMapping("/active/{encodedUserId}")
    public ResponseEntity<ResultResponse> getActiveTicket(@PathVariable String encodedUserId) {
        Long userId = decodeUserId(encodedUserId);
        return ResponseEntity.ok(supportTicketService.getActiveTicket(userId));
    }

    @GetMapping("/unread-count/{encodedUserId}")
    public ResponseEntity<ResultResponse> getUnreadCount(@PathVariable String encodedUserId) {
        Long userId = decodeUserId(encodedUserId);
        return ResponseEntity.ok(supportTicketService.getUnreadCount(userId));
    }

    private Long decodeUserId(String encoded) {
        return Long.parseLong(new String(Base64.getDecoder().decode(encoded)));
    }
}