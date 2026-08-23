package com.pokade.global.security.oauth;

import com.pokade.domain.auth.dto.OAuth2Outcome;
import com.pokade.domain.auth.service.OAuth2LoginService;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.web.RefreshTokenCookieFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2LoginService oauth2LoginService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final String redirectBase;
    private final MeterRegistry meterRegistry;

    public OAuth2LoginSuccessHandler(
            OAuth2LoginService oauth2LoginService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            MeterRegistry meterRegistry,
            @Value("${app.oauth2.redirect-base}") String redirectBase) {
        this.oauth2LoginService = oauth2LoginService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.meterRegistry = meterRegistry;
        this.redirectBase = redirectBase;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String providerTag = OAuth2Metrics.providerTag(token.getAuthorizedClientRegistrationId());
        String email = token.getPrincipal().getAttribute("email");

        if (email == null) {
            countResult(providerTag, "error");
            redirect(response, "/login?error=email_required");
            return;
        }

        Provider provider;
        try {
            provider = Provider.valueOf(token.getAuthorizedClientRegistrationId().toUpperCase());
        } catch (IllegalArgumentException e) {
            countResult(providerTag, "error");
            redirect(response, "/login?error=unsupported_provider");
            return;
        }

        try {
            OAuth2Outcome outcome = oauth2LoginService.resolve(email, provider);
            switch (outcome) {
                case OAuth2Outcome.LoggedIn loggedIn -> {
                    response.addHeader(HttpHeaders.SET_COOKIE,
                            refreshTokenCookieFactory.create(loggedIn.refreshToken()).toString());
                    countResult(providerTag, "logged_in");
                    redirect(response, "/oauth2/success");
                }
                case OAuth2Outcome.Conflict conflict -> {
                    countResult(providerTag, "conflict");
                    redirect(response, "/login?error=email_conflict&provider=" + conflict.provider().name());
                }
                case OAuth2Outcome.SignupRequired signup -> {
                    countResult(providerTag, "signup_required");
                    redirect(response, "/signup/social#ticket="
                            + URLEncoder.encode(signup.ticket(), StandardCharsets.UTF_8));

                }
            }
        } catch (BusinessException e) {   // resolve의 상태 가드(정지 등) → 에러 페이지
            countResult(providerTag, "error");
            redirect(response, "/login?error=" + e.getErrorCode().name().toLowerCase());
        }

    }

    private void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(redirectBase + path);
    }

    // 콜백 처리 결과를 지표에 기록한다. 리다이렉트 동작은 그대로 두고 분포만 남긴다.
    private void countResult(String providerTag, String result) {
        Counter.builder(OAuth2Metrics.RESULT_COUNTER)
                .tag(OAuth2Metrics.PROVIDER_TAG, providerTag)
                .tag(OAuth2Metrics.RESULT_TAG, result)
                .register(meterRegistry)
                .increment();
    }
}
