package com.doova.ktab.features.imagegen.prompt;

import com.doova.ktab.features.imagegen.enums.ImageAspectRatio;
import com.doova.ktab.features.imagegen.enums.ImageTheme;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Builds safe, publication-grade prompts for book illustration generation.
 * Enforces strict boundaries so raw book content is never processed or leaked.
 */
@Component
public class ImagePromptBuilder {

    private static final Pattern INJECTION_PATTERNS = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|prior)\\s+instructions|system\\s*:|developer\\s*:|<\\|.*?\\|>)"
    );

    /**
     * Sanitizes user input and constructs the complete prompt for the image generator.
     */
    public String buildPrompt(String userContext, ImageTheme theme, ImageAspectRatio aspectRatio, String styleNotes) {
        return buildPrompt(userContext, theme, aspectRatio, styleNotes, null);
    }

    /**
     * Constructs prompt with book entity context (title, author, genre, synopsis, cover page reference)
     * enabling the AI model to search its knowledge base and ground visual fidelity in the book's world.
     */
    public String buildPrompt(
            String userContext,
            ImageTheme theme,
            ImageAspectRatio aspectRatio,
            String styleNotes,
            BookPromptContext bookContext
    ) {
        String cleanContext = sanitize(userContext);
        String cleanNotes = (styleNotes != null && !styleNotes.isBlank()) ? sanitize(styleNotes) : "None";

        String bookSection;
        if (bookContext != null && bookContext.title() != null && !bookContext.title().isBlank()) {
            String title = sanitize(bookContext.title());
            String author = (bookContext.author() != null && !bookContext.author().isBlank()) ? sanitize(bookContext.author()) : "Unknown Author";
            String genre = (bookContext.genre() != null && !bookContext.genre().isBlank()) ? sanitize(bookContext.genre()) : "General";
            String description = (bookContext.description() != null && !bookContext.description().isBlank()) ? sanitize(bookContext.description()) : "Not provided";
            String coverRef = (bookContext.coverImageUrl() != null && !bookContext.coverImageUrl().isBlank())
                    ? bookContext.coverImageUrl()
                    : "Not provided";

            bookSection = """
                    BOOK ENTITY & LORE GROUNDING:
                    - Book Title: "%s"
                    - Author: %s
                    - Genre: %s
                    - Synopsis / Premise: %s
                    - Official Cover Page Reference / Style: %s
                    DIRECTIVE FOR AI:
                    Search your knowledge base, literary lore, and visual memory for the book "%s".
                    Ground the visual style, characters, setting, and mood in this book's established universe and cover art aesthetic.
                    """.formatted(title, author, genre, description, coverRef, title);
        } else {
            bookSection = "BOOK ENTITY & LORE GROUNDING:\n- Literary Context: General book illustration";
        }

        return """
                TASK: HIGH-QUALITY BOOK SCENE ILLUSTRATION
                ==================================================
                %s
                ==================================================
                PRIMARY SCENE CONTEXT:
                %s

                VISUAL ART STYLE:
                Theme: %s
                Style Directives: %s
                Target Aspect Ratio: %s (%s)
                Specific Artistic Notes: %s

                COMPOSITION & QUALITY SPECIFICATIONS:
                - Publication-grade book illustration with clear visual narrative.
                - Cohesive color harmony, well-balanced lighting, and strong focal point.
                - Depth of field and dimensional layered composition (foreground, midground, background).

                STRICT NEGATIVE CONSTRAINTS:
                - Do NOT render any text, typography, letters, alphabet, captions, or subtitles.
                - Do NOT include watermarks, signatures, copyright marks, or logos.
                - Avoid blurry textures, distorted geometry, or oversaturated noise.
                ==================================================
                """.formatted(
                bookSection,
                cleanContext,
                theme.getDisplayName(),
                theme.getStyleDirective(),
                aspectRatio.getRatio(),
                aspectRatio.getDescription(),
                cleanNotes
        );
    }

    /**
     * Computes deterministic SHA-256 hash of the canonical prompt inputs for deduplication.
     */
    public String computePromptHash(String userContext, ImageTheme theme, ImageAspectRatio aspectRatio) {
        String canonicalKey = sanitize(userContext) + "|" + theme.name() + "|" + aspectRatio.getRatio();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Sanitizes user creative description, preventing prompt injection and unprintable control characters.
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String stripped = INJECTION_PATTERNS.matcher(input).replaceAll(" ");
        // Strip control characters except newline and tab
        stripped = stripped.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        return stripped.trim();
    }
}
