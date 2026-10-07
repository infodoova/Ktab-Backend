package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StoryPersistence {

    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final com.doova.ktab.features.storybook.repository.StorybookCharacterRepository characters;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public StoryContext load(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        List<StorybookPage> stored = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        StoryPlanResponse plan = null;
        if (!stored.isEmpty()) {
            String coverScene = stored.get(0).getKind() == PageKind.COVER ? stored.get(0).getSceneEn() : book.getCoverSceneEn();
            plan = new StoryPlanResponse(book.getTitleAr(), coverScene, stored.stream()
                    .filter(p -> p.getKind() == PageKind.STORY)
                    .map(p -> new PagePlan(p.getPageIndex(), p.getTextAr(), p.getSceneEn(), p.getCharacters(), p.getTextZone()))
                    .toList());
        }
        return new StoryContext(bookId, book.getStatus(),
                book.getInputs().toStoryRequest(book.getVariety(), book.getPageCount()), plan,
                book.getCharacterBible(), book.getStoryBlueprint(),
                book.getTheme(), book.getStoryTone(), book.getLesson(), book.getStoryIdea(), book.getThingsToAvoid(),
                com.doova.ktab.features.storybook.story.SupportingCast.of(characters.findByStorybook_IdAndKindOrderByIdAsc(
                        bookId, com.doova.ktab.features.storybook.enums.CharacterKind.SUPPORTING)));
    }

    @Transactional
    public void savePlan(Long bookId, StoryPlanResponse plan, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        book.setTitleAr(plan.titleAr());
        book.setCoverSceneEn(plan.coverSceneEn());
        pages.deleteByStorybook_Id(bookId);
        entityManager.flush();

        List<CharacterInScene> coverCast = new ArrayList<>(List.of(new CharacterInScene("CHILD", "happy")));
        if (book.getInputs().companion() != null) {
            coverCast.add(new CharacterInScene("COMPANION", "happy"));
        }
        pages.save(page(book, 0, PageKind.COVER, null, plan.coverSceneEn(), coverCast, TextZone.TOP));
        for (PagePlan p : plan.pages()) {
            pages.save(page(book, p.pageNumber(), PageKind.STORY, p.textAr(), p.sceneEn(),
                    p.characters() == null ? List.of() : p.characters(), p.textZone()));
        }
        enqueuer.enqueue(bookId, JobStep.STORY_CRITIC, -1, generation);
    }

    @Transactional
    public void enqueueCritic(Long bookId, int generation) {
        enqueuer.enqueue(bookId, JobStep.STORY_CRITIC, -1, generation);
    }

    @Transactional
    public void enqueueLanguageCritic(Long bookId, int generation) {
        enqueuer.enqueue(bookId, JobStep.LANGUAGE_CRITIC, -1, generation);
    }

    @Transactional
    public void saveRewrittenPages(Long bookId, StoryPlanResponse updatedPlan) {
        if (updatedPlan != null && updatedPlan.titleAr() != null && !updatedPlan.titleAr().isBlank()) {
            books.findById(bookId).ifPresent(book -> book.setTitleAr(updatedPlan.titleAr()));
        }
        if (updatedPlan != null && updatedPlan.pages() != null) {
            for (PagePlan p : updatedPlan.pages()) {
                pages.findByStorybook_IdAndPageIndex(bookId, p.pageNumber()).ifPresent(page -> {
                    page.setTextAr(p.textAr());
                    page.setSceneEn(p.sceneEn());
                    page.setTextZone(p.textZone());
                });
            }
        }
    }

    /**
     * Keeps what a critic still objected to when the story was accepted anyway, so a human can see it. Each complaint is labelled with
     * its critic; a critic's own earlier complaints are replaced, and complaints from the other critic about a page that was rewritten
     * since are dropped because they describe text that no longer exists.
     */
    @Transactional
    public void recordUnresolvedProblems(Long bookId, String source, Map<Integer, List<String>> problems, java.util.Set<Integer> rewrittenPages) {
        String prefix = source + ": ";
        for (StorybookPage page : pages.findByStorybook_IdOrderByPageIndexAsc(bookId)) {
            int index = page.getPageIndex();
            List<String> kept = new ArrayList<>();
            if (page.getCriticProblems() != null) {
                for (String old : page.getCriticProblems()) {
                    if (!old.startsWith(prefix) && !rewrittenPages.contains(index)) {
                        kept.add(old);
                    }
                }
            }
            for (String problem : problems.getOrDefault(index, List.of())) {
                kept.add(prefix + problem);
            }
            page.setCriticProblems(kept.isEmpty() ? null : kept);
        }
    }

    @Transactional
    public void acceptStory(Long bookId, StoryPlanResponse checkedPlan) {
        Storybook book = books.findById(bookId).orElseThrow();
        book.setTitleAr(checkedPlan.titleAr());
        for (PagePlan p : checkedPlan.pages()) {
            StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, p.pageNumber()).orElseThrow();
            page.setTextAr(p.textAr());
            page.setSceneEn(p.sceneEn());
            page.setTextZone(p.textZone());
        }
        stateMachine.transition(book, StorybookStatus.STORY_READY);
        events.publishEvent(new com.doova.ktab.features.storybook.event.StorybookStoryReadyEvent(bookId));
    }

    @Transactional
    public void restartPlan(Long bookId, int nextGeneration, Map<Integer, List<String>> problems) {
        pages.deleteByStorybook_Id(bookId);
        enqueuer.enqueue(bookId, JobStep.STORY_PLAN, -1, nextGeneration);
    }

    @Transactional
    public void failStory(Long bookId, String reason) {
        stateMachine.fail(books.findById(bookId).orElseThrow(), reason);
    }

    private static StorybookPage page(Storybook book, int index, PageKind kind, String text, String scene,
                                      List<CharacterInScene> cast, TextZone zone) {
        StorybookPage p = new StorybookPage();
        p.setStorybook(book);
        p.setPageIndex(index);
        p.setKind(kind);
        p.setTextAr(text);
        p.setSceneEn(scene);
        p.setCharacters(cast);
        p.setTextZone(zone);
        return p;
    }
}
