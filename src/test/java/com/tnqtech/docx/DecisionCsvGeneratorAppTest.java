package com.tnqtech.docx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class DecisionCsvGeneratorAppTest {

    @Test
    void sanitizeTextCollapsesWhitespaceAndQuotes() throws Exception {
        final Method sanitizeText = DecisionCsvGeneratorApp.class.getDeclaredMethod("sanitizeText", String.class);
        sanitizeText.setAccessible(true);
        final String input = "  \"European\u00A0Union\"  \r\n";
        final String result = (String) sanitizeText.invoke(null, input);
        assertEquals("European Union", result);
    }

    @Test
    void sanitizeTextPreservesMarkupWhileNormalizingWhitespace() throws Exception {
        final Method sanitizeText = DecisionCsvGeneratorApp.class.getDeclaredMethod("sanitizeText", String.class);
        sanitizeText.setAccessible(true);
        final String input = "<unlink>European\u00A0Union</unlink>.\rAuthor";
        final String result = (String) sanitizeText.invoke(null, input);
        assertEquals("<unlink>European Union</unlink>. Author", result);
    }

    @Test
    void sanitizeFileReferenceRemovesEncodedQuotesAndDuplicateScheme() throws Exception {
        final Method sanitizeFileReference = DecisionCsvGeneratorApp.class.getDeclaredMethod(
            "sanitizeFileReference",
            String.class
        );
        sanitizeFileReference.setAccessible(true);
        final String input = "file:///C:/%22file:///C:/GIT-Personal/new-regression-tool/currentVersion/"
            + "MOLP_17106_tud_ACE.docx.dom%22";
        final String result = (String) sanitizeFileReference.invoke(null, input);
        assertEquals(
            "file:///C:/GIT-Personal/new-regression-tool/currentVersion/MOLP_17106_tud_ACE.docx.dom",
            result
        );
    }

    @Test
    void identicalCsvFilesProduceNoDifferences() throws Exception {
        final Path tempDirectory = Files.createTempDirectory("decision-app-test");
        final Path csvPath = tempDirectory.resolve("sample.csv");
        Files.writeString(
            csvPath,
            "FileName|Rule|Highlight|ParaStyle|CharStyle|Find|Replace|Input|Output|Stage|Status|Suggestion\n"
                + "file1|Rule A|Highlight|Para|Char|Find text|Replace text|Input value|Output value|Stage|Status|Suggestion\n"
                + "file2|Rule B|Highlight|Para|Char|Find text|Replace text|Input value|Output value|Stage|Status|Suggestion\n"
                + "file3|Rule C|Highlight|Para|Char|Find text|Replace text|Input value|Output value|Stage|Status|Suggestion\n"
                + "file4|Rule D|Highlight|Para|Char|Find text|Replace text|Input value|Output value|Stage|Status|Suggestion\n",
            StandardCharsets.UTF_8
        );

        final var constructor = DecisionCsvGeneratorApp.class.getDeclaredConstructor(Path.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        final DecisionCsvGeneratorApp app = constructor.newInstance(tempDirectory, csvPath, false);

        final Method readCsv = DecisionCsvGeneratorApp.class.getDeclaredMethod("readCsv", Path.class);
        readCsv.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<?> records = (List<?>) readCsv.invoke(app, csvPath);

        final Method deduplicate = DecisionCsvGeneratorApp.class.getDeclaredMethod("deduplicateRecords", List.class);
        deduplicate.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<?> uniqueRecords = (List<?>) deduplicate.invoke(null, records);

        final Method compare = DecisionCsvGeneratorApp.class.getDeclaredMethod("compareRecords", List.class, List.class);
        compare.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<?> differences = (List<?>) compare.invoke(app, uniqueRecords, uniqueRecords);

        assertTrue(differences.isEmpty());
    }
}
