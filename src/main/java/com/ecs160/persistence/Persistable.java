package com.ecs160.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Annotation to enable persistence for a class
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Persistable { }
