package com.tnqtech.docx;

import jakarta.xml.bind.JAXBElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.CTVerticalAlignRun;
import org.docx4j.wml.P;
import org.docx4j.wml.P.Hyperlink;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.STVerticalAlignRun;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.docx4j.wml.Tc;

/**
 * Extracts plain text and formatting metadata from DOCX documents.
 */
public class DocxExtractor {

    static {
        System.setProperty("docx4j.jaxb.exceptionOnUnexpected", "false");
    }

    public static class Node {

        private final int id;
        private final String content;
        private String format;
        private final String paraStyle;
        private final String charStyle;
        private String font;
        private String color;
        private String link;
        private final Integer tableId;
        private final Integer rowIndex;
        private final Integer cellIndex;
        private final List<String> comments = new ArrayList<>();
        private final List<String> alerts = new ArrayList<>();
        private int startPosition;
        private int endPosition;
        private String originalXmlPath;
        private boolean isSuperscript;
        private boolean isSubscript;

        Node(final int id, final String content, final String paraStyle, final String charStyle,
                final Integer tableId, final Integer rowIndex, final Integer cellIndex) {
            this.id = id;
            this.content = content;
            this.paraStyle = paraStyle;
            this.charStyle = charStyle;
            this.tableId = tableId;
            this.rowIndex = rowIndex;
            this.cellIndex = cellIndex;
        }

        public int getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public String getFormat() {
            return format;
        }

        public String getParaStyle() {
            return paraStyle;
        }

        public String getCharStyle() {
            return charStyle;
        }

        public String getFont() {
            return font;
        }

        public String getColor() {
            return color;
        }

        public String getLink() {
            return link;
        }

        public Integer getTableId() {
            return tableId;
        }

        public Integer getRowIndex() {
            return rowIndex;
        }

        public Integer getCellIndex() {
            return cellIndex;
        }

        public List<String> getComments() {
            return Collections.unmodifiableList(comments);
        }

        public List<String> getAlerts() {
            return Collections.unmodifiableList(alerts);
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public String getOriginalXmlPath() {
            return originalXmlPath;
        }

        public boolean isSuperscript() {
            return isSuperscript;
        }

        public boolean isSubscript() {
            return isSubscript;
        }
    }

    public static class ExtractionResult {

        private final List<Node> nodes;
        private final Map<String, List<Integer>> paragraphStyles;
        private final Map<String, List<Integer>> characterStyles;
        private final String plainText;

        ExtractionResult(final List<Node> nodes,
                final Map<String, List<Integer>> paragraphStyles,
                final Map<String, List<Integer>> characterStyles,
                final String plainText) {
            this.nodes = List.copyOf(nodes);
            this.paragraphStyles = paragraphStyles.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
            this.characterStyles = characterStyles.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
            this.plainText = plainText;
        }

        public List<Node> getNodes() {
            return nodes;
        }

        public Map<String, List<Integer>> getParagraphStyles() {
            return paragraphStyles;
        }

        public Map<String, List<Integer>> getCharacterStyles() {
            return characterStyles;
        }

        public String getPlainText() {
            return plainText;
        }
    }

    private static final class BufferFragment {

        private final int start;
        private final String text;

        BufferFragment(final int start, final String text) {
            this.start = start;
            this.text = text;
        }
    }

    private final Map<Integer, Node> nodeMap = new LinkedHashMap<>();
    private final Map<String, List<Integer>> paraStyleMap = new LinkedHashMap<>();
    private final Map<String, List<Integer>> charStyleMap = new LinkedHashMap<>();
    private final List<BufferFragment> bufferFragments = new ArrayList<>();
    private int runningCharOffset;

    public ExtractionResult extract(final Path docxPath) throws DocxExtractorException {
        Objects.requireNonNull(docxPath, "DOCX path must not be null");
        if (!Files.exists(docxPath)) {
            throw new DocxExtractorException("DOCX file does not exist: " + docxPath);
        }
        resetState();

        try {
            final WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(docxPath.toFile());
            final MainDocumentPart mainDocPart = wordMLPackage.getMainDocumentPart();
            final List<Object> bodyElements = mainDocPart.getContent();

            int nodeId = 1;
            int paragraphIndex = 0;

            for (final Object bodyElement : bodyElements) {
                final Object element = unwrap(bodyElement);
                if (element instanceof P) {
                    nodeId = processParagraph((P) element, paragraphIndex, nodeId, null, null, null, mainDocPart);
                    appendParagraphBreak();
                    paragraphIndex++;
                } else if (element instanceof Tbl) {
                    nodeId = processTable((Tbl) element, paragraphIndex, nodeId, mainDocPart, bodyElements,
                            bodyElement);
                    paragraphIndex++;
                }
            }
        } catch (final Docx4JException ex) {
            throw new DocxExtractorException("Unable to parse DOCX file", ex);
        }

        final String plainText;
        try {
            plainText = buildPlainText();
        } catch (final IllegalStateException ex) {
            throw new DocxExtractorException("Unable to assemble plain text output", ex);
        }
        return new ExtractionResult(new ArrayList<>(nodeMap.values()), new LinkedHashMap<>(paraStyleMap),
                new LinkedHashMap<>(charStyleMap), plainText);
    }

    private void resetState() {
        nodeMap.clear();
        paraStyleMap.clear();
        charStyleMap.clear();
        bufferFragments.clear();
        runningCharOffset = 0;
    }

    private int processTable(final Tbl table, final int paragraphIndex, final int startingNodeId,
            final MainDocumentPart mainDocPart, final List<Object> bodyElements, final Object tableObject)
            throws DocxExtractorException {
        int nodeId = startingNodeId;
        final List<Object> rows = table.getContent();
        final int tableId = paragraphIndex;
        int rowIndex = 0;

        for (final Object rowObj : rows) {
            final Object rowElement = unwrap(rowObj);
            if (!(rowElement instanceof Tr)) {
                continue;
            }
            final Tr row = (Tr) rowElement;
            int cellIndex = 0;
            for (final Object cellObj : row.getContent()) {
                final Object cellElement = unwrap(cellObj);
                if (!(cellElement instanceof Tc)) {
                    continue;
                }
                final Tc cell = (Tc) cellElement;
                boolean firstParagraphInCell = true;
                for (final Object pObj : cell.getContent()) {
                    final Object paragraphElement = unwrap(pObj);
                    if (paragraphElement instanceof P) {
                        if (!firstParagraphInCell) {
                            appendSoftBreak();
                        }
                        nodeId = processParagraph((P) paragraphElement, paragraphIndex, nodeId, tableId, rowIndex,
                                cellIndex, mainDocPart);
                        firstParagraphInCell = false;
                    }
                }
                cellIndex++;
                appendParagraphBreak();
            }
            rowIndex++;
        }

        final int tableIndex = bodyElements.indexOf(tableObject);
        final int nextIndex = tableIndex + 1;
        if (nextIndex < bodyElements.size()) {
            final Object nextElement = unwrap(bodyElements.get(nextIndex));
            if (!(nextElement instanceof P)) {
                appendParagraphBreak();
            }
        }
        return nodeId;
    }

    private int processParagraph(final P paragraph, final int paragraphIndex, final int startingNodeId,
            final Integer tableId, final Integer rowIndex, final Integer cellIndex,
            final MainDocumentPart mainDocPart) throws DocxExtractorException {
        int nodeId = startingNodeId;
        final String paragraphStyle = getParagraphStyle(paragraph);
        int runIndex = 0;

        for (final Object runObj : paragraph.getContent()) {
            final Object runElement = unwrap(runObj);
            if (isOmml(runElement)) {
                final String mathText = extractMathText(runElement);
                if (!mathText.trim().isEmpty()) {
                    nodeId = createNode(nodeId, mathText, paragraphStyle, "math", paragraphIndex, runIndex,
                            tableId, rowIndex, cellIndex, mainDocPart, null);
                    runIndex++;
                }
            } else if (runElement instanceof R || runElement instanceof Hyperlink) {
                final List<R> runList = collectRuns(runElement);
                final String link = resolveHyperlink(runElement, mainDocPart);
                for (final R run : runList) {
                    final String text = extractRunText(run);
                    if (text.isEmpty()) {
                        continue;
                    }
                    nodeId = createNode(nodeId, text, paragraphStyle, getCharacterStyle(run), paragraphIndex,
                            runIndex, tableId, rowIndex, cellIndex, mainDocPart, link, run);
                    runIndex++;
                }
            }
        }
        return nodeId;
    }

    private int createNode(final int nodeId, final String text, final String paragraphStyle, final String charStyle,
            final int paragraphIndex, final int runIndex, final Integer tableId, final Integer rowIndex,
            final Integer cellIndex, final MainDocumentPart mainDocPart, final String link)
            throws DocxExtractorException {
        return createNode(nodeId, text, paragraphStyle, charStyle, paragraphIndex, runIndex, tableId, rowIndex,
                cellIndex, mainDocPart, link, null);
    }

    private int createNode(final int nodeId, final String text, final String paragraphStyle, final String charStyle,
            final int paragraphIndex, final int runIndex, final Integer tableId, final Integer rowIndex,
            final Integer cellIndex, final MainDocumentPart mainDocPart, final String link, final R run)
            throws DocxExtractorException {
        final Node node = new Node(nodeId, text, paragraphStyle, charStyle, tableId, rowIndex, cellIndex);
        node.startPosition = runningCharOffset;
        node.endPosition = runningCharOffset + getVisibleCharCount(text);
        node.originalXmlPath = buildXmlPath(paragraphIndex, runIndex, tableId, rowIndex, cellIndex, charStyle);
        if (link != null) {
            node.link = link;
        }
        if (run != null) {
            populateFormatting(node, run);
        }

        runningCharOffset = node.endPosition;
        recordFragment(node.startPosition, text);

        nodeMap.put(nodeId, node);
        paraStyleMap.computeIfAbsent(paragraphStyle, key -> new ArrayList<>()).add(nodeId);
        charStyleMap.computeIfAbsent(charStyle, key -> new ArrayList<>()).add(nodeId);
        return nodeId + 1;
    }

    private void appendSoftBreak() {
        recordBreak();
    }

    private void appendParagraphBreak() {
        recordBreak();
    }

    private void recordBreak() {
        recordFragment(runningCharOffset, "\n");
        runningCharOffset += 1;
    }

    private void recordFragment(final int start, final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        bufferFragments.add(new BufferFragment(start, text));
    }

    private List<R> collectRuns(final Object runElement) {
        if (runElement instanceof R) {
            return List.of((R) runElement);
        }
        if (runElement instanceof Hyperlink) {
            final List<R> runs = new ArrayList<>();
            for (final Object contentObj : ((Hyperlink) runElement).getContent()) {
                final Object contentElement = unwrap(contentObj);
                if (contentElement instanceof R) {
                    runs.add((R) contentElement);
                }
            }
            return runs;
        }
        return List.of();
    }

    private String resolveHyperlink(final Object runElement, final MainDocumentPart mainDocPart)
            throws DocxExtractorException {
        if (!(runElement instanceof Hyperlink)) {
            return null;
        }
        final Hyperlink hyperlink = (Hyperlink) runElement;
        if (hyperlink.getId() == null) {
            return null;
        }
        final RelationshipsPart relationshipsPart = mainDocPart.getRelationshipsPart();
        if (relationshipsPart == null) {
            return null;
        }
        final Relationship relationship = relationshipsPart.getRelationshipByID(hyperlink.getId());
        if (relationship == null || relationship.getTarget() == null) {
            throw new DocxExtractorException("Unable to resolve hyperlink target");
        }
        return relationship.getTarget();
    }

    private void populateFormatting(final Node node, final R run) {
        final RPr runProperties = run.getRPr();
        if (runProperties == null) {
            return;
        }
        final List<String> formatValues = new ArrayList<>();
        if (runProperties.getB() != null && Boolean.TRUE.equals(runProperties.getB().isVal())) {
            formatValues.add("bold");
        }
        if (runProperties.getI() != null && Boolean.TRUE.equals(runProperties.getI().isVal())) {
            formatValues.add("italic");
        }
        if (runProperties.getU() != null && runProperties.getU().getVal() != null
                && !"none".equalsIgnoreCase(runProperties.getU().getVal().value())) {
            formatValues.add("underline");
        }
        node.format = String.join(",", formatValues);

        final RFonts fonts = runProperties.getRFonts();
        if (fonts != null) {
            if (fonts.getAscii() != null) {
                node.font = fonts.getAscii();
            } else if (fonts.getHAnsi() != null) {
                node.font = fonts.getHAnsi();
            } else if (fonts.getCs() != null) {
                node.font = fonts.getCs();
            }
        }
        if (runProperties.getColor() != null && runProperties.getColor().getVal() != null
                && !"auto".equalsIgnoreCase(runProperties.getColor().getVal())) {
            node.color = runProperties.getColor().getVal();
        }
        final CTVerticalAlignRun vertAlign = runProperties.getVertAlign();
        if (vertAlign != null) {
            if (vertAlign.getVal() == STVerticalAlignRun.SUPERSCRIPT) {
                node.isSuperscript = true;
            }
            if (vertAlign.getVal() == STVerticalAlignRun.SUBSCRIPT) {
                node.isSubscript = true;
            }
        }
    }

    private String buildXmlPath(final int paragraphIndex, final int runIndex, final Integer tableId,
            final Integer rowIndex, final Integer cellIndex, final String charStyle) {
        if (tableId == null) {
            return String.format("p[%d]/%s[%d]", paragraphIndex, "math".equals(charStyle) ? "math" : "r",
                    runIndex);
        }
        return String.format("table[%d]/tr[%d]/tc[%d]/p[%d]/%s[%d]", tableId, rowIndex, cellIndex, paragraphIndex,
                "math".equals(charStyle) ? "math" : "r", runIndex);
    }

    private String extractRunText(final R run) {
        final StringBuilder builder = new StringBuilder();
        for (final Object content : run.getContent()) {
            final Object value = unwrap(content);
            if (value instanceof Text) {
                builder.append(((Text) value).getValue());
            }
        }
        return builder.toString();
    }

    private boolean isOmml(final Object obj) {
        if (obj == null) {
            return false;
        }
        return obj.getClass().getName().startsWith("org.docx4j.math.");
    }

    private String extractMathText(final Object ommlObject) {
        final StringBuilder builder = new StringBuilder();
        extractMathTextRecursive(ommlObject, builder, Collections.newSetFromMap(new IdentityHashMap<>()));
        return builder.toString();
    }

    private void extractMathTextRecursive(final Object obj, final StringBuilder builder, final Set<Object> visited) {
        if (obj == null || visited.contains(obj)) {
            return;
        }
        visited.add(obj);
        final Object value = unwrap(obj);
        if (value instanceof org.docx4j.math.CTText) {
            final String textValue = ((org.docx4j.math.CTText) value).getValue();
            if (textValue != null) {
                builder.append(textValue);
            }
            return;
        }
        if (value instanceof List) {
            for (final Object item : (List<?>) value) {
                extractMathTextRecursive(item, builder, visited);
            }
            return;
        }
        final Package pkg = value.getClass().getPackage();
        if (pkg == null || !pkg.getName().startsWith("org.docx4j.math")) {
            return;
        }
        for (final var field : value.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                final Object fieldValue = field.get(value);
                if (fieldValue == null || isPrimitiveLike(fieldValue)) {
                    continue;
                }
                extractMathTextRecursive(fieldValue, builder, visited);
            } catch (final IllegalAccessException ignored) {
                // ignore inaccessible fields
            }
        }
    }

    private boolean isPrimitiveLike(final Object value) {
        return value.getClass().isPrimitive() || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Class
                || value instanceof Package;
    }

    private String getParagraphStyle(final P paragraph) {
        if (paragraph.getPPr() != null && paragraph.getPPr().getPStyle() != null) {
            return paragraph.getPPr().getPStyle().getVal();
        }
        return "normal";
    }

    private String getCharacterStyle(final R run) {
        if (run.getRPr() != null && run.getRPr().getRStyle() != null) {
            return run.getRPr().getRStyle().getVal();
        }
        return "normal";
    }

    private int getVisibleCharCount(final String text) {
        return text.length();
    }

    private String buildPlainText() {
        if (bufferFragments.isEmpty()) {
            return "";
        }
        final char[] buffer = new char[runningCharOffset];
        final List<BufferFragment> fragments = new ArrayList<>(bufferFragments);
        fragments.sort((left, right) -> Integer.compare(left.start, right.start));
        for (final BufferFragment fragment : fragments) {
            final char[] chars = fragment.text.toCharArray();
            final int end = fragment.start + chars.length;
            if (fragment.start < 0 || end > buffer.length) {
                throw new IllegalStateException("Fragment exceeds plain text buffer bounds");
            }
            System.arraycopy(chars, 0, buffer, fragment.start, chars.length);
        }
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == '\0') {
                throw new IllegalStateException("Plain text buffer contains unassigned characters");
            }
        }
        return new String(buffer);
    }

    private Object unwrap(final Object obj) {
        if (obj instanceof JAXBElement) {
            return ((JAXBElement<?>) obj).getValue();
        }
        return obj;
    }
}
