package com.atheryon.mortgages.migration.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stage 1 — Parse CSV to raw records.
 * Handles BOM markers, quoted fields, mixed line endings, empty rows,
 * and auto-detects delimiter (comma, semicolon, tab).
 */
@Service
public class CsvParserService {

    private static final Logger log = LoggerFactory.getLogger(CsvParserService.class);

    public record ParseResult(
            List<String> headers,
            List<Map<String, String>> rows,
            char delimiter
    ) {}

    public ParseResult parse(InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // Strip BOM if present
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        // Normalise line endings to \n
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));

        // Remove trailing empty lines
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        // Auto-detect delimiter from header row
        char delimiter = detectDelimiter(lines.get(0));
        log.info("Detected delimiter: '{}'", delimiter == '\t' ? "TAB" : String.valueOf(delimiter));

        // Parse header row
        List<String> headers = parseLine(lines.get(0), delimiter);
        log.info("Parsed {} columns from header", headers.size());

        // Parse data rows
        List<Map<String, String>> rows = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue; // skip empty rows
            }

            List<String> fields = parseLine(line, delimiter);

            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String value = j < fields.size() ? fields.get(j).trim() : "";
                row.put(headers.get(j), value);
            }
            rows.add(row);
        }

        log.info("Parsed {} data rows", rows.size());
        return new ParseResult(headers, rows, delimiter);
    }

    /**
     * Detect the most likely delimiter by counting occurrences outside quoted regions.
     */
    private char detectDelimiter(String headerLine) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        counts.put(',', 0);
        counts.put(';', 0);
        counts.put('\t', 0);

        boolean inQuotes = false;
        for (char c : headerLine.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && counts.containsKey(c)) {
                counts.merge(c, 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    /**
     * Parse a single CSV line respecting quoted fields.
     */
    private List<String> parseLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped quote ("")
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == delimiter) {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }

        fields.add(current.toString());
        return fields;
    }
}
