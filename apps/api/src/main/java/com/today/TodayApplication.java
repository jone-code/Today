package com.today;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.today")
public class TodayApplication {

  public static void main(String[] args) {
    SpringApplication.run(TodayApplication.class, args);
  }
}
