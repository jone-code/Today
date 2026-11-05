package com.today.memory;

import com.today.persistence.MemoryEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {

  MemoryEntity findById(@Param("id") String id);

  MemoryEntity findVisibleById(@Param("id") String id, @Param("userId") String userId);

  MemoryEntity findConfirmedById(@Param("id") String id, @Param("userId") String userId);

  List<MemoryEntity> listByUserId(
      @Param("userId") String userId, @Param("includeArchived") boolean includeArchived);

  List<MemoryEntity> listConfirmedForAgent(
      @Param("userId") String userId, @Param("limit") int limit);

  int insert(MemoryEntity entity);

  int update(MemoryEntity entity);

  int updateEmbeddingIfConfirmed(@Param("id") String id, @Param("userId") String userId,
      @Param("expectedText") String expectedText, @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
      @Param("embeddingJson") String embeddingJson, @Param("updatedAt") Instant updatedAt);

  int deleteById(@Param("id") String id, @Param("userId") String userId);

  int countWithEmbedding();

  int countAll();

  List<MemoryEntity> listPage(@Param("limit") int limit, @Param("offset") int offset);

  List<MemoryEntity> listPageByUserId(
      @Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);
}
