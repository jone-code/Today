package com.today.todo;

import com.today.persistence.TodoEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoMapper {

  TodoEntity findById(@Param("id") String id);

  List<TodoEntity> listByUserId(
      @Param("userId") String userId, @Param("status") String status);

  int insert(TodoEntity entity);

  int update(TodoEntity entity);

  int deleteByIdAndUserId(@Param("id") String id, @Param("userId") String userId);
}
