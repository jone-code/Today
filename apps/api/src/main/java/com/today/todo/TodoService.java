package com.today.todo;

import com.today.common.EntityMapper;
import com.today.identity.IdentityService;
import com.today.persistence.TodoEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TodoService {

  private final TodoMapper todoMapper;
  private final IdentityService identity;

  public TodoService(TodoMapper todoMapper, IdentityService identity) {
    this.todoMapper = todoMapper;
    this.identity = identity;
  }

  public TodoListDto list(String status) {
    String userId = identity.getCurrentUserId();
    String filter =
        status == null || status.isBlank() || "all".equalsIgnoreCase(status) ? null : status;
    if (filter != null && !"open".equals(filter) && !"done".equals(filter)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
    }
    List<TodoDto> items =
        todoMapper.listByUserId(userId, filter).stream().map(this::toDto).toList();
    return new TodoListDto(items);
  }

  @Transactional
  public TodoDto create(TodoCreateRequest input) {
    String userId = identity.getCurrentUserId();
    Instant now = EntityMapper.now();
    TodoEntity entity = new TodoEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setTitle(input.title().trim());
    entity.setNote(blankToNull(input.note()));
    entity.setStatus("open");
    entity.setDueDate(parseDue(input.dueDate()));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setCompletedAt(null);
    todoMapper.insert(entity);
    return toDto(entity);
  }

  @Transactional
  public TodoDto update(String id, TodoUpdateRequest input) {
    String userId = identity.getCurrentUserId();
    TodoEntity existing = requireOwned(id, userId);

    if (input.title() != null && !input.title().isBlank()) {
      existing.setTitle(input.title().trim());
    }
    if (input.note() != null) {
      existing.setNote(blankToNull(input.note()));
    }
    if (Boolean.TRUE.equals(input.clearDueDate())) {
      existing.setDueDate(null);
    } else if (input.dueDate() != null) {
      existing.setDueDate(parseDue(input.dueDate()));
    }
    if (input.status() != null) {
      applyStatus(existing, input.status());
    }
    existing.setUpdatedAt(EntityMapper.now());
    todoMapper.update(existing);
    return toDto(existing);
  }

  @Transactional
  public TodoDto toggle(String id) {
    String userId = identity.getCurrentUserId();
    TodoEntity existing = requireOwned(id, userId);
    applyStatus(existing, "done".equals(existing.getStatus()) ? "open" : "done");
    existing.setUpdatedAt(EntityMapper.now());
    todoMapper.update(existing);
    return toDto(existing);
  }

  @Transactional
  public void delete(String id) {
    String userId = identity.getCurrentUserId();
    int deleted = todoMapper.deleteByIdAndUserId(id, userId);
    if (deleted == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "todo not found");
    }
  }

  private void applyStatus(TodoEntity entity, String status) {
    if ("done".equals(status)) {
      entity.setStatus("done");
      if (entity.getCompletedAt() == null) {
        entity.setCompletedAt(EntityMapper.now());
      }
    } else {
      entity.setStatus("open");
      entity.setCompletedAt(null);
    }
  }

  private TodoEntity requireOwned(String id, String userId) {
    TodoEntity existing = todoMapper.findById(id);
    if (existing == null || !existing.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "todo not found");
    }
    return existing;
  }

  private LocalDate parseDue(String dueDate) {
    if (dueDate == null || dueDate.isBlank()) {
      return null;
    }
    return LocalDate.parse(dueDate);
  }

  private String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private TodoDto toDto(TodoEntity entity) {
    return new TodoDto(
        entity.getId(),
        entity.getUserId(),
        entity.getTitle(),
        entity.getNote(),
        entity.getStatus(),
        entity.getDueDate() == null ? null : entity.getDueDate().toString(),
        entity.getCreatedAt().toString(),
        entity.getUpdatedAt().toString(),
        entity.getCompletedAt() == null ? null : entity.getCompletedAt().toString());
  }
}
