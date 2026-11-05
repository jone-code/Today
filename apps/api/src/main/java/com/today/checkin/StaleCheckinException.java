package com.today.checkin;

public class StaleCheckinException extends RuntimeException {
  public StaleCheckinException() {
    super("checkin changed while AI processing");
  }
}
