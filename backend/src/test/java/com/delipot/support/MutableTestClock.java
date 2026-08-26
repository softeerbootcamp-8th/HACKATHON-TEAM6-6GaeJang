package com.delipot.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 시간을 임의로 진행시키기 위한 Clock. 만료/슬라이딩 검증에 쓴다. */
public class MutableTestClock extends Clock {

	private Instant instant;
	private final ZoneId zone;

	public MutableTestClock(Instant start) {
		this(start, ZoneOffset.UTC);
	}

	private MutableTestClock(Instant start, ZoneId zone) {
		this.instant = start;
		this.zone = zone;
	}

	/** 지정한 시간만큼 앞으로 감는다. */
	public void advance(Duration duration) {
		this.instant = this.instant.plus(duration);
	}

	@Override
	public Instant instant() {
		return instant;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableTestClock(instant, zone);
	}
}
