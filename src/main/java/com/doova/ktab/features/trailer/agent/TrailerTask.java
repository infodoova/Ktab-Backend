package com.doova.ktab.features.trailer.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The per-trailer task text sent as the outcome description; the durable instructions live in the agent's system prompt. */
public final class TrailerTask {

    private TrailerTask() {
    }

    public static String describe(String title, String author, String language, String voiceId, int maxVideoJobs,
                                  int maxJobs, int maxInFlight, String generateArgs, boolean hasCover) {
        String coverLine = hasCover ? "Cover image: /workspace/cover.jpg\n" : "";
        return """
                Produce the 30-second trailer for this book.
                Book: /workspace/book.pdf
                %sTitle: %s
                Author: %s
                Book language: %s
                ElevenLabs voice_id for the Arabic narration: %s
                End-card layers (rendered by Ktab, composite them as-is): /workspace/endcard/
                Higgsfield video allowance: at most %d video jobs in total, including replacements for a discarded video.
                Higgsfield total allowance: at most %d Higgsfield jobs in total; image jobs count toward the total, not toward the video jobs.
                Higgsfield concurrency: keep at most %d generations in flight at a time.
                Call generate_video with exactly these arguments: %s
                Write every deliverable to /mnt/session/outputs/ as described in your instructions.
                """.formatted(coverLine, title, author == null ? "" : author, language == null ? "ar" : language, voiceId,
                maxVideoJobs, maxJobs, maxInFlight,
                generateArgs == null || generateArgs.isBlank() ? "(see your instructions)" : generateArgs);
    }

    public static String rubric() {
        try (InputStream in = TrailerTask.class.getResourceAsStream("/trailer-agent/rubric.md")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource trailer-agent/rubric.md");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
