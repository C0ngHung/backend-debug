package smartosc.conghung.modules.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import smartosc.conghung.common.exception.AppException;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.modules.auth.dto.request.LoginRequestDto;
import smartosc.conghung.modules.auth.dto.response.LoginResponseDto;
import smartosc.conghung.modules.auth.entity.RedisToken;
import smartosc.conghung.modules.auth.entity.User;
import smartosc.conghung.modules.auth.repository.RedisTokenRepository;
import smartosc.conghung.modules.auth.security.JwtInfo;
import smartosc.conghung.modules.auth.security.JwtService;
import smartosc.conghung.modules.auth.security.TokenPayload;
import smartosc.conghung.modules.auth.service.AuthenticationService;

import java.text.ParseException;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenRepository redisTokenRepository;

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        Authentication authenticate = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = (User) authenticate.getPrincipal();

        TokenPayload accessPayload = jwtService.generateAccessToken(user);
        TokenPayload refreshPayload = jwtService.generateRefreshToken(user);

        log.info("Login successful for user");

        return LoginResponseDto.builder()
                .accessToken(accessPayload.getToken())
                .refreshToken(refreshPayload.getToken())
                .build();
    }

    @Override
    public void logout(String token) {
        try {
            if (token == null || token.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            JwtInfo jwtInfo = jwtService.parseToken(token);
            String jwtID = jwtInfo.getJwtID();
            Date expirationTime = jwtInfo.getExpirationTime();
            Date currentTime = new Date();

            if (expirationTime.before(currentTime)) {
                log.info("Token already expired, no logout needed");
                return;
            }

            if (redisTokenRepository.existsById(jwtID)) {
                log.info("Token already blacklisted");
                return;
            }

            long timeToExpireInSeconds = (expirationTime.getTime() - currentTime.getTime()) / 1000;

            redisTokenRepository.save(RedisToken.builder()
                    .jwtID(jwtID)
                    .expiredTime(timeToExpireInSeconds)
                    .build());

            log.info("Logout successful");

        } catch (ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}
