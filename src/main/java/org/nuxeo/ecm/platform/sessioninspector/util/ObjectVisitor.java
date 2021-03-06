/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     <a href="mailto:grenard@nuxeo.com">Guillaume</a>
 */
package org.nuxeo.ecm.platform.sessioninspector.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.nuxeo.ecm.platform.sessioninspector.jsf.model.ObjectStatistics;
import org.nuxeo.runtime.javaagent.AgentLoader;

/**
 * Build the graph of reference of a given object.
 *
 * @since 5.9.2
 */
public class ObjectVisitor {

    protected final Map<Object, Object> visited = new IdentityHashMap<Object, Object>();

    public Collection<ObjectStatistics> getObjectStatisticsList() {
        Map<String, ObjectStatistics> map = new HashMap<String, ObjectStatistics>();
        for (Object o : visited.keySet()) {
            final String type = o.getClass().getCanonicalName();
            ObjectStatistics os = map.get(type);
            if (os == null) {
                os = new ObjectStatistics(type, 1, AgentLoader.INSTANCE.getSizer().sizeOf(o));
                map.put(type, os);
            } else {
                os.setNbInstance(os.getNbInstance() + 1);
                os.setCumulatedSize(os.getCumulatedSize() + AgentLoader.INSTANCE.getSizer().sizeOf(o));
            }
        }
        return map.values();
    }

    public Map<Object, Object> getVisited() {
        return visited;
    }

    public ObjectVisitor() {
        super();
    }

    public void visit(Object each) {
        if (each == null) {
            return;
        }
        if (visited.containsKey(each)) {
            return;
        }
        visited.put(each, each);
        Class<?> eachType = each.getClass();
        if (eachType.isArray()) {
            if (eachType.getComponentType().isPrimitive()) {
                return;
            }
            for (int i = 0; i < Array.getLength(each); i++) {
                visit(Array.get(each, i));
            }
        } else {
            visit(each, eachType);
        }
    }

    protected void visit(Object each, Class<?> eachType) {
        if (eachType.equals(Object.class)) {
            return;
        }
        for (Field eachField : eachType.getDeclaredFields()) {
            visit(each, eachField);
        }
        visit(each, eachType.getSuperclass());
    }

    protected void visit(Object each, Field eachField) {
        if ((eachField.getModifiers() & Modifier.STATIC) != 0) {
            return;
        }
        if (eachField.getType().isPrimitive()) {
            return;
        }
        boolean oldAccessible = eachField.isAccessible();
        eachField.setAccessible(true);
        try {
            visit(eachField.get(each));
        } catch (Exception e) {
            throw new RuntimeException("Exception trying to access field " + eachField, e);
        } finally {
            eachField.setAccessible(oldAccessible);
        }
    }
}