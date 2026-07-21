package com.uravugal.matrimony.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// import com.uravugal.matrimony.dtos.ChatRequest;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
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
            // Block gate: if either side has blocked the other, reject
            if (request.getSenderId() != null && request.getConversationId() != null) {
                Conversation convo = conversationRepository.findById(request.getConversationId()).orElse(null);
                if (convo != null) {
                    Long otherUserId = convo.getUserOne().equals(request.getSenderId()) ? convo.getUserTwo() : convo.getUserOne();
                    com.uravugal.matrimony.models.BlockedUser block = blockedUserRepository
                            .findByUsersEitherDirection(request.getSenderId(), otherUserId);
                    if (block != null) {
                        response.setCode(403);
                        response.setStatus(ResponseStatus.FAILURE);
                        response.setMessage("USER_BLOCKED");
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

            // getUserChatList now filters to isActive='Y' (needed for the new delete-conversation
            // feature to actually hide anything). Since isActive is one shared flag on the
            // conversation row rather than per-participant, deleting it currently hides it for
            // BOTH sides — so a new message needs to revive it here, mirroring how WhatsApp
            // brings a deleted chat back into your list the moment the other person messages
            // you again, rather than leaving it hidden forever.
            if (conversation.getIsActive() != ActiveStatus.Y) {
                conversation.setIsActive(ActiveStatus.Y);
                conversationRepository.save(conversation);
            }

            ChatEntity chatMessage = new ChatEntity();
            chatMessage.setConversationId(conversation.getId());
            chatMessage.setSenderId(request.getSenderId());
            chatMessage.setMessage(request.getMessage());
            chatMessage.setIsRead(false);
            chatMessage.setIsActive(ActiveStatus.Y);

            chatRepository.save(chatMessage);

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
                notification.setMessage("sent you a message. ' " + request.getMessage() + "'");
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
                java.util.concurrent.CompletableFuture.runAsync(() ->
                    pushNotificationService.sendPushNotificationToUser(
                            finalReceiverId,
                            "New Message",
                            finalSenderName + " sent you a message. Tap to read it now!")
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

    public ResultResponse getActiveChatsByConversation(Long conversationId) {
        ResultResponse response = new ResultResponse();
        try {
            List<ChatEntity> chatMessages = chatRepository.findAllByConversationIdAndIsActive(conversationId, ActiveStatus.Y);

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
