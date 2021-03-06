/*
 * ResolveConceptualElement.java
 * ---------------------------------
 * Copyright (c) 2017
 * RESOLVE Software Research Group
 * School of Computing
 * Clemson University
 * All rights reserved.
 * ---------------------------------
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package edu.clemson.cs.r2jt.absyn;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.data.AsStringCapability;
import edu.clemson.cs.r2jt.data.Location;
import java.lang.reflect.ParameterizedType;

public abstract class ResolveConceptualElement implements AsStringCapability {

    public abstract void accept(ResolveConceptualVisitor v);

    public abstract String asString(int indent, int increment);

    public abstract Location getLocation();

    /**
     * Builds a sequence of numSpaces spaces and returns that
     * sequence.
     */
    protected void printSpace(int numSpaces, StringBuffer buffer) {
        for (int i = 0; i < numSpaces; ++i) {
            buffer.append(" ");
        }
    }

    public java.util.List<ResolveConceptualElement> getChildren() {

        //We'd like to hit the fields in the order they appear in the class,
        //starting with the most general class and getting more specific.  So,
        //we build a stack of the class hierarchy of this instance
        Deque<Class<?>> hierarchy = new LinkedList<Class<?>>();
        Class<?> curClass = this.getClass();
        do {
            hierarchy.push(curClass);
            curClass = curClass.getSuperclass();
        } while (curClass != ResolveConceptualElement.class);

        List<ResolveConceptualElement> children =
                new List<ResolveConceptualElement>();
        // get a list of all the declared and inherited members of that class
        ArrayList<Field> fields = new ArrayList<Field>();
        while (!hierarchy.isEmpty()) {

            curClass = hierarchy.pop();

            Field[] curFields = curClass.getDeclaredFields();
            for (int i = 0; i < curFields.length; ++i) {
                fields.add(curFields[i]);
            }
            curClass = curClass.getSuperclass();
        }

        // loop through all the class members
        Iterator<Field> iterFields = fields.iterator();
        while (iterFields.hasNext()) {
            Field curField = iterFields.next();

            if (!Modifier.isStatic(curField.getModifiers())) {

                curField.setAccessible(true);
                Class<?> fieldType = curField.getType();

                try {
                    // is this member a ResolveConceptualElement?
                    // if so, add it as a child
                    if (ResolveConceptualElement.class
                            .isAssignableFrom(fieldType)) {
                        //System.out.println("Walking: " + curField.getName());
                        children.add(ResolveConceptualElement.class
                                .cast(curField.get(this)));
                    }
                    // is this member a list of ResolveConceptualElements?
                    // if so, add the elements to the list of children
                    else if (java.util.List.class.isAssignableFrom(fieldType)) {
                        Class<?> listOf =
                                (Class<?>) ((ParameterizedType) curField
                                        .getGenericType())
                                        .getActualTypeArguments()[0];
                        java.util.List<?> fieldList =
                                java.util.List.class.cast(curField.get(this));
                        if (fieldList != null
                                && fieldList.size() > 0
                                && ResolveConceptualElement.class
                                        .isAssignableFrom(listOf)) {
                            children
                                    .add(new VirtualListNode(
                                            this,
                                            curField.getName(),
                                            (java.util.List<ResolveConceptualElement>) fieldList,
                                            (Class<?>) ((ParameterizedType) curField
                                                    .getGenericType())
                                                    .getActualTypeArguments()[0]));
                        }
                    }
                }
                catch (Exception ex) {
                    if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    }
                    else {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        return children;
    }
}
