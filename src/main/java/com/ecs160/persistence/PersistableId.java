package com.ecs160.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Annotation to specify a field in a class as the key for storage during persistence
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistableId { }
