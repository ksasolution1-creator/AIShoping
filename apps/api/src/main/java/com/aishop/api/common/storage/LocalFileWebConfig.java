package com.aishop.api.common.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 로컬에 저장한 업로드 파일을 /files/** 로 제공한다. */
@Configuration(proxyBeanMethods = false)
public class LocalFileWebConfig implements WebMvcConfigurer {

	private final LocalFileStorage storage;

	public LocalFileWebConfig(LocalFileStorage storage) {
		this.storage = storage;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(LocalFileStorage.URL_PREFIX + "**")
			.addResourceLocations(storage.root().toUri().toString() + "/");
	}

}
