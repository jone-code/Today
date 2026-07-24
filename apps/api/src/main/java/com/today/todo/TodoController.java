package com.today.todo;

import jakarta.validation.Valid;
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

  public TodoController(TodoService todos) {
    this.todos = todos;
  }

  @GetMapping
  public TodoListDto list(@RequestParam(required = false) String status) {
    return todos.list(status);
  }

  @PostMapping
  public TodoDto create(@Valid @RequestBody TodoCreateRequest body) {
    return todos.create(body);
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
