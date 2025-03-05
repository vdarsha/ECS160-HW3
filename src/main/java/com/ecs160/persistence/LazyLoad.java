package com.ecs160.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * Annotation for if a field also labelled as @PersistableListField should further be lazy loaded
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface LazyLoad { }
