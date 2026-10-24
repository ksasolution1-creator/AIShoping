package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.SEARCH_LOGS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.trim;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

/** 쇼핑몰 검색어 기록과 인기검색어 집계 */
@Repository
public class SearchLogRepository {

	private final DSLContext dsl;

	public SearchLogRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public void log(String keyword) {
		dsl.insertInto(SEARCH_LOGS).set(SEARCH_LOGS.KEYWORD, keyword).execute();
	}

	/** 최근 기간에 많이 찾은 검색어 (대소문자·앞뒤 공백 무시) */
	public List<String> popular(Duration period, int limit) {
		Field<String> normalized = lower(trim(SEARCH_LOGS.KEYWORD));
		return dsl.select(normalized, count())
			.from(SEARCH_LOGS)
			.where(SEARCH_LOGS.CREATED_AT.gt(OffsetDateTime.now().minus(period)))
			.groupBy(normalized)
			.orderBy(count().desc(), normalized)
			.limit(limit)
			.fetch(r -> r.value1());
	}

}
