package utils;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public class DocxToHtmlReport {

    public static void main(String[] args) {
        String inputFile = "D:\\reg-stable\\kumar\\CPPM_6875_derive_text\\CPPM_6875_tud_ACE.docx";
        String outputDir = "output";
        String htmlFile = outputDir + "/report.html";

        try {
            Files.createDirectories(Paths.get(outputDir));
            extractToHtml(inputFile, htmlFile, outputDir);
            System.out.println("✅ Report generated: " + htmlFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void extractToHtml(String inputDocx, String outputHtml, String outputDir) throws Exception {
        try (FileInputStream fis = new FileInputStream(inputDocx);
             XWPFDocument doc = new XWPFDocument(fis)) {

            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; margin: 30px; }")
                .append("h1, h2 { color: #2c3e50; }")
                .append("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }")
                .append("th, td { border: 1px solid #ccc; padding: 8px; }")
                .append("img { max-width: 300px; margin: 10px 0; border: 1px solid #ccc; }")
                .append(".meta { background: #f8f8f8; padding: 10px; border-left: 4px solid #007acc; }")
                .append("</style></head><body>");

            html.append("<h1>DOCX Extraction Report</h1>");
            html.append("<h2>Source File: ").append(inputDocx).append("</h2>");

            // --- Metadata ---
            html.append("<h2>📄 Metadata</h2><div class='meta'>");
            POIXMLProperties.CoreProperties core = doc.getProperties().getCoreProperties();
            html.append("<b>Title:</b> ").append(Optional.ofNullable(core.getTitle()).orElse("N/A")).append("<br>");
            html.append("<b>Author:</b> ").append(Optional.ofNullable(core.getCreator()).orElse("N/A")).append("<br>");
            html.append("<b>Created:</b> ").append(core.getCreated() != null ? core.getCreated().toString() : "N/A").append("<br>");
            html.append("<b>Modified By:</b> ").append(Optional.ofNullable(core.getLastModifiedByUser()).orElse("N/A")).append("<br>");
            html.append("</div>");

            // --- Headers & Footers ---
            html.append("<h2>📑 Headers & Footers</h2>");
            for (XWPFHeader header : doc.getHeaderList()) {
                html.append("<h3>Header:</h3><p>").append(header.getText()).append("</p>");
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                html.append("<h3>Footer:</h3><p>").append(footer.getText()).append("</p>");
            }

            // --- Paragraphs ---
            html.append("<h2>📝 Paragraphs</h2>");
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                html.append("<p>").append(paragraph.getText()).append("</p>");
            }

            // --- Tables ---
            html.append("<h2>📋 Tables</h2>");
            for (XWPFTable table : doc.getTables()) {
                html.append("<table>");
                for (XWPFTableRow row : table.getRows()) {
                    html.append("<tr>");
                    for (XWPFTableCell cell : row.getTableCells()) {
                        html.append("<td>").append(cell.getText()).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }

            // --- Images ---
            html.append("<h2>🖼️ Images</h2>");
            int imgCount = 0;
            for (XWPFPictureData pic : doc.getAllPictures()) {
                imgCount++;
                byte[] data = pic.getData();
                String fileName = "image_" + imgCount + "_" + pic.getFileName();
                Path imgPath = Paths.get(outputDir, fileName);
                Files.write(imgPath, data); // ✅ pure Java
                html.append("<img src='").append(fileName).append("' alt='Image ").append(imgCount).append("'>");
            }

            if (imgCount == 0) html.append("<p><i>No images found.</i></p>");

            html.append("</body></html>");

            // Save HTML file
            try (FileWriter writer = new FileWriter(outputHtml)) {
                writer.write(html.toString());
            }
        }
    }
}
