package com.today.identity;

import com.today.persistence.UserEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthUserDetailsService implements UserDetailsService {

  private final UserMapper userMapper;

  public AuthUserDetailsService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserEntity user = userMapper.findByEmail(username.trim().toLowerCase());
    if (user == null) {
      throw new UsernameNotFoundException("user not found");
    }
    return new AuthUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName());
  }

  public AuthUserPrincipal loadById(String userId) {
    UserEntity user = userMapper.findById(userId);
    if (user == null) {
      throw new UsernameNotFoundException("user not found");
    }
    return new AuthUserPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName());
  }
}
