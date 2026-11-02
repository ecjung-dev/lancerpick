package com.example.lancerpick.wallet.gateway;

/** 원문 응답·인증 헤더·결제 식별자를 예외 메시지에 담지 않는다. */
public class TossGatewayException extends RuntimeException {
    public TossGatewayException() { super("Payment provider result is unavailable"); }
}
