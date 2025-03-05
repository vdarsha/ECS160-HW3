package com.ecs160.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import redis.clients.jedis.Jedis;

/*
 * Persistence session that is used to either save persistable data from an arbitrary object to Redis,
 * or to load persistable data from Redis store into an arbitrary object.
 * Assumption - only support int/long/and string values
 */
public class Session {
    private final Jedis jedisSession;
    private final Map<Object, ReflectedObjectAttributes> objAttrsList;

    /**
     * Instantiate new persistence session with given Jedis session
     * @param jedisSession Jedis session to use for persistence
     */
    public Session(Jedis jedisSession) {
        this.jedisSession = jedisSession;
        objAttrsList = new HashMap<Object, ReflectedObjectAttributes>();
    }

    /**
     * Add new object for later persistence on persistAll() call
     * @param obj Object to enable persistence for in this session
     * @throws Exception ReflectedObjectAttribute instantiation may throw exception
     */
    public void add(Object obj) throws Exception {
        objAttrsList.put(obj, new ReflectedObjectAttributes(obj.getClass()));
    }

    /**
     * Trigger persistence saving to Redis for all loaded objects
     * @throws IllegalAccessException If reflection cannot access field
     * @throws PersistenceException If there is an exception when attempting to persist object
     */
    public void persistAll() throws IllegalAccessException, PersistenceException {
        for (Map.Entry<Object, ReflectedObjectAttributes> attrPair : objAttrsList.entrySet()) {
            persistRecursive(attrPair.getKey(), attrPair.getValue());
        }
    }

    /**
     * Recursively persist fields into Redis
     * Regular call-stack function recursion is safe because recursion depth is limited to two
     * (only top-level posts and first-level replies are allowed)
     * @param obj object to persist
     * @param attrs reflected object attributes of object to persist
     * @throws IllegalAccessException If reflection cannot access field
     * @throws PersistenceException If there is an exception when attempting to persist object
     */
    public void persistRecursive(Object obj, ReflectedObjectAttributes attrs) throws IllegalAccessException, PersistenceException {
        String objId = attrs.getId(obj);
        Map<String, String> fieldPairs = attrs.getFieldPairs(obj);

        // Objects in lists of the persisted objects may be added or removed independent of the parent-level object list
        // in this Session object.
        // Therefore, we must manually re-generate reflected object attributes for list objects whenever persistAll() is called.
        for (ListFieldPair listFields : attrs.getListFieldPairs(obj)) {
            ReflectedObjectAttributes listObjAttrs = listFields.getListObjAttrs();
            List<Object> listObjs = listFields.getObjList();

            // Per HW2 specification, list fields are stored as comma-separated string of references to the list object IDs.
            // Also, per IntelliJ suggestion, I use StringBuilder instead of String to construct the list,
            // due to performance loss if I were to instead immutably keep replacing the String every time a new ID is appended.
            StringBuilder idList = new StringBuilder();

            for (Object listObj : listObjs) {
                // Given that recursion depth is fixed to 2 (no replies-to-replies are allowed), then call-stack recursion
                // will not result in stack-overflow.
                // In fact, normal call-stack recursion enables high readability.
                persistRecursive(listObj, listObjAttrs);
                String listObjId = listObjAttrs.getId(listObj);
                idList.append(listObjId).append(",");
            }

            // Remove trailing comma
            if (!idList.isEmpty()) {
                idList.delete(idList.length() - 1, idList.length());
            }

            // Add the generated
            fieldPairs.put(listFields.getField().getName(), idList.toString());
        }

        // Object persistence structure allows for single hset() call to persist data
        jedisSession.hset(objId, fieldPairs);
    }

    /**
     * Load object from Redis store.
     * It is a requirement that the given object have the "id" field specified
     * so that the location in Redis is known.
     *
     * In particular, this method is bi-recursive, since it will call setFields() from the reflected object attributes class,
     * and then setFields() may either directly call load() again,
     * or defer a recursive call of load() if LazyLoad is specified.
     * @param object Object to load persistence data into
     * @param objAttrs Reflected object attributes to use for setting the persistence fields on the object
     * @return Object with data optionally loaded, depending on where @LazyLoad is set.
     * @throws Exception setFields() may throw an exception
     */
    public Object load(Object object, ReflectedObjectAttributes objAttrs) throws Exception {
        // load() method is bi-recursive: pass in this Session object so that list objects can be recursively loaded
        objAttrs.setFields(this, object, jedisSession);

        return object;
    }
}
