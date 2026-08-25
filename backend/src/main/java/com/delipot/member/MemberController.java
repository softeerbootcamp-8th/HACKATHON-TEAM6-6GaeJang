package com.delipot.member;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.delipot.global.response.ApiResponse;
import com.delipot.member.dto.NicknameAvailabilityResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "회원 정보")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@Operation(summary = "닉네임 중복확인", description = "온보딩 중 닉네임 입력마다 호출한다. available=true 면 사용 가능.")
	@GetMapping("/check-nickname")
	public ApiResponse<NicknameAvailabilityResponse> checkNickname(@RequestParam String nickname) {
		return ApiResponse.ok(new NicknameAvailabilityResponse(memberService.isNicknameAvailable(nickname)));
	}
}
