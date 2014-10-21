package com.trip.expensemanager;

import com.trip.expensemanager.EMF;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.datanucleus.query.JPACursorHelper;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.persistence.EntityExistsException;
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

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param login the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateLogIn")
	public LogIn updateLogIn(LogIn login) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsLogIn(login)) {
				throw new EntityNotFoundException("Object does not exist");
			}
//			LogIn tempLogin=mgr.find(LogIn.class, login.getId());
//			tempLogin.setTripIDs(login.getTripIDs());
//			tempLogin.setRegId(login.getRegId());
			mgr.persist(login);
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
//			LogIn tempLogin=mgr.find(LogIn.class, login.getId());
//			tempLogin.setTripIDs(login.getTripIDs());
//			tempLogin.setRegId(login.getRegId());
			mgr.merge(login);
		} finally {
			mgr.close();
		}
		return login;
	}

}
