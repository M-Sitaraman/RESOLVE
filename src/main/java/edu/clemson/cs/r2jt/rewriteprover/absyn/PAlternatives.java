/*
 * PAlternatives.java
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

import java.util.*;

import edu.clemson.cs.r2jt.absyn.AltItemExp;
import edu.clemson.cs.r2jt.absyn.AlternativeExp;
import edu.clemson.cs.r2jt.typeandpopulate.MTType;
import edu.clemson.cs.r2jt.rewriteprover.immutableadts.ArrayBackedImmutableList;
import edu.clemson.cs.r2jt.rewriteprover.immutableadts.ImmutableList;
import edu.clemson.cs.r2jt.misc.Utils.Mapping;
import edu.clemson.cs.r2jt.misc.RCollections;

public class PAlternatives extends PExp {

    public final List<Alternative> myAlternatives;

    public final PExp myOtherwiseClauseResult;

    public PAlternatives(List<PExp> conditions, List<PExp> results,
            PExp otherwiseClauseResult, MTType type, MTType typeValue) {

        super(
                calculateStructureHash(conditions, results,
                        otherwiseClauseResult), calculateStructureHash(
                        conditions, results, otherwiseClauseResult), type,
                typeValue);

        myAlternatives = new LinkedList<Alternative>();

        sanityCheckConditions(conditions);

        if (conditions.size() != results.size()) {
            throw new IllegalArgumentException("conditions.size() must equal "
                    + "results.size().");
        }

        Iterator<PExp> conditionIter = conditions.iterator();
        Iterator<PExp> resultIter = results.iterator();

        while (conditionIter.hasNext()) {
            myAlternatives.add(new Alternative(conditionIter.next(), resultIter
                    .next()));
        }

        myOtherwiseClauseResult = otherwiseClauseResult;
    }

    public PAlternatives(AlternativeExp alternativeExp) {

        this(getConditions(alternativeExp), getResults(alternativeExp),
                getOtherwiseClauseResult(alternativeExp), alternativeExp
                        .getMathType(), alternativeExp.getMathTypeValue());
    }

    public void accept(PExpVisitor v) {
        v.beginPExp(this);
        v.beginPAlternatives(this);

        v.beginChildren(this);

        boolean first = true;
        for (Alternative alt : myAlternatives) {
            if (!first) {
                v.fencepostPAlternatives(this);
            }

            alt.result.accept(v);
            alt.condition.accept(v);
        }
        v.fencepostPAlternatives(this);

        myOtherwiseClauseResult.accept(v);

        v.endChildren(this);

        v.endPAlternatives(this);
        v.endPExp(this);
    }

    private static List<PExp> getConditions(AlternativeExp alternativeExp) {

        List<PExp> result = new LinkedList<PExp>();
        for (AltItemExp aie : alternativeExp.getAlternatives()) {
            if (aie.getTest() != null) {
                result.add(PExp.buildPExp(aie.getTest()));
            }
        }

        return result;
    }

    private static List<PExp> getResults(AlternativeExp alternativeExp) {

        List<PExp> result = new LinkedList<PExp>();
        for (AltItemExp aie : alternativeExp.getAlternatives()) {
            if (aie.getTest() != null) {
                result.add(PExp.buildPExp(aie.getAssignment()));
            }
        }

        return result;
    }

    private static PExp getOtherwiseClauseResult(AlternativeExp alternativeExp) {

        PExp workingOtherwiseClauseResult = null;

        for (AltItemExp aie : alternativeExp.getAlternatives()) {
            if (workingOtherwiseClauseResult != null) {
                throw new IllegalArgumentException("AlternativeExps with "
                        + "additional alternatives after the 'otherwise' "
                        + "clause are not accepted by the prover. \n\t"
                        + aie.getAssignment() + " appears in such a position.");
            }

            if (aie.getTest() == null) {
                workingOtherwiseClauseResult =
                        PExp.buildPExp(aie.getAssignment());
            }
        }

        return workingOtherwiseClauseResult;
    }

    private void sanityCheckConditions(List<PExp> conditions) {
        for (PExp condition : conditions) {
            if (!condition
                    .typeMatches(condition.getType().getTypeGraph().BOOLEAN)) {
                throw new IllegalArgumentException("AlternativeExps with "
                        + "non-boolean-typed conditions are not accepted "
                        + "by the prover. \n\t" + condition + " has type "
                        + condition.getType());
            }
        }
    }

    private static int calculateStructureHash(List<PExp> conditions,
            List<PExp> results, PExp otherwiseClauseResult) {

        int hash = 0;

        Iterator<PExp> conditionIter = conditions.iterator();
        Iterator<PExp> resultIter = conditions.iterator();

        while (conditionIter.hasNext()) {
            hash *= 31;
            hash += conditionIter.next().structureHash;
            hash *= 34;
            hash += resultIter.next().structureHash;
        }

        return hash;
    }

    private static MTType getResultType(List<PExp> results,
            PExp otherwiseClauseResult) {

        //TODO : This could be made more flexible--if the first alternative
        //       is an N and the second a Z, that shouldn't be an error--the
        //       result type is Z
        PExp prototypeResult = null;

        for (PExp curResult : results) {
            if (prototypeResult == null) {
                prototypeResult = curResult;
            }
            else {
                if (!curResult.typeMatches(prototypeResult)) {
                    throw new IllegalArgumentException("AlternativeExps with "
                            + "results of different types are not accepted by "
                            + "the prover. \n\t" + prototypeResult + " has "
                            + "type " + prototypeResult.getType() + ".\n\t"
                            + curResult + " has type " + curResult.getType()
                            + ".");
                }
            }
        }

        if (!otherwiseClauseResult.typeMatches(prototypeResult)) {
            throw new IllegalArgumentException("AlternativeExps with "
                    + "results of different types are not accepted by "
                    + "the prover. \n\t" + prototypeResult + " has " + "type "
                    + prototypeResult.getType() + ".\n\t"
                    + otherwiseClauseResult + " has type "
                    + otherwiseClauseResult.getType() + ".");
        }

        return prototypeResult.getType();
    }

    @Override
    public PAlternatives withTypeReplaced(MTType t) {

        return new PAlternatives(RCollections.map(myAlternatives,
                UnboxCondition.INSTANCE), RCollections.map(myAlternatives,
                UnboxResult.INSTANCE), myOtherwiseClauseResult, t, myTypeValue);
    }

    @Override
    public PAlternatives withTypeValueReplaced(MTType t) {

        return new PAlternatives(RCollections.map(myAlternatives,
                UnboxCondition.INSTANCE), RCollections.map(myAlternatives,
                UnboxResult.INSTANCE), myOtherwiseClauseResult, myType, t);
    }

    @Override
    public PAlternatives withSubExpressionReplaced(int index, PExp e) {
        List<PExp> newResults =
                RCollections.map(myAlternatives, UnboxResult.INSTANCE);
        List<PExp> newConditions =
                RCollections.map(myAlternatives, UnboxCondition.INSTANCE);
        PExp newOtherwise = myOtherwiseClauseResult;

        if (index < 0 || index > (myAlternatives.size() * 2) + 1) {
            throw new IndexOutOfBoundsException("" + index);
        }
        else {
            if (index % 2 == 0) {
                index /= 2;
                if (index < myAlternatives.size()) {
                    newResults.set(index, e);
                }
                else {
                    newOtherwise = e;
                }
            }
            else {
                newConditions.set(index / 2, e);
            }
        }

        return new PAlternatives(newConditions, newResults, newOtherwise,
                myType, myTypeValue);
    }

    @Override
    public ImmutableList<PExp> getSubExpressions() {
        List<PExp> exps = new LinkedList<PExp>();

        for (Alternative a : myAlternatives) {
            exps.add(a.result);
            exps.add(a.condition);
        }

        exps.add(myOtherwiseClauseResult);

        return new ArrayBackedImmutableList<PExp>(exps);
    }

    @Override
    public PExpSubexpressionIterator getSubExpressionIterator() {
        return new PAlternativesIterator();
    }

    @Override
    public boolean isObviouslyTrue() {
        boolean result = true;

        for (Alternative a : myAlternatives) {
            result &= a.result.isObviouslyTrue();
        }

        return result && myOtherwiseClauseResult.isObviouslyTrue();
    }

    @Override
    protected void splitIntoConjuncts(List<PExp> accumulator) {
        accumulator.add(this);
    }

    @Override
    public PExp flipQuantifiers() {
        throw new UnsupportedOperationException("This method has not yet "
                + "been implemented.");
    }

    @Override
    public void bindTo(PExp target, Map<PExp, PExp> accumulator)
            throws BindingException {

        if (!(target instanceof PAlternatives)) {
            throw BINDING_EXCEPTION;
        }

        PAlternatives targetAsPAlternatives = (PAlternatives) target;

        if (myAlternatives.size() != targetAsPAlternatives.myAlternatives
                .size()) {
            throw BINDING_EXCEPTION;
        }

        Iterator<Alternative> thisAlternatives = myAlternatives.iterator();
        Iterator<Alternative> targetAlternatives =
                targetAsPAlternatives.myAlternatives.iterator();

        Alternative curThisAlt, curTargetAlt;
        while (thisAlternatives.hasNext()) {
            curThisAlt = thisAlternatives.next();
            curTargetAlt = targetAlternatives.next();

            curThisAlt.result.bindTo(curTargetAlt.result, accumulator);
            curThisAlt.condition.bindTo(curTargetAlt.condition, accumulator);
        }

        myOtherwiseClauseResult.bindTo(
                targetAsPAlternatives.myOtherwiseClauseResult, accumulator);
    }

    @Override
    public PExp substitute(Map<PExp, PExp> substitutions) {
        PExp retval;

        if (substitutions.containsKey(this)) {
            retval = substitutions.get(this);
        }
        else {
            List<PExp> conditions = new ArrayList<PExp>();
            List<PExp> results = new ArrayList<PExp>();
            PExp otherwise = myOtherwiseClauseResult.substitute(substitutions);
            for (Alternative a : myAlternatives) {
                conditions.add(a.condition.substitute(substitutions));
                results.add(a.result.substitute(substitutions));
            }
            retval =
                    new PAlternatives(conditions, results, otherwise,
                            getType(), getTypeValue());
        }

        return retval;
    }

    @Override
    public boolean containsName(String name) {
        boolean result = false;

        for (Alternative a : myAlternatives) {
            result |=
                    a.condition.containsName(name)
                            || a.result.containsName(name);
        }

        return result || myOtherwiseClauseResult.containsName(name);
    }

    @Override
    public Set<String> getSymbolNamesNoCache() {
        Set<String> result = new HashSet<String>();

        for (Alternative a : myAlternatives) {
            result.addAll(a.condition.getSymbolNames());
            result.addAll(a.result.getSymbolNames());
        }

        result.addAll(myOtherwiseClauseResult.getSymbolNames());

        return result;
    }

    @Override
    public Set<PSymbol> getQuantifiedVariablesNoCache() {
        Set<PSymbol> result = new HashSet<PSymbol>();

        for (Alternative a : myAlternatives) {
            result.addAll(a.condition.getQuantifiedVariables());
            result.addAll(a.result.getQuantifiedVariables());
        }

        result.addAll(myOtherwiseClauseResult.getQuantifiedVariables());

        return result;
    }

    @Override
    public List<PExp> getFunctionApplicationsNoCache() {
        List<PExp> result = new LinkedList<PExp>();

        for (Alternative a : myAlternatives) {
            result.addAll(a.condition.getFunctionApplications());
            result.addAll(a.result.getFunctionApplications());
        }

        result.addAll(myOtherwiseClauseResult.getFunctionApplications());

        result.add(this);

        return result;
    }

    @Override
    public boolean containsExistential() {
        boolean result = false;

        for (Alternative a : myAlternatives) {
            result |= a.condition.containsExistential();
            result |= a.result.containsExistential();
        }

        return result || myOtherwiseClauseResult.containsExistential();
    }

    @Override
    public boolean isEquality() {
        return false;
    }

    @Override
    public boolean isLiteral() {
        return false;
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    @Override
    public String getTopLevelOperation() {
        return "{{";
    }

    private static class UnboxResult implements Mapping<Alternative, PExp> {

        public final static UnboxResult INSTANCE = new UnboxResult();

        public PExp map(Alternative a) {
            return a.result;
        }
    }

    private static class UnboxCondition implements Mapping<Alternative, PExp> {

        public final static UnboxCondition INSTANCE = new UnboxCondition();

        public PExp map(Alternative a) {
            return a.condition;
        }
    }

    public static class Alternative {

        public final PExp condition;
        public final PExp result;

        public Alternative(PExp condition, PExp result) {
            this.condition = condition;
            this.result = result;
        }
    }

    private class PAlternativesIterator implements PExpSubexpressionIterator {

        private int myCurAlternativeNum;

        //These variables combine to tell you what the last thing returned was:
        //if myReturnedOtherwiseFlag == true, the last thing returned was the
        //otherwise clause and there's nothing left to return.  Otherwise, if
        //myCurAlternative == null, the last thing returned was the condition
        //of the (myCurAlternativeNum)th element.  Otherwise (if 
        //myCurAlternative != null), the last thing returned was the result of
        //the (myCurAlternativeNum)th element.
        private final Iterator<Alternative> myAlternativesIter;
        private Alternative myCurAlternative;
        private boolean myReturnedOtherwiseFlag = false;

        public PAlternativesIterator() {
            myAlternativesIter = myAlternatives.iterator();
        }

        @Override
        public boolean hasNext() {
            return (myCurAlternative != null) || (myAlternativesIter.hasNext())
                    || !myReturnedOtherwiseFlag;
        }

        @Override
        public PExp next() {
            PExp result;

            if (myCurAlternative == null) {
                if (myAlternativesIter.hasNext()) {
                    myCurAlternativeNum++;

                    myCurAlternative = myAlternativesIter.next();
                    result = myCurAlternative.result;
                }
                else if (!myReturnedOtherwiseFlag) {
                    myReturnedOtherwiseFlag = true;
                    result = myOtherwiseClauseResult;
                }
                else {
                    throw new NoSuchElementException();
                }
            }
            else {
                result = myCurAlternative.condition;
                myCurAlternative = null;
            }

            return result;
        }

        @Override
        public PAlternatives replaceLast(PExp newExpression) {
            List<PExp> newConditions =
                    RCollections.map(myAlternatives, UnboxCondition.INSTANCE);
            List<PExp> newResults =
                    RCollections.map(myAlternatives, UnboxResult.INSTANCE);
            PExp newOtherwise = myOtherwiseClauseResult;

            if (myReturnedOtherwiseFlag) {
                newOtherwise = newExpression;
            }
            else {
                if (myCurAlternative == null) {
                    newConditions.set(myCurAlternativeNum, newExpression);
                }
                else {
                    newResults.set(myCurAlternativeNum, newExpression);
                }
            }

            return new PAlternatives(newConditions, newResults, newOtherwise,
                    myType, myTypeValue);
        }
    }

    public String toSMTLIB(Map<String, MTType> typeMap) {
        return "not implemented";
    }
}
