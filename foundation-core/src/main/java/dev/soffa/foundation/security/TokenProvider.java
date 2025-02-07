package dev.soffa.foundation.security;


import dev.soffa.foundation.model.Authentication;
import dev.soffa.foundation.model.Token;
import dev.soffa.foundation.model.TokenType;

import java.util.Map;

public interface TokenProvider {

    default Token create(TokenType type, String subject) {
        return create(type, subject, null);
    }

    Token create(TokenType type, String subject, Map<String, Object> claims);

    Token create(TokenType type, String subject, Map<String, Object> claims, int ttlInMinutes);

    Authentication decode(String token, ClaimsExtractor extractor);

    Authentication decode(String token);

    Authentication extractInfo(Token token);

    TokensConfig getConfig();

}
