package com.trip.expensemanager;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class LogIn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String username;
	private String password;
	private List<Long> tripIDs;
	private List<Long> deviceIDs;

	public Long getId() {
		return id;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public List<Long> getTripIDs() {
		return tripIDs;
	}

	public void setTripIDs(List<Long> tripIDs) {
		this.tripIDs = tripIDs;
	}

	public List<Long> getDeviceIDs() {
		return deviceIDs;
	}

	public void setDeviceIDs(List<Long> deviceIDs) {
		this.deviceIDs = deviceIDs;
	}
	
	
}
