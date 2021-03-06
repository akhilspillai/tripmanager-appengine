package com.trip.expensemanager;

import java.io.IOException;
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

@Api(name = "distributionendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class DistributionEndpoint {

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
		GCMUtil objGCMUtil=new GCMUtil();
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
									objGCMUtil.addToToSync("DA", retDestribution.getId(), deviceId, retDestribution.getToId());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
			}
		}
		objGCMUtil.doSendViaGcm(jsonArr);
		return retDestribution;
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
