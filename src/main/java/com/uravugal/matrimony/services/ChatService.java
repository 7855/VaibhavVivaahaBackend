package com.uravugal.matrimony.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// import com.uravugal.matrimony.dtos.ChatRequest;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.ChatStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.ChatEntity;
import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.config.UserWebSocketHandler;
import com.uravugal.matrimony.repositories.ChatRepository;
import com.uravugal.matrimony.repositories.ConversationRepository;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.BlockedUserRepository blockedUserRepository;

    public ResultResponse sendChatMessage(ChatEntity request) {
        ResultResponse response = new ResultResponse();
        try {
            // Block gate. Two very different cases:
            //  - the SENDER blocked the other member → refuse (they must unblock first);
            //  - the OTHER member blocked the sender → accept the message but keep it hidden
            //    from the recipient forever (WhatsApp behaviour: single grey tick, never
            //    delivered, and the blocked person is never told). Telling them outright
            //    ("this user has blocked you") was the client's complaint.
            boolean hiddenFromReceiver = false;
            if (request.getSenderId() != null && request.getConversationId() != null) {
                Conversation convo = conversationRepository.findById(request.getConversationId()).orElse(null);
                if (convo != null) {
                    Long otherUserId = convo.getUserOne().equals(request.getSenderId()) ? convo.getUserTwo() : convo.getUserOne();
                    com.uravugal.matrimony.models.BlockedUser block = blockedUserRepository
                            .findByUsersEitherDirection(request.getSenderId(), otherUserId);
                    if (block != null) {
                        // Client decision 2026-09-19 (reverses the silent WhatsApp-style block of
                        // 09-17): BOTH sides are told, with different words. The blocker gets
                        // USER_BLOCKED ("unblock to send" + an Unblock button in the app); the
                        // blocked member gets BLOCKED_BY_USER. Nothing is stored either way.
                        response.setCode(403);
                        response.setStatus(ResponseStatus.FAILURE);
                        response.setMessage(request.getSenderId().equals(block.getBlockedByUserId())
                                ? "USER_BLOCKED" : "BLOCKED_BY_USER");
                        return response;
                    }

                    // Connection gate: only an ACCEPTED conversation may carry messages. The app
                    // already refuses on PENDING; this also covers DISCONNECTED (a member removed
                    // the connection) so an old screen or a stale chat list cannot keep talking.
                    if (convo.getStatus() != ChatStatus.ACCEPTED) {
                        response.setCode(403);
                        response.setStatus(ResponseStatus.FAILURE);
                        response.setMessage(convo.getStatus() == ChatStatus.DISCONNECTED ? "NOT_CONNECTED" : "REQUEST_PENDING");
                        return response;
                    }
                }
            }

            // Plan gate: check MESSAGE feature
            // findTopByUserIdOrderByCreatedAtDesc picks whichever subscription row was CREATED
            // most recently, regardless of its status — a user whose subscription was renewed
            // via an UPDATE to an older row (keeping its original createdAt) while a newer,
            // since-expired row exists (e.g. from the self-healing Free-plan fallback) would
            // have their genuinely active plan shadowed by the stale newer-but-inactive row.
            // Look up the actual ACTIVE subscription instead.
            UserSubscriptions senderSub = userSubscriptionsRepository
                    .findTopByUserIdAndStatusOrderByEndDateDesc(request.getSenderId(), SubscriptionStatus.ACTIVE)
                    .orElse(null);
            if (senderSub == null || senderSub.getSubscriptionPlanId() == 1) {
                // Free plan — no chat
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("PLAN_UPGRADE_REQUIRED");
                return response;
            }
            Features messageFeature = featuresRepository.findByCode("MESSAGE");
            if (messageFeature != null) {
                PlanFeatures planFeature = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        messageFeature.getId(), senderSub.getSubscriptionPlanId());
                if (planFeature == null) {
                    response.setCode(403);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("PLAN_UPGRADE_REQUIRED");
                    return response;
                }

                // Conversation limit for plans with numeric limit (e.g. Starter = 5)
                String limitVal = planFeature.getLimitValue();
                if (limitVal != null && limitVal.matches("\\d+")) {
                    int maxConversations = Integer.parseInt(limitVal);

                    // Check if user already sent a message in THIS conversation
                    Long existingInThisConvo = chatRepository.countMessagesByConversationAndSender(
                            request.getConversationId(), request.getSenderId());

                    if (existingInThisConvo == 0) {
                        // First message in a NEW conversation — check if limit reached
                        Long activeConversations = chatRepository.countDistinctConversationsBySenderId(
                                request.getSenderId());

                        if (activeConversations >= maxConversations) {
                            response.setCode(403);
                            response.setStatus(ResponseStatus.FAILURE);
                            response.setMessage("CHAT_LIMIT_REACHED");
                            response.setData(java.util.Map.of(
                                "limit", maxConversations,
                                "used", activeConversations
                            ));
                            return response;
                        }
                    }
                    // If existingInThisConvo > 0, user already chatting here — allow (no new count)
                }
                // "enabled" / "unlimited" values = no limit, pass through
            }

            Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

            // A new message revives the thread in both members' chat lists, mirroring how
            // WhatsApp brings a deleted chat back the moment the other person messages you again.
            //
            // Deleting is now PER-PARTICIPANT (Conversation.deletedByUserOne/Two) rather than the
            // shared isActive flag, so clear both of those. isActive is still reset for rows that
            // were hidden by the old shared-flag behaviour before this change shipped.
            boolean revived = false;
            if (conversation.getIsActive() != ActiveStatus.Y) {
                conversation.setIsActive(ActiveStatus.Y);
                revived = true;
            }
            if (hiddenFromReceiver) {
                // A suppressed message must not resurface the thread for the member who blocked
                // — only the sender's own side is un-hidden.
                boolean senderIsOne = conversation.getUserOne().equals(request.getSenderId());
                if (senderIsOne && Boolean.TRUE.equals(conversation.getDeletedByUserOne())) {
                    conversation.setDeletedByUserOne(Boolean.FALSE); revived = true;
                } else if (!senderIsOne && Boolean.TRUE.equals(conversation.getDeletedByUserTwo())) {
                    conversation.setDeletedByUserTwo(Boolean.FALSE); revived = true;
                }
            } else if (Boolean.TRUE.equals(conversation.getDeletedByUserOne())
                    || Boolean.TRUE.equals(conversation.getDeletedByUserTwo())) {
                conversation.clearDeletedFlags();
                revived = true;
            }
            if (revived) {
                conversationRepository.save(conversation);
            }

            ChatEntity chatMessage = new ChatEntity();
            chatMessage.setConversationId(conversation.getId());
            chatMessage.setSenderId(request.getSenderId());
            chatMessage.setMessage(request.getMessage());
            chatMessage.setIsRead(false);
            chatMessage.setIsActive(ActiveStatus.Y);
            chatMessage.setHiddenFromReceiver(hiddenFromReceiver);
            // Double grey tick straight away when the recipient's device is connected right now.
            Long deliverTo = conversation.getUserOne().equals(request.getSenderId())
                    ? conversation.getUserTwo() : conversation.getUserOne();
            boolean deliveredNow = !hiddenFromReceiver && UserWebSocketHandler.isUserOnline(deliverTo);
            chatMessage.setIsDelivered(deliveredNow);

            chatRepository.save(chatMessage);
            response.setData(java.util.Map.of("id", chatMessage.getId(), "delivered", deliveredNow));

            if (hiddenFromReceiver) {
                // Never delivered: no in-app notification, no push, no WebSocket event.
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Message sent successfully");
                return response;
            }

            // ✅ Identify receiver (the other participant in the conversation)
            Long receiverId = conversation.getUserOne().equals(request.getSenderId())
                    ? conversation.getUserTwo()
                    : conversation.getUserOne();

            // ✅ Fetch both users
            UserEntity sender = userRepository.findById(request.getSenderId()).orElse(null);
            UserEntity receiver = userRepository.findById(receiverId).orElse(null);

            if (sender != null && receiver != null) {
                // ✅ Save notification in DB
                Notification notification = new Notification();
                notification.setSenderId(sender.getUserId());
                notification.setReceiverId(receiverId);
                // A free member cannot open the chat, so the notification must not carry the
                // message text either — they learn that someone wrote, not what.
                notification.setMessage(canReadMessages(receiverId)
                        ? "sent you a message. ' " + request.getMessage() + "'"
                        : "sent you a message. Upgrade to read it.");
                notification.setNotificationCategory("MESSAGE");
                notification.setTitle("New Message");
                notificationRepository.save(notification);

                // ✅ Send push notification — off the request thread. This makes a real HTTP call
                // to Expo's push API; running it synchronously here added that entire round-trip
                // to every single message send (reported as ~3s to see a sent message appear —
                // traced to this, not the DB write or the WebSocket push below).
                String senderName = sender.getFirstName() + " " + sender.getLastName();
                final Long finalReceiverId = receiverId;
                final String finalSenderName = senderName;
                // Direct (plan-gate bypassed): a received chat message is an essential
                // notification every member gets, paid or not. Discovery-style alerts
                // (profile views, likes, shortlists, daily matches) stay plan-gated.
                java.util.concurrent.CompletableFuture.runAsync(() ->
                    pushNotificationService.sendPushNotificationToUserDirect(
                            finalReceiverId,
                            "New Message",
                            // Free recipients see "Someone sent you a message" — the sender's
                            // identity is a paid-plan benefit. Resolved per recipient.
                            pushNotificationService.nameForRecipient(finalReceiverId, finalSenderName)
                                    + " sent you a message. Tap to read it now!")
                );

                // ✅ Push message to recipient via WebSocket for real-time delivery
                String wsMessage = String.format(
                    "{\"type\":\"chat_message\",\"data\":{\"conversationId\":\"%d\",\"senderId\":\"%d\",\"message\":\"%s\",\"senderName\":\"%s\",\"timestamp\":\"%s\"}}",
                    conversation.getId(),
                    request.getSenderId(),
                    request.getMessage().replace("\"", "\\\""),
                    senderName.replace("\"", "\\\""),
                    java.time.Instant.now().toString()
                );
                UserWebSocketHandler.sendMessageToUser(receiverId, wsMessage);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Message sent successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error sending message: " + e.getMessage());
        }
        return response;
    }

    /**
     * A member's device just connected. Everything that was waiting for them is now DELIVERED
     * (single → double grey tick); each sender who is online is told so their open chat can
     * update without a refetch loop.
     */
    public void onUserConnected(Long userId) {
        try {
            List<Object[]> pending = chatRepository.findUndeliveredForReceiver(userId);
            if (pending.isEmpty()) return;
            chatRepository.markDeliveredForReceiver(userId);
            for (Object[] row : pending) {
                Long conversationId = ((Number) row[0]).longValue();
                Long senderId = ((Number) row[1]).longValue();
                UserWebSocketHandler.sendMessageToUser(senderId, String.format(
                        "{\"type\":\"message_delivered\",\"data\":{\"conversationId\":\"%d\",\"deliveredToUserId\":\"%d\"}}",
                        conversationId, userId));
            }
        } catch (Exception e) {
            System.out.println("⚠️ onUserConnected(" + userId + ") failed: " + e.getMessage());
        }
    }

    /**
     * Push online / last-seen to everyone this member shares a conversation with, so an open
     * chat header flips instantly instead of waiting on the app's fallback poll.
     */
    public void broadcastPresence(Long userId, boolean online) {
        try {
            // ⚠️ Instant, NOT LocalDateTime. LocalDateTime.now() serialises with no zone and in
            // the SERVER's local time (IST), while the REST getUserOnlineStatus answer for the
            // same field comes back as UTC — also unmarked. Two unmarked values 5h30m apart down
            // one code path: whichever the app assumed, the other rendered 5h30m wrong. Instant
            // ends in 'Z', so the app can trust the marker instead of guessing.
            String payload = String.format(
                    "{\"type\":\"presence\",\"data\":{\"userId\":\"%d\",\"isOnline\":%s,\"lastSeen\":\"%s\"}}",
                    userId, online, java.time.Instant.now());
            for (Object partner : conversationRepository.findPartnerIds(userId)) {
                if (partner == null) continue;
                Long partnerId = ((Number) partner).longValue();
                if (UserWebSocketHandler.isUserOnline(partnerId)) {
                    UserWebSocketHandler.sendMessageToUser(partnerId, payload);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ broadcastPresence(" + userId + ") failed: " + e.getMessage());
        }
    }

    /**
     * Does this member's ACTIVE plan carry the MESSAGE feature? Free (plan 1) and any plan
     * without a MESSAGE row cannot read chats — the app shows them an upgrade wall — so
     * message TEXT must not leak to them through previews or notifications either.
     */
    public boolean canReadMessages(Long userId) {
        if (userId == null) return false;
        try {
            UserSubscriptions sub = userSubscriptionsRepository
                    .findTopByUserIdAndStatusOrderByEndDateDesc(userId, SubscriptionStatus.ACTIVE)
                    .orElse(null);
            if (sub == null || sub.getSubscriptionPlanId() == null || sub.getSubscriptionPlanId() == 1) return false;
            Features messageFeature = featuresRepository.findByCode("MESSAGE");
            if (messageFeature == null) return true;
            return planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                    messageFeature.getId(), sub.getSubscriptionPlanId()) != null;
        } catch (Exception e) {
            return false; // fail closed
        }
    }

    public ResultResponse getActiveChatsByConversation(Long conversationId) {
        return getActiveChatsByConversation(conversationId, null);
    }

    /**
     * Incremental sync (see ChatRepository.findVisibleAfter). Returns an OBJECT, unlike the legacy
     * array form below, so old app builds that call without `after` are unaffected:
     *   { messages: [...newer than afterId], readUpToId, deliveredUpToId, status }
     * `status` rides along so the app needs no second call to learn a chat was DISCONNECTED.
     */
    public ResultResponse syncConversation(Long conversationId, Long viewerId, Long afterId) {
        ResultResponse response = new ResultResponse();
        try {
            long viewer = viewerId != null ? viewerId : -1L;
            long after = afterId != null && afterId > 0 ? afterId : 0L;
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("messages", chatRepository.findVisibleAfter(conversationId, ActiveStatus.Y, viewer, after));
            data.put("readUpToId", chatRepository.maxReadId(conversationId, viewer));
            data.put("deliveredUpToId", chatRepository.maxDeliveredId(conversationId, viewer));
            conversationRepository.findById(conversationId)
                    .ifPresent(c -> data.put("status", String.valueOf(c.getStatus())));
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Conversation synced.");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error syncing conversation: " + e.getMessage());
        }
        return response;
    }

    /** viewerId (optional) drops messages that are hidden from this viewer — see ChatEntity.hiddenFromReceiver. */
    public ResultResponse getActiveChatsByConversation(Long conversationId, Long viewerId) {
        ResultResponse response = new ResultResponse();
        try {
            List<ChatEntity> chatMessages = viewerId != null
                    ? chatRepository.findVisibleByConversationForViewer(conversationId, ActiveStatus.Y, viewerId)
                    : chatRepository.findAllByConversationIdAndIsActive(conversationId, ActiveStatus.Y);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Chat messages fetched successfully.");
            response.setData(chatMessages);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching chat messages: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse inActiveMessageById(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            if(chatRepository.findById(id).isPresent()) {
                ChatEntity chatMessage = chatRepository.findById(id).get();
                chatMessage.setIsActive(ActiveStatus.N);
                chatRepository.save(chatMessage);
                
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Chat messages inactivated successfully.");
            }else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Chat messages not found.");
            }

          
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching chat messages: " + e.getMessage());
        }
        return response;
    }

    // Note: despite the parameter name, "senderId" here is actually the READER's own id — the
    // repository query marks every message NOT sent by this user as read (i.e. "mark the other
    // person's messages as read by me"). Kept as-is to match the existing repository method
    // signature rather than renaming across call sites.
    public ResultResponse markMessagesAsRead(Long conversationId, Long senderId) {
        ResultResponse response = new ResultResponse();
        try {
            // Update messages as read
            Integer updatedCount = chatRepository.updateMessagesAsRead(conversationId, senderId);

            // Live read-receipt: previously this only updated the DB, so the ORIGINAL sender's
            // tick (single grey -> double blue) never updated while both were actively chatting —
            // it only refreshed on the next full re-fetch (e.g. leaving and re-opening the
            // screen). Push a WS event to the original sender so their screen can refresh.
            if (updatedCount != null && updatedCount > 0) {
                Conversation convo = conversationRepository.findById(conversationId).orElse(null);
                if (convo != null) {
                    Long originalSenderId = convo.getUserOne().equals(senderId) ? convo.getUserTwo() : convo.getUserOne();
                    String wsMessage = String.format(
                        "{\"type\":\"message_read\",\"data\":{\"conversationId\":\"%d\",\"readByUserId\":\"%d\"}}",
                        conversationId,
                        senderId
                    );
                    UserWebSocketHandler.sendMessageToUser(originalSenderId, wsMessage);
                }
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Messages marked as read successfully.");
            response.setData(updatedCount); // Number of messages updated
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error marking messages as read: " + e.getMessage());
        }
        return response;
    }
}
