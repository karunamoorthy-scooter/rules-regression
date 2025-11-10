package com.tnqtech.report;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * CSVCompareAndReport
 *
 * Usage: java CSVCompareAndReport expected.csv current.csv comparison-report.html
 *
 * - Reads both CSV files (first row as header)
 * - Compares full-row equality (Rules Matched)
 * - Finds partial matches based on fields: FileName, Rule, Highlight, ParaStyle, CharStyle, Find (Mismatched Rules)
 * - Remaining expected rows -> Missed Rules
 * - Remaining current rows -> New Rules
 * - Generates a professional HTML report with 4 tabs (Rules Matched, Mismatched Rules, Missed Rules, New Rules)
 *   Each table has: search, page-size select (10,20,50,100), pagination buttons.
 *
 * NOTE: This is a single-file utility with a small CSV parser that supports quoted fields.
 */
public class CSVCompareAndReport {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java CSVCompareAndReport expected.csv current.csv output.html");
            return;
        }
        Path expectedPath = Paths.get(args[0]);
        Path currentPath = Paths.get(args[1]);
        Path outPath = Paths.get(args[2]);

        List<Map<String, String>> expected = readCsv(expectedPath);
        List<Map<String, String>> current = readCsv(currentPath);

        ReportData report = compare(expected, current);

        String html = generateHtml(report);
        Files.write(outPath, html.getBytes());
        System.out.println("Report generated: " + outPath.toAbsolutePath());
    }

    // Basic CSV parser that supports quoted fields with escaped quotes "" and commas inside quotes.
    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            List<String> headers = parseCsvLine(headerLine);
            String line;
            while ((line = br.readLine()) != null) {
                List<String> values = parseCsvLine(line);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i);
                    String val = i < values.size() ? values.get(i) : "";
                    map.put(key, val);
                }
                rows.add(map);
            }
        }
        return rows;
    }

    // parse a CSV line into fields
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null || line.isEmpty()) return fields;
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // check for escaped quote
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    private static ReportData compare(List<Map<String, String>> expected, List<Map<String, String>> current) {
        // Prepare full-row strings preserving order
        List<Row> expRows = toRows(expected);
        List<Row> curRows = toRows(current);

        // Full-string match -> Rules Matched
        LinkedHashSet<String> curFullSet = new LinkedHashSet<>();
        for (Row r : curRows) curFullSet.add(r.fullString);

        List<Row> rulesMatched = new ArrayList<>();
        List<Row> expRemaining = new ArrayList<>();
        for (Row r : expRows) {
            if (curFullSet.contains(r.fullString)) {
                rulesMatched.add(r);
            } else {
                expRemaining.add(r);
            }
        }

        // Remove matched full-strings from current remaining list
        List<Row> curRemaining = new ArrayList<>();
        for (Row r : curRows) {
            if (!containsFull(r.fullString, rulesMatched)) curRemaining.add(r);
        }

        // Partial matching based on specific keys -> Mismatched Rules (pair expected,current)
        List<MismatchPair> mismatched = new ArrayList<>();
        List<Row> expAfterPartial = new ArrayList<>(expRemaining);
        List<Row> curAfterPartial = new ArrayList<>(curRemaining);

        String[] keys = new String[] {"FileName","Rule","Highlight","ParaStyle","CharStyle","Find"};

        // For each expected row, try to find a current row with exact equality on these keys
        Iterator<Row> expIt = expAfterPartial.iterator();
        while (expIt.hasNext()) {
            Row e = expIt.next();
            boolean paired = false;
            Iterator<Row> curIt = curAfterPartial.iterator();
            while (curIt.hasNext()) {
                Row c = curIt.next();
                if (keysEqual(e.map, c.map, keys)) {
                    mismatched.add(new MismatchPair(e, c));
                    curIt.remove();
                    paired = true;
                    break;
                }
            }
            if (paired) expIt.remove();
        }

        // Remaining expected -> Missed Rules
        List<Row> missed = new ArrayList<>(expAfterPartial);
        // Remaining current -> New Rules
        List<Row> news = new ArrayList<>(curAfterPartial);

        return new ReportData(rulesMatched, mismatched, missed, news);
    }

    private static boolean containsFull(String full, List<Row> list) {
        for (Row r : list) if (r.fullString.equals(full)) return true;
        return false;
    }

    private static boolean keysEqual(Map<String, String> a, Map<String, String> b, String[] keys) {
        for (String k : keys) {
            String va = a.getOrDefault(k, "").trim();
            String vb = b.getOrDefault(k, "").trim();
            if (!va.equals(vb)) return false;
        }
        return true;
    }

    private static List<Row> toRows(List<Map<String, String>> list) {
        List<Row> out = new ArrayList<>();
        for (Map<String,String> map : list) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String,String> e : map.entrySet()) {
                if (!first) sb.append("||");
                sb.append(e.getKey()).append("=").append(e.getValue());
                first = false;
            }
            out.add(new Row(map, sb.toString()));
        }
        return out;
    }

    // Simple data holders
    private static class Row {
        Map<String,String> map;
        String fullString;
        Row(Map<String,String> m, String f) { this.map = m; this.fullString = f; }
    }

    private static class MismatchPair {
        Row expected;
        Row current;
        MismatchPair(Row e, Row c) { this.expected = e; this.current = c; }
    }

    private static class ReportData {
        List<Row> rulesMatched;
        List<MismatchPair> mismatched;
        List<Row> missed;
        List<Row> news;
        ReportData(List<Row> a, List<MismatchPair> b, List<Row> c, List<Row> d) {
            this.rulesMatched = a; this.mismatched = b; this.missed = c; this.news = d;
        }
    }

    // Generate a standalone HTML that embeds data as JSON-like arrays and provides search/pagination controls.
    private static String generateHtml(ReportData r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\\n<html lang=\"en\">\\n<head>\\n<meta charset=\"utf-8\">\\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\\n<title>CSV Comparison Report</title>\\n<style>\\nbody{font-family:Arial,Helvetica,sans-serif;margin:0;padding:16px;background:#f5f7fb;color:#222}\\n.container{max-width:1200px;margin:0 auto;background:#fff;padding:20px;border-radius:8px;box-shadow:0 6px 18px rgba(12,20,40,0.08)}\\n.header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}\\n.tabs{display:flex;gap:8px;margin-bottom:12px}\\n.tab{padding:8px 12px;border-radius:6px;background:#eef2ff;cursor:pointer;color:#0b3;}\r\n"
        		+ ".tab.active{background:#0b63ff;color:white}\r\n"
        		+ ".controls{display:flex;gap:8px;align-items:center;margin-bottom:8px}\r\n"
        		+ ".table-wrap{overflow:auto}\r\n"
        		+ "table{width:100%;border-collapse:collapse}\r\n"
        		+ "th,td{padding:8px;border-bottom:1px solid #e6eef8;text-align:left}\r\n"
        		+ "th{background:#f0f6ff}\r\n"
        		+ ".pager{display:flex;gap:6px;align-items:center;margin-top:8px}\r\n"
        		+ ".btn{padding:6px 10px;border-radius:6px;border:1px solid #dbe9ff;background:white;cursor:pointer}\r\n"
        		+ ".btn:disabled{opacity:0.5;cursor:not-allowed}\r\n"
        		+ ".small{font-size:0.9em;color:#555}\r\n"
        		+ ".badge{display:inline-block;padding:4px 8px;border-radius:12px;background:#e6f7ff;color:#036}\r\n"
        		+ "</style>\\n</head><body>\\n<div class=\"container\">\\n  <div class=\"header\">\\n    <h2>CSV Comparison Report</h2>\r\n"
        		+ "    <div class=\"small\">Generated: \" + new Date().toString() + \"</div>\r\n"
        		+ "  </div>\\n  <div class=\"tabs\">\\n    <div class=\"tab active\" data-tab=\"matched\">Rules Matched <span class=\"badge\">\" + r.rulesMatched.size() + \"</span></div>\\n    <div class=\"tab\" data-tab=\"mismatched\">Mismatched Rules <span class=\"badge\">\" + r.mismatched.size() + \"</span></div>\\n    <div class=\"tab\" data-tab=\"missed\">Missed Rules <span class=\"badge\">\" + r.missed.size() + \"</span></div>\\n    <div class=\"tab\" data-tab=\"new\">New Rules <span class=\"badge\">\" + r.news.size() + \"</span></div>\\n  </div>\\n\r\n"
        		+ "  <div class=\"controls\">\\n    <div>Show <select id=\"pageSize\"><option>10</option><option>20</option><option>50</option><option>100</option></select> entries</div>\r\n"
        		+ "    <div style=\"margin-left:auto\">Search: <input id=\"searchBox\" placeholder=\"search...\"></div>\r\n"
        		+ "  </div>\\n\r\n"
        		+ "  <div id=\"content\">\\n    <div class=\"table-wrap\" data-panel=\"matched\">\\n      \" + toTableHtmlForMatched(r.rulesMatched) + \"\\n    </div>\\n    <div class=\"table-wrap\" data-panel=\"mismatched\" style=\"display:none\">\\n      \" + toTableHtmlForMismatched(r.mismatched) + \"\\n    </div>\\n    <div class=\"table-wrap\" data-panel=\"missed\" style=\"display:none\">\\n      \" + toTableHtmlForRows(r.missed) + \"\\n    </div>\\n    <div class=\"table-wrap\" data-panel=\"new\" style=\"display:none\">\\n      \" + toTableHtmlForRows(r.news) + \"\\n    </div>\\n  </div>\\n\r\n"
        		+ "  <div class=\"small\">Note: Partial matches are determined by equality of the fields FileName, Rule, Highlight, ParaStyle, CharStyle and Find.</div>\r\n"
        		+ "</div>\r\n"
        		+ "\r\n"
        		+ "<script>\r\n"
        		+ "// Simple tab handling\r\n"
        		+ "const tabs = document.querySelectorAll('.tab');\r\n"
        		+ "const panels = document.querySelectorAll('[data-panel]');\r\n"
        		+ "const searchBox = document.getElementById('searchBox');\r\n"
        		+ "const pageSizeSelect = document.getElementById('pageSize');\r\n"
        		+ "let currentPanel = 'matched';\r\n"
        		+ "let currentPage = 1;\r\n"
        		+ "let pageSize = parseInt(pageSizeSelect.value);\r\n"
        		+ "\r\n"
        		+ "tabs.forEach(t => t.addEventListener('click', () => {\r\n"
        		+ "  tabs.forEach(x=>x.classList.remove('active'));\r\n"
        		+ "  t.classList.add('active');\r\n"
        		+ "  currentPanel = t.getAttribute('data-tab');\r\n"
        		+ "  currentPage = 1;\r\n"
        		+ "  renderPanel();\r\n"
        		+ "}));\r\n"
        		+ "\r\n"
        		+ "searchBox.addEventListener('input', () => { currentPage = 1; renderPanel(); });\r\n"
        		+ "pageSizeSelect.addEventListener('change', () => { pageSize = parseInt(pageSizeSelect.value); currentPage = 1; renderPanel(); });\r\n"
        		+ "\r\n"
        		+ "function renderPanel() {\r\n"
        		+ "  panels.forEach(p => p.style.display = p.getAttribute('data-panel')===currentPanel ? '' : 'none');\r\n"
        		+ "  const table = document.querySelector('[data-panel=\"'+currentPanel+'\"]').querySelector('table');\r\n"
        		+ "  const tbody = table.querySelector('tbody');\r\n"
        		+ "  const rows = Array.from(tbody.querySelectorAll('tr'));\r\n"
        		+ "  const q = searchBox.value.trim().toLowerCase();\r\n"
        		+ "  let filtered = rows.filter(r => r.textContent.toLowerCase().includes(q));\r\n"
        		+ "  const total = filtered.length;\r\n"
        		+ "  const totalPages = Math.max(1, Math.ceil(total / pageSize));\r\n"
        		+ "  if (currentPage > totalPages) currentPage = totalPages;\r\n"
        		+ "  const start = (currentPage - 1) * pageSize;\r\n"
        		+ "  const end = start + pageSize;\r\n"
        		+ "  rows.forEach(r => r.style.display = 'none');\r\n"
        		+ "  filtered.slice(start, end).forEach(r => r.style.display = 'table-row');\r\n"
        		+ "\r\n"
        		+ "  // render pager\r\n"
        		+ "  const pager = document.getElementById('pager-'+currentPanel);\r\n"
        		+ "  pager.innerHTML = '';\r\n"
        		+ "  const prev = makeBtn('Prev', () => { if (currentPage>1) { currentPage--; renderPanel(); } });\r\n"
        		+ "  prev.disabled = currentPage===1;\r\n"
        		+ "  pager.appendChild(prev);\r\n"
        		+ "  const info = document.createElement('span'); info.className='small'; info.style.margin='0 8px'; info.textContent = 'Page '+currentPage+' of '+totalPages+' ('+total+' items)';\r\n"
        		+ "  pager.appendChild(info);\r\n"
        		+ "  const next = makeBtn('Next', () => { if (currentPage<totalPages) { currentPage++; renderPanel(); } });\r\n"
        		+ "  next.disabled = currentPage===totalPages;\r\n"
        		+ "  pager.appendChild(next);\r\n"
        		+ "}\r\n"
        		+ "\r\n"
        		+ "function makeBtn(txt, fn){ const b=document.createElement('button'); b.className='btn'; b.textContent=txt; b.onclick=fn; return b; }\r\n"
        		+ "\r\n"
        		+ "// initial render\r\n"
        		+ "renderPanel();\r\n"
        		+ "</script>\r\n"
        		+ "\r\n"
        		+ "</body></html>");
        return sb.toString();
    }

    private static String toTableHtmlForMatched(List<Row> rows) {
        if (rows.isEmpty()) return "<div class='small'>No matched rows.</div>";
        // Use header from first row
        List<String> cols = new ArrayList<>(rows.get(0).map.keySet());
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        for (String c : cols) sb.append("<th>").append(escapeHtml(c)).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (Row r : rows) {
            sb.append("<tr>");
            for (String c : cols) sb.append("<td>").append(escapeHtml(r.map.getOrDefault(c, ""))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table><div id='pager-matched' class='pager'></div>");
        return sb.toString();
    }

    private static String toTableHtmlForRows(List<Row> rows) {
        if (rows.isEmpty()) return "<div class='small'>No rows.</div>";
        List<String> cols = new ArrayList<>(rows.get(0).map.keySet());
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        for (String c : cols) sb.append("<th>").append(escapeHtml(c)).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (Row r : rows) {
            sb.append("<tr>");
            for (String c : cols) sb.append("<td>").append(escapeHtml(r.map.getOrDefault(c, ""))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table><div id='pager-"+ (rows==null?"":"") +"' class='pager'></div>");
        // Note: pager id will be provided properly by wrapper that places this table in panel with data-panel name. We'll add specific pager ids next.
        // For missed and new panels, replace pager id to unique value
        return sb.toString().replace("id='pager-" + (rows==null?"":"") + "'", "id='pager-missed'");
    }

    private static String toTableHtmlForMismatched(List<MismatchPair> pairs) {
        if (pairs.isEmpty()) return "<div class='small'>No mismatched rows.</div>";
        // We'll show two side-by-side sets of columns: expected | current
        // Collect union of headers
        LinkedHashSet<String> headers = new LinkedHashSet<>();
        for (MismatchPair p : pairs) { headers.addAll(p.expected.map.keySet()); headers.addAll(p.current.map.keySet()); }
        List<String> cols = new ArrayList<>(headers);
        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        for (String c : cols) sb.append("<th>Exp: ").append(escapeHtml(c)).append("</th>");
        for (String c : cols) sb.append("<th>Cur: ").append(escapeHtml(c)).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (MismatchPair p : pairs) {
            sb.append("<tr>");
            for (String c : cols) sb.append("<td>").append(escapeHtml(p.expected.map.getOrDefault(c, ""))).append("</td>");
            for (String c : cols) sb.append("<td>").append(escapeHtml(p.current.map.getOrDefault(c, ""))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table><div id='pager-mismatched' class='pager'></div>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}

