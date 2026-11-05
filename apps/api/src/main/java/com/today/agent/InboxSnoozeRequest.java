package com.today.agent;

import java.time.Instant;

public record InboxSnoozeRequest(String date, Instant until) {}
