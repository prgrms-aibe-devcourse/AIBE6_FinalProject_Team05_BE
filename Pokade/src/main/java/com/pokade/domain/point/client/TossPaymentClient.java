package com.pokade.domain.point.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokade.domain.point.client.dto.TossCancelRequest;
import com.pokade.domain.point.client.dto.TossCancelResponse;
import com.pokade.domain.point.client.dto.TossConfirmRequest;
import com.pokade.domain.point.client.dto.TossConfirmResponse;
import com.pokade.domain.point.client.dto.TossErrorResponse;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";

    private final RestClient restClient;
    // 별도 빈 의존 없이 에러 응답 파싱에만 쓰는 일회성 용도라, 앱 공용 ObjectMapper를 주입받지 않고
    // 직접 인스턴스를 둔다 (domain.inquiry의 기존 컨벤션과 동일).
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TossPaymentClient(RestClient.Builder restClientBuilder, TossProperties properties) {
        // 토스페이먼츠는 시크릿 키를 Basic Auth의 아이디로, 비밀번호는 빈 문자열로 사용한다.
        String encodedAuth = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + encodedAuth)
                .build();
    }

    // 결제 승인 - amount는 반드시 우리 서버가 사전에 기록해 둔(PointChargeOrder) 금액을 넘겨야 한다.
    // 클라이언트가 리다이렉트로 보낸 금액을 그대로 전달하면 위변조된 금액으로 승인을 시도하게 된다.
    public TossConfirmResponse confirmPayment(String paymentKey, String orderId, long amount) {
        TossConfirmResponse response = restClient.post()
                .uri(CONFIRM_PATH)
                .body(new TossConfirmRequest(paymentKey, orderId, amount))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    String message = parseErrorMessage(httpResponse.getBody().readAllBytes());
                    throw new BusinessException(ErrorCode.PAYMENT_FAILED, message);
                })
                .body(TossConfirmResponse.class);

        if (response == null || !response.isDone()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        return response;
    }

    // 결제취소(환불) - 거래 취소 시 에스크로로 잡아둔 금액을 실제로 되돌려준다.
    public TossCancelResponse cancelPayment(String paymentKey, String cancelReason) {
        TossCancelResponse response = restClient.post()
                .uri(CANCEL_PATH, paymentKey)
                .body(new TossCancelRequest(cancelReason))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                    String message = parseErrorMessage(httpResponse.getBody().readAllBytes());
                    throw new BusinessException(ErrorCode.PAYMENT_FAILED, message);
                })
                .body(TossCancelResponse.class);

        if (response == null || !response.isCanceled()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        return response;
    }

    private String parseErrorMessage(byte[] body) {
        try {
            TossErrorResponse error = objectMapper.readValue(body, TossErrorResponse.class);
            return error.message();
        } catch (Exception e) {
            return "토스페이먼츠 결제 승인에 실패했습니다.";
        }
    }
}
