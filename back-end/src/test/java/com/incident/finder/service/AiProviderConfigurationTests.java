package com.incident.finder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

class AiProviderConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderTestConfiguration.class);

    @Test
    void activatesOnlyClaudeWhenClaudeIsSelected() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=claude",
                        "app.ai.claude.api-key=test-claude-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("selectedAiAnalysisProvider");
                    assertThat(context.getBean(AiAnalysisProvider.class))
                            .isInstanceOf(ClaudeApiClient.class);
                    assertThat(context).hasBean("com.incident.finder.service.ClaudeApiClient");
                    assertThat(context).doesNotHaveBean(GeminiApiClient.class);
                });
    }

    @Test
    void activatesOnlyGeminiWhenGeminiIsSelected() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=gemini",
                        "app.ai.gemini.api-key=test-gemini-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("selectedAiAnalysisProvider");
                    assertThat(context.getBean(AiAnalysisProvider.class))
                            .isInstanceOf(GeminiApiClient.class);
                    assertThat(context).hasBean("com.incident.finder.service.GeminiApiClient");
                    assertThat(context).doesNotHaveBean(ClaudeApiClient.class);
                });
    }

    @Test
    void activatesOnlyGroqWhenGroqIsSelected() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=groq",
                        "app.ai.groq.api-key=test-groq-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("selectedAiAnalysisProvider");
                    assertThat(context.getBean(AiAnalysisProvider.class))
                            .isInstanceOf(GroqApiClient.class);
                    assertThat(context).hasBean("com.incident.finder.service.GroqApiClient");
                    assertThat(context).doesNotHaveBean(ClaudeApiClient.class);
                    assertThat(context).doesNotHaveBean(GeminiApiClient.class);
                });
    }

    @Test
    void allowsStandardSearchStartupWhenGroqKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=groq")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiAnalysisProvider provider = context.getBean(AiAnalysisProvider.class);
                    assertThat(provider).isInstanceOf(GroqApiClient.class);
                    assertThrows(
                            AiProviderException.class,
                            () -> provider.analyze("issue", List.of())
                    );
                });
    }

    @Test
    void allowsStandardSearchStartupWhenGeminiKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=gemini")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiAnalysisProvider provider = context.getBean(AiAnalysisProvider.class);
                    assertThat(provider).isInstanceOf(GeminiApiClient.class);
                    assertThrows(
                            AiProviderException.class,
                            () -> provider.analyze("issue", List.of())
                    );
                });
    }

    @Test
    void allowsStandardSearchStartupWhenClaudeKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=claude")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiAnalysisProvider provider = context.getBean(AiAnalysisProvider.class);
                    assertThat(provider).isInstanceOf(ClaudeApiClient.class);
                    assertThrows(
                            AiProviderException.class,
                            () -> provider.analyze("issue", List.of())
                    );
                });
    }

    @Test
    void failsClearlyWhenProviderIsUnsupported() {
        contextRunner
                .withPropertyValues("app.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Unsupported app.ai.provider 'openai'. Supported providers are 'claude', 'gemini', and 'groq'."
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            AiProviderConfiguration.class,
            ClaudeApiClient.class,
            GeminiApiClient.class,
            GroqApiClient.class
    })
    static class ProviderTestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
