package fr.easywork.ingest.pipeline;

import fr.easywork.document.event.ExtractedEntityPayload;
import fr.easywork.document.event.ExtractedEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityExtractorTest {

    private final EntityExtractor extractor = new EntityExtractor();

    @Test
    void extract_findsFrenchStyleDate() {
        List<ExtractedEntityPayload> result = extractor.extract("Facture émise le 23/09/2022 pour le client");

        assertThat(result)
            .contains(new ExtractedEntityPayload(ExtractedEntityType.DATE, "23/09/2022", "2022-09-23"));
    }

    @Test
    void extract_findsIsoDate() {
        List<ExtractedEntityPayload> result = extractor.extract("Document date: 2022-09-23");

        assertThat(result)
            .contains(new ExtractedEntityPayload(ExtractedEntityType.DATE, "2022-09-23", "2022-09-23"));
    }

    @Test
    void extract_ignoresInvalidDate() {
        List<ExtractedEntityPayload> result = extractor.extract("Reference number 99/99/9999 is not a date");

        assertThat(result).noneMatch(e -> e.type() == ExtractedEntityType.DATE);
    }

    @Test
    void extract_findsAmountWithSuffixEuroSign() {
        List<ExtractedEntityPayload> result = extractor.extract("Montant total : 1 234,56 € TTC");

        assertThat(result)
            .contains(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "1 234,56 €", "1234.56"));
    }

    @Test
    void extract_findsAmountWithPrefixEuroSign() {
        List<ExtractedEntityPayload> result = extractor.extract("Total: €123.45 due");

        assertThat(result)
            .contains(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "€123.45", "123.45"));
    }

    @Test
    void extract_findsAmountWithEurSuffix() {
        List<ExtractedEntityPayload> result = extractor.extract("Amount due: 99,00 EUR");

        assertThat(result)
            .contains(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "99,00 EUR", "99.00"));
    }

    @Test
    void extract_findsIban() {
        List<ExtractedEntityPayload> result =
            extractor.extract("IBAN: FR76 3000 6000 0112 3456 7890 189 - merci");

        assertThat(result)
            .contains(new ExtractedEntityPayload(
                ExtractedEntityType.IBAN, "FR76 3000 6000 0112 3456 7890 189", "FR7630006000011234567890189"));
    }

    @Test
    void extract_findsReferenceAfterKeyword() {
        List<ExtractedEntityPayload> result = extractor.extract("Référence: INV-2024-001 pour votre commande");

        assertThat(result)
            .anyMatch(e -> e.type() == ExtractedEntityType.REFERENCE && "INV-2024-001".equals(e.normalizedValue()));
    }

    @Test
    void extract_returnsEmptyList_whenTextIsBlank() {
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void extract_returnsEmptyList_whenTextIsNull() {
        assertThat(extractor.extract(null)).isEmpty();
    }
}
