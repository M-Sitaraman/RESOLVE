/*
 * TreeWalkerStackVisitor.java
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
package edu.clemson.cs.r2jt.treewalk;

import java.util.Iterator;
import java.util.LinkedList;
import edu.clemson.cs.r2jt.absyn.*;

public abstract class TreeWalkerStackVisitor extends TreeWalkerVisitor {

    private LinkedList<ResolveConceptualElement> myVisitStack =
            new LinkedList<ResolveConceptualElement>();
    private ResolveConceptualElement myParent;

    private void pushParent() {
        myVisitStack.push(myParent);
    }

    private void popParent() {
        myVisitStack.pop();
    }

    protected ResolveConceptualElement getParent() {
        return myVisitStack.peek();
    }

    protected ResolveConceptualElement getAncestor(int index) {
        return myVisitStack.get(index);
    }

    protected int getAncestorSize() {
        return myVisitStack.size();
    }

    protected Iterator<ResolveConceptualElement> getAncestorInterator() {
        return myVisitStack.iterator();
    }

    public void preAnyStack(ResolveConceptualElement data) {}

    public void postAnyStack(ResolveConceptualElement data) {}

    public final void preAny(ResolveConceptualElement data) {
        preAnyStack(data);
        myParent = data;
        pushParent();

    }

    public final void postAny(ResolveConceptualElement data) {
        popParent();
        postAnyStack(data);
    }
}
