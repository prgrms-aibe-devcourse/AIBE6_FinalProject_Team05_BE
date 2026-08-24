package com.pokade.global.init;

import com.pokade.domain.user.entity.User;
import com.pokade.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev") // dev 프로파일에서만, 앱 기동 시 테스트 계정을 멱등하게 심는다.
@RequiredArgsConstructor
public class DevUserInit implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int LOADTEST_USER_COUNT = 50;

    @Override
    public void run(ApplicationArguments args) {
        seed("test1@pokade.com", "테스터원");
        seed("test2@pokade.com", "테스터투");
        seed("test3@pokade.com", "테스터삼");
        seedLoadTestUsers();
    }

    //이미 있으면 건너뛰고, 없으면 ACTIVE 로컬 유저 생성
    private void seed(String email, String nickname) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = User.createLocalUser(email, passwordEncoder.encode("test1234"), nickname);
        user.verifyEmail();
        userRepository.save(user);
    }

    // 부하테스트용 계정을 VU 수만큼 심는다. 해시를 한 번만 계산해 부팅 지연을 막는다
    private void seedLoadTestUsers() {
        String encodedPassword = passwordEncoder.encode("test1234");
        for (int i = 1; i <= LOADTEST_USER_COUNT; i++) {
            String email = "loadtest" + i + "@pokade.com";
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            User user = User.createLocalUser(email, encodedPassword, "부하테스트" + i);
            user.verifyEmail();
            userRepository.save(user);
        }
    }
}
