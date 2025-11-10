package com.tnqtech.report;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class CSVComparatorN4 {
    
    static class CSVData {
        List<String> headers;
        List<Map<String, String>> rows;
        
        CSVData() {
            headers = new ArrayList<>();
            rows = new ArrayList<>();
        }
    }
    
    static class ComparisonResult {
        List<MatchedRule> matched = new ArrayList<>();
        List<MismatchedRule> mismatched = new ArrayList<>();
        List<Map<String, String>> missed = new ArrayList<>();
        List<Map<String, String>> newRules = new ArrayList<>();
        List<String> headers = new ArrayList<>();
    }
    
    static class MatchedRule {
        int rowNumber;
        Map<String, String> expectedRow;
        Map<String, String> currentRow;
        
        MatchedRule(int rowNumber, Map<String, String> expectedRow, Map<String, String> currentRow) {
            this.rowNumber = rowNumber;
            this.expectedRow = expectedRow;
            this.currentRow = currentRow;
        }
    }
    
    static class MismatchedRule {
        int rowNumber;
        Map<String, String> expectedRow;
        Map<String, String> currentRow;
        Map<String, String[]> mismatchedFields; // column -> [expectedValue, currentValue]
        
        MismatchedRule(int rowNumber, Map<String, String> expectedRow, Map<String, String> currentRow) {
            this.rowNumber = rowNumber;
            this.expectedRow = expectedRow;
            this.currentRow = currentRow;
            this.mismatchedFields = new LinkedHashMap<>();
        }
    }
    
    public static void main(String[] args) {
        try {
          //  String expectedFile = "1.Expected.csv";
           // String currentFile = "2.Current.csv";
            //String outputFile = "comparison_report.html";
            
			/*
			 * String expectedFile = "D:\\reg-stable\\kumar\\expected1.csv"; String
			 * currentFile = "D:\\reg-stable\\kumar\\output1.csv"; String outputFile =
			 * "comparison_report_N4-8.html";
			 */
        	
        	

			 String expectedFile = "D:\\reg-stable\\kumar\\04_08_25\\expectedVersion_04_08_25\\expected.csv"; 
			 String currentFile = "D:\\reg-stable\\kumar\\04_08_25\\currentVersion_04_08_25\\output.csv"; 
			 String outputFile = "comparison_report_N4-9-3.html";
			 
            
            ComparisonResult result = compareCSVFiles(expectedFile, currentFile);
            generateHTMLReport(result, outputFile);
            
            System.out.println("===========================================");
            System.out.println("    CSV COMPARISON REPORT GENERATED");
            System.out.println("===========================================");
            System.out.println("Report file: " + outputFile);
            System.out.println("-------------------------------------------");
            System.out.println("Rules Matched:    " + result.matched.size());
            System.out.println("Rules Mismatched: " + result.mismatched.size());
            System.out.println("Rules Missed:     " + result.missed.size());
            System.out.println("New Rules:        " + result.newRules.size());
            System.out.println("===========================================");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static ComparisonResult compareCSVFiles(String expectedFile, String currentFile) throws IOException {
        CSVData expectedData = readCSVWithHeaders(expectedFile);
        CSVData currentData = readCSVWithHeaders(currentFile);
        
        ComparisonResult result = new ComparisonResult();
        result.headers = expectedData.headers;
        
        Set<Integer> matchedCurrentIndices = new HashSet<>();
        
        // Compare each expected row with current rows
        for (int i = 0; i < expectedData.rows.size(); i++) {
            Map<String, String> expectedRow = expectedData.rows.get(i);
            int rowNumber = i + 2; // +2 because row 1 is header
            
            boolean foundMatch = false;
            
            // Try to find a match in current data
            for (int j = 0; j < currentData.rows.size(); j++) {
                if (matchedCurrentIndices.contains(j)) continue;
                
                Map<String, String> currentRow = currentData.rows.get(j);
                
                // Compare column by column
                MismatchedRule mismatchedRule = compareRows(rowNumber, expectedRow, currentRow, expectedData.headers);
                
                if (mismatchedRule.mismatchedFields.isEmpty()) {
                    // Perfect match
                    result.matched.add(new MatchedRule(rowNumber, expectedRow, currentRow));
                    matchedCurrentIndices.add(j);
                    foundMatch = true;
                    break;
                } else if (mismatchedRule.mismatchedFields.size() < expectedData.headers.size()) {
                    // Partial match - some fields match, some don't
                    result.mismatched.add(mismatchedRule);
                    matchedCurrentIndices.add(j);
                    foundMatch = true;
                    break;
                }
            }
            
            // If no match found at all, it's a missed rule
            if (!foundMatch) {
                result.missed.add(expectedRow);
            }
        }
        
        // Find new rules in current that weren't matched
        for (int i = 0; i < currentData.rows.size(); i++) {
            if (!matchedCurrentIndices.contains(i)) {
                result.newRules.add(currentData.rows.get(i));
            }
        }
        
        return result;
    }
    
    private static MismatchedRule compareRows(int rowNumber, Map<String, String> expectedRow, 
                                             Map<String, String> currentRow, List<String> headers) {
        MismatchedRule rule = new MismatchedRule(rowNumber, expectedRow, currentRow);
        
        for (String header : headers) {
            String expectedValue = expectedRow.getOrDefault(header, "").trim();
            String currentValue = currentRow.getOrDefault(header, "").trim();
            
            // Exact comparison - preserves case and special characters
            if (!expectedValue.equals(currentValue)) {
                rule.mismatchedFields.put(header, new String[]{expectedValue, currentValue});
            }
        }
        
        return rule;
    }
    
    private static CSVData readCSVWithHeaders(String filename) throws IOException {
        CSVData data = new CSVData();
        List<String> lines = Files.readAllLines(Paths.get(filename));
        
        if (lines.isEmpty()) {
            return data;
        }
        
        // First line is headers
        data.headers = parseCSVLine(lines.get(0));
        
        // Read data rows
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                List<String> values = parseCSVLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                
                for (int j = 0; j < data.headers.size(); j++) {
                    String value = j < values.size() ? values.get(j) : "";
                    row.put(data.headers.get(j), value);
                }
                
                data.rows.add(row);
            }
        }
        
        return data;
    }
    
    private static List<String> parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        
        return values;
    }
    
    private static void generateHTMLReport(ComparisonResult result, String outputFile) throws IOException {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>CSV Comparison Report</title>\n");
        html.append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n");
        html.append("    <link href=\"https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.0/font/bootstrap-icons.css\" rel=\"stylesheet\">\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { \n");
        html.append("            background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);\n");
        html.append("            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n");
        html.append("            min-height: 100vh;\n");
        html.append("            padding: 2rem 0;\n");
        html.append("            transform: scale(0.9);transform-origin: top center;");
        html.append("        }\n");
        html.append("        .report-header { \n");
        html.append("            background:#ffffff; color: #641755 !important;");
        html.append("            padding: 0rem 0rem;\n");
        html.append("            border-radius: 0px;\n");
        html.append("            margin-bottom: 3rem;\n");
        html.append("            margin-top: -3rem;\n");
       // html.append("            box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);\n");
        html.append("        }\n");
        html.append("        .report-title { font-family:system-ui;");
        html.append("            font-size: 2.8rem;\n");
        html.append("            font-weight: 400;\n");
        html.append("            margin-bottom: -0.5rem;\n");
        html.append("            letter-spacing: 0.5px;\n");
        html.append("        }\n");
        html.append("        .report-subtitle { \n");
        html.append("            font-size: 1.2rem;\n");
        html.append("            opacity: 0.95;\n");
        html.append("            font-weight: 300;\n");
        html.append("        }\n");
        html.append("        .stats-card { \n");
        html.append("            border-radius: 15px;\n");
        html.append("            border: none;\n");
        html.append("            box-shadow: 0 5px 20px rgba(0,0,0,0.08);\n");
        html.append("            transition: all 0.3s ease;\n");
        html.append("            overflow: hidden;\n");
        html.append("        }\n");
        html.append("        .stats-card:hover { \n");
        html.append("            transform: translateY(-8px);\n");
        html.append("            box-shadow: 0 15px 35px rgba(0,0,0,0.15);\n");
        html.append("        }\n");
        html.append("        .stats-icon { \n");
        html.append("            font-size: 1rem;\n");
        html.append("            opacity: 0.9;\n");
        html.append("            margin-bottom: 0.5rem;\n");
        html.append("        }\n");
        html.append("        .stats-number { \n");
        html.append("            font-size: 1rem;\n");
        html.append("            font-weight: 700;\n");
        html.append("            line-height: 1;\n");
        html.append("            margin: 0.5rem 0;\n");
        html.append("        }\n");
        html.append("        .stats-card .card-body { \n");
        html.append("            padding: 2rem 1.5rem;\n");
        html.append("        }\n");
        html.append("        .bg-success-soft { background: #ffffff; color:green; }\n");
        html.append("        .bg-warning-soft { background: #ffffff; color:yellow; }\n");
        html.append("        .bg-danger-soft { background:  #ffffff; color:red; }\n");
        html.append("        .bg-info-soft { background:  #ffffff; color:blue; }\n");
        html.append("        .nav-tabs { \n");
        html.append("            border: none;\n");
        html.append("            margin-bottom: 0;\n");
        html.append("            background: #dfa908;\n");
        html.append("            border-radius: 15px 15px 0 0;\n");
        html.append("            padding: 0.5rem 1rem 0;\n");
        html.append("            box-shadow: 0 2px 10px rgba(0,0,0,0.05);\n");
        html.append("        }\n");
        html.append("        .nav-tabs .nav-link { \n");
        html.append("            border: none;\n");
        html.append("            color: #6c757d;\n");
        html.append("            font-weight: 500;\n");
        html.append("            padding: 1rem 1.8rem;\n");
        html.append("            border-radius: 10px 10px 0 0;\n");
        html.append("            transition: all 0.3s ease;\n");
        html.append("            margin-right: 0.3rem;\n");
        html.append("            background: transparent;\n");
        html.append("        }\n");
        html.append("        .nav-tabs .nav-link:hover { \n");
        html.append("            background: #f8f9fa;\n");
        html.append("            color: #667eea;\n");
        html.append("        }\n");
        html.append("        .nav-tabs .nav-link.active { \n");
        html.append("            background: #f8f9fa;\n");
        html.append("            color: #ac1f95;\n");
        html.append("            box-shadow: 0 -3px 10px rgba(102, 126, 234, 0.3);\n");
        html.append("        }\n");
        html.append("        .nav-tabs .nav-link.active .badge { \n");
        html.append("            background: rgba(255,255,255,0.3);\n");
        html.append("            color: white;\n");
        html.append("        }\n");
        html.append("        .nav-tabs .nav-link i { \n");
        html.append("            margin-right: 0.5rem;\n");
        html.append("        }\n");
      
        html.append("        .mismatch-expected { \n");
        html.append("            background: linear-gradient(90deg, #fff9e6 0%, #ffffff 100%);\n");
        html.append("            border-left: 4px solid #ffc107;\n");
        html.append("            font-weight: 500;\n");
        html.append("        }\n");
        html.append("        .mismatch-current { \n");
        html.append("            background: linear-gradient(90deg, #ffe6e6 0%, #ffffff 100%);\n");
        html.append("            border-left: 4px solid #ff6b6b;\n");
        html.append("            font-weight: 500;\n");
        html.append("        }\n");
        html.append("        .empty-state { \n");
        html.append("            text-align: center;\n");
        html.append("            padding: 4rem 2rem;\n");
        html.append("            color: #adb5bd;\n");
        html.append("        }\n");
        html.append("        .empty-state i { \n");
        html.append("            font-size: 5rem;\n");
        html.append("            opacity: 0.3;\n");
        html.append("            margin-bottom: 1rem;\n");
        html.append("            display: block;\n");
        html.append("        }\n");
        html.append("        .empty-state div {\n");
        html.append("            font-size: 1.2rem;\n");
        html.append("            font-weight: 500;\n");
        html.append("        }\n");
        html.append("        .row-number { \n");
        html.append("            background: linear-gradient(135deg, #e9ecef 0%, #f8f9fa 100%);\n");
        html.append("            font-weight: 600;\n");
        html.append("            color: #667eea;\n");
        html.append("            text-align: center;\n");
        html.append("        }\n");
        html.append("        .column-name { \n");
        html.append("            font-weight: 600;\n");
        html.append("            color: #667eea;\n");
        html.append("            background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);\n");
        html.append("        }\n");
        html.append("        .badge { \n");
        html.append("            padding: 0.4rem 0.8rem;\n");
        html.append("            border-radius: 20px;\n");
        html.append("            font-weight: 500;\n");
        html.append("            font-size: 0.85rem;\n");
        html.append("        }\n");
        
        html.append("           .text-white {\r\n"
        		+ "    --bs-text-opacity: 1;\r\n"
        		+ "    color: rgb(69 57 69) !important;\r\n"
        		+ "}");
        
        
        html.append("                .table-container {\r\n"
        		+ "  overflow-x: auto;                   /* enables horizontal scroll if wide */\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "table {\r\n"
        		+ "  width: 100%;\r\n"
        		+ "  border-collapse: collapse;\r\n"
        		+ "  table-layout: auto;                 /* let browser calculate widths naturally */\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "th, td {\r\n"
        		+ "  border: 1px solid #ccc;\r\n"
        		+ "  text-align: left;\r\n"
        		+ "  padding: 8px 12px;                  /* adds space to prevent text overlap */\r\n"
        		+ "  white-space: nowrap;                /* prevents wrapping if you want single-line cells */\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "th {\r\n"
        		+ "  background-color: #f2f2f2;\r\n"
        		+ "  font-weight: bold;\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "tr:nth-child(even) {\r\n"
        		+ "  background-color: #f9f9f9;\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "tr:hover {\r\n"
        		+ "  background-color: #e0e0e0;\r\n"
        		+ "}");
        
        
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container-fluid py-4\">\n");
        
        // Header
        html.append("        <div class=\"report-header\">\n");
        html.append("            <div class=\"report-title\">Rule-Based Regression Report</div>\n");
        html.append("            <div class=\"report-subtitle\">Detailed analysis of Expected vs Current CSV files</div>\n");
        html.append("        </div>\n");
        
        // Statistics Cards
        html.append("        <div class=\"row mb-4\">\n");
        html.append("            <div class=\"col-md-3 mb-3\">\n");
        html.append("                <div class=\"card stats-card text-white bg-success-soft\">\n");
        html.append("                    <div class=\"card-body text-center\">\n");
        html.append("                        <i class=\"bi bi-check-circle-fill stats-icon\"></i>\n");
        html.append("                        <div class=\"stats-number\">").append(result.matched.size()).append("</div>\n");
        html.append("                        <div style=\"font-size: 1.1rem; font-weight: 500;\">Rules Matched</div>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"col-md-3 mb-3\">\n");
        html.append("                <div class=\"card stats-card text-white bg-warning-soft\">\n");
        html.append("                    <div class=\"card-body text-center\">\n");
        html.append("                        <i class=\"bi bi-exclamation-triangle-fill stats-icon\"></i>\n");
        html.append("                        <div class=\"stats-number\">").append(result.mismatched.size()).append("</div>\n");
        html.append("                        <div style=\"font-size: 1.1rem; font-weight: 500;\">Rules Mismatched</div>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"col-md-3 mb-3\">\n");
        html.append("                <div class=\"card stats-card text-white bg-danger-soft\">\n");
        html.append("                    <div class=\"card-body text-center\">\n");
        html.append("                        <i class=\"bi bi-x-circle-fill stats-icon\"></i>\n");
        html.append("                        <div class=\"stats-number\">").append(result.missed.size()).append("</div>\n");
        html.append("                        <div style=\"font-size: 1.1rem; font-weight: 500;\">Rules Missed</div>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"col-md-3 mb-3\">\n");
        html.append("                <div class=\"card stats-card text-white bg-info-soft\">\n");
        html.append("                    <div class=\"card-body text-center\">\n");
        html.append("                        <i class=\"bi bi-plus-circle-fill stats-icon\"></i>\n");
        html.append("                        <div class=\"stats-number\">").append(result.newRules.size()).append("</div>\n");
        html.append("                        <div style=\"font-size: 1.1rem; font-weight: 500;\">New Rules</div>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        
        // Tabs
        html.append("        <ul class=\"nav nav-tabs\" id=\"comparisonTab\" role=\"tablist\">\n");
        html.append("            <li class=\"nav-item\" role=\"presentation\">\n");
        html.append("                <button class=\"nav-link active\" data-bs-toggle=\"tab\" data-bs-target=\"#matched\" type=\"button\" role=\"tab\">\n");
        html.append("                    <i class=\"bi bi-check-circle-fill\"></i> Rules Matched <span class=\"badge bg-light text-dark ms-2\">").append(result.matched.size()).append("</span>\n");
        html.append("                </button>\n");
        html.append("            </li>\n");
        html.append("            <li class=\"nav-item\" role=\"presentation\">\n");
        html.append("                <button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#mismatched\" type=\"button\" role=\"tab\">\n");
        html.append("                    <i class=\"bi bi-exclamation-triangle-fill\"></i> Rules Mismatched <span class=\"badge bg-light text-dark ms-2\">").append(result.mismatched.size()).append("</span>\n");
        html.append("                </button>\n");
        html.append("            </li>\n");
        html.append("            <li class=\"nav-item\" role=\"presentation\">\n");
        html.append("                <button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#missed\" type=\"button\" role=\"tab\">\n");
        html.append("                    <i class=\"bi bi-x-circle-fill\"></i> Rules Missed <span class=\"badge bg-light text-dark ms-2\">").append(result.missed.size()).append("</span>\n");
        html.append("                </button>\n");
        html.append("            </li>\n");
        html.append("            <li class=\"nav-item\" role=\"presentation\">\n");
        html.append("                <button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#new\" type=\"button\" role=\"tab\">\n");
        html.append("                    <i class=\"bi bi-plus-circle-fill\"></i> New Rules <span class=\"badge bg-light text-dark ms-2\">").append(result.newRules.size()).append("</span>\n");
        html.append("                </button>\n");
        html.append("            </li>\n");
        html.append("        </ul>\n");
        
        html.append("        <div class=\"tab-content\">\n");
        
        // Matched Tab
        html.append("            <div class=\"tab-pane fade show active\" id=\"matched\">\n");
        html.append(generateMatchedTable(result));
        html.append("            </div>\n");
        
        // Mismatched Tab
        html.append("            <div class=\"tab-pane fade\" id=\"mismatched\">\n");
        html.append(generateMismatchedTable(result));
        html.append("            </div>\n");
        
        // Missed Tab
        html.append("            <div class=\"tab-pane fade\" id=\"missed\">\n");
        html.append(generateMissedTable(result));
        html.append("            </div>\n");
        
        // New Rules Tab
        html.append("            <div class=\"tab-pane fade\" id=\"new\">\n");
        html.append(generateNewRulesTable(result));
        html.append("            </div>\n");
        
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        Files.write(Paths.get(outputFile), html.toString().getBytes());
    }
    
    private static String generateMatchedTable(ComparisonResult result) {
        if (result.matched.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No matched rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-container\">\n");
        html.append("<table class=\"table table-hover table-bordered\">\n");
        html.append("    <thead>\n");
        html.append("        <tr>\n");
        html.append("            <th class=\"row-number\">Row #</th>\n");
        
        for (String header : result.headers) {
            html.append("            <th>").append(escapeHtml(header)).append("</th>\n");
        }
        
        html.append("        </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");
        
        for (MatchedRule rule : result.matched) {
            html.append("        <tr>\n");
            html.append("            <td class=\"row-number\">").append(rule.rowNumber).append("</td>\n");
            
            for (String header : result.headers) {
                String value = rule.expectedRow.getOrDefault(header, "");
                html.append("            <td>").append(escapeHtml(value)).append("</td>\n");
            }
            
            html.append("        </tr>\n");
        }
        
        html.append("    </tbody>\n");
        html.append("</table>\n");
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private static String generateMismatchedTable(ComparisonResult result) {
        if (result.mismatched.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No mismatched rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-container\">\n");
        html.append("<table class=\"table table-hover table-bordered\">\n");
        html.append("    <thead>\n");
        html.append("        <tr>\n");
        html.append("            <th class=\"row-number\">Row #</th>\n");
        html.append("            <th class=\"column-name\">Column Name</th>\n");
        html.append("            <th style=\"width: 35%;\">Expected Value</th>\n");
        html.append("            <th style=\"width: 35%;\">Current Value</th>\n");
        html.append("        </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");
        
        for (MismatchedRule rule : result.mismatched) {
            int mismatchCount = rule.mismatchedFields.size();
            int index = 0;
            
            for (Map.Entry<String, String[]> entry : rule.mismatchedFields.entrySet()) {
                String columnName = entry.getKey();
                String expectedValue = entry.getValue()[0];
                String currentValue = entry.getValue()[1];
                
                html.append("        <tr>\n");
                
                if (index == 0) {
                    html.append("            <td class=\"row-number\" rowspan=\"").append(mismatchCount).append("\">")
                        .append(rule.rowNumber).append("</td>\n");
                }
                
                html.append("            <td class=\"column-name\">").append(escapeHtml(columnName)).append("</td>\n");
                html.append("            <td class=\"mismatch-expected\">").append(escapeHtml(expectedValue)).append("</td>\n");
                html.append("            <td class=\"mismatch-current\">").append(escapeHtml(currentValue)).append("</td>\n");
                html.append("        </tr>\n");
                
                index++;
            }
        }
        
        html.append("    </tbody>\n");
        html.append("</table>\n");
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private static String generateMissedTable(ComparisonResult result) {
        if (result.missed.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No missed rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-container\">\n");
        html.append("<table class=\"table table-hover table-bordered\">\n");
        html.append("    <thead>\n");
        html.append("        <tr>\n");
        html.append("            <th class=\"row-number\">#</th>\n");
        
        for (String header : result.headers) {
            html.append("            <th>").append(escapeHtml(header)).append("</th>\n");
        }
        
        html.append("        </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");
        
        int rowNum = 1;
        for (Map<String, String> row : result.missed) {
            html.append("        <tr>\n");
            html.append("            <td class=\"row-number\">").append(rowNum++).append("</td>\n");
            
            for (String header : result.headers) {
                String value = row.getOrDefault(header, "");
                html.append("            <td>").append(escapeHtml(value)).append("</td>\n");
            }
            
            html.append("        </tr>\n");
        }
        
        html.append("    </tbody>\n");
        html.append("</table>\n");
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private static String generateNewRulesTable(ComparisonResult result) {
        if (result.newRules.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No new rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-container\">\n");
        html.append("<table class=\"table table-hover table-bordered\">\n");
        html.append("    <thead>\n");
        html.append("        <tr>\n");
        html.append("            <th class=\"row-number\">#</th>\n");
        
        for (String header : result.headers) {
            html.append("            <th>").append(escapeHtml(header)).append("</th>\n");
        }
        
        html.append("        </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");
        
        int rowNum = 1;
        for (Map<String, String> row : result.newRules) {
            html.append("        <tr>\n");
            html.append("            <td class=\"row-number\">").append(rowNum++).append("</td>\n");
            
            for (String header : result.headers) {
                String value = row.getOrDefault(header, "");
                html.append("            <td>").append(escapeHtml(value)).append("</td>\n");
            }
            
            html.append("        </tr>\n");
        }
        
        html.append("    </tbody>\n");
        html.append("</table>\n");
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
