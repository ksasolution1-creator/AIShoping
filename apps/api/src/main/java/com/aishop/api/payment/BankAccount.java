package com.aishop.api.payment;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 무통장입금 받을 회사 계좌와 입금 기한 */
@Component
public class BankAccount {

	private final String bankName;
	private final String accountNo;
	private final String holder;
	private final Duration depositWindow;

	public BankAccount(@Value("${aishop.bank.name:}") String bankName,
			@Value("${aishop.bank.account-no:}") String accountNo, @Value("${aishop.bank.holder:}") String holder,
			@Value("${aishop.bank.deposit-hours:48}") long depositHours) {
		this.bankName = bankName;
		this.accountNo = accountNo;
		this.holder = holder;
		this.depositWindow = Duration.ofHours(depositHours);
	}

	public boolean isConfigured() {
		return !bankName.isBlank() && !accountNo.isBlank() && !holder.isBlank();
	}

	public String bankName() {
		return bankName;
	}

	public String accountNo() {
		return accountNo;
	}

	public String holder() {
		return holder;
	}

	public Duration depositWindow() {
		return depositWindow;
	}

}
