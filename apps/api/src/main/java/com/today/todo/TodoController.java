package com.today.todo;

import com.today.common.CreateIdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/todos")
public class TodoController {

  private final TodoService todos;
  private final CreateIdempotencyService creates;

  public TodoController(TodoService todos, CreateIdempotencyService creates) {
    this.todos = todos;
    this.creates = creates;
  }

  @GetMapping
  public TodoListDto list(@RequestParam(required = false) String status) {
    return todos.list(status);
  }

  @PostMapping
  public TodoDto create(@Valid @RequestBody TodoCreateRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return creates.create("todo", key, body, TodoDto.class, () -> todos.create(body));
  }

  @PutMapping("/{id}")
  public TodoDto update(@PathVariable String id, @Valid @RequestBody TodoUpdateRequest body) {
    return todos.update(id, body);
  }

  @PostMapping("/{id}/toggle")
  public TodoDto toggle(@PathVariable String id) {
    return todos.toggle(id);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    todos.delete(id);
  }
}
