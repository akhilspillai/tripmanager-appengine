package com.trip.expensemanager;

import com.trip.expensemanager.EMF;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.datanucleus.query.JPACursorHelper;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "loginendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class LogInEndpoint {

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listLogIn")
	public CollectionResponse<LogIn> listLogIn(
			@Nullable @Named("cursor") String cursorString,
			@Nullable @Named("limit") Integer limit,
			@Nullable @Named("username") String username) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<LogIn> execute = null;
		Query query=null;
		try {
			mgr = getEntityManager();

			if(username!=null){
				query = mgr.createQuery("select from LogIn as LogIn where LogIn.username=:username_fk");
				query.setParameter("username_fk", username);
			} else{
				query = mgr.createQuery("select from LogIn as LogIn");
			}
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
			}

			if (limit != null) {
				query.setFirstResult(0);
				query.setMaxResults(limit);
			}

			execute = (List<LogIn>) query.getResultList();
			cursor = JPACursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (LogIn obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<LogIn> builder().setItems(execute)
				.setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getLogIn")
	public LogIn getLogIn(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		LogIn login = null;
		try {
			login = mgr.find(LogIn.class, id);
		} finally {
			mgr.close();
		}
		return login;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param login the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertLogIn")
	public LogIn insertLogIn(LogIn login) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(login);
		} finally {
			mgr.close();
		}
		return login;
	}

	//	@ApiMethod(name = "createAccount")
	//	public LogIn createAccount(@Named("regId") String regId, @Named("make") String make, @Named("username") String username, @Named("password") String password, @Named("userId") Long userId) {
	//		EntityManager mgr = getEntityManager();
	//		List<Long> deviceIds=null;
	//		DeviceInfoEndpoint devInfoEndpoint=new DeviceInfoEndpoint();
	//		DeviceInfo devInfo=null;
	//		LogIn login=null;
	//		try {
	//			if(userId==0L){
	//				login=new LogIn();
	//				devInfo=new DeviceInfo();
	//				devInfo.setGcmRegId(regId);
	//				devInfo.setMake(make);
	//				devInfo.setUserId(userId);
	//				devInfo=devInfoEndpoint.insertDeviceInfo(devInfo);
	//				deviceIds=new ArrayList<Long>();
	//				deviceIds.add(devInfo.getId());
	//				login.setPassword(password);
	//				login.setUsername(username);
	//				login.setDeviceIDs(deviceIds);
	//			} else{
	//				login=getLogIn(userId);
	//				CollectionResponse<DeviceInfo> infoCollResp = devInfoEndpoint.listDeviceInfo(null, null, login.getId());
	//				Collection<DeviceInfo> devInfos = infoCollResp.getItems();
	//				for(DeviceInfo devInfoTemp:devInfos){
	//					if(devInfoTemp.getGcmRegId().equals(regId)){
	//						devInfo=devInfoTemp;
	//						break;
	//					}
	//				}
	//				if(devInfo==null){
	//					devInfo=new DeviceInfo();
	//					devInfo.setGcmRegId(regId);
	//					devInfo.setMake(make);
	//					devInfo.setUserId(userId);
	//					devInfo=devInfoEndpoint.insertDeviceInfo(devInfo);
	//					deviceIds=login.getDeviceIDs();
	//					deviceIds.add(devInfo.getId());
	//					login.setDeviceIDs(deviceIds);
	//				}
	//			}
	//			mgr.persist(login);
	//		} finally {
	//			mgr.close();
	//		}
	//		return login;
	//	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param login the entity to be updated.
	 * @return The updated entity.
	 * @throws IOException 
	 * @throws JSONException 
	 */
	@ApiMethod(name = "updateLogIn")
	public LogIn updateLogIn(LogIn login) throws IOException, JSONException {
		EntityManager mgr = getEntityManager();
		GCMUtil objGCMUtil=new GCMUtil();
		try {
			if (!containsLogIn(login)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			//			LogIn tempLogin=mgr.find(LogIn.class, login.getId());
			//			tempLogin.setTripIDs(login.getTripIDs());
			//			tempLogin.setRegId(login.getRegId());
			LogInEndpoint endpoint=new LogInEndpoint();
			LogIn oldLogin=endpoint.getLogIn(login.getId());
			List<Long> oldDevIds=oldLogin.getDeviceIDs();
			List<Long> newDevIds=login.getDeviceIDs();
			if(newDevIds.containsAll(oldDevIds)){
				if(oldLogin.getPurchaseId()==null && login.getPurchaseId()!=null){
					DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
					DeviceInfo devInfo=null;
					List<Long> devIds=login.getDeviceIDs();
					JSONArray jsonArr=new JSONArray();
					for(long devId:devIds){
						devInfo=devInfoendpoint.getDeviceInfo(devId);
						objGCMUtil.addToToSync("IP", login.getId(), devId, login.getId());
						jsonArr.put(devInfo.getGcmRegId());
					}
					objGCMUtil.doSendViaGcm(jsonArr);
				}
			} else{
				JSONArray jsonArr=new JSONArray();
				DeviceInfoEndpoint devInfoendpoint=new DeviceInfoEndpoint();
				DeviceInfo devInfo=null;
				for(long devId:oldDevIds){
					if(!newDevIds.contains(devId)){
						devInfo=devInfoendpoint.getDeviceInfo(devId);
						objGCMUtil.addToToSync("LO", login.getId(), devId, login.getId());
						jsonArr.put(devInfo.getGcmRegId());
					}
				}
				objGCMUtil.doSendViaGcm(jsonArr);
			}
			mgr.merge(login);
		} finally {
			mgr.close();
		}
		return login;
	}

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 */
	@ApiMethod(name = "removeLogIn")
	public void removeLogIn(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		try {
			LogIn login = mgr.find(LogIn.class, id);
			mgr.remove(login);
		} finally {
			mgr.close();
		}
	}

	private boolean containsLogIn(LogIn login) {
		EntityManager mgr = getEntityManager();
		boolean contains = true;
		try {
			LogIn item = mgr.find(LogIn.class, login.getId());
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

	public LogIn mergeLogIn(LogIn login) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsLogIn(login)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.merge(login);
		} finally {
			mgr.close();
		}
		return login;
	}

}
