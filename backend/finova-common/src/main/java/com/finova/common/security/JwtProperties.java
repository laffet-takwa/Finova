package com.finova.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration. All values come from environment variables so no secret is
 * ever committed to the repository.
 */
@ConfigurationProperties(prefix = "finova.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "finova";
    private long accessTokenTtlSeconds = 3600L;
    private long refreshTokenTtlSeconds = 604800L;
    private String accessTokenType = "Bearer";

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

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String getAccessTokenType() {
        return accessTokenType;
    }

    public void setAccessTokenType(String accessTokenType) {
        this.accessTokenType = accessTokenType;
    }
}
