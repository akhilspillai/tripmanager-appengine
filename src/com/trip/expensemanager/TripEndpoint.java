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
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;
@Api(name = "tripendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class TripEndpoint {

	private static final String API_KEY = "AIzaSyAIG5GBeL2dYuoN5525AqOmHydvAoW7_LE";

	private static final Logger log = Logger.getLogger(TripEndpoint.class.getName());


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
			Trip retTrip = new TripEndpoint().insertNewTrip(trip);
			LogInEndpoint endpoint=new LogInEndpoint();
			LogIn login = endpoint.getLogIn(retTrip.getAdmin());
			JSONArray jsonArr=new JSONArray();
			if(login!=null){
				List<Long> deviceIds = login.getDeviceIDs();
				DeviceInfo devInfo=null;
				DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
				long changerId=retTrip.getChangerId();
				if(deviceIds!=null && deviceIds.size()>1){
					for(long deviceId:deviceIds){
						if(deviceId!=changerId){
							devInfo=devInfoendpoint.getDeviceInfo(deviceId);
							if(devInfo!=null){
								addToToSync("TA", retTrip.getId(), deviceId, retTrip.getChangerId());
								jsonArr.put(devInfo.getGcmRegId());
							}
						}
					}
				}
			}
			if(jsonArr.length()!=0){
				doSendViaGcm(jsonArr);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			mgr.close();
		}
		return trip;
	}
	
	private Trip insertNewTrip(Trip trip) {
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
		boolean userAdded=false;
		boolean userRemoved=false;
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
			userAdded=userIdsTemp.size()<userIds.size();
			userRemoved=userIdsTemp.size()>userIds.size();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			ExpenseEndpoint expenseEndpoint=new ExpenseEndpoint();
			List<Long> lstTrips=null;
			if(userRemoved){
				login=endpoint.getLogIn(trip.getChangerId());
				lstTrips=login.getTripIDs();
				if(lstTrips!=null){
					lstTrips.remove(trip.getChangerId());
				}
				login.setTripIDs(lstTrips);
				endpoint.mergeLogIn(login);
				CollectionResponse<Expense> expensesCollResp = expenseEndpoint.listExpense(null, null, trip.getId(), trip.getChangerId(), null);
				Collection<Expense> expenses = expensesCollResp.getItems();
				for(Expense expTemp:expenses){
					expenseEndpoint.removeTripExpense(expTemp.getId());
				}
			}
			DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
			DeviceInfo devInfo=null;
			List<Long> deviceIds=null;
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				login=endpoint.getLogIn(userId);
				if(login!=null){
					deviceIds=login.getDeviceIDs();
					if(deviceIds!=null){
						for(long deviceId:deviceIds){
							devInfo=devInfoendpoint.getDeviceInfo(deviceId);
							if(devInfo!=null){
								if(userAdded){
									addToToSync("UA", trip.getId(), deviceId, trip.getChangerId());
								} else if(userRemoved){
									addToToSync("UD", trip.getId(), deviceId, trip.getChangerId());
								} else{
									addToToSync("TU", trip.getId(), deviceId, trip.getChangerId());
								}
								jsonArr.put(devInfo.getGcmRegId());
							}
						}
					}
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
		log.info("request "+json);
		URL url = new URL("https://android.googleapis.com/gcm/send");
		HTTPRequest request = new HTTPRequest(url, HTTPMethod.POST);
		request.addHeader(new HTTPHeader("Content-Type","application/json")); 
		request.addHeader(new HTTPHeader("Authorization", "key="+API_KEY));
		request.setPayload(json.getBytes("UTF-8"));
		HTTPResponse response = URLFetchServiceFactory.getURLFetchService().fetch(request);
		log.info("Content "+new String(response.getContent()));
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
			if(trip!=null){
				List<Long> userIds = trip.getUserIDs();
				List<Long> tripIds=null;
				LogIn login;
				LogInEndpoint endpoint=new LogInEndpoint();
				JSONArray jsonArr=new JSONArray();
				ExpenseEndpoint expenseEndpoint=new ExpenseEndpoint();
				CollectionResponse<Expense> result = expenseEndpoint.listExpense(null, null, id, null, null);
				Collection<Expense> expenses=result.getItems();
				for(Expense expenseTemp:expenses){
					expenseEndpoint.removeTripExpense(expenseTemp.getId());
				}
				DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
				DeviceInfo devInfo=null;
				List<Long> deviceIds=null;
				for (Long userId:userIds) {
					login=endpoint.getLogIn(userId);
					if(login!=null){
						deviceIds=login.getDeviceIDs();
						tripIds=login.getTripIDs();
						if(tripIds.contains(trip.getId())){
							tripIds.remove(trip.getId());
						}
						login.setTripIDs(tripIds);
						endpoint.mergeLogIn(login);
						if(deviceIds!=null){
							for(long deviceId:deviceIds){
								devInfo=devInfoendpoint.getDeviceInfo(deviceId);
								if(devInfo!=null){
									addToToSync("TD", trip.getId(), deviceId, trip.getAdmin());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
				mgr.remove(trip);
				doSendViaGcm(jsonArr);
			}
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
