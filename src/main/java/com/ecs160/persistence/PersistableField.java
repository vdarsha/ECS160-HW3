package com.ecs160.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Annotation to enable a class field as persistable
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistableField { }
