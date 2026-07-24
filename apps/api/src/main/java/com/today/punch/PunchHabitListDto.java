package com.today.punch;

import java.util.List;

public record PunchHabitListDto(List<PunchHabitDto> items, String date) {}
