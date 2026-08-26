package com.delipot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.delipot.chat.dto.ChatMessagePageResponse;
import com.delipot.chat.dto.ChatMessageResponse;
import com.delipot.chat.dto.ChatRoomCreateRequest;
import com.delipot.chat.dto.ChatRoomDetailResponse;
import com.delipot.chat.dto.ChatRoomResponse;
import com.delipot.chat.dto.ChatRoomSummaryResponse;
import com.delipot.global.error.BusinessException;
import com.delipot.global.error.ErrorCode;
import com.delipot.member.Member;
import com.delipot.member.MemberRepository;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	private static final Instant FIXED = Instant.parse("2026-08-25T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

	@Mock
	private ChatRoomRepository chatRoomRepository;
	@Mock
	private ChatRoomMemberRepository chatRoomMemberRepository;
	@Mock
	private ChatMessageRepository chatMessageRepository;
	@Mock
	private MemberRepository memberRepository;
	@Mock
	private ChatImageUploader chatImageUploader;
	@Mock
	private SimpMessagingTemplate messagingTemplate;

	private ChatService chatService;

	@BeforeEach
	void setUp() {
		chatService = new ChatService(
			chatRoomRepository, chatRoomMemberRepository, chatMessageRepository,
			memberRepository, chatImageUploader, messagingTemplate, CLOCK
		);
	}

	private static void setId(Object entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
	}

	@Test
	@DisplayName("요청자 본인이 참여자 목록에 없으면 방을 만들 수 없다")
	void createRoom_requesterNotIncluded() {
		ChatRoomCreateRequest request = new ChatRoomCreateRequest("방", List.of(2L, 3L));

		assertThatThrownBy(() -> chatService.createRoom(1L, request))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);

		verifyNoInteractions(chatRoomRepository);
	}

	@Test
	@DisplayName("참여자 목록에 중복이 있어도 멤버는 한 번씩만 저장된다")
	void createRoom_dedupesMembers() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		given(chatRoomRepository.save(any())).willReturn(room);

		ChatRoomCreateRequest request = new ChatRoomCreateRequest("방", List.of(1L, 2L, 1L));
		ChatRoomResponse response = chatService.createRoom(1L, request);

		assertThat(response.name()).isEqualTo("방");
		verify(chatRoomMemberRepository, times(2)).save(any());
	}

	@Test
	@DisplayName("방 상세는 참여자 수와 닉네임 목록을 함께 반환한다")
	void getRoom_success() {
		ChatRoom room = ChatRoom.create("방", "동진시장 사거리 편의점 앞", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.of(membership));
		given(chatRoomMemberRepository.findByChatRoomId(10L)).willReturn(List.of(
			membership, ChatRoomMember.join(room, 2L, OffsetDateTime.now(CLOCK))
		));

		Member member1 = Member.register("01011111111", "hash", "닉네임1", "주소");
		setId(member1, 1L);
		Member member2 = Member.register("01022222222", "hash", "닉네임2", "주소");
		setId(member2, 2L);
		given(memberRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(member1, member2));

		ChatRoomDetailResponse response = chatService.getRoom(1L, 10L);

		assertThat(response.location()).isEqualTo("동진시장 사거리 편의점 앞");
		assertThat(response.memberCount()).isEqualTo(2);
		assertThat(response.members()).extracting("nickname").containsExactly("닉네임1", "닉네임2");
	}

	@Test
	@DisplayName("방을 나간 사람도 과거에 메시지를 보낸 적 있으면 닉네임 목록에 남는다 — memberCount에는 안 잡힌다")
	void getRoom_includesNicknameOfMemberWhoLeft() {
		ChatRoom room = ChatRoom.create("방", "동진시장 사거리 편의점 앞", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.of(membership));
		// 2L은 이미 나가서 활성 멤버십이 없다 — findByChatRoomId 결과에 1L만 남는다.
		given(chatRoomMemberRepository.findByChatRoomId(10L)).willReturn(List.of(membership));
		given(chatMessageRepository.findDistinctSenderIds(10L)).willReturn(List.of(1L, 2L));

		Member member1 = Member.register("01011111111", "hash", "닉네임1", "주소");
		setId(member1, 1L);
		Member member2 = Member.register("01022222222", "hash", "나간사람", "주소");
		setId(member2, 2L);
		given(memberRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(member1, member2));

		ChatRoomDetailResponse response = chatService.getRoom(1L, 10L);

		assertThat(response.memberCount()).isEqualTo(1);
		assertThat(response.members()).extracting("nickname").containsExactlyInAnyOrder("닉네임1", "나간사람");
	}

	@Test
	@DisplayName("참여자가 아닌 memberId는 방 상세 조회가 거부된다")
	void getRoom_accessDenied() {
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.getRoom(1L, 10L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
	}

	@Test
	@DisplayName("한 번도 안 읽었으면(lastReadMessageId=null) 0 기준으로 안읽은 개수를 센다")
	void getMyRooms_unreadCountFromScratch() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByMemberId(1L)).willReturn(List.of(membership));
		given(chatMessageRepository.findFirstByChatRoomIdOrderByIdDesc(10L)).willReturn(Optional.empty());
		given(chatMessageRepository.countUnread(10L, 0L, 1L)).willReturn(3L);

		List<ChatRoomSummaryResponse> result = chatService.getMyRooms(1L);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).unreadCount()).isEqualTo(3L);
		assertThat(result.get(0).lastMessagePreview()).isNull();
	}

	@Test
	@DisplayName("참여자가 아닌 memberId는 메시지 조회가 거부된다 (방 존재 여부와 무관하게 동일한 응답)")
	void getMessages_accessDenied() {
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.getMessages(1L, 10L, null, 20))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
	}

	@Test
	@DisplayName("size보다 1개 더 조회해서 다음 페이지 존재 여부와 커서를 판단한다")
	void getMessages_paginationCursor() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L))
			.willReturn(Optional.of(ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK))));

		List<ChatMessage> fetched = new ArrayList<>();
		for (long id = 5; id >= 1; id--) {
			ChatMessage message = ChatMessage.write(room, 1L, "msg" + id, OffsetDateTime.now(CLOCK));
			setId(message, id);
			fetched.add(message);
		}
		given(chatMessageRepository.findPage(eq(10L), isNull(), any())).willReturn(fetched);

		ChatMessagePageResponse response = chatService.getMessages(1L, 10L, null, 4);

		assertThat(response.messages()).hasSize(4);
		assertThat(response.hasNext()).isTrue();
		assertThat(response.nextCursor()).isEqualTo(2L);
	}

	@Test
	@DisplayName("방을 읽으면 최신 메시지 id로 lastReadMessageId가 갱신된다")
	void markRoomRead_advancesToLatestMessage() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.of(membership));

		ChatMessage lastMessage = ChatMessage.write(room, 2L, "안녕", OffsetDateTime.now(CLOCK));
		setId(lastMessage, 5L);
		given(chatMessageRepository.findFirstByChatRoomIdOrderByIdDesc(10L)).willReturn(Optional.of(lastMessage));

		chatService.markRoomRead(1L, 10L);

		assertThat(membership.getLastReadMessageId()).isEqualTo(5L);
	}

	@Test
	@DisplayName("방에 메시지가 없으면 읽음 처리는 아무 것도 하지 않는다")
	void markRoomRead_noMessagesIsNoOp() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.of(membership));
		given(chatMessageRepository.findFirstByChatRoomIdOrderByIdDesc(10L)).willReturn(Optional.empty());

		chatService.markRoomRead(1L, 10L);

		assertThat(membership.getLastReadMessageId()).isNull();
	}

	@Test
	@DisplayName("참여자가 아닌 memberId는 읽음 처리가 거부된다")
	void markRoomRead_accessDenied() {
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.markRoomRead(1L, 10L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);

		verifyNoInteractions(chatMessageRepository);
	}

	@Test
	@DisplayName("이미지 메시지는 업로더가 돌려준 URL을 content로 IMAGE 타입 저장된다")
	void postImageMessage_success() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		ChatRoomMember membership = ChatRoomMember.join(room, 1L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.of(membership));

		MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[] {1, 2, 3});
		given(chatImageUploader.upload(10L, file))
			.willReturn("https://bucket.s3.ap-northeast-2.amazonaws.com/chat-images/10/x.png");
		given(chatMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		ChatMessageResponse response = chatService.postImageMessage(1L, 10L, file);

		assertThat(response.type()).isEqualTo(ChatMessage.MessageType.IMAGE);
		assertThat(response.content()).isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/chat-images/10/x.png");
		assertThat(response.senderId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("참여자가 아닌 memberId는 이미지 전송이 거부되고 업로드는 시도조차 안 한다")
	void postImageMessage_accessDenied() {
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 1L)).willReturn(Optional.empty());
		MockMultipartFile file = new MockMultipartFile("file", "cat.png", "image/png", new byte[] {1});

		assertThatThrownBy(() -> chatService.postImageMessage(1L, 10L, file))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED);

		verifyNoInteractions(chatImageUploader);
	}

	@Test
	@DisplayName("아직 방 멤버가 아니면 addMember가 새 멤버십을 만든다")
	void addMember_addsWhenNotAlreadyMember() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L)).willReturn(Optional.empty());
		given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));

		chatService.addMember(10L, 2L);

		verify(chatRoomMemberRepository).save(any());
	}

	@Test
	@DisplayName("이미 방 멤버면 addMember는 아무 것도 하지 않는다(재시도 안전)")
	void addMember_noOpWhenAlreadyMember() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		ChatRoomMember existing = ChatRoomMember.join(room, 2L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L)).willReturn(Optional.of(existing));

		chatService.addMember(10L, 2L);

		verifyNoInteractions(chatRoomRepository);
		verify(chatRoomMemberRepository, times(0)).save(any());
	}

	@Test
	@DisplayName("removeMember는 방 멤버십을 지운다")
	void removeMember_deletesMembership() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		ChatRoomMember membership = ChatRoomMember.join(room, 2L, OffsetDateTime.now(CLOCK));
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L)).willReturn(Optional.of(membership));

		chatService.removeMember(10L, 2L);

		verify(chatRoomMemberRepository).delete(membership);
	}

	@Test
	@DisplayName("이미 멤버가 아니면 removeMember는 아무 것도 하지 않는다(재시도 안전)")
	void removeMember_noOpWhenNotMember() {
		given(chatRoomMemberRepository.findByChatRoomIdAndMemberId(10L, 2L)).willReturn(Optional.empty());

		chatService.removeMember(10L, 2L);

		verify(chatRoomMemberRepository, times(0)).delete(any());
	}

	@Test
	@DisplayName("입장 시스템 메시지는 senderId 없이 SYSTEM_JOIN 타입으로 저장된다")
	void postSystemJoinMessage_success() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
		given(chatMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		var response = chatService.postSystemNoticeMessage(10L, "동교동자취러님이 들어왔어요");

		assertThat(response.type()).isEqualTo(ChatMessage.MessageType.SYSTEM_JOIN);
		assertThat(response.senderId()).isNull();
		assertThat(response.content()).isEqualTo("동교동자취러님이 들어왔어요");
		assertThat(response.menuPrice()).isNull();
		verify(messagingTemplate).convertAndSend(eq("/topic/rooms/10"), eq(response));
	}

	@Test
	@DisplayName("존재하지 않는 방에 시스템 메시지를 남기려 하면 실패한다")
	void postSystemJoinMessage_roomNotFound() {
		given(chatRoomRepository.findById(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.postSystemNoticeMessage(999L, "content"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	@DisplayName("메뉴 시스템 메시지는 제출한 사람의 senderId와 menuPrice를 함께 저장한다")
	void postSystemMenuMessage_success() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
		given(chatMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		var response = chatService.postSystemMenuMessage(10L, 1L, "허니콤보 세트 (순살로 변경) + 콜라 제로 500ml", 12000);

		assertThat(response.type()).isEqualTo(ChatMessage.MessageType.SYSTEM_MENU);
		assertThat(response.senderId()).isEqualTo(1L);
		assertThat(response.menuPrice()).isEqualTo(12000);
	}

	@Test
	@DisplayName("가게 링크 메시지는 총대 senderId를 달고 LINK 타입으로, content엔 URL만 저장된다")
	void postStoreLinkMessage_success() {
		ChatRoom room = ChatRoom.create("방", OffsetDateTime.now(CLOCK));
		setId(room, 10L);
		given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
		given(chatMessageRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		var response = chatService.postStoreLinkMessage(10L, 1L, "https://web.coupangeats.com/share?storeId=781313");

		assertThat(response.type()).isEqualTo(ChatMessage.MessageType.LINK);
		assertThat(response.senderId()).isEqualTo(1L);
		assertThat(response.content()).isEqualTo("https://web.coupangeats.com/share?storeId=781313");
		assertThat(response.menuPrice()).isNull();
		verify(messagingTemplate).convertAndSend(eq("/topic/rooms/10"), eq(response));
	}

	@Test
	@DisplayName("존재하지 않는 방에 가게 링크 메시지를 남기려 하면 실패한다")
	void postStoreLinkMessage_roomNotFound() {
		given(chatRoomRepository.findById(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.postStoreLinkMessage(999L, 1L, "https://web.coupangeats.com/x"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}
}
