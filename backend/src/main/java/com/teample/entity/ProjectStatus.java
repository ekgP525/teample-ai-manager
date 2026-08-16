package com.teample.entity;

public enum ProjectStatus {
    ACTIVE,
    END_SCHEDULED,
    ENDED,
    DELETED;

    public boolean isDeleted() {
        return this == DELETED;
    }

    public boolean isEnded() {
        return this == ENDED;
    }

    public boolean isVisibleInActiveList() {
        return this != DELETED;
    }

    public boolean blocksNewMinutes() {
        return this == ENDED || this == DELETED;
    }

    public String toClientStatus() {
        return switch (this) {
            case ACTIVE -> "ACTIVE";
            case END_SCHEDULED -> "DISPOSAL_SCHEDULED";
            case ENDED -> "DISPOSED";
            case DELETED -> "DELETED";
        };
    }
}
