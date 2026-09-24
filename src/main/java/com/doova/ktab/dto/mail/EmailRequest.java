package com.doova.ktab.dto.mail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EmailRequest(
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String fromEmail,
        String fromName,
        String replyTo,
        String subject,
        String templateName,
        Map<String, Object> templateVariables,
        String htmlBody,
        String textBody,
        List<EmailAttachment> attachments,
        Map<String, String> headers
) {
    public EmailRequest {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        templateVariables = templateVariables == null ? Map.of() : Map.copyOf(templateVariables);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> toList = new ArrayList<>();
        private final List<String> ccList = new ArrayList<>();
        private final List<String> bccList = new ArrayList<>();
        private String fromEmail;
        private String fromName;
        private String replyTo;
        private String subject;
        private String templateName;
        private final Map<String, Object> variables = new HashMap<>();
        private String htmlBody;
        private String textBody;
        private final List<EmailAttachment> attachmentList = new ArrayList<>();
        private final Map<String, String> headerMap = new HashMap<>();

        public Builder to(String recipient) {
            if (recipient != null && !recipient.isBlank()) {
                this.toList.add(recipient.trim());
            }
            return this;
        }

        public Builder to(String... recipients) {
            if (recipients != null) {
                for (String r : recipients) {
                    to(r);
                }
            }
            return this;
        }

        public Builder to(Collection<String> recipients) {
            if (recipients != null) {
                recipients.forEach(this::to);
            }
            return this;
        }

        public Builder cc(String recipient) {
            if (recipient != null && !recipient.isBlank()) {
                this.ccList.add(recipient.trim());
            }
            return this;
        }

        public Builder cc(String... recipients) {
            if (recipients != null) {
                for (String r : recipients) {
                    cc(r);
                }
            }
            return this;
        }

        public Builder bcc(String recipient) {
            if (recipient != null && !recipient.isBlank()) {
                this.bccList.add(recipient.trim());
            }
            return this;
        }

        public Builder bcc(String... recipients) {
            if (recipients != null) {
                for (String r : recipients) {
                    bcc(r);
                }
            }
            return this;
        }

        public Builder from(String email, String name) {
            this.fromEmail = email;
            this.fromName = name;
            return this;
        }

        public Builder fromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
            return this;
        }

        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder templateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder template(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder variable(String key, Object value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder templateVariables(Map<String, Object> map) {
            if (map != null) {
                this.variables.putAll(map);
            }
            return this;
        }

        public Builder variables(Map<String, Object> map) {
            return templateVariables(map);
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder html(String html) {
            this.htmlBody = html;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder text(String text) {
            this.textBody = text;
            return this;
        }

        public Builder attachment(EmailAttachment attachment) {
            if (attachment != null) {
                this.attachmentList.add(attachment);
            }
            return this;
        }

        public Builder attachment(String filename, byte[] data, String contentType) {
            return attachment(EmailAttachment.of(filename, data, contentType));
        }

        public Builder attachments(Collection<EmailAttachment> attachments) {
            if (attachments != null) {
                this.attachmentList.addAll(attachments);
            }
            return this;
        }

        public Builder header(String key, String value) {
            this.headerMap.put(key, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headerMap.putAll(headers);
            }
            return this;
        }

        public EmailRequest build() {
            return new EmailRequest(
                    Collections.unmodifiableList(new ArrayList<>(this.toList)),
                    Collections.unmodifiableList(new ArrayList<>(this.ccList)),
                    Collections.unmodifiableList(new ArrayList<>(this.bccList)),
                    this.fromEmail,
                    this.fromName,
                    this.replyTo,
                    this.subject,
                    this.templateName,
                    Collections.unmodifiableMap(new HashMap<>(this.variables)),
                    this.htmlBody,
                    this.textBody,
                    Collections.unmodifiableList(new ArrayList<>(this.attachmentList)),
                    Collections.unmodifiableMap(new HashMap<>(this.headerMap))
            );
        }
    }
}
