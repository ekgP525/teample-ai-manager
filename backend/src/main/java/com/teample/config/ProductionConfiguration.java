package com.teample.config;

import java.net.URI;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod | (!dev & !test)")
public class ProductionConfiguration {
    public ProductionConfiguration(Environment env) {
        for (String name : new String[]{"DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD", "ANTHROPIC_API_KEY", "SUPABASE_JWKS_URI", "SUPABASE_JWT_ISSUER", "CORS_ALLOWED_ORIGINS"}) {
            String value = env.getProperty(name);
            if (value == null || value.isBlank() || value.contains("<")) throw new IllegalStateException("Missing production setting: " + name);
        }
        if (!env.getProperty("DATABASE_URL", "").startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("Production requires a persistent PostgreSQL database.");
        for (String origin : env.getProperty("CORS_ALLOWED_ORIGINS", "").split(",")) {
            URI uri = URI.create(origin.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || origin.contains("*")
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty()))
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain exact HTTPS origins without paths.");
        }
        if (env.getProperty("ai.daily-limit", Integer.class, 20) < 1 || env.getProperty("ai.concurrent-limit", Integer.class, 4) < 1)
            throw new IllegalStateException("AI limits must be positive.");
    }
}
