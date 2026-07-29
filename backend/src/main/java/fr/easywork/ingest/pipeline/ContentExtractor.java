package fr.easywork.ingest.pipeline;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Profile("ingest")
class ContentExtractor {

    private static final int NATIVE_TEXT_THRESHOLD = 50;
    private final AutoDetectParser parser = new AutoDetectParser();

    ExtractionResult extract(InputStream input) throws Exception {
        var handler = new BodyContentHandler(-1);
        var metadata = new Metadata();
        parser.parse(input, handler, metadata, new ParseContext());

        String text = handler.toString().trim();
        String mimeType = metadata.get("Content-Type");
        boolean requiresOcr = text.length() < NATIVE_TEXT_THRESHOLD;

        return new ExtractionResult(text, mimeType, requiresOcr);
    }
}
