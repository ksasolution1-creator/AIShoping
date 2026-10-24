package com.aishop.api.common.web;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long total) {

	public int totalPages() {
		return size == 0 ? 0 : (int) ((total + size - 1) / size);
	}

}
