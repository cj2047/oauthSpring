package com.zjzhd.yfb.service;

import java.util.List;

import com.zjzhd.yfb.model.User;

public interface UserService {

	public void saveUsers(List<User> us);

	public List<User> getAllUsers();

	public User getUserByName(String username);

}
