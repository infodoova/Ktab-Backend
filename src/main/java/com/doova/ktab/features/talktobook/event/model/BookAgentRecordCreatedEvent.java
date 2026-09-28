package com.doova.ktab.features.talktobook.event.model;

/**
 * Event fired when a new BookAgentRecord is persisted, prompting asynchronous storage maintenance.
 */
public record BookAgentRecordCreatedEvent(
        Long bookId
) {
}
