package com.doova.ktab.features.tts.util;

import java.util.Base64;

public final class AudioDecoder {

    private AudioDecoder() {}

    public static byte[] decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
