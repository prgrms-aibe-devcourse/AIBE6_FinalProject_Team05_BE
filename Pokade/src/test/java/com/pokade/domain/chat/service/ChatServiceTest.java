package com.pokade.domain.chat.service;

import com.pokade.domain.chat.dto.ChatHistoryImportRequest;
import com.pokade.domain.chat.dto.ChatHistoryImportResponse;
import com.pokade.domain.chat.entity.ChatMessage;
import com.pokade.domain.chat.repository.ChatMessageRepository;
import com.pokade.domain.chat.store.ChatImportIdempotencyStore;
import com.pokade.domain.chat.store.ChatRateLimitStore;
import com.pokade.domain.chat.tool.PriceChatTools;
import com.pokade.domain.price.dto.PriceRankingResponse;
import com.pokade.domain.price.service.PriceService;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String SESSION_ID = "11111111-1111-1111-1111-111111111111";
    private static final Long USER_ID = 1L;
    private static final String RATE_LIMIT_KEY = "chat-import:" + USER_ID;

    @Mock
    private ChatClient chatClient;

    @Mock
    private PriceChatTools priceChatTools;

    @Mock
    private PriceService priceService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatImportIdempotencyStore chatImportIdempotencyStore;

    @Mock
    private ChatRateLimitStore chatRateLimitStore;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private ChatService chatService;

    private ChatHistoryImportRequest requestOf(ChatHistoryImportRequest.Entry... entries) {
        return new ChatHistoryImportRequest(SESSION_ID, List.of(entries));
    }

    private ChatHistoryImportRequest.Entry entry(String presetId, Instant askedAt) {
        return new ChatHistoryImportRequest.Entry(presetId, askedAt);
    }

    private PriceRankingResponse ranking(String cardName) {
        return new PriceRankingResponse(1L, cardName, null, 10000, BigDecimal.TEN, 1000);
    }

    private void allowRateLimit() {
        given(chatRateLimitStore.isLocked(RATE_LIMIT_KEY)).willReturn(false);
        given(chatRateLimitStore.recordAndCount(RATE_LIMIT_KEY, "import")).willReturn(1L);
    }

    // Mockito의 boolean mock 기본값은 false라, "처음 보는 항목"으로 취급되려면 markIfAbsent를 true로 명시해야 한다.
    private void allowIdempotency() {
        given(chatImportIdempotencyStore.markIfAbsent(anyString())).willReturn(true);
    }

    @Test
    @DisplayName("t1 비로그인이면 UNAUTHORIZED 예외가 발생하고 아무 저장소도 조회하지 않는다")
    void t1() {
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now()));

        assertThatThrownBy(() -> chatService.importHistory(request, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(chatRateLimitStore, chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t2 이미 잠금된 사용자면 CHAT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void t2() {
        given(chatRateLimitStore.isLocked(RATE_LIMIT_KEY)).willReturn(true);
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now()));

        assertThatThrownBy(() -> chatService.importHistory(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t3 반복 호출 임계값을 넘기면 세션을 잠그고 CHAT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void t3() {
        given(chatRateLimitStore.isLocked(RATE_LIMIT_KEY)).willReturn(false);
        given(chatRateLimitStore.recordAndCount(RATE_LIMIT_KEY, "import")).willReturn(5L);
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now()));

        assertThatThrownBy(() -> chatService.importHistory(request, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        verify(chatRateLimitStore).lock(RATE_LIMIT_KEY);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t4 유효한 랭킹 프리셋 1건은 이관에 성공하고 USER/ASSISTANT 메시지가 각각 저장된다")
    void t4() {
        allowRateLimit();
        allowIdempotency();
        given(priceService.getRanking("rise")).willReturn(List.of(ranking("피카츄")));
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now().minus(1, ChronoUnit.HOURS)));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(0);
        verify(chatImportIdempotencyStore).markIfAbsent(anyString());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("t5 존재하지 않는 presetId는 skip되고 아무것도 저장하지 않는다")
    void t5() {
        allowRateLimit();
        ChatHistoryImportRequest request = requestOf(entry("not-a-real-preset", Instant.now()));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t6 rankingType이 없는 프리셋(how-to-use)은 재계산 대상이 아니므로 skip된다")
    void t6() {
        allowRateLimit();
        ChatHistoryImportRequest request = requestOf(entry("how-to-use", Instant.now()));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t7 24시간을 넘긴 askedAt은 skip된다")
    void t7() {
        allowRateLimit();
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now().minus(25, ChronoUnit.HOURS)));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t8 미래 시각의 askedAt은 skip된다")
    void t8() {
        allowRateLimit();
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now().plus(1, ChronoUnit.HOURS)));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verifyNoInteractions(chatImportIdempotencyStore, chatMessageRepository, priceService);
    }

    @Test
    @DisplayName("t9 이미 이관된 항목(멱등성 위반)은 skip되고 답변을 재계산하거나 메시지를 저장하지 않는다")
    void t9() {
        allowRateLimit();
        given(chatImportIdempotencyStore.markIfAbsent(anyString())).willReturn(false);
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now().minus(1, ChronoUnit.HOURS)));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verify(priceService, never()).getRanking(anyString());
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    @DisplayName("t10 여러 건 중 일부만 유효하면 유효한 것만 이관되고 나머지는 skip되는 부분 성공을 허용한다")
    void t10() {
        allowRateLimit();
        allowIdempotency();
        given(priceService.getRanking("rise")).willReturn(List.of(ranking("피카츄")));
        ChatHistoryImportRequest request = requestOf(
                entry("top-gainers", Instant.now().minus(1, ChronoUnit.HOURS)),
                entry("not-a-real-preset", Instant.now()),
                entry("top-losers", Instant.now().minus(25, ChronoUnit.HOURS))
        );

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(2);
        verify(chatImportIdempotencyStore, times(1)).markIfAbsent(anyString());
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("t11 랭킹 조회가 실패해도 그 entry만 skip되고 예외가 전체 이관을 중단시키지 않으며, 재시도 가능하도록 멱등성 마커를 해제한다")
    void t11() {
        allowRateLimit();
        allowIdempotency();
        given(priceService.getRanking(eq("fall"))).willThrow(new BusinessException(ErrorCode.CARD_NOT_FOUND));
        ChatHistoryImportRequest request = requestOf(entry("top-losers", Instant.now().minus(1, ChronoUnit.HOURS)));

        ChatHistoryImportResponse response = chatService.importHistory(request, USER_ID);

        assertThat(response.imported()).isEqualTo(0);
        assertThat(response.skipped()).isEqualTo(1);
        verifyNoInteractions(chatMessageRepository);
        verify(chatImportIdempotencyStore).release(anyString());
    }

    @Test
    @DisplayName("t12 메시지 저장이 실패하면 멱등성 마커를 해제하고, 이후 재시도(재로그인)하면 정상적으로 이관된다")
    void t12() {
        allowRateLimit();
        allowIdempotency();
        given(priceService.getRanking("rise")).willReturn(List.of(ranking("피카츄")));
        // 첫 저장 시도(USER 메시지)에서 실패, 재시도부터는 정상 저장.
        given(chatMessageRepository.save(any(ChatMessage.class)))
                .willThrow(new RuntimeException("DB 저장 실패"))
                .willReturn(null);
        ChatHistoryImportRequest request = requestOf(entry("top-gainers", Instant.now().minus(1, ChronoUnit.HOURS)));

        ChatHistoryImportResponse first = chatService.importHistory(request, USER_ID);
        assertThat(first.imported()).isEqualTo(0);
        assertThat(first.skipped()).isEqualTo(1);
        verify(chatImportIdempotencyStore).release(anyString());

        ChatHistoryImportResponse retry = chatService.importHistory(request, USER_ID);
        assertThat(retry.imported()).isEqualTo(1);
        assertThat(retry.skipped()).isEqualTo(0);
    }
}
