package com.tnqtech.report;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.tnqtech.report.CSVComparatorN5.ComparisonResult;


public class CSVComparatorN6 {
    
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
        Map<String, String[]> mismatchedFields;
        
        MismatchedRule(int rowNumber, Map<String, String> expectedRow, Map<String, String> currentRow) {
            this.rowNumber = rowNumber;
            this.expectedRow = expectedRow;
            this.currentRow = currentRow;
            this.mismatchedFields = new LinkedHashMap<>();
        }
    }
    
    public static void main(String[] args) {
        try {
            String expectedFile = "D:\\reg-stable\\kumar\\04_08_25\\expectedVersion_04_08_25\\expected.csv"; 
            String currentFile = "D:\\reg-stable\\kumar\\04_08_25\\currentVersion_04_08_25\\output.csv"; 
            String outputFile = "comparison_report_N6_10_2.html";
            
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
        
        for (int i = 0; i < expectedData.rows.size(); i++) {
            Map<String, String> expectedRow = expectedData.rows.get(i);
            int rowNumber = i + 2;
            
            boolean foundMatch = false;
            
            for (int j = 0; j < currentData.rows.size(); j++) {
                if (matchedCurrentIndices.contains(j)) continue;
                
                Map<String, String> currentRow = currentData.rows.get(j);
                MismatchedRule mismatchedRule = compareRows(rowNumber, expectedRow, currentRow, expectedData.headers);
                
                if (mismatchedRule.mismatchedFields.isEmpty()) {
                    result.matched.add(new MatchedRule(rowNumber, expectedRow, currentRow));
                    matchedCurrentIndices.add(j);
                    foundMatch = true;
                    break;
                } else if (mismatchedRule.mismatchedFields.size() < expectedData.headers.size()) {
                    result.mismatched.add(mismatchedRule);
                    matchedCurrentIndices.add(j);
                    foundMatch = true;
                    break;
                }
            }
            
            if (!foundMatch) {
                result.missed.add(expectedRow);
            }
        }
        
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
        
        data.headers = parseCSVLine(lines.get(0));
        
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
        html.append("        body { background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; min-height: 100vh; padding: 2rem 0; }\n");
        html.append("        .report-header { background: #ffffff; color: #641755; padding: 2rem; margin-bottom: 3rem; }\n");
        html.append("        .report-title { font-size: 2.8rem; font-weight: 400; margin-bottom: 0.5rem; }\n");
        html.append("        .report-subtitle { font-size: 1.2rem; opacity: 0.95; font-weight: 300; }\n");
        html.append("        .stats-card { border-radius: 15px; border: none; box-shadow: 0 5px 20px rgba(0,0,0,0.08); transition: all 0.3s; }\n");
        html.append("        .stats-card:hover { transform: translateY(-8px); box-shadow: 0 15px 35px rgba(0,0,0,0.15); }\n");
        html.append("        .stats-number { font-size: 2rem; font-weight: 700; }\n");
        html.append("        .bg-success-soft { background: #ffffff; color: green; }\n");
        html.append("        .bg-warning-soft { background: #ffffff; color: orange; }\n");
        html.append("        .bg-danger-soft { background: #ffffff; color: red; }\n");
        html.append("        .bg-info-soft { background: #ffffff; color: blue; }\n");
        html.append("        .nav-tabs { border: none; background: #dfa908; border-radius: 15px 15px 0 0; padding: 0.5rem 1rem 0; }\n");
        html.append("        .nav-tabs .nav-link { color: #6c757d; font-weight: 500; padding: 1rem 1.8rem; border-radius: 10px 10px 0 0; }\n");
        html.append("        .nav-tabs .nav-link.active { background: #f8f9fa; color: #ac1f95; }\n");
        html.append("        .mismatch-expected { background: #fff9e6; border-left: 4px solid #ffc107; }\n");
        html.append("        .mismatch-current { background: #ffe6e6; border-left: 4px solid #ff6b6b; }\n");
        html.append("        .empty-state { text-align: center; padding: 4rem 2rem; color: #adb5bd; }\n");
        html.append("        .empty-state i { font-size: 5rem; opacity: 0.3; margin-bottom: 1rem; }\n");
        html.append("        .row-number { background: #e9ecef; font-weight: 600; color: #667eea; text-align: center; }\n");
        html.append("        .table-wrapper { background: white; padding: 1.5rem; border-radius: 8px; margin-top: 1rem; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        html.append("        .table-controls { display: flex; gap: 1rem; margin-bottom: 1.5rem; align-items: center; flex-wrap: wrap; background: #f8f9fa; padding: 1rem; border-radius: 6px; }\n");
        html.append("        .search-box { flex: 1; min-width: 250px; }\n");
        html.append("        .search-box input { width: 100%; padding: 0.6rem; border: 1px solid #ddd; border-radius: 4px; }\n");
        html.append("        .search-box input:focus { outline: none; border-color: #667eea; box-shadow: 0 0 5px rgba(102, 126, 234, 0.3); }\n");
        html.append("        .rows-per-page { display: flex; gap: 0.7rem; align-items: center; }\n");
        html.append("        .rows-per-page label { margin: 0; font-weight: 500; white-space: nowrap; }\n");
        html.append("        .rows-per-page select { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; cursor: pointer; }\n");
        html.append("        .table-container { overflow-x: auto; margin-bottom: 1rem; }\n");
        html.append("        table { width: 100%; border-collapse: collapse; }\n");
        html.append("        th, td { border: 1px solid #ddd; text-align: left; padding: 10px; }\n");
        html.append("        th { background-color: #f2f2f2; font-weight: bold; }\n");
        html.append("        tbody tr:nth-child(even) { background-color: #f9f9f9; }\n");
        html.append("        tbody tr:hover { background-color: #e8f0ff; }\n");
        html.append("        .pagination-controls { display: flex; gap: 0.7rem; margin-top: 1.5rem; justify-content: center; align-items: center; flex-wrap: wrap; }\n");
        html.append("        .pagination-controls button { padding: 0.6rem 1.2rem; border: 1px solid #ddd; background: white; border-radius: 4px; cursor: pointer; font-weight: 500; }\n");
        html.append("        .pagination-controls button:hover:not(:disabled) { background: #667eea; color: white; border-color: #667eea; }\n");
        html.append("        .pagination-controls button:disabled { opacity: 0.5; cursor: not-allowed; }\n");
        html.append("        .pagination-info { margin: 0 1rem; font-weight: 500; color: #667eea; text-align: center; min-width: 300px; }\n");
        html.append("        .hidden-row { display: none !important; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container-fluid\" style=\"max-width: 1400px; margin: 0 auto;\">\n");
        html.append("        <div class=\"report-header\">\n");
        html.append("            <div class=\"report-title\">Rule-Based Regression Report</div>\n");
        html.append("            <div class=\"report-subtitle\">Detailed analysis of Expected vs Current CSV files</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"row mb-4\">\n");
        html.append("            <div class=\"col-md-3 mb-3\"><div class=\"card stats-card bg-success-soft\"><div class=\"card-body text-center\"><i class=\"bi bi-check-circle-fill\" style=\"font-size: 2rem;\"></i><div class=\"stats-number\">").append(result.matched.size()).append("</div><div>Rules Matched</div></div></div></div>\n");
        html.append("            <div class=\"col-md-3 mb-3\"><div class=\"card stats-card bg-warning-soft\"><div class=\"card-body text-center\"><i class=\"bi bi-exclamation-triangle-fill\" style=\"font-size: 2rem;\"></i><div class=\"stats-number\">").append(result.mismatched.size()).append("</div><div>Rules Mismatched</div></div></div></div>\n");
        html.append("            <div class=\"col-md-3 mb-3\"><div class=\"card stats-card bg-danger-soft\"><div class=\"card-body text-center\"><i class=\"bi bi-x-circle-fill\" style=\"font-size: 2rem;\"></i><div class=\"stats-number\">").append(result.missed.size()).append("</div><div>Rules Missed</div></div></div></div>\n");
        html.append("            <div class=\"col-md-3 mb-3\"><div class=\"card stats-card bg-info-soft\"><div class=\"card-body text-center\"><i class=\"bi bi-plus-circle-fill\" style=\"font-size: 2rem;\"></i><div class=\"stats-number\">").append(result.newRules.size()).append("</div><div>New Rules</div></div></div></div>\n");
        html.append("        </div>\n");
        html.append("        <ul class=\"nav nav-tabs\" role=\"tablist\">\n");
        html.append("            <li class=\"nav-item\"><button class=\"nav-link active\" data-bs-toggle=\"tab\" data-bs-target=\"#matched\" type=\"button\"><i class=\"bi bi-check-circle-fill\"></i> Rules Matched <span class=\"badge bg-light text-dark ms-2\">").append(result.matched.size()).append("</span></button></li>\n");
        html.append("            <li class=\"nav-item\"><button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#mismatched\" type=\"button\"><i class=\"bi bi-exclamation-triangle-fill\"></i> Rules Mismatched <span class=\"badge bg-light text-dark ms-2\">").append(result.mismatched.size()).append("</span></button></li>\n");
        html.append("            <li class=\"nav-item\"><button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#missed\" type=\"button\"><i class=\"bi bi-x-circle-fill\"></i> Rules Missed <span class=\"badge bg-light text-dark ms-2\">").append(result.missed.size()).append("</span></button></li>\n");
        html.append("            <li class=\"nav-item\"><button class=\"nav-link\" data-bs-toggle=\"tab\" data-bs-target=\"#new\" type=\"button\"><i class=\"bi bi-plus-circle-fill\"></i> New Rules <span class=\"badge bg-light text-dark ms-2\">").append(result.newRules.size()).append("</span></button></li>\n");
        html.append("        </ul>\n");
        html.append("        <div class=\"tab-content\">\n");
        html.append("            <div class=\"tab-pane fade show active\" id=\"matched\">\n");
        html.append(generateMatchedTable(result));
        html.append("            </div>\n");
        html.append("            <div class=\"tab-pane fade\" id=\"mismatched\">\n");
        html.append(generateMismatchedTable(result));
        html.append("            </div>\n");
        html.append("            <div class=\"tab-pane fade\" id=\"missed\">\n");
        html.append(generateMissedTable(result));
        html.append("            </div>\n");
        html.append("            <div class=\"tab-pane fade\" id=\"new\">\n");
        html.append(generateNewRulesTable(result));
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js\"></script>\n");
        html.append("    <script>\n");
        html.append("        function initializePaginationAndSearch(tableId) {\n");
        html.append("            const rowsPerPageSelect = document.getElementById(tableId + 'RowsPerPage');\n");
        html.append("            const searchInput = document.getElementById(tableId + 'Search');\n");
        html.append("            const table = document.getElementById(tableId + 'Table');\n");
        html.append("            const tbody = table.querySelector('tbody');\n");
        html.append("            const rows = Array.from(tbody.querySelectorAll('tr'));\n");
        html.append("            let currentPage = 1;\n");
        html.append("            let filteredRows = [...rows];\n");
        html.append("            let rowsPerPage = parseInt(rowsPerPageSelect.value);\n");
        html.append("            function displayTable() {\n");
        html.append("                const startIndex = (currentPage - 1) * rowsPerPage;\n");
        html.append("                const endIndex = startIndex + rowsPerPage;\n");
        html.append("                rows.forEach(row => row.classList.add('hidden-row'));\n");
        html.append("                filteredRows.slice(startIndex, endIndex).forEach(row => row.classList.remove('hidden-row'));\n");
        html.append("                updatePaginationControls();\n");
        html.append("            }\n");
        html.append("            function updatePaginationControls() {\n");
        html.append("                const totalPages = Math.ceil(filteredRows.length / rowsPerPage);\n");
        html.append("                const paginationDiv = document.getElementById(tableId + 'Pagination');\n");
        html.append("                const infoSpan = paginationDiv.querySelector('.pagination-info');\n");
        html.append("                const startRow = filteredRows.length === 0 ? 0 : (currentPage - 1) * rowsPerPage + 1;\n");
        html.append("                const endRow = Math.min(currentPage * rowsPerPage, filteredRows.length);\n");
        html.append("                infoSpan.textContent = `Page ${currentPage} of ${Math.max(1, totalPages)} | Showing ${startRow}-${endRow} of ${filteredRows.length}`;\n");
        html.append("                document.getElementById(tableId + 'PrevBtn').disabled = currentPage === 1 || totalPages === 0;\n");
        html.append("                document.getElementById(tableId + 'NextBtn').disabled = currentPage === totalPages || totalPages === 0;\n");
        html.append("            }\n");
        html.append("            function filterRows() {\n");
        html.append("                const searchTerm = searchInput.value.toLowerCase();\n");
        html.append("                filteredRows = rows.filter(row => row.textContent.toLowerCase().includes(searchTerm));\n");
        html.append("                currentPage = 1;\n");
        html.append("                displayTable();\n");
        html.append("            }\n");
        html.append("            rowsPerPageSelect.addEventListener('change', (e) => { rowsPerPage = parseInt(e.target.value); currentPage = 1; displayTable(); });\n");
        html.append("            searchInput.addEventListener('input', filterRows);\n");
        html.append("            document.getElementById(tableId + 'PrevBtn').addEventListener('click', () => { if (currentPage > 1) { currentPage--; displayTable(); } });\n");
        html.append("            document.getElementById(tableId + 'NextBtn').addEventListener('click', () => { const totalPages = Math.ceil(filteredRows.length / rowsPerPage); if (currentPage < totalPages) { currentPage++; displayTable(); } });\n");
        html.append("            displayTable();\n");
        html.append("        }\n");
        html.append("        document.addEventListener('DOMContentLoaded', () => {\n");
        html.append("            setTimeout(() => { initializePaginationAndSearch('matched'); initializePaginationAndSearch('mismatched'); initializePaginationAndSearch('missed'); initializePaginationAndSearch('new'); }, 100);\n");
        html.append("        });\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        Files.write(Paths.get(outputFile), html.toString().getBytes());
    }
    
    private static String generateMatchedTable(ComparisonResult result) {
        if (result.matched.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No matched rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-wrapper\">\n");
        html.append("    <div class=\"table-controls\">\n");
        html.append("        <div class=\"search-box\"><input type=\"text\" id=\"matchedSearch\" placeholder=\"Search matched rules...\" /></div>\n");
        html.append("        <div class=\"rows-per-page\"><label for=\"matchedRowsPerPage\">Rows per page:</label>\n");
        html.append("            <select id=\"matchedRowsPerPage\"><option value=\"10\">10</option><option value=\"20\" selected>20</option><option value=\"50\">50</option><option value=\"100\">100</option></select>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"table-container\">\n");
        html.append("        <table id=\"matchedTable\"><thead><tr><th class=\"row-number\">Row #</th>\n");
        
        for (String header : result.headers) {
            html.append("            <th>").append(escapeHtml(header)).append("</th>\n");
        }
        
        html.append("        </tr></thead><tbody>\n");
        
        for (MatchedRule rule : result.matched) {
            html.append("            <tr><td class=\"row-number\">").append(rule.rowNumber).append("</td>\n");
            for (String header : result.headers) {
                String value = rule.expectedRow.getOrDefault(header, "");
                html.append("                <td>").append(escapeHtml(value)).append("</td>\n");
            }
            html.append("            </tr>\n");
        }
        
        html.append("        </tbody></table>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"pagination-controls\" id=\"matchedPagination\">\n");
        html.append("        <button id=\"matchedPrevBtn\">← Previous</button>\n");
        html.append("        <span class=\"pagination-info\">Page 1 of 1</span>\n");
        html.append("        <button id=\"matchedNextBtn\">Next →</button>\n");
        html.append("    </div>\n");
        html.append("</div>\n");
        
        return html.toString();
    }
    
    private static String generateMismatchedTable(ComparisonResult result) {
        if (result.mismatched.isEmpty()) {
            return "<div class=\"empty-state\"><i class=\"bi bi-inbox\"></i><div>No mismatched rules found</div></div>";
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"table-wrapper\">\n");
        html.append("    <div class=\"table-controls\">\n");
        html.append("        <div class=\"search-box\"><input type=\"text\" id=\"mismatchedSearch\" placeholder=\"Search mismatched rules...\" /></div>\n");
        html.append("        <div class=\"rows-per-page\"><label for=\"mismatchedRowsPerPage\">Rows per page:</label>\n");
        html.append("            <select id=\"mismatchedRowsPerPage\"><option value=\"10\">10</option><option value=\"20\" selected>20</option><option value=\"50\">50</option><option value=\"100\">100</option></select>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"table-container\">\n");
        html.append("        <table id=\"mismatchedTable\"><thead><tr><th class=\"row-number\">Row #</th><th>Column Name</th><th style=\"width: 30%;\">Expected Value</th><th style=\"width: 30%;\">Current Value</th></tr></thead><tbody>\n");
        
        for (MismatchedRule rule : result.mismatched) {
            int mismatchCount = rule.mismatchedFields.size();
            int index = 0;
            
            for (Map.Entry<String, String[]> entry : rule.mismatchedFields.entrySet()) {
                String columnName = entry.getKey();
                String expectedValue = entry.getValue()[0];
                String currentValue = entry.getValue()[1];
                
                html.append("            <tr>\n");
                
                if (index == 0) {
                    html.append("                <td class=\"row-number\" rowspan=\"").append(mismatchCount).append("\">").append(rule.rowNumber).append("</td>\n");
                }
                
                html.append("                <td>").append(escapeHtml(columnName)).append("</td>\n");
                html.append("                <td class=\"mismatch-expected\">").append(escapeHtml(expectedValue)).append("</td>\n");
                html.append("                <td class=\"mismatch-current\">").append(escapeHtml(currentValue)).append("</td>\n");
                html.append("            </tr>\n");
                
                index++;
            }
        }
        
        html.append("        </tbody></table>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"pagination-controls\" id=\"mismatchedPagination\">\n");
        html.append("        <button id=\"mismatchedPrevBtn\">← Previous</button>\n");
        html.append("        <span class=\"pagination-info\">Page 1 of 1</span>\n");
        html.append("        <button id=\"mismatchedNextBtn\">Next →</button>\n");
        html.append("    </div>\n");
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
    
