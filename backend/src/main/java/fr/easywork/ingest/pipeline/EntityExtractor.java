package fr.easywork.ingest.pipeline;

import fr.easywork.document.event.ExtractedEntityPayload;
import fr.easywork.document.event.ExtractedEntityType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, regex-based extraction of dates, amounts, IBANs and reference
 * numbers from a document's already-extracted text (ADR 0003) — no NLP
 * library, no external service. Runs once per document, right after
 * Tika/Tesseract text extraction, inside {@link IngestPipeline}.
 */
@Component
@Profile("ingest")
class EntityExtractor {

    // dd/mm/yyyy or dd-mm-yyyy
    private static final Pattern DATE_DMY =
        Pattern.compile("\\b(\\d{2})[/\\-](\\d{2})[/\\-](\\d{4})\\b");
    // ISO yyyy-mm-dd
    private static final Pattern DATE_ISO =
        Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    // 1 234,56 € / 1234.56€ / €1234,56 / 1234,56 EUR
    private static final Pattern AMOUNT_SUFFIX =
        Pattern.compile("(\\d{1,3}(?:[ .]\\d{3})*(?:[,.]\\d{2})?)\\s?(?:€|EUR\\b)");
    private static final Pattern AMOUNT_PREFIX =
        Pattern.compile("€\\s?(\\d{1,3}(?:[ .]\\d{3})*(?:[,.]\\d{2})?)");

    // ISO 13616 IBAN, optionally space-grouped in 4s (e.g. FR76 3000 6000 ...)
    private static final Pattern IBAN =
        Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){2,7}(?:\\s?[A-Z0-9]{1,3})?\\b");

    // "Référence: ABC-123", "Ref# INV2024001", "N° 45678", "Invoice # 9981"
    private static final Pattern REFERENCE = Pattern.compile(
        "(?i:r[ée]f(?:[ée]rence)?|invoice\\s*#|n°|no\\.)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9\\-/]{2,29})");

    List<ExtractedEntityPayload> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ExtractedEntityPayload> results = new ArrayList<>();
        extractDates(text, results);
        extractAmounts(text, results);
        extractIbans(text, results);
        extractReferences(text, results);
        return results;
    }

    private void extractDates(String text, List<ExtractedEntityPayload> results) {
        Matcher dmy = DATE_DMY.matcher(text);
        while (dmy.find()) {
            String raw = dmy.group();
            toIsoDate(dmy.group(3), dmy.group(2), dmy.group(1))
                .ifPresent(iso -> results.add(new ExtractedEntityPayload(ExtractedEntityType.DATE, raw, iso)));
        }
        Matcher iso = DATE_ISO.matcher(text);
        while (iso.find()) {
            String raw = iso.group();
            toIsoDate(iso.group(1), iso.group(2), iso.group(3))
                .ifPresent(normalized ->
                    results.add(new ExtractedEntityPayload(ExtractedEntityType.DATE, raw, normalized)));
        }
    }

    private static java.util.Optional<String> toIsoDate(String year, String month, String day) {
        try {
            return java.util.Optional.of(
                LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return java.util.Optional.empty();
        }
    }

    private void extractAmounts(String text, List<ExtractedEntityPayload> results) {
        addAmountMatches(AMOUNT_SUFFIX.matcher(text), results);
        addAmountMatches(AMOUNT_PREFIX.matcher(text), results);
    }

    private void addAmountMatches(Matcher matcher, List<ExtractedEntityPayload> results) {
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = normalizeAmount(matcher.group(1));
            if (normalized != null) {
                results.add(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, raw, normalized));
            }
        }
    }

    /** "1 234,56" / "1.234,56" / "1234.56" -> "1234.56" (plain decimal, dot separator). */
    private static String normalizeAmount(String value) {
        String cleaned = value.replaceAll("[ .](?=\\d{3})", "");
        int lastComma = cleaned.lastIndexOf(',');
        if (lastComma >= 0) {
            cleaned = cleaned.substring(0, lastComma) + "." + cleaned.substring(lastComma + 1);
        }
        try {
            return new java.math.BigDecimal(cleaned).toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void extractIbans(String text, List<ExtractedEntityPayload> results) {
        Matcher matcher = IBAN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            String normalized = raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            results.add(new ExtractedEntityPayload(ExtractedEntityType.IBAN, raw, normalized));
        }
    }

    private void extractReferences(String text, List<ExtractedEntityPayload> results) {
        Matcher matcher = REFERENCE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            results.add(new ExtractedEntityPayload(ExtractedEntityType.REFERENCE, matcher.group(), value));
        }
    }
}
