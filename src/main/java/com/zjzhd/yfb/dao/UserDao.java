package com.zjzhd.yfb.dao;

import java.util.List;

import com.zjzhd.yfb.model.User;

public interface UserDao {

	public int saveUser(User u);

	public List<User> findAll();
	
	public User findUserByName(String username);

}
