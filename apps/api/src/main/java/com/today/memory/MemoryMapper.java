package com.today.memory;

import com.today.persistence.MemoryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {

  MemoryEntity findById(@Param("id") String id);

  List<MemoryEntity> listByUserId(@Param("userId") String userId);

  int insert(MemoryEntity entity);

  int update(MemoryEntity entity);
}
