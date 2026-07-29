package fr.easywork.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("search")
@EnableConfigurationProperties(SearchProperties.class)
public class SearchModuleConfig {

    @Bean
    Client meilisearchClient(SearchProperties props) {
        return new Client(new Config(props.host(), props.apiKey()));
    }
}
