package com.aishop.api.catalog;

/** 상품 상태등급. DB의 skus.condition_grade CHECK 제약과 같은 값 */
public enum ConditionGrade {

	NEW("새상품"),
	RETURN_UNOPENED("반품 미개봉"),
	RETURN_OPENED("반품 개봉"),
	REFURB("리퍼"),
	USED_A("중고 A급"),
	USED_B("중고 B급"),
	USED_C("중고 C급"),
	DISPLAY("전시상품");

	private final String label;

	ConditionGrade(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
