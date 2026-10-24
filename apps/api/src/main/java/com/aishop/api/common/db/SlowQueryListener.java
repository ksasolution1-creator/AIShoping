package com.aishop.api.common.db;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 기준 시간보다 오래 걸린 쿼리를 WARN 로그로 남긴다.
 * Sentry를 붙이면 WARN 로그가 그대로 수집된다.
 */
public class SlowQueryListener implements ExecuteListener {

	private static final Logger log = LoggerFactory.getLogger(SlowQueryListener.class);
	private static final String START_KEY = "aishop.query.start";

	private final long thresholdNanos;

	public SlowQueryListener(long thresholdMillis) {
		this.thresholdNanos = thresholdMillis * 1_000_000L;
	}

	@Override
	public void executeStart(ExecuteContext ctx) {
		ctx.data(START_KEY, System.nanoTime());
	}

	@Override
	public void executeEnd(ExecuteContext ctx) {
		Object start = ctx.data(START_KEY);
		if (!(start instanceof Long startNanos)) {
			return;
		}
		long elapsed = System.nanoTime() - startNanos;
		if (elapsed >= thresholdNanos) {
			log.warn("Slow query {}ms: {}", elapsed / 1_000_000L, ctx.sql());
		}
	}

}
