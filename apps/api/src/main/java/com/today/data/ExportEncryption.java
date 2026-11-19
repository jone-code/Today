package com.today.data;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM at rest; the ZIP is decrypted only into the authenticated response. */
@Component
public class ExportEncryption {
  private final String configuredKey;
  public ExportEncryption(@Value("${today.data.export-key:}") String key) { configuredKey = key; }
  private Cipher cipher(int mode, byte[] nonce) throws Exception {
    byte[] key = Base64.getDecoder().decode(configuredKey);
    if (key.length != 32) throw new IllegalStateException("export key must contain 32 bytes");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
    return cipher;
  }
  public OutputStream encrypt(OutputStream output) throws Exception {
    byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
    Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
    output.write(nonce);
    return new CipherOutputStream(output, cipher);
  }
  public InputStream decrypt(InputStream input) throws IOException {
    try {
      byte[] nonce = input.readNBytes(12);
      if (nonce.length != 12) throw new IOException("invalid export package");
      return new CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, nonce));
    } catch (Exception failure) { input.close(); throw new IOException("export unavailable", failure); }
  }
}
