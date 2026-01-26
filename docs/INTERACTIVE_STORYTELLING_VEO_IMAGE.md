### Veo scene image generation (standalone module)

This is a **non-integrated** implementation placed under:

- `src/main/java/com/doova/ktab/interactivestorytelling/image`

It contains a minimal HTTP client (`VeoImageClient`) plus prompt helpers (`SceneImagePromptFactory`) to generate an image for a story scene.

### What you get

- **Prompt builder**: turn/scene text → image prompt
- **Request/response types**: `VeoImageRequest`, `VeoImageResult`
- **HTTP client**: `VeoImageClient` (plain Java `HttpClient`)

### Example usage (not wired into Spring)

```java
import com.doova.ktab.interactivestorytelling.image.*;

import java.time.Duration;
import java.util.List;

public class Demo {
  public static void main(String[] args) {
    VeoClientConfig cfg = new VeoClientConfig(
        System.getenv("VEO_BASE_URL"),
        System.getenv("VEO_API_KEY"),
        Duration.ofSeconds(60)
    );

    VeoImageClient client = new VeoImageClient(cfg);

    // If you have Story context available, prefer fromSceneAndStory(story, sceneText, ...)
    String prompt = SceneImagePromptFactory.fromScene(
        "The rain slicks the cobblestones as the guard approaches...",
        "cinematic storybook illustration",
        "English"
    );

    VeoImageRequest req = new VeoImageRequest(
        prompt,
        SceneImagePromptFactory.defaultNegativePrompt(),
        VeoAspectRatio.LANDSCAPE_16_9,
        1280,
        720,
        0,
        1,
        "cinematic",
        List.of("no_gore")
    );

    VeoImageResult res = client.generate(req);
    System.out.println(res);
  }
}
```

### Notes / adaptation points

- `VeoImageClient` currently assumes `POST {baseUrl}/v1/images:generate`.
  - Adjust that path + payload mapping in `VeoImageClient.toProviderPayload(...)` to match your Veo provider endpoint.
- If your provider returns a different response shape, update `parseProviderResponse(...)`.

