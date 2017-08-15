package com.zjzhd.yfb.dao.impl;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.zjzhd.yfb.dao.UserDao;
import com.zjzhd.yfb.model.User;

@Repository
public class UserDaoImpl implements UserDao {

	@Autowired
	private SessionFactory sessionFactory;

	public int saveUser(User u){
		return (Integer)sessionFactory.getCurrentSession().save(u);
	}

	public List<User> findAll(){
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(User.class);
		return criteria.addOrder(Order.asc("id")).list();
	}

	public User findUserByName(String username){
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(User.class);
		return (User)criteria.add(Restrictions.eq("username",username)).uniqueResult();
	}

}
