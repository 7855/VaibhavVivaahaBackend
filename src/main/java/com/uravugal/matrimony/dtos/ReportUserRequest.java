package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportUserRequest {
    @NotBlank(message = "Reporter user ID is required")
    private String reportedByUserId;

    @NotNull(message = "Reported user ID is required")
    private Long reportedUserId;

    @NotBlank(message = "Reason is required")
    private String reason;

    // Whether this report should also block the reported user. Null is treated as "true" for
    // backward compatibility with any client that doesn't send it — but the mobile app now always
    // sends this explicitly (defaulting to checked for profile reports, unchecked for message
    // reports), since silently force-blocking on every report was the original bug being fixed.
    private Boolean blockUser;

    // Present only when reporting a specific chat message rather than the whole profile.
    private Long reportedMessageId;
    private String messageContent;
}