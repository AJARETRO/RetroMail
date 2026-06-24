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
    private final boolean newsEnabled;
    private final boolean surveysEnabled;
    private final boolean salesEnabled;

    public SubscriptionState(Type type, String email, String code, boolean newsEnabled, boolean surveysEnabled, boolean salesEnabled) {
        this.type = type;
        this.email = email;
        this.code = code;
        this.newsEnabled = newsEnabled;
        this.surveysEnabled = surveysEnabled;
        this.salesEnabled = salesEnabled;
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

    public boolean isNewsEnabled() {
        return newsEnabled;
    }

    public boolean isSurveysEnabled() {
        return surveysEnabled;
    }

    public boolean isSalesEnabled() {
        return salesEnabled;
    }
}
