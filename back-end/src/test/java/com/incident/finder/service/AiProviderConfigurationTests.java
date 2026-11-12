package com.incident.finder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

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
    void failsClearlyWhenSelectedProviderKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=gemini")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Gemini is selected but app.ai.gemini.api-key, model, and base-url must be configured."
                            );
                });
    }

    @Test
    void failsClearlyWhenClaudeKeyIsMissing() {
        contextRunner
                .withPropertyValues("app.ai.provider=claude")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Claude is selected but app.ai.claude.api-key, model, and url must be configured."
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
                                    "Unsupported app.ai.provider 'openai'. Supported providers are 'claude' and 'gemini'."
                            );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            AiProviderConfiguration.class,
            ClaudeApiClient.class,
            GeminiApiClient.class
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
