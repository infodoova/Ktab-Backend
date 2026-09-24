package com.doova.ktab.features.studio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces docs/ocr_engine_v3.md's core independence rule: {@code features.studio} must
 * never import {@code features.ocr}. The two ingestion pipelines are separate Spring Batch
 * jobs with separate config and separate quality gates on purpose — see "Two independent
 * pipelines, one output contract". Only the shared content tables (tbl_book_pages,
 * tbl_book_sections, tbl_books) are the contract between them, not each other's code.
 * <p>
 * No ArchUnit dependency is available in this build (offline environment, not cached), so
 * this is a plain source scan instead of a bytecode-level rule. It catches import statements,
 * which is how every class in this codebase references another package; a fully-qualified
 * in-body reference without an import would slip through, but that is not this codebase's
 * style. If ArchUnit becomes available, replace this with a proper
 * {@code noClasses().that().resideInAPackage("..features.studio..")
 *   .should().dependOnClassesThat().resideInAPackage("..features.ocr..")} rule.
 */
class StudioIndependenceTest {

    private static final String FORBIDDEN_IMPORT_PREFIX = "import com.doova.ktab.features.ocr.";
    private static final Path STUDIO_SOURCE_ROOT =
            Path.of("src", "main", "java", "com", "doova", "ktab", "features", "studio");

    @Test
    void studioPackageNeverImportsOcrPackage() throws IOException {
        if (!Files.isDirectory(STUDIO_SOURCE_ROOT)) {
            fail("Expected " + STUDIO_SOURCE_ROOT + " to exist - has features.studio moved? "
                    + "Update this test's source root if so.");
        }

        List<String> violations;
        try (Stream<Path> files = Files.walk(STUDIO_SOURCE_ROOT)) {
            violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(StudioIndependenceTest::findViolationsIn)
                    .toList();
        }

        if (!violations.isEmpty()) {
            fail("features.studio must not import features.ocr (docs/ocr_engine_v3.md, "
                    + "\"Two independent pipelines\"). Violations:\n" + String.join("\n", violations));
        }
    }

    private static Stream<String> findViolationsIn(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> line.trim().startsWith(FORBIDDEN_IMPORT_PREFIX))
                    .map(line -> file + ": " + line.trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
