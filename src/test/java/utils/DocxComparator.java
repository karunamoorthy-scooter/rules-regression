package utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DocxComparator {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java DocxComparator <file1.docx> <file2.docx> [output.html]");
            return;
        }
        
        String file1 = args[0];
        String file2 = args[1];
        String outputFile = args.length > 2 ? args[2] : "comparison_report.html";
        
        try {
            String xml1 = extractDocumentXml(file1);
            String xml2 = extractDocumentXml(file2);
            
            String htmlReport = generateComparisonReport(xml1, xml2, file1, file2);
            Files.write(Paths.get(outputFile), htmlReport.getBytes());
            
            System.out.println("Comparison report generated: " + outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static String extractDocumentXml(String docxPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(docxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return readStreamAsString(zis);
                }
            }
        }
        throw new IOException("document.xml not found in " + docxPath);
    }
    
    private static String readStreamAsString(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
    
    private static String generateComparisonReport(String xml1, String xml2, 
                                                   String file1, String file2) {
        StringBuilder html = new StringBuilder();
        
        // HTML Header
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>DOCX Comparison Report</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
        html.append(".header { background: #2c3e50; color: white; padding: 20px; border-radius: 5px; }\n");
        html.append(".container { background: white; padding: 20px; margin-top: 20px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append(".file-info { margin: 10px 0; padding: 10px; background: #ecf0f1; border-radius: 3px; }\n");
        html.append(".stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin: 20px 0; }\n");
        html.append(".stat-card { background: #3498db; color: white; padding: 15px; border-radius: 5px; text-align: center; }\n");
        html.append(".stat-card.added { background: #27ae60; }\n");
        html.append(".stat-card.removed { background: #e74c3c; }\n");
        html.append(".stat-card.unchanged { background: #95a5a6; }\n");
        html.append(".diff-container { display: flex; gap: 20px; margin-top: 20px; }\n");
        html.append(".diff-panel { flex: 1; }\n");
        html.append(".diff-title { font-weight: bold; margin-bottom: 10px; padding: 10px; background: #34495e; color: white; border-radius: 3px; }\n");
        html.append(".diff-content { border: 1px solid #ddd; padding: 15px; background: #fafafa; border-radius: 3px; max-height: 600px; overflow-y: auto; font-family: 'Courier New', monospace; font-size: 12px; white-space: pre-wrap; word-wrap: break-word; }\n");
        html.append(".added { background-color: #d4edda; color: #155724; }\n");
        html.append(".removed { background-color: #f8d7da; color: #721c24; }\n");
        html.append(".line { margin: 2px 0; padding: 2px 5px; }\n");
        html.append(".line-number { display: inline-block; width: 50px; color: #999; user-select: none; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        
        // Header
        html.append("<div class=\"header\">\n");
        html.append("<h1>DOCX Document.xml Comparison Report</h1>\n");
        html.append("<p>Generated on: ").append(new Date()).append("</p>\n");
        html.append("</div>\n");
        
        // File Information
        html.append("<div class=\"container\">\n");
        html.append("<h2>File Information</h2>\n");
        html.append("<div class=\"file-info\"><strong>File 1:</strong> ").append(escapeHtml(file1)).append("</div>\n");
        html.append("<div class=\"file-info\"><strong>File 2:</strong> ").append(escapeHtml(file2)).append("</div>\n");
        
        // Compare and generate statistics
        List<String> lines1 = Arrays.asList(xml1.split("\n"));
        List<String> lines2 = Arrays.asList(xml2.split("\n"));
        
        DiffResult diff = computeDiff(lines1, lines2);
        
        // Statistics
        html.append("<h2>Statistics</h2>\n");
        html.append("<div class=\"stats\">\n");
        html.append("<div class=\"stat-card\"><h3>").append(lines1.size()).append("</h3><p>Lines in File 1</p></div>\n");
        html.append("<div class=\"stat-card\"><h3>").append(lines2.size()).append("</h3><p>Lines in File 2</p></div>\n");
        html.append("<div class=\"stat-card added\"><h3>").append(diff.added).append("</h3><p>Lines Added</p></div>\n");
        html.append("<div class=\"stat-card removed\"><h3>").append(diff.removed).append("</h3><p>Lines Removed</p></div>\n");
        html.append("<div class=\"stat-card unchanged\"><h3>").append(diff.unchanged).append("</h3><p>Lines Unchanged</p></div>\n");
        html.append("</div>\n");
        
        // Side-by-side diff
        html.append("<h2>Side-by-Side Comparison</h2>\n");
        html.append("<div class=\"diff-container\">\n");
        
        // File 1 Panel
        html.append("<div class=\"diff-panel\">\n");
        html.append("<div class=\"diff-title\">").append(escapeHtml(file1)).append("</div>\n");
        html.append("<div class=\"diff-content\">\n");
        for (int i = 0; i < diff.display1.size(); i++) {
            String line = diff.display1.get(i);
            String cssClass = diff.classes1.get(i);
            html.append("<div class=\"line ").append(cssClass).append("\">");
            html.append("<span class=\"line-number\">").append(i + 1).append("</span>");
            html.append(escapeHtml(line));
            html.append("</div>\n");
        }
        html.append("</div>\n</div>\n");
        
        // File 2 Panel
        html.append("<div class=\"diff-panel\">\n");
        html.append("<div class=\"diff-title\">").append(escapeHtml(file2)).append("</div>\n");
        html.append("<div class=\"diff-content\">\n");
        for (int i = 0; i < diff.display2.size(); i++) {
            String line = diff.display2.get(i);
            String cssClass = diff.classes2.get(i);
            html.append("<div class=\"line ").append(cssClass).append("\">");
            html.append("<span class=\"line-number\">").append(i + 1).append("</span>");
            html.append(escapeHtml(line));
            html.append("</div>\n");
        }
        html.append("</div>\n</div>\n");
        
        html.append("</div>\n");
        html.append("</div>\n");
        html.append("</body>\n</html>");
        
        return html.toString();
    }
    
    private static DiffResult computeDiff(List<String> lines1, List<String> lines2) {
        DiffResult result = new DiffResult();
        
        int maxLines = Math.max(lines1.size(), lines2.size());
        
        for (int i = 0; i < maxLines; i++) {
            String line1 = i < lines1.size() ? lines1.get(i) : null;
            String line2 = i < lines2.size() ? lines2.get(i) : null;
            
            if (line1 != null && line2 != null) {
                if (line1.equals(line2)) {
                    result.display1.add(line1);
                    result.display2.add(line2);
                    result.classes1.add("");
                    result.classes2.add("");
                    result.unchanged++;
                } else {
                    result.display1.add(line1);
                    result.display2.add(line2);
                    result.classes1.add("removed");
                    result.classes2.add("added");
                    result.removed++;
                    result.added++;
                }
            } else if (line1 != null) {
                result.display1.add(line1);
                result.display2.add("");
                result.classes1.add("removed");
                result.classes2.add("");
                result.removed++;
            } else {
                result.display1.add("");
                result.display2.add(line2);
                result.classes1.add("");
                result.classes2.add("added");
                result.added++;
            }
        }
        
        return result;
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    static class DiffResult {
        List<String> display1 = new ArrayList<>();
        List<String> display2 = new ArrayList<>();
        List<String> classes1 = new ArrayList<>();
        List<String> classes2 = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int unchanged = 0;
    }
}