package com.doova.ktab.features.trailer.agent;

import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEventsTest {

    private final ObjectMapper json = new ObjectMapper();
    static final Pattern JOB = Pattern.compile("(?i)\"?(job|request)_?id\"?");
    /** The pattern actually shipped in TrailerProperties.higgsfieldJobPattern's default, not a copy of it. */
    static final Pattern REAL_JOB_PATTERN = Pattern.compile(new TrailerProperties().getHiggsfieldJobPattern());

    private JsonNode ev(String raw) throws Exception {
        return json.readTree(raw);
    }

    private List<JsonNode> events(String... raws) throws Exception {
        List<JsonNode> list = new ArrayList<>();
        for (String r : raws) {
            list.add(ev(r));
        }
        return list;
    }

    @Test
    void videoJobsAreCountedSeparatelyFromImageJobs() throws Exception {
        String pending = "[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[{\\\"id\\\":\\\"j\\\",\\\"status\\\":\\\"pending\\\"}]}\"}]";
        SessionSnapshot s = SessionEvents.interpret("running", events(
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u1\",\"name\":\"generate_image\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u1\",\"content\":" + pending + "}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u2\",\"name\":\"generate_image\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u2\",\"content\":" + pending + "}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u3\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u3\",\"content\":" + pending + "}"),
                List.of(), "generate", REAL_JOB_PATTERN);

        assertThat(s.higgsfieldGenerations()).isEqualTo(3);
        assertThat(s.videoJobs()).isEqualTo(1);
    }

    @Test
    void idleEndTurnWithSatisfiedOutcomeIsFinished() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}"),
                List.of(new SessionEvents.OutcomeResult("satisfied", "All criteria met")), "generate", JOB);

        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.FINISHED);
        assertThat(s.outcomeResult()).isEqualTo("satisfied");
    }

    @Test
    void requiresActionIsReportedNotTreatedAsDone() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"requires_action\"}}"),
                List.of(), "generate", JOB);
        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.NEEDS_ACTION);
    }

    @Test
    void budgetReachedIsItsOwnPhase() throws Exception {
        SessionSnapshot s = SessionEvents.interpret("idle",
                events("{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"end_turn\"}}",
                        "{\"type\":\"session.status_idle\",\"stop_reason\":{\"type\":\"budget_reached\"}}"),
                List.of(), "generate", JOB);
        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.BUDGET_REACHED);
    }

    @Test
    void countsOnlyCallsThatCreatedAJob() throws Exception {
        // Mirrors a real run: a completed job, a concurrency rejection, and a preset suggestion, all from
        // generate_video, plus an unrelated status-poll call and a non-Higgsfield tool call that must be ignored.
        SessionSnapshot s = SessionEvents.interpret("running", events(
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u1\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u1\",\"is_error\":false,"
                        + "\"content\":[{\"type\":\"text\",\"text\":\"{\\\"job_id\\\":\\\"j-1\\\"}\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u2\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u2\",\"is_error\":true,"
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Maximum number of concurrent requests (4) has been reached\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u3\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u3\",\"is_error\":false,"
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Suggested preset: cinematic. Resend with a preset.\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u4\",\"name\":\"get_generation_status\"}",
                "{\"type\":\"agent.tool_use\",\"id\":\"u5\",\"name\":\"bash\"}"),
                List.of(), "generate", JOB);

        assertThat(s.phase()).isEqualTo(SessionSnapshot.Phase.RUNNING);
        assertThat(s.higgsfieldGenerations()).isEqualTo(1); // jobs actually created
        assertThat(s.higgsfieldCalls()).isEqualTo(3);       // every generate_video call
    }

    @Test
    void realHiggsfieldReplyShapesAreClassifiedCorrectlyByTheShippedPattern() throws Exception {
        // Captured 2026-09-29 from a real completed trailer session (sesn_019vVx9aaduimtQtF3vpaC4R): a job-created
        // reply, a preset-recommendation notice (which itself carries an "id" for the *preset*, not a job — the
        // bug the old default pattern had), a 429 rejection, and a get_cost dry-run reply. Only the first is a job.
        SessionSnapshot s = SessionEvents.interpret("running", events(
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u1\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u1\",\"is_error\":false,\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[{\\\"id\\\":\\\"dd21e106-f0da-430a-9ab9\\\","
                        + "\\\"type\\\":\\\"video\\\",\\\"status\\\":\\\"pending\\\",\\\"model\\\":\\\"seedance_2_5\\\"}]}\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u2\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u2\",\"is_error\":false,\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"{\\\"notice\\\":{\\\"type\\\":\\\"preset_recommendation\\\","
                        + "\\\"data\\\":{\\\"preset\\\":{\\\"id\\\":\\\"24bae836-2c4a-48e0-89b6\\\",\\\"name\\\":\\\"IN THE DARK\\\"}}}}\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u3\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u3\",\"is_error\":true,\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"Error starting generation: seedance_2_5 backend request failed "
                        + "(429): rate_limit_reached\"}]}",
                "{\"type\":\"agent.mcp_tool_use\",\"id\":\"u4\",\"name\":\"generate_video\"}",
                "{\"type\":\"agent.mcp_tool_result\",\"mcp_tool_use_id\":\"u4\",\"is_error\":false,\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"{\\\"cost\\\":{\\\"credits\\\":60,\\\"credits_exact\\\":60}}\"}]}"),
                List.of(), "generate", REAL_JOB_PATTERN);

        assertThat(s.higgsfieldGenerations()).isEqualTo(1); // only the real job-created reply
        assertThat(s.higgsfieldCalls()).isEqualTo(4);        // every generate_video call, including the dry-run
    }

    @Test
    void terminatedIsTerminated() throws Exception {
        assertThat(SessionEvents.interpret("terminated", List.of(), List.of(), "generate", JOB).phase())
                .isEqualTo(SessionSnapshot.Phase.TERMINATED);
    }
}
