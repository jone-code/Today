package com.today.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

import com.today.media.LocalMediaStorage;
import com.today.persistence.AgentMediaEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentMediaCleanupSchedulerTest {
  @Test
  void onlyPurgesMetadataAfterFileDeletionSucceeds() {
    AgentMediaMapper mapper = mock(AgentMediaMapper.class);
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    AgentMediaEntity old = new AgentMediaEntity();
    old.setId("a1"); old.setUserId("u1"); old.setRelativePath("agent/u1/old.jpg");
    when(mapper.listReplaced(100)).thenReturn(List.of(old));
    var scheduler = new AgentMediaCleanupScheduler(mapper, storage);

    scheduler.purgeReplaced();
    verify(mapper, never()).markPurged(any(), any());
    verify(mapper).deferCleanup("a1", "u1");

    when(storage.deleteIfPresent("agent/u1/old.jpg")).thenReturn(true);
    scheduler.purgeReplaced();
    verify(mapper).markPurged("a1", "u1");
  }

  @Test
  void exceptionFromOneFileDoesNotBlockRemainingFiles() {
    AgentMediaMapper mapper = mock(AgentMediaMapper.class);
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    AgentMediaEntity first = new AgentMediaEntity();
    first.setId("a1"); first.setUserId("u1"); first.setRelativePath("agent/u1/first.jpg");
    AgentMediaEntity second = new AgentMediaEntity();
    second.setId("a2"); second.setUserId("u1"); second.setRelativePath("agent/u1/second.jpg");
    when(mapper.listReplaced(100)).thenReturn(List.of(first, second));
    doThrow(new IllegalStateException("disk error"))
        .when(storage).deleteIfPresent("agent/u1/first.jpg");
    when(storage.deleteIfPresent("agent/u1/second.jpg")).thenReturn(true);

    new AgentMediaCleanupScheduler(mapper, storage).purgeReplaced();

    verify(mapper).deferCleanup("a1", "u1");
    verify(mapper).markPurged("a2", "u1");
  }
}
