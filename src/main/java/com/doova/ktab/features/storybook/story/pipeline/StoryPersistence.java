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
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final EntityManager entityManager;

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
                book.getInputs().toStoryRequest(book.getVariety(), book.getPageCount()), plan);
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
    public void acceptStory(Long bookId, StoryPlanResponse checkedPlan) {
        Storybook book = books.findById(bookId).orElseThrow();
        book.setTitleAr(checkedPlan.titleAr());
        for (PagePlan p : checkedPlan.pages()) {
            StorybookPage page = pages.findByStorybook_IdAndPageIndex(bookId, p.pageNumber()).orElseThrow();
            page.setTextAr(p.textAr());
            page.setSceneEn(p.sceneEn());
            page.setTextZone(p.textZone());
            page.setCriticProblems(null);
        }
        stateMachine.transition(book, StorybookStatus.STORY_READY);
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
