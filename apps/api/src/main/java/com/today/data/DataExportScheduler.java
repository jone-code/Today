package com.today.data;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataExportScheduler {
  private final DataRequestService service;
  public DataExportScheduler(DataRequestService service) { this.service = service; }

  @Scheduled(fixedDelayString = "${today.data.export-poll-ms:5000}")
  public void tick() { service.cleanupExpiredExports(); service.processNextExport(); service.processNextDeletion(); }
}
