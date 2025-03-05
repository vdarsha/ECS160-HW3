package com.ecs160.persistence;

import java.lang.reflect.Field;
import java.util.List;

/*
 * Stores a Field of an object list, reflected object attributes for that field, and the corresponding
 * actual list object into one structure.
 * Useful for storing this triplet in a collection.
 */
public class ListFieldPair {
    private final Field field;
    private final ReflectedObjectAttributes listObjAttrs;
    private final List<Object> objList;

    /**
     * Instantiate a ListFieldPair structure
     * @param field Field to store
     * @param listObjAttrs reflected object attributes to store
     * @param objList List object to store
     */
    public ListFieldPair(Field field, ReflectedObjectAttributes listObjAttrs, List<Object> objList) {
        this.field = field;
        this.listObjAttrs = listObjAttrs;
        this.objList = objList;
    }

    /**
     * Getter for field
     * @return field
     */
    public Field getField() {
        return field;
    }

    /**
     * Getter for reflected object attributes
     * @return reflected object attributes
     */
    public ReflectedObjectAttributes getListObjAttrs() {
        return listObjAttrs;
    }

    /**
     * Getter for Object list
     * @return Object list
     */
    public List<Object> getObjList() {
        return objList;
    }
}
