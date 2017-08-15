package com.zjzhd.yfb.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table
public class Role {

	private int			id;
	private String	role;

	@Id
	public int getId(){
		return id;
	}

	public void setId(int id){
		this.id = id;
	}

	public String getRole(){
		return role;
	}

	public void setRole(String role){
		this.role = role;
	}

}
