package com.aishop.api.notification;

/** 알림을 보내야 하는 주문 이벤트. 주문 트랜잭션이 커밋된 뒤에 처리된다 */
public final class OrderEvents {

	private OrderEvents() {
	}

	/** 무통장입금 주문이 들어왔다 */
	public record BankOrderPlaced(String orderNo) {
	}

	/** 운영자가 무통장입금 입금을 확인했다 */
	public record DepositConfirmed(String orderNo) {
	}

}
