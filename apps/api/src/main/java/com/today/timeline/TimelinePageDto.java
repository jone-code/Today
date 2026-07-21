package com.today.timeline;

import java.util.List;

public record TimelinePageDto(List<TimelineItemDto> items, String nextCursor) {}
