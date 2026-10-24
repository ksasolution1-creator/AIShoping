package com.aishop.api.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import com.aishop.api.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "aishop.db.slow-query-threshold-ms=0")
@ExtendWith(OutputCaptureExtension.class)
class SlowQueryListenerTests {

	@Autowired
	DSLContext dsl;

	@Test
	void logsQueriesOverThreshold(CapturedOutput output) {
		dsl.selectOne().fetch();

		assertThat(output).contains("Slow query").contains("select 1");
	}

}
