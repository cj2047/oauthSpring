package com.zjzhd.yfb.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

@Entity
@Table
public class User {

	private int			id;
	private String	username;
	private String	password;
	private Role		role;

	@Id
	@GeneratedValue
	public int getId(){
		return id;
	}

	public void setId(int id){
		this.id = id;
	}

	@Size(min = 6)
	public String getUsername(){
		return username;
	}

	public void setUsername(String username){
		this.username = username;
	}

	public String getPassword(){
		return password;
	}

	public void setPassword(String password){
		this.password = password;
	}

	@ManyToOne
	public Role getRole(){
		return role;
	}

	public void setRole(Role role){
		this.role = role;
	}

}
