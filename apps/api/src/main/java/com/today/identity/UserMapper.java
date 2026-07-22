package com.today.identity;

import com.today.persistence.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

  UserEntity findById(@Param("id") String id);

  UserEntity findByEmail(@Param("email") String email);

  int insert(UserEntity entity);
}
