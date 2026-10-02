package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrailerHarvesterTest {

    final BookTrailerRepository trailers = mock(BookTrailerRepository.class);
    final TrailerAgentGateway gateway = mock(TrailerAgentGateway.class);
    final TrailerStore store = mock(TrailerStore.class);
    final MediaProbe probe = mock(MediaProbe.class);
    final TrailerNotifier notifier = mock(TrailerNotifier.class);
    final TrailerHarvester harvester = new TrailerHarvester(trailers, gateway, store, probe, new TrailerProperties(), notifier);
    final BookTrailer t = new BookTrailer();

    static final String SRT = "1\n00:00:01,000 --> 00:00:03,500\nذَهَبَ سامي إلى المدرسة.\n\n";
    static final String QC_OK = "{\"status\":\"ok\",\"duration_seconds\":30.0,"
            + "\"captions\":{\"language\":\"ar\"},\"frame_check\":{\"text_found\":false}}";

    @BeforeEach
    void setUp() {
        t.setId(7L);
        t.setBookId(3L);
        t.setSessionId("sesn_1");
        t.setStatus(TrailerStatus.HARVESTING);
        t.setOutcomeResult("satisfied");
        when(trailers.findById(7L)).thenReturn(Optional.of(t));
        when(trailers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(gateway.outputs("sesn_1")).thenReturn(List.of(
                new TrailerAgentGateway.OutputFile("f1", "trailer.mp4", 9),
                new TrailerAgentGateway.OutputFile("f2", "trailer_clean.mp4", 9),
                new TrailerAgentGateway.OutputFile("f3", "captions_ar.srt", 9),
                new TrailerAgentGateway.OutputFile("f4", "qc_report.json", 9)));
        doAnswer(i -> {
            Path target = i.getArgument(1);
            String id = i.getArgument(0);
            Files.writeString(target, switch (id) { case "f3" -> SRT; case "f4" -> QC_OK; default -> "mp4"; });
            return null;
        }).when(gateway).download(anyString(), any());
    }

    @Test
    void satisfiedOkAndCorrectMediaIsReadyAndStoredInR2() {
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.READY);
        assertThat(t.getVideoKey()).isEqualTo("trailers/3/7/trailer.mp4");
        verify(store).upload(eq("trailers/3/7/trailer.mp4"), any(), eq("video/mp4"));
        verify(store).upload(eq("trailers/3/7/captions_ar.srt"), any(), eq("application/x-subrip"));
        assertThat(t.getCaptionsKey()).isEqualTo("trailers/3/7/captions_ar.srt");
        verify(gateway).archive("sesn_1");
    }

    @Test
    void nonArabicCaptionsAreNeedsReview() {
        // The agent must burn Arabic captions (R4); a report saying otherwise is not READY, even if everything else is fine.
        doAnswer(i -> {
            Path target = i.getArgument(1);
            String id = i.getArgument(0);
            Files.writeString(target, switch (id) {
                case "f3" -> SRT;
                case "f4" -> "{\"status\":\"ok\",\"captions\":{\"language\":\"en\"},\"frame_check\":{\"text_found\":false}}";
                default -> "mp4";
            });
            return null;
        }).when(gateway).download(anyString(), any());
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
        assertThat(t.getError()).contains("Arabic");
    }

    @Test
    void wrongDurationIsNeedsReviewEvenIfTheGraderWasSatisfied() {
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(24.0, 1920, 1080));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
        assertThat(t.getError()).contains("24.0");
    }

    @Test
    void graderNotSatisfiedIsNeedsReview() {
        // Disable auto-promote so the non-satisfied outcome actually routes to NEEDS_REVIEW.
        TrailerProperties strictProps = new TrailerProperties();
        strictProps.setAutoPromoteValidMediaWithoutQc(false);
        TrailerHarvester strictHarvester = new TrailerHarvester(trailers, gateway, store, probe, strictProps, notifier);

        t.setOutcomeResult("max_iterations_reached");
        when(probe.probe(any())).thenReturn(new MediaProbe.Media(30.0, 1920, 1080));

        strictHarvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.NEEDS_REVIEW);
    }

    @Test
    void noTrailerFileFails() {
        when(gateway.outputs("sesn_1")).thenReturn(List.of(new TrailerAgentGateway.OutputFile("f4", "qc_report.json", 9)));

        harvester.harvest(7L);

        assertThat(t.getStatus()).isEqualTo(TrailerStatus.FAILED);
        verify(store, never()).upload(anyString(), any(), anyString());
    }
}
