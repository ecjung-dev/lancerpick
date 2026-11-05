package com.example.lancerpick.contract.entity;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "order_contracts")
public class OrderContract {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private long orderId;

  @Column(nullable = false, updatable = false)
  private int version;

  @Column(name = "template_version", nullable = false, updatable = false, length = 50)
  private String templateVersion;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "active_slot")
  private Integer activeSlot;

  @Column(name = "special_terms", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String specialTerms;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "canonical_content",
      nullable = false,
      updatable = false,
      columnDefinition = "JSON")
  private String canonicalContent;

  @Column(
      name = "content_hash",
      nullable = false,
      updatable = false,
      columnDefinition = "BINARY(32)")
  private byte[] contentHash;

  @Column(name = "file_storage_key", length = 255)
  private String fileStorageKey;

  @Column(name = "file_backend", length = 20)
  private String fileBackend;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "file_hash", columnDefinition = "BINARY(32)")
  private byte[] fileHash;

  @Column(name = "generation_key", nullable = false, columnDefinition = "CHAR(36)")
  private String generationKey;

  @Column(name = "created_by", nullable = false, updatable = false)
  private long createdBy;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "signed_at")
  private LocalDateTime signedAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected OrderContract() {}

  public static OrderContract draft(
      long orderId,
      int version,
      String templateVersion,
      String terms,
      String canonical,
      long actor,
      LocalDateTime at) {
    if (orderId <= 0
        || version < 1
        || actor <= 0
        || at == null
        || templateVersion == null
        || templateVersion.isBlank()
        || templateVersion.length() > 50
        || terms == null
        || terms.codePointCount(0, terms.length()) > 5000
        || canonical == null
        || canonical.isBlank()) {
      throw new IllegalArgumentException("Invalid contract draft");
    }
    var c = new OrderContract();
    c.orderId = orderId;
    c.version = version;
    c.templateVersion = templateVersion;
    c.specialTerms = terms;
    c.canonicalContent = canonical;
    c.contentHash = hash(canonical);
    c.status = "DRAFT";
    c.activeSlot = 1;
    c.generationKey = UUID.randomUUID().toString();
    c.createdBy = actor;
    c.createdAt = at;
    c.updatedAt = at;
    return c;
  }

  public void supersede(LocalDateTime at) {
    requireMonotonicTime(at);
    if ("SIGNED".equals(status) || "SUPERSEDED".equals(status))
      throw new IllegalStateException("Contract cannot be replaced");
    status = "SUPERSEDED";
    activeSlot = null;
    updatedAt = at;
  }

  public void startGeneration(LocalDateTime at) {
    if (!"DRAFT".equals(status)) throw new IllegalStateException("Not a draft");
    requireMonotonicTime(at);
    status = "GENERATING";
    updatedAt = at;
  }

  public void retryGeneration(LocalDateTime at) {
    if (!"FAILED".equals(status) && !"GENERATING".equals(status))
      throw new IllegalStateException("Generation is not retryable");
    requireMonotonicTime(at);
    generationKey = UUID.randomUUID().toString();
    status = "GENERATING";
    updatedAt = at;
  }

  public boolean failGeneration(String attemptKey, LocalDateTime at) {
    if (!"GENERATING".equals(status) || !generationKey.equals(attemptKey)) return false;
    requireMonotonicTime(at);
    status = "FAILED";
    updatedAt = at;
    return true;
  }

  /** Internal renderer completion, not an HTTP input. Caller must lock order/current contract. */
  public void completeGeneration(
      String requestKey,
      String storageKey,
      String backend,
      long bytes,
      byte[] sha256,
      LocalDateTime at) {
    if (!"GENERATING".equals(status)
        || !generationKey.equals(requestKey)
        || storageKey == null
        || storageKey.isBlank()
        || storageKey.length() > 255
        || backend == null
        || backend.isBlank()
        || backend.length() > 20
        || bytes <= 0
        || sha256 == null
        || sha256.length != 32) {
      throw new IllegalStateException("Invalid renderer completion");
    }
    requireMonotonicTime(at);
    fileStorageKey = storageKey;
    fileBackend = backend;
    fileSize = bytes;
    fileHash = sha256.clone();
    status = "READY";
    updatedAt = at;
  }

  public void signComplete(LocalDateTime at) {
    if (!"READY".equals(status)) throw new IllegalStateException("Not ready");
    requireMonotonicTime(at);
    status = "SIGNED";
    signedAt = at;
    updatedAt = at;
  }

  public boolean matches(byte[] hash) {
    return MessageDigest.isEqual(contentHash, hash);
  }

  private void requireMonotonicTime(LocalDateTime at) {
    if (at == null || at.isBefore(updatedAt)) {
      throw new IllegalArgumentException("Contract event time cannot be null or go backwards");
    }
  }

  public byte[] getContentHash() {
    return contentHash.clone();
  }

  public byte[] getFileHash() {
    return fileHash == null ? null : fileHash.clone();
  }

  public static byte[] hash(String input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
