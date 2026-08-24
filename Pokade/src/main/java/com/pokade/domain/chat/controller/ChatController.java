package com.pokade.domain.chat.controller;

import com.pokade.domain.chat.dto.ChatHistoryImportRequest;
import com.pokade.domain.chat.dto.ChatHistoryImportResponse;
import com.pokade.domain.chat.dto.ChatHistoryResponse;
import com.pokade.domain.chat.dto.ChatQueryRequest;
import com.pokade.domain.chat.dto.ChatQueryResponse;
import com.pokade.domain.chat.dto.QuickQuestionResponse;
import com.pokade.domain.chat.service.ChatService;
import com.pokade.domain.chat.support.QuickQuestion;
import com.pokade.global.exception.BusinessException;
import com.pokade.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "시세 챗봇", description = "Tool Calling 기반 시세 질의응답 챗봇 API")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "시세 질의응답",
            description = "자연어 시세 질문(또는 FAQ 버튼의 프리셋 질문)을 보내면 Tool Calling 기반 답변을 반환합니다. " +
                    "비로그인 사용자는 FAQ 프리셋 질문만 보낼 수 있으며, 그 외 자유 입력은 401을 반환합니다(로그인 필요)."
    )
    @PostMapping("/query")
    public ResponseEntity<ChatQueryResponse> query(
            @Valid @RequestBody ChatQueryRequest request,
            @AuthenticationPrincipal Long principalUserId
    ) {
        ChatQueryResponse response = chatService.queryChat(request, principalUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "챗봇 대화 이력 조회",
            description = "로그인한 사용자가 본인이 주고받은 세션 내 대화 이력을 조회합니다."
    )
    @GetMapping("/history")
    public ResponseEntity<Page<ChatHistoryResponse>> getHistory(
            @RequestParam String sessionId,
            @AuthenticationPrincipal Long principalUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        if (principalUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Page<ChatHistoryResponse> response = chatService.getHistory(sessionId, principalUserId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "비로그인 히스토리 이관",
            description = "비로그인 상태로 localStorage에 쌓아둔 프리셋(급등/급락) 클릭 기록을 로그인/회원가입 직후 서버로 보내 "
                    + "채팅 히스토리에 반영합니다. 로그인이 필요하며, 답변 내용은 서버가 이 시점 기준으로 다시 계산합니다."
    )
    @PostMapping("/history/import")
    public ResponseEntity<ChatHistoryImportResponse> importHistory(
            @Valid @RequestBody ChatHistoryImportRequest request,
            @AuthenticationPrincipal Long principalUserId
    ) {
        if (principalUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ChatHistoryImportResponse response = chatService.importHistory(request, principalUserId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "FAQ 프리셋 질문 목록",
            description = "프론트에서 버튼으로 노출할 자주 묻는 질문 목록을 반환합니다. 버튼 클릭 시 question 값을 그대로 /api/chat/query에 보내면 됩니다."
    )
    @GetMapping("/quick-questions")
    public ResponseEntity<List<QuickQuestionResponse>> getQuickQuestions() {
        return ResponseEntity.ok(QuickQuestion.all());
    }
}
