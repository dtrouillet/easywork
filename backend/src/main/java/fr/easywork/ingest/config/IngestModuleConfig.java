package fr.easywork.ingest.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("ingest")
@EnableConfigurationProperties(IngestProperties.class)
public class IngestModuleConfig {

    @Bean
    Tesseract tesseract(IngestProperties props) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(props.tessdataPath());
        tesseract.setLanguage(props.ocrLanguages());
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);
        return tesseract;
    }
}
