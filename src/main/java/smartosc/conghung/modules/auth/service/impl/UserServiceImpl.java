package smartosc.conghung.modules.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import smartosc.conghung.common.exception.AppException;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.modules.auth.dto.request.UserCreateRequestDto;
import smartosc.conghung.modules.auth.dto.response.UserCreateResponseDto;
import smartosc.conghung.modules.auth.entity.User;
import smartosc.conghung.modules.auth.repository.UserRepository;
import smartosc.conghung.modules.auth.service.UserService;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserCreateResponseDto createUser(UserCreateRequestDto request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);

        log.info("User registered successfully");

        return UserCreateResponseDto.builder()
                .email(saved.getEmail())
                .build();
    }
}
