package com.today.agent;

import com.today.common.EntityMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentScheduler {
  private final AppointmentService appointments;

  public AppointmentScheduler(AppointmentService appointments) { this.appointments = appointments; }

  @Scheduled(fixedDelayString = "${today.agent.appointment-poll-ms:60000}")
  public void tick() { appointments.dispatchDue(EntityMapper.now()); }
}
