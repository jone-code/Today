package com.today.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.today.identity.IdentityService;
import com.today.persistence.TodoEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TodoServiceTest {
  private final TodoMapper mapper = mock(TodoMapper.class);
  private final IdentityService identity = mock(IdentityService.class);
  private final TodoService service = new TodoService(mapper, identity);

  @Test
  void rejectsOtherUsersTodoBeforeWriting() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TodoDto before = snapshot("old", "2026-11-03T09:00:00Z");

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> service.updateFromAgent("todo-1", before, change()));
    assertEquals(404, failure.getStatusCode().value());
    verify(mapper, never()).update(any());
  }

  @Test
  void rejectsChangedTodoEvenWhenTimestampMatches() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TodoEntity changed = entity("new", "2026-11-03T09:00:00Z");
    when(mapper.lockByIdAndUser("todo-1", "u1")).thenReturn(changed);

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> service.updateFromAgent("todo-1", snapshot("old", "2026-11-03T09:00:00Z"), change()));
    assertEquals(409, failure.getStatusCode().value());
    verify(mapper, never()).update(any());
  }

  @Test
  void confirmedChangeUsesExistingTodoUpdate() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TodoEntity existing = entity("old", "2026-11-03T09:00:00Z");
    when(mapper.lockByIdAndUser("todo-1", "u1")).thenReturn(existing);
    when(mapper.findById("todo-1")).thenReturn(existing);

    TodoDto saved = service.updateFromAgent("todo-1", snapshot("old", "2026-11-03T09:00:00Z"), change());
    assertEquals("new", saved.title());
    assertEquals("todo-1", saved.id());
    verify(mapper).update(existing);
  }

  @Test
  void settingTheSameDesiredStatusTwiceDoesNotUndoCompletion() {
    when(identity.getCurrentUserId()).thenReturn("u1");
    TodoEntity existing = entity("test", "2026-11-03T09:00:00Z");
    when(mapper.findById("todo-1")).thenReturn(existing);
    TodoUpdateRequest done = new TodoUpdateRequest(null, null, null, "done", null);

    TodoDto first = service.update("todo-1", done);
    TodoDto replay = service.update("todo-1", done);

    assertEquals("done", replay.status());
    assertEquals(first.completedAt(), replay.completedAt());
  }

  private static TodoUpdateRequest change() {
    return new TodoUpdateRequest("new", null, null, null, false);
  }

  private static TodoDto snapshot(String title, String updatedAt) {
    return new TodoDto("todo-1", "u1", title, null, "open", null,
        "2026-11-02T09:00:00Z", updatedAt, null);
  }

  private static TodoEntity entity(String title, String updatedAt) {
    TodoEntity entity = new TodoEntity();
    entity.setId("todo-1");
    entity.setUserId("u1");
    entity.setTitle(title);
    entity.setStatus("open");
    entity.setCreatedAt(Instant.parse("2026-11-02T09:00:00Z"));
    entity.setUpdatedAt(Instant.parse(updatedAt));
    return entity;
  }
}
