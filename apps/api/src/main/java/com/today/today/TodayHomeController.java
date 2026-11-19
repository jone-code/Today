package com.today.today;

import com.today.agent.FollowupService;
import com.today.agent.ProposalService;
import com.today.checkin.CheckinAiJobDto;
import com.today.checkin.CheckinAiJobService;
import com.today.checkin.CheckinDto;
import com.today.checkin.CheckinMemoryCandidateService;
import com.today.checkin.CheckinService;
import com.today.common.EntityMapper;
import com.today.punch.PunchHabitListDto;
import com.today.punch.PunchService;
import com.today.reminder.ReminderListDto;
import com.today.reminder.ReminderService;
import com.today.summary.DaySummaryDto;
import com.today.summary.SummaryService;
import com.today.todo.TodoListDto;
import com.today.todo.TodoService;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TodayHomeController {
  private final CheckinService checkins;
  private final CheckinAiJobService aiJobs;
  private final SummaryService summaries;
  private final CheckinMemoryCandidateService memoryCandidates;
  private final ProposalService proposals;
  private final FollowupService followups;
  private final TodoService todos;
  private final PunchService punches;
  private final ReminderService reminders;

  public TodayHomeController(
      CheckinService checkins,
      CheckinAiJobService aiJobs,
      SummaryService summaries,
      CheckinMemoryCandidateService memoryCandidates,
      ProposalService proposals,
      FollowupService followups,
      TodoService todos,
      PunchService punches,
      ReminderService reminders) {
    this.checkins = checkins;
    this.aiJobs = aiJobs;
    this.summaries = summaries;
    this.memoryCandidates = memoryCandidates;
    this.proposals = proposals;
    this.followups = followups;
    this.todos = todos;
    this.punches = punches;
    this.reminders = reminders;
  }

  @GetMapping("/v1/today/home")
  public TodayHomeDto home() {
    CheckinDto checkin = checkins.getToday();
    CheckinAiJobDto job = checkin == null ? null : aiJobs.findByCheckinId(checkin.id());
    DaySummaryDto summary = null;
    int pendingMemoryCount = 0;
    if (checkin != null) {
      try {
        summary = summaries.getByDate(checkin.date());
      } catch (ResponseStatusException e) {
        if (e.getStatusCode() != HttpStatus.NOT_FOUND) throw e;
      }
      pendingMemoryCount = (int) memoryCandidates.list(checkin.id()).stream()
          .filter(candidate -> "pending".equals(candidate.status())).count();
    }
    TodoListDto openTodos = todos.list("open");
    PunchHabitListDto habits = punches.listHabits(LocalDate.now().toString());
    ReminderListDto reminderList = reminders.listMine();
    return new TodayHomeDto(
        checkin,
        job,
        summary,
        pendingMemoryCount,
        proposals.pending().size(),
        followups.unreadCount(),
        openTodos.items().size(),
        habits.items().size(),
        (int) habits.items().stream().filter(item -> item.punchedToday()).count(),
        (int) reminderList.items().stream().filter(item -> item.enabled()).count());
  }
}
