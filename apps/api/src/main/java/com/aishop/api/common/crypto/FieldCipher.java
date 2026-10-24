package com.aishop.api.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 개인정보 컬럼(*_enc) 암호화. AES-256-GCM, 저장 형식은 "v1:" + base64(iv + 암호문).
 * 키는 FIELD_ENCRYPTION_KEY 환경변수(32바이트를 base64로 인코딩한 값)로 받는다.
 */
@Component
public class FieldCipher {

	private static final Logger log = LoggerFactory.getLogger(FieldCipher.class);
	private static final String PREFIX = "v1:";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public FieldCipher(@Value("${aishop.crypto.field-key:}") String base64Key) {
		byte[] keyBytes;
		if (base64Key == null || base64Key.isBlank()) {
			// 로컬 개발용 고정 키. 운영에서는 반드시 FIELD_ENCRYPTION_KEY를 설정한다
			log.warn("FIELD_ENCRYPTION_KEY가 없어 개발용 키로 개인정보를 암호화합니다. 운영 환경에서는 반드시 설정하세요.");
			keyBytes = sha256("aishop-local-development-only");
		}
		else {
			keyBytes = Base64.getDecoder().decode(base64Key.strip());
			if (keyBytes.length != 32) {
				throw new IllegalStateException("FIELD_ENCRYPTION_KEY는 32바이트를 base64로 인코딩한 값이어야 합니다.");
			}
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plain) {
		if (plain == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
			return PREFIX + Base64.getEncoder().encodeToString(out);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("암호화 실패", ex);
		}
	}

	public String decrypt(String stored) {
		if (stored == null) {
			return null;
		}
		if (!stored.startsWith(PREFIX)) {
			throw new IllegalStateException("알 수 없는 암호문 형식");
		}
		try {
			byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("복호화 실패", ex);
		}
	}

	/** 결제창에 넘길 고객 식별값. 회원 ID를 그대로 노출하지 않도록 키로 서명한 값을 쓴다 */
	public String opaqueId(String purpose, long id) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
			byte[] digest = mac.doFinal((purpose + ":" + id).getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 32);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("식별값 생성 실패", ex);
		}
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
