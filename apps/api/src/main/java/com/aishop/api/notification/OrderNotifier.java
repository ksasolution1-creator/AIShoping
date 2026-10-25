package com.aishop.api.notification;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.aishop.api.jooq.tables.records.OrderItemsRecord;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.notification.OrderEvents.BankOrderPlaced;
import com.aishop.api.notification.OrderEvents.DepositConfirmed;
import com.aishop.api.order.OrderRepository;
import com.aishop.api.payment.BankAccount;

/**
 * 무통장입금 이메일 알림.
 * - 새 주문: 운영자에게 입금자명·금액, 고객에게 입금 계좌·기한
 * - 입금 확인: 고객에게 확인 완료
 * - 입금 기한 임박: 운영자에게 아직 확인 안 된 주문 목록 ({@link DepositReminderJob})
 */
@Component
public class OrderNotifier {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final OrderRepository orders;
	private final BankAccount bank;
	private final Mailer mailer;
	private final String adminEmail;
	private final String shopUrl;
	private final String adminUrl;

	public OrderNotifier(OrderRepository orders, BankAccount bank, Mailer mailer,
			@Value("${aishop.notify.admin-email:}") String adminEmail,
			@Value("${aishop.social.redirect-base:http://localhost:3000}") String shopUrl,
			@Value("${aishop.notify.admin-url:http://localhost:3001}") String adminUrl) {
		this.orders = orders;
		this.bank = bank;
		this.mailer = mailer;
		this.adminEmail = adminEmail;
		this.shopUrl = shopUrl;
		this.adminUrl = adminUrl;
	}

	@Async
	@TransactionalEventListener
	public void onBankOrderPlaced(BankOrderPlaced event) {
		OrdersRecord order = orders.findByOrderNo(event.orderNo()).orElse(null);
		if (order == null) {
			return;
		}
		String items = itemLines(order);
		mailer.send(adminEmail, "[입금대기] " + order.getDepositorName() + " " + won(order.getPayAmount()),
				"""
				새 무통장입금 주문이 들어왔습니다. 통장에 입금되면 관리자 화면에서 입금 확인을 눌러 주세요.

				주문번호: %s
				입금자명: %s
				입금 금액: %s
				입금 기한: %s

				%s
				주문 보기: %s/orders/%s
				""".formatted(order.getOrderNo(), order.getDepositorName(), won(order.getPayAmount()),
						when(order.getDepositDueAt()), items, adminUrl, order.getOrderNo()));

		mailer.send(order.getOrdererEmail(), "[떨고래 마켓] 주문이 접수되었습니다. 입금해 주세요",
				"""
				%s님, 주문해 주셔서 감사합니다.
				아래 계좌로 입금해 주시면 확인 후 상품 준비를 시작합니다.

				입금 계좌: %s %s (예금주 %s)
				입금 금액: %s
				입금자명: %s
				입금 기한: %s까지 (기한이 지나면 주문이 자동으로 취소됩니다)

				주문번호: %s
				%s
				주문 상세: %s/orders/%s
				""".formatted(order.getOrdererName(), bank.bankName(), bank.accountNo(), bank.holder(),
						won(order.getPayAmount()), order.getDepositorName(), when(order.getDepositDueAt()),
						order.getOrderNo(), items, shopUrl, order.getOrderNo()));
	}

	@Async
	@TransactionalEventListener
	public void onDepositConfirmed(DepositConfirmed event) {
		OrdersRecord order = orders.findByOrderNo(event.orderNo()).orElse(null);
		if (order == null) {
			return;
		}
		mailer.send(order.getOrdererEmail(), "[떨고래 마켓] 입금이 확인되었습니다",
				"""
				%s님, %s 입금이 확인되었습니다. 상품 준비를 시작하며, 발송되면 주문 상세에서 송장번호를 확인할 수 있습니다.

				주문번호: %s
				%s
				주문 상세: %s/orders/%s
				""".formatted(order.getOrdererName(), won(order.getPayAmount()), order.getOrderNo(), itemLines(order),
						shopUrl, order.getOrderNo()));
	}

	/** 입금 기한이 다가오는데 아직 확인되지 않은 주문들을 운영자에게 한 번에 알린다 */
	public void remindDepositsDue(List<OrdersRecord> dueSoon) {
		if (dueSoon.isEmpty()) {
			return;
		}
		StringBuilder body = new StringBuilder("입금 기한이 다가오는데 아직 입금 확인이 안 된 주문입니다.\n")
			.append("통장에 입금됐는데 확인을 놓친 건이 없는지 봐 주세요. 기한이 지나면 자동으로 취소됩니다.\n\n");
		for (OrdersRecord order : dueSoon) {
			body.append("- ")
				.append(order.getOrderNo())
				.append(" · 입금자 ")
				.append(order.getDepositorName())
				.append(" · ")
				.append(won(order.getPayAmount()))
				.append(" · ")
				.append(when(order.getDepositDueAt()))
				.append("까지\n");
		}
		body.append("\n입금대기 주문 보기: ").append(adminUrl).append("/orders?status=PENDING_PAYMENT\n");
		mailer.send(adminEmail, "[입금 기한 임박] 미확인 " + dueSoon.size() + "건", body.toString());
	}

	private String itemLines(OrdersRecord order) {
		StringBuilder sb = new StringBuilder("주문 상품\n");
		for (OrderItemsRecord item : orders.findItems(order.getId())) {
			sb.append("- ").append(item.getProductName());
			if (item.getOptionText() != null) {
				sb.append(" (").append(item.getOptionText()).append(")");
			}
			sb.append(" × ").append(item.getQty()).append('\n');
		}
		return sb.toString();
	}

	private static String won(long amount) {
		return String.format("%,d원", amount);
	}

	private static String when(OffsetDateTime at) {
		return at == null ? "-" : at.atZoneSameInstant(SEOUL).format(WHEN);
	}

}
