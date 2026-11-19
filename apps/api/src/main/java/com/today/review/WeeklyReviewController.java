package com.today.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reviews/weekly")
public class WeeklyReviewController {
  private final WeeklyReviewService reviews;
  public WeeklyReviewController(WeeklyReviewService reviews) { this.reviews = reviews; }
  @GetMapping public WeeklyReviewDto get(@RequestParam String week) { return reviews.getOrGenerate(week); }
  @PostMapping("/retry") public WeeklyReviewDto retry(@RequestParam String week) { return reviews.retry(week); }
}
