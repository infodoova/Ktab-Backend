package com.doova.ktab.features.storybook.blueprint;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class BlueprintCatalog {

    private final Map<String, Blueprint> latestByKey = new HashMap<>();

    @Autowired
    public BlueprintCatalog(ObjectMapper objectMapper) {
        this(objectMapper, "classpath*:storybook/blueprints/*.json");
    }

    public BlueprintCatalog(ObjectMapper objectMapper, String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            for (Resource resource : resources) {
                Blueprint blueprint;
                try (InputStream in = resource.getInputStream()) {
                    blueprint = objectMapper.readValue(in, Blueprint.class);
                }
                validate(blueprint, resource.getFilename());
                latestByKey.merge(blueprint.key(), blueprint,
                        (a, b) -> a.version() >= b.version() ? a : b);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load storybook blueprints from " + locationPattern, e);
        }
    }

    public Blueprint get(String key) {
        Blueprint blueprint = latestByKey.get(key);
        if (blueprint == null) {
            throw new IllegalArgumentException("Unknown blueprint: " + key);
        }
        return blueprint;
    }

    public List<Blueprint> forAgeBand(AgeBand band) {
        return all().stream().filter(b -> b.ageBands().contains(band)).toList();
    }

    public List<Blueprint> all() {
        return latestByKey.values().stream().sorted(Comparator.comparing(Blueprint::key)).toList();
    }

    private static void validate(Blueprint b, String filename) {
        String expected = b.key() + ".v" + b.version() + ".json";
        if (!expected.equals(filename)) {
            throw new IllegalStateException("Blueprint file " + filename + " must be named " + expected);
        }
        if (b.ageBands() == null || b.ageBands().isEmpty()) {
            throw new IllegalStateException("Blueprint " + b.key() + " has no age bands");
        }
        List<Integer> orders = b.beats().stream().map(BlueprintBeat::order).sorted().toList();
        if (!orders.equals(IntStream.rangeClosed(1, 15).boxed().toList())) {
            throw new IllegalStateException("Blueprint " + b.key() + " must have beat orders 1..15");
        }
        for (BlueprintBeat beat : b.beats()) {
            if (!Blueprint.BEAT_THRESHOLDS.contains(beat.minPageCount())) {
                throw new IllegalStateException("Blueprint " + b.key() + " beat " + beat.order()
                        + " has minPageCount " + beat.minPageCount() + "; use 10, 12 or 15");
            }
        }
        for (int pageCount : List.of(10, 12, 15)) {
            int n = b.beatsFor(pageCount).size();
            if (n != pageCount) {
                throw new IllegalStateException("Blueprint " + b.key() + " yields " + n
                        + " beats for a " + pageCount + "-page book");
            }
        }
    }
}
