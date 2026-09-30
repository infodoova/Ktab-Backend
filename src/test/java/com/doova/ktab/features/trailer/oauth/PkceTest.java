package com.doova.ktab.features.trailer.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    @Test
    void rfc7636AppendixBVector() {
        // RFC 7636 Appendix B: verifier -> S256 challenge
        assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void verifierIsUrlSafeAndLongEnough() {
        String v = Pkce.verifier();
        assertThat(v).matches("[A-Za-z0-9_-]{43,128}");
    }
}
