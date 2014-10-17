package com.trip.expensemanager;

import com.trip.expensemanager.EMF;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.datanucleus.query.JPACursorHelper;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "tripendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class TripEndpoint {

	private static final String API_KEY = "AIzaSyAIG5GBeL2dYuoN5525AqOmHydvAoW7_LE";



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
			TripEndpoint tripEndpoint=new TripEndpoint();
			Trip tripTemp=tripEndpoint.getTrip(trip.getId());
			//			mgr.persist(tempTrip);
			mgr.persist(trip);
			//			Sender sender = new Sender(MessageEndpoint.API_KEY);
			List<Long> userIdsTemp=tripTemp.getUserIDs();
			List<Long> userIds = trip.getUserIDs();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				if(!userId.equals(trip.getChangerId())){
					login=endpoint.getLogIn(userId);
					if(userIdsTemp.containsAll(userIds)){
						addToToSync("TU", trip.getId(), login.getId(), trip.getChangerId());
					} else{
						addToToSync("UA", trip.getId(), login.getId(), trip.getChangerId());
					}
					jsonArr.put(login.getRegId());
				}
			}
			doSendViaGcm(jsonArr);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			mgr.close();
		}
		return trip;
	}

	private void addToToSync(String message, Long lngId, Long userId, Long changerId) throws IOException {
		ToSync toSync=new ToSync();
		toSync.setSyncItem(lngId);
		toSync.setSyncType(message);
		toSync.setUserId(userId);
		toSync.setChangerId(changerId);
		ToSyncEndpoint toSyncEndpoint=new ToSyncEndpoint();
		toSyncEndpoint.insertToSync(toSync);
	}

	private void doSendViaGcm(JSONArray jsonArr) throws IOException, JSONException {
		String json ="{}";
		//		jsonArr.put("APA91bFgxjBiEAGTAUfEDUKNTWQbgImWqGoafiN1sjmSvaLF7v0x8IAFUNcCvOXpI3_VuJfLEOFpoxapCa6h37A1NJckgtVA3_kl3BXvLiR3Mf9aEJptrR6QDOWOR44fXHrLk1FalqMe-q2xdpic-0iCBdUWO7bdtg");
		JSONObject jsonObj=new JSONObject();
		jsonObj.put("registration_ids", jsonArr);

		json=jsonObj.toString();
		System.out.println("request "+json);

		URL url = new URL("https://android.googleapis.com/gcm/send");
		HTTPRequest request = new HTTPRequest(url, HTTPMethod.POST);
		request.addHeader(new HTTPHeader("Content-Type","application/json")); 
		request.addHeader(new HTTPHeader("Authorization", "key="+API_KEY));
		request.setPayload(json.getBytes("UTF-8"));
		HTTPResponse response = URLFetchServiceFactory.getURLFetchService().fetch(request);
		System.out.println("Content "+new String(response.getContent()));
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
			List<Long> userIds = trip.getUserIDs();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				if(!userId.equals(trip.getAdmin())){
					login=endpoint.getLogIn(userId);
					addToToSync("TD", trip.getId(), login.getId(), trip.getAdmin());
					jsonArr.put(login.getRegId());
				}
			}
			doSendViaGcm(jsonArr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
