package com.aishop.api.common.storage;

import org.springframework.web.multipart.MultipartFile;

/** 업로드 파일 저장소. 로컬 디스크 구현으로 시작하고 운영에서는 S3 구현으로 교체한다. */
public interface FileStorage {

	/**
	 * @param directory 저장 폴더 (예: products/12)
	 * @return 브라우저에서 접근할 수 있는 URL
	 */
	String store(String directory, MultipartFile file);

	void delete(String url);

}
