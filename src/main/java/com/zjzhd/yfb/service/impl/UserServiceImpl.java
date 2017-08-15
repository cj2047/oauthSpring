package com.zjzhd.yfb.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zjzhd.yfb.dao.UserDao;
import com.zjzhd.yfb.model.User;
import com.zjzhd.yfb.service.UserService;

@Service
@Transactional
public class UserServiceImpl implements UserService {

	@Autowired
	private UserDao userDao;

	public void saveUsers(List<User> us){
		for(User u : us){
			userDao.saveUser(u);
		}

	}

	public List<User> getAllUsers(){
		return userDao.findAll();
	}

	public User getUserByName(String username){
		return userDao.findUserByName(username);
	}
	
	

}
