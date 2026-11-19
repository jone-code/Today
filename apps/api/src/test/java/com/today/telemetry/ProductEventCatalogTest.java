package com.today.telemetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductEventCatalogTest {
  @Test
  void acceptsDocumentedEventsAndRejectsUnknownNames() {
    assertTrue(ProductEventCatalog.isKnown("auth_completed"));
    assertTrue(ProductEventCatalog.isKnown(" summary_viewed "));
    assertTrue(ProductEventCatalog.isKnown("voice_transcription_failed"));
    assertTrue(ProductEventCatalog.isKnown("voice_transcription_retried"));
    assertTrue(ProductEventCatalog.isKnown("voice_recording_failed"));
    assertTrue(ProductEventCatalog.isKnown("memory_context_scope_updated"));
    assertFalse(ProductEventCatalog.isKnown("summary_viewd"));
    assertFalse(ProductEventCatalog.isKnown(null));
  }
}
