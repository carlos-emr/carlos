/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Local text extraction only. No browser, external resources, scripts, OCR, or model calls. */
final class ClinicalSummaryTextExtractor {
    record Extract(String text, boolean complete, String reason) { }
    private record Cached(Extract extract, long created, int bytes) { }
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private static final int CACHE_BYTES = 8 * 1024 * 1024;
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int cachedBytes;

    static Extract document(String filename, String contentType) throws IOException {
        File directory = PathValidationUtils.resolveConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
        File file = PathValidationUtils.validateExistingPath(new File(directory, filename), directory);
        try (var input = Files.newInputStream(file.toPath())) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) return new Extract("", false, "File exceeds the text reader's memory limit; open the original.");
            return bytes(bytes, contentType);
        }
    }

    static Extract bytes(byte[] bytes, String contentType) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) return new Extract("", false, "File exceeds the text reader's memory limit; open the original.");
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        String key;
        try {
            key = type + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
        synchronized (CACHE) {
            Cached cached = CACHE.get(key);
            if (cached != null && System.nanoTime() - cached.created() < Duration.ofMinutes(15).toNanos()) return cached.extract();
        }
        Extract extract = read(bytes, type);
        int size = 2 * (extract.text().length() + extract.reason().length());
        if (size <= CACHE_BYTES) {
            synchronized (CACHE) {
                Cached previous = CACHE.put(key, new Cached(extract, System.nanoTime(), size));
                if (previous != null) cachedBytes -= previous.bytes();
                cachedBytes += size;
                var oldest = CACHE.values().iterator();
                while ((CACHE.size() > 64 || cachedBytes > CACHE_BYTES) && oldest.hasNext()) {
                    cachedBytes -= oldest.next().bytes();
                    oldest.remove();
                }
            }
        }
        return extract;
    }

    private static Extract read(byte[] bytes, String type) throws IOException {
        if ("application/pdf".equals(type)) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                if (!pdf.getCurrentAccessPermission().canExtractContent()) {
                    return new Extract("", false, "PDF does not permit text extraction.");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                StringBuilder text = new StringBuilder();
                boolean emptyPage = false;
                boolean readablePage = false;
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String body = stripper.getText(pdf);
                    emptyPage |= body.isBlank();
                    readablePage |= !body.isBlank();
                    text.append("\nPage ").append(page).append(":\n").append(body);
                    if (text.length() > MAX_FILE_BYTES) return new Extract("", false, "Extracted text exceeds the reader's memory limit; open the original.");
                }
                return new Extract(readablePage ? text.toString() : "", false, emptyPage
                        ? "One or more pages have no readable text. Images, handwriting and visual layout require review of the original."
                        : "PDF text was extracted from every page. Images, handwriting and visual layout require review of the original.");
            }
        }
        if ("text/plain".equals(type) || "text/html".equals(type)) {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return "text/html".equals(type) ? html(text) : new Extract(text, true, "Complete stored plain text.");
        }
        return new Extract("", false, "This format has no supported text reader; open the original. Scans require OCR or manual review.");
    }

    static Extract html(String html) {
        if (html == null || html.isBlank()) return new Extract("", false, "No stored form content.");
        var document = Jsoup.parse(html);
        document.select("script,style,template,iframe,object,embed").remove();
        for (Element field : document.select("input,textarea,select")) {
            String type = field.attr("type").toLowerCase(Locale.ROOT);
            if ("hidden".equals(type) || "password".equals(type) || "submit".equals(type) || "button".equals(type)) {
                field.remove();
                continue;
            }
            String name = field.attr("name").isBlank() ? field.id() : field.attr("name");
            String value;
            if ("checkbox".equals(type) || "radio".equals(type)) {
                value = (field.hasAttr("checked") ? "checked" : "unchecked") + "; stored value: " + field.val();
            } else if ("select".equals(field.tagName())) {
                value = field.select("option[selected]").text();
                if (value.isBlank()) value = "No explicit stored selection";
            } else {
                value = field.val();
            }
            field.replaceWith(new Element("p").text("Recorded field " + name + ": " + value));
        }
        return new Extract(document.body().wholeText().strip(), false,
                "Stored text and field values only. Scripts, hidden fields, images and calculated or externally loaded content were not evaluated; review the original form.");
    }
}
