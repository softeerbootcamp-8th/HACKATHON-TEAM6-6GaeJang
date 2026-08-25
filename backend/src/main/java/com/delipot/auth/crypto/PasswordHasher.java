package com.delipot.auth.crypto;

import org.springframework.stereotype.Component;

import at.favre.lib.crypto.bcrypt.BCrypt;

/** BCrypt 해싱 래퍼. Spring Security 없이 비밀번호를 안전하게 저장/검증한다. */
@Component
public class PasswordHasher {

	// 해커톤 규모에선 12 라운드가 안전/속도 균형이 무난하다.
	private static final int COST = 12;

	public String encode(String rawPassword) {
		return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray());
	}

	public boolean matches(String rawPassword, String hash) {
		return BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified;
	}
}
