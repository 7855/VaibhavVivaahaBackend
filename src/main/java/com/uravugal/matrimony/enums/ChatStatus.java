package com.uravugal.matrimony.enums;

public enum ChatStatus {
    PENDING, ACCEPTED, BLOCKED,
    // Interest connection removed by either side after acceptance — the thread stays in both
    // chat lists (history is kept) but nothing can be sent until an interest is accepted again.
    DISCONNECTED
}
