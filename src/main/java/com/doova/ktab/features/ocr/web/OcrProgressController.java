package com.doova.ktab.features.ocr.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.utils.response.ResponseUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequiredArgsConstructor
@ApiVersion(value = 1, keepLegacyPath = true)
@RequestMapping(path = "/ocr/progress", produces = "application/json")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
@Tag(name = "OCR Progress API", description = "Endpoints for monitoring batch OCR job execution and book OCR progress.")
public class OcrProgressController {

    private final JobExplorer jobExplorer;
    private final MessageSource messageSource;

    /**
     * Get progress by job execution ID
     */
    @Operation(summary = "Get the progress of an OCR job execution")
    @GetMapping("/job-execution/{executionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> byExecution(@PathVariable Long executionId) {
        JobExecution exec = jobExplorer.getJobExecution(executionId);
        if (exec == null) {
            throw new ResourceNotFoundException(ApiMessageKey.OCR_JOB_NOT_FOUND);
        }
        return ResponseUtils.success(toDto(exec), ApiMessageKey.OCR_PROGRESS_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    /**
     * Get latest OCR execution for a given bookId
     */
    @Operation(summary = "Get the progress of a book's latest OCR job")
    @GetMapping("/book/{bookId}/latest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> latestForBook(@PathVariable Long bookId) {

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
            throw new ResourceNotFoundException(ApiMessageKey.OCR_JOB_NOT_FOUND);
        }

        return ResponseUtils.success(toDto(latest), ApiMessageKey.OCR_PROGRESS_FETCHED.getMessage(messageSource), HttpStatus.OK);
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
