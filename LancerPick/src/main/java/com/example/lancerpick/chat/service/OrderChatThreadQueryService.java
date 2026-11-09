package com.example.lancerpick.chat.service;

import com.example.lancerpick.chat.dto.ChatThreadResponse;
import com.example.lancerpick.chat.repository.OrderChatThreadQueries;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OrderChatThreadQueryService {
  private final OrderChatThreadQueries queries;

  public OrderChatThreadQueryService(OrderChatThreadQueries queries) {
    this.queries = queries;
  }

  public PageResponse<ChatThreadResponse> list(long member, String q, int page, int size) {
    String keyword = q == null ? null : q.strip();
    if (keyword != null && keyword.isEmpty()) keyword = null;
    if (page < 0
        || size < 1
        || size > 100
        || (keyword != null && keyword.codePointCount(0, keyword.length()) > 100)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "대화 목록 조회 조건을 확인해 주세요.");
    }
    boolean number = keyword != null && keyword.matches("[0-9]+");
    var id = number ? positiveId(keyword) : OptionalLong.empty();
    var condition =
        new OrderChatThreadQueries.Condition(
            member, number ? null : keyword, number, id.isPresent() ? id.getAsLong() : null);
    long count = queries.count(condition);
    long offset = (long) page * size;
    var items =
        offset >= count
            ? List.<ChatThreadResponse>of()
            : queries.find(condition, size, offset).stream()
                .map(row ->
                    new ChatThreadResponse(
                        Long.toString(row.orderId()),
                        row.productTitle(),
                        new ChatThreadResponse.Counterparty(
                            Long.toString(row.counterpartyId()), row.counterpartyName()),
                        row.buyerId() == member ? "BUYER" : "SELLER",
                        row.status().name(),
                        OrderChatService.canSend(row.status()),
                        preview(row.lastContent()),
                        row.lastSenderId() == null ? null : row.lastSenderId().toString(),
                        at(row.lastAt()),
                        at(row.activityAt())))
                .toList();
    return new PageResponse<>(
        items, page, size, count, Math.toIntExact(count / size + (count % size == 0 ? 0 : 1)));
  }

  private static String preview(String text) {
    if (text == null || text.codePointCount(0, text.length()) <= 140) return text;
    return text.substring(0, text.offsetByCodePoints(0, 140)) + "…";
  }

  private static OptionalLong positiveId(String digits) {
    try {
      long value = Long.parseLong(digits);
      return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
    } catch (NumberFormatException exception) {
      return OptionalLong.empty();
    }
  }

  private static OffsetDateTime at(LocalDateTime time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }
}
