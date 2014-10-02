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
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityManager;
import javax.persistence.Query;

@Api(name = "tosyncendpoint", namespace = @ApiNamespace(ownerDomain = "trip.com", ownerName = "trip.com", packagePath = "expensemanager"))
public class ToSyncEndpoint {

	/**
	 * This method lists all the entities inserted in datastore.
	 * It uses HTTP GET method and paging support.
	 *
	 * @return A CollectionResponse class containing the list of all entities
	 * persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listToSync")
	public CollectionResponse<ToSync> listToSync(
			@Nullable @Named("cursor") String cursorString,
			@Nullable @Named("limit") Integer limit) {

		EntityManager mgr = null;
		Cursor cursor = null;
		List<ToSync> execute = null;

		try {
			mgr = getEntityManager();
			Query query = mgr.createQuery("select from ToSync as ToSync");
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				query.setHint(JPACursorHelper.CURSOR_HINT, cursor);
			}

			if (limit != null) {
				query.setFirstResult(0);
				query.setMaxResults(limit);
			}

			execute = (List<ToSync>) query.getResultList();
			cursor = JPACursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (ToSync obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<ToSync> builder().setItems(execute)
				.setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 *
	 * @param id the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getToSync")
	public ToSync getToSync(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		ToSync tosync = null;
		try {
			tosync = mgr.find(ToSync.class, id);
		} finally {
			mgr.close();
		}
		return tosync;
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already
	 * exists in the datastore, an exception is thrown.
	 * It uses HTTP POST method.
	 *
	 * @param tosync the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertToSync")
	public ToSync insertToSync(ToSync tosync) {
		EntityManager mgr = getEntityManager();
		try {
			mgr.persist(tosync);
		} finally {
			mgr.close();
		}
		return tosync;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not
	 * exist in the datastore, an exception is thrown.
	 * It uses HTTP PUT method.
	 *
	 * @param tosync the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateToSync")
	public ToSync updateToSync(ToSync tosync) {
		EntityManager mgr = getEntityManager();
		try {
			if (!containsToSync(tosync)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.persist(tosync);
		} finally {
			mgr.close();
		}
		return tosync;
	}

	/**
	 * This method removes the entity with primary key id.
	 * It uses HTTP DELETE method.
	 *
	 * @param id the primary key of the entity to be deleted.
	 */
	@ApiMethod(name = "removeToSync")
	public void removeToSync(@Named("id") Long id) {
		EntityManager mgr = getEntityManager();
		try {
			ToSync tosync = mgr.find(ToSync.class, id);
			mgr.remove(tosync);
		} finally {
			mgr.close();
		}
	}

	private boolean containsToSync(ToSync tosync) {
		EntityManager mgr = getEntityManager();
		boolean contains = true;
		try {
			ToSync item = mgr.find(ToSync.class, tosync.getId());
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
