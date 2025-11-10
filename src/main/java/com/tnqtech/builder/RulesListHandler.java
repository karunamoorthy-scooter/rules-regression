package com.tnqtech.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class RulesListHandler {

    /**
     * Generates XML files for all rule categories
     * @param result ComparisonResult containing all categories
     * @param outputDirectory Directory where XML files will be created
     * @throws IOException if file writing fails
     */
    public void generateRulesXML(ComparisonResult result, String outputDirectory) throws IOException {
        // Create output directory if it doesn't exist
        Path dirPath = Paths.get(outputDirectory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Generate XML for each category
        generateXMLForCategory(result.matched, Paths.get(outputDirectory, "MatchedRules.xml").toString(), "Matched Changes");
        generateXMLForCategory(extractRecordsFromModified(result.modified), Paths.get(outputDirectory, "ModifiedRules.xml").toString(), "Modified Changes");
        generateXMLForCategory(result.missed, Paths.get(outputDirectory, "MissedRules.xml").toString(), "Missed Changes");
        generateXMLForCategory(result.newRecords, Paths.get(outputDirectory, "NewRules.xml").toString(), "New Changes");

        System.out.println("XML rule files generated successfully in: " + outputDirectory);
    }

    /**
     * Extracts CSVRecord list from ModifiedRecord list
     */
    private List<CSVRecord> extractRecordsFromModified(List<ModifiedRecord> modifiedRecords) {
        List<CSVRecord> records = new ArrayList<>();
        for (ModifiedRecord mod : modifiedRecords) {
            records.add(mod.expected);
        }
        return records;
    }

    /**
     * Generates XML file for a specific category
     */
    private void generateXMLForCategory(List<CSVRecord> records, String outputFilePath, String categoryName) throws IOException {
        // Count rule occurrences
        Map<String, Integer> ruleCounts = countRules(records);

        // Generate XML content
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<!-- ").append(categoryName).append(" - Total Rules: ").append(ruleCounts.size()).append(" -->\n");
        xml.append("<rules>\n");

        // Add each rule with count
        for (Map.Entry<String, Integer> entry : ruleCounts.entrySet()) {
            xml.append(String.format("  <rule count=\"%d\" name=\"%s\"/>\n", 
                entry.getValue(), 
                escapeXml(entry.getKey())));
        }

        xml.append("</rules>");

        // Write to file
        Files.write(Paths.get(outputFilePath), xml.toString().getBytes("UTF-8"));
        System.out.println("  - " + categoryName + ": " + outputFilePath + " (" + ruleCounts.size() + " unique rules)");
    }

    /**
     * Counts occurrences of each rule in the record list
     * Returns LinkedHashMap to preserve insertion order
     */
    private Map<String, Integer> countRules(List<CSVRecord> records) {
        Map<String, Integer> ruleCounts = new LinkedHashMap<>();
        
        for (CSVRecord record : records) {
            String ruleName = record.getRule();
            if (ruleName != null && !ruleName.trim().isEmpty()) {
                ruleCounts.put(ruleName, ruleCounts.getOrDefault(ruleName, 0) + 1);
            }
        }
        
        return ruleCounts;
    }

    /**
     * Escapes special XML characters
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}