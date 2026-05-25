package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.SupportTicket;
import com.uravugal.matrimony.models.TicketMessage;
import com.uravugal.matrimony.repositories.SupportTicketRepository;
import com.uravugal.matrimony.repositories.TicketMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SupportTicketService {

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    public ResultResponse createTicket(Long userId, String subject, String category, String message) {
        ResultResponse resp = new ResultResponse();
        try {
            SupportTicket ticket = new SupportTicket();
            ticket.setUserId(userId);
            ticket.setSubject(subject);
            ticket.setCategory(category);
            ticket.setPriority("MEDIUM");
            ticket.setStatus("OPEN");
            ticket.setCreatedAt(LocalDateTime.now());
            ticket.setUpdatedAt(LocalDateTime.now());
            SupportTicket saved = supportTicketRepository.save(ticket);

            TicketMessage msg = new TicketMessage();
            msg.setTicketId(saved.getId());
            msg.setSenderId(userId);
            msg.setSenderType("USER");
            msg.setMessage(message);
            msg.setCreatedAt(LocalDateTime.now());
            ticketMessageRepository.save(msg);

            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(saved.getId());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ticket", saved);
            data.put("messages", messages);

            resp.setCode(200);
            resp.setMessage("Ticket created successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error creating ticket: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getMyTickets(Long userId, int page, int size) {
        ResultResponse resp = new ResultResponse();
        try {
            var tickets = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
            resp.setCode(200);
            resp.setMessage("Tickets fetched");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(tickets.getContent());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getTicketMessages(Long ticketId) {
        ResultResponse resp = new ResultResponse();
        try {
            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
            resp.setCode(200);
            resp.setMessage("Messages fetched");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(messages);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse replyToTicket(Long ticketId, Long userId, String message) {
        ResultResponse resp = new ResultResponse();
        try {
            Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(ticketId);
            if (ticketOpt.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("Ticket not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            SupportTicket ticket = ticketOpt.get();
            if (!ticket.getUserId().equals(userId)) {
                resp.setCode(403);
                resp.setMessage("Unauthorized");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            TicketMessage msg = new TicketMessage();
            msg.setTicketId(ticketId);
            msg.setSenderId(userId);
            msg.setSenderType("USER");
            msg.setMessage(message);
            msg.setCreatedAt(LocalDateTime.now());
            ticketMessageRepository.save(msg);

            ticket.setUpdatedAt(LocalDateTime.now());
            supportTicketRepository.save(ticket);

            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ticket", ticket);
            data.put("messages", messages);

            resp.setCode(200);
            resp.setMessage("Reply sent");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getActiveTicket(Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            var tickets = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1));
            List<SupportTicket> content = tickets.getContent();

            if (content.isEmpty() || "CLOSED".equals(content.get(0).getStatus())) {
                resp.setCode(200);
                resp.setMessage("No active ticket");
                resp.setStatus(ResponseStatus.SUCCESS);
                resp.setData(null);
                return resp;
            }

            SupportTicket ticket = content.get(0);
            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ticket", ticket);
            data.put("messages", messages);

            resp.setCode(200);
            resp.setMessage("Active ticket found");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getUnreadCount(Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            var tickets = supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50));
            int unread = 0;
            for (SupportTicket ticket : tickets.getContent()) {
                if ("CLOSED".equals(ticket.getStatus())) continue;
                List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
                if (!messages.isEmpty()) {
                    TicketMessage last = messages.get(messages.size() - 1);
                    if ("ADMIN".equals(last.getSenderType())) {
                        unread++;
                    }
                }
            }
            resp.setCode(200);
            resp.setMessage("Unread count");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(unread);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
