package com.today.telemetry;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductEventMapper {
  int insert(ProductEventEntity event);
}
