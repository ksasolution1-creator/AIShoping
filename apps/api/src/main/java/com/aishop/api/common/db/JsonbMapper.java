package com.aishop.api.common.db;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jooq.JSONB;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** jsonb 컬럼 ↔ Map 변환 */
@Component
public class JsonbMapper {

	private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	public JsonbMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public JSONB toJsonb(Map<String, String> map) {
		return JSONB.valueOf(jsonMapper.writeValueAsString(map == null ? Map.of() : map));
	}

	public Map<String, String> toStringMap(JSONB jsonb) {
		if (jsonb == null || jsonb.data() == null) {
			return Map.of();
		}
		return jsonMapper.readValue(jsonb.data(), STRING_MAP);
	}

}
