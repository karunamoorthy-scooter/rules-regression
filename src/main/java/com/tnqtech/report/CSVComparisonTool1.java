package com.tnqtech.report;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

public class CSVComparisonTool1 {
    
    static class RuleRecord {
        String fileName, rule, highlight, paraStyle, charStyle, find;
        String[] originalRow;
        
        public RuleRecord(String[] row) {
            if (row.length >= 6) {
                this.fileName = row[0];
                this.rule = row[1];
                this.highlight = row[2];
                this.paraStyle = row[3];
                this.charStyle = row[4];
                this.find = row[5];
                this.originalRow = row;
            }
        }
        
        public String getKey() {
            return fileName + "|" + rule + "|" + highlight + "|" + paraStyle + "|" + charStyle + "|" + find;
        }
        
        public String getPartialKey() {
            return fileName + "|" + rule + "|" + highlight + "|" + paraStyle + "|" + charStyle + "|" + find;
        }
        
        public String toJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (int i = 0; i < originalRow.length; i++) {
                sb.append("\"col").append(i + 1).append("\":\"")
                  .append(originalRow[i].replace("\"", "\\\"")).append("\"");
                if (i < originalRow.length - 1) sb.append(",");
            }
            sb.append("}");
            return sb.toString();
        }
    }
    
    static class ComparisonResult {
        List<RuleRecord> matched = new ArrayList<>();
        List<RuleRecord> mismatched = new ArrayList<>();
        List<RuleRecord> missed = new ArrayList<>();
        List<RuleRecord> newRules = new ArrayList<>();
    }
    
    public static void main(String[] args) throws Exception {
    	  String expectedFile = "D:\\reg-stable\\kumar\\04_08_25\\expectedVersion_04_08_25\\expected.csv";
          String currentFile = "D:\\reg-stable\\kumar\\04_08_25\\currentVersion_04_08_25\\output.csv";
          String outputFile = "ComparisonReport1.html";
        
        List<RuleRecord> expectedRecords = readCSV(expectedFile);
        List<RuleRecord> currentRecords = readCSV(currentFile);
        
        ComparisonResult result = compareRecords(expectedRecords, currentRecords);
        
        generateHTMLReport(result, outputFile);
        System.out.println("Report generated: " + outputFile);
    }
    
    private static List<RuleRecord> readCSV(String filePath) throws Exception {
        List<RuleRecord> records = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            while ((line = reader.readNext()) != null) {
            	
            	
            		
            	System.out.println("line.length: "+line.length);
            	System.out.println("line::: "+line);
            	
            	for(int i=0;i<line.length;i++) {
            		
            		System.out.println("line["+i+"]: "+line[i]);
            		
            	}
            	
            	
            	
            	
                records.add(new RuleRecord(line));
            }
        }
        return records;
    }
    
    private static ComparisonResult compareRecords(List<RuleRecord> expected, List<RuleRecord> current) {
        ComparisonResult result = new ComparisonResult();
        
        Set<String> expectedKeys = new HashSet<>();
        Set<String> currentKeys = new HashSet<>();
        Map<String, RuleRecord> expectedMap = new HashMap<>();
        Map<String, RuleRecord> currentMap = new HashMap<>();
        
        for (RuleRecord record : expected) {
            expectedKeys.add(record.getKey());
            expectedMap.put(record.getKey(), record);
        }
        
        for (RuleRecord record : current) {
            currentKeys.add(record.getKey());
            currentMap.put(record.getKey(), record);
        }
        
        // Rules Matched - exact matches
        for (String key : expectedKeys) {
            if (currentKeys.contains(key)) {
                result.matched.add(expectedMap.get(key));
            }
        }
        
        // Missed Rules - in Expected but not in Current
        for (String key : expectedKeys) {
            if (!currentKeys.contains(key)) {
                result.missed.add(expectedMap.get(key));
            }
        }
        
        // New Rules - in Current but not in Expected
        for (String key : currentKeys) {
            if (!expectedKeys.contains(key)) {
                result.newRules.add(currentMap.get(key));
            }
        }
        
        // Mismatched Rules - partial matches on key fields
        Set<String> matchedOrMissed = new HashSet<>();
        matchedOrMissed.addAll(result.matched.stream().map(RuleRecord::getKey).collect(Collectors.toSet()));
        matchedOrMissed.addAll(result.missed.stream().map(RuleRecord::getKey).collect(Collectors.toSet()));
        matchedOrMissed.addAll(result.newRules.stream().map(RuleRecord::getKey).collect(Collectors.toSet()));
        
        for (RuleRecord expRecord : expected) {
            if (matchedOrMissed.contains(expRecord.getKey())) continue;
            
            for (RuleRecord curRecord : current) {
                if (matchedOrMissed.contains(curRecord.getKey())) continue;
                
                if (isPartialMatch(expRecord, curRecord)) {
                    result.mismatched.add(expRecord);
                    matchedOrMissed.add(expRecord.getKey());
                    break;
                }
            }
        }
        
        return result;
    }
    
    private static boolean isPartialMatch(RuleRecord r1, RuleRecord r2) {
        return r1.fileName.equals(r2.fileName) &&
               r1.rule.equals(r2.rule) &&
               r1.highlight.equals(r2.highlight) &&
               r1.paraStyle.equals(r2.paraStyle) &&
               r1.charStyle.equals(r2.charStyle) &&
               r1.find.equals(r2.find);
    }
    
    private static void generateHTMLReport(ComparisonResult result, String outputFile) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<title>CSV Comparison Report</title>\n");
        html.append("<style>\n");
        html.append(getCSSStyles());
        html.append("\n</style>\n</head>\n<body>\n");
        
        html.append("<div class='container'>\n");
        html.append("<h1>CSV Comparison Report</h1>\n");
        html.append("<p class='summary'>").append(getSummary(result)).append("</p>\n");
        
        // Tabs
        html.append("<div class='tabs'>\n");
        html.append(getTabButton("Matched", "matched", true));
        html.append(getTabButton("Mismatched", "mismatched", false));
        html.append(getTabButton("Missed", "missed", false));
        html.append(getTabButton("New", "new", false));
        html.append("</div>\n");
        
        // Tab content
        html.append(getTabContent("matched", result.matched, true));
        html.append(getTabContent("mismatched", result.mismatched, false));
        html.append(getTabContent("missed", result.missed, false));
        html.append(getTabContent("new", result.newRules, false));
        
        html.append("</div>\n");
        html.append("<script>\n").append(getJavaScript()).append("\n</script>\n");
        html.append("</body>\n</html>");
        
        Files.write(Paths.get(outputFile), html.toString().getBytes());
    }
    
    private static String getSummary(ComparisonResult result) {
        return String.format("Rules Matched: %d | Mismatched: %d | Missed: %d | New: %d | Total: %d",
                result.matched.size(), result.mismatched.size(), result.missed.size(),
                result.newRules.size(), result.matched.size() + result.mismatched.size() + 
                result.missed.size() + result.newRules.size());
    }
    
    private static String getTabButton(String label, String id, boolean active) {
        return String.format("<button class='tab-button %s' onclick=\"openTab(event, '%s')\">%s Rules</button>\n",
                active ? "active" : "", id, label);
    }
    
    private static String getTabContent(String tabId, List<RuleRecord> records, boolean active) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id='").append(tabId).append("' class='tab-content")
          .append(active ? " active" : "").append("'>\n");
        sb.append("<div class='controls'>\n");
        sb.append("<input type='text' id='search-").append(tabId)
          .append("' class='search-box' placeholder='Search...' onkeyup=\"filterTable('")
          .append(tabId).append("')\">\n");
        sb.append("<label>Rows per page: ");
        sb.append("<select onchange=\"changePageSize('").append(tabId).append("', this.value)\">\n");
        sb.append("<option value='10'>10</option>\n");
        sb.append("<option value='20'>20</option>\n");
        sb.append("<option value='50'>50</option>\n");
        sb.append("<option value='100'>100</option>\n");
        sb.append("</select></label>\n");
        sb.append("</div>\n");
        
        sb.append("<table id='table-").append(tabId).append("' class='data-table'>\n");
        sb.append("<thead><tr>\n");
        sb.append("<th>FileName</th><th>Rule</th><th>Highlight</th><th>ParaStyle</th>")
          .append("<th>CharStyle</th><th>Find</th>\n");
        for (int i = 6; i < (records.size() > 0 ? records.get(0).originalRow.length : 0); i++) {
            sb.append("<th>Col").append(i + 1).append("</th>\n");
        }
        sb.append("</tr></thead>\n<tbody>\n");
        
        for (RuleRecord record : records) {
            sb.append("<tr class='data-row'>\n");
            for (String cell : record.originalRow) {
                sb.append("<td>").append(escapeHtml(cell)).append("</td>\n");
            }
            sb.append("</tr>\n");
        }
        
        sb.append("</tbody>\n</table>\n");
        sb.append("<div class='pagination' id='pagination-").append(tabId).append("'></div>\n");
        sb.append("</div>\n");
        
        return sb.toString();
    }
    
    private static String getCSSStyles() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n" +
                ".container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 10px 40px rgba(0,0,0,0.2); padding: 30px; }\n" +
                "h1 { color: #333; margin-bottom: 10px; font-size: 28px; }\n" +
                ".summary { color: #666; font-size: 14px; margin-bottom: 30px; padding: 15px; background: #f5f5f5; border-radius: 4px; }\n" +
                ".tabs { display: flex; border-bottom: 2px solid #e0e0e0; margin-bottom: 20px; gap: 0; }\n" +
                ".tab-button { padding: 12px 24px; background: none; border: none; cursor: pointer; font-size: 14px; font-weight: 600; color: #666; transition: all 0.3s; position: relative; }\n" +
                ".tab-button:hover { color: #667eea; }\n" +
                ".tab-button.active { color: #667eea; border-bottom: 3px solid #667eea; }\n" +
                ".tab-content { display: none; }\n" +
                ".tab-content.active { display: block; animation: fadeIn 0.3s; }\n" +
                "@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }\n" +
                ".controls { display: flex; gap: 20px; margin-bottom: 20px; align-items: center; flex-wrap: wrap; }\n" +
                ".search-box { flex: 1; min-width: 200px; padding: 10px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }\n" +
                ".search-box:focus { outline: none; border-color: #667eea; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1); }\n" +
                ".controls select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; cursor: pointer; }\n" +
                ".data-table { width: 100%; border-collapse: collapse; font-size: 14px; table-layout: fixed; }\n" +
                ".data-table thead { background: #f8f9fa; }\n" +
                ".data-table th { padding: 12px; text-align: left; font-weight: 600; color: #333; border-bottom: 2px solid #e0e0e0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n" +
                ".data-table td { padding: 12px; border-bottom: 1px solid #f0f0f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n" +
                ".data-table tbody tr:hover { background: #f5f7ff; }\n" +
                ".data-table tbody tr.hidden { display: none; }\n" +
                ".pagination { display: flex; gap: 5px; justify-content: center; margin-top: 20px; flex-wrap: wrap; }\n" +
                ".pagination button { padding: 8px 12px; border: 1px solid #ddd; background: white; cursor: pointer; border-radius: 4px; font-size: 14px; transition: all 0.2s; }\n" +
                ".pagination button:hover { background: #f0f0f0; }\n" +
                ".pagination button.active { background: #667eea; color: white; border-color: #667eea; }\n" +
                ".pagination button:disabled { color: #ccc; cursor: not-allowed; }\n";
    }
    
    private static String getJavaScript() {
        return "const rowsPerPage = {};\n" +
                "const currentPage = {};\n" +
                "const filteredData = {};\n" +
                "\n" +
                "function openTab(evt, tabName) {\n" +
                "  const tabContents = document.getElementsByClassName('tab-content');\n" +
                "  for (let i = 0; i < tabContents.length; i++) {\n" +
                "    tabContents[i].classList.remove('active');\n" +
                "  }\n" +
                "  const buttons = document.getElementsByClassName('tab-button');\n" +
                "  for (let i = 0; i < buttons.length; i++) {\n" +
                "    buttons[i].classList.remove('active');\n" +
                "  }\n" +
                "  document.getElementById(tabName).classList.add('active');\n" +
                "  evt.currentTarget.classList.add('active');\n" +
                "}\n" +
                "\n" +
                "function filterTable(tabId) {\n" +
                "  const input = document.getElementById('search-' + tabId);\n" +
                "  const filter = input.value.toLowerCase();\n" +
                "  const table = document.getElementById('table-' + tabId);\n" +
                "  const rows = table.querySelectorAll('tbody tr');\n" +
                "  filteredData[tabId] = [];\n" +
                "  rows.forEach((row, index) => {\n" +
                "    const text = row.textContent.toLowerCase();\n" +
                "    if (text.includes(filter)) {\n" +
                "      filteredData[tabId].push(index);\n" +
                "    }\n" +
                "  });\n" +
                "  currentPage[tabId] = 0;\n" +
                "  displayPage(tabId);\n" +
                "}\n" +
                "\n" +
                "function changePageSize(tabId, size) {\n" +
                "  rowsPerPage[tabId] = parseInt(size);\n" +
                "  currentPage[tabId] = 0;\n" +
                "  displayPage(tabId);\n" +
                "}\n" +
                "\n" +
                "function displayPage(tabId) {\n" +
                "  if (!rowsPerPage[tabId]) rowsPerPage[tabId] = 10;\n" +
                "  if (!currentPage[tabId]) currentPage[tabId] = 0;\n" +
                "  \n" +
                "  const table = document.getElementById('table-' + tabId);\n" +
                "  const rows = table.querySelectorAll('tbody tr');\n" +
                "  const indices = filteredData[tabId] || Array.from({length: rows.length}, (_, i) => i);\n" +
                "  \n" +
                "  rows.forEach((row, i) => row.classList.add('hidden'));\n" +
                "  \n" +
                "  const start = currentPage[tabId] * rowsPerPage[tabId];\n" +
                "  const end = start + rowsPerPage[tabId];\n" +
                "  \n" +
                "  indices.slice(start, end).forEach(idx => {\n" +
                "    rows[idx].classList.remove('hidden');\n" +
                "  });\n" +
                "  \n" +
                "  updatePagination(tabId, indices.length);\n" +
                "}\n" +
                "\n" +
                "function updatePagination(tabId, totalRows) {\n" +
                "  const paginationDiv = document.getElementById('pagination-' + tabId);\n" +
                "  paginationDiv.innerHTML = '';\n" +
                "  \n" +
                "  const totalPages = Math.ceil(totalRows / (rowsPerPage[tabId] || 10));\n" +
                "  if (totalPages <= 1) return;\n" +
                "  \n" +
                "  for (let i = 0; i < totalPages; i++) {\n" +
                "    const btn = document.createElement('button');\n" +
                "    btn.textContent = i + 1;\n" +
                "    if (i === currentPage[tabId]) btn.classList.add('active');\n" +
                "    btn.onclick = () => {\n" +
                "      currentPage[tabId] = i;\n" +
                "      displayPage(tabId);\n" +
                "    };\n" +
                "    paginationDiv.appendChild(btn);\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "document.addEventListener('DOMContentLoaded', () => {\n" +
                "  ['matched', 'mismatched', 'missed', 'new'].forEach(tabId => {\n" +
                "    rowsPerPage[tabId] = 10;\n" +
                "    currentPage[tabId] = 0;\n" +
                "    filterTable(tabId);\n" +
                "  });\n" +
                "});\n";
    }
    
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
