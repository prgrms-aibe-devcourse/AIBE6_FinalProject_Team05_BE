package com.pokade.global.security.oauth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String redirectBase;

    private final MeterRegistry meterRegistry;

    public OAuth2LoginFailureHandler(
            MeterRegistry meterRegistry,
            @Value("${app.oauth2.redirect-base}") String redirectBase) {
        this.meterRegistry = meterRegistry;
        this.redirectBase = redirectBase;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String reason = "oauth2_failed";
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            reason = oauthException.getError().getErrorCode();
        }
        Counter.builder(OAuth2Metrics.RESULT_COUNTER)
                .tag(OAuth2Metrics.PROVIDER_TAG, OAuth2Metrics.providerTagFromUri(request.getRequestURI()))
                .tag(OAuth2Metrics.RESULT_TAG, "failure")
                .register(meterRegistry)
                .increment();
        response.sendRedirect(redirectBase + "/login?error=" + URLEncoder.encode(reason, StandardCharsets.UTF_8));

    }
}
