package com.tnqtech.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import csvreport.TNQLogo;

// Data Model Class
class CSVRecord {
    private String fileName;
    private String rule;
    private String highlight;
    private String paraStyle;
    private String charStyle;
    private String find;
    private String replace;
    private String input;
    private String output;
    private String stage;
    private String status;
    private String suggestion;
    private String instanceText;
    private String bookMarkName;

    public CSVRecord(String[] values) {
        if (values.length >= 14) {
            this.fileName = values[0];
            this.rule = values[1];
            this.highlight = values[2];
            this.paraStyle = values[3];
            this.charStyle = values[4];
            this.find = values[5];
            this.replace = values[6];
            this.input = values[7];
            this.output = values[8];
            this.stage = values[9];
            this.status = values[10];
            this.suggestion = values[11];
            this.instanceText = values[12];
            this.bookMarkName = values[13];
        }
    }

    public String getFileName() { return fileName; }
    public String getRule() { return rule; }
    public String getHighlight() { return highlight; }
    public String getParaStyle() { return paraStyle; }
    public String getCharStyle() { return charStyle; }
    public String getFind() { return find; }
    public String getReplace() { return replace; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getStage() { return stage; }
    public String getStatus() { return status; }
    public String getSuggestion() { return suggestion; }
    public String getInstanceText() { return instanceText; }
    public String getBookMarkName() { return bookMarkName; }

    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setRule(String rule) { this.rule = rule; }
    public void setHighlight(String highlight) { this.highlight = highlight; }
    public void setParaStyle(String paraStyle) { this.paraStyle = paraStyle; }
    public void setCharStyle(String charStyle) { this.charStyle = charStyle; }
    public void setFind(String find) { this.find = find; }
    public void setReplace(String replace) { this.replace = replace; }
    public void setInput(String input) { this.input = input; }
    public void setOutput(String output) { this.output = output; }
    public void setStage(String stage) { this.stage = stage; }
    public void setStatus(String status) { this.status = status; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public void setInstanceText(String instanceText) { this.instanceText = instanceText; }
    public void setBookMarkName(String bookMarkName) { this.bookMarkName = bookMarkName; }

    public String getMatchKey() {
        return String.join("|", fileName, rule, find, replace, input, instanceText);
    }

    public List<String> getAllValues() {
        return Arrays.asList(fileName, rule, highlight, paraStyle, charStyle, 
                           find, replace, input, output, stage, status, suggestion, instanceText, bookMarkName);
    }

    public boolean exactMatch(CSVRecord other) {
        return this.getAllValues().equals(other.getAllValues());
    }

    public List<String> getComparableValues() {
        return Arrays.asList(highlight, paraStyle, charStyle, output, stage, status, suggestion);
    }
}

class ModifiedRecord {
    CSVRecord expected;
    CSVRecord current;
    List<String> modifiedFields;

    public ModifiedRecord(CSVRecord expected, CSVRecord current, List<String> modifiedFields) {
        this.expected = expected;
        this.current = current;
        this.modifiedFields = modifiedFields;
    }
}

class ComparisonResult {
    List<CSVRecord> matched = new ArrayList<>();
    List<ModifiedRecord> modified = new ArrayList<>();
    List<CSVRecord> missed = new ArrayList<>();
    List<CSVRecord> newRecords = new ArrayList<>();
}

public class RulesReportBuilder {
    
    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.err.println("Usage: java CompareCsvReport <Expected.csv> <Output.csv> <Report.html>");
                System.exit(2);
            }
            Path expectedFile = Paths.get(args[0]);
            Path currentFile = Paths.get(args[1]);
            Path outputFile = Paths.get(args[2]);

            List<CSVRecord> expectedRecords = readCSV(expectedFile.toString());
            List<CSVRecord> currentRecords = readCSV(currentFile.toString());

            ComparisonResult result = compareRecords(expectedRecords, currentRecords);

            generateHTMLReport(result, outputFile.toString());

            System.out.println("Comparison completed successfully!");
            System.out.println("Matched: " + result.matched.size());
            System.out.println("Modified: " + result.modified.size());
            System.out.println("Missed: " + result.missed.size());
            System.out.println("New: " + result.newRecords.size());
            System.out.println("Report generated: " + outputFile);
            
            RulesListHandler rulesHandler = new RulesListHandler();
            rulesHandler.generateRulesXML(result, "./src/main/resources/rules");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<CSVRecord> readCSV(String filePath) throws IOException {
        List<CSVRecord> records = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split("\\$", -1);
            records.add(new CSVRecord(values));
        }
        
        return records;
    }

    private static ComparisonResult compareRecords(List<CSVRecord> expected, List<CSVRecord> current) {
        ComparisonResult result = new ComparisonResult();
        
        List<CSVRecord> expectedCopy = new ArrayList<>(expected);
        List<CSVRecord> currentCopy = new ArrayList<>(current);
        
        Iterator<CSVRecord> expIter = expectedCopy.iterator();
        while (expIter.hasNext()) {
            CSVRecord expRecord = expIter.next();
            Iterator<CSVRecord> curIter = currentCopy.iterator();
            
            while (curIter.hasNext()) {
                CSVRecord curRecord = curIter.next();
                
                if (expRecord.exactMatch(curRecord)) {
                    result.matched.add(expRecord);
                    expIter.remove();
                    curIter.remove();
                    break;
                }
            }
        }

        expIter = expectedCopy.iterator();
        while (expIter.hasNext()) {
            CSVRecord expRecord = expIter.next();
            Iterator<CSVRecord> curIter = currentCopy.iterator();
            
            while (curIter.hasNext()) {
                CSVRecord curRecord = curIter.next();
                
                if (expRecord.getMatchKey().equals(curRecord.getMatchKey())) {
                    List<String> modifiedFields = new ArrayList<>();
                    String[] fields = {"Highlight", "ParaStyle", "CharStyle", "Output", "Stage", "Status", "Suggestion"};
                    List<String> expValues = expRecord.getComparableValues();
                    List<String> curValues = curRecord.getComparableValues();
                    
                    for (int i = 0; i < expValues.size(); i++) {
                        if (!expValues.get(i).equals(curValues.get(i))) {
                            modifiedFields.add(fields[i]);
                        }
                    }
                    
                    if (!modifiedFields.isEmpty()) {
                        result.modified.add(new ModifiedRecord(expRecord, curRecord, modifiedFields));
                        expIter.remove();
                        curIter.remove();
                        break;
                    }
                }
            }
        }

        result.missed.addAll(expectedCopy);
        result.newRecords.addAll(currentCopy);

        return result;
    }

    private static void generateHTMLReport(ComparisonResult result, String outputFile) throws IOException {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n<html lang='en'>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        html.append("<title>ACE Rule Based Comparison Report</title>\n");
        html.append("<style>\n");
        html.append(getCSS());
        html.append("</style>\n</head>\n<body>\n");
       
        html.append("<div class='container'>\n");
        html.append("<div class='header'>\n");
        html.append("<img class=\"tnq-logo\" width=\"10%\" src=\""+TNQLogo.tnqLocoBase64()+"\" alt=\"TNQ TECH logo\" />\n");
        html.append("<h1 class='title'>ACE Rule Based Comparison Report</h1>\n");
        html.append("<div class='currentDateTime'>"+currentTimeStamp()+"</div>");
        html.append("</div>\n");
        
        html.append("<div class='cards'>\n");
        html.append(createCard("Matched Changes", result.matched.size(), "#100fb9"));
        html.append(createCard("Modified Changes", result.modified.size(), "#100fb9"));
        html.append(createCard("Missed Changes", result.missed.size(), "#100fb9"));
        html.append(createCard("New Changes", result.newRecords.size(), "#100fb9"));
        html.append("</div>\n");
        
        // Add Modified Summary Link and Rules Verification Link
        html.append("<div style='margin-bottom: 20px; text-align: center; display: flex; gap: 20px; justify-content: center;'>\n");
        html.append("<a href='#' class='summary-link' onclick='openModifiedSummaryModal(); return false;'>View Mismatched Output</a>\n");
        html.append("<a href='#' class='summary-link' onclick='openRulesVerificationModal(); return false;'>View Rules Verification</a>\n");
        html.append("</div>\n");
        
        html.append("<div class='tabs'>\n");
        html.append("<button class='tab-btn active' onclick='openTab(event, \"matched\")'>Matched Changes</button>\n");
        html.append("<button class='tab-btn' onclick='openTab(event, \"modified\")'>Modified Changes</button>\n");
        html.append("<button class='tab-btn' onclick='openTab(event, \"missed\")'>Missed Changes</button>\n");
        html.append("<button class='tab-btn' onclick='openTab(event, \"new\")'>New Changes</button>\n");
        html.append("</div>\n");
        
        html.append("<div id='matched' class='tab-content active'>\n");
        html.append(generateTableHTML("matched", result.matched, null));
        html.append("</div>\n");
        
        html.append("<div id='modified' class='tab-content'>\n");
        html.append(generateModifiedTableHTML(result.modified));
        html.append("</div>\n");
        
        html.append("<div id='missed' class='tab-content'>\n");
        html.append(generateTableHTML("missed", result.missed, null));
        html.append("</div>\n");
        
        html.append("<div id='new' class='tab-content'>\n");
        html.append(generateTableHTML("new", result.newRecords, null));
        html.append("</div>\n");
        
        html.append("</div>\n");
        
        // Add Modal for Modified Summary
        html.append(generateModifiedSummaryModal(result));
        
        // Add Modal for Rules Verification
        html.append(generateRulesVerificationModal(result));
        
        html.append("<script>\n");
        html.append(getJavaScript());
        html.append("</script>\n");
        html.append("</body>\n</html>");
        
        Files.write(Paths.get(outputFile), html.toString().getBytes());
    }

    private static String generateModifiedSummaryModal(ComparisonResult result) {
        StringBuilder html = new StringBuilder();
        
        // Count output differences
        int outputDiffCount = 0;
        List<ModifiedRecord> outputDiffRecords = new ArrayList<>();
        
        for (ModifiedRecord mod : result.modified) {
            if (mod.modifiedFields.contains("Output")) {
                outputDiffCount++;
                outputDiffRecords.add(mod);
            }
        }
        
        html.append("<div id='modifiedSummaryModal' class='modal'>\n");
        html.append("<div class='modal-content'>\n");
        html.append("<div class='modal-header'>\n");
        html.append("<h2 class='modal-title'>Modified Summary Report</h2>\n");
        html.append("<span class='close-modal' onclick='closeModifiedSummaryModal()'>&times;</span>\n");
        html.append("</div>\n");
        html.append("<div class='modal-body'>\n");
        
        html.append("<div class='summary-header'>\n");
        html.append("<div class='summary-title'>Output Field Differences</div>\n");
        html.append("<div class='summary-count'>Total Count: " + outputDiffCount + "</div>\n");
        html.append("</div>\n");
        
        html.append(generateOutputDiffTable(outputDiffRecords));
        
        html.append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");
        
        return html.toString();
    }

    private static String generateRulesVerificationModal(ComparisonResult result) {
        StringBuilder html = new StringBuilder();
        
        // Read rules from XML file
        InputStream inputStream =  RulesReportBuilder.class.getClassLoader()
        	    .getResourceAsStream("rules/ace-rules.xml");
        List<String> xmlRules = readRulesFromXML(inputStream);
        
        
        System.out.println("========"+xmlRules.size());
        
        // Get unique rules from each category
        java.util.Set<String> matchedRules = new java.util.HashSet<>();
        java.util.Set<String> modifiedRules = new java.util.HashSet<>();
        java.util.Set<String> missedRules = new java.util.HashSet<>();
        java.util.Set<String> newRules = new java.util.HashSet<>();
        
        for (CSVRecord rec : result.matched) {
            if (rec.getRule() != null && !rec.getRule().trim().isEmpty()) {
                matchedRules.add(rec.getRule().trim());
            }
        }
        
        for (ModifiedRecord mod : result.modified) {
            if (mod.current.getRule() != null && !mod.current.getRule().trim().isEmpty()) {
                modifiedRules.add(mod.current.getRule().trim());
            }
        }
        
        for (CSVRecord rec : result.missed) {
            if (rec.getRule() != null && !rec.getRule().trim().isEmpty()) {
                missedRules.add(rec.getRule().trim());
            }
        }
        
        for (CSVRecord rec : result.newRecords) {
            if (rec.getRule() != null && !rec.getRule().trim().isEmpty()) {
                newRules.add(rec.getRule().trim());
            }
        }
        
        // Find rules not processed (in XML but not in any category)
        java.util.Set<String> allProcessedRules = new java.util.HashSet<>();
        allProcessedRules.addAll(matchedRules);
        allProcessedRules.addAll(modifiedRules);
        allProcessedRules.addAll(missedRules);
        allProcessedRules.addAll(newRules);
        
        java.util.Set<String> notProcessedRules = new java.util.HashSet<>();
        for (String xmlRule : xmlRules) {
            if (!allProcessedRules.contains(xmlRule)) {
                notProcessedRules.add(xmlRule);
            }
        }
        
        html.append("<div id='rulesVerificationModal' class='modal'>\n");
        html.append("<div class='modal-content rules-modal-large'>\n");
        html.append("<div class='modal-header'>\n");
        html.append("<h2 class='modal-title'>Rules Verification Report</h2>\n");
        html.append("<div class='modal-controls'>\n");
        html.append("<span class='maximize-btn' onclick='toggleMaximize(\"rulesVerificationModal\")' title='Maximize'>⛶</span>\n");
        html.append("<span class='close-modal' onclick='closeRulesVerificationModal()'>&times;</span>\n");
        html.append("</div>\n");
        html.append("</div>\n");
        html.append("<div class='modal-body'>\n");
        
        // Summary Cards
        html.append("<div class='rules-summary-cards'>\n");
        html.append(createRulesCard("Total Rules in XML", xmlRules.size(), "#3b82f6"));
        html.append(createRulesCard("Matched Rules", matchedRules.size(), "#10b981"));
        html.append(createRulesCard("Modified Rules", modifiedRules.size(), "#f59e0b"));
        html.append(createRulesCard("Missed Rules", missedRules.size(), "#ef4444"));
        html.append(createRulesCard("New Rules", newRules.size(), "#8b5cf6"));
        html.append(createRulesCard("Rules NOT Processed", notProcessedRules.size(), "#dc2626"));
        html.append("</div>\n");
        
        // Detailed sections
        html.append("<div class='rules-tables-container'>\n");
        
        html.append("<div class='rules-table-section'>\n");
        html.append("<h3 class='rules-table-title'>Matched Rules (" + matchedRules.size() + ")</h3>\n");
        html.append(generateRulesTable(matchedRules, "matched-rules-table"));
        html.append("</div>\n");
        
        html.append("<div class='rules-table-section'>\n");
        html.append("<h3 class='rules-table-title'>Modified Rules (" + modifiedRules.size() + ")</h3>\n");
        html.append(generateRulesTable(modifiedRules, "modified-rules-table"));
        html.append("</div>\n");
        
        html.append("<div class='rules-table-section'>\n");
        html.append("<h3 class='rules-table-title'>Missed Rules (" + missedRules.size() + ")</h3>\n");
        html.append(generateRulesTable(missedRules, "missed-rules-table"));
        html.append("</div>\n");
        
        html.append("<div class='rules-table-section'>\n");
        html.append("<h3 class='rules-table-title'>New Rules (" + newRules.size() + ")</h3>\n");
        html.append(generateRulesTable(newRules, "new-rules-table"));
        html.append("</div>\n");
        
        html.append("<div class='rules-table-section rules-not-processed-section'>\n");
        html.append("<h3 class='rules-table-title rules-not-processed-title'>Rules NOT Processed (" + notProcessedRules.size() + ")</h3>\n");
        html.append(generateRulesTable(notProcessedRules, "not-processed-rules-table"));
        html.append("</div>\n");
        
        html.append("</div>\n");
        
        html.append("</div>\n");
        html.append("</div>\n");
        html.append("</div>\n");
        
        return html.toString();
    }

    private static List<String> readRulesFromXML(InputStream inputStream) {
        List<String> rules = new ArrayList<>();
        
        if (inputStream == null) {
            System.err.println("Error: XML file not found in resources");
            return rules;
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("<rule name=\"")) {
                    int startIdx = line.indexOf("name=\"") + 6;
                    int endIdx = line.indexOf("\"", startIdx);
                    if (startIdx > 5 && endIdx > startIdx) {
                        String ruleName = line.substring(startIdx, endIdx);
                        rules.add(ruleName);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading XML file: " + e.getMessage());
        }
        
        return rules;
    }

    private static String createRulesCard(String title, int count, String color) {
        return String.format(
            "<div class='rules-card' style='border-left: 1px solid %s'>\n" +
            "<div class='rules-card-title'>%s</div>\n" +
            "<div class='rules-card-count' style='color: %s'>%d</div>\n" +
            "</div>\n", color, title, color, count);
    }

    private static String generateRulesTable(java.util.Set<String> rules, String id) {
        StringBuilder html = new StringBuilder();
        
        if (rules.isEmpty()) {
            html.append("<div class='rules-empty'>No rules in this category</div>\n");
            return html.toString();
        }
        
        List<String> sortedRules = new ArrayList<>(rules);
        java.util.Collections.sort(sortedRules);
        
        html.append("<div class='table-controls'>\n");
        html.append("<div class='search-container'>\n");
        html.append("<input type='text' id='search-" + id + "' class='search-input' placeholder='Search rules...' oninput='searchRulesTable(\"" + id + "\", this.value)'>\n");
        html.append("<button class='clear-btn' onclick='clearRulesSearch(\"" + id + "\")' title='Clear search'>×</button>\n");
        html.append("</div>\n");
        html.append("<div class='rules-count'>Showing: <span id='count-" + id + "'>" + sortedRules.size() + "</span> / " + sortedRules.size() + "</div>\n");
        html.append("</div>\n");
        
        html.append("<div class='rules-table-wrapper'>\n");
        html.append("<table class='rules-table' id='table-" + id + "'>\n");
        html.append("<thead>\n<tr>\n");
        html.append("<th style='width: 80px;'>#</th>\n");
        html.append("<th>Rule Name</th>\n");
        html.append("</tr>\n</thead>\n");
        html.append("<tbody id='tbody-" + id + "'>\n");
        
        int index = 1;
        for (String rule : sortedRules) {
            html.append("<tr class='rules-table-row'>\n");
            html.append("<td class='rule-index'>" + index + "</td>\n");
            html.append("<td class='rule-name'>" + escapeHtml(rule) + "</td>\n");
            html.append("</tr>\n");
            index++;
        }
        
        html.append("</tbody>\n</table>\n");
        html.append("</div>\n");
        
        return html.toString();
    }

    private static String generateRulesList(java.util.Set<String> rules, String id) {
        StringBuilder html = new StringBuilder();
        
        if (rules.isEmpty()) {
            html.append("<div class='rules-empty'>No rules in this category</div>\n");
            return html.toString();
        }
        
        html.append("<div class='rules-list' id='" + id + "'>\n");
        List<String> sortedRules = new ArrayList<>(rules);
        java.util.Collections.sort(sortedRules);
        
        for (String rule : sortedRules) {
            html.append("<div class='rule-item'>" + escapeHtml(rule) + "</div>\n");
        }
        html.append("</div>\n");
        
        return html.toString();
    }

    private static String generateOutputDiffTable(List<ModifiedRecord> records) {
        StringBuilder html = new StringBuilder();
        String id = "output-diff";
        
        html.append("<div class='table-controls'>\n");
        html.append("<div class='search-container'>\n");
        html.append("<input type='text' id='search-" + id + "' class='search-input' placeholder='Search...' oninput='searchTable(\"" + id + "\", this.value)'>\n");
        html.append("<button class='clear-btn' onclick='clearSearch(\"" + id + "\")' title='Clear search'>×</button>\n");
        html.append("</div>\n");
        html.append("<label>Rows per page: <select class='rows-select' onchange='changeRowsPerPage(\"" + id + "\", this.value)'>\n");
        html.append("<option value='5'>5</option>\n");
        html.append("<option value='10' selected>10</option>\n");
        html.append("<option value='20'>20</option>\n");
        html.append("<option value='50'>50</option>\n");
        html.append("<option value='100'>100</option>\n");
        html.append("</select></label>\n");
        html.append("</div>\n");
        
        html.append("<div class='table-wrapper'>\n<table id='table-" + id + "'>\n<thead>\n<tr>\n");
        html.append("<th onclick='sortTable(\"" + id + "\", 0)'>Type <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 1)'>Rule <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 2)'>FileName <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 3)'>Output <span class='sort-icon'>⇅</span></th>\n");
        html.append("</tr>\n</thead>\n<tbody id='tbody-" + id + "'>\n");
        
        for (ModifiedRecord mod : records) {
            html.append("<tr class='expected-row'>\n");
            html.append("<td class='type-cell'>Expected</td>");
            html.append("<td>" + escapeHtml(mod.expected.getRule()) + "</td>");
            html.append("<td>" + escapeHtml(mod.expected.getFileName()) + "</td>");
            html.append("<td class='modified-cell'>" + escapeHtml(mod.expected.getOutput()) + "</td>\n");
            html.append("</tr>\n");
            
            html.append("<tr class='current-row'>\n");
            html.append("<td class='type-cell'>Current</td>");
            html.append("<td>" + escapeHtml(mod.current.getRule()) + "</td>");
            html.append("<td>" + escapeHtml(mod.current.getFileName()) + "</td>");
            html.append("<td class='modified-cell'>" + escapeHtml(mod.current.getOutput()) + "</td>\n");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n</div>\n");
        html.append("<div class='pagination' id='pagination-" + id + "'></div>\n");
        
        return html.toString();
    }

    private static String generateModifiedTableHTML(List<ModifiedRecord> records) {
        StringBuilder html = new StringBuilder();
        String id = "modified";
        
        html.append("<div class='table-controls'>\n");
        html.append("<div class='search-container'>\n");
        html.append("<input type='text' id='search-" + id + "' class='search-input' placeholder='Search...' oninput='searchTable(\"" + id + "\", this.value)'>\n");
        html.append("<button class='clear-btn' onclick='clearSearch(\"" + id + "\")' title='Clear search'>×</button>\n");
        html.append("</div>\n");
        html.append("<label>Rows per page: <select class='rows-select' onchange='changeRowsPerPage(\"" + id + "\", this.value)'>\n");
        html.append("<option value='5'>5</option>\n");
        html.append("<option value='10' selected>10</option>\n");
        html.append("<option value='20'>20</option>\n");
        html.append("<option value='50'>50</option>\n");
        html.append("<option value='100'>100</option>\n");
        html.append("<option value='200'>200</option>\n");
        html.append("<option value='500'>500</option>\n");
        html.append("</select></label>\n");
        html.append("</div>\n");
        
        html.append("<div class='table-wrapper'>\n<table id='table-" + id + "'>\n<thead>\n<tr>\n");
        html.append("<th onclick='sortTable(\"" + id + "\", 0)'>Type <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 1)'>FileName <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 2)'>Rule <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 3)'>Highlight <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 4)'>ParaStyle <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 5)'>CharStyle <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 6)'>Find <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 7)'>Replace <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 8)'>Input <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 9)'>Output <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 10)'>Stage <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 11)'>Status <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 12)'>Suggestion <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 13)'>InstanceText <span class='sort-icon'>⇅</span></th>\n");
        html.append("<th onclick='sortTable(\"" + id + "\", 14)'>BookMarkName <span class='sort-icon'>⇅</span></th>\n");
        html.append("</tr>\n</thead>\n<tbody id='tbody-" + id + "'>\n");
        
        for (ModifiedRecord mod : records) {
            html.append("<tr class='expected-row'>\n");
            html.append("<td class='type-cell'>Expected</td>");
            html.append("<td>" + escapeHtml(mod.expected.getFileName()) + "</td>");
            html.append("<td>" + escapeHtml(mod.expected.getRule()) + "</td>");
            html.append(getCellHTML(mod.expected.getHighlight(), mod.modifiedFields.contains("Highlight")));
            html.append(getCellHTML(mod.expected.getParaStyle(), mod.modifiedFields.contains("ParaStyle")));
            html.append(getCellHTML(mod.expected.getCharStyle(), mod.modifiedFields.contains("CharStyle")));
            html.append("<td>" + escapeHtml(mod.expected.getFind()) + "</td>");
            html.append("<td>" + escapeHtml(mod.expected.getReplace()) + "</td>");
            html.append("<td>" + escapeHtml(mod.expected.getInput()) + "</td>");
            html.append(getCellHTML(mod.expected.getOutput(), mod.modifiedFields.contains("Output")));
            html.append(getCellHTML(mod.expected.getStage(), mod.modifiedFields.contains("Stage")));
            html.append(getCellHTML(mod.expected.getStatus(), mod.modifiedFields.contains("Status")));
            html.append(getCellHTML(mod.expected.getSuggestion(), mod.modifiedFields.contains("Suggestion")));
            html.append("<td>" + escapeHtml(mod.expected.getInstanceText()) + "</td>\n");
            html.append("<td>" + escapeHtml(mod.expected.getBookMarkName()) + "</td>\n");
            
            html.append("</tr>\n");
            
            html.append("<tr class='current-row'>\n");
            html.append("<td class='type-cell'>Current</td>");
            html.append("<td>" + escapeHtml(mod.current.getFileName()) + "</td>");
            html.append("<td>" + escapeHtml(mod.current.getRule()) + "</td>");
            html.append(getCellHTML(mod.current.getHighlight(), mod.modifiedFields.contains("Highlight")));
            html.append(getCellHTML(mod.current.getParaStyle(), mod.modifiedFields.contains("ParaStyle")));
            html.append(getCellHTML(mod.current.getCharStyle(), mod.modifiedFields.contains("CharStyle")));
            html.append("<td>" + escapeHtml(mod.current.getFind()) + "</td>");
            html.append("<td>" + escapeHtml(mod.current.getReplace()) + "</td>");
            html.append("<td>" + escapeHtml(mod.current.getInput()) + "</td>");
            html.append(getCellHTML(mod.current.getOutput(), mod.modifiedFields.contains("Output")));
            html.append(getCellHTML(mod.current.getStage(), mod.modifiedFields.contains("Stage")));
            html.append(getCellHTML(mod.current.getStatus(), mod.modifiedFields.contains("Status")));
            html.append(getCellHTML(mod.current.getSuggestion(), mod.modifiedFields.contains("Suggestion")));
            html.append("<td>" + escapeHtml(mod.current.getInstanceText()) + "</td>\n");
            html.append("<td>" + escapeHtml(mod.current.getBookMarkName()) + "</td>\n");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n</div>\n");
        html.append("<div class='pagination' id='pagination-" + id + "'></div>\n");
        
        return html.toString();
    }

    private static String getCellHTML(String value, boolean isModified) {
        if (isModified) {
            return "<td class='modified-cell'>" + escapeHtml(value) + "</td>";
        } else {
            return "<td class='no-change'>" + escapeHtml(value) + "</td>";
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private static String currentTimeStamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formatted = now.format(formatter);
        System.out.println("Formatted Date and Time: " + formatted);
        return formatted;
    }

    private static String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
                + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; padding: 20px; }\n"
                + ".container { max-width: 1400px; margin: 0 auto; background: #f8f8f8; padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n"
                + ".header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; }\n"
                + ".title { font-family: Arial, sans-serif; font-size: 2em; font-weight: bold; color: #586408; text-align: center; text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2); }\n"
                + ".currentDateTime { margin-right: 14px; padding: 0px 14px; border-radius: 12px; background: linear-gradient(243deg, #eaeae4, #eaeaf2); color: #2d0404; box-shadow: -1px 1px 1px 0px rgba(2, 6, 23, 0.6), inset 0 1px 0 rgba(255, 255, 255, 0.02); font-family: Inter, \"Segoe UI\", Roboto, system-ui, -apple-system, \"Helvetica Neue\", Arial; user-select: none; }\n"
                + ".datetime { font-size: 14px; color: #6b7280; font-weight: 500; }\n"
                + ".cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 0px; margin-bottom: 30px; }\n"
                + ".card { width:95%;text-align:center; background: white; padding: 9px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); transition: transform 0.2s; }\n"
                + ".card:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.15); }\n"
                + ".card-title { font-size: 14px; color: #6b7280; margin-bottom: 8px; font-weight: 500; }\n"
                + ".card-count { font-size: 17px; font-weight: bold; }\n"
                + ".summary-link { display: inline-block; padding: 1px 206px; background: #fefefe; color: #807101; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); transition: all 0.3s; }\n"
                + ".summary-link:hover { transform: translateY(-2px); box-shadow: 0 6px 12px rgba(0,0,0,0.15); }\n"
                + ".summary-header { margin: 20px 0 20px 0; padding: 15px; background: #fefefe; border-left: 4px solid #fefefe; border-radius: 6px; }\n"
                + ".summary-title { font-size: 1.5em; color: #1e40af; font-weight: 600; margin-bottom: 10px; }\n"
                + ".summary-count { font-size: 1.2em; color: #374151; }\n"
                + ".modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.6); backdrop-filter: blur(4px); }\n"
                + ".modal-content { background-color: #fefefe; margin: 2% auto; padding: 0; border-radius: 12px; width: 90%; max-width: 1200px; box-shadow: 0 10px 40px rgba(0,0,0,0.3); animation: modalSlideIn 0.3s ease-out; }\n"
                + "@keyframes modalSlideIn { from { opacity: 0; transform: translateY(-50px); } to { opacity: 1; transform: translateY(0); } }\n"
                + ".modal-header { padding: 0px 1px; background: #9f9555; color: white; border-radius: 12px 12px 0 0; display: flex; justify-content: space-between; align-items: center; }\n"
                + ".modal-title { margin: 0; font-size: 1.8em; font-weight: 600; }\n"
                + ".close-modal { color: white; font-size: 35px; font-weight: bold; cursor: pointer; transition: all 0.3s; line-height: 1; }\n"
                + ".close-modal:hover { transform: scale(1.2); color: #ffd700; }\n"
                + ".modal-body { padding: 30px; max-height: calc(100vh - 200px); overflow-y: auto; }\n"
                + ".tabs { display: flex; gap: 10px; margin-bottom: 20px; border-bottom: 2px solid #e5e7eb; }\n"
                + ".tab-btn { border-radius:5px; padding: 12px 24px; border: none; background: #eaebec; cursor: pointer; font-size: 14px; font-weight: 500; color: #6b7280; transition: all 0.3s; border-bottom: 3px solid transparent; }\n"
                + ".tab-btn:hover { color: #3b82f6; background: #bdb286; }\n"
                + ".tab-btn.active { color: #082656; border-bottom-color: #082656; background: #bdb286;}\n"
                + ".tab-content { display: none; }\n"
                + ".tab-content.active { display: block; }\n"
                + ".table-controls { margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center; gap: 15px; }\n"
                + ".search-container { position: relative; width: 300px; }\n"
                + ".search-input { padding: 8px 35px 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 100%; transition: border-color 0.2s; font-size: 14px; }\n"
                + ".search-input:focus { outline: none; border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }\n"
                + ".clear-btn { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); background: none; border: none; font-size: 24px; color: #9ca3af; cursor: pointer; padding: 0; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: all 0.2s; }\n"
                + ".clear-btn:hover { background: #f3f4f6; color: #ef4444; }\n"
                + ".rows-select { padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; cursor: pointer; transition: border-color 0.2s; }\n"
                + ".rows-select:hover { border-color: #3b82f6; }\n"
                + ".table-wrapper { border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; }\n"
                + "::-webkit-scrollbar { width: 12px; height: 12px; }\n"
                + "::-webkit-scrollbar-track { background: #f1f1f1; border-radius: 10px; }\n"
                + "::-webkit-scrollbar-thumb { background: #888; border-radius: 10px; }\n"
                + "::-webkit-scrollbar-thumb:hover { background: #555; }\n"
                + "table { width: 100%; border-collapse: collapse; display: block; }\n"
                + "thead { position: sticky; top: 0; background: #cec49c; z-index: 10; display: table; width: 100%; table-layout: fixed; }\n"
                + "tbody { display: block; max-height: 400px; overflow-y: auto; overflow-x: auto; }\n"
                + "tbody tr { display: table; width: 100%; table-layout: fixed; }\n"
                + "th { padding: 0px; text-align: left; font-weight: 600; color: #374151; border-bottom: 2px solid #e5e7eb; white-space: normal; word-wrap: break-word; font-size: 13px; cursor: pointer; user-select: none; position: relative;}\n"
                + "th:hover { background: #bdb88f; }\n"
                + ".sort-icon { margin-left: 5px; color: #9ca3af; font-size: 14px; }\n"
                + "th.sort-asc .sort-icon { color: #3b82f6; }\n"
                + "th.sort-desc .sort-icon { color: #3b82f6; }\n"
                + "th.sort-asc .sort-icon::after { content: ' ▲'; }\n"
                + "th.sort-desc .sort-icon::after { content: ' ▼'; }\n"
                + "td { padding: 12px; border-bottom: 1px solid #f3f4f6; font-size: 13px; white-space: normal; word-wrap: break-word;}\n"
                + "tbody tr { transition: background-color 0.2s; }\n"
                + "tbody tr:hover { background-color: #f9fafb; }\n"
                + ".modified-cell { background-color: #fef3c7; font-weight: 500; }\n"
                + ".no-change { background-color: #fafafa; }\n"
                + ".expected-row { border-left: 4px solid #ef4444; }\n"
                + ".current-row { border-left: 4px solid #3b82f6; }\n"
                + ".type-cell { font-weight: 600; font-size: 12px; text-transform: uppercase; }\n"
                + ".pagination { display: flex; justify-content: center; align-items: center; gap: 8px; margin-top: 20px; flex-wrap: wrap; }\n"
                + ".pagination button { padding: 8px 12px; border: 1px solid #d1d5db; background: white; cursor: pointer; border-radius: 6px; transition: all 0.2s; font-size: 13px; }\n"
                + ".pagination button:hover:not(:disabled) { background: #3b82f6; color: white; border-color: #3b82f6; }\n"
                + ".pagination button:disabled { cursor: not-allowed; opacity: 0.5; }\n"
                + ".pagination button.active { background: #3b82f6; color: white; border-color: #3b82f6; font-weight: 600; }\n"
                + ".pagination-info { color: #6b7280; font-size: 13px; margin: 0 10px; }\n"
                + ".rules-summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px; }\n"
                + ".rules-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }\n"
                + ".rules-card-title { font-size: 14px; color: #6b7280; margin-bottom: 10px; font-weight: 500; }\n"
                + ".rules-card-count { font-size: 28px; font-weight: bold; }\n"
                + ".rules-modal-large .modal-body { padding: 20px; }\n"
                + ".rules-modal-large.maximized { width: 98%; height: 98vh; max-width: none; margin: 1vh auto; }\n"
                + ".rules-modal-large.maximized .modal-body { max-height: calc(98vh - 120px); }\n"
                + ".modal-controls { display: flex; align-items: center; gap: 10px; }\n"
                + ".maximize-btn { color: white; font-size: 28px; font-weight: bold; cursor: pointer; transition: all 0.3s; line-height: 1; padding: 0 10px; }\n"
                + ".maximize-btn:hover { transform: scale(1.2); color: #ffd700; }\n"
                + ".rules-tables-container { display: flex; flex-direction: column; gap: 25px; }\n"
                + ".rules-table-section { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }\n"
                + ".rules-table-title { font-size: 1.3em; color: #374151; font-weight: 600; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px solid #e5e7eb; }\n"
                + ".rules-not-processed-section { border: 2px solid #fecaca; background: #fef2f2; }\n"
                + ".rules-not-processed-title { color: #dc2626; }\n"
                + ".rules-table-wrapper { border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; max-height: 400px; overflow-y: auto; }\n"
                + ".rules-table { width: 100%; border-collapse: collapse; }\n"
                + ".rules-table thead { position: sticky; top: 0; background: #f3f4f6; z-index: 5; }\n"
                + ".rules-table th { padding: 12px; text-align: center; font-weight: 600; color: #374151; border-bottom: 2px solid #e5e7eb; font-size: 14px; }\n"
                + ".rules-table td { padding: 12px; border-bottom: 1px solid #f3f4f6; font-size: 13px; }\n"
                + ".rules-table-row:hover { background-color: #f9fafb; }\n"
                + ".rule-index { font-weight: 600; color: #6b7280; text-align: center; }\n"
                + ".rule-name { color: #374151; word-break: break-word; }\n"
                + ".rules-count { font-size: 14px; color: #6b7280; font-weight: 500; }\n"
                + ".rules-empty { padding: 40px; text-align: center; color: #9ca3af; font-style: italic; font-size: 16px; background: #f9fafb; border-radius: 8px; }\n"
                + ".rules-section { margin-bottom: 30px; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }\n"
                + ".rules-section-title { font-size: 1.3em; color: #374151; font-weight: 600; margin-bottom: 15px; padding-bottom: 10px; border-bottom: 2px solid #e5e7eb; }\n"
                + ".rules-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 10px; max-height: 300px; overflow-y: auto; padding: 10px; background: #f9fafb; border-radius: 6px; }\n"
                + ".rule-item { padding: 10px 15px; background: white; border-radius: 6px; font-size: 13px; color: #374151; border-left: 3px solid #3b82f6; box-shadow: 0 1px 3px rgba(0,0,0,0.1); word-break: break-word; }\n"
                + ".rules-not-processed { border: 2px solid #fecaca; background: #fef2f2; }\n"
                + ".rules-not-processed .rules-section-title { color: #dc2626; }";
    }
    
    public static String getJavaScript() {
        return "const paginationState = {};\n" +
                "const searchState = {};\n" +
                "const sortState = {};\n" +
                "function openModifiedSummaryModal() {\n" +
                "  document.getElementById('modifiedSummaryModal').style.display = 'block';\n" +
                "  if (!paginationState['output-diff']) {\n" +
                "    initPagination('output-diff', 10);\n" +
                "  }\n" +
                "}\n" +
                "function closeModifiedSummaryModal() {\n" +
                "  document.getElementById('modifiedSummaryModal').style.display = 'none';\n" +
                "}\n" +
                "function openRulesVerificationModal() {\n" +
                "  document.getElementById('rulesVerificationModal').style.display = 'block';\n" +
                "}\n" +
                "function closeRulesVerificationModal() {\n" +
                "  const modal = document.getElementById('rulesVerificationModal');\n" +
                "  modal.style.display = 'none';\n" +
                "  const modalContent = modal.querySelector('.modal-content');\n" +
                "  modalContent.classList.remove('maximized');\n" +
                "}\n" +
                "function toggleMaximize(modalId) {\n" +
                "  const modal = document.getElementById(modalId);\n" +
                "  const modalContent = modal.querySelector('.modal-content');\n" +
                "  modalContent.classList.toggle('maximized');\n" +
                "}\n" +
                "function searchRulesTable(tableId, searchTerm) {\n" +
                "  const tbody = document.getElementById('tbody-' + tableId);\n" +
                "  if (!tbody) return;\n" +
                "  const rows = tbody.getElementsByTagName('tr');\n" +
                "  const search = searchTerm.toLowerCase().trim();\n" +
                "  let visibleCount = 0;\n" +
                "  for (let i = 0; i < rows.length; i++) {\n" +
                "    const ruleName = rows[i].querySelector('.rule-name');\n" +
                "    if (ruleName) {\n" +
                "      const text = ruleName.textContent.toLowerCase();\n" +
                "      if (search === '' || text.includes(search)) {\n" +
                "        rows[i].style.display = '';\n" +
                "        visibleCount++;\n" +
                "      } else {\n" +
                "        rows[i].style.display = 'none';\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "  const countSpan = document.getElementById('count-' + tableId);\n" +
                "  if (countSpan) {\n" +
                "    countSpan.textContent = visibleCount;\n" +
                "  }\n" +
                "}\n" +
                "function clearRulesSearch(tableId) {\n" +
                "  const searchInput = document.getElementById('search-' + tableId);\n" +
                "  if (searchInput) {\n" +
                "    searchInput.value = '';\n" +
                "    searchRulesTable(tableId, '');\n" +
                "  }\n" +
                "}\n" +
                "window.onclick = function(event) {\n" +
                "  const modifiedModal = document.getElementById('modifiedSummaryModal');\n" +
                "  const rulesModal = document.getElementById('rulesVerificationModal');\n" +
                "  if (event.target == modifiedModal) {\n" +
                "    closeModifiedSummaryModal();\n" +
                "  }\n" +
                "  if (event.target == rulesModal) {\n" +
                "    closeRulesVerificationModal();\n" +
                "  }\n" +
                "}\n" +
                "function updateDateTime() {\n" +
                "  const now = new Date();\n" +
                "  const options = { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true };\n" +
                "  document.getElementById('datetime').textContent = now.toLocaleString('en-US', options);\n" +
                "}\n" +
                "function openTab(evt, tabName) {\n" +
                "  const contents = document.getElementsByClassName('tab-content');\n" +
                "  for (let i = 0; i < contents.length; i++) { contents[i].classList.remove('active'); }\n" +
                "  const btns = document.getElementsByClassName('tab-btn');\n" +
                "  for (let i = 0; i < btns.length; i++) { btns[i].classList.remove('active'); }\n" +
                "  document.getElementById(tabName).classList.add('active');\n" +
                "  evt.currentTarget.classList.add('active');\n" +
                "  if (!paginationState[tabName]) { initPagination(tabName, 10); }\n" +
                "}\n" +
                "function initPagination(tabId, rowsPerPage) {\n" +
                "  const tbody = document.getElementById('tbody-' + tabId);\n" +
                "  if (!tbody) return;\n" +
                "  const rows = Array.from(tbody.getElementsByTagName('tr'));\n" +
                "  paginationState[tabId] = { currentPage: 1, rowsPerPage: rowsPerPage, totalRows: rows.length, allRows: rows };\n" +
                "  searchState[tabId] = { filteredRows: rows, searchTerm: '' };\n" +
                "  sortState[tabId] = { column: -1, direction: 'none' };\n" +
                "  renderPagination(tabId);\n" +
                "}\n" +
                "function sortTable(tabId, columnIndex) {\n" +
                "  if (!paginationState[tabId]) return;\n" +
                "  const state = paginationState[tabId];\n" +
                "  const sort = sortState[tabId];\n" +
                "  const table = document.getElementById('table-' + tabId);\n" +
                "  const headers = table.querySelectorAll('th');\n" +
                "  headers.forEach((h, i) => {\n" +
                "    h.classList.remove('sort-asc', 'sort-desc');\n" +
                "  });\n" +
                "  if (sort.column === columnIndex) {\n" +
                "    if (sort.direction === 'asc') {\n" +
                "      sort.direction = 'desc';\n" +
                "      headers[columnIndex].classList.add('sort-desc');\n" +
                "    } else if (sort.direction === 'desc') {\n" +
                "      sort.direction = 'none';\n" +
                "      sort.column = -1;\n" +
                "    } else {\n" +
                "      sort.direction = 'asc';\n" +
                "      headers[columnIndex].classList.add('sort-asc');\n" +
                "    }\n" +
                "  } else {\n" +
                "    sort.column = columnIndex;\n" +
                "    sort.direction = 'asc';\n" +
                "    headers[columnIndex].classList.add('sort-asc');\n" +
                "  }\n" +
                "  if (sort.direction === 'none') {\n" +
                "    state.allRows.sort((a, b) => {\n" +
                "      return Array.from(state.allRows).indexOf(a) - Array.from(state.allRows).indexOf(b);\n" +
                "    });\n" +
                "  } else {\n" +
                "    state.allRows.sort((a, b) => {\n" +
                "      const aVal = a.getElementsByTagName('td')[columnIndex]?.textContent.trim() || '';\n" +
                "      const bVal = b.getElementsByTagName('td')[columnIndex]?.textContent.trim() || '';\n" +
                "      const aNum = parseFloat(aVal);\n" +
                "      const bNum = parseFloat(bVal);\n" +
                "      let comparison = 0;\n" +
                "      if (!isNaN(aNum) && !isNaN(bNum)) {\n" +
                "        comparison = aNum - bNum;\n" +
                "      } else {\n" +
                "        comparison = aVal.localeCompare(bVal, undefined, { numeric: true, sensitivity: 'base' });\n" +
                "      }\n" +
                "      return sort.direction === 'asc' ? comparison : -comparison;\n" +
                "    });\n" +
                "  }\n" +
                "  const tbody = document.getElementById('tbody-' + tabId);\n" +
                "  state.allRows.forEach(row => tbody.appendChild(row));\n" +
                "  if (searchState[tabId].searchTerm) {\n" +
                "    searchTable(tabId, searchState[tabId].searchTerm);\n" +
                "  } else {\n" +
                "    searchState[tabId].filteredRows = state.allRows;\n" +
                "    state.currentPage = 1;\n" +
                "    renderPagination(tabId);\n" +
                "  }\n" +
                "}\n" +
                "function searchTable(tabId, searchTerm) {\n" +
                "  if (!paginationState[tabId]) return;\n" +
                "  const state = paginationState[tabId];\n" +
                "  const search = searchTerm.toLowerCase().trim();\n" +
                "  searchState[tabId].searchTerm = searchTerm;\n" +
                "  if (search === '') {\n" +
                "    searchState[tabId].filteredRows = state.allRows;\n" +
                "  } else {\n" +
                "    const filteredRows = state.allRows.filter(row => {\n" +
                "      const cells = row.getElementsByTagName('td');\n" +
                "      for (let i = 0; i < cells.length; i++) {\n" +
                "        const text = cells[i].textContent.toLowerCase();\n" +
                "        if (text.includes(search)) return true;\n" +
                "      }\n" +
                "      return false;\n" +
                "    });\n" +
                "    searchState[tabId].filteredRows = filteredRows;\n" +
                "  }\n" +
                "  state.currentPage = 1;\n" +
                "  state.totalRows = searchState[tabId].filteredRows.length;\n" +
                "  renderPagination(tabId);\n" +
                "}\n" +
                "function clearSearch(tabId) {\n" +
                "  const searchInput = document.getElementById('search-' + tabId);\n" +
                "  if (searchInput) {\n" +
                "    searchInput.value = '';\n" +
                "    searchTable(tabId, '');\n" +
                "  }\n" +
                "}\n" +
                "function renderPagination(tabId) {\n" +
                "  const state = paginationState[tabId];\n" +
                "  if (!state || !searchState[tabId]) return;\n" +
                "  const filteredRows = searchState[tabId].filteredRows;\n" +
                "  const totalPages = Math.max(1, Math.ceil(state.totalRows / state.rowsPerPage));\n" +
                "  state.allRows.forEach(row => { row.style.display = 'none'; });\n" +
                "  const start = (state.currentPage - 1) * state.rowsPerPage;\n" +
                "  const end = start + state.rowsPerPage;\n" +
                "  filteredRows.forEach((row, idx) => {\n" +
                "    if (idx >= start && idx < end) { row.style.display = ''; }\n" +
                "  });\n" +
                "  const paginationDiv = document.getElementById('pagination-' + tabId);\n" +
                "  if (!paginationDiv) return;\n" +
                "  let html = '<button onclick=\"changePage(\\'' + tabId + '\\', 1)\" ' + (state.currentPage === 1 || state.totalRows === 0 ? 'disabled' : '') + '>&lt;&lt; Start</button>';\n" +
                "  html += '<button onclick=\"changePage(\\'' + tabId + '\\', ' + (state.currentPage - 1) + ')\" ' + (state.currentPage === 1 || state.totalRows === 0 ? 'disabled' : '') + '>&lt; Previous</button>';\n" +
                "  const startPage = Math.max(1, state.currentPage - 2);\n" +
                "  const endPage = Math.min(totalPages, state.currentPage + 2);\n" +
                "  for (let i = startPage; i <= endPage; i++) {\n" +
                "    html += '<button onclick=\"changePage(\\'' + tabId + '\\', ' + i + ')\" class=\"' + (i === state.currentPage ? 'active' : '') + '\">' + i + '</button>';\n" +
                "  }\n" +
                "  html += '<button onclick=\"changePage(\\'' + tabId + '\\', ' + (state.currentPage + 1) + ')\" ' + (state.currentPage === totalPages || state.totalRows === 0 ? 'disabled' : '') + '>Next &gt;</button>';\n" +
                "  html += '<button onclick=\"changePage(\\'' + tabId + '\\', ' + totalPages + ')\" ' + (state.currentPage === totalPages || state.totalRows === 0 ? 'disabled' : '') + '>End &gt;&gt;</button>';\n" +
                "  const displayInfo = state.totalRows === 0 ? 'No results found' : 'Page ' + state.currentPage + ' of ' + totalPages + ' (Showing ' + state.totalRows + ' rows)';\n" +
                "  html += '<span class=\"pagination-info\">' + displayInfo + '</span>';\n" +
                "  paginationDiv.innerHTML = html;\n" +
                "}\n" +
                "function changePage(tabId, page) {\n" +
                "  const state = paginationState[tabId];\n" +
                "  if (!state) return;\n" +
                "  const totalPages = Math.ceil(state.totalRows / state.rowsPerPage);\n" +
                "  if (page < 1 || page > totalPages || state.totalRows === 0) return;\n" +
                "  state.currentPage = page;\n" +
                "  renderPagination(tabId);\n" +
                "}\n" +
                "function changeRowsPerPage(tabId, rows) {\n" +
                "  if (!paginationState[tabId]) return;\n" +
                "  paginationState[tabId].rowsPerPage = parseInt(rows);\n" +
                "  paginationState[tabId].currentPage = 1;\n" +
                "  renderPagination(tabId);\n" +
                "}\n" +
                "window.onload = function() { updateDateTime(); setInterval(updateDateTime, 1000); initPagination('matched', 10); };";
    
}
       
    private static String createCard(String title, int count, String color) {
        return String.format(
            "<div class='card' style='border-left: 0px solid %s'>\n" +
            "<div class='card-title'>%s</div>\n" +
            "<div class='card-count' style='color: %s'>%d</div>\n" +
            "</div>\n", color, title, color, count);
    }

    private static String generateTableHTML(String id, List<CSVRecord> records, String type) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class='table-controls'>\n");
        html.append("<div class='search-container'>\n");
        html.append("<input type='text' id='search-" + id + "' class='search-input' placeholder='Search...' oninput='searchTable(\"" + id + "\", this.value)'>\n");
        html.append("<button class='clear-btn' onclick='clearSearch(\"" + id + "\")' title='Clear search'>×</button>\n");
        html.append("</div>\n");
        html.append("<label>Select Rows: <select class='rows-select' onchange='changeRowsPerPage(\"" + id + "\", this.value)'>\n");
        html.append("<option value='5'>5</option>\n");
        html.append("<option value='10' selected>10</option>\n");
        html.append("<option value='20'>20</option>\n");
        html.append("<option value='50'>50</option>\n");
        html.append("<option value='100'>100</option>\n");
        html.append("<option value='200'>200</option>\n");
        html.append("<option value='500'>500</option>\n");
        html.append("</select></label>\n");
        html.append("</div>\n");
        
        html.append("<div class='table-wrapper'>\n<table id='table-" + id + "'>\n<thead>\n<tr>\n");
        html.append("<th onclick='sortTable(\"" + id + "\", 0)'>FileName <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 1)'>Rule <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 2)'>Highlight <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 3)'>ParaStyle <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 4)'>CharStyle <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 5)'>Find <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 6)'>Replace <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 7)'>Input <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 8)'>Output <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 9)'>Stage <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 10)'>Status <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 11)'>Suggestion <span class='sort-icon'>⇅</span></th>");
        html.append("<th onclick='sortTable(\"" + id + "\", 12)'>InstanceText <span class='sort-icon'>⇅</span></th>\n");
        html.append("<th onclick='sortTable(\"" + id + "\", 12)'>BookMarkName <span class='sort-icon'>⇅</span></th>\n");
        html.append("</tr>\n</thead>\n<tbody id='tbody-" + id + "'>\n");
        
        for (CSVRecord record : records) {
            html.append("<tr>\n");
            html.append("<td>" + escapeHtml(record.getFileName()) + "</td>");
            html.append("<td>" + escapeHtml(record.getRule()) + "</td>");
            html.append("<td>" + escapeHtml(record.getHighlight()) + "</td>");
            html.append("<td>" + escapeHtml(record.getParaStyle()) + "</td>");
            html.append("<td>" + escapeHtml(record.getCharStyle()) + "</td>");
            html.append("<td>" + escapeHtml(record.getFind()) + "</td>");
            html.append("<td>" + escapeHtml(record.getReplace()) + "</td>");
            html.append("<td>" + escapeHtml(record.getInput()) + "</td>");
            html.append("<td>" + escapeHtml(record.getOutput()) + "</td>");
            html.append("<td>" + escapeHtml(record.getStage()) + "</td>");
            html.append("<td>" + escapeHtml(record.getStatus()) + "</td>");
            html.append("<td>" + escapeHtml(record.getSuggestion()) + "</td>");
            html.append("<td>" + escapeHtml(record.getInstanceText()) + "</td>\n");
            html.append("<td>" + escapeHtml(record.getBookMarkName()) + "</td>\n");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n</div>\n");
        html.append("<div class='pagination' id='pagination-" + id + "'></div>\n");
        html.append("<script>window.onload = function() { initPagination('output-diff', 10); };</script>\n");
    
    return html.toString();
    }
}