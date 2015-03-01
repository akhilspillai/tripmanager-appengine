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
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "distributionendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class DistributionEndpoint {

	private static final String API_KEY = "AIzaSyAIG5GBeL2dYuoN5525AqOmHydvAoW7_LE";
	private static final Logger log = Logger.getLogger(ExpenseEndpoint.class.getName());

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listDistribution")
	public CollectionResponse<Distribution> listDistribution(
			@Nullable @Named("cursor") String cursorString,
			@Nullable @Named("limit") Integer limit,
			@Nullable @Named("tripId") Long tripId) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<Distribution> execute = null;

		try {
			mgr = getEntityManager();
			Query query;
			if(tripId!=null){
				query = mgr.createQuery("select from Distribution D where D.tripId=:tripId_fk order by D.creationDate");
				query.setParameter("tripId_fk", tripId);
			} else{
				query = mgr.createQuery("select from Distribution as Distribution");
			}

			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
			}

			if (limit != null) {
				query.setFirstResult(0);
				query.setMaxResults(limit);
			}

			execute = (List<Distribution>) query.getResultList();
			cursor = JPACursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (Distribution obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<Distribution> builder().setItems(execute)
				.setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getDistribution")
	public Distribution getDistribution(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		Distribution distribution = null;
		try {
			distribution = mgr.find(Distribution.class, id);
		} finally {
			mgr.close();
		}
		return distribution;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param distribution the entity to be inserted.
	 * @return The inserted entity.
	 * @throws IOException 
	 * @throws JSONException 
	 */
	@ApiMethod(name = "insertDistribution")
	public Distribution insertDistribution(Distribution distribution) throws IOException, JSONException {
		Distribution retDestribution;
		DistributionEndpoint distEndpoint=new DistributionEndpoint();
		retDestribution=distEndpoint.insertNewDistribution(distribution);
		LogIn login;
		LogInEndpoint loginEndpoint=new LogInEndpoint();
		JSONArray jsonArr=new JSONArray();
		List<Long> deviceIds=null;
		DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
		DeviceInfo devInfo=null;
		login=loginEndpoint.getLogIn(retDestribution.getToId());
		long changerId=retDestribution.getChangerId();
		TripEndpoint tripEndpoint=new TripEndpoint();
		Trip trip=tripEndpoint.getTrip(distribution.getTripId());
		if(trip!=null){
			List<Long> tripUserIds=trip.getUserIDs();
			LogInEndpoint endpoint=new LogInEndpoint();
			for (Long userId:tripUserIds) {
				login=endpoint.getLogIn(userId);
				if(login!=null){
					deviceIds=login.getDeviceIDs();
					if(deviceIds!=null){
						for(long deviceId:deviceIds){
							if(deviceId!=changerId){
								devInfo=devInfoendpoint.getDeviceInfo(deviceId);
								if(devInfo!=null){
									addToToSync("DA", retDestribution.getId(), deviceId, retDestribution.getToId());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
			}
		}
		doSendViaGcm(jsonArr);
		return retDestribution;
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
		if(jsonArr.length()!=0){
			JSONObject jsonObj=new JSONObject();
			jsonObj.put("registration_ids", jsonArr);

			json=jsonObj.toString();
			log.info("request "+json);
			URL url = new URL("https://android.googleapis.com/gcm/send");
			HTTPRequest request = new HTTPRequest(url, HTTPMethod.POST);
			request.addHeader(new HTTPHeader("Content-Type","application/json")); 
			request.addHeader(new HTTPHeader("Authorization", "key="+API_KEY));
			request.setPayload(json.getBytes("UTF-8"));
			HTTPResponse response = URLFetchServiceFactory.getURLFetchService().fetch(request);
			log.info("Content "+new String(response.getContent()));
		} else{
			log.info("Array is empty");
		}
	}

	private Distribution insertNewDistribution(Distribution distribution) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(distribution);
		} finally {
			mgr.close();
		}
		return distribution;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param distribution the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateDistribution")
	public Distribution updateDistribution(Distribution distribution) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsDistribution(distribution)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.persist(distribution);
		} finally {
			mgr.close();
		}
		return distribution;
	}

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 */
	@ApiMethod(name = "removeDistribution")
	public void removeDistribution(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		try {
			Distribution distribution = mgr.find(Distribution.class, id);
			mgr.remove(distribution);
		} finally {
			mgr.close();
		}
	}

	private boolean containsDistribution(Distribution distribution) {
		EntityManager mgr = getEntityManager();
		boolean contains = true;
		try {
			Distribution item = mgr.find(Distribution.class,
					distribution.getId());
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
