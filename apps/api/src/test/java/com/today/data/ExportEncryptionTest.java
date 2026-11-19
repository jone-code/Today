package com.today.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExportEncryptionTest {
  @Test void encryptedExportAuthenticatesCiphertextAndRequiresKey() throws Exception {
    var encryption = new ExportEncryption("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    var output = new ByteArrayOutputStream();
    try (var stream = encryption.encrypt(output)) { stream.write("private journal".getBytes()); }
    byte[] ciphertext = output.toByteArray();
    assertFalse(Arrays.equals("private journal".getBytes(), ciphertext));
    try (var stream = encryption.decrypt(new ByteArrayInputStream(ciphertext))) {
      assertEquals("private journal", new String(stream.readAllBytes()));
    }
    ciphertext[ciphertext.length - 1] ^= 1;
    assertThrows(java.io.IOException.class, () -> {
      try (var stream = encryption.decrypt(new ByteArrayInputStream(ciphertext))) { stream.readAllBytes(); }
    });
    assertThrows(Exception.class, () -> new ExportEncryption("").encrypt(new ByteArrayOutputStream()));
  }
}
