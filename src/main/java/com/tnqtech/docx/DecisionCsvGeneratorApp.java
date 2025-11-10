package com.tnqtech.docx;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Generates a CSV report from a DOM decision XML file after converting the
 * source DOCX file to plain text.
 */
public final class DecisionCsvGeneratorAppOri {

    /** Canonicalize strings for comparison: normalize Unicode spaces to ASCII space,
     *  trim, and collapse multiple spaces. */
    private static String norm(String s) {
        if (s == null) return "";
        // Replace common non-breaking spaces with regular space
        String t = s.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        // Replace any Unicode separator (Z category) with a normal space
        t = t.replaceAll("\\p{Z}", " ");
        // Trim and collapse multiple spaces
        t = t.trim().replaceAll(" +", " ");
        return t;
    }

    private static final List<String> HEADERS = List.of(
        "FileName",
        "Rule",
        "Highlight",
        "ParaStyle",
        "CharStyle",
        "Find",
        "Replace",
        "Input",
        "Output",
        "Stage",
        "Status",
        "Suggestion",
        "InstanceText",
        "BookMarkName"
    );

    private static final String PRE_ACE_SUFFIX = "_preACE.docx";
    private static final String MID_ACE_SUFFIX = "_midACE.docx";
    private static final String DOM_SUFFIX = "_ACE.docx.dom";
    private static final String ALT_DOM_SUFFIX = "_ACE_docx.dom";
    private static final String USAGE_MESSAGE =
        "Usage:\n"
            + "  java -cp <jar> com.tnqtech.docx.DecisionCsvGeneratorApp <input-directory>\n"
            + "      Generate output.csv and expected.csv inside <input-directory>.\n"
            + "  java -cp <jar> com.tnqtech.docx.DecisionCsvGeneratorApp <expected.csv> <input-directory>\n"
            + "      Compare the expected CSV with new results and create a report.\n"
            + "  java -cp <jar> com.tnqtech.docx.DecisionCsvGeneratorApp <expected.csv> <actual.csv> compare\n"
            + "      Compare two CSV files directly and create a report.";
    private static final String INCOMPLETE_GROUP_MESSAGE = "Skipping incomplete file set for prefix '%s'%n";
    private static final String CREATED_CSV_MESSAGE = "Created CSV file: %s%n";
    private static final String CREATED_REPORT_MESSAGE = "Created HTML report: %s%n";
    private static final String CREATED_EXPECTED_MESSAGE = "Created expected CSV file: %s%n";
    private static final String INPUT_DIRECTORY_ERROR = "Input path must be a directory: %s";
    private static final String EXPECTED_FILE_ERROR = "Expected CSV must be a regular file: %s";
    private static final String ACTUAL_FILE_ERROR = "Actual CSV must be a regular file: %s";
    private static final String OUTPUT_FILE_NAME = "output.csv";
    private static final String MID_PROCESS_STAGE = "midprocess";
    private static final String MAIN_PROCESS_STAGE = "mainprocess";
    private static final String REPORT_FILE_NAME = "ACE_regression_report.html";

    private static final char COLUMN_SEPARATOR = '$';

    private static final Comparator<String> STRING_COMPARATOR =
        Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<DecisionRecord> RECORD_COMPARATOR = Comparator
        .comparing(DecisionRecord::fileName, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::rule, STRING_COMPARATOR)
        .thenComparingInt(DecisionRecord::bufferPosition)
        .thenComparing(DecisionRecord::input, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::find, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::stage, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::output, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::highlight, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::paraStyle, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::charStyle, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::replace, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::status, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::suggestion, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::instanceText, STRING_COMPARATOR)
        .thenComparing(DecisionRecord::bookMarkName, STRING_COMPARATOR)
        ;

    private final Path expectedCsvPath;
    private final Path inputDirectory;
    private final boolean generateExpectedOnly;
    private final Path actualCsvPath;
    private final boolean compareOnly;

    private DecisionCsvGeneratorAppOri(
        final Path inputDirectory,
        final Path expectedCsvPath,
        final boolean generateExpectedOnly,
        final Path actualCsvPath,
        final boolean compareOnly
    ) {
        if (!compareOnly && inputDirectory == null) {
            throw new IllegalArgumentException("inputDirectory must not be null when not running a comparison");
        }
        this.expectedCsvPath = expectedCsvPath;
        this.inputDirectory = resolveInputDirectory(inputDirectory, expectedCsvPath, actualCsvPath, compareOnly);
        this.generateExpectedOnly = generateExpectedOnly;
        this.actualCsvPath = actualCsvPath;
        this.compareOnly = compareOnly;
    }

    private DecisionCsvGeneratorAppOri(
        final Path inputDirectory,
        final Path expectedCsvPath,
        final boolean generateExpectedOnly
    ) {
        this(inputDirectory, expectedCsvPath, generateExpectedOnly, null, false);
    }

    private static Path resolveInputDirectory(
        final Path inputDirectory,
        final Path expectedCsvPath,
        final Path actualCsvPath,
        final boolean compareOnly
    ) {
        if (inputDirectory != null) {
            return inputDirectory;
        }
        if (!compareOnly) {
            return null;
        }
        Path candidate = actualCsvPath != null ? actualCsvPath.toAbsolutePath().getParent() : null;
        if (candidate == null && expectedCsvPath != null) {
            candidate = expectedCsvPath.toAbsolutePath().getParent();
        }
        if (candidate == null) {
            candidate = Path.of(".").toAbsolutePath().normalize();
        }
        return candidate;
    }

    public static void main(final String[] args) throws Exception {
    	
        if (args.length == 1) {
            final Path inputDirectory = Path.of(args[0]);
            final Path expectedCsvPath = inputDirectory.resolve("expected.csv");
            final DecisionCsvGeneratorAppOri app =
                new DecisionCsvGeneratorAppOri(inputDirectory, expectedCsvPath, true, null, false);
            app.run();
            return;
        }
        if (args.length == 3) {
            final String action = args[2];
            if (!"compare".equalsIgnoreCase(action)) {
                System.err.println(USAGE_MESSAGE);
                System.exit(1);
            }
            final Path expectedCsvPath = Path.of(args[0]);
            final Path actualCsvPath = Path.of(args[1]);
            final DecisionCsvGeneratorAppOri app = new DecisionCsvGeneratorAppOri(
                null,
                expectedCsvPath,
                false,
                actualCsvPath,
                true
            );
            app.run();
            return;
        }
        if (args.length != 2) {
            System.err.println(USAGE_MESSAGE);
            System.exit(1);
        }
        final DecisionCsvGeneratorAppOri app =
            new DecisionCsvGeneratorAppOri(Path.of(args[1]), Path.of(args[0]), false, null, false);
        app.run();
    }

    private void run()
        throws IOException, ParserConfigurationException, SAXException, DocxExtractorException {
        final Instant overallStart = Instant.now();
        final long cpuStart = getCpuTime();
        final long startingMemory = getUsedMemory();

        if (compareOnly) {
            runCsvComparison(overallStart, cpuStart, startingMemory);
            return;
        }

        if (!Files.isDirectory(inputDirectory)) {
            throw new IOException(String.format(Locale.ROOT, INPUT_DIRECTORY_ERROR, inputDirectory));
        }
        if (!generateExpectedOnly && !Files.isRegularFile(expectedCsvPath)) {
            throw new IOException(String.format(Locale.ROOT, EXPECTED_FILE_ERROR, expectedCsvPath));
        }

        final Map<String, FileGroup> fileGroups = collectFileGroups();
        final List<DecisionRecord> allRecords = new ArrayList<>();
        int incompleteGroups = 0;
        int processedGroups = 0;

        for (final Map.Entry<String, FileGroup> entry : fileGroups.entrySet()) {
            final String prefix = entry.getKey();
            final FileGroup group = entry.getValue();
            if (!group.isComplete()) {
                System.err.printf(Locale.ROOT, INCOMPLETE_GROUP_MESSAGE, prefix);
                incompleteGroups++;
                continue;
            }
            allRecords.addAll(processFileGroup(prefix, group));
            processedGroups++;
        }

        allRecords.sort(RECORD_COMPARATOR);
        final List<DecisionRecord> uniqueRecords = deduplicateRecords(allRecords);

        final Path csvPath = inputDirectory.resolve(OUTPUT_FILE_NAME);
        writeCsv(csvPath, uniqueRecords);
        System.out.printf(Locale.ROOT, CREATED_CSV_MESSAGE, csvPath);

        List<Difference> differences = List.of();
        if (generateExpectedOnly) {
            writeCsv(expectedCsvPath, uniqueRecords);
            System.out.printf(Locale.ROOT, CREATED_EXPECTED_MESSAGE, expectedCsvPath);
        } else {
            final List<DecisionRecord> expectedRecords = readCsv(expectedCsvPath);
            expectedRecords.sort(RECORD_COMPARATOR);
            final List<DecisionRecord> uniqueExpectedRecords = deduplicateRecords(expectedRecords);
            differences = compareRecords(uniqueExpectedRecords, uniqueRecords);
            final Path reportPath = inputDirectory.resolve(REPORT_FILE_NAME);
            writeHtmlReport(reportPath, differences, expectedCsvPath, csvPath);
            System.out.printf(Locale.ROOT, CREATED_REPORT_MESSAGE, reportPath);
        }

        final Instant overallEnd = Instant.now();
        final long cpuEnd = getCpuTime();
        printRunSummary(
            fileGroups.size(),
            processedGroups,
            incompleteGroups,
            uniqueRecords.size(),
            differences,
            overallStart,
            overallEnd,
            cpuStart,
            cpuEnd,
            startingMemory
        );
    }

    private void runCsvComparison(
        final Instant overallStart,
        final long cpuStart,
        final long startingMemory
    ) throws IOException {
        if (!Files.isRegularFile(expectedCsvPath)) {
            throw new IOException(String.format(Locale.ROOT, EXPECTED_FILE_ERROR, expectedCsvPath));
        }
        if (actualCsvPath == null || !Files.isRegularFile(actualCsvPath)) {
            throw new IOException(String.format(Locale.ROOT, ACTUAL_FILE_ERROR, actualCsvPath));
        }

        final List<DecisionRecord> expectedRecords = readCsv(expectedCsvPath);
        expectedRecords.sort(RECORD_COMPARATOR);
        final List<DecisionRecord> uniqueExpectedRecords = deduplicateRecords(expectedRecords);

        final List<DecisionRecord> actualRecords = readCsv(actualCsvPath);
        actualRecords.sort(RECORD_COMPARATOR);
        final List<DecisionRecord> uniqueActualRecords = deduplicateRecords(actualRecords);

        final List<Difference> differences = compareRecords(uniqueExpectedRecords, uniqueActualRecords);

        Path reportDirectory = actualCsvPath.getParent();
        if (reportDirectory == null) {
            reportDirectory = Path.of(".");
        }
        final Path reportPath = reportDirectory.resolve(REPORT_FILE_NAME);
        writeHtmlReport(reportPath, differences, expectedCsvPath, actualCsvPath);
        System.out.printf(Locale.ROOT, CREATED_REPORT_MESSAGE, reportPath);

        final Instant overallEnd = Instant.now();
        final long cpuEnd = getCpuTime();
        printCsvComparisonSummary(
            uniqueActualRecords.size(),
            differences,
            overallStart,
            overallEnd,
            cpuStart,
            cpuEnd,
            startingMemory
        );
    }

    private static Document parseXml(final Path xmlPath)
        throws ParserConfigurationException, IOException, SAXException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            return builder.parse(inputStream);
        }
    }

    private static List<DecisionRecord> extractRecords(
        final Document document,
        final String preAceTextContent,
        final String midAceTextContent,
        final String fileName
    ) {
        final List<DecisionRecord> records = new ArrayList<>();
        final NodeList decisionNodes = document.getElementsByTagName("Decision");
        for (int i = 0; i < decisionNodes.getLength(); i++) {
            final Node node = decisionNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            final Element element = (Element) node;
            final String rule = sanitizeText(getChildText(element, "Rule", true));
            final String highlight = sanitizeText(getChildText(element, "Highlight", true));
            final String paraStyle = sanitizeText(getChildText(element, "ParaStyle", true));
            final String charStyle = sanitizeText(getChildText(element, "CharStyle", true));
            final String find = sanitizeText(getChildText(element, "Find", false));
            final String instanceText = sanitizeText(getChildText(element, "InstanceText", false));
            final String bookMarkName = sanitizeText(getChildText(element, "BookMarkName", false));
            final String replace = sanitizeText(getChildText(element, "Replace", false));
            final String suggestion = stripAllDoubleQuotes(
                sanitizeText(getChildText(element, "Suggestion", false))
            );
            final String status = sanitizeText(
                element.hasAttribute("status") ? element.getAttribute("status").trim() : ""
            );
            final String stageAttribute = sanitizeText(
                element.hasAttribute("stage") ? element.getAttribute("stage").trim() : ""
            );
            final int bufferPosition = parseInteger(getChildText(element, "BufferPosition", true));

            final StageContext stageContext = determineStageContext(stageAttribute, preAceTextContent, midAceTextContent);

            final InputExtraction inputExtraction = deriveInput(stageContext.textContent(), bufferPosition, find);
            final String rawInput = inputExtraction.text();
            final String rawOutput = deriveOutput(inputExtraction, find, replace, bufferPosition);
            final String input = stripAllDoubleQuotes(rawInput);
            final String output = stripAllDoubleQuotes(rawOutput);
            final String normalizedReplace = normalizeReplaceField(replace);
            final DecisionRecord record = new DecisionRecord(
                sanitizeFileReference(fileName),
                rule,
                highlight,
                paraStyle,
                charStyle,
                find,
                normalizedReplace,
                input,
                output,
                stageContext.stageLabel(),
                status,
                suggestion,
                instanceText,
                bookMarkName,
                bufferPosition
            );
            records.add(record);
        }
        return records;
    }

    private List<DecisionRecord> processFileGroup(final String prefix, final FileGroup group)
            throws IOException, ParserConfigurationException, SAXException, DocxExtractorException {
            final Path preAceDocxPath = group.preAceDocxPath;
            final Path midAceDocxPath = group.midAceDocxPath;
            final Path decisionXmlPath = group.decisionXmlPath;

            final Path preAceTextPath = replaceExtension(preAceDocxPath, ".txt");
            final Path midAceTextPath = replaceExtension(midAceDocxPath, ".txt");

            try {
                DocxExtractorApp.main(new String[]{preAceDocxPath.toString(), preAceTextPath.toString()});
            } catch (final DocxExtractorException ex) {
                System.err.printf(Locale.ROOT, "Warning: Failed to extract text from %s: %s%n", preAceDocxPath, ex.getMessage());
                // Create empty file so processing can continue
                Files.writeString(preAceTextPath, "", StandardCharsets.UTF_8);
            } catch (final Exception ex) {
                System.err.printf(Locale.ROOT, "Warning: Error processing %s: %s%n", preAceDocxPath, ex.getMessage());
                Files.writeString(preAceTextPath, "", StandardCharsets.UTF_8);
            }

            try {
                DocxExtractorApp.main(new String[]{midAceDocxPath.toString(), midAceTextPath.toString()});
            } catch (final DocxExtractorException ex) {
                System.err.printf(Locale.ROOT, "Warning: Failed to extract text from %s: %s%n", midAceDocxPath, ex.getMessage());
                Files.writeString(midAceTextPath, "", StandardCharsets.UTF_8);
            } catch (final Exception ex) {
                System.err.printf(Locale.ROOT, "Warning: Error processing %s: %s%n", midAceDocxPath, ex.getMessage());
                Files.writeString(midAceTextPath, "", StandardCharsets.UTF_8);
            }

            final String preAceTextContent = Files.readString(preAceTextPath, StandardCharsets.UTF_8);
            final String midAceTextContent = Files.readString(midAceTextPath, StandardCharsets.UTF_8);
            final Document document = parseXml(decisionXmlPath);
            return extractRecords(document, preAceTextContent, midAceTextContent, prefix);
        }

    private Map<String, FileGroup> collectFileGroups() throws IOException {
        final Map<String, FileGroup> groups = new HashMap<>();
        try (Stream<Path> stream = Files.walk(inputDirectory)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                final String fileName = path.getFileName().toString();
                if (fileName.endsWith(PRE_ACE_SUFFIX)) {
                    final String prefix = createGroupKey(path, PRE_ACE_SUFFIX.length());
                    groups.computeIfAbsent(prefix, key -> new FileGroup()).preAceDocxPath = path;
                } else if (fileName.endsWith(MID_ACE_SUFFIX)) {
                    final String prefix = createGroupKey(path, MID_ACE_SUFFIX.length());
                    groups.computeIfAbsent(prefix, key -> new FileGroup()).midAceDocxPath = path;
                } else if (fileName.endsWith(DOM_SUFFIX) || fileName.endsWith(ALT_DOM_SUFFIX)) {
                    final int suffixLength = fileName.endsWith(DOM_SUFFIX)
                        ? DOM_SUFFIX.length()
                        : ALT_DOM_SUFFIX.length();
                    final String prefix = createGroupKey(path, suffixLength);
                    groups.computeIfAbsent(prefix, key -> new FileGroup()).decisionXmlPath = path;
                }
            });
        }
        return groups;
    }

    private String createGroupKey(final Path path, final int suffixLength) {
        final String fileName = path.getFileName().toString();
        final String prefix = fileName.substring(0, fileName.length() - suffixLength);
        final Path parentDirectory = path.getParent() == null ? inputDirectory : path.getParent();
        final Path relativeParent = inputDirectory.relativize(parentDirectory);
        final Path relativeGroupPath = relativeParent.resolve(prefix);
        final String key = relativeGroupPath.toString();
        return key.replace('\\', '/');
    }

    private static StageContext determineStageContext(
        final String stageAttribute,
        final String preAceTextContent,
        final String midAceTextContent
    ) {
        if (stageAttribute != null && stageAttribute.equalsIgnoreCase(MID_PROCESS_STAGE)) {
            return new StageContext(MID_PROCESS_STAGE, preAceTextContent);
        }
        return new StageContext(MAIN_PROCESS_STAGE, midAceTextContent);
    }

    private static InputExtraction deriveInput(final String text, final int bufferPosition, final String find) {
        if (text == null || text.isEmpty()) {
            return new InputExtraction("", 0);
        }
        final int textLength = text.length();
        final int clampedPosition = Math.min(Math.max(bufferPosition, 0), textLength);
        int start = clampedPosition;
        while (start > 0 && !isSpaceOrNewLine(text.charAt(start - 1))) {
            start--;
        }
        int end = clampedPosition;
        final int expectedLength = find == null ? 0 : Math.max(find.length(), 0);
        if (expectedLength > 0) {
            end = Math.min(clampedPosition + expectedLength, textLength);
        }
        while (end < textLength && !isSpaceOrNewLine(text.charAt(end))) {
            end++;
        }
        if (start < 0) {
            start = 0;
        }
        if (end < start) {
            end = start;
        }
        if (end > textLength) {
            end = textLength;
        }
        final String extracted = text.substring(start, end);
        return new InputExtraction(extracted, start);
    }

    private static String deriveOutput(
        final InputExtraction inputExtraction,
        final String find,
        final String replace,
        final int bufferPosition
    ) {
        if (replace == null || replace.isEmpty() || inputExtraction == null) {
            return "";
        }
        final String input = inputExtraction.text();
        if (find == null || find.isEmpty()) {
            if (input == null) {
                return "";
            }
            final int relativePosition = Math.max(
                0,
                Math.min(bufferPosition - inputExtraction.start(), input.length())
            );
            return new StringBuilder(input).insert(relativePosition, replace).toString();
        }
        if (input == null || input.isEmpty()) {
            return "";
        }
        final int index = input.indexOf(find);
        if (index < 0) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(input, 0, index);
        builder.append(replace);
        builder.append(input.substring(index + find.length()));
        return builder.toString();
    }

    private static String normalizeReplaceField(final String replace) {
        return sanitizeText(replace);
    }

    private static boolean isSpaceOrNewLine(final char ch) {
        return ch == ' ' || ch == '\n' || ch == '\r';
    }

    private record InputExtraction(String text, int start) {
    }

    private static int parseInteger(final String value) {
        if (value == null) {
            return -1;
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (final NumberFormatException ex) {
            return -1;
        }
    }

    private static void writeCsv(final Path csvPath, final List<DecisionRecord> records) throws IOException {
        final StringBuilder builder = new StringBuilder();
        appendRow(builder, HEADERS);
        for (final DecisionRecord record : records) {
            appendRow(builder, List.of(
                record.fileName(),
                record.rule(),
                record.highlight(),
                record.paraStyle(),
                record.charStyle(),
                record.find(),
                record.replace(),
                record.input(),
                record.output(),
                record.stage(),
                record.status(),
                record.suggestion(),
                record.instanceText(),
                record.bookMarkName()
            ));
        }
        Files.writeString(csvPath, builder.toString(), StandardCharsets.UTF_8);
    }

    private static List<DecisionRecord> deduplicateRecords(final List<DecisionRecord> records) {
        final Map<RecordIdentity, DecisionRecord> unique = new LinkedHashMap<>();
        for (final DecisionRecord record : records) {
            final RecordIdentity identity = new RecordIdentity(
                normalizeKeyPart(record.fileName()),
                normalizeKeyPart(record.rule()),
                normalizeKeyPart(record.highlight()),
                normalizeKeyPart(record.paraStyle()),
                normalizeKeyPart(record.charStyle()),
                normalizeKeyPart(record.find()),
                normalizeKeyPart(record.replace()),
                normalizeKeyPart(record.instanceText()),
                normalizeKeyPart(record.bookMarkName()),
                normalizeKeyPart(record.input()),
                normalizeKeyPart(record.output()),
                normalizeKeyPart(record.stage()),
                normalizeKeyPart(record.status()),
                normalizeKeyPart(record.suggestion())
            );
            unique.putIfAbsent(identity, record);
        }
        return new ArrayList<>(unique.values());
    }

    private static void appendRow(final StringBuilder builder, final List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(COLUMN_SEPARATOR);
            }
            builder.append(toCsvValue(values.get(i)));
        }
        builder.append(System.lineSeparator());
    }

    private static String toCsvValue(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final String withoutQuotes = value.replace('"', ' ');
        final StringBuilder sanitized = new StringBuilder(withoutQuotes.length());
        for (int i = 0; i < withoutQuotes.length(); i++) {
            final char ch = withoutQuotes.charAt(i);
            if (ch == '\r' || ch == '\n') {
                sanitized.append(' ');
            } else {
                sanitized.append(ch);
            }
        }
        return sanitized.toString();
    }

    private static String getChildText(final Element parent, final String tagName, final boolean trim) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                final String value = child.getTextContent();
                if (value == null) {
                    return "";
                }
                return trim ? value.trim() : value;
            }
        }
        return "";
    }

    private List<DecisionRecord> readCsv(final Path csvPath) throws IOException {
        final String content = Files.readString(csvPath, StandardCharsets.UTF_8);
        final List<String> rows = splitCsvRows(content);
        final List<DecisionRecord> records = new ArrayList<>();
        boolean headerSkipped = false;
        for (final String row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            if (!headerSkipped && row.contains("FileName")) {
                headerSkipped = true;
                continue;
            }
            final List<String> values = parseCsvLine(row);
            if (values.size() < HEADERS.size()) {
                continue;
            }
            records.add(new DecisionRecord(
                sanitizeFileReference(values.get(0)),
                sanitizeText(values.get(1)),
                sanitizeText(values.get(2)),
                sanitizeText(values.get(3)),
                sanitizeText(values.get(4)),
                sanitizeText(values.get(5)),
                sanitizeText(values.get(6)),
                stripAllDoubleQuotes(sanitizeText(values.get(7))),
                stripAllDoubleQuotes(sanitizeText(values.get(8))),
                sanitizeText(values.get(9)),
                sanitizeText(values.get(10)),
                stripAllDoubleQuotes(sanitizeText(values.get(11))),
                sanitizeText(values.get(12)),
                sanitizeText(values.get(13)),
                -1
            ));
        }
        return records;
    }

    private static List<String> splitCsvRows(final String content) {
        final List<String> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            final char ch = content.charAt(i);
            if (ch == '\"') {
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if ((ch == '\n' || ch == '\r') && !inQuotes) {
                rows.add(current.toString());
                current.setLength(0);
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            rows.add(current.toString());
        }
        return rows;
    }

    private static List<String> parseCsvLine(final String line) {
        final List<String> values = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            final char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    builder.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == COLUMN_SEPARATOR && !inQuotes) {
                values.add(builder.toString());
                builder.setLength(0);
            } else {
                builder.append(ch);
            }
        }
        values.add(builder.toString());
        return values;
    }

    private List<Difference> compareRecords(
        final List<DecisionRecord> expectedRecords,
        final List<DecisionRecord> actualRecords
    ) {
        final Map<ComparisonKey, List<DecisionRecord>> expectedGrouped = groupRecords(expectedRecords);
        final Map<ComparisonKey, List<DecisionRecord>> actualGrouped = groupRecords(actualRecords);
        final Set<ComparisonKey> keySet = new HashSet<>();
        keySet.addAll(expectedGrouped.keySet());
        keySet.addAll(actualGrouped.keySet());

        final List<ComparisonKey> sortedKeys = new ArrayList<>(keySet);
        sortedKeys.sort(Comparator
            .comparing(ComparisonKey::fileName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(ComparisonKey::rule, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(ComparisonKey::input, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));

        final List<Difference> differences = new ArrayList<>();
        for (final ComparisonKey key : sortedKeys) {
            final List<DecisionRecord> expectedGroup = new ArrayList<>(
                expectedGrouped.getOrDefault(key, List.of())
            );
            final List<DecisionRecord> actualGroup = new ArrayList<>(
                actualGrouped.getOrDefault(key, List.of())
            );

            final int pairedCount = Math.min(expectedGroup.size(), actualGroup.size());
            for (int i = 0; i < pairedCount; i++) {
                final DecisionRecord expected = expectedGroup.get(i);
                final DecisionRecord actual = actualGroup.get(i);
                if (recordsEqual(expected, actual)) {
                    continue;
                }
                final List<FieldChange> changes = determineFieldChanges(expected, actual);
                final Severity severity = determineSeverity(
                    expected,
                    actual,
                    DifferenceType.MODIFIED,
                    changes
                );
                differences.add(
                    new Difference(
                        key.rule(),
                        actual.fileName(),
                        DifferenceType.MODIFIED,
                        severity,
                        expected,
                        actual,
                        changes
                    )
                );
            }

            if (expectedGroup.size() > pairedCount) {
                for (int i = pairedCount; i < expectedGroup.size(); i++) {
                    final DecisionRecord expected = expectedGroup.get(i);
                    differences.add(
                        new Difference(
                            key.rule(),
                            expected.fileName(),
                            DifferenceType.MISSING,
                            Severity.CRITICAL,
                            expected,
                            null,
                            List.of()
                        )
                    );
                }
            }

            if (actualGroup.size() > pairedCount) {
                for (int i = pairedCount; i < actualGroup.size(); i++) {
                    final DecisionRecord actual = actualGroup.get(i);
                    differences.add(
                        new Difference(
                            key.rule(),
                            actual.fileName(),
                            DifferenceType.NEW,
                            Severity.CRITICAL,
                            null,
                            actual,
                            List.of()
                        )
                    );
                }
            }
        }
        differences.sort(Comparator
            .comparing(Difference::fileName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Difference::rule, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Difference::type)
            .thenComparing(Difference::severity));
        return differences;
    }

    private Map<ComparisonKey, List<DecisionRecord>> groupRecords(final List<DecisionRecord> records) {
        final Map<ComparisonKey, List<DecisionRecord>> grouped = new HashMap<>();
        for (final DecisionRecord record : records) {
            grouped.computeIfAbsent(createKey(record), key -> new ArrayList<>()).add(record);
        }
        for (final List<DecisionRecord> group : grouped.values()) {
            group.sort(RECORD_COMPARATOR);
        }
        return grouped;
    }

    private static ComparisonKey createKey(final DecisionRecord record) {
        return new ComparisonKey(
            normalizeKeyPart(record.fileName()),
            normalizeKeyPart(record.rule()),
            normalizeKeyPart(record.input())
        );
    }

    private static String normalizeKeyPart(final String value) {
        if (value == null) {
            return "";
        }
        // Canonicalize spaces and case for stable keying
        String t = value.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ');
        t = t.replaceAll("\\p{Z}", " ");
        t = t.trim().replaceAll(" +", " ");
        return t.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean recordsEqual(final DecisionRecord expected, final DecisionRecord actual) {
        if (expected == actual) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        return valuesEqual(expected.highlight(), actual.highlight())
            && valuesEqual(expected.paraStyle(), actual.paraStyle())
            && valuesEqual(expected.charStyle(), actual.charStyle())
            && valuesEqual(expected.find(), actual.find())
            && valuesEqual(expected.replace(), actual.replace())
            && valuesEqual(expected.instanceText(), actual.instanceText())
            && valuesEqual(expected.bookMarkName(), actual.bookMarkName())
            && valuesEqual(expected.input(), actual.input())
            && valuesEqual(expected.output(), actual.output())
            && valuesEqual(expected.stage(), actual.stage())
            && valuesEqual(expected.status(), actual.status())
            && valuesEqual(expected.suggestion(), actual.suggestion());
    }

    private static List<FieldChange> determineFieldChanges(
        final DecisionRecord expected,
        final DecisionRecord actual
    ) {
        final List<FieldChange> changes = new ArrayList<>();
        addChange("Highlight", expected.highlight(), actual.highlight(), changes);
        addChange("ParaStyle", expected.paraStyle(), actual.paraStyle(), changes);
        addChange("CharStyle", expected.charStyle(), actual.charStyle(), changes);
        addChange("Find", expected.find(), actual.find(), changes);
        addChange("Replace", expected.replace(), actual.replace(), changes);
        addChange("InstanceText", expected.instanceText(), actual.instanceText(), changes);
        addChange("BookMarkName", expected.bookMarkName(), actual.bookMarkName(), changes);
        addChange("Input", expected.input(), actual.input(), changes);
        addChange("Output", expected.output(), actual.output(), changes);
        addChange("Stage", expected.stage(), actual.stage(), changes);
        addChange("Status", expected.status(), actual.status(), changes);
        addChange("Suggestion", expected.suggestion(), actual.suggestion(), changes);
        return changes;
    }

    private static void addChange(
        final String field,
        final String expected,
        final String actual,
        final List<FieldChange> accumulator
    ) {
        if (!valuesEqual(expected, actual)) {
            accumulator.add(new FieldChange(field, expected, actual));
        }
    }

    private static Severity determineSeverity(
        final DecisionRecord expected,
        final DecisionRecord actual,
        final DifferenceType type,
        final List<FieldChange> changes
    ) {
        if (type != DifferenceType.MODIFIED) {
            return Severity.CRITICAL;
        }
        final boolean inputChanged = !valuesEqual(expected.input(), actual.input());
        final boolean outputChanged = !valuesEqual(expected.output(), actual.output());
        if (inputChanged || outputChanged) {
            return Severity.CRITICAL;
        }
        if (!valuesEqual(expected.highlight(), actual.highlight())
            || !valuesEqual(expected.status(), actual.status())
            || !valuesEqual(expected.suggestion(), actual.suggestion())) {
            return Severity.MODERATE;
        }
        return Severity.MODERATE;
    }

    private static long getCpuTime() {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
            return threadMXBean.getCurrentThreadCpuTime();
        }
        return 0L;
    }

    private static long getUsedMemory() {
        final Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void printRunSummary(
        final int discoveredGroups,
        final int processedGroups,
        final int incompleteGroups,
        final int recordCount,
        final List<Difference> differences,
        final Instant start,
        final Instant end,
        final long cpuStart,
        final long cpuEnd,
        final long startingMemory
    ) {
        final Duration duration = Duration.between(start, end);
        final long cpuDurationMillis = (cpuEnd - cpuStart) / 1_000_000L;
        final long endingMemory = getUsedMemory();
        final long memoryDeltaBytes = Math.max(0L, endingMemory - startingMemory);

        int newCount = 0;
        int missingCount = 0;
        int modifiedCount = 0;
        for (final Difference difference : differences) {
            if (difference.type() == DifferenceType.NEW) {
                newCount++;
            } else if (difference.type() == DifferenceType.MISSING) {
                missingCount++;
            } else {
                modifiedCount++;
            }
        }

        System.out.println();
        System.out.println("--- Run Summary ---");
        System.out.printf(Locale.ROOT, "File groups discovered: %d%n", discoveredGroups);
        System.out.printf(Locale.ROOT, "File groups processed: %d%n", processedGroups);
        System.out.printf(Locale.ROOT, "File groups skipped (incomplete): %d%n", incompleteGroups);
        System.out.printf(Locale.ROOT, "Records processed: %d%n", recordCount);
        if (!differences.isEmpty()) {
            System.out.printf(Locale.ROOT, "Differences - New: %d, Missing: %d, Modified: %d%n", newCount, missingCount, modifiedCount);
        }

        System.out.println();
        System.out.println("--- Performance Summary ---");
        System.out.printf(Locale.ROOT, "Wall-clock time: %d ms%n", duration.toMillis());
        System.out.printf(Locale.ROOT, "CPU time: %d ms%n", cpuDurationMillis);
        System.out.printf(Locale.ROOT, "Additional memory used: %d MB%n", memoryDeltaBytes / (1024 * 1024));
    }


    private static void printCsvComparisonSummary(
        final int recordCount,
        final List<Difference> differences,
        final Instant start,
        final Instant end,
        final long cpuStart,
        final long cpuEnd,
        final long startingMemory
    ) {
        final Duration duration = Duration.between(start, end);
        final long cpuDurationMillis = (cpuEnd - cpuStart) / 1_000_000L;
        final long endingMemory = getUsedMemory();
        final long memoryDeltaBytes = Math.max(0L, endingMemory - startingMemory);

        int newCount = 0;
        int missingCount = 0;
        int modifiedCount = 0;
        for (final Difference difference : differences) {
            if (difference.type() == DifferenceType.NEW) {
                newCount++;
            } else if (difference.type() == DifferenceType.MISSING) {
                missingCount++;
            } else {
                modifiedCount++;
            }
        }

        System.out.println();
        System.out.println("--- Comparison Summary ---");
        System.out.printf(Locale.ROOT, "Records processed: %d%n", recordCount);
        System.out.printf(
            Locale.ROOT,
            "Differences - New: %d, Missing: %d, Modified: %d%n",
            newCount,
            missingCount,
            modifiedCount
        );

        System.out.println();
        System.out.println("--- Performance Summary ---");
        System.out.printf(Locale.ROOT, "Wall-clock time: %d ms%n", duration.toMillis());
        System.out.printf(Locale.ROOT, "CPU time: %d ms%n", cpuDurationMillis);
        System.out.printf(Locale.ROOT, "Additional memory used: %d MB%n", memoryDeltaBytes / (1024 * 1024));
    }


    private void writeHtmlReport(
        final Path reportPath,
        final List<Difference> differences,
        final Path expectedCsv,
        final Path actualCsv
    ) throws IOException {
        final StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>ACE Regression Report</title>\n");
        html.append("  <style>\n");
        html.append("    :root {\n");
        html.append("      color-scheme: light;\n");
        html.append("    }\n");
        html.append("    * {\n");
        html.append("      box-sizing: border-box;\n");
        html.append("    }\n");
        html.append("    body {\n");
        html.append("      margin: 0;\n");
        html.append("      font-family: 'Segoe UI', Tahoma, sans-serif;\n");
        html.append("      background: #eef2f7;\n");
        html.append("      color: #1f2933;\n");
        html.append("      font-size: 14px;\n");
        html.append("      line-height: 1.5;\n");
        html.append("    }\n");
        html.append("    code {\n");
        html.append("      background: rgba(37, 99, 235, 0.12);\n");
        html.append("      color: #1d4ed8;\n");
        html.append("      padding: 1px 4px;\n");
        html.append("      border-radius: 4px;\n");
        html.append("      font-size: 0.85rem;\n");
        html.append("    }\n");
        html.append("    .page {\n");
        html.append("      max-width: 1180px;\n");
        html.append("      margin: 0 auto;\n");
        html.append("      padding: 32px 20px 64px;\n");
        html.append("    }\n");
        html.append("    .page-header {\n");
        html.append("      display: flex;\n");
        html.append("      flex-direction: column;\n");
        html.append("      gap: 6px;\n");
        html.append("      margin-bottom: 28px;\n");
        html.append("    }\n");
        html.append("    .page-header h1 {\n");
        html.append("      margin: 0;\n");
        html.append("      font-size: 1.8rem;\n");
        html.append("      font-weight: 600;\n");
        html.append("      color: #0f172a;\n");
        html.append("    }\n");
        html.append("    .page-header p {\n");
        html.append("      margin: 0;\n");
        html.append("      color: #475569;\n");
        html.append("      font-size: 0.95rem;\n");
        html.append("    }\n");
        html.append("    .stat-grid {\n");
        html.append("      display: grid;\n");
        html.append("      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));\n");
        html.append("      gap: 16px;\n");
        html.append("      margin-bottom: 28px;\n");
        html.append("    }\n");
        html.append("    .stat-card {\n");
        html.append("      background: #ffffff;\n");
        html.append("      border: 1px solid #dbe5f3;\n");
        html.append("      border-radius: 14px;\n");
        html.append("      padding: 18px 20px;\n");
        html.append("      box-shadow: 0 16px 32px rgba(15, 23, 42, 0.08);\n");
        html.append("      display: flex;\n");
        html.append("      flex-direction: column;\n");
        html.append("      gap: 12px;\n");
        html.append("    }\n");
        html.append("    .stat-card.impacted {\n");
        html.append("      grid-column: 1 / -1;\n");
        html.append("    }\n");
        html.append("    .stat-label {\n");
        html.append("      font-size: 0.72rem;\n");
        html.append("      text-transform: uppercase;\n");
        html.append("      letter-spacing: 0.09em;\n");
        html.append("      color: #64748b;\n");
        html.append("    }\n");
        html.append("    .stat-value {\n");
        html.append("      font-size: 1.9rem;\n");
        html.append("      font-weight: 600;\n");
        html.append("      color: #1d4ed8;\n");
        html.append("    }\n");
        html.append("    .impacted-list {\n");
        html.append("      list-style: none;\n");
        html.append("      margin: 0;\n");
        html.append("      padding: 0;\n");
        html.append("      display: flex;\n");
        html.append("      flex-wrap: wrap;\n");
        html.append("      gap: 10px;\n");
        html.append("    }\n");
        html.append("    .impacted-pill {\n");
        html.append("      padding: 6px 12px;\n");
        html.append("      border-radius: 999px;\n");
        html.append("      background: #e0f2fe;\n");
        html.append("      color: #0369a1;\n");
        html.append("      font-size: 0.8rem;\n");
        html.append("      font-weight: 600;\n");
        html.append("    }\n");
        html.append("    .impacted-pill.empty {\n");
        html.append("      background: #e2e8f0;\n");
        html.append("      color: #475569;\n");
        html.append("    }\n");
        html.append("    .no-differences {\n");
        html.append("      margin-top: 16px;\n");
        html.append("      padding: 20px;\n");
        html.append("      border-radius: 14px;\n");
        html.append("      background: #ecfdf5;\n");
        html.append("      border: 1px solid #bbf7d0;\n");
        html.append("      color: #047857;\n");
        html.append("      font-size: 1rem;\n");
        html.append("      box-shadow: 0 16px 30px rgba(14, 159, 110, 0.15);\n");
        html.append("    }\n");
        html.append("    .rules {\n");
        html.append("      display: flex;\n");
        html.append("      flex-direction: column;\n");
        html.append("      gap: 18px;\n");
        html.append("    }\n");
        html.append("    .rule {\n");
        html.append("      background: #ffffff;\n");
        html.append("      border: 1px solid #dbe5f3;\n");
        html.append("      border-radius: 14px;\n");
        html.append("      box-shadow: 0 18px 36px rgba(15, 23, 42, 0.07);\n");
        html.append("      overflow: hidden;\n");
        html.append("    }\n");
        html.append("    .rule summary {\n");
        html.append("      list-style: none;\n");
        html.append("      display: flex;\n");
        html.append("      flex-wrap: wrap;\n");
        html.append("      align-items: center;\n");
        html.append("      gap: 12px;\n");
        html.append("      padding: 20px;\n");
        html.append("      cursor: pointer;\n");
        html.append("      user-select: none;\n");
        html.append("    }\n");
        html.append("    .rule summary::-webkit-details-marker {\n");
        html.append("      display: none;\n");
        html.append("    }\n");
        html.append("    .rule-name {\n");
        html.append("      font-size: 1.05rem;\n");
        html.append("      font-weight: 600;\n");
        html.append("      color: #111827;\n");
        html.append("      flex: 1 1 auto;\n");
        html.append("    }\n");
        html.append("    .rule-counts {\n");
        html.append("      display: flex;\n");
        html.append("      flex-wrap: wrap;\n");
        html.append("      gap: 8px;\n");
        html.append("    }\n");
        html.append("    .count-chip {\n");
        html.append("      padding: 4px 10px;\n");
        html.append("      border-radius: 999px;\n");
        html.append("      background: #eef2ff;\n");
        html.append("      color: #3730a3;\n");
        html.append("      font-size: 0.75rem;\n");
        html.append("      font-weight: 600;\n");
        html.append("      letter-spacing: 0.03em;\n");
        html.append("    }\n");
        html.append("    .count-chip[data-count=\"0\"] {\n");
        html.append("      opacity: 0.55;\n");
        html.append("    }\n");
        html.append("    .chevron {\n");
        html.append("      width: 12px;\n");
        html.append("      height: 12px;\n");
        html.append("      border-right: 2px solid #475569;\n");
        html.append("      border-bottom: 2px solid #475569;\n");
        html.append("      transform: rotate(-45deg);\n");
        html.append("      transition: transform 0.2s ease;\n");
        html.append("    }\n");
        html.append("    .rule[open] .chevron {\n");
        html.append("      transform: rotate(45deg);\n");
        html.append("    }\n");
        html.append("    .rule-content {\n");
        html.append("      padding: 0 20px 20px;\n");
        html.append("    }\n");
        html.append("    .diff-table {\n");
        html.append("      width: 100%;\n");
        html.append("      border-collapse: collapse;\n");
        html.append("    }\n");
        html.append("    .diff-table th, .diff-table td {\n");
        html.append("      padding: 12px 14px;\n");
        html.append("      border-bottom: 1px solid #e2e8f0;\n");
        html.append("      vertical-align: top;\n");
        html.append("      text-align: left;\n");
        html.append("    }\n");
        html.append("    .diff-table thead th {\n");
        html.append("      background: #f8fafc;\n");
        html.append("      font-size: 0.72rem;\n");
        html.append("      letter-spacing: 0.08em;\n");
        html.append("      text-transform: uppercase;\n");
        html.append("      color: #64748b;\n");
        html.append("      border-bottom: 1px solid #dbe5f3;\n");
        html.append("    }\n");
        html.append("    .diff-table tbody tr:last-child td {\n");
        html.append("      border-bottom: none;\n");
        html.append("    }\n");
        html.append("    .type-label {\n");
        html.append("      display: inline-flex;\n");
        html.append("      align-items: center;\n");
        html.append("      padding: 4px 10px;\n");
        html.append("      border-radius: 999px;\n");
        html.append("      font-weight: 600;\n");
        html.append("      font-size: 0.75rem;\n");
        html.append("    }\n");
        html.append("    .type-label[data-type=\"new\"] {\n");
        html.append("      background: #dcfce7;\n");
        html.append("      color: #166534;\n");
        html.append("    }\n");
        html.append("    .type-label[data-type=\"missing\"] {\n");
        html.append("      background: #fee2e2;\n");
        html.append("      color: #b91c1c;\n");
        html.append("    }\n");
        html.append("    .type-label[data-type=\"modified\"] {\n");
        html.append("      background: #e0e7ff;\n");
        html.append("      color: #312e81;\n");
        html.append("    }\n");
        html.append("    .badge {\n");
        html.append("      display: inline-flex;\n");
        html.append("      align-items: center;\n");
        html.append("      padding: 4px 10px;\n");
        html.append("      border-radius: 999px;\n");
        html.append("      font-weight: 600;\n");
        html.append("      font-size: 0.75rem;\n");
        html.append("      letter-spacing: 0.02em;\n");
        html.append("    }\n");
        html.append("    .badge-critical {\n");
        html.append("      background: #fee2e2;\n");
        html.append("      color: #b91c1c;\n");
        html.append("    }\n");
        html.append("    .badge-moderate {\n");
        html.append("      background: #fef3c7;\n");
        html.append("      color: #92400e;\n");
        html.append("    }\n");
        html.append("    .details {\n");
        html.append("      font-size: 0.9rem;\n");
        html.append("      color: #1f2937;\n");
        html.append("    }\n");
        html.append("    .details ul {\n");
        html.append("      margin: 8px 0 0;\n");
        html.append("      padding-left: 20px;\n");
        html.append("    }\n");
        html.append("    .details li {\n");
        html.append("      margin: 4px 0;\n");
        html.append("    }\n");
        html.append("    .details p {\n");
        html.append("      margin: 6px 0 0;\n");
        html.append("    }\n");
        html.append("    .file-links {\n");
        html.append("      display: flex;\n");
        html.append("      flex-direction: column;\n");
        html.append("      gap: 6px;\n");
        html.append("      word-break: break-word;\n");
        html.append("    }\n");
        html.append("    .file-link {\n");
        html.append("      color: #2563eb;\n");
        html.append("      text-decoration: none;\n");
        html.append("      font-weight: 600;\n");
        html.append("    }\n");
        html.append("    .file-link:hover {\n");
        html.append("      text-decoration: underline;\n");
        html.append("    }\n");
        html.append("    @media (max-width: 768px) {\n");
        html.append("      .rule summary {\n");
        html.append("        flex-direction: column;\n");
        html.append("        align-items: flex-start;\n");
        html.append("      }\n");
        html.append("      .rule-counts {\n");
        html.append("        width: 100%;\n");
        html.append("      }\n");
        html.append("      .chevron {\n");
        html.append("        align-self: flex-end;\n");
        html.append("      }\n");
        html.append("      .diff-table th, .diff-table td {\n");
        html.append("        padding: 10px;\n");
        html.append("      }\n");
        html.append("    }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <main class=\"page\">\n");
        html.append("    <header class=\"page-header\">\n");
        html.append("      <h1>ACE Regression Report</h1>\n");
        html.append("      <p>Compared expected file <code>")
            .append(escapeHtml(expectedCsv.toString()))
            .append("</code> with generated file <code>")
            .append(escapeHtml(actualCsv.toString()))
            .append("</code>.</p>\n");
        html.append("    </header>\n");

        if (differences.isEmpty()) {
            html.append("    <div class=\"no-differences\">No differences detected.</div>\n");
        } else {
            final int totalDifferences = differences.size();
            int newCount = 0;
            int missingCount = 0;
            int modifiedCount = 0;
            final LinkedHashSet<String> impactedRules = new LinkedHashSet<>();
            for (final Difference difference : differences) {
                if (difference.type() == DifferenceType.NEW) {
                    newCount++;
                } else if (difference.type() == DifferenceType.MISSING) {
                    missingCount++;
                } else {
                    modifiedCount++;
                }
                final String ruleName = difference.rule();
                if (ruleName != null && !ruleName.isBlank()) {
                    impactedRules.add(ruleName);
                }
            }

            html.append("    <section class=\"stat-grid\">\n");
            html.append("      <article class=\"stat-card\">\n");
            html.append("        <span class=\"stat-label\">Total Differences</span>\n");
            html.append("        <span class=\"stat-value\" data-summary-count=\"total\">")
                .append(totalDifferences)
                .append("</span>\n");
            html.append("      </article>\n");
            html.append("      <article class=\"stat-card\">\n");
            html.append("        <span class=\"stat-label\">New</span>\n");
            html.append("        <span class=\"stat-value\" data-summary-count=\"new\">")
                .append(newCount)
                .append("</span>\n");
            html.append("      </article>\n");
            html.append("      <article class=\"stat-card\">\n");
            html.append("        <span class=\"stat-label\">Missing</span>\n");
            html.append("        <span class=\"stat-value\" data-summary-count=\"missing\">")
                .append(missingCount)
                .append("</span>\n");
            html.append("      </article>\n");
            html.append("      <article class=\"stat-card\">\n");
            html.append("        <span class=\"stat-label\">Modified</span>\n");
            html.append("        <span class=\"stat-value\" data-summary-count=\"modified\">")
                .append(modifiedCount)
                .append("</span>\n");
            html.append("      </article>\n");
            html.append("      <article class=\"stat-card impacted\">\n");
            html.append("        <span class=\"stat-label\">Impacted Rules</span>\n");
            html.append("        <ul class=\"impacted-list\">\n");
            if (impactedRules.isEmpty()) {
                html.append("          <li class=\"impacted-pill empty\">None</li>\n");
            } else {
                for (final String impactedRule : impactedRules) {
                    html.append("          <li class=\"impacted-pill\">")
                        .append(escapeHtml(impactedRule))
                        .append("</li>\n");
                }
            }
            html.append("        </ul>\n");
            html.append("      </article>\n");
            html.append("    </section>\n");

            html.append("    <section class=\"rules\">\n");
            final Map<String, List<Difference>> grouped = new LinkedHashMap<>();
            for (final Difference difference : differences) {
                final String key = difference.rule();
                grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(difference);
            }
            for (final Map.Entry<String, List<Difference>> entry : grouped.entrySet()) {
                final String rawRule = entry.getKey();
                final String ruleLabel = rawRule == null || rawRule.isBlank() ? "(No Rule)" : rawRule;
                final List<Difference> ruleDifferences = entry.getValue();
                int ruleNew = 0;
                int ruleMissing = 0;
                int ruleModified = 0;
                for (final Difference difference : ruleDifferences) {
                    if (difference.type() == DifferenceType.NEW) {
                        ruleNew++;
                    } else if (difference.type() == DifferenceType.MISSING) {
                        ruleMissing++;
                    } else {
                        ruleModified++;
                    }
                }

                html.append("      <details class=\"rule\" open>\n");
                html.append("        <summary>\n");
                html.append("          <span class=\"rule-name\">")
                    .append(escapeHtml(ruleLabel))
                    .append("</span>\n");
                html.append("          <span class=\"rule-counts\">\n");
                html.append("            <span class=\"count-chip\" data-count=\"")
                    .append(ruleDifferences.size())
                    .append("\">Total: ")
                    .append(ruleDifferences.size())
                    .append("</span>\n");
                html.append("            <span class=\"count-chip\" data-count=\"")
                    .append(ruleNew)
                    .append("\">New: ")
                    .append(ruleNew)
                    .append("</span>\n");
                html.append("            <span class=\"count-chip\" data-count=\"")
                    .append(ruleMissing)
                    .append("\">Missing: ")
                    .append(ruleMissing)
                    .append("</span>\n");
                html.append("            <span class=\"count-chip\" data-count=\"")
                    .append(ruleModified)
                    .append("\">Modified: ")
                    .append(ruleModified)
                    .append("</span>\n");
                html.append("          </span>\n");
                html.append("          <span class=\"chevron\" aria-hidden=\"true\"></span>\n");
                html.append("        </summary>\n");
                html.append("        <div class=\"rule-content\">\n");
                html.append("          <table class=\"diff-table\">\n");
                html.append("            <thead>\n");
                html.append("              <tr><th>File</th><th>Change</th><th>Severity</th><th>Details</th></tr>\n");
                html.append("            </thead>\n");
                html.append("            <tbody>\n");
                for (final Difference difference : ruleDifferences) {
                    final String severityClass = difference.severity().name().toLowerCase(Locale.ROOT);
                    final String typeValue = difference.type().name().toLowerCase(Locale.ROOT);
                    final String differenceId = createDifferenceId(difference);
                    html.append("              <tr class=\"difference-row\" data-diff-id=\"")
                        .append(escapeHtml(differenceId))
                        .append("\" data-diff-type=\"")
                        .append(typeValue)
                        .append("\">\n");
                    html.append("                <td>")
                        .append(createFileLink(difference))
                        .append("</td>\n");
                    html.append("                <td><span class=\"type-label\" data-type=\"")
                        .append(typeValue)
                        .append("\">")
                        .append(escapeHtml(toDisplayString(difference.type())))
                        .append("</span></td>\n");
                    html.append("                <td><span class=\"badge badge-")
                        .append(severityClass)
                        .append("\">")
                        .append(escapeHtml(toDisplayString(difference.severity())))
                        .append("</span></td>\n");
                    html.append("                <td class=\"details\">")
                        .append(buildDetailsSection(difference))
                        .append("</td>\n");
                    html.append("              </tr>\n");
                }
                html.append("            </tbody>\n");
                html.append("          </table>\n");
                html.append("        </div>\n");
                html.append("      </details>\n");
            }
            html.append("    </section>\n");
        }

        html.append("  </main>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        Files.writeString(reportPath, html.toString(), StandardCharsets.UTF_8);
    }

    private String createFileLink(final Difference difference) {
        final DecisionRecord reference = difference.actualRecord() != null
            ? difference.actualRecord()
            : difference.expectedRecord();
        if (reference == null) {
            return "";
        }
        final String rawBaseName = reference.fileName();
        if (rawBaseName == null || rawBaseName.isBlank()) {
            return "";
        }
        final String sanitizedBaseName = sanitizeFileReference(rawBaseName);
        if (sanitizedBaseName.isEmpty()) {
            return "";
        }
        final Path basePath = resolveBasePath(sanitizedBaseName);
        if (basePath == null) {
            return "";
        }
        final Path parentDirectory = basePath.getParent() != null ? basePath.getParent() : inputDirectory;
        final String baseIdentifier = stripKnownSuffixes(basePath.getFileName().toString());

        final String normalizedStage = reference.stage() == null
            ? ""
            : reference.stage().trim().toLowerCase(Locale.ROOT);
        String docSuffix = determineDocSuffix(normalizedStage);
        Path docPath = parentDirectory.resolve(baseIdentifier + docSuffix);
        if (!Files.exists(docPath)) {
            final Path alternateDoc = parentDirectory.resolve(baseIdentifier + alternateDocSuffix(docSuffix));
            if (Files.exists(alternateDoc)) {
                docPath = alternateDoc;
            }
        }

        Path domPath = parentDirectory.resolve(baseIdentifier + DOM_SUFFIX);
        if (!Files.exists(domPath)) {
            final Path alternateDom = parentDirectory.resolve(baseIdentifier + ALT_DOM_SUFFIX);
            if (Files.exists(alternateDom)) {
                domPath = alternateDom;
            }
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("<div class=\"file-links\">\n");
        builder
            .append("  <a class=\"file-link\" href=\"")
            .append(escapeHtml(toFileUriString(docPath)))
            .append("\" target=\"_blank\">")
            .append(escapeHtml(baseIdentifier + getFileLabelSuffix(docPath)))
            .append("</a>\n");
        builder
            .append("  <a class=\"file-link\" href=\"")
            .append(escapeHtml(toFileUriString(domPath)))
            .append("\" target=\"_blank\">")
            .append(escapeHtml(baseIdentifier + getFileLabelSuffix(domPath)))
            .append("</a>\n");
        builder.append("</div>");
        return builder.toString();
    }

    private Path resolveBasePath(final String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return null;
        }
        if (isWindowsAbsolutePath(baseName)) {
            final String normalized = baseName.replace('\\', '/');
            try {
                return Path.of(URI.create("file:///" + normalized));
            } catch (final IllegalArgumentException ex) {
                // fall through and try other strategies
            }
        }

        Path candidate = tryCreatePathFromUri(baseName);
        if (candidate == null) {
            candidate = tryCreatePath(baseName);
        }
        if (candidate == null) {
            if (inputDirectory == null) {
                return null;
            }
            candidate = inputDirectory.resolve(baseName);
        }
        if (!candidate.isAbsolute() && inputDirectory != null) {
            candidate = inputDirectory.resolve(candidate);
        }
        return candidate;
    }

    private static Path tryCreatePathFromUri(final String value) {
        if (!value.startsWith("file:")) {
            return null;
        }
        try {
            return Path.of(URI.create(value));
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    private static Path tryCreatePath(final String value) {
        try {
            return Path.of(value);
        } catch (final InvalidPathException ex) {
            return null;
        }
    }

    private static String sanitizeText(final String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        String result = stripEnclosingQuotes(value);
        result = stripBoundaryQuote(result, true);
        result = stripBoundaryQuote(result, false);
        result = normalizeWhitespace(result);
        return result;
    }

    private static String sanitizeFileReference(final String value) {
        if (value == null) {
            return "";
        }
        String sanitized = sanitizeText(value);
        if (sanitized.isEmpty()) {
            return sanitized;
        }
        sanitized = sanitized.replace("%22", "");
        sanitized = sanitized.replace('"', ' ');
        sanitized = sanitized.replace('\'', ' ');
        sanitized = normalizeWhitespace(sanitized);
        sanitized = collapseDuplicateFileScheme(sanitized);
        return stripOuterQuotes(sanitized);
    }

    private static String stripAllDoubleQuotes(final String value) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return value;
        }
        return value.replace("\"", "");
    }

    private static String normalizeWhitespace(final String value) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return "";
        }
        final StringBuilder builder = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (isWhitespaceCharacter(ch)) {
                if (!previousWhitespace) {
                    builder.append(' ');
                    previousWhitespace = true;
                }
            } else {
                builder.append(ch);
                previousWhitespace = false;
            }
        }
        int start = 0;
        int end = builder.length();
        while (start < end && builder.charAt(start) == ' ') {
            start++;
        }
        while (end > start && builder.charAt(end - 1) == ' ') {
            end--;
        }
        if (start >= end) {
            return "";
        }
        return builder.substring(start, end);
    }

    private static boolean isWhitespaceCharacter(final char ch) {
        return Character.isWhitespace(ch)
            || ch == '\u00A0'
            || ch == '\u2007'
            || ch == '\u202F'
            || ch == '\u200B'
            || ch == '\u2060'
            || ch == '\uFEFF';
    }

    private static String collapseDuplicateFileScheme(final String value) {
        String result = value;
        final String duplicate = "file:///file:///";
        while (result.contains(duplicate)) {
            result = result.replace(duplicate, "file:///");
        }
        final String prefix = "file:///";
        if (result.startsWith(prefix)) {
            final int lastIndex = result.lastIndexOf(prefix);
            if (lastIndex > 0) {
                result = result.substring(lastIndex);
            }
        }
        final String alternateDuplicate = "file://file://";
        while (result.contains(alternateDuplicate)) {
            result = result.replace(alternateDuplicate, "file://");
        }
        return result;
    }

    private static String stripEnclosingQuotes(final String value) {
        final int first = firstNonWhitespaceIndex(value);
        final int last = lastNonWhitespaceIndex(value);
        if (first >= 0 && last >= first && value.charAt(first) == '"' && value.charAt(last) == '"') {
            final String withoutTrailing = removeCharAt(value, last);
            return removeCharAt(withoutTrailing, first);
        }
        return value;
    }

    private static String stripBoundaryQuote(final String value, final boolean leading) {
        final int index = leading ? firstNonWhitespaceIndex(value) : lastNonWhitespaceIndex(value);
        if (index < 0 || value.charAt(index) != '"') {
            return value;
        }
        final int adjacent = leading ? index + 1 : index - 1;
        if (adjacent >= 0 && adjacent < value.length() && value.charAt(adjacent) == '"') {
            return value;
        }
        return removeCharAt(value, index);
    }

    private static int firstNonWhitespaceIndex(final String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int lastNonWhitespaceIndex(final String value) {
        for (int i = value.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String removeCharAt(final String value, final int index) {
        if (index < 0 || index >= value.length()) {
            return value;
        }
        return value.substring(0, index) + value.substring(index + 1);
    }

    private static String stripOuterQuotes(final String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        while (result.length() >= 6 && result.startsWith("%22") && result.endsWith("%22")) {
            result = result.substring(3, result.length() - 3).trim();
        }
        if (result.length() >= 2) {
            final char first = result.charAt(0);
            final char last = result.charAt(result.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                result = result.substring(1, result.length() - 1).trim();
            }
        }
        return result;
    }

    private static boolean isWindowsAbsolutePath(final String value) {
        return value.length() > 2
            && Character.isLetter(value.charAt(0))
            && value.charAt(1) == ':'
            && (value.charAt(2) == '\\' || value.charAt(2) == '/');
    }

    private static String stripKnownSuffixes(final String fileName) {
        String result = fileName;
        for (final String suffix : new String[] { PRE_ACE_SUFFIX, MID_ACE_SUFFIX, DOM_SUFFIX, ALT_DOM_SUFFIX }) {
            if (result.endsWith(suffix)) {
                return result.substring(0, result.length() - suffix.length());
            }
        }
        final int dotIndex = result.lastIndexOf('.');
        return dotIndex >= 0 ? result.substring(0, dotIndex) : result;
    }

    private static String determineDocSuffix(final String normalizedStage) {
        if (normalizedStage.equals(MID_PROCESS_STAGE)) {
            return PRE_ACE_SUFFIX;
        }
        if (normalizedStage.equals(MAIN_PROCESS_STAGE)) {
            return MID_ACE_SUFFIX;
        }
        return MID_ACE_SUFFIX;
    }

    private static String alternateDocSuffix(final String currentSuffix) {
        return PRE_ACE_SUFFIX.equals(currentSuffix) ? MID_ACE_SUFFIX : PRE_ACE_SUFFIX;
    }

    private static String toFileUriString(final Path path) {
        String raw;
        try {
            raw = path.toAbsolutePath().normalize().toUri().toString();
        } catch (final Exception ex) {
            raw = path.toUri().toString();
        }
        return sanitizeUri(raw);
    }

    private static String sanitizeUri(final String value) {
        if (value == null) {
            return "";
        }
        String sanitized = stripOuterQuotes(value);
        sanitized = sanitized.replace("\"", "");
        sanitized = sanitized.replace("%22", "");
        sanitized = sanitized.replace("'", "");
        sanitized = collapseDuplicateFileScheme(sanitized);
        return sanitized;
    }

    private static String getFileLabelSuffix(final Path path) {
        final String fileName = path.getFileName().toString();
        final String base = stripKnownSuffixes(fileName);
        return fileName.substring(base.length());
    }

    private static String createDifferenceId(final Difference difference) {
        final StringBuilder builder = new StringBuilder();
        builder.append(difference.type().name()).append('|');
        builder.append(normalizeForId(difference.rule())).append('|');
        builder.append(normalizeForId(difference.fileName())).append('|');
        builder.append(difference.severity().name()).append('|');
        appendRecordSignature(builder, difference.expectedRecord());
        builder.append('|');
        appendRecordSignature(builder, difference.actualRecord());
        return UUID.nameUUIDFromBytes(builder.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void appendRecordSignature(final StringBuilder builder, final DecisionRecord record) {
        if (record == null) {
            builder.append('-');
            return;
        }
        builder.append(normalizeForId(record.fileName())).append(';');
        builder.append(normalizeForId(record.rule())).append(';');
        builder.append(normalizeForId(record.highlight())).append(';');
        builder.append(normalizeForId(record.paraStyle())).append(';');
        builder.append(normalizeForId(record.charStyle())).append(';');
        builder.append(normalizeForId(record.find())).append(';');
        builder.append(normalizeForId(record.replace())).append(';');
        builder.append(normalizeForId(record.instanceText())).append(';');
        builder.append(normalizeForId(record.bookMarkName())).append(';');
        builder.append(normalizeForId(record.input())).append(';');
        builder.append(normalizeForId(record.output())).append(';');
        builder.append(normalizeForId(record.stage())).append(';');
        builder.append(normalizeForId(record.status())).append(';');
        builder.append(normalizeForId(record.suggestion())).append(';');
    }

    private static String normalizeForId(final String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean valuesEqual(final String left, final String right) {
        if (left == null || right == null) {
            return java.util.Objects.equals(left, right);
        }
        final String L = norm(left);
        final String R = norm(right);
        return L.equals(R) || L.equalsIgnoreCase(R);
    }

    private static String buildDetailsSection(final Difference difference) {
        final StringBuilder builder = new StringBuilder();
        if (difference.type() == DifferenceType.NEW) {
            builder.append("New record present in generated output but missing from expected results.");
            builder.append(formatRecordDetails(difference.actualRecord()));
        } else if (difference.type() == DifferenceType.MISSING) {
            builder.append("Record missing from generated output but present in expected results.");
            builder.append(formatRecordDetails(difference.expectedRecord()));
        } else {
            builder.append("Changed columns:");
            builder.append(formatChanges(difference.changes()));
        }
        return builder.toString();
    }

    private static String formatRecordDetails(final DecisionRecord record) {
        if (record == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        appendDetailItem(builder, "Stage", record.stage());
        appendDetailItem(builder, "Input", record.input());
        appendDetailItem(builder, "Output", record.output());
        appendDetailItem(builder, "Highlight", record.highlight());
        appendDetailItem(builder, "Status", record.status());
        appendDetailItem(builder, "Suggestion", record.suggestion());
        builder.append("</ul>");
        return builder.toString();
    }

    private static void appendDetailItem(final StringBuilder builder, final String label, final String value) {
        builder
            .append("<li><strong>")
            .append(escapeHtml(label))
            .append(":</strong> ")
            .append(escapeHtml(value == null ? "" : value))
            .append("</li>");
    }

    private static String formatChanges(final List<FieldChange> changes) {
        if (changes.isEmpty()) {
            return "<p>No differences captured.</p>";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        for (final FieldChange change : changes) {
            builder
                .append("<li><strong>")
                .append(escapeHtml(change.field()))
                .append(":</strong> ")
                .append(escapeHtml(change.expectedValue() == null ? "" : change.expectedValue()))
                .append(" &rarr; ")
                .append(escapeHtml(change.actualValue() == null ? "" : change.actualValue()))
                .append("</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }

    private static String escapeHtml(final String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String toDisplayString(final Enum<?> value) {
        final String name = value.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private record DecisionRecord(
        String fileName,
        String rule,
        String highlight,
        String paraStyle,
        String charStyle,
        String find,
        String replace,
        String input,
        String output,
        String stage,
        String status,
        String suggestion,
        String instanceText,
        String bookMarkName,
        int bufferPosition
        
    ) {
    }

    private record RecordIdentity(
        String fileName,
        String rule,
        String highlight,
        String paraStyle,
        String charStyle,
        String find,
        String replace,
        String instanceText,
        String bookMarkName,
        String input,
        String output,
        String stage,
        String status,
        String suggestion
    ) {
    }

    private record ComparisonKey(String fileName, String rule, String input) {
    }

    private record FieldChange(String field, String expectedValue, String actualValue) {
    }

    private enum DifferenceType {
        NEW,
        MISSING,
        MODIFIED
    }

    private enum Severity {
        CRITICAL,
        MODERATE
    }

    private record Difference(
        String rule,
        String fileName,
        DifferenceType type,
        Severity severity,
        DecisionRecord expectedRecord,
        DecisionRecord actualRecord,
        List<FieldChange> changes
    ) {
    }

    private record StageContext(String stageLabel, String textContent) {
    }

    private static Path replaceExtension(final Path path, final String newExtension) {
        final String fileName = path.getFileName().toString();
        final int dotIndex = fileName.lastIndexOf('.');
        final String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return path.resolveSibling(baseName + newExtension);
    }

    private static final class FileGroup {
        private Path preAceDocxPath;
        private Path midAceDocxPath;
        private Path decisionXmlPath;

        private boolean isComplete() {
            return preAceDocxPath != null && midAceDocxPath != null && decisionXmlPath != null;
        }
    }
}
