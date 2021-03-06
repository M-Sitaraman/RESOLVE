/*
 * DefinitionBody.java
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

import edu.clemson.cs.r2jt.data.Location;

public class DefinitionBody extends ResolveConceptualElement {

    /** The base member. */
    private Exp base;

    /** The hypothesis member. */
    private Exp hypothesis;

    /** The definition member. */
    private Exp definition;

    private boolean isInductive;

    public DefinitionBody(Exp base, Exp hypothesis, Exp definition) {
        this.base = base;
        this.hypothesis = hypothesis;
        this.definition = definition;
        this.isInductive = (base != null);
    }

    public boolean isInductive() {
        return this.isInductive;
    }

    public Location getLocation() {
        Location result;

        if (base == null) {
            result = definition.getLocation();
        }
        else {
            result = base.getLocation();
        }

        return result;
    }

    @Override
    public void accept(ResolveConceptualVisitor v) {
    // TODO Auto-generated method stub
    }

    @Override
    public String asString(int indent, int increment) {
        StringBuffer sb = new StringBuffer();
        if (base != null) {
            sb.append(base.asString(indent + increment, increment));
        }

        if (hypothesis != null) {
            sb.append(hypothesis.asString(indent + increment, increment));
        }

        if (definition != null) {
            sb.append(definition.asString(indent + increment, increment));
        }
        return sb.toString();
    }

    public Exp getBase() {
        return base;
    }

    public Exp getHypothesis() {
        return hypothesis;
    }

    public Exp getDefinition() {
        return definition;
    }

    public void setBase(Exp newBase) {
        base = newBase;
    }

    public void setHypothesis(Exp newHypothesis) {
        hypothesis = newHypothesis;
    }

    public void setDefinition(Exp newDefinition) {
        definition = newDefinition;
    }
}
