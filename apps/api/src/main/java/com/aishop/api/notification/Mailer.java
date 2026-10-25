package com.aishop.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 텍스트 메일 발송. SMTP(MAIL_HOST)를 설정하지 않으면 보내지 않고 로그만 남긴다.
 * 알림 메일이 실패해도 주문 처리에는 영향을 주지 않도록 예외를 삼킨다.
 */
@Component
public class Mailer {

	private static final Logger log = LoggerFactory.getLogger(Mailer.class);

	private final ObjectProvider<JavaMailSender> sender;
	private final boolean enabled;
	private final String from;

	public Mailer(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host,
			@Value("${aishop.notify.mail-from:}") String from, @Value("${spring.mail.username:}") String username) {
		this.sender = sender;
		this.enabled = !host.isBlank();
		this.from = from.isBlank() ? username : from;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** @return 보냈으면 true */
	public boolean send(String to, String subject, String text) {
		if (to == null || to.isBlank()) {
			return false;
		}
		if (!enabled) {
			log.info("메일 미설정으로 발송 생략: [{}] {}", subject, to);
			return false;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		if (!from.isBlank()) {
			message.setFrom(from);
		}
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		try {
			sender.getObject().send(message);
			return true;
		}
		catch (MailException | IllegalStateException ex) {
			log.error("메일 발송 실패: [{}] {}", subject, to, ex);
			return false;
		}
	}

}
