package com.today.review;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.today.checkin.CheckinMapper;
import com.today.identity.IdentityService;
import com.today.persistence.CheckinEntity;
import com.today.persistence.TodoEntity;
import com.today.persistence.WeeklyReviewEntity;
import com.today.todo.TodoMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WeeklyReviewServiceTest {
  @Test void retryRejectsSucceededAndRunningReviewsBeforeReadingInputs() {
    var reviews = mock(WeeklyReviewMapper.class);
    var checkins = mock(CheckinMapper.class);
    var todos = mock(TodoMapper.class);
    var identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    var week = LocalDate.of(2026, 11, 16);

    for (String status : new String[] {"succeeded", "running"}) {
      var entity = new WeeklyReviewEntity();
      entity.setStatus(status); entity.setWeekStart(week);
      if ("running".equals(status)) entity.setLockedAt(Instant.now());
      when(reviews.find("u1", week)).thenReturn(entity);

      assertThrows(ResponseStatusException.class,
          () -> new WeeklyReviewService(reviews, checkins, todos, identity)
              .retry("2026-11-16"));
      verifyNoInteractions(checkins, todos);
      clearInvocations(reviews, checkins, todos);
    }
  }

  @Test void staleRunningReviewCanBeRecovered() {
    var reviews = mock(WeeklyReviewMapper.class);
    var checkins = mock(CheckinMapper.class);
    var todos = mock(TodoMapper.class);
    var identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    var entity = new WeeklyReviewEntity();
    entity.setId("r1"); entity.setUserId("u1"); entity.setWeekStart(LocalDate.of(2026, 11, 16));
    entity.setStatus("running"); entity.setLockedAt(Instant.now().minusSeconds(901)); entity.setAttempts(1);
    when(reviews.find("u1", entity.getWeekStart())).thenReturn(entity);
    when(checkins.listByUserIdAndRange(eq("u1"), any(), any())).thenReturn(List.of());
    when(todos.listCompletedByUserIdAndRange(eq("u1"), any(), any())).thenReturn(List.of());

    var result = new WeeklyReviewService(reviews, checkins, todos, identity).retry("2026-11-16");

    org.junit.jupiter.api.Assertions.assertEquals("succeeded", result.status());
    verify(reviews, times(2)).update(entity);
    verify(checkins).listByUserIdAndRange(eq("u1"), any(), any());
    verify(todos).listCompletedByUserIdAndRange(eq("u1"), any(), any());
  }

  @Test void firstGenerationReturnsSummaryAndIsNotRetryable() {
    var reviews = mock(WeeklyReviewMapper.class);
    var checkins = mock(CheckinMapper.class);
    var todos = mock(TodoMapper.class);
    var identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(reviews.find(eq("u1"), any())).thenReturn(null);
    var checkin = new CheckinEntity(); checkin.setRawText("  一周完成了重要工作  ");
    var todo = new TodoEntity(); todo.setTitle("完成复盘");
    when(checkins.listByUserIdAndRange(eq("u1"), any(), any())).thenReturn(List.of(checkin));
    when(todos.listCompletedByUserIdAndRange(eq("u1"), any(), any())).thenReturn(List.of(todo));

    var result = new WeeklyReviewService(reviews, checkins, todos, identity)
        .getOrGenerate("2026-11-18");

    org.junit.jupiter.api.Assertions.assertEquals("succeeded", result.status());
    org.junit.jupiter.api.Assertions.assertEquals(1, result.daysRecorded());
    org.junit.jupiter.api.Assertions.assertEquals(1, result.completedTodos());
    org.junit.jupiter.api.Assertions.assertFalse(result.retryable());
    org.junit.jupiter.api.Assertions.assertEquals("一周完成了重要工作", result.highlights().get(0));
    verify(reviews).insert(any(WeeklyReviewEntity.class));
    verify(reviews, times(2)).update(any(WeeklyReviewEntity.class));
  }

  @Test void generationFailurePersistsRetryableFailedReview() {
    var reviews = mock(WeeklyReviewMapper.class);
    var checkins = mock(CheckinMapper.class);
    var todos = mock(TodoMapper.class);
    var identity = mock(IdentityService.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(reviews.find(eq("u1"), any())).thenReturn(null);
    when(checkins.listByUserIdAndRange(eq("u1"), any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));

    var service = new WeeklyReviewService(reviews, checkins, todos, identity);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.getOrGenerate("2026-11-18"));

    var failed = org.mockito.ArgumentCaptor.forClass(WeeklyReviewEntity.class);
    verify(reviews, times(2)).update(failed.capture());
    var entity = failed.getAllValues().get(1);
    org.junit.jupiter.api.Assertions.assertEquals("failed", entity.getStatus());
    org.junit.jupiter.api.Assertions.assertEquals("database unavailable", entity.getLastError());
  }
}
