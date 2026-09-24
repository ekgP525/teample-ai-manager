package com.teample.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTest {
    private MockEnvironment environment() {
        MockEnvironment env = new MockEnvironment();
        for (String name : new String[]{"DATABASE_USERNAME", "DATABASE_PASSWORD", "ANTHROPIC_API_KEY"}) env.setProperty(name, "test-value");
        env.setProperty("DATABASE_URL", "jdbc:postgresql://db.example.com/teample");
        env.setProperty("SUPABASE_JWKS_URI", "https://project.supabase.co/auth/v1/.well-known/jwks.json");
        env.setProperty("SUPABASE_JWT_ISSUER", "https://project.supabase.co/auth/v1");
        env.setProperty("CORS_ALLOWED_ORIGINS", "https://app.example.com");
        return env;
    }
    @Test void rejectsMissingDatabaseAndWildcardsButAcceptsExactProductionOrigin() {
        assertThatThrownBy(() -> new ProductionConfiguration(new MockEnvironment())).hasMessageContaining("DATABASE_URL");
        MockEnvironment env = environment();
        assertThatCode(() -> new ProductionConfiguration(env)).doesNotThrowAnyException();
        env.setProperty("DATABASE_URL", "jdbc:h2:mem:temporary");
        assertThatThrownBy(() -> new ProductionConfiguration(env)).hasMessageContaining("PostgreSQL");
        env.setProperty("DATABASE_URL", "jdbc:postgresql://db.example.com/teample");
        env.setProperty("CORS_ALLOWED_ORIGINS", "https://*.vercel.app");
        assertThatThrownBy(() -> new ProductionConfiguration(env)).hasMessageContaining("exact HTTPS");
    }
}
