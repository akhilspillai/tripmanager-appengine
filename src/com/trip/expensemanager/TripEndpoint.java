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
import com.google.appengine.datanucleus.query.JPACursorHelper;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "tripendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class TripEndpoint {

	

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listTrip")
	public CollectionResponse<Trip> listTrip(
			@Nullable @Named("cursor") String cursorString,
			@Nullable @Named("limit") Integer limit) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<Trip> execute = null;

		try {
			mgr = getEntityManager();
			Query query = mgr.createQuery("select from Trip as Trip");
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
			}

			if (limit != null) {
				query.setFirstResult(0);
				query.setMaxResults(limit);
			}

			execute = (List<Trip>) query.getResultList();
			cursor = JPACursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (Trip obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<Trip> builder().setItems(execute)
				.setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getTrip")
	public Trip getTrip(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		Trip trip = null;
		try {
			trip = mgr.find(Trip.class, id);
		} finally {
			mgr.close();
		}
		return trip;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param trip the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertTrip")
	public Trip insertTrip(Trip trip) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(trip);
		} finally {
			mgr.close();
		}
		return trip;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param trip the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateTrip")
	public Trip updateTrip(Trip trip) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsTrip(trip)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			Trip tempTrip=mgr.find(Trip.class, trip.getId());
			tempTrip.setUserIDs(trip.getUserIDs());
			mgr.persist(tempTrip);
//			Sender sender = new Sender(MessageEndpoint.API_KEY);
//			List<Long> userIds = tempTrip.getUserIDs();
//			LogIn login=null;
//			LogInEndpoint endpoint = new LogInEndpoint();
//			long userIdTemp=userIds.get(userIds.size()-1);
//			login=endpoint.getLogIn(userIdTemp);
//			long lngTripId=tempTrip.getId();
//			for (Long userId:userIds) {
////				login= mgr.find(LogIn.class, userId);
//				if(userId!=userIdTemp){
//					login=endpoint.getLogIn(userId);
//					doSendViaGcm(lngTripId+",UA,"+userIdTemp, sender, login);
//				}
//			}
		} finally {
			mgr.close();
		}
		return trip;
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

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 */
	@ApiMethod(name = "removeTrip")
	public void removeTrip(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		try {
			Trip trip = mgr.find(Trip.class, id);
			mgr.remove(trip);
		} finally {
			mgr.close();
		}
	}

	private boolean containsTrip(Trip trip) {
		EntityManager mgr = getEntityManager();
		boolean contains = true;
		try {
			Trip item = mgr.find(Trip.class, trip.getId());
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

}
