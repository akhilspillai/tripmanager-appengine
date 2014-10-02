package com.trip.expensemanager;

import com.trip.expensemanager.EMF;
import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.datanucleus.query.JPACursorHelper;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "expenseendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class ExpenseEndpoint {

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listExpense")
	public CollectionResponse<Expense> listExpense(
			@Nullable @Named("cursor") String cursorString,
			@Nullable @Named("limit") Integer limit,
			@Nullable @Named("tripId") Long tripId) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<Expense> execute = null;
		Query query=null;
		try {
			mgr = getEntityManager();
			if(tripId!=null){
				query = mgr.createQuery("select from Expense as Expense where Expense.tripId=:tripId_fk");
				query.setParameter("tripId_fk", tripId);
			} else{
				query = mgr.createQuery("select from Expense as Expense");
			}
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
			}

			if (limit != null) {
				query.setFirstResult(0);
				query.setMaxResults(limit);
			}

			execute = (List<Expense>) query.getResultList();
			cursor = JPACursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (Expense obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<Expense> builder().setItems(execute)
				.setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getExpense")
	public Expense getExpense(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		Expense expense = null;
		try {
			expense = mgr.find(Expense.class, id);
		} finally {
			mgr.close();
		}
		return expense;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param expense the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertExpense")
	public Expense insertExpense(Expense expense) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(expense);
//			TripEndpoint tripEndpoint=new TripEndpoint();
//			Trip tempTrip=tripEndpoint.getTrip(expense.getTripId());
//			Sender sender = new Sender(MessageEndpoint.API_KEY);
//			List<Long> userIds = tempTrip.getUserIDs();
//			LogIn login=null;
//			LogInEndpoint endpoint = new LogInEndpoint();
//			long userIdTemp=userIds.get(userIds.size()-1);
//			login=endpoint.getLogIn(userIdTemp);
//			for (Long userId:userIds) {
////				login= mgr.find(LogIn.class, userId);
//				if(userId!=userIdTemp){
//					login=endpoint.getLogIn(userId);
//					doSendViaGcm(",EA,"+userIdTemp, sender, login);
//				}
//			}
		} finally {
			mgr.close();
		}
		return expense;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param expense the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateExpense")
	public Expense updateExpense(Expense expense) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsExpense(expense)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.persist(expense);
		} finally {
			mgr.close();
		}
		return expense;
	}

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 */
	@ApiMethod(name = "removeExpense")
	public void removeExpense(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		try {
			Expense expense = mgr.find(Expense.class, id);
			mgr.remove(expense);
		} finally {
			mgr.close();
		}
	}

	private boolean containsExpense(Expense expense) {
		EntityManager mgr = getEntityManager();
		boolean contains = true;
		try {
			Expense item = mgr.find(Expense.class, expense.getId());
			if (item == null) {
				contains = false;
			}
		} finally {
			mgr.close();
		}
		return contains;
	}

	private static EntityManager getEntityManager() {
		return EMF.get().createEntityManager();
	}
	
	private static Result doSendViaGcm(String message, Sender sender, LogIn login) throws IOException {
		// Trim message if needed.
		if (message.length() > 1000) {
			message = message.substring(0, 1000) + "[...]";
		}

		// This message object is a Google Cloud Messaging object, it is NOT 
		// related to the MessageData class
		Message msg = new Message.Builder().addData("message", message).build();
		Result result = sender.send(msg, login.getRegId(),5);
		LogInEndpoint endpoint = new LogInEndpoint();
		if (result.getMessageId() != null) {
			String canonicalRegId = result.getCanonicalRegistrationId();
			if (canonicalRegId != null) {
				endpoint.removeLogIn(login.getId());
				login.setRegId(canonicalRegId);
				endpoint.insertLogIn(login);
			}
		} else {
			String error = result.getErrorCodeName();
			if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
				endpoint.removeLogIn(login.getId());
			}
		}

		return result;
	}

}
