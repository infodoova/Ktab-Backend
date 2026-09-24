package com.doova.ktab.features.studio.reconciler;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.StudioApiException;
import com.doova.ktab.features.studio.client.dto.StudioProjectResponse;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phase 4.5 Orphan reconciliation.
 *
 * <p>Hourly scheduled job that lists all projects visible under the ElevenLabs API key,
 * left-joins against {@code tbl_studio_projects}, and cleans up:
 * <ul>
 *   <li>Remote projects completely unknown locally (e.g. crashed before project row committed)</li>
 *   <li>Local projects in terminal lifecycle with age &gt; 2h where remote deletion was missed</li>
 * </ul>
 *
 * <p>Increments the {@code studio.orphans.reclaimed} counter whenever a project is reclaimed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StudioOrphanReconciler {

    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioProperties props;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${ktab.studio.orphan-reconciler.cron:0 0 * * * *}")
    public void reconcileOrphans() {
        if (props.getAudiobook().isDryRun()) {
            log.info("studio.reconciler.dryRun — skipping orphan reclamation");
            return;
        }

        log.info("studio.reconciler.start scanning for orphaned Studio projects");
        int reclaimed = 0;

        try {
            List<StudioProjectResponse> remoteProjects = client.listProjects();

            for (StudioProjectResponse remote : remoteProjects) {
                String extProjectId = remote.projectId();
                if (extProjectId == null || extProjectId.isBlank()) continue;

                Optional<StudioProject> localOpt = projectRepository.findByExternalProjectId(extProjectId);

                if (localOpt.isEmpty()) {
                    // Remote project exists at ElevenLabs but unknown in our database
                    log.warn("studio.orphan.found extProjectId={} — not in database, deleting remote", extProjectId);
                    deleteRemote(extProjectId);
                    meterRegistry.counter("studio.orphans.reclaimed", "reason", "unknown_locally").increment();
                    reclaimed++;
                } else {
                    StudioProject local = localOpt.get();
                    if (local.getLifecycle().isTerminal() && local.getProjectDeletedAt() == null) {
                        // Check if it's older than 2 hours
                        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
                        LocalDateTime projectTime = local.getUpdatedAt() != null ? local.getUpdatedAt() : local.getCreatedAt();
                        if (projectTime != null && projectTime.isBefore(cutoff)) {
                            log.warn("studio.orphan.found extProjectId={} — terminal lifecycle {} stale, deleting remote",
                                    extProjectId, local.getLifecycle());
                            deleteRemote(extProjectId);
                            markDeleted(local);
                            meterRegistry.counter("studio.orphans.reclaimed", "reason", "terminal_stale").increment();
                            reclaimed++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("studio.reconciler.error failed during reconciliation: {}", e.getMessage(), e);
        }

        log.info("studio.reconciler.done reclaimedCount={}", reclaimed);
    }

    private void deleteRemote(String externalProjectId) {
        try {
            client.deleteProject(externalProjectId);
        } catch (StudioApiException.Fatal e) {
            if (e.statusCode() == 404) {
                log.info("studio.orphan.alreadyDeleted extProjectId={}", externalProjectId);
            } else {
                log.error("studio.orphan.deleteFailed extProjectId={}: {}", externalProjectId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("studio.orphan.deleteFailed extProjectId={}: {}", externalProjectId, e.getMessage());
        }
    }

    @Transactional
    public void markDeleted(StudioProject project) {
        project.setProjectDeletedAt(Instant.now());
        projectRepository.save(project);
    }
}
