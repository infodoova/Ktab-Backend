package com.doova.ktab.features.studio.web;

import com.doova.ktab.features.audiobook.AudiobookLauncher;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.BookAudioChapter;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudioJobControllerTest {

    private final AudiobookLauncher launcher = mock(AudiobookLauncher.class);
    private final BookRepository books = mock(BookRepository.class);
    private final StudioProjectRepository projects = mock(StudioProjectRepository.class);
    private final BookAudioChapterRepository audio = mock(BookAudioChapterRepository.class);
    private final StudioProperties studio = new StudioProperties();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StudioJobController(launcher, books, projects,
                mock(StudioChapterRepository.class), audio, studio, new SimpleMeterRegistry(),
                mock(org.springframework.context.MessageSource.class))).build();
        Book book = new Book();
        book.setHasAudio(false);
        when(books.findById(7L)).thenReturn(Optional.of(book));
        when(launcher.runningFor(7L)).thenReturn(Optional.empty());
        when(launcher.activeJobName()).thenReturn("nativeAudiobookJob");
    }

    @Test
    void startingAnAudiobookLaunchesWhicheverJobTheFlagPicksAndSaysWhich() throws Exception {
        JobExecution exec = mock(JobExecution.class);
        when(exec.getId()).thenReturn(55L);
        when(exec.getStatus()).thenReturn(BatchStatus.STARTING);
        when(launcher.launch(7L)).thenReturn(exec);

        mvc.perform(post("/studio/books/7/audiobook"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.executionId").value(55))
                .andExpect(jsonPath("$.data.pipeline").value("NATIVE"));
    }

    @Test
    void aSecondStartWhileOneIsRunningIs409() throws Exception {
        JobExecution running = mock(JobExecution.class);
        when(running.getId()).thenReturn(9L);
        when(launcher.runningFor(7L)).thenReturn(Optional.of(running));

        mvc.perform(post("/studio/books/7/audiobook")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.executionId").value(9));
        verify(launcher, never()).launch(any());
    }

    @Test
    void statusWhileStudioIsOffListsTheNativeChaptersAndWhatIsRunning() throws Exception {
        BookSection section = new BookSection();
        section.setTitle("الفصل الأول");
        BookAudioChapter c = new BookAudioChapter();
        c.setSortOrder(1);
        c.setBookSection(section);
        c.setDurationMs(5000);
        when(audio.findByBook_IdOrderBySortOrderAsc(7L)).thenReturn(List.of(c));

        mvc.perform(get("/studio/books/7/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pipeline").value("NATIVE"))
                .andExpect(jsonPath("$.data.hasAudio").value(false))
                .andExpect(jsonPath("$.data.running").value(false))
                .andExpect(jsonPath("$.data.chapters[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.chapters[0].title").value("الفصل الأول"))
                .andExpect(jsonPath("$.data.chapters[0].durationMs").value(5000));
    }

    @Test
    void statusWhileStudioIsOnStillShowsTheStudioProject() throws Exception {
        studio.setEnabled(true);
        when(projects.findLiveByBookId(7L)).thenReturn(Optional.empty());

        mvc.perform(get("/studio/books/7/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pipeline").value("STUDIO"))
                .andExpect(jsonPath("$.data.projectExists").value(false));
    }
}
