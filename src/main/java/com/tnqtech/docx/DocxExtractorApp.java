package com.tnqtech.docx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Command line entry point that converts DOCX files to plain text.
 */
public final class DocxExtractorApp {

    private DocxExtractorApp() {
        // utility class
    }

    public static void main(final String[] args) throws DocxExtractorException {
        System.setProperty("log4j.defaultInitOverride", "true");
        if (args.length != 2) {
            System.err.println("Usage: java -jar docx-extractor.jar <input.docx> <output.txt>");
            System.exit(1);
        }
        final Path inputPath = Path.of(args[0]);
        final Path textOutputPath = Path.of(args[1]);

        final Instant programStart = Instant.now();
        final long cpuStart = getCpuTime();

        final DocxExtractor extractor = new DocxExtractor();
        try {
            final DocxExtractor.ExtractionResult result = extractor.extract(inputPath);
            createParentDirectory(textOutputPath);
            Files.writeString(textOutputPath, result.getPlainText());
            printSummary(result);
        } catch (final IOException ex) {
            throw new DocxExtractorException("Unable to write extraction output", ex);
        }

        final Instant programEnd = Instant.now();
        final long cpuEnd = getCpuTime();
        printPerformance(programStart, programEnd, cpuStart, cpuEnd);
    }

    private static void printSummary(final DocxExtractor.ExtractionResult result) {
        System.out.printf(Locale.ROOT, "Total Nodes: %d%n", result.getNodes().size());
        System.out.printf(Locale.ROOT, "Paragraph Styles: %s%n", result.getParagraphStyles().keySet());
        System.out.printf(Locale.ROOT, "Character Styles: %s%n", result.getCharacterStyles().keySet());
    }

    private static void printPerformance(final Instant start, final Instant end, final long cpuStart, final long cpuEnd) {
        final Duration duration = Duration.between(start, end);
        final long cpuDurationMillis = (cpuEnd - cpuStart) / 1_000_000;
        final long usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println();
        System.out.println("--- Performance Summary ---");
        System.out.printf(Locale.ROOT, "Total Execution Time: %d ms%n", duration.toMillis());
        System.out.printf(Locale.ROOT, "Total CPU Time: %d ms%n", cpuDurationMillis);
        System.out.printf(Locale.ROOT, "Memory Usage: %d MB%n", usedMemoryBytes / (1024 * 1024));
    }

    private static long getCpuTime() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
            return threadMXBean.getCurrentThreadCpuTime();
        }
        return 0L;
    }

    private static void createParentDirectory(final Path path) throws IOException {
        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
