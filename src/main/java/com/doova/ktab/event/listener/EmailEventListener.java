package com.doova.ktab.event.listener;

import com.doova.ktab.event.model.SendEmailEvent;
import com.doova.ktab.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final EmailService emailService;

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSendEmail(SendEmailEvent event) {
        log.debug("Processing SendEmailEvent after commit for subject: {}", event.request().subject());
        try {
            emailService.sendSync(event.request());
        } catch (Exception e) {
            log.error("Failed to process transactional email event for subject '{}': {}", 
                    event.request().subject(), e.getMessage(), e);
        }
    }
}
