package com.example.lancerpick.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 일회용 토큰 만들기·해시(3인 설계 7.1). 원문 = SecureRandom 32바이트를 URL-safe Base64(패딩 없음, 43자)로 쓴 값.
 * DB에는 SHA-256(원문 32바이트)만 저장한다. 원문은 메일 링크(또는 가입 증명 응답)에만 들어간다.
 * 토큰이 2^256가지라 맞혀 볼 수 없으므로 비밀번호처럼 느린 해시(BCrypt)를 쓰지 않고, 해시로 바로 찾는다.
 * 비밀번호 재설정 토큰, 가입 인증 메일 토큰, 가입 증명(proof)이 모두 이 형식을 쓴다. 목적은 account_challenges.purpose로 구분한다.
 */
public final class OneTimeTokens {
    private static final int TOKEN_BYTES = 32;
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();   // 여러 스레드가 함께 써도 안전하다
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private OneTimeTokens() {
    }

    /** value: 메일 링크에 넣을 원문, hash: DB에 저장할 값. toString에 원문이 찍히지 않게 덮어쓴다. */
    public record Issued(String value, byte[] hash) {
        @Override
        public String toString() {
            return "Issued[value=***]";
        }
    }

    public static Issued issue() {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return new Issued(ENCODER.encodeToString(raw), sha256(raw));
    }

    /** 형식이 맞으면 DB 조회용 해시, 아니면 빈 값. 같은 바이트를 다른 글자로 쓴 값은 받지 않는다(표준형만). */
    public static Optional<byte[]> hashOf(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            return Optional.empty();
        }
        byte[] raw;
        try {
            raw = DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (raw.length != TOKEN_BYTES || !ENCODER.encodeToString(raw).equals(value)) {
            return Optional.empty();
        }
        return Optional.of(sha256(raw));
    }

    private static byte[] sha256(byte[] raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
