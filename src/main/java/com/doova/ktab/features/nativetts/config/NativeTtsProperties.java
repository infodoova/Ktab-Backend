package com.doova.ktab.features.nativetts.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Ktab's own text-to-speech (ElevenLabs standard API), used for audiobooks while the Studio switch is off. */
@Component
@ConfigurationProperties(prefix = "ktab.native-tts")
@Getter
@Setter
public class NativeTtsProperties {

    private String apiKey;
    private String baseUrl = "https://api.elevenlabs.io";
    /** No default: the owner chooses the narrator. The audiobook job refuses to start while this is blank. */
    private String voiceId;
    /** eleven_multilingual_v2 accepts neighbouring text and long requests; eleven_v3 accepts neither (measured 2026-10-01). */
    private String modelId = "eleven_multilingual_v2";
    private String outputFormat = "mp3_44100_128";
    private int maxCharsPerRequest = 2500;
    /** Send the previous/next chunk so the voice joins smoothly (ignored for eleven_v3, which rejects it). */
    private boolean sendContext = true;
    private int maxRetries = 4;
    private long retryBackoffMillis = 2000;
    /** The audiobook job fails before synthesizing anything above this many characters. */
    private long maxCharsPerBook = 1_500_000;
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";

    public boolean isV3() {
        return modelId != null && modelId.startsWith("eleven_v3");
    }
}
