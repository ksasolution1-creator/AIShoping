package com.aishop.api.order;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 배송완료 후 8일이 지나도록 구매확정하지 않은 상품을 자동으로 확정한다 */
@Component
public class PurchaseAutoConfirmJob {

	static final Duration AFTER_DELIVERY = Duration.ofDays(8);

	private static final Logger log = LoggerFactory.getLogger(PurchaseAutoConfirmJob.class);

	private final OrderRepository orders;

	public PurchaseAutoConfirmJob(OrderRepository orders) {
		this.orders = orders;
	}

	@Scheduled(cron = "${aishop.order.auto-confirm-cron:0 10 * * * *}", zone = "Asia/Seoul")
	public void run() {
		int confirmed = 0;
		List<Long> itemIds;
		int round;
		do {
			itemIds = orders.findAutoConfirmable(AFTER_DELIVERY, 200);
			round = 0;
			for (long itemId : itemIds) {
				// 조회와 확정 사이에 반품 신청이 들어오면 confirmItem이 건너뛴다
				if (orders.confirmItem(itemId)) {
					round++;
				}
			}
			confirmed += round;
		} while (itemIds.size() == 200 && round > 0);
		if (confirmed > 0) {
			log.info("자동 구매확정 {}건", confirmed);
		}
	}

}
