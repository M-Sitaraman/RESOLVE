/*
 * MTType.java
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
package edu.clemson.cs.r2jt.typeandpopulate;

import java.util.List;
import java.util.Map;

import edu.clemson.cs.r2jt.typereasoning.TypeGraph;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>The parent class of all mathematical types.</p>
 */
public abstract class MTType {

    protected final TypeGraph myTypeGraph;

    private final Set<Object> myKnownAlphaEquivalencies = new HashSet<Object>();
    private final Map<MTType, Map<String, MTType>> myKnownSyntacticSubtypeBindings =
            new HashMap<MTType, Map<String, MTType>>();

    /**
     * <p>Allows us to detect if we're getting into an equals-loop.</p>
     */
    private int myEqualsDepth = 0;

    public MTType(TypeGraph typeGraph) {
        myTypeGraph = typeGraph;
    }

    public TypeGraph getTypeGraph() {
        return myTypeGraph;
    }

    public abstract void accept(TypeVisitor v);

    public abstract void acceptOpen(TypeVisitor v);

    public abstract void acceptClose(TypeVisitor v);

    public abstract List<MTType> getComponentTypes();

    public abstract MTType withComponentReplaced(int index, MTType newType);

    public MTType withComponentsReplaced(Map<Integer, MTType> newTypes) {

        MTType target = this;
        for (Map.Entry<Integer, MTType> entry : newTypes.entrySet()) {
            target =
                    target.withComponentReplaced(entry.getKey(), entry
                            .getValue());
        }

        return target;
    }

    /**
     * <p>Returns <code>true</code> <strong>iff</strong> <code>o</code> is an
     * </code>MTType</code> that is <em>alpha equivalent</em> to this type.
     * I.e., it must be exactly the same with the sole exception that 
     * quantified variables may have different names if they are otherwise
     * identical.  So, BigUnion{t : MType}{t} <code>equals</code>
     * BigUnion{r : MType}{r}.  However, BigUnion{t : MType}{t} <em>does 
     * not</em> <code>equals</code> BigUnion{r : Power(MType)}{r}.</p>
     * 
     * @param o The object to compare with this <code>MTType</code>.
     * 
     * @return <code>true</code> <strong>iff</strong> this <code>MTType</code>
     *		is alpha equivalent to <code>o</code>.
     */
    @Override
    public final boolean equals(Object o) {
        myEqualsDepth++;

        boolean result;

        if (this == o) {
            result = true;
        }
        else {
            //We only check our cache if we're at the first level of equals
            //comparison to avoid an infinite recursive loop
            result =
                    (myEqualsDepth == 1)
                            && myKnownAlphaEquivalencies.contains(o);

            if (!result) {
                try {
                    //All 'equals' logic should be put into AlphaEquivalencyChecker! 
                    //Don't override equals!
                    AlphaEquivalencyChecker alphaEq =
                            myTypeGraph.threadResources.alphaChecker;
                    alphaEq.reset();

                    alphaEq.visit(this, (MTType) o);

                    result = alphaEq.getResult();
                }
                catch (ClassCastException cce) {
                    result = false;
                }

                //We only cache our answer at the first level to avoid an 
                //infinite equals loop
                if ((myEqualsDepth == 1) && result) {
                    myKnownAlphaEquivalencies.add(o);
                }
            }
        }

        myEqualsDepth--;

        return result;
    }

    public final Map<String, MTType> getSyntacticSubtypeBindings(MTType o)
            throws NoSolutionException {

        Map<String, MTType> result;

        if (myKnownSyntacticSubtypeBindings.containsKey(o)) {
            result = myKnownSyntacticSubtypeBindings.get(o);
        }
        else {
            SyntacticSubtypeChecker checker =
                    new SyntacticSubtypeChecker(myTypeGraph);

            try {
                checker.visit(this, o);
            }
            catch (RuntimeException e) {

                Throwable cause = e;
                while (cause != null
                        && !(cause instanceof TypeMismatchException)) {
                    cause = cause.getCause();
                }

                if (cause == null) {
                    throw e;
                }

                throw NoSolutionException.INSTANCE;
            }

            result = Collections.unmodifiableMap(checker.getBindings());
            myKnownSyntacticSubtypeBindings.put(o, result);
        }

        return result;
    }

    public final boolean isSubtypeOf(MTType o) {
        return myTypeGraph.isSubtype(this, o);
    }

    public final boolean isSyntacticSubtypeOf(MTType o) {

        boolean result;

        try {
            getSyntacticSubtypeBindings(o);
            result = true;
        }
        catch (NoSolutionException e) {
            result = false;
        }

        return result;
    }

    public final boolean isBoolean() {
        return (myTypeGraph.BOOLEAN == this);
    }

    public final boolean alphaEquivalentTo(MTType t) {
        return this.equals(t);
    }

    public final MTType getCopyWithVariablesSubstituted(
            Map<String, MTType> substitutions) {
        VariableReplacingVisitor renamer =
                new VariableReplacingVisitor(substitutions);
        accept(renamer);
        return renamer.getFinalExpression();
    }

    public Map<String, MTType> bindTo(MTType o, FinalizedScope context)
            throws BindingException {

        BindingVisitor bind = new BindingVisitor(myTypeGraph, context);
        bind.visit(this, o);

        if (!bind.binds()) {
            throw new BindingException(this, o);
        }

        return bind.getBindings();
    }

    public Map<String, MTType> bindTo(MTType o, Map<String, MTType> context)
            throws BindingException {

        BindingVisitor bind = new BindingVisitor(myTypeGraph, context);
        bind.visit(this, o);

        if (!bind.binds()) {
            throw new BindingException(this, o);
        }

        return bind.getBindings();
    }

    public Map<String, MTType> bindTo(MTType template,
            Map<String, MTType> thisContext, Map<String, MTType> templateContext)
            throws BindingException {

        BindingVisitor bind =
                new BindingVisitor(myTypeGraph, thisContext, templateContext);
        bind.visit(this, template);

        if (!bind.binds()) {
            throw new BindingException(this, template);
        }

        return bind.getBindings();
    }

    public MTType getType() {
        //TODO : Each MTType should really contain it's declared type.  I.e.,
        //       if I say "Definition X : Set", I should store that X is
        //       of type Set someplace.  That's not currently available, so for
        //       the moment we say that all types are of type MType, the parent
        //       type of all types.
        return myTypeGraph.CLS;
    }

    /**
     * <p>Returns the object-reference hash.</p>
     */
    public final int objectReferenceHashCode() {
        return super.hashCode();
    }

    @Override
    public final int hashCode() {
        return getHashCode();
    }

    /**
     * <p>This is just a template method to <em>force</em> all concrete 
     * subclasses of <code>MTType</code> to implement <code>hashCode()</code>,
     * as the type resolution algorithm depends on it being implemented 
     * sensibly.</p>
     * 
     * @return A hashcode consistent with <code>equals()</code> and thus
     *     alpha-equivalency.
     */
    public abstract int getHashCode();

    /**
     * <p>Indicates that this type is known to contain only elements <em>that
     * are themselves</em> types.  Practically, this answers the question, "can
     * an instance of this type itself be used as a type?"</p>
     */
    public boolean isKnownToContainOnlyMTypes() {
        return false;
    }

    /**
     * <p>Indicates that every instance of this type is itself known to contain
     * only elements that are types.  Practically, this answers the question,
     * "if a function returns an instance of this type, can that instance itself
     * be said to contain only types?"</p>
     */
    public boolean membersKnownToContainOnlyMTypes() {
        return false;
    }

    private class MTTypeObjectHashWrapper {

        public final MTType type;

        public MTTypeObjectHashWrapper(MTType t) {
            type = t;
        }

        @Override
        public boolean equals(Object o) {
            return type.equals(o);
        }

        @Override
        public int hashCode() {
            return type.objectReferenceHashCode();
        }
    }
}
