package com.ecs160.persistence;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Method;

/*
 * Class provides a static proxy generation method,
 * which is used to enable lazy loading on a class labelled as @Persistable with a list field labelled
 * with @LazyLoad and @PersistableListField.
 */
public class PersistableProxy {
    /**
     * Dynamically create a proxy around the given persistable object, that is an item within a persistable list field.
     * @param session persistence session that should be captured and then later called for session.load() if a getter on this persisted class is invoked.
     * @param fieldAttrs reflected object attributes for the given object class
     * @param id retrieved id field value for the given object. This is the only field that is immediately retrieved from Redis.
     * @param newListObj The provided persistable object, which is also an element of some parent persistable list field.
     * @return Proxy-wrapped, persistable and lazy loaded object
     * @throws Exception Reflection supports throwing exceptions if dynamic actions such as object creation fail
     */
    public static Object generateProxy(Session session, ReflectedObjectAttributes fieldAttrs, String id, Object newListObj) throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(newListObj.getClass());
        Class<?> proxyClass = factory.createClass();

        // In ReflectedObjectAttributes we verify that the target class has a default constructor
        Object proxyAttrs = proxyClass.getDeclaredConstructor().newInstance();
        // Set id on list object
        // Field is either a String or an Integer, according to HW2 assumptions
        try {
            fieldAttrs.setId(proxyAttrs, id);
        } catch (IllegalArgumentException ex) {
            fieldAttrs.setId(proxyAttrs, Integer.valueOf(id));
        }

        ((ProxyObject) proxyAttrs).setHandler(new MethodHandler() {
            boolean isFullyLoaded = false;

            @Override
            public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Exception {
                if ((!isFullyLoaded) && thisMethod.getName().contains("get")) {
                    // Only load all the non-id attributes from Session instance if a getter method (obeying Java Bean convention) is requested,
                    // which may be attempting to access a persistable field.
                    session.load(self, fieldAttrs);
                    isFullyLoaded = true;
                }

                return proceed.invoke(self, args);
            }
        });

        return proxyAttrs;
    }
}
