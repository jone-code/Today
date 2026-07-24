package com.today;

import com.today.aigateway.AiProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.today")
@EnableScheduling
@EnableConfigurationProperties(AiProperties.class)
public class TodayApplication {

  public static void main(String[] args) {
    SpringApplication.run(TodayApplication.class, args);
  }
}
