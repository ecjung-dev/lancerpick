package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.admin.dto.AdminContractPdfResponse.*;
import com.example.lancerpick.admin.service.AdminContractPdfQueryService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.entity.OrderContract;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.order.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class AdminContractPdfQueryServiceTests {
  private final MemberRepository members = mock(MemberRepository.class);
  private final OrderRepository orders = mock(OrderRepository.class);
  private final OrderContractRepository contracts = mock(OrderContractRepository.class);
  private final CancellationRequestRepository cancellations =
      mock(CancellationRequestRepository.class);
  private final Order order = mock(Order.class);
  private final OrderContract contract = mock(OrderContract.class);
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 9, 0, 0);
  private final AdminContractPdfQueryService service =
      new AdminContractPdfQueryService(
          members,
          orders,
          contracts,
          cancellations,
          Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
          Duration.ofMinutes(2));

  @BeforeEach
  void setup() {
    when(members.findRoleById(1L)).thenReturn(Optional.of(MemberRole.ADMIN));
    when(orders.findById(2L)).thenReturn(Optional.of(order));
    when(order.getId()).thenReturn(2L);
    when(order.getPolicyVersion()).thenReturn("CONTRACT_V3");
    when(order.getStatus()).thenReturn(OrderStatus.AWAITING_CONTRACT);
    when(order.getAcceptedAt()).thenReturn(now.minusMinutes(5));
    when(contracts.findByOrderIdAndActiveSlot(2, 1)).thenReturn(Optional.of(contract));
    when(contract.getId()).thenReturn(9007199254740993L);
    when(contract.getVersion()).thenReturn(1);
    when(contract.getActiveSlot()).thenReturn(1);
    when(contract.getStatus()).thenReturn("DRAFT");
    when(contract.getCreatedAt()).thenReturn(now.minusMinutes(5));
    when(contract.getUpdatedAt()).thenReturn(now.minusMinutes(2));
  }

  @ParameterizedTest
  @CsvSource({"DRAFT,INITIAL_GENERATION", "FAILED,RETRY_FAILED", "GENERATING,RETRY_EXPIRED"})
  void generationCandidatesIncludeExactTimeoutBoundary(String status, GenerationKind kind) {
    when(contract.getStatus()).thenReturn(status);
    var result = service.inspect(1, 2, null);
    assertThat(result.generationCandidate().eligible()).isTrue();
    assertThat(result.generationCandidate().kind()).isEqualTo(kind);
    assertThat(result.contractId()).isEqualTo("9007199254740993");
    verify(contracts, never()).save(any());
    verify(orders, never()).save(any());
  }

  @Test
  void runningOrFutureGenerationIsNotOffered() {
    when(contract.getStatus()).thenReturn("GENERATING");
    when(contract.getUpdatedAt()).thenReturn(now.minusMinutes(2).plusNanos(1000));
    assertThat(service.inspect(1, 2, null).generationCandidate().reason())
        .isEqualTo(BlockReason.GENERATION_ACTIVE);
    when(contract.getUpdatedAt()).thenReturn(now.plusSeconds(1));
    assertThat(service.inspect(1, 2, null).generationCandidate().reason())
        .isEqualTo(BlockReason.CLOCK_BEFORE_UPDATE);
  }

  @ParameterizedTest
  @CsvSource({"READY,DOCUMENT_READY", "SIGNED,DOCUMENT_SIGNED", "OTHER,UNSUPPORTED_STATE"})
  void publishedOrUnsupportedDocumentsAreNotRegenerated(String status, BlockReason reason) {
    when(contract.getStatus()).thenReturn(status);
    assertThat(service.inspect(1, 2, null).generationCandidate().reason()).isEqualTo(reason);
  }

  @Test
  void historicalVersionCannotBeRegenerated() {
    when(contracts.findByOrderIdAndVersion(2, 1)).thenReturn(Optional.of(contract));
    when(contract.getActiveSlot()).thenReturn(null);
    assertThat(service.inspect(1, 2, 1).generationCandidate().reason())
        .isEqualTo(BlockReason.NOT_CURRENT_VERSION);
    verify(contracts, never()).findByOrderIdAndActiveSlot(anyLong(), any());
  }

  @Test
  void cancelledPaidOrPendingCancellationOrdersAreBlocked() {
    when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
    assertThat(service.inspect(1, 2, null).generationCandidate().reason())
        .isEqualTo(BlockReason.ORDER_NOT_WRITABLE);
    when(order.getStatus()).thenReturn(OrderStatus.AWAITING_PAYMENT);
    when(order.getPaidAt()).thenReturn(now);
    assertThat(service.inspect(1, 2, null).generationCandidate().reason())
        .isEqualTo(BlockReason.ORDER_NOT_WRITABLE);
    when(order.getPaidAt()).thenReturn(null);
    when(cancellations.findByOrderIdAndStatus(2, "PENDING"))
        .thenReturn(List.of(mock(CancellationRequest.class)));
    assertThat(service.inspect(1, 2, null).generationCandidate().reason())
        .isEqualTo(BlockReason.CANCELLATION_PENDING);
  }

  @Test
  void currentDatabaseRoleIsCheckedBeforeAnyOrderLookup() {
    for (long id : new long[] {0, 9}) {
      assertThatThrownBy(() -> service.inspect(id, 2, null))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.code()).isEqualTo("AUTHENTICATION_REQUIRED"));
    }
    when(members.findRoleById(1L))
        .thenReturn(
            Optional.of(
                Arrays.stream(MemberRole.values())
                    .filter(r -> r != MemberRole.ADMIN)
                    .findFirst()
                    .orElseThrow()));
    assertThatThrownBy(() -> service.inspect(1, 2, null))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    verifyNoInteractions(orders, contracts, cancellations);
  }

  @Test
  void invalidMissingOrLegacyTargetsDoNotReturnContractData() {
    assertThatThrownBy(() -> service.inspect(1, 0, null)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.inspect(1, 2, 0)).isInstanceOf(BusinessException.class);
    when(order.getPolicyVersion()).thenReturn("LEGACY_V1");
    assertThatThrownBy(() -> service.inspect(1, 2, null))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    when(order.getPolicyVersion()).thenReturn("CONTRACT_V3");
    when(contracts.findByOrderIdAndActiveSlot(2, 1)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.inspect(1, 2, null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void responseContainsMetadataOnlyAndDoesNotClaimFileIntegrity() {
    when(contract.getFileStorageKey()).thenReturn("private-location.pdf");
    when(contract.getFileBackend()).thenReturn("LOCAL");
    when(contract.getFileSize()).thenReturn(120L);
    when(contract.getFileHash()).thenReturn(new byte[32]);
    var result = service.inspect(1, 2, null);
    assertThat(result.fileMetadataPresent()).isTrue();
    String json = JsonMapper.builder().build().writeValueAsString(result);
    assertThat(json)
        .doesNotContain(
            "private-location", "fileHash", "generationKey", "canonicalContent", "specialTerms");
    when(contract.getFileHash()).thenReturn(new byte[2]);
    assertThat(service.inspect(1, 2, null).fileMetadataPresent()).isFalse();
  }
}
