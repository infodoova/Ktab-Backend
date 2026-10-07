package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.admin.StorybookAdminService;
import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.illustration.LookService;
import com.doova.ktab.features.storybook.model.*;
import com.doova.ktab.features.storybook.repository.*;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.pipeline.StoryApprovalService;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.StorybookService;
import com.doova.ktab.features.storybook.web.dto.CharacterInput;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.UserRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "ktab.storybook.enabled=true",
        "ktab.storybook.credits.required=false",
        "ktab.storybook.llm.provider=OPENAI",
        "ktab.storybook.llm.model=gpt-6-luna",
        "ktab.storybook.image.primary-model=gemini-3.1-flash-image",
        "ktab.storybook.image.fallback-model=gemini-3-pro-image",
        "ktab.storybook.worker.poll-delay=999999s"
})
@DisplayName("Storybook End-To-End Live Execution Pipeline")
class StorybookEndToEndLiveExecutionIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private StorybookService storybookService;

    @Autowired
    private StorybookRepository storybookRepository;

    @Autowired
    private StorybookPageRepository pageRepository;

    @Autowired
    private StorybookCharacterRepository characterRepository;

    @Autowired
    private StorybookJobRepository jobRepository;

    @Autowired
    private StorybookAiCallRepository aiCallRepository;

    @Autowired
    private StoryApprovalService storyApprovalService;

    @Autowired
    private LookService lookService;

    @Autowired
    private StorybookAdminService adminService;

    @Autowired
    private StepHandlerRegistry handlers;

    @Autowired
    private JobClaimer claimer;

    @Autowired
    private JobOutcomeRecorder recorder;

    @Autowired
    private StorybookAssetStore assetStore;

    @Autowired
    private StorybookProperties properties;

    @Test
    @DisplayName("Generate complete storybook as end-user: DB persistence, approval bypassing, Gemini 3.1 & GPT-6 Luna, Playwright PDF with Cairo")
    void generateCompleteStory_asEndUser_persistsToDbAndOutputsPdf() throws Exception {
        System.out.println("================================================================================");
        System.out.println(">>> STARTING COMPLETE STORYBOOK END-TO-END LIVE PIPELINE EXECUTION");
        System.out.println("================================================================================");

        // 1. Ensure test user exists in PostgreSQL
        String testEmail = "parent.tester." + System.currentTimeMillis() + "@ktab.app";
        User user = new User();
        user.setEmail(testEmail);
        user.setFirstName("أحمد");
        user.setLastName("المهندس");
        user.setPasswordDigest("$2a$10$notrealpasswordhashfortestingonly");
        user.setRole("20"); // READER
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();
        System.out.printf("  1. Test User Prepared: ID=%d, Email=%s%n", user.getId(), user.getEmail());

        // 2. Prepare ChildProfile in PostgreSQL
        ChildProfile profile = new ChildProfile();
        profile.setOwner(user);
        profile.setNameAr("سامي");
        profile.setGender(ChildGender.BOY);
        profile.setAgeBand(AgeBand.AGE_6_8);
        profile.setAppearance(StoryFixtures.APPEARANCE);
        profile = childProfileRepository.save(profile);
        assertThat(profile.getId()).isNotNull();
        System.out.printf("     - Child Profile Created: ID=%d, Name=%s, AgeBand=%s%n",
                profile.getId(), profile.getNameAr(), profile.getAgeBand());

        // 3. Build multi-character request matching V2 spec
        CharacterInput childChar = new CharacterInput(
                "child-1", "سامي", "CHILD", 7, ChildGender.BOY, "Protagonist", "self",
                List.of("فضولي", "شجاع"), StoryFixtures.APPEARANCE, "قميص أزرق طويل الأكمام وبنطال رمادي",
                "الرسم والاستكشاف", "العجلة", "الرسم", "دفتر رسم صغير", "هادئ ومرح"
        );

        CharacterInput companionChar = new CharacterInput(
                "companion-1", "بسبوس", "COMPANION", 2, null, "Companion", "pet cat",
                List.of("مرح", "وفي"), null, "طوق أخضر صغير",
                "سرعة الحركة", "الخوف من الماء", "اللعب بالخيوط", "كرة صوف حمراء", "مواء لطيف"
        );

        CreateStorybookRequest request = new CreateStorybookRequest(
                profile.getId(),
                List.of(Interest.CATS, Interest.DRAWING),
                new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوس", null, CompanionSpec.PetColor.ORANGE),
                StorySetting.CAIRO,
                ArtStyle.SOFT_WATERCOLOR,
                15,
                LanguageVariety.EGYPTIAN,
                TashkeelLevel.NONE,
                "إلى بطلنا الصغير سامي، نرجو أن تكون هذه المغامرة بداية لرحلة استكشاف لا تنتهي.",
                "FRIENDSHIP",
                "PLAYFUL",
                "التعاون والعناية بالكائنات الحية والطبيعة",
                "مغامرة في حديقة المدرسة مع قطته الصغيرة لاكتشاف سر النباتات العجيبة",
                List.of("العنف أو المشاهد المخيفة"),
                "PORTRAIT",
                List.of(childChar, companionChar)
        );

        // 4. User initiates creation -> Persists to DB
        StorybookDetail detail = storybookService.create(user, request);
        Long bookId = detail.id();
        System.out.printf("  2. Storybook Created in DB: ID=%d, Title=%s, Status=%s%n",
                bookId, detail.titleAr(), detail.status());

        Storybook initialBook = storybookRepository.findById(bookId).orElseThrow();
        assertThat(initialBook.getStatus()).isEqualTo(StorybookStatus.DRAFT);
        assertThat(characterRepository.findByStorybook_Id(bookId)).hasSize(2);
        System.out.printf("     - Persisted characters in DB: %d%n", characterRepository.findByStorybook_Id(bookId).size());

        // 5. Orchestrator Loop: Process jobs until READY, auto-approving Gate 1 & Gate 2
        int maxIterations = 80;
        int iter = 0;
        Storybook currentBook = initialBook;

        System.out.println("  3. Starting Step-by-Step Pipeline Handlers Execution...");
        while (currentBook.getStatus() != StorybookStatus.READY && iter++ < maxIterations) {

            // Gate 1: Story Ready Approval (User bypasses/approves)
            if (currentBook.getStatus() == StorybookStatus.STORY_READY && currentBook.getStoryApprovedAt() == null) {
                System.out.printf("  >>> [APPROVAL GATE 1 TRIGGERED] Auto-approving story draft for book %d...%n", bookId);
                storyApprovalService.approveStory(user, bookId);
                currentBook = storybookRepository.findById(bookId).orElseThrow();
                System.out.printf("      New Status after Gate 1: %s%n", currentBook.getStatus());
            }

            // Gate 2: Character Look Approval (User bypasses/approves)
            if (currentBook.getStatus() == StorybookStatus.CHARACTER_READY && currentBook.getLookApprovedAt() == null) {
                System.out.printf("  >>> [APPROVAL GATE 2 TRIGGERED] Auto-approving character look for book %d...%n", bookId);
                lookService.approve(user, bookId);
                currentBook = storybookRepository.findById(bookId).orElseThrow();
                System.out.printf("      New Status after Gate 2: %s%n", currentBook.getStatus());
            }

            // Human QA check if flagged
            if (currentBook.getStatus() == StorybookStatus.QA) {
                System.out.printf("  >>> [ADMIN QA] Auto-accepting flagged pages for book %d...%n", bookId);
                for (var flagged : adminService.flaggedPages()) {
                    if (flagged.bookId().equals(bookId)) {
                        adminService.accept(flagged.pageId());
                    }
                }
                currentBook = storybookRepository.findById(bookId).orElseThrow();
                System.out.printf("      New Status after Admin QA: %s%n", currentBook.getStatus());
            }

            // Pick up pending jobs using standard claimer
            List<StorybookJob> claimedJobs = claimer.claim("test-worker", 20)
                    .stream()
                    .filter(j -> j.getStorybookId().equals(bookId))
                    .toList();

            if (claimedJobs.isEmpty()) {
                if (currentBook.getStatus() == StorybookStatus.READY || currentBook.getStatus() == StorybookStatus.FAILED) {
                    break;
                }
                Thread.sleep(150);
                currentBook = storybookRepository.findById(bookId).orElseThrow();
                continue;
            }

            for (StorybookJob job : claimedJobs) {
                System.out.printf("     -> Step Handler: %-18s [Page: %2d, Gen: %d] ...%n",
                        job.getStep(), job.getPageIndex(), job.getGeneration());

                StepHandler handler = handlers.get(job.getStep());
                assertThat(handler).as("Handler for step " + job.getStep()).isNotNull();

                long start = System.currentTimeMillis();
                StepOutcome outcome = handler.handle(job);
                long duration = System.currentTimeMillis() - start;

                recorder.record(job.getId(), outcome);
                System.out.printf("        Done in %d ms | Outcome: %s%n", duration, outcome.type());
            }

            currentBook = storybookRepository.findById(bookId).orElseThrow();
        }

        // 6. Assert Final State
        Storybook finalBook = storybookRepository.findById(bookId).orElseThrow();
        System.out.println("================================================================================");
        System.out.printf(">>> PIPELINE COMPLETED! Final Storybook Status: %s%n", finalBook.getStatus());
        System.out.println("================================================================================");

        assertThat(finalBook.getStatus()).isEqualTo(StorybookStatus.READY);
        assertThat(finalBook.getCharacterBible()).isNotBlank();
        assertThat(finalBook.getStoryBlueprint()).isNotBlank();
        assertThat(finalBook.getPdfKey()).isNotBlank();

        // 7. Verify Database Persisted Pages
        List<StorybookPage> pages = pageRepository.findByStorybook_IdOrderByPageIndexAsc(bookId);
        assertThat(pages).hasSize(11); // Cover (page 0) + 10 Story pages (pages 1..10)
        System.out.printf("  ✓ Persisted Story Pages in Database: %d pages%n", pages.size());

        // 8. Verify AI Ledger Cost Tracking
        var aiCalls = aiCallRepository.findByStorybookIdOrderByIdAsc(bookId);
        assertThat(aiCalls).isNotEmpty();
        System.out.printf("  ✓ Total Tracked AI Ledger Calls: %d%n", aiCalls.size());
        for (var call : aiCalls) {
            System.out.printf("     - AI Call: Purpose=%-22s Provider=%-10s Model=%-30s Cost=$%s Latency=%dms%n",
                    call.getPurpose(), call.getProvider(), call.getModel(), call.getCostUsd(), call.getLatencyMs());
        }

        // 9. Fetch, Save, and Verify PDF
        byte[] pdfBytes = assetStore.get(finalBook.getPdfKey());
        assertThat(pdfBytes).isNotEmpty();

        Path outDir = Path.of("target/live-story-output");
        Files.createDirectories(outDir);
        Path pdfPath = outDir.resolve("storybook-" + bookId + "-sami-adventure.pdf");
        Files.write(pdfPath, pdfBytes);

        // Also copy to scratch for easy user access
        Path scratchDir = Path.of("scratch");
        Files.createDirectories(scratchDir);
        Path scratchPdf = scratchDir.resolve("storybook-sami-adventure.pdf");
        Files.write(scratchPdf, pdfBytes);

        System.out.println("================================================================================");
        System.out.printf(">>> PDF GENERATED SUCCESSFULLY!%n");
        System.out.printf("    File Path:  %s%n", pdfPath.toAbsolutePath());
        System.out.printf("    Scratch:    %s%n", scratchPdf.toAbsolutePath());
        System.out.printf("    File Size:  %d bytes (%.2f KB)%n", pdfBytes.length, pdfBytes.length / 1024.0);

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            float width = doc.getPage(0).getMediaBox().getWidth();
            float height = doc.getPage(0).getMediaBox().getHeight();
            System.out.printf("    Page Count: %d pages%n", pageCount);
            System.out.printf("    Page Size:  %.1f pt x %.1f pt (Square 21cm x 21cm)%n", width, height);
            System.out.println("================================================================================");

            assertThat(pageCount).isGreaterThanOrEqualTo(12);
            assertThat(width).isCloseTo(595.3f, org.assertj.core.data.Offset.offset(2.0f));
            assertThat(height).isCloseTo(595.3f, org.assertj.core.data.Offset.offset(2.0f));
        }
    }
}
