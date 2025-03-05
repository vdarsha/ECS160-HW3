package com.ecs160.persistence;

import redis.clients.jedis.Jedis;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/*
 * A separate class is used to represent all the fields associated with persistence annotations.
 * That is, collections are used to represent the fields in a particular class that have been annotated with
 * @PersistableField, @PersistableListField, or @PersistableId.
 *
 * This common interface encapsulates data and abstracts the manipulation of persistable objects.
 */
public class ReflectedObjectAttributes {
    // We must make "id" non-final since we iterate through a loop in order to find the id field.
    // In the code (see below), we enforce that only one @PersistableId can be specified.
    private Field id;
    private final Class<?> thisClass;
    private final List<Field> fields;
    private boolean isLazyLoad;
    // Recursively store reflected object attributes of persisted list fields
    private final Map<Field, ReflectedObjectAttributes> listFieldAttrs;
    // HashMap of previously constructed list ReflectedObjectAttributes to prevent infinite recursion
    private final Map<String, ReflectedObjectAttributes> prevAttrs;

    // The constructor will set up the field structure with reflection so that we don't have to repeat this work
    // when running persistAll().
    // However, per HW2 specification, actual persistence with Jedis is delayed until the persistAll() call.
    //
    // Furthermore, don't actually retrieve the data associated with the object until the getters are called in the Session persistAll() method.
    // This ensures that up-to-date object data is retrieved on the persistAll() call.
    /**
     * Instantiate a new reflected object attributes class
     * @param objClass Class for the persistable object which should be represented
     * @param newPrevAttrs To prevent infinite recursion with list fields that may specify recursive reference to the same class, we must keep track of parent object attributes.
     * @throws Exception Reflection code may generate exceptions
     */
    public ReflectedObjectAttributes(Class<?> objClass, Map<String, ReflectedObjectAttributes> newPrevAttrs) throws Exception {
        this.prevAttrs = newPrevAttrs;
        this.thisClass = objClass;
        this.isLazyLoad = false;
        // Initialize containers for fields and list-type fields
        fields = new LinkedList<Field>();
        // We use a HashMap to store both list Field object and the associated reflected object attributes.
        listFieldAttrs = new HashMap<Field, ReflectedObjectAttributes>();

        // Throw exception if object's instantiated class does not contain an @Persistable annotation
        if (!objClass.isAnnotationPresent(Persistable.class)) {
            throw new NotPersistableException(String.format("Class \"%s\" does not contain a \"Persistable\" annotation.", objClass.getName()));
        }

        // Verify that default constructor exists for the persisted object
        // This is necessary, because we need a principled and reliable way to be able to dynamically instantiate this class
        // when loading from Redis.
        //
        // Although an Object is provided to the load() function in Session, any child replies will need new Objects to be instantiated.
        try {
            objClass.getDeclaredConstructor();
        } catch (NoSuchMethodException ex) {
            throw new PersistenceException(String.format("Class \"%s\" must contain a default constructor so that dynamic instantiation for object loading is possible", objClass.getName()));
        }

        // Iterate only through declared fields for the current class type.
        // This is because if we iterate through all fields, then we may inadvertently scan through fields
        // in a parent class/interface that did not have the @Persistable annotation enabled.
        for (Field field : objClass.getDeclaredFields()) {
            // Per HW2 specification, fields annotated as persistable must have "private" access modifier.
            if (!Modifier.toString(field.getModifiers()).contains("private")) {
                throw new NotPersistableException(String.format("Field \"%s\" annotated as persistable must have private visibility", field.getName()));
            }

            // Find and set the id
            if (field.isAnnotationPresent(PersistableId.class)) {
                if (id != null) {
                    throw new IdException("Cannot specify multiple @PersistableId annotations");
                }

                id = field;
            }

            if (field.isAnnotationPresent(PersistableField.class)) {
                fields.add(field);
            }

            if (field.isAnnotationPresent(PersistableListField.class)) {
                // Per HW2 specification, a field annotated as @PersistableListField must be an instance of List<> type.
                if (!List.class.isAssignableFrom(field.getType())) {
                    throw new NotPersistableException(String.format("Field \"%s\" annotated as @PersistableListField must be a List<> type.", field.getName()));
                }

                PersistableListField listFieldAnnot = field.getAnnotation(PersistableListField.class);
                // Attempt getting class from provided fully-qualified class name
                Class<?> listFieldClass = Class.forName(listFieldAnnot.className());

                // Check if base class of list item reflected attributes have already been created previously.
                // This check will prevent infinite recursion (e.g. Post has a list field "replies" with elements type Post).
                ReflectedObjectAttributes listItemAttrs;
                if (prevAttrs.containsKey(listFieldAnnot.className())) {
                    listItemAttrs = prevAttrs.get(listFieldAnnot.className());
                } else {
                    if (listFieldClass.getName().equals(thisClass.getName())) {
                        prevAttrs.put(listFieldAnnot.className(), this);
                        listItemAttrs = new ReflectedObjectAttributes(listFieldClass, prevAttrs);
                    } else {
                        listItemAttrs = new ReflectedObjectAttributes(listFieldClass, prevAttrs);
                        prevAttrs.put(listFieldAnnot.className(), listItemAttrs);
                    }
                }

                // Convert reflected object attributes into lazily-loaded proxy of @LazyLoad is present on list
                if (field.isAnnotationPresent(LazyLoad.class)) {
                    listItemAttrs.setIsLazyLoad(true);
                }
                // Recursively insert list field class attributes
                listFieldAttrs.put(field, listItemAttrs);
            }
        }

        if (id == null) {
            throw new IdException("Persisted object must have one field annotated with @PersistableId");
        }
    }

    /**
     * Instantiate a new reflected object attributes class
     * Overload of method that will instantiate a new previous/parent attributes collection
     * @param objClass Class for the persistable object which should be represented
     */
    public ReflectedObjectAttributes(Class<?> objClass) throws Exception {
        this(objClass, new HashMap<String, ReflectedObjectAttributes>());
    }

    /**
     * Set that the class being represented is being lazy loaded (objects belongs to a parent lazy-loaded list)
     * @param val true or false
     */
    public void setIsLazyLoad(boolean val) {
        isLazyLoad = val;
    }

    /**
     * Get the lazy loading state for the represented objects
     * @return whether represented objects are being lazy loaded
     */
    public boolean getIsLazyLoad() {
        return isLazyLoad;
    }

    // Although in the HW2 specification we know that the Post class has "id" with type Integer,
    // we do not have the constraint under the "Assumptions" section that all ids must be of type Integer.
    // Given that Integer or String types are persistable, it is also possible that some other class may have
    // an id with type String instead.
    // Thus, given that it is easy to convert an Integer to a String, and that Jedis indexes key as a String,
    // this persistence is code is maximally decoupled from the persisted code by simply returning the id
    // as a String, regardless of the original source data type.
    /**
     * Get the labelled id field from a given object instance
     * @param obj object instance to load from
     * @return id field from given object instance
     * @throws IllegalAccessException Reflection exception
     * @throws IdException Throw exception if id is not set on object
     */
    public String getId(Object obj) throws IllegalAccessException, IdException {
        String retrievedId;
        // We must first temporarily set id to be accessible
        id.setAccessible(true);
        try {
            // Due to runtime polymorphism, the following toString() call will return either the same String
            // if id is a string, or the Integer conversion to a String if id is an Integer.
            retrievedId = id.get(obj).toString();

            return retrievedId;
        } catch (NullPointerException ex) {
            // NullPointerException thrown if ID is not instantiated on object.
            // In that case, throw IdException.
            throw new IdException("id field not instantiated in object");
        } finally {
            // Restore access modifier
            id.setAccessible(false);
        }
    }

    /**
     * Sets id field on given object
     * @param obj object to set id field on
     * @param newId new id value
     * @throws IllegalAccessException Reflection exception if id field cannot be accessed
     */
    public void setId(Object obj, Object newId) throws IllegalAccessException {
        // We must first temporarily set id to be accessible
        id.setAccessible(true);
        id.set(obj, newId);
        // Restore access modifiers
        id.setAccessible(false);
    }

    // Similar to the getId() function, Strings are returned because "String" is the most general type of data
    // given Integer or String, and is the default data type for inserting/retrieving with Jedis.
    /**
     * Get all the persistable fields and their values from the given object
     * @param obj object to retrieve fields and values
     * @return map representing pairs of fields and values
     * @throws IllegalAccessException Reflection throws exception if field(s) cannot be accessed
     */
    public Map<String, String> getFieldPairs(Object obj) throws IllegalAccessException {
        Map<String, String> retrievedFields = new HashMap<String, String>();

        for (Field field : fields) {
            // We must first temporarily set field to be accessible
            field.setAccessible(true);
            // Due to runtime polymorphism, the following toString() call will return either the same String
            // if field is a string, or the Integer conversion to a String if field is an Integer.
            retrievedFields.put(field.getName(), field.get(obj).toString());
            // Restore access modifier
            field.setAccessible(false);
        }

        return retrievedFields;
    }

    /**
     * Get all the persistable list fields and their values from the given object
     * List field values are represented using the ListFieldPair structure to encapsulate the Field type of the list, field object attributes of
     * the items, and the object representing the actual List<> itself.
     * @param obj object to retrieve list fields and values
     * @return List of retrieved list fields
     * @throws IllegalAccessException Reflection throws exception if field(s) cannot be accessed
     */
    public List<ListFieldPair> getListFieldPairs(Object obj) throws IllegalAccessException {
        List<ListFieldPair> retrievedListFields = new LinkedList<ListFieldPair>();

        for (Map.Entry<Field, ReflectedObjectAttributes> fieldAttrPair : listFieldAttrs.entrySet()) {
            Field field = fieldAttrPair.getKey();
            // We must temporarily set list field to be accessible in order to access it
            field.setAccessible(true);
            // NOTE: I suppress the warning for unchecked cast to List<Object> because:
            // 1. In the ReflectedObjectAttributes() constructor I verify that the object class either derives or is a List<>
            // 2. In the HW2 assumptions, it is given that the type of the list object is guaranteed to be non-primitive.
            // Thus, using parent class "Object" as the List type will never result in exception when casting the returned field object.
            @SuppressWarnings("unchecked")
            List<Object> listObjs = (List<Object>) field.get(obj);
            // Restore access modifier
            field.setAccessible(false);

            ListFieldPair listFieldPair = new ListFieldPair(field, fieldAttrPair.getValue(), listObjs);
            retrievedListFields.add(listFieldPair);
        }

        return retrievedListFields;
    }

    /**
     * Generate new instance of the class that this reflected attributes object represents.
     * This will use the default constructor of the target persistable class.
     * This is likely the only reliable way to dynamically generate new instances of an arbitrary class.
     * Otherwise, we would have to first generate template objects and pass it, which is cumbersome when automatically
     * instantiating objects for lists.
     * Or, automatically generating default values for the first useable constructor is error-prone because
     * we may generate values that are semantically incorrect for the given constructor.
     *
     * Instead, by enforcing that a @Persistable object must have a default no-args constructor defined, then
     * we force the programmer to ensure that there is a "blank" form of the persistable object that can be
     * dynamically instantiated and then for which Redis data can be loaded into.
     * @return Dynamically generated object from default constructor
     * @throws InvocationTargetException If reflection cannot invoke a method
     * @throws InstantiationException If reflection cannot trigger dynamic object instantiation
     * @throws IllegalAccessException If reflection cannot access the specified field or method
     * @throws NoSuchMethodException If reflection cannot find the specified method
     */
    public Object generateInstance() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        // When generating the reflected object attributes, we enforced that the default constructor exists.
        // Thus, we cannot actually get a NoSuchMethodException.
        Constructor<?> constructor = thisClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object newInstance = constructor.newInstance();
        constructor.setAccessible(false);

        return newInstance;
    }

    /**
     * Set the fields in the provided object using the reflected persistence object attributes in
     * this class and a particular Jedis session to load from.
     * @param session Persistence session that should be used to load the data into the specified object
     * @param object Object instance represented by this reflected attributes class to load persistence data into
     * @param jedisSession Jedis session to load stored persistence data from
     * @throws Exception Reflection may generate exception
     */
    public void setFields(Session session, Object object, Jedis jedisSession) throws Exception {
        // If objId is not set, then PersistenceException will be thrown (refer to ReflectedObjectAttributes class).
        String objId = getId(object);
        Map<String, String> objPairs = jedisSession.hgetAll(objId);

        // Set non-list fields
        for (Field field : fields) {
            String strValue = objPairs.get(field.getName());
            Object origValue = strValue;

            // Given HW2 assumptions, we know that the only types we have to support for persistence
            // are String and Integer types.
            if (field.getType() == Integer.class) {
                origValue = Integer.valueOf(strValue);
            }

            // We must first temporarily set field to be accessible
            field.setAccessible(true);
            field.set(object, origValue);
            // Restore access modifier
            field.setAccessible(false);
        }

        // Set list fields
        for (Map.Entry<Field, ReflectedObjectAttributes> fieldPair : listFieldAttrs.entrySet()) {
            Field field = fieldPair.getKey();
            ReflectedObjectAttributes fieldAttrs = fieldPair.getValue();

            // Given HW2 assumptions, it is guaranteed that the list container is always a List<> type.
            // Thus, we can initialize the List as a LinkedList<Object>.
            List<Object> objs = new LinkedList<Object>();
            // Retrieve comma-separated array of ids
            if (!objPairs.get(field.getName()).isEmpty()) {
                String[] listString = objPairs.get(field.getName()).split(",");
                // Recursively load all list ids into new objects
                for (String id : listString) {
                    Object newListObj = fieldAttrs.generateInstance();

                    // Set id on list object
                    try {
                        fieldAttrs.setId(newListObj, id);
                    } catch (IllegalArgumentException ex) {
                        fieldAttrs.setId(newListObj, Integer.valueOf(id));
                    }

                    // Extra credit feature:
                    // We defer the session.load() call to the proxy intercept method if the reflected object attributes
                    // specifies that the represented object should be lazy loaded.
                    if (fieldAttrs.getIsLazyLoad()) {
                        newListObj = PersistableProxy.generateProxy(session, fieldAttrs, id, newListObj);
                    } else {
                        // Bi-recursion: we call load() from the provided Session object to recursively load the new list object.
                        newListObj = session.load(newListObj, fieldAttrs);
                    }
                    objs.add(newListObj);
                }
            }

            // We must first temporarily set field to be accessible
            field.setAccessible(true);
            field.set(object, objs);
            // Restore access modifier
            field.setAccessible(false);
        }
    }
}
