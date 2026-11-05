package com.example.lancerpick.notification.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.notification.dto.NotificationResponse;
import com.example.lancerpick.notification.dto.UnreadCountResponse;
import com.example.lancerpick.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notifications;
  private final CurrentMemberId currentMember;

  public NotificationController(NotificationService notifications, CurrentMemberId currentMember) {
    this.notifications = notifications;
    this.currentMember = currentMember;
  }

  @GetMapping
  public ResponseEntity<PageResponse<NotificationResponse>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(notifications.list(currentMember.require(authentication), page, size));
  }

  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> unreadCount(Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(notifications.unreadCount(currentMember.require(authentication)));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<Void> read(Authentication authentication, @PathVariable String id) {
    notifications.markRead(currentMember.require(authentication), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> readAll(Authentication authentication) {
    notifications.markAllRead(currentMember.require(authentication));
    return ResponseEntity.noContent().build();
  }
}
