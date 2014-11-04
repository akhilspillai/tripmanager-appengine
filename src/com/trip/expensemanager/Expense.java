package com.trip.expensemanager;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Expense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String name;
	private String description;
	private Long userId;
	private Long tripId;
	private Long changerId;
	private String amount;
	private String currency;
	private Date creationDate;
	private Long[] expenseUserIds;
	
	public Long getId(){
		return this.id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getAmount() {
		return amount;
	}

	public void setAmount(String amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Long getTripId() {
		return tripId;
	}

	public void setTripId(Long tripId) {
		this.tripId = tripId;
	}

	public Long getChangerId() {
		return changerId;
	}

	public void setChangerId(Long changerId) {
		this.changerId = changerId;
	}

	public Long[] getExpenseUserIds() {
		return expenseUserIds;
	}

	public void setExpenseUserIds(Long[] expenseUserIds) {
		this.expenseUserIds = expenseUserIds;
	}	
	
}
