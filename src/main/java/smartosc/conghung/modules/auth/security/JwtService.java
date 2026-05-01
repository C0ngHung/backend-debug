package smartosc.conghung.modules.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import smartosc.conghung.modules.auth.entity.User;
import smartosc.conghung.modules.auth.repository.RedisTokenRepository;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService implements InitializingBean {

    private static final JWSAlgorithm SIGN_ALG = JWSAlgorithm.HS512;
    private static final long ACCESS_TOKEN_TTL = 30;
    private static final ChronoUnit ACCESS_TOKEN_UNIT = ChronoUnit.MINUTES;
    private static final long REFRESH_TOKEN_TTL = 14;
    private static final ChronoUnit REFRESH_TOKEN_UNIT = ChronoUnit.DAYS;

    @Value("${jwt.issuer:smartosc.conghung}")
    private String issuer;

    @Value("${jwt.audience:backend-debug-api}")
    private String audience;

    @Value("${jwt.secret-key}")
    private String secretKeyBase64;

    @Getter
    private SecretKey secretKey;

    private final RedisTokenRepository redisTokenRepository;
    private final Clock clock = Clock.systemUTC();

    @Override
    public void afterPropertiesSet() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        } catch (IllegalArgumentException ex) {
            keyBytes = secretKeyBase64.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "HS512 requires secret key length >= 64 bytes. Current: " + keyBytes.length);
        }

        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
    }

    public TokenPayload generateAccessToken(User user) {
        return generateToken(user, ACCESS_TOKEN_TTL, ACCESS_TOKEN_UNIT);
    }

    public TokenPayload generateRefreshToken(User user) {
        return generateToken(user, REFRESH_TOKEN_TTL, REFRESH_TOKEN_UNIT);
    }

    public boolean verifyToken(String token) throws ParseException, JOSEException {
        SignedJWT jwt = SignedJWT.parse(token);

        // 1. Verify signature first (security-first order)
        if (!jwt.verify(new MACVerifier(secretKey))) {
            return false;
        }

        // 2. Check expiration
        Date exp = jwt.getJWTClaimsSet().getExpirationTime();
        if (exp == null || exp.before(Date.from(Instant.now(clock)))) {
            return false;
        }

        // 3. Check revoked by JTI in Redis blacklist
        String jti = jwt.getJWTClaimsSet().getJWTID();
        if (jti == null || jti.isBlank()) {
            return false;
        }

        return !redisTokenRepository.existsById(jti);
    }

    public JwtInfo parseToken(String token) throws ParseException {
        SignedJWT jwt = SignedJWT.parse(token);
        var claims = jwt.getJWTClaimsSet();

        return JwtInfo.builder()
                .jwtID(claims.getJWTID())
                .issuedAt(claims.getIssueTime())
                .expirationTime(claims.getExpirationTime())
                .build();
    }

    private TokenPayload generateToken(User user, long duration, ChronoUnit unit) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(duration, unit);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(user.getEmail())
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .build();

        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(SIGN_ALG).type(JOSEObjectType.JWT).build(),
                    claims
            );
            jwt.sign(new MACSigner(secretKey));

            return TokenPayload.builder()
                    .token(jwt.serialize())
                    .jwtID(jti)
                    .expiredTime(Date.from(exp))
                    .build();

        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }
}
