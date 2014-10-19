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
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "expenseendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class ExpenseEndpoint {

	private static final String API_KEY = "AIzaSyAIG5GBeL2dYuoN5525AqOmHydvAoW7_LE";

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
			if(tripId!=null){
				query = mgr.createQuery("select from Expense E where E.tripId=:tripId_fk");
				query.setParameter("tripId_fk", tripId);
			} else if(date!=null && userId!=null){
				query = mgr.createQuery("select from Expense E where E.userId=:userId_fk");
				query.setParameter("userId_fk", userId);
//				query.setParameter("date_fk", date);
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
	 */
	@ApiMethod(name = "insertExpense")
	public Expense insertExpense(Expense expense) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(expense);
		} finally {
			mgr.close();
		}
		return expense;
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
			TripEndpoint tripEndpoint=new TripEndpoint();
			Trip trip=tripEndpoint.getTrip(expense.getTripId());
			List<Long> userIds = trip.getUserIDs();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			String msg;
			if(expense.getChangerId()==0L){
				msg="EA";
			}
			else {
				msg="EU";
			}
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				if(!userId.equals(expense.getUserId())){
					login=endpoint.getLogIn(userId);
					addToToSync(msg, expense.getId(), login.getId(), expense.getUserId());
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
			TripEndpoint tripEndpoint=new TripEndpoint();
			Trip trip=tripEndpoint.getTrip(expense.getTripId());
			List<Long> userIds = trip.getUserIDs();
			LogIn login;
			LogInEndpoint endpoint=new LogInEndpoint();
			JSONArray jsonArr=new JSONArray();
			for (Long userId:userIds) {
				if(!userId.equals(expense.getUserId())){
					login=endpoint.getLogIn(userId);
					addToToSync("ED", expense.getId(), login.getId(), expense.getUserId());
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
