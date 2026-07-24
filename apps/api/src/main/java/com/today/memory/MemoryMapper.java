package com.today.memory;

import com.today.persistence.MemoryEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {

  MemoryEntity findById(@Param("id") String id);

  List<MemoryEntity> listByUserId(
      @Param("userId") String userId, @Param("includeArchived") boolean includeArchived);

  int insert(MemoryEntity entity);

  int update(MemoryEntity entity);

  int deleteById(@Param("id") String id, @Param("userId") String userId);
}
