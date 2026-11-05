package com.example.lancerpick.contract;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.contract.document.*;
import com.example.lancerpick.contract.dto.ContractResponse;
import com.example.lancerpick.contract.service.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ContractDocumentRecoveryTests {
  @Test
  void staleResultDoesNotDeleteFileReferencedByAnOlderVersion() throws Exception {
    var tx = mock(ContractDocumentTransactions.class);
    var renderer = mock(ContractPdfRenderer.class);
    var storage = mock(PrivateContractStorage.class);
    var service = new ContractDocumentService(tx, renderer, storage);
    String key = UUID.randomUUID().toString();
    var job = new ContractDocumentTransactions.Job(1, 2, 3, 1, key, "{}");
    var response = new ContractResponse("4", 2, "DRAFT", "", "{}", "hash", null, List.of());
    when(tx.claim(1, 2, 1)).thenReturn(new ContractDocumentTransactions.Claim(response, job));
    byte[] bytes = "%PDF fixture".getBytes();
    when(renderer.render(3, 1, "{}")).thenReturn(bytes);
    when(storage.store(key, bytes)).thenReturn(key + ".pdf");
    when(tx.complete(eq(job), eq(key + ".pdf"), anyLong(), any()))
        .thenReturn(new ContractDocumentTransactions.Applied(false, false, response));
    assertThatThrownBy(() -> service.generate(1, 2, 1))
        .isInstanceOf(com.example.lancerpick.common.exception.BusinessException.class);
    verify(storage, never()).discardObsolete(anyString());
  }

  @Test
  void uncertainCommitRetainsFileInsteadOfDeletingPossiblyCommittedDocument() throws Exception {
    var tx = mock(ContractDocumentTransactions.class);
    var renderer = mock(ContractPdfRenderer.class);
    var storage = mock(PrivateContractStorage.class);
    var service = new ContractDocumentService(tx, renderer, storage);
    String key = UUID.randomUUID().toString();
    var job = new ContractDocumentTransactions.Job(1, 2, 3, 1, key, "{}");
    var response = new ContractResponse("3", 1, "GENERATING", "", "{}", "hash", null, List.of());
    when(tx.claim(1, 2, 1)).thenReturn(new ContractDocumentTransactions.Claim(response, job));
    byte[] bytes = "%PDF fixture".getBytes();
    when(renderer.render(3, 1, "{}")).thenReturn(bytes);
    when(storage.store(key, bytes)).thenReturn(key + ".pdf");
    when(tx.complete(eq(job), eq(key + ".pdf"), anyLong(), any()))
        .thenThrow(new DataAccessResourceFailureException("unknown commit"));
    assertThatThrownBy(() -> service.generate(1, 2, 1))
        .isInstanceOf(DataAccessResourceFailureException.class);
    verify(storage, never()).discardObsolete(anyString());
    verify(tx, never()).fail(any());
  }
}
