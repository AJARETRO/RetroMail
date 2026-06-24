package dev.retro.papersmtp.config;

import java.util.List;

public interface MailConfigInterface {
    boolean isMailHandlerEnabled();
    int getMailHandlerPort();
    String getMailHandlerDomain();
    List<String> getMailHandlerRestricted();
    
    String getImapHost();
    int getImapPort();
    boolean isImapSsl();
    String getImapUsername();
    String getImapPassword();
    int getImapPollInterval();

    String getDbType();
    String getSqliteFile();
    String getMysqlHost();
    int getMysqlPort();
    String getMysqlDatabase();
    String getMysqlUsername();
    String getMysqlPassword();

    String getSmtpHost();
    int getSmtpPort();
    String getSmtpUsername();
    String getSmtpPassword();
    boolean isSmtpSsl();
    boolean isSmtpStarttls();
    String getSmtpFromAddress();
    String getSmtpFromName();
}
