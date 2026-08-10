package com.pokade.global.security.oauth;

import com.pokade.domain.auth.dto.OAuth2Outcome;
import com.pokade.domain.auth.service.OAuth2LoginService;
import com.pokade.domain.user.entity.type.Provider;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.web.RefreshTokenCookieFactory;
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

    public OAuth2LoginSuccessHandler(
            OAuth2LoginService oauth2LoginService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            @Value("${app.oauth2.redirect-base}") String redirectBase) {
        this.oauth2LoginService = oauth2LoginService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.redirectBase = redirectBase;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String email = token.getPrincipal().getAttribute("email");

        if (email == null) {
            redirect(response, "/login?error=email_required");
            return;
        }

        Provider provider;
        try {
            provider = Provider.valueOf(token.getAuthorizedClientRegistrationId().toUpperCase());
        } catch (IllegalArgumentException e) {
            redirect(response, "/login?error=unsupported_provider");
            return;
        }

        try {
            OAuth2Outcome outcome = oauth2LoginService.resolve(email, provider);
            switch (outcome) {
                case OAuth2Outcome.LoggedIn loggedIn -> {
                    response.addHeader(HttpHeaders.SET_COOKIE,
                            refreshTokenCookieFactory.create(loggedIn.refreshToken()).toString());
                    redirect(response, "/oauth2/success");
                }
                case OAuth2Outcome.Conflict conflict -> redirect(response, "/login?error=email_conflict");
                case OAuth2Outcome.SignupRequired signup -> redirect(response, "/signup/social?ticket="
                        + URLEncoder.encode(signup.ticket(), StandardCharsets.UTF_8));
            }
        } catch (BusinessException e) {   // resolve의 상태 가드(정지 등) → 에러 페이지
            redirect(response, "/login?error=" + e.getErrorCode().name().toLowerCase());
        }

    }

    private void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(redirectBase + path);
    }
}
