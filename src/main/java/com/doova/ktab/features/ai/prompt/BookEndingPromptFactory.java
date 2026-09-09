package com.doova.ktab.features.ai.prompt;

import com.doova.ktab.features.ai.dto.request.GenerateEndingCommand;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;
import java.util.Map;

/**
 * Responsible only for building the Prompt object
 * from a high-level command. No HTTP / controller / model calls here.
 */
@Component
public class BookEndingPromptFactory {

    private static final String BOOK_ENDING_TEMPLATE = """
            You are given a single attached PDF file that contains a complete book.
            
            Your ONLY source of truth for this task is the content of that attached PDF.
            
            Fill-in parameters (use these as your strict guidance):
            
            APPROX_WORD_COUNT_FOR_ENDING: {APPROX_WORD_COUNT_FOR_ENDING}
            
            TARGET_AUDIENCE_AGE_AND_PROFILE: {TARGET_AUDIENCE_AGE_AND_PROFILE}
            (This field includes: age range, reading level/background, genre preferences,
            sensitivity limits, desired emotional impact, and any cultural/contextual notes.)
            
            YOUR TASK
            
            Create a new, extended ending for this specific book that:
            
            - Continues organically from the book’s existing final chapter(s).
            - Preserves the author’s voice, tone, and style as closely as possible.
            - Respects the original plot, word building, themes, and character arcs.
            - Is written in the same language as the book PDF.
            - Is approximately APPROX_WORD_COUNT_FOR_ENDING words (slightly more or less is acceptable for natural flow).
            - Subtly adapts focus and emphasis to resonate with the TARGET_AUDIENCE_AGE_AND_PROFILE,
              without changing the book’s core meaning.
            
            ABSOLUTE CONSTRAINTS TO REDUCE HALLUCINATIONS
            
            Obey ALL of these rules strictly:
            
            SOURCE OF TRUTH
            
            - Use ONLY the content of the attached book PDF.
            - Do NOT use outside knowledge about:
              - The real-world author.
              - Any known adaptations, fandom theories, or sequels.
              - Other books, movies, or franchises.
            - If something is not clearly supported or logically implied by the text, treat it as unknown
              and avoid inventing arbitrary details.
            
            STORY CONSISTENCY
            
            - Do NOT:
              - Introduce new main characters out of nowhere.
              - Introduce new magic systems, technologies, or settings that are not at least implied earlier.
              - Recon or contradict established facts, timelines, relationships, or character traits.
            - You may create small, plausible, low-risk details only when:
              - They are consistent with multiple existing story elements, AND
              - They are necessary to keep the ending readable and coherent.
            - If the book leaves something ambiguous or unresolved and you lack enough information to
              resolve it decisively:
              - Prefer a thematically consistent, suggestive ending instead of making up random twists.
            
            STYLE & TONE MATCHING
            
            - Detect and mimic:
              - Narrative voice (1st/3rd person, tense, point of view).
              - Sentence length, vocabulary, rhythm, and dialogue style.
              - Overall emotional tone (e.g., dark, ironic, hopeful, melancholic).
            - Avoid modernizing, simplifying, or “fixing” the style unless that shift is already happening
              in the last chapters.
            - Do NOT mention that this is an “AI-generated ending” or refer to yourself, the user, or the PDF.
            
            TARGET AUDIENCE ADAPTATION (WITHOUT BREAKING THE BOOK)
            
            - Adapt the emphasis, not the canon:
              - Adjust how clear or subtle you make themes and symbolism based on the
                TARGET_AUDIENCE_AGE_AND_PROFILE.
              - Highlight character arcs and emotional beats that this audience will connect with.
              - Calibrate how intense or explicit emotional scenes are, respecting the sensitivity
                information included in TARGET_AUDIENCE_AGE_AND_PROFILE.
            - Do NOT change:
              - The fundamental message of the story.
              - The established moral universe.
              - The core fates of characters in a way that contradicts their development.
            
            SAFETY & APPROPRIATENESS
            
            - Follow general safety and content guidelines.
            - If the book includes sensitive content:
              - Reflect it in a way that feels truthful to the book while staying within the limits
                described in TARGET_AUDIENCE_AGE_AND_PROFILE.
              - Avoid gratuitous graphic detail or shock purely for effect.
            
            OUTPUT FORMAT
            
            - Output only the final, continuous ending text as if it were the last part of the book.
            - No headings, bullet points, analysis, explanations, or notes.
            - Do NOT describe your process or mention any of these instructions.
            - Just continue the narrative in the same language and style as the original author,
              for approximately APPROX_WORD_COUNT_FOR_ENDING words.
            """;

    public Prompt buildPrompt(GenerateEndingCommand command) {
        // 1) Fill placeholders
        PromptTemplate template = new PromptTemplate(BOOK_ENDING_TEMPLATE);

        // Map both the broad IntendedAudience description and the specific Profile text
        String rendered = template.render(Map.of("APPROX_WORD_COUNT_FOR_ENDING", command.approxWordCountForEnding(), "TARGET_AUDIENCE_AGE_AND_PROFILE", command.audienceProfile().getProfileText().trim()));

        // 2) Attach PDF as multimodal input
        Resource pdfResource = command.pdfResource();
        Media pdfMedia = new Media(new MimeType("application", "pdf"), pdfResource);

        UserMessage userMessage = UserMessage.builder().text(rendered).media(List.of(pdfMedia)) // or .media(pdfMedia)
                .build();

        // 3) Wrap into Prompt
        return new Prompt(List.of(userMessage));
    }
}
