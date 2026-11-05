package com.example.lancerpick.contract;

import static org.assertj.core.api.Assertions.*;

import com.example.lancerpick.contract.document.PrivateContractStorage;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateContractStorageTests {
  @TempDir Path directory;

  @Test
  void roundtripChecksHashAndNeverOverwritesAnAttempt() throws Exception {
    var storage = new PrivateContractStorage(directory.toString());
    byte[] bytes = "%PDF fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String attempt = UUID.randomUUID().toString();
    String key = storage.store(attempt, bytes);
    assertThat(storage.read(key, bytes.length, PrivateContractStorage.hash(bytes)))
        .isEqualTo(bytes);
    assertThatThrownBy(() -> storage.store(attempt, "other".getBytes()))
        .isInstanceOf(FileAlreadyExistsException.class);
    assertThat(Files.readAllBytes(directory.resolve(key))).isEqualTo(bytes);
    try (var files = Files.list(directory)) {
      assertThat(files.count()).isEqualTo(1);
    }
    storage.discardObsolete(key);
    assertThat(Files.exists(directory.resolve(key))).isFalse();
  }

  @Test
  void tamperedWrongSizeMissingAndTraversalFilesAreRejected() throws Exception {
    var storage = new PrivateContractStorage(directory.toString());
    byte[] bytes = "original".getBytes();
    String key = storage.store(UUID.randomUUID().toString(), bytes);
    Files.write(directory.resolve(key), "modified".getBytes());
    assertThatThrownBy(() -> storage.read(key, bytes.length, PrivateContractStorage.hash(bytes)))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.read(key, 2, PrivateContractStorage.hash(bytes)))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.read(UUID.randomUUID() + ".pdf", 8, new byte[32]))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.read("../secrets.pdf", 8, new byte[32]))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.discardObsolete("../secrets.pdf"))
        .isInstanceOf(IOException.class);
  }

  @Test
  void emptyAndOversizedFilesAreRejected() {
    var storage = new PrivateContractStorage(directory.toString());
    assertThatThrownBy(() -> storage.store(UUID.randomUUID().toString(), new byte[0]))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() ->
            storage.store(
                UUID.randomUUID().toString(), new byte[PrivateContractStorage.MAX_BYTES + 1]))
        .isInstanceOf(IOException.class);
  }
}
