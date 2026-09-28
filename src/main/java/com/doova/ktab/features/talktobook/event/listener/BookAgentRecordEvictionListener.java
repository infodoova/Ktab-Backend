package com.doova.ktab.features.talktobook.event.listener;

import com.doova.ktab.features.talktobook.config.TalkToBookProperties;
import com.doova.ktab.features.talktobook.event.model.BookAgentRecordCreatedEvent;
import com.doova.ktab.features.talktobook.repository.BookAgentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookAgentRecordEvictionListener {

    private final BookAgentRecordRepository recordRepository;
    private final TalkToBookProperties properties;

    @Async
    @EventListener
    @Transactional
    public void onRecordCreated(BookAgentRecordCreatedEvent event) {
        Long bookId = event.bookId();
        long totalRecords = recordRepository.countByBookId(bookId);

        if (totalRecords > properties.getMaxRecordsPerBook()) {
            log.info("Book {} reached {} cached records (threshold: {}). Initiating LFU/LRU eviction.",
                    bookId, totalRecords, properties.getMaxRecordsPerBook());

            List<UUID> idsToEvict = recordRepository.findIdsForEviction(
                    bookId, PageRequest.of(0, properties.getEvictionBatchSize()));

            if (!idsToEvict.isEmpty()) {
                recordRepository.deleteAllByIdIn(idsToEvict);
                log.info("Evicted {} lowest-frequency cached records for book {}. Remaining: {}",
                        idsToEvict.size(), bookId, totalRecords - idsToEvict.size());
            }
        }
    }
}
