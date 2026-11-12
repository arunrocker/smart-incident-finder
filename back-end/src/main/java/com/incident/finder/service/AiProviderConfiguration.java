package com.incident.finder.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties({
        AiProviderProperties.class,
        ClaudeApiProperties.class,
        GeminiApiProperties.class
})
public class AiProviderConfiguration {

    @Bean
    @Primary
    public AiAnalysisProvider selectedAiAnalysisProvider(
            AiProviderProperties properties,
            ObjectProvider<ClaudeApiClient> claudeProvider,
            ObjectProvider<GeminiApiClient> geminiProvider
    ) {
        String provider = properties.getProvider();
        if ("claude".equals(provider)) {
            return claudeProvider.getObject();
        }
        if ("gemini".equals(provider)) {
            return geminiProvider.getObject();
        }
        throw new IllegalStateException(
                "Unsupported app.ai.provider '%s'. Supported providers are 'claude' and 'gemini'."
                        .formatted(provider)
        );
    }
}
