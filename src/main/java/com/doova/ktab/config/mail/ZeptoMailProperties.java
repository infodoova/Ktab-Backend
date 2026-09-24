package com.doova.ktab.config.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ktab.mail.zepto")
public class ZeptoMailProperties {

    /**
     * Whether the email service is enabled.
     */
    private boolean enabled = true;

    /**
     * SMTP host for Zoho ZeptoMail.
     */
    private String host = "smtp.zeptomail.com";

    /**
     * SMTP port (typically 587 for STARTTLS or 465 for SSL).
     */
    private int port = 587;

    /**
     * SMTP username (default "emailapikey" for ZeptoMail).
     */
    private String username = "emailapikey";

    /**
     * ZeptoMail Send Mail Token / SMTP password.
     */
    private String password = "";

    /**
     * Default From email address (must be a domain verified in ZeptoMail).
     */
    private String fromEmail = "noreply@ktab.app";

    /**
     * Default From personal/display name.
     */
    private String fromName = "Ktab";

    /**
     * Default Reply-To address (optional).
     */
    private String replyTo;

    /**
     * Socket connection timeout in milliseconds.
     */
    private int connectionTimeoutMs = 5000;

    /**
     * Socket read timeout in milliseconds.
     */
    private int readTimeoutMs = 5000;

    /**
     * Socket write timeout in milliseconds.
     */
    private int writeTimeoutMs = 5000;

    /**
     * Async task executor core pool size.
     */
    private int asyncCorePoolSize = 4;

    /**
     * Async task executor max pool size.
     */
    private int asyncMaxPoolSize = 16;

    /**
     * Async task executor queue capacity.
     */
    private int asyncQueueCapacity = 250;
}
