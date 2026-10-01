package com.doova.ktab.features.nativetts;

/** One vendor call. {@code previousText}/{@code nextText} are empty when there is none. Other vendors can implement this. */
public interface ChunkTtsProvider {

    ChunkAudio synthesize(String text, String previousText, String nextText);
}
