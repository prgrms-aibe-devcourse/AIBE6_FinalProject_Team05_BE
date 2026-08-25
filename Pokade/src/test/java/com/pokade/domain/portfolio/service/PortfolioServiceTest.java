package com.pokade.domain.portfolio.service;

import com.pokade.domain.ai.entity.GradeResult;
import com.pokade.domain.ai.entity.GradeResultImage;
import com.pokade.domain.ai.entity.GradeStatus;
import com.pokade.domain.ai.entity.PhotoType;
import com.pokade.domain.ai.repository.GradeResultImageRepository;
import com.pokade.domain.ai.repository.GradeResultRepository;
import com.pokade.domain.card.entity.Card;
import com.pokade.domain.card.repository.CardPriceRepository;
import com.pokade.domain.card.repository.CardRepository;
import com.pokade.domain.card.repository.CardVariantRepository;
import com.pokade.domain.card.support.CardNameKoResolver;
import com.pokade.domain.portfolio.dto.PortfolioAnalyticsItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioAnalyticsResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemAddRequest;
import com.pokade.domain.portfolio.dto.PortfolioItemPnlResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemResponse;
import com.pokade.domain.portfolio.dto.PortfolioItemUpdateRequest;
import com.pokade.domain.portfolio.dto.PortfolioSummaryResponse;
import com.pokade.domain.portfolio.entity.PortfolioItem;
import com.pokade.domain.portfolio.repository.PortfolioItemRepository;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import com.pokade.global.infra.storage.S3FileStorage;
import com.pokade.global.port.UserAccessChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioItemRepository portfolioItemRepository;
    @Mock CardRepository cardRepository;
    @Mock CardNameKoResolver cardNameKoResolver;
    @Mock CardVariantRepository cardVariantRepository;
    @Mock CardPriceRepository cardPriceRepository;
    @Mock GradeResultRepository gradeResultRepository;
    @Mock GradeResultImageRepository gradeResultImageRepository;
    @Mock S3FileStorage s3FileStorage;
    @Mock UserAccessChecker userAccessChecker;

    @InjectMocks PortfolioService portfolioService;

    // id 필드 포함 — enrich 내부 Collectors.toMap(Card::getId, ...) 정상 동작을 위해 필수
    private Card stubCard(Long id) {
        return Card.builder()
                .id(id)
                .externalId("ext-" + id)
                .name("리자몽")
                .build();
    }

    private PortfolioItem stubItem(Long userId, Long cardId, Long variantId) {
        return PortfolioItem.builder()
                .userId(userId)
                .cardId(cardId)
                .variantId(variantId)
                .quantity(1)
                .acquiredPrice(50000)
                .acquiredAt(LocalDateTime.now())
                .build();
    }

    // ===== addItem =====

    @Test
    @DisplayName("추가: 존재하지 않는 카드면 CARD_NOT_FOUND")
    void addItem_cardNotFound() {
        PortfolioItemAddRequest request = new PortfolioItemAddRequest(999L, null, 1, null, null);
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.addItem(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_NOT_FOUND);
        then(portfolioItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("추가: 존재하지 않는 variantId면 CARD_NOT_FOUND")
    void addItem_variantNotFound() {
        PortfolioItemAddRequest request = new PortfolioItemAddRequest(1L, 999L, 1, null, null);
        given(cardRepository.findById(1L)).willReturn(Optional.of(stubCard(1L)));
        given(cardVariantRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.addItem(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_NOT_FOUND);
        then(portfolioItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("추가: variantId null이고 대표 변형도 없으면 시세 없이 정상 추가된다")
    void addItem_variantIdNull_success() {
        PortfolioItemAddRequest request = new PortfolioItemAddRequest(1L, null, 2, 30000, null);
        Card card = stubCard(1L);
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));
        // 대표 변형 없으면 resolvedVariantId = null → cardPriceRepository 호출 안 됨
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(Optional.empty());

        PortfolioItemResponse response = portfolioService.addItem(1L, request);

        then(portfolioItemRepository).should().save(any(PortfolioItem.class));
        assertThat(response.cardId()).isEqualTo(1L);
        assertThat(response.variantId()).isNull();
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.currentMarketPrice()).isNull();
    }

    @Test
    @DisplayName("추가: 같은 카드를 여러 번 추가해도 에러 없이 별도 행으로 저장된다")
    void addItem_duplicateAllowed() {
        PortfolioItemAddRequest request = new PortfolioItemAddRequest(1L, null, 1, null, null);
        Card card = stubCard(1L);
        given(cardRepository.findById(1L)).willReturn(Optional.of(card));
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));
        given(cardVariantRepository.findPrimaryVariantId(1L)).willReturn(Optional.empty());

        portfolioService.addItem(1L, request);
        portfolioService.addItem(1L, request);

        then(portfolioItemRepository).should(org.mockito.Mockito.times(2)).save(any(PortfolioItem.class));
    }

    // ===== getMyPortfolio =====

    @Test
    @DisplayName("조회: 포트폴리오가 비어 있으면 빈 리스트 반환")
    void getMyPortfolio_empty() {
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of());

        List<PortfolioItemResponse> result = portfolioService.getMyPortfolio(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("조회: variantId 있는 항목의 시세를 포함해 항목 수만큼 반환")
    void getMyPortfolio_success() {
        // item1: variantId null (대표 변형도 없음), item2: variantId=5
        PortfolioItem item1 = stubItem(1L, 10L, null);
        PortfolioItem item2 = stubItem(1L, 20L, 5L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item1, item2));
        given(cardRepository.findAllById(any())).willReturn(List.of(stubCard(10L), stubCard(20L)));
        // item1의 cardId=10은 대표 변형 없음
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(anyList())).willReturn(List.of());
        // item2의 variantId=5 → findAllById({5}) 호출
        given(cardVariantRepository.findAllById(any())).willReturn(List.of());
        // 시세 조회 (variantId=5 기준)
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of());

        List<PortfolioItemResponse> result = portfolioService.getMyPortfolio(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("조회: CardNameKoResolver로 구한 한글명을 cardNameKo로 함께 내려준다")
    void getMyPortfolio_includesCardNameKo() {
        PortfolioItem item = stubItem(1L, 10L, null);
        Card card = stubCard(10L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardRepository.findAllById(any())).willReturn(List.of(card));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(anyList())).willReturn(List.of());
        given(cardNameKoResolver.resolve(card)).willReturn("리자몽 한글명");

        List<PortfolioItemResponse> result = portfolioService.getMyPortfolio(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cardNameKo()).isEqualTo("리자몽 한글명");
    }

    // ===== updateItem =====

    @Test
    @DisplayName("수정: 본인 항목이 아니면 PORTFOLIO_ITEM_NOT_FOUND")
    void updateItem_notFound() {
        given(portfolioItemRepository.findByIdAndUserId(1L, 99L)).willReturn(Optional.empty());
        PortfolioItemUpdateRequest request = new PortfolioItemUpdateRequest(3, null, null);

        assertThatThrownBy(() -> portfolioService.updateItem(99L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 수량만 변경하면 나머지 필드는 유지된다")
    void updateItem_quantityOnly() {
        PortfolioItem item = stubItem(1L, 10L, null);
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardRepository.findById(10L)).willReturn(Optional.of(stubCard(10L)));
        // variantId null + 대표 변형 없음 → cardPriceRepository 호출 안 됨
        given(cardVariantRepository.findPrimaryVariantId(10L)).willReturn(Optional.empty());

        PortfolioItemUpdateRequest request = new PortfolioItemUpdateRequest(5, null, null);
        PortfolioItemResponse response = portfolioService.updateItem(1L, 1L, request);

        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.acquiredPrice()).isEqualTo(50000); // 원래 값 유지
    }

    // ===== deleteItem =====

    @Test
    @DisplayName("삭제: 본인 항목이 아니면 PORTFOLIO_ITEM_NOT_FOUND")
    void deleteItem_notFound() {
        given(portfolioItemRepository.findByIdAndUserId(1L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.deleteItem(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND);
        then(portfolioItemRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("삭제: 정상 케이스면 repository.delete() 호출")
    void deleteItem_success() {
        PortfolioItem item = stubItem(1L, 10L, null);
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));

        portfolioService.deleteItem(1L, 1L);

        then(portfolioItemRepository).should().delete(item);
    }

    // ===== addFromCompletedTrade =====

    @Test
    @DisplayName("거래 연동: 이미 동일 tradeId로 등록된 경우 중복 저장하지 않는다")
    void addFromCompletedTrade_idempotent() {
        given(portfolioItemRepository.existsByTradeId(1L)).willReturn(true);

        portfolioService.addFromCompletedTrade(1L, 1L, 10L, null, 50000);

        then(portfolioItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("거래 연동: 신규 tradeId면 포트폴리오 항목 저장")
    void addFromCompletedTrade_success() {
        given(portfolioItemRepository.existsByTradeId(1L)).willReturn(false);
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));

        portfolioService.addFromCompletedTrade(1L, 1L, 10L, 5L, 50000);

        then(portfolioItemRepository).should().save(any(PortfolioItem.class));
    }

    // ===== getSummary =====

    private CardPriceRepository.VariantMarketPriceView variantMarketPriceView(
            Long variantId, BigDecimal market, String currency, BigDecimal change1dPct) {
        return new CardPriceRepository.VariantMarketPriceView() {
            @Override
            public Long getVariantId() {
                return variantId;
            }

            @Override
            public BigDecimal getMarket() {
                return market;
            }

            @Override
            public String getCurrency() {
                return currency;
            }

            @Override
            public BigDecimal getChange1dPct() {
                return change1dPct;
            }
        };
    }

    @Test
    @DisplayName("총 평가액: 보유 카드가 없으면 0으로 반환")
    void getSummary_empty() {
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of());

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        assertThat(result).isEqualTo(new PortfolioSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    @DisplayName("총 평가액: 시세 정보가 없는 항목은 계산에서 제외되어 0으로 반환")
    void getSummary_noPriceData() {
        PortfolioItem item = stubItem(1L, 10L, null);
        ReflectionTestUtils.setField(item, "id", 100L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardVariantRepository.findPrimaryVariantIdsByCardIds(anyList())).willReturn(List.of());

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        assertThat(result).isEqualTo(new PortfolioSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    @DisplayName("총 평가액: 전일 대비 등락 없이(change1dPct=null) 평가액만 정상 계산")
    void getSummary_withoutChange() {
        PortfolioItem item = stubItem(1L, 10L, 5L);
        ReflectionTestUtils.setField(item, "id", 100L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10000"), "KRW", null)));

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        // 전일가도 시세와 동일(10000.00)해서 등락은 0이지만, 나눗셈을 거치는 경로라 스케일이 0.00으로 남는다.
        assertThat(result).isEqualTo(new PortfolioSummaryResponse(
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("0.00"), "KRW"));
    }

    @Test
    @DisplayName("총 평가액: change1dPct 반영해 전일 대비 등락액·등락률 계산")
    void getSummary_withPositiveChange() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L)
                .cardId(10L)
                .variantId(5L)
                .quantity(2)
                .acquiredPrice(50000)
                .acquiredAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(item, "id", 100L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10000"), "KRW", new BigDecimal("25"))));

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        // market=10000, qty=2 → totalValue=20000 / 전일가=10000/1.25=8000 → previousValue=16000
        // changeAmount=4000, changeRate=4000/16000*100=25.00
        assertThat(result).isEqualTo(new PortfolioSummaryResponse(
                new BigDecimal("20000"), new BigDecimal("4000"), new BigDecimal("25.00"), "KRW"));
    }

    @Test
    @DisplayName("총 평가액: USD/JPY 시세는 고정 환율(1400원/9원)로 환산해 합산한다")
    void getSummary_convertsUsdAndJpyToKrw() {
        PortfolioItem usdItem = PortfolioItem.builder()
                .userId(1L).cardId(10L).variantId(5L).quantity(1).build();
        ReflectionTestUtils.setField(usdItem, "id", 100L);
        PortfolioItem jpyItem = PortfolioItem.builder()
                .userId(1L).cardId(20L).variantId(6L).quantity(1).build();
        ReflectionTestUtils.setField(jpyItem, "id", 200L);

        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(usdItem, jpyItem));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(
                        variantMarketPriceView(5L, new BigDecimal("10"), "USD", null),
                        variantMarketPriceView(6L, new BigDecimal("100"), "JPY", null)));

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        // USD: 10*1400=14000, JPY: 100*9=900 → 총 14900
        assertThat(result.totalValue()).isEqualTo(new BigDecimal("14900"));
        assertThat(result.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("총 평가액: 지원하지 않는 통화는 시세 없음과 동일하게 계산에서 제외된다")
    void getSummary_excludesUnsupportedCurrency() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L).cardId(10L).variantId(5L).quantity(1).build();
        ReflectionTestUtils.setField(item, "id", 100L);

        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10000"), "EUR", null)));

        PortfolioSummaryResponse result = portfolioService.getSummary(1L);

        assertThat(result).isEqualTo(new PortfolioSummaryResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    // ===== getPnl =====

    @Test
    @DisplayName("손익: 본인 항목이 아니면 PORTFOLIO_ITEM_NOT_FOUND")
    void getPnl_notFound() {
        given(portfolioItemRepository.findByIdAndUserId(1L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPnl(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("손익: 취득가가 없으면 PORTFOLIO_ACQUIRED_PRICE_REQUIRED")
    void getPnl_acquiredPriceMissing() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L)
                .cardId(10L)
                .variantId(5L)
                .quantity(1)
                .acquiredPrice(null)
                .build();
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> portfolioService.getPnl(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_ACQUIRED_PRICE_REQUIRED);
        then(cardPriceRepository).should(never()).findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("손익: 시세 정보가 없으면 PORTFOLIO_PRICE_NOT_FOUND")
    void getPnl_priceNotFound() {
        PortfolioItem item = stubItem(1L, 10L, 5L);
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of());

        assertThatThrownBy(() -> portfolioService.getPnl(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_PRICE_NOT_FOUND);
    }

    @Test
    @DisplayName("손익: variantId가 null이고 대표 변형도 없으면 PORTFOLIO_PRICE_NOT_FOUND")
    void getPnl_noVariant_priceNotFound() {
        PortfolioItem item = stubItem(1L, 10L, null);
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardVariantRepository.findPrimaryVariantId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPnl(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_PRICE_NOT_FOUND);
        then(cardPriceRepository).should(never()).findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("손익: 현재 시세가 취득가보다 높으면 이익(+)으로 계산")
    void getPnl_profit() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L)
                .cardId(10L)
                .variantId(5L)
                .quantity(2)
                .acquiredPrice(8000)
                .acquiredAt(LocalDateTime.now())
                .build();
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10000"), "KRW", null)));

        PortfolioItemPnlResponse result = portfolioService.getPnl(1L, 1L);

        // 취득총액=8000*2=16000, 현재총액=10000*2=20000 → pnlAmount=4000, pnlRate=4000/16000*100=25.00
        assertThat(result).isEqualTo(new PortfolioItemPnlResponse(
                null, 10L, 2, 8000, new BigDecimal("10000"), "KRW",
                new BigDecimal("4000"), new BigDecimal("25.00")));
    }

    @Test
    @DisplayName("손익: 현재 시세가 취득가보다 낮으면 손실(-)로 계산")
    void getPnl_loss() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L)
                .cardId(10L)
                .variantId(5L)
                .quantity(1)
                .acquiredPrice(10000)
                .acquiredAt(LocalDateTime.now())
                .build();
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("7500"), "KRW", null)));

        PortfolioItemPnlResponse result = portfolioService.getPnl(1L, 1L);

        // pnlAmount=7500-10000=-2500, pnlRate=-2500/10000*100=-25.00
        assertThat(result).isEqualTo(new PortfolioItemPnlResponse(
                null, 10L, 1, 10000, new BigDecimal("7500"), "KRW",
                new BigDecimal("-2500"), new BigDecimal("-25.00")));
    }

    @Test
    @DisplayName("손익: 지원하지 않는 통화면 PORTFOLIO_PRICE_NOT_FOUND")
    void getPnl_unsupportedCurrency_priceNotFound() {
        PortfolioItem item = stubItem(1L, 10L, 5L);
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10000"), "EUR", null)));

        assertThatThrownBy(() -> portfolioService.getPnl(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_PRICE_NOT_FOUND);
    }

    @Test
    @DisplayName("손익: USD 시세는 고정 환율(1400원)로 환산해 손익을 계산하고 응답도 KRW로 내려간다")
    void getPnl_convertsUsdToKrw() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L).cardId(10L).variantId(5L).quantity(1).acquiredPrice(10000)
                .acquiredAt(LocalDateTime.now()).build();
        given(portfolioItemRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(item));
        given(cardPriceRepository.findMarketPricesByVariantIds(anyList(), anyString(), anyString(), anyString()))
                .willReturn(List.of(variantMarketPriceView(5L, new BigDecimal("10"), "USD", null)));

        PortfolioItemPnlResponse result = portfolioService.getPnl(1L, 1L);

        // 현재 시세: 10*1400=14000원 → pnlAmount=14000-10000=4000, pnlRate=40.00
        assertThat(result).isEqualTo(new PortfolioItemPnlResponse(
                null, 10L, 1, 10000, new BigDecimal("14000"), "KRW",
                new BigDecimal("4000"), new BigDecimal("40.00")));
    }

    // ===== getAnalytics =====

    private Card stubCard(Long id, String setName, String rarity) {
        return Card.builder()
                .id(id)
                .externalId("ext-" + id)
                .name("리자몽")
                .setName(setName)
                .rarity(rarity)
                .build();
    }

    @Test
    @DisplayName("구성비율: 보유 카드가 없으면 빈 목록 반환")
    void getAnalytics_empty() {
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of());

        PortfolioAnalyticsResponse result = portfolioService.getAnalytics(1L);

        assertThat(result.bySet()).isEmpty();
        assertThat(result.byRarity()).isEmpty();
    }

    @Test
    @DisplayName("구성비율: 카드 정보를 찾을 수 없으면 '미분류' 수량으로 집계된다")
    void getAnalytics_cardNotFound() {
        PortfolioItem item = stubItem(1L, 10L, null);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardRepository.findAllById(any())).willReturn(List.of());

        PortfolioAnalyticsResponse result = portfolioService.getAnalytics(1L);

        assertThat(result.bySet()).containsExactly(
                new PortfolioAnalyticsItemResponse("미분류", BigDecimal.ONE, new BigDecimal("100.00")));
        assertThat(result.byRarity()).containsExactly(
                new PortfolioAnalyticsItemResponse("미분류", BigDecimal.ONE, new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("구성비율: 세트·레어도 정보가 없는 카드는 '미분류'로 집계된다")
    void getAnalytics_unclassified() {
        PortfolioItem item = PortfolioItem.builder()
                .userId(1L)
                .cardId(10L)
                .variantId(5L)
                .quantity(1)
                .build();
        ReflectionTestUtils.setField(item, "id", 100L);
        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item));
        given(cardRepository.findAllById(any())).willReturn(List.of(stubCard(10L, null, null)));

        PortfolioAnalyticsResponse result = portfolioService.getAnalytics(1L);

        assertThat(result.bySet()).containsExactly(
                new PortfolioAnalyticsItemResponse("미분류", BigDecimal.ONE, new BigDecimal("100.00")));
        assertThat(result.byRarity()).containsExactly(
                new PortfolioAnalyticsItemResponse("미분류", BigDecimal.ONE, new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("구성비율: 세트별·레어도별 보유 수량 비중을 내림차순으로 반환한다")
    void getAnalytics_success() {
        // item1: 세트 A / 레어도 R, 수량 3 / item2: 세트 B / 레어도 R, 수량 1
        PortfolioItem item1 = PortfolioItem.builder()
                .userId(1L).cardId(10L).variantId(5L).quantity(3).build();
        ReflectionTestUtils.setField(item1, "id", 100L);
        PortfolioItem item2 = PortfolioItem.builder()
                .userId(1L).cardId(20L).variantId(6L).quantity(1).build();
        ReflectionTestUtils.setField(item2, "id", 200L);

        given(portfolioItemRepository.findByUserIdOrderByIdDesc(1L)).willReturn(List.of(item1, item2));
        given(cardRepository.findAllById(any())).willReturn(List.of(
                stubCard(10L, "세트A", "레어도R"),
                stubCard(20L, "세트B", "레어도R")));

        PortfolioAnalyticsResponse result = portfolioService.getAnalytics(1L);

        // 총 수량=4, 세트A=3(75%), 세트B=1(25%) → 내림차순
        assertThat(result.bySet()).containsExactly(
                new PortfolioAnalyticsItemResponse("세트A", new BigDecimal("3"), new BigDecimal("75.00")),
                new PortfolioAnalyticsItemResponse("세트B", BigDecimal.ONE, new BigDecimal("25.00")));
        // 레어도는 둘 다 "레어도R"로 합산되어 100%
        assertThat(result.byRarity()).containsExactly(
                new PortfolioAnalyticsItemResponse("레어도R", new BigDecimal("4"), new BigDecimal("100.00")));
    }

    // ===== addFromGradeResult =====

    private GradeResult stubGradeResult(Long userId, GradeStatus status, String grade, String visionCardId) {
        return GradeResult.builder()
                .userId(userId)
                .status(status)
                .grade(grade)
                .visionCardId(visionCardId)
                .isFree(true)
                .pointUsed(0)
                .retryAllowed(false)
                .build();
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 존재하지 않는 결과면 GRADE_RESULT_NOT_FOUND")
    void addFromGradeResult_notFound() {
        given(gradeResultRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GRADE_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 본인 결과가 아니면 ACCESS_DENIED")
    void addFromGradeResult_accessDenied() {
        GradeResult gradeResult = stubGradeResult(99L, GradeStatus.SUCCESS, "S", "base1-4");
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 품질 실패(QUALITY_FAIL) 결과면 GRADE_RESULT_NOT_REGISTRABLE")
    void addFromGradeResult_notRegistrable() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.QUALITY_FAIL, null, null);
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GRADE_RESULT_NOT_REGISTRABLE);
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 이미 등록된 결과면 GRADE_RESULT_ALREADY_REGISTERED")
    void addFromGradeResult_alreadyRegistered() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "S", "base1-4");
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(true);

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GRADE_RESULT_ALREADY_REGISTERED);
        then(portfolioItemRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("도감 등록(AI 진단): vision_card_id로 카드를 못 찾으면 CARD_NOT_FOUND")
    void addFromGradeResult_cardNotFound() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "S", "base1-4");
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(false);
        given(cardRepository.findByExternalId("base1-4")).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 정상 케이스면 vision_card_id로 카드를 해석해 등록한다")
    void addFromGradeResult_success() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "S", "base1-4");
        Card card = stubCard(10L);
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(false);
        given(cardRepository.findByExternalId("base1-4")).willReturn(Optional.of(card));
        given(cardVariantRepository.findPrimaryVariantId(10L)).willReturn(Optional.empty());
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));

        PortfolioItemResponse response = portfolioService.addFromGradeResult(1L, 1L, null, null);

        assertThat(response.cardId()).isEqualTo(10L);
        assertThat(response.quantity()).isEqualTo(1);
        then(portfolioItemRepository).should().save(any(PortfolioItem.class));
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 카드를 인식하지 못했어도 cardId를 직접 지정하면 그 카드로 등록한다")
    void addFromGradeResult_overrideCardId_whenUnrecognized() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "A", null);
        Card overrideCard = stubCard(20L);
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(false);
        given(cardRepository.findById(20L)).willReturn(Optional.of(overrideCard));
        given(cardVariantRepository.findPrimaryVariantId(20L)).willReturn(Optional.empty());
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));

        PortfolioItemResponse response = portfolioService.addFromGradeResult(1L, 1L, 20L, null);

        assertThat(response.cardId()).isEqualTo(20L);
        then(cardRepository).should(never()).findByExternalId(any());
        then(portfolioItemRepository).should().save(any(PortfolioItem.class));
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 지정한 cardId가 존재하지 않으면 CARD_NOT_FOUND")
    void addFromGradeResult_overrideCardId_notFound() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "A", null);
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(false);
        given(cardRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.addFromGradeResult(1L, 1L, 999L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_NOT_FOUND);
    }

    @Test
    @DisplayName("도감 등록(AI 진단): 진단에 쓴 앞면 사진을 표지로 저장하고, 조회 시 presigned URL로 내려준다")
    void addFromGradeResult_usesFrontImageAsThumbnail() {
        GradeResult gradeResult = stubGradeResult(1L, GradeStatus.SUCCESS, "S", "base1-4");
        Card card = stubCard(10L);
        GradeResultImage front = GradeResultImage.builder()
                .gradeResultId(1L).photoType(PhotoType.FRONT).imageUrl("ai-grade/front-key.png").build();
        GradeResultImage back = GradeResultImage.builder()
                .gradeResultId(1L).photoType(PhotoType.BACK).imageUrl("ai-grade/back-key.png").build();
        given(gradeResultRepository.findById(1L)).willReturn(Optional.of(gradeResult));
        given(portfolioItemRepository.existsByGradeResultId(1L)).willReturn(false);
        given(cardRepository.findByExternalId("base1-4")).willReturn(Optional.of(card));
        given(cardVariantRepository.findPrimaryVariantId(10L)).willReturn(Optional.empty());
        given(gradeResultImageRepository.findByGradeResultId(1L)).willReturn(List.of(back, front));
        given(s3FileStorage.generatePresignedUrl("ai-grade/front-key.png")).willReturn("https://s3.example/front-presigned");
        given(portfolioItemRepository.save(any(PortfolioItem.class))).willAnswer(inv -> inv.getArgument(0));

        PortfolioItemResponse response = portfolioService.addFromGradeResult(1L, 1L, null, null);

        assertThat(response.cardImageSmall()).isEqualTo("https://s3.example/front-presigned");
    }

    // ===== setThumbnail =====

    @Test
    @DisplayName("표지 사진 변경: 정상 업로드 시 S3 key를 저장하고 presigned URL을 반환한다")
    void setThumbnail_success() {
        PortfolioItem item = PortfolioItem.builder().userId(1L).cardId(10L).quantity(1).build();
        Card card = stubCard(10L);
        MultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[10]);
        given(portfolioItemRepository.findByIdAndUserId(5L, 1L)).willReturn(Optional.of(item));
        given(s3FileStorage.upload(image, "portfolio")).willReturn("portfolio/new-key.png");
        given(s3FileStorage.generatePresignedUrl("portfolio/new-key.png")).willReturn("https://s3.example/new-presigned");
        given(cardRepository.findById(10L)).willReturn(Optional.of(card));
        given(cardVariantRepository.findPrimaryVariantId(10L)).willReturn(Optional.empty());

        PortfolioItemResponse response = portfolioService.setThumbnail(1L, 5L, image);

        assertThat(item.getThumbnailKey()).isEqualTo("portfolio/new-key.png");
        assertThat(response.cardImageSmall()).isEqualTo("https://s3.example/new-presigned");
    }

    @Test
    @DisplayName("표지 사진 변경: 본인 소유가 아니거나 없는 항목이면 PORTFOLIO_ITEM_NOT_FOUND")
    void setThumbnail_notFound() {
        MultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[10]);
        given(portfolioItemRepository.findByIdAndUserId(5L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.setThumbnail(1L, 5L, image))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PORTFOLIO_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("표지 사진 변경: 이미지가 아닌 파일이면 UNSUPPORTED_IMAGE_TYPE")
    void setThumbnail_unsupportedType() {
        MultipartFile pdf = new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> portfolioService.setThumbnail(1L, 5L, pdf))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        then(portfolioItemRepository).should(never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("표지 사진 변경: 5MB를 초과하면 FILE_TOO_LARGE")
    void setThumbnail_tooLarge() {
        MultipartFile large = new MockMultipartFile("image", "a.png", "image/png", new byte[(int) (5 * 1024 * 1024 + 1)]);

        assertThatThrownBy(() -> portfolioService.setThumbnail(1L, 5L, large))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }
}
