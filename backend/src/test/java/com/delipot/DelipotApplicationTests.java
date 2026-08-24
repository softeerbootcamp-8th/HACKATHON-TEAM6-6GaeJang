package com.delipot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 스프링 컨텍스트가 끝까지 뜨는지 확인한다. MySQL 없이 돌도록 h2 프로파일을 쓴다. */
@SpringBootTest
@ActiveProfiles("h2")
class DelipotApplicationTests {

	@Test
	void contextLoads() {
	}
}
