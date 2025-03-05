package com.ecs160.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Annotation to specify a list field in a class as persistable
 * The "className" field specifies the fully-qualified class name of the item to be stored
 * in the list.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistableListField {
    String className();
}
