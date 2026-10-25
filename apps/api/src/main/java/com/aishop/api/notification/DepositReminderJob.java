package com.aishop.api.notification;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.order.OrderRepository;

/** 입금 기한이 remind-before-hours 안으로 들어온 무통장입금 대기 주문을 운영자에게 한 번 알린다 */
@Component
public class DepositReminderJob {

	private static final Logger log = LoggerFactory.getLogger(DepositReminderJob.class);

	private final OrderRepository orders;
	private final OrderNotifier notifier;
	private final TransactionTemplate tx;
	private final Duration remindBefore;

	public DepositReminderJob(OrderRepository orders, OrderNotifier notifier, TransactionTemplate tx,
			@Value("${aishop.notify.deposit-remind-before-hours:6}") long remindBeforeHours) {
		this.orders = orders;
		this.notifier = notifier;
		this.tx = tx;
		this.remindBefore = Duration.ofHours(remindBeforeHours);
	}

	@Scheduled(fixedDelayString = "${aishop.notify.deposit-remind-check-ms:600000}", initialDelay = 60_000)
	public void run() {
		// 알림 대상으로 표시하는 것까지 커밋한 뒤 메일을 보낸다 (여러 서버여도 한 번만 보낸다)
		List<OrdersRecord> dueSoon = tx.execute(status -> orders.claimDepositReminders(remindBefore, 100));
		try {
			notifier.remindDepositsDue(dueSoon == null ? List.of() : dueSoon);
		}
		catch (RuntimeException ex) {
			log.error("입금 기한 임박 알림 실패", ex);
		}
	}

}
