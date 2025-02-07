package dev.soffa.foundation.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import dev.soffa.foundation.commons.IOUtil;
import dev.soffa.foundation.commons.Logger;
import dev.soffa.foundation.commons.TextUtil;
import dev.soffa.foundation.commons.TokenUtil;
import dev.soffa.foundation.context.Context;
import dev.soffa.foundation.error.ConfigurationException;
import dev.soffa.foundation.error.InvalidTokenException;
import dev.soffa.foundation.error.NotImplementedException;
import dev.soffa.foundation.error.UnauthorizedException;
import dev.soffa.foundation.model.Authentication;
import dev.soffa.foundation.model.Token;
import dev.soffa.foundation.model.TokenType;
import dev.soffa.foundation.model.UserInfo;
import lombok.Data;
import lombok.SneakyThrows;

import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;

@Data
public class DefaultTokenProvider implements TokenProvider, ClaimsExtractor {

    private static final Logger LOG = Logger.get(DefaultTokenProvider.class);
    private TokensConfig config;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private String privateJwks;

    public DefaultTokenProvider(TokensConfig config) {
        this.config = config;
        configureJwksProcessor();
    }

    @Override
    public Token create(TokenType type, String subjet, Map<String, Object> claims) {
        return create(type, subjet, claims, config.getDefaultTtl());
    }

    @Override
    public Token create(TokenType type, String subjet, Map<String, Object> claims, int ttlInMinutes) {
        String token;
        if (type == TokenType.JWT) {
            if (privateJwks != null) {
                token = TokenUtil.fromJwks(
                    privateJwks,
                    config.getIssuer(),
                    subjet,
                    claims,
                    Duration.ofMinutes(ttlInMinutes)
                );
            } else if (TextUtil.isNotEmpty(config.getSecret())) {
                token = TokenUtil.createJwt(
                    config.getIssuer(),
                    config.getSecret(),
                    subjet,
                    claims,
                    ttlInMinutes
                );
            } else {
                throw new ConfigurationException("No secret or private jwks configured");
            }
        } else {
            throw new NotImplementedException("Token type not supported yet: %s", type.name());
        }
        return new Token(token, subjet, claims, ttlInMinutes);
    }


    @Override
    public Authentication extractInfo(Token token) {
        String tenant = token.lookupClaim("tenant", "tenantId", Context.TENANT_ID).orElse(null);

        UserInfo profile = new UserInfo();

        profile.setCity(token.lookupClaim("city", "location").orElse(null));
        profile.setCountry(token.lookupClaim("country", "countryId").orElse(null));
        profile.setGender(token.lookupClaim("gender", "sex", "sexe").orElse(null));
        profile.setEmail(token.lookupClaim("email", "mail").orElse(null));
        profile.setPhoneNumber(token.lookupClaim("mobile", "mobileNumber", "phoneNumber", "phone").orElse(null));
        profile.setGivenName(token.lookupClaim("givenname", "given_name", "firstname", "first_name", "prenom").orElse(null));
        profile.setFamilyName(token.lookupClaim("familyname", "family_name", "lastName", "last_name").orElse(null));
        profile.setNickname(token.lookupClaim("nickname", "nick_name", "pseudo", "alias").orElse(null));

        Set<String> permissions = new HashSet<>();
        Set<String> roles = new HashSet<>();

        token.lookupClaim("permissions", "grants").ifPresent(s -> {
            for (String item : s.split(",")) {
                if (TextUtil.isNotEmpty(item)) {
                    permissions.add(item.trim().toLowerCase());
                }
            }
        });

        Object principal = token.lookupClaim("principal").orElse(null);

        token.lookupClaim("roles").ifPresent(s -> {
            for (String item : s.split(",")) {
                if (TextUtil.isNotEmpty(item)) {
                    roles.add(item.trim().toLowerCase());
                }
            }
        });

        String liveMode = token.lookupClaim("live", "liveMode").orElse("false");

        return Authentication.builder().
            claims(token.getClaims()).
            liveMode(Boolean.parseBoolean(liveMode.toLowerCase())).
            username(token.getSubject()).
            tenantId(tenant).
            application(token.lookupClaim("applicationName", "application", "applicationId", "app").orElse(null)).
            profile(profile).
            roles(roles).
            principal(principal).
            permissions(permissions).
            build();
    }

    @SneakyThrows
    private void configureJwksProcessor() {
        if (config.getPrivateJwks() != null) {
            privateJwks = IOUtil.getResourceAsString(config.getPrivateJwks());
        }
        if (config.getPublicJwks() != null) {
            JWKSet source;
            if (config.getPublicJwks().startsWith("http")) {
                source = JWKSet.load(new URL(config.getPublicJwks()));
            } else {
                source = JWKSet.load(Objects.requireNonNull(DefaultTokenProvider.class.getResourceAsStream(config.getPublicJwks())));
            }
            JWKSource<SecurityContext> keySource = new ImmutableJWKSet<>(source);
            jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
        }
    }

    @Override
    public Authentication decode(String token) {
        return decode(token, this);
    }

    @Override
    public Authentication decode(String token, ClaimsExtractor extractor) {
        if (jwtProcessor != null) {
            return decodejwtWithJwks(token, extractor);
        } else {
            return decodeJwtWithSecret(token, extractor);
        }
    }

    public Authentication decodejwtWithJwks(String token, ClaimsExtractor extractor) {
        try {
            LOG.debug("Decoding JWT with JWKS");
            JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
            return extractor.extractInfo(new Token(token, claimsSet.getSubject(), claimsSet.getClaims()));
        } catch (ParseException | JOSEException | BadJOSEException e) {
            throw new InvalidTokenException(e.getMessage(), e);
        }
    }

    public Authentication decodeJwtWithSecret(String token, ClaimsExtractor claimsExtractor) {
        try {
            LOG.debug("Decoding JWT token");
            Algorithm algorithm = Algorithm.HMAC256(config.getSecret());
            JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(config.getIssuer())
                .build(); //Reusable verifier instance
            DecodedJWT jwt = verifier.verify(token);

            Map<String, Claim> baseClaims = jwt.getClaims();

            Map<String, Object> claims = new HashMap<>();
            for (Map.Entry<String, Claim> entry : baseClaims.entrySet()) {
                if (entry.getValue().isNull()) {
                    continue;
                }
                Object value = entry.getValue().asString();
                if (value == null) {
                    value = entry.getValue().asBoolean();
                    if (value == null) {
                        value = entry.getValue().asDouble();
                        if (value == null) {
                            value = entry.getValue().asInt();
                            if (value == null) {
                                value = entry.getValue().asLong();
                                if (value == null) {
                                    value = entry.getValue().asDate();
                                    if (value == null) {
                                        value = entry.getValue().asMap();
                                        if (value == null) {
                                            LOG.warn("Unsupported claim type '%s', using string.", entry.getKey());
                                            value = entry.toString();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                claims.put(entry.getKey(), value);
            }

            return claimsExtractor.extractInfo(new Token(token, jwt.getSubject(), claims));

        } catch (Exception e) {
            throw new UnauthorizedException(e.getMessage(), e);
        }
    }
}
