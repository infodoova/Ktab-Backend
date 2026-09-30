package com.doova.ktab.features.storybook.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component("anthropicLlmGateway")
@RequiredArgsConstructor
@Slf4j
public class AnthropicLlmGateway implements LlmGateway {

    private final AnthropicClient storybookAnthropicClient;
    private final StorybookProperties properties;

    @Override
    public <T> LlmCall<T> call(LlmRequest<T> request) {
        String model = properties.getLlm().getModel();

        List<ContentBlockParam> blocks = new ArrayList<>();
        for (LlmImage image : request.images()) {
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                            .data(Base64.getEncoder().encodeToString(image.bytes()))
                            .mediaType("image/png".equals(image.mediaType())
                                    ? Base64ImageSource.MediaType.IMAGE_PNG
                                    : Base64ImageSource.MediaType.IMAGE_JPEG)
                            .build())
                    .build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(request.user()).build()));

        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(request.responseType())
                .build();

        long started = System.nanoTime();
        StructuredMessage<T> message;
        try {
            message = storybookAnthropicClient.messages().create(params);
        } catch (RateLimitException | InternalServerException | AnthropicIoException e) {
            throw new LlmCallFailedException(request.purpose() + " call failed transiently", true, e);
        } catch (RuntimeException e) {
            throw new LlmCallFailedException(request.purpose() + " call failed", false, e);
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new LlmCallFailedException(request.purpose() + " was refused by the model", false, null);
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new LlmCallFailedException(request.purpose() + " hit max_tokens; output truncated", false, null);
        }

        T value = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new LlmCallFailedException(
                        request.purpose() + " returned no structured output", true, null));

        log.debug("storybook llm purpose={} model={} in={} out={} latencyMs={}", request.purpose(), model,
                message.usage().inputTokens(), message.usage().outputTokens(), latencyMs);

        return new LlmCall<>(value, model, message.usage().inputTokens(), message.usage().outputTokens(), latencyMs);
    }
}
