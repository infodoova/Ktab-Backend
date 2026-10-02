package com.doova.ktab.features.nativetts.batch;

import com.doova.ktab.features.nativetts.config.NativeTtsProperties;
import com.doova.ktab.repository.book.BookPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/** Fails fast, before anything is paid for: no voice, no text, or more characters than the per-book cap. */
@Slf4j
public class NativeEstimateTasklet implements Tasklet {

    private final Long bookId;
    private final NativeTtsProperties props;
    private final BookPageRepository pages;

    public NativeEstimateTasklet(Long bookId, NativeTtsProperties props, BookPageRepository pages) {
        this.bookId = bookId;
        this.props = props;
        this.pages = pages;
    }

    long estimate() {
        if (props.getVoiceId() == null || props.getVoiceId().isBlank()) {
            throw new IllegalStateException("No narrator voice is configured: set KTAB_NATIVE_TTS_VOICE_ID");
        }
        long chars = pages.getTotalCharacterCount(bookId);
        if (chars <= 0) {
            throw new IllegalStateException("The book has no text to read");
        }
        if (chars > props.getMaxCharsPerBook()) {
            throw new IllegalStateException("The book has " + chars + " characters, over the limit of "
                    + props.getMaxCharsPerBook() + " (ktab.native-tts.max-chars-per-book)");
        }
        return chars;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("native audiobook estimate bookId={} characters={} model={}", bookId, estimate(), props.getModelId());
        return RepeatStatus.FINISHED;
    }
}
