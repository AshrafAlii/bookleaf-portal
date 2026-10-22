package com.bookleaf.portal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view over the {@code app.*} configuration block.
 *
 * <p>Every secret and API key is read from the environment (see
 * {@code application.yml}); nothing sensitive is compiled into the artefact.</p>
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final Seed seed = new Seed();
    private final Ai ai = new Ai();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    public Seed getSeed() {
        return seed;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Jwt {
        private String secret = "local-dev-secret-change-me-please-32-chars-min";
        private String issuer = "bookleaf-portal";
        private Duration accessTokenTtl = Duration.ofMinutes(30);
        private Duration refreshTokenTtl = Duration.ofDays(7);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Security {
        /** Set to true in deployed environments so the refresh cookie is HTTPS-only. */
        private boolean secureCookies = false;

        public boolean isSecureCookies() {
            return secureCookies;
        }

        public void setSecureCookies(boolean secureCookies) {
            this.secureCookies = secureCookies;
        }
    }

    public static class Seed {
        private boolean enabled = true;
        private String dataPath = "classpath:data/bookleaf_sample_data.json";
        private String authorPassword = "Author@1234";
        private String adminPassword = "Admin@1234";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getAuthorPassword() {
            return authorPassword;
        }

        public void setAuthorPassword(String authorPassword) {
            this.authorPassword = authorPassword;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    public static class Ai {
        /** {@code auto} picks a provider from whichever API key is present. */
        private String provider = "auto";
        private final Anthropic anthropic = new Anthropic();
        private final OpenAi openai = new OpenAi();
        private String triageModel = "";
        private String draftModel = "";
        private int triageMaxOutputTokens = 300;
        private int draftMaxOutputTokens = 700;
        private Duration requestTimeout = Duration.ofSeconds(20);
        private String knowledgeBasePath = "classpath:knowledge/knowledge-base.md";
        private int knowledgeTopSections = 3;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Anthropic getAnthropic() {
            return anthropic;
        }

        public OpenAi getOpenai() {
            return openai;
        }

        public String getTriageModel() {
            return triageModel;
        }

        public void setTriageModel(String triageModel) {
            this.triageModel = triageModel;
        }

        public String getDraftModel() {
            return draftModel;
        }

        public void setDraftModel(String draftModel) {
            this.draftModel = draftModel;
        }

        public int getTriageMaxOutputTokens() {
            return triageMaxOutputTokens;
        }

        public void setTriageMaxOutputTokens(int triageMaxOutputTokens) {
            this.triageMaxOutputTokens = triageMaxOutputTokens;
        }

        public int getDraftMaxOutputTokens() {
            return draftMaxOutputTokens;
        }

        public void setDraftMaxOutputTokens(int draftMaxOutputTokens) {
            this.draftMaxOutputTokens = draftMaxOutputTokens;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public String getKnowledgeBasePath() {
            return knowledgeBasePath;
        }

        public void setKnowledgeBasePath(String knowledgeBasePath) {
            this.knowledgeBasePath = knowledgeBasePath;
        }

        public int getKnowledgeTopSections() {
            return knowledgeTopSections;
        }

        public void setKnowledgeTopSections(int knowledgeTopSections) {
            this.knowledgeTopSections = knowledgeTopSections;
        }

        /**
         * Resolves the provider that will actually be used.
         *
         * <p>{@code auto} prefers Anthropic when its key is present, then OpenAI,
         * and finally falls back to {@code none} — in which case the system runs
         * on its deterministic heuristic path with no network calls at all.</p>
         */
        public String resolvedProvider() {
            String configured = provider == null ? "auto" : provider.toLowerCase();
            return switch (configured) {
                case "none", "off", "disabled" -> "none";
                case "anthropic" -> hasAnthropicKey() ? "anthropic" : "none";
                case "openai" -> hasOpenAiKey() ? "openai" : "none";
                default -> {
                    if (hasAnthropicKey()) {
                        yield "anthropic";
                    } else if (hasOpenAiKey()) {
                        yield "openai";
                    } else {
                        yield "none";
                    }
                }
            };
        }

        public boolean hasAnthropicKey() {
            return anthropic.getApiKey() != null && !anthropic.getApiKey().isBlank();
        }

        public boolean hasOpenAiKey() {
            return openai.getApiKey() != null && !openai.getApiKey().isBlank();
        }

        public String triageModelName() {
            if (triageModel != null && !triageModel.isBlank()) {
                return triageModel;
            }
            return "anthropic".equals(resolvedProvider()) ? anthropic.getModel() : openai.getModel();
        }

        public String draftModelName() {
            if (draftModel != null && !draftModel.isBlank()) {
                return draftModel;
            }
            return triageModelName();
        }

        public static class Anthropic {
            private String apiKey = "";
            private String baseUrl = "https://api.anthropic.com";
            private String model = "claude-3-5-haiku-latest";

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }
        }

        public static class OpenAi {
            private String apiKey = "";
            private String baseUrl = "https://api.openai.com";
            private String model = "gpt-4o-mini";

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }
        }
    }
}
