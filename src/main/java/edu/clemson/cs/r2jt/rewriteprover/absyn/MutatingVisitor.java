/*
 * MutatingVisitor.java
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
package edu.clemson.cs.r2jt.rewriteprover.absyn;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * TODO : MutatingVisitor tends to crash if you use it for multiple sequential
 * traversals.  Always creating a new instance of the visitor is a workaround
 * for now.
 */
public class MutatingVisitor extends BoundVariableVisitor {

    private LinkedList<Integer> myIndices = new LinkedList<Integer>();
    private PExp myRoot;
    private LinkedList<Map<Integer, PExp>> myChangesAtLevel =
            new LinkedList<Map<Integer, PExp>>();

    private PExp myClosingType;

    protected PExp myFinalExpression;

    public PExp getFinalPExp() {
        return myFinalExpression;
    }

    @Override
    public final void beginPExp(PExp e) {

        if (myRoot == null) {
            myRoot = e;
            myFinalExpression = myRoot;
        }

        myIndices.push(0); //We start at the zeroth child
        myChangesAtLevel.push(new HashMap<Integer, PExp>());

        mutateBeginPExp(e);
    }

    protected boolean atRoot() {
        return (myIndices.size() == 1);
    }

    public void mutateBeginPExp(PExp e) {}

    public void mutateEndPExp(PExp e) {}

    public void replaceWith(PExp replacement) {
        if (myIndices.size() == 1) {
            //We're the root
            myFinalExpression = replacement;
        }
        else {
            myChangesAtLevel.get(1).put(myIndices.get(1), replacement);
        }
    }

    protected final PExp getTransformedVersion() {
        return myClosingType;
    }

    @Override
    public final void endChildren(PExp e) {
        myClosingType = e;

        Map<Integer, PExp> changes = myChangesAtLevel.peek();
        if (!changes.isEmpty()) {
            myClosingType = e.withSubExpressionsReplaced(changes);
            replaceWith(myClosingType);
        }

        mutateEndChildren(e);
    }

    public void mutateEndChildren(PExp t) {}

    @Override
    public final void endPExp(PExp e) {
        mutateEndPExp(e);

        //We're not visiting any more children at this level (because the
        //level just ended!)
        myIndices.pop();
        myChangesAtLevel.pop();

        //If I'm the root, there's no chance I have any siblings
        if (e != myRoot) {
            //Increment to the next potential child index
            int i = myIndices.pop();

            myIndices.push(i + 1);
        }
    }
}
