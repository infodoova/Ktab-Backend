package com.doova.ktab.features.ocr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ocr/progress")
public class OcrProgressController {

    private final JobExplorer jobExplorer;

    /**
     * Get progress by job execution ID
     */
    @GetMapping("/job-execution/{executionId}")
    public ResponseEntity<Map<String, Object>> byExecution(@PathVariable Long executionId) {
        JobExecution exec = jobExplorer.getJobExecution(executionId);
        if (exec == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(exec));
    }

    /**
     * Get latest OCR execution for a given bookId
     */
    @GetMapping("/book/{bookId}/latest")
    public ResponseEntity<Map<String, Object>> latestForBook(@PathVariable Long bookId) {

        // Spring Batch does NOT index job executions by parameters
        // We scan recent instances instead
        List<JobInstance> instances = jobExplorer.getJobInstances("ocrJob", 0, 50);

        JobExecution latest = null;

        for (JobInstance ji : instances) {
            for (JobExecution e : jobExplorer.getJobExecutions(ji)) {
                JobParameters p = e.getJobParameters();
                Long b = p.getLong("bookId");

                if (b != null && b.equals(bookId)) {
                    if (latest == null || e.getCreateTime().isAfter(latest.getCreateTime())) {
                        latest = e;
                    }
                }
            }
        }

        if (latest == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toDto(latest));
    }

    /**
     * Convert JobExecution → API DTO
     */
    private Map<String, Object> toDto(JobExecution exec) {

        Integer pageCount = exec.getExecutionContext().containsKey("pageCount") ? exec.getExecutionContext().getInt("pageCount") : null;

        long totalRead = 0;
        long totalWrite = 0;
        long totalSkip = 0;

        List<Map<String, Object>> steps = new ArrayList<>();

        for (StepExecution se : exec.getStepExecutions()) {

            totalRead += se.getReadCount();
            totalWrite += se.getWriteCount();
            totalSkip += se.getSkipCount();

            Map<String, Object> stepDto = new LinkedHashMap<>();
            stepDto.put("stepName", se.getStepName());
            stepDto.put("status", se.getStatus().toString());
            stepDto.put("readCount", se.getReadCount());
            stepDto.put("writeCount", se.getWriteCount());
            stepDto.put("skipCount", se.getSkipCount());
            stepDto.put("commitCount", se.getCommitCount());
            stepDto.put("rollbackCount", se.getRollbackCount());
            stepDto.put("startTime", se.getStartTime());
            stepDto.put("endTime", se.getEndTime());

            steps.add(stepDto);
        }

        double percentComplete = 0.0;
        if (pageCount != null && pageCount > 0) {
            percentComplete = Math.min(100.0, (totalWrite * 100.0) / pageCount);
        }

        Instant start = exec.getStartTime() != null ? exec.getStartTime().toInstant(ZoneOffset.UTC) : null;

        Instant end = exec.getEndTime() != null ? exec.getEndTime().toInstant(ZoneOffset.UTC) : null;

        Long elapsedSeconds = (start == null) ? null : Duration.between(start, end != null ? end : Instant.now()).getSeconds();

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("executionId", exec.getId());
        dto.put("jobName", exec.getJobInstance().getJobName());
        dto.put("status", exec.getStatus().toString());
        dto.put("exitStatus", exec.getExitStatus().getExitCode());
        dto.put("createTime", exec.getCreateTime());
        dto.put("startTime", exec.getStartTime());
        dto.put("endTime", exec.getEndTime());
        dto.put("elapsedSeconds", elapsedSeconds);
        dto.put("pageCount", pageCount);
        dto.put("totalRead", totalRead);
        dto.put("totalWrite", totalWrite);
        dto.put("totalSkip", totalSkip);
        dto.put("percentComplete", percentComplete);
        dto.put("steps", steps);

        return dto;
    }
}
