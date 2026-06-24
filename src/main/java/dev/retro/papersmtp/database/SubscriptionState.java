package dev.retro.papersmtp.database;

public class SubscriptionState {
    public enum Type {
        NONE,
        PENDING,
        VERIFIED
    }

    private final Type type;
    private final String email;
    private final String code;

    public SubscriptionState(Type type, String email, String code) {
        this.type = type;
        this.email = email;
        this.code = code;
    }

    public Type getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }
}
