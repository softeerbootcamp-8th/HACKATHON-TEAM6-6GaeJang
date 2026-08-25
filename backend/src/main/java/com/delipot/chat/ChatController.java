package com.delipot.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.auth.LoginMember;
import com.delipot.auth.RequireAuthenticate;
import com.delipot.chat.dto.ChatMessagePageResponse;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.chat.dto.ChatRoomSummaryResponse;
import com.delipot.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Chat", description = "채팅방/메시지")
@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@Operation(summary = "채팅방 생성", description = "참여자 목록(요청자 본인 포함)으로 그룹 채팅방을 만든다.")
	@RequireAuthenticate
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ChatRoomResponse> createRoom(
		@LoginMember Long memberId,
		@Valid @RequestBody ChatRoomCreateRequest request
	) {
		return ApiResponse.ok(chatService.createRoom(memberId, request));
	}

	@Operation(summary = "내 채팅방 목록", description = "내가 참여 중인 채팅방을 마지막 메시지·안읽은 개수와 함께 반환한다.")
	@RequireAuthenticate
	@GetMapping
	public ApiResponse<List<ChatRoomSummaryResponse>> getMyRooms(@LoginMember Long memberId) {
		return ApiResponse.ok(chatService.getMyRooms(memberId));
	}

	@Operation(summary = "채팅방 메시지 이력", description = "커서(before) 기준 이전 메시지를 최신순으로 반환한다.")
	@RequireAuthenticate
	@GetMapping("/{roomId}/messages")
	public ApiResponse<ChatMessagePageResponse> getMessages(
		@LoginMember Long memberId,
		@PathVariable Long roomId,
		@RequestParam(required = false) Long before,
		@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.ok(chatService.getMessages(memberId, roomId, before, size));
	}

	@Operation(summary = "채팅방 읽음 처리", description = "방의 최신 메시지까지 읽음 처리한다(안읽은 개수 초기화).")
	@RequireAuthenticate
	@PatchMapping("/{roomId}/read")
	public ApiResponse<Void> markRead(@LoginMember Long memberId, @PathVariable Long roomId) {
		chatService.markRoomRead(memberId, roomId);
		return ApiResponse.ok();
	}
}
