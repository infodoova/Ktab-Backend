package com.doova.ktab.util.text;

/**
 * Shared text-layer quality checks used by both the PDF classifier
 * ({@code features.ingestion.pdf.PdfTypeClassifier}) and the OCR pipeline's
 * text-layer TOC source ({@code features.ocr.structure.TextLayerTocSource}).
 * Kept in one place so the two checks cannot silently drift apart.
 * See docs/ocr_engine_v3.md, Phase 1.4.
 * <p>
 * {@code text.length() > N} alone proves a text layer exists, not that it is usable:
 * broken CID-to-Unicode maps extract as null/replacement-char runs, foreign OCR layers
 * (e.g. a HYBRID_OCR page's burned-in text) pass length checks but are lower quality
 * than ours, and Arabic frequently extracts as isolated presentation forms that need
 * normalization before they are usable for matching or display.
 */
public final class TextLayerQualityAssessor {

    /** Text layers with a higher replacement/null-char ratio than this are unusable. */
    private static final double MAX_REPLACEMENT_CHAR_RATIO = 0.02;

    /** Above this presentation-form ratio, the text is usable but should be normalized first. */
    private static final double PRESENTATION_FORM_FLAG_RATIO = 0.20;

    /** Below this many letters, the sample is too small to judge script ratio at all. */
    private static final int MIN_ALPHA_CHARS_FOR_SANITY = 30;

    private static final char REPLACEMENT_CHAR = (char) 0xFFFD;
    private static final char NULL_CHAR = (char) 0x0000;

    private TextLayerQualityAssessor() {
    }

    /**
     * @param passesSanityCheck     true if this text layer is trustworthy enough to use as-is
     * @param needsNormalization    true if usable, but should be run through a normalizer first
     * @param expectedScriptRatio   fraction of letters belonging to the expected script (0..1)
     * @param replacementCharRatio  fraction of all characters that are U+FFFD or U+0000
     * @param presentationFormRatio fraction of letters in Arabic presentation-form blocks
     * @param alphanumericRatio     fraction of all characters that are letters or digits
     */
    public record Assessment(
            boolean passesSanityCheck,
            boolean needsNormalization,
            double expectedScriptRatio,
            double replacementCharRatio,
            double presentationFormRatio,
            double alphanumericRatio,
            int alphaChars,
            int totalChars
    ) {
        static Assessment empty() {
            return new Assessment(false, false, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * @param text           extracted page/region text to assess
     * @param languageCode   e.g. {@code Book.getLanguage()}; null or unrecognized defaults to
     *                       the Arabic profile, since that is the primary corpus
     * @param minScriptRatio minimum {@code expectedScriptRatio} required to pass, e.g. 0.60
     */
    public static Assessment assess(String text, String languageCode, double minScriptRatio) {
        if (text == null || text.isBlank()) {
            return Assessment.empty();
        }

        ScriptProfile profile = ScriptProfile.forLanguage(languageCode, text);

        int totalChars = text.length();
        int alphaChars = 0;
        int expectedScriptChars = 0;
        int presentationFormChars = 0;
        int replacementChars = 0;
        int alphanumericChars = 0;

        for (int i = 0; i < totalChars; i++) {
            char c = text.charAt(i);

            if (c == REPLACEMENT_CHAR || c == NULL_CHAR) {
                replacementChars++;
            }
            if (Character.isLetterOrDigit(c)) {
                alphanumericChars++;
            }
            if (Character.isLetter(c)) {
                alphaChars++;
                if (profile.isExpectedScript(c)) {
                    expectedScriptChars++;
                }
                if (profile.isPresentationForm(c)) {
                    presentationFormChars++;
                }
            }
        }

        double expectedScriptRatio = alphaChars == 0 ? 0.0 : (double) expectedScriptChars / alphaChars;
        double replacementCharRatio = (double) replacementChars / totalChars;
        double presentationFormRatio = alphaChars == 0 ? 0.0 : (double) presentationFormChars / alphaChars;
        double alphanumericRatio = (double) alphanumericChars / totalChars;

        boolean passes = alphaChars > MIN_ALPHA_CHARS_FOR_SANITY
                && expectedScriptRatio > minScriptRatio
                && replacementCharRatio < MAX_REPLACEMENT_CHAR_RATIO;

        boolean needsNormalization = presentationFormRatio > PRESENTATION_FORM_FLAG_RATIO;

        return new Assessment(passes, needsNormalization, expectedScriptRatio, replacementCharRatio,
                presentationFormRatio, alphanumericRatio, alphaChars, totalChars);
    }

    private enum ScriptProfile {
        ARABIC {
            @Override
            boolean isExpectedScript(char c) {
                // Arabic block: U+0600-U+06FF
                return c >= 0x0600 && c <= 0x06FF;
            }

            @Override
            boolean isPresentationForm(char c) {
                // Arabic Presentation Forms-A (U+FB50-U+FDFF) and -B (U+FE70-U+FEFF)
                return (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF);
            }
        },
        LATIN {
            @Override
            boolean isExpectedScript(char c) {
                // Basic Latin + Latin-1 Supplement + Latin Extended-A/B, up to IPA Extensions
                return Character.isLetter(c) && c < 0x0250;
            }

            @Override
            boolean isPresentationForm(char c) {
                return false;
            }
        };

        abstract boolean isExpectedScript(char c);

        abstract boolean isPresentationForm(char c);

        static ScriptProfile forLanguage(String languageCode) {
            return forLanguage(languageCode, null);
        }

        static ScriptProfile forLanguage(String languageCode, String text) {
            if (languageCode != null && !languageCode.isBlank()) {
                String lc = languageCode.toLowerCase();
                if (lc.startsWith("en") || lc.startsWith("fr") || lc.startsWith("de")
                        || lc.startsWith("es") || lc.startsWith("it")) {
                    return LATIN;
                }
                return ARABIC;
            }
            if (text != null) {
                int arabic = 0;
                int latin = 0;
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (ARABIC.isExpectedScript(c)) {
                        arabic++;
                    } else if (LATIN.isExpectedScript(c)) {
                        latin++;
                    }
                }
                if (latin > arabic) {
                    return LATIN;
                }
            }
            return ARABIC;
        }
    }
}
