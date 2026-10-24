package com.aishop.api.order;

import java.util.Map;

/** 송장번호로 배송 조회 페이지 주소를 만든다 (tracker.delivery 공개 조회 페이지) */
public final class TrackingLinks {

	private static final Map<String, String> TRACKER_IDS = Map.of(
			"CJ", "kr.cjlogistics",
			"HANJIN", "kr.hanjin",
			"LOTTE", "kr.lotte",
			"EPOST", "kr.epost",
			"LOGEN", "kr.logen");

	private TrackingLinks() {
	}

	public static String url(String carrierCode, String trackingNo) {
		String trackerId = carrierCode == null ? null : TRACKER_IDS.get(carrierCode);
		return trackerId == null || trackingNo == null ? null
				: "https://tracker.delivery/#/" + trackerId + "/" + trackingNo;
	}

}
