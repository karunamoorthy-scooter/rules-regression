package com.tnqtech.docx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;

class DocxExtractorTest {

    @Test
    void extractsParagraphsWithAccurateOffsets() throws Docx4JException, IOException, DocxExtractorException {
        final Path docxFile = Files.createTempFile("docx-extractor", ".docx");
        try {
            final WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            final MainDocumentPart mainPart = pkg.getMainDocumentPart();
            mainPart.addParagraphOfText("Hello");
            mainPart.addParagraphOfText("World");
            pkg.save(docxFile.toFile());

            final DocxExtractor extractor = new DocxExtractor();
            final DocxExtractor.ExtractionResult result = extractor.extract(docxFile);

            assertEquals("Hello\nWorld\n", result.getPlainText());
            final List<DocxExtractor.Node> nodes = result.getNodes();
            assertEquals(2, nodes.size());
            assertEquals(0, nodes.get(0).getStartPosition());
            assertEquals(5, nodes.get(0).getEndPosition());
            assertEquals(6, nodes.get(1).getStartPosition());
            assertEquals(11, nodes.get(1).getEndPosition());
            assertTrue(result.getParagraphStyles().containsKey("normal"));
            assertTrue(result.getCharacterStyles().containsKey("normal"));
        } finally {
            Files.deleteIfExists(docxFile);
        }
    }

    @Test
    void cliWritesPlainTextFile() throws Exception {
        final Path docxFile = Files.createTempFile("docx-extractor-cli", ".docx");
        final Path outputFile = docxFile.resolveSibling("docx-extractor-cli-output.txt");
        try {
            final WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            pkg.getMainDocumentPart().addParagraphOfText("CLI");
            pkg.save(docxFile.toFile());

            DocxExtractorApp.main(new String[] {docxFile.toString(), outputFile.toString()});

            final String extracted = Files.readString(outputFile);
            assertEquals("CLI\n", extracted);
        } finally {
            Files.deleteIfExists(docxFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void extractsTablesWithCellMetadata() throws Docx4JException, IOException, DocxExtractorException {
        final Path docxFile = Files.createTempFile("docx-extractor-table", ".docx");
        try {
            final WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            final MainDocumentPart mainPart = pkg.getMainDocumentPart();
            mainPart.addParagraphOfText("Intro");
            mainPart.getContent().add(createTable("CellOne", "CellTwo"));
            mainPart.addParagraphOfText("After");
            pkg.save(docxFile.toFile());

            final DocxExtractor extractor = new DocxExtractor();
            final DocxExtractor.ExtractionResult result = extractor.extract(docxFile);

            assertTrue(result.getPlainText().contains("CellOne"));
            assertTrue(result.getPlainText().contains("CellTwo"));
            final List<DocxExtractor.Node> nodes = result.getNodes();
            assertTrue(nodes.size() >= 4);
            final DocxExtractor.Node tableNode = nodes.stream()
                    .filter(node -> "CellOne".equals(node.getContent()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(tableNode.getTableId());
            assertEquals(0, tableNode.getRowIndex());
            assertEquals(0, tableNode.getCellIndex());
            assertFalse(tableNode.getOriginalXmlPath().isEmpty());
        } finally {
            Files.deleteIfExists(docxFile);
        }
    }

    @Test
    void plainTextOutputAlignsWithNodeOffsets() throws Exception {
        final Path docxFile = Files.createTempFile("docx-extractor-offsets", ".docx");
        try {
            final WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            final MainDocumentPart mainPart = pkg.getMainDocumentPart();
            final ObjectFactory factory = new ObjectFactory();

            mainPart.addParagraphOfText("Alpha");

            final Tbl table = factory.createTbl();
            final Tr row = factory.createTr();
            row.getContent().add(createCell(factory, "Bravo", "Charlie"));
            row.getContent().add(createCell(factory, "Delta"));
            table.getContent().add(row);
            mainPart.getContent().add(table);

            mainPart.addParagraphOfText("Echo");
            pkg.save(docxFile.toFile());

            final DocxExtractor extractor = new DocxExtractor();
            final DocxExtractor.ExtractionResult result = extractor.extract(docxFile);

            final String plainText = result.getPlainText();
            assertEquals("Alpha\nBravo\nCharlie\nDelta\nEcho\n", plainText);

            for (final DocxExtractor.Node node : result.getNodes()) {
                final String nodeSlice = plainText.substring(node.getStartPosition(), node.getEndPosition());
                assertEquals(node.getContent(), nodeSlice);
            }
        } finally {
            Files.deleteIfExists(docxFile);
        }
    }

    private Tbl createTable(final String leftCell, final String rightCell) {
        final ObjectFactory factory = new ObjectFactory();
        final Tbl table = factory.createTbl();
        final Tr row = factory.createTr();
        row.getContent().add(createCell(factory, leftCell));
        row.getContent().add(createCell(factory, rightCell));
        table.getContent().add(row);
        return table;
    }

    private Tc createCell(final ObjectFactory factory, final String... textValues) {
        final Tc cell = factory.createTc();
        for (final String textValue : textValues) {
            final P paragraph = factory.createP();
            final R run = factory.createR();
            final Text text = factory.createText();
            text.setValue(textValue);
            run.getContent().add(text);
            paragraph.getContent().add(run);
            cell.getContent().add(paragraph);
        }
        return cell;
    }
}
