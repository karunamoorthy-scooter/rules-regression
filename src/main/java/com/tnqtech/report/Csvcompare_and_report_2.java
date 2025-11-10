package com.tnqtech.report;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Csvcompare_and_report_2 {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java CSVCompareAndReport <Expected.csv> <Current.csv> <Report.html>");
            return;
        }
        String expectedPath = args[0];
        String currentPath = args[1];
        String reportPath = args[2];

        List<List<String>> expectedRows = readCsv(expectedPath);
        List<List<String>> currentRows = readCsv(currentPath);

        List<List<String>> rulesMatched = new ArrayList<>();
        List<MismatchPair> mismatchedRules = new ArrayList<>();
        List<List<String>> missedRules = new ArrayList<>();
        List<List<String>> newRules = new ArrayList<>();

        // Remove header row for comparison
        List<String> header = expectedRows.get(0);
        expectedRows.remove(0);
        currentRows.remove(0);

        // Exact match removal
        Iterator<List<String>> expIt = expectedRows.iterator();
        while (expIt.hasNext()) {
            List<String> expRow = expIt.next();
            Iterator<List<String>> curIt = currentRows.iterator();
            boolean matched = false;
            while (curIt.hasNext()) {
                List<String> curRow = curIt.next();
                if (expRow.equals(curRow)) {
                    rulesMatched.add(expRow);
                    expIt.remove();
                    curIt.remove();
                    matched = true;
                    break;
                }
            }
        }

        // Mismatched detection based on selected columns
        String[] keys = {"FileName", "Rule", "Highlight", "ParaStyle", "CharStyle", "Find"};
        for (Iterator<List<String>> expIter = expectedRows.iterator(); expIter.hasNext();) {
            List<String> expRow = expIter.next();
            Map<String, String> expMap = toMap(header, expRow);
            boolean foundPartial = false;
            for (Iterator<List<String>> curIter = currentRows.iterator(); curIter.hasNext();) {
                List<String> curRow = curIter.next();
                Map<String, String> curMap = toMap(header, curRow);
                boolean partial = true;
                for (String k : keys) {
                    if (!Objects.equals(expMap.getOrDefault(k, ""), curMap.getOrDefault(k, ""))) {
                        partial = false;
                        break;
                    }
                }
                if (partial && !expRow.equals(curRow)) {
                    mismatchedRules.add(new MismatchPair(expRow, curRow));
                    expIter.remove();
                    curIter.remove();
                    foundPartial = true;
                    break;
                }
            }
            if (foundPartial) continue;
        }

        missedRules.addAll(expectedRows);
        newRules.addAll(currentRows);

        String html = generateHtml(header, rulesMatched, mismatchedRules, missedRules, newRules);
        Files.write(Paths.get(reportPath), html.getBytes());
        System.out.println("Report generated: " + reportPath);
    }

    private static List<List<String>> readCsv(String path) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                List<String> values = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                boolean inQuotes = false;
                for (char c : line.toCharArray()) {
                    if (c == '"') inQuotes = !inQuotes;
                    else if (c == ',' && !inQuotes) {
                        values.add(sb.toString().trim());
                        sb.setLength(0);
                    } else sb.append(c);
                }
                values.add(sb.toString().trim());
                rows.add(values);
            }
        }
        return rows;
    }

    private static Map<String, String> toMap(List<String> header, List<String> row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < header.size() && i < row.size(); i++) {
            map.put(header.get(i), row.get(i));
        }
        return map;
    }

    private static String generateHtml(List<String> header, List<List<String>> rulesMatched, List<MismatchPair> mismatchedRules, List<List<String>> missedRules, List<List<String>> newRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'><style>");
        sb.append("body{font-family:Arial;margin:20px;} .tabs{display:flex;cursor:pointer;margin-bottom:10px;} .tab{padding:10px 20px;border:1px solid #ccc;border-bottom:none;background:#f1f1f1;margin-right:5px;} .tab.active{background:white;font-weight:bold;} .panel{display:none;border:1px solid #ccc;padding:10px;} .panel.active{display:block;} table{width:100%;border-collapse:collapse;} th,td{border:1px solid #ddd;padding:5px;} th{background:#f9f9f9;} .search{margin-bottom:10px;} .diff{background-color:#ffecb3;font-weight:bold;} .exp-row{background-color:#f5f5f5;} .cur-row{background-color:#ffffff;} ");
        sb.append("</style></head><body>");

        sb.append("<div class='tabs'>");
        sb.append("<div class='tab active' data-target='matched'>Rules Matched</div>");
        sb.append("<div class='tab' data-target='mismatched'>Mismatched Rules</div>");
        sb.append("<div class='tab' data-target='missed'>Missed Rules</div>");
        sb.append("<div class='tab' data-target='new'>New Rules</div></div>");

        sb.append("<div id='matched' class='panel active'>").append(toTableHtml(header, rulesMatched)).append("</div>");
        sb.append("<div id='mismatched' class='panel'>").append(toMismatchTableHtml(header, mismatchedRules)).append("</div>");
        sb.append("<div id='missed' class='panel'>").append(toTableHtml(header, missedRules)).append("</div>");
        sb.append("<div id='new' class='panel'>").append(toTableHtml(header, newRules)).append("</div>");

        sb.append("<script>\n");
        sb.append("const tabs=document.querySelectorAll('.tab');tabs.forEach(t=>t.onclick=()=>{tabs.forEach(x=>x.classList.remove('active'));document.querySelectorAll('.panel').forEach(p=>p.classList.remove('active'));t.classList.add('active');document.getElementById(t.dataset.target).classList.add('active');});\n");
        sb.append("</script></body></html>");
        return sb.toString();
    }

    private static String toTableHtml(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<input class='search' placeholder='Search...' onkeyup='filterTable(this)'/>\n");
        sb.append("<table><thead><tr>");
        for (String h : header) sb.append("<th>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            sb.append("<tr>");
            for (String val : row) sb.append("<td>").append(escapeHtml(val)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String toMismatchTableHtml(List<String> header, List<MismatchPair> mismatched) {
        StringBuilder sb = new StringBuilder();
        sb.append("<input class='search' placeholder='Search...' onkeyup='filterTable(this)'/>\n");
        sb.append("<table><thead><tr><th>Type</th>");
        for (String h : header) sb.append("<th>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (MismatchPair pair : mismatched) {
            sb.append("<tr class='exp-row'><td><b>Exp:</b></td>");
            for (int i = 0; i < header.size(); i++) {
                String expVal = i < pair.exp.size() ? pair.exp.get(i) : "";
                String curVal = i < pair.cur.size() ? pair.cur.get(i) : "";
                if (!Objects.equals(expVal, curVal))
                    sb.append("<td class='diff'>").append(escapeHtml(expVal)).append("</td>");
                else sb.append("<td>").append(escapeHtml(expVal)).append("</td>");
            }
            sb.append("</tr>");
            sb.append("<tr class='cur-row'><td><b>Cur:</b></td>");
            for (int i = 0; i < header.size(); i++) {
                String expVal = i < pair.exp.size() ? pair.exp.get(i) : "";
                String curVal = i < pair.cur.size() ? pair.cur.get(i) : "";
                if (!Objects.equals(expVal, curVal))
                    sb.append("<td class='diff'>").append(escapeHtml(curVal)).append("</td>");
                else sb.append("<td>").append(escapeHtml(curVal)).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static class MismatchPair {
        List<String> exp;
        List<String> cur;
        MismatchPair(List<String> e, List<String> c){this.exp=e;this.cur=c;}
    }
}
