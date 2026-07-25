package com.today;

import com.today.aigateway.AiProperties;
import com.today.vector.VectorProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Only interfaces annotated with @Mapper — avoids treating VectorStore etc. as MyBatis mappers.
@MapperScan(value = "com.today", annotationClass = Mapper.class)
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, VectorProperties.class})
public class TodayApplication {

  public static void main(String[] args) {
    SpringApplication.run(TodayApplication.class, args);
  }
}
