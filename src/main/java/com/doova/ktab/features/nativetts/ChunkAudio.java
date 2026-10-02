package com.doova.ktab.features.nativetts;

/** One TTS response: the MP3 plus, for every character that was sent, when it starts and ends (seconds from the chunk's start). */
public record ChunkAudio(byte[] mp3, String chars, double[] startSec, double[] endSec) {
}
