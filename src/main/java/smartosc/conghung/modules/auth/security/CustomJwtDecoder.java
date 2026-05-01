package smartosc.conghung.modules.auth.security;

import com.nimbusds.jose.JOSEException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.text.ParseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {

    private final JwtService jwtService;
    private NimbusJwtDecoder nimbusJwtDecoder;

    @PostConstruct
    public void init() {
        nimbusJwtDecoder = NimbusJwtDecoder
                .withSecretKey(jwtService.getSecretKey())
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            if (!jwtService.verifyToken(token)) {
                throw new JwtException("Invalid token");
            }
        } catch (ParseException | JOSEException e) {
            throw new JwtException("Token verification failed", e);
        }
        return nimbusJwtDecoder.decode(token);
    }
}
