package com.trip.expensemanager;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.Query;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.datanucleus.query.JPACursorHelper;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
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
		GCMUtil objGCMUtil=new GCMUtil();
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
								objGCMUtil.addToToSync("TA", retTrip.getId(), deviceId, retTrip.getAdmin());
								jsonArr.put(devInfo.getGcmRegId());
							}
						}
					}
				}
			}
			if(jsonArr.length()!=0){
				objGCMUtil.doSendViaGcm(jsonArr);
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
		GCMUtil objGCMUtil=new GCMUtil();
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
			List<Long> userIdsToSync=userIds;
			userAdded=userIdsTemp.size()<userIds.size();
			userRemoved=userIdsTemp.size()>userIds.size();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
			DeviceInfo devInfo=null;
			List<Long> lstTrips=null;
			long lngChangerId=0L;
			if(userRemoved){
				for (Long userIdTemp:userIdsTemp) {
					if(!userIds.contains(userIdTemp)){
						lngChangerId=userIdTemp;
						break;
					}
				}
				login=endpoint.getLogIn(lngChangerId);
				lstTrips=login.getTripIDs();
				if(lstTrips!=null){
					lstTrips.remove(trip.getId());
				}
				login.setTripIDs(lstTrips);
				endpoint.mergeLogIn(login);
				userIdsToSync=userIdsTemp;
			}
			long changerId=trip.getChangerId();
			List<Long> deviceIds=null;
			JSONArray jsonArr=new JSONArray();
			if(!userRemoved){
				for (Long userId:userIds) {
					login=endpoint.getLogIn(userId);
					if(login!=null){
						deviceIds=login.getDeviceIDs();
						if(deviceIds!=null){
							if(deviceIds.contains(changerId)){
								lngChangerId=userId;
								break;
							}
						}
					}
				}
			}

			for (Long userId:userIdsToSync) {
				login=endpoint.getLogIn(userId);
				if(login!=null){
					deviceIds=login.getDeviceIDs();
					if(deviceIds!=null){
						for(long deviceId:deviceIds){
							if(deviceId!=changerId){
								devInfo=devInfoendpoint.getDeviceInfo(deviceId);
								if(devInfo!=null){
									if(userAdded){
										objGCMUtil.addToToSync("UA", trip.getId(), deviceId, lngChangerId);
									} else if(userRemoved){
										objGCMUtil.addToToSync("UD", trip.getId(), deviceId, lngChangerId);
									} else{
										objGCMUtil.addToToSync("TU", trip.getId(), deviceId, lngChangerId);
									}
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
			}
			objGCMUtil.doSendViaGcm(jsonArr);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} finally {
			mgr.close();
		}
		return trip;
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
		GCMUtil objGCMUtil=new GCMUtil();
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
				DistributionEndpoint distEndpoint=new DistributionEndpoint();
				CollectionResponse<Distribution> distResult = distEndpoint.listDistribution(null, null, id);
				Collection<Distribution> distributions=distResult.getItems();
				for(Distribution distTemp:distributions){
					distEndpoint.removeDistribution(distTemp.getId());
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
									objGCMUtil.addToToSync("TD", trip.getId(), deviceId, trip.getAdmin());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
				mgr.remove(trip);
				objGCMUtil.doSendViaGcm(jsonArr);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
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
