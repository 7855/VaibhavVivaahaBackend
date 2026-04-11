package com.uravugal.matrimony.enums;

public enum FeatureType {
    SEND_INTEREST(1L),
    VIEW_CONTACT(2L),
    CHAT(3L);

    private final Long id;

    FeatureType(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}

