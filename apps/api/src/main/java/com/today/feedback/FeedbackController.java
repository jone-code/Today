package com.today.feedback;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/feedback")
public class FeedbackController {
  private final FeedbackService feedback;

  public FeedbackController(FeedbackService feedback) {
    this.feedback = feedback;
  }

  @PostMapping
  public FeedbackDto create(@Valid @RequestBody FeedbackCreateRequest body,
      @RequestHeader("Idempotency-Key") String requestId) {
    return feedback.create(body, requestId);
  }

  @GetMapping
  public List<FeedbackDto> list() {
    return feedback.list();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    feedback.delete(id);
  }
}
