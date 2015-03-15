package com.trip.expensemanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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
			@Nullable @Named("tripId") Long tripId,
			@Nullable @Named("userId") Long userId,
			@Nullable @Named("date") Date date) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<Expense> execute = null;
		Query query=null;
		try {
			mgr = getEntityManager();
			if(tripId!=null && userId==null){
				query = mgr.createQuery("select from Expense E where E.tripId=:tripId_fk order by E.creationDate");
				query.setParameter("tripId_fk", tripId);
			} else if(tripId!=null && userId!=null){
				query = mgr.createQuery("select from Expense E where E.userId=:userId_fk and E.tripId=:tripId_fk order by E.creationDate");
				query.setParameter("userId_fk", userId);
				query.setParameter("tripId_fk", tripId);
			}else{
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
	 * @throws IOException 
	 * @throws JSONException 
	 */
	@ApiMethod(name = "insertExpense")
	public Expense insertExpense(Expense expense) throws IOException, JSONException {
		Expense retExpense=null;
		ExpenseEndpoint endpoint=new ExpenseEndpoint();
		GCMUtil objGCMUtil=new GCMUtil();
		retExpense=endpoint.insertNewExpense(expense);
		TripEndpoint tripEndpoint=new TripEndpoint();
		Trip trip=tripEndpoint.getTrip(expense.getTripId());
		DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
		DeviceInfo devInfo=null;
		List<Long> deviceIds=null;
		if(trip!=null){
			long changerId=expense.getChangerId();
			List<Long> userIds = trip.getUserIDs();
			LogIn login;
			LogInEndpoint loginEndpoint=new LogInEndpoint();
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				login=loginEndpoint.getLogIn(userId);
				if(login!=null){
					deviceIds=login.getDeviceIDs();
					if(deviceIds!=null){
						for(long deviceId:deviceIds){
							if(deviceId!=changerId){
								devInfo=devInfoendpoint.getDeviceInfo(deviceId);
								if(devInfo!=null){
									objGCMUtil.addToToSync("EA", retExpense.getId(), deviceId, retExpense.getUserId());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
			}
			objGCMUtil.doSendViaGcm(jsonArr);
		}
		return retExpense;
	}


	private Expense insertNewExpense(Expense expense){
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(expense);
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
		GCMUtil objGCMUtil=new GCMUtil();
		try {
			if (!containsExpense(expense)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			List<Long> userIdsPrev=getExpense(expense.getId()).getExpenseUserIds();
			List<Long> userIds = expense.getExpenseUserIds();
			List<Long> userIdsTemp=new ArrayList<Long>();
			userIdsTemp.addAll(userIds);
			expense.setExpenseUserIds(userIdsTemp);
			mgr.merge(expense);
			TripEndpoint tripEndpoint=new TripEndpoint();
			Trip trip=tripEndpoint.getTrip(expense.getTripId());
			DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
			DeviceInfo devInfo=null;
			List<Long> deviceIds=null;
			if(trip!=null){
				long changerId=expense.getChangerId();
				for(Long userId:userIdsPrev){
					if(!userIds.contains(userId)){
						userIds.add(userId);
					}
				}
				List<Long> tripUserIds=trip.getUserIDs();
				LogIn login;
				LogInEndpoint endpoint=new LogInEndpoint();
				JSONArray jsonArr=new JSONArray();
				for (Long userId:tripUserIds) {
					login=endpoint.getLogIn(userId);
					if(login!=null){
						deviceIds=login.getDeviceIDs();
						if(deviceIds!=null){
							for(long deviceId:deviceIds){
								if(deviceId!=changerId){
									devInfo=devInfoendpoint.getDeviceInfo(deviceId);
									if(devInfo!=null){
										objGCMUtil.addToToSync("EU", expense.getId(), deviceId, expense.getUserId());
										jsonArr.put(devInfo.getGcmRegId());
									}
								}
							}
						}
					}
				}
				objGCMUtil.doSendViaGcm(jsonArr);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
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
		GCMUtil objGCMUtil=new GCMUtil();
		try {
			Expense expense = mgr.find(Expense.class, id);
			TripEndpoint tripEndpoint=new TripEndpoint();
			Trip trip=tripEndpoint.getTrip(expense.getTripId());
			if(trip!=null){
				List<Long> userIds = trip.getUserIDs();
				LogIn login;
				LogInEndpoint endpoint=new LogInEndpoint();
				JSONArray jsonArr=new JSONArray();
				DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
				DeviceInfo devInfo=null;
				List<Long> deviceIds=null;
				for (Long userId:userIds) {
					login=endpoint.getLogIn(userId);
					if(login!=null){
						deviceIds=login.getDeviceIDs();
						if(deviceIds!=null){
							for(long deviceId:deviceIds){
								devInfo=devInfoendpoint.getDeviceInfo(deviceId);
								if(devInfo!=null){
									objGCMUtil.addToToSync("ED", expense.getId(), deviceId, expense.getUserId());
									jsonArr.put(devInfo.getGcmRegId());
								}
							}
						}
					}
				}
				mgr.remove(expense);
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

	public void removeTripExpense(@Named("id")Long id) {
		EntityManager mgr = getEntityManager();
		try {
			Expense expense = mgr.find(Expense.class, id);
			mgr.remove(expense);
		} finally {
			mgr.close();
		}
	}

}
