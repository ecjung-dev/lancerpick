package com.example.lancerpick.contract.document;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Private local adapter; keys are server-generated UUIDs, never a client pathname. */
@Component
public class PrivateContractStorage {
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private final Path root;

  public PrivateContractStorage(
      @Value("${contracts.storage-root:${user.dir}/.local-data/contracts}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public String store(String generationKey, byte[] bytes) throws IOException {
    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid PDF size");
    String key = java.util.UUID.fromString(generationKey).toString() + ".pdf";
    Path target = resolve(key);
    checkAncestors();
    Files.createDirectories(root);
    checkDirectory();
    Path temporary = Files.createTempFile(root, ".contract-", ".tmp");
    try {
      Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
      // No overwrite; an attempt key belongs to exactly one generation claim.
      Files.move(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return key;
  }

  public byte[] read(String key, long expectedSize, byte[] expectedHash) throws IOException {
    Path file = resolve(key);
    checkDirectory();
    if (Files.isSymbolicLink(file)
        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        || expectedSize <= 0
        || expectedSize > MAX_BYTES
        || Files.size(file) != expectedSize) throw new IOException("Invalid stored document");
    byte[] bytes;
    try (var channel =
            Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        var input = java.nio.channels.Channels.newInputStream(channel)) {
      bytes = input.readNBytes(MAX_BYTES + 1);
    }
    if (bytes.length != expectedSize || !MessageDigest.isEqual(hash(bytes), expectedHash))
      throw new IOException("Document integrity failure");
    return bytes;
  }

  public void discardObsolete(String key) throws IOException {
    Path path = resolve(key);
    checkDirectory();
    if (Files.isSymbolicLink(path)) throw new IOException("Invalid file");
    Files.deleteIfExists(path);
  }

  private Path resolve(String key) throws IOException {
    if (key == null
        || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf"))
      throw new IOException("Invalid storage key");
    Path p = root.resolve(key).normalize();
    if (!p.getParent().equals(root)) throw new IOException("Invalid storage key");
    return p;
  }

  private void checkDirectory() throws IOException {
    checkAncestors();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Missing storage");
  }

  private void checkAncestors() throws IOException {
    for (Path p = root; p != null; p = p.getParent()) {
      if (Files.isSymbolicLink(p)) throw new IOException("Symbolic directory not allowed");
    }
  }

  public static byte[] hash(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
