package com.aishop.api.common.db;

import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JooqConfig {

	@Bean
	ExecuteListenerProvider slowQueryListenerProvider(
			@Value("${aishop.db.slow-query-threshold-ms:200}") long thresholdMillis) {
		return new DefaultExecuteListenerProvider(new SlowQueryListener(thresholdMillis));
	}

}
