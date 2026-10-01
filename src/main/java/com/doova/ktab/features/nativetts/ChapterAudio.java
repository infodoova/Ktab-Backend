package com.doova.ktab.features.nativetts;

import java.nio.file.Path;

/** A whole chapter: one joined MP3 and a character-level timing index (milliseconds from the chapter's start). */
public record ChapterAudio(Path mp3, int durationMs, String chars, int[] startMs, int[] endMs) {
}
