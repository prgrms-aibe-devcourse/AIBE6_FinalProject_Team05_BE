package com.pokade.domain.point.client;

import com.pokade.domain.point.client.dto.TossConfirmResponse;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentClientTest {

    private static final String BASE_URL = "http://localhost:8089";
    private static final TossProperties PROPERTIES = new TossProperties("test_ck_dummy", "test_sk_dummy", BASE_URL);

    private TossPaymentClient client;
    private MockRestServiceServer mockServer;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentClient(builder, PROPERTIES);
    }

    @Test
    @DisplayName("confirmPayment: 시크릿 키를 Basic Auth로 담아 요청하고, DONE 응답을 그대로 반환한다")
    void confirmPayment_sendsBasicAuthAndReturnsResponse() {
        setUp();
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("test_sk_dummy:".getBytes());

        mockServer.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", expectedAuth))
                .andExpect(jsonPath("$.paymentKey").value("pay_123"))
                .andExpect(jsonPath("$.orderId").value("order_123"))
                .andExpect(jsonPath("$.amount").value(10000))
                .andRespond(withSuccess(
                        """
                        {"paymentKey":"pay_123","orderId":"order_123","status":"DONE","totalAmount":10000}
                        """,
                        MediaType.APPLICATION_JSON));

        TossConfirmResponse response = client.confirmPayment("pay_123", "order_123", 10000L);

        assertThat(response.isDone()).isTrue();
        assertThat(response.totalAmount()).isEqualTo(10000L);
        mockServer.verify();
    }

    @Test
    @DisplayName("confirmPayment: 토스가 4xx 에러 응답을 내려주면 PAYMENT_FAILED로 변환해 메시지를 그대로 담는다")
    void confirmPayment_errorResponse_translatesToPaymentFailed() {
        setUp();
        mockServer.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"REJECT_CARD_PAYMENT","message":"결제가 거절되었습니다."}
                                """));

        assertThatThrownBy(() -> client.confirmPayment("pay_123", "order_123", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED)
                .hasMessage("결제가 거절되었습니다.");
    }

    @Test
    @DisplayName("confirmPayment: 응답 status가 DONE이 아니면 PAYMENT_FAILED를 던진다")
    void confirmPayment_notDone_throwsPaymentFailed() {
        setUp();
        mockServer.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(
                        """
                        {"paymentKey":"pay_123","orderId":"order_123","status":"IN_PROGRESS","totalAmount":10000}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.confirmPayment("pay_123", "order_123", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_FAILED);
    }
}
