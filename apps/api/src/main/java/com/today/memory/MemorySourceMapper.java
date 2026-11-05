package com.today.memory;

import com.today.persistence.MemorySourceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemorySourceMapper {
  int insert(MemorySourceEntity entity);
  List<MemorySourceEntity> listByMemory(@Param("memoryId") String memoryId, @Param("userId") String userId);
  List<MemoryOrigin> listDisplayableOrigins(@Param("memoryId") String memoryId, @Param("userId") String userId);
  int deactivateBySource(@Param("userId") String userId, @Param("sourceType") String sourceType, @Param("sourceId") String sourceId);
  List<MemorySourceEntity> listBySource(@Param("userId") String userId, @Param("sourceType") String sourceType, @Param("sourceId") String sourceId);
}
