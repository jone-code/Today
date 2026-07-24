package com.today.proactive;

import com.today.common.PromptSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class PromptFingerprints {

  private PromptFingerprints() {}

  static String of(ProactivePromptDto prompt) {
    String related = prompt.relatedDate() == null ? "" : prompt.relatedDate();
    String source = prompt.source() == null ? "gentle" : prompt.source().name();
    String base = source + "|" + related + "|" + normalizeText(prompt.text());
    return sha256(base).substring(0, 32);
  }

  static String ofFollowupTopic(String topic, String relatedDate) {
    return sha256("followup|" + relatedDate + "|" + topic).substring(0, 32);
  }

  static String normalizeText(String text) {
    if (text == null) return "";
    return text.trim().replaceAll("\\s+", " ");
  }

  private static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  static int sourceRank(PromptSource source) {
    if (source == null) return 99;
    return switch (source) {
      case followup -> 0;
      case pattern -> 1;
      case memory -> 2;
      case gentle -> 3;
    };
  }
}
