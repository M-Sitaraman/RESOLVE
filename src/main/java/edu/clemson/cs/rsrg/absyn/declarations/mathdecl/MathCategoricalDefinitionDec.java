/**
 * MathCategoricalDefinitionDec.java
 * ---------------------------------
 * Copyright (c) 2016
 * RESOLVE Software Research Group
 * School of Computing
 * Clemson University
 * All rights reserved.
 * ---------------------------------
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package edu.clemson.cs.rsrg.absyn.declarations.mathdecl;

import edu.clemson.cs.rsrg.absyn.declarations.Dec;
import edu.clemson.cs.rsrg.absyn.expressions.Exp;
import edu.clemson.cs.rsrg.parsing.data.PosSymbol;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>This is the class for all the mathematical categorical definition declarations
 * that the compiler builds using the ANTLR4 AST nodes.</p>
 *
 * @version 2.0
 */
public class MathCategoricalDefinitionDec extends Dec {

    // ===========================================================
    // Member Fields
    // ===========================================================

    /** <p>All the definitions defined in this categorical definition</p> */
    private final List<MathDefinitionDec> myDefinitions;

    /** <p>The expression relating the mathematical definitions</p> */
    private final Exp myRelatedByExp;

    // ===========================================================
    // Constructors
    // ===========================================================

    public MathCategoricalDefinitionDec(PosSymbol name,
            List<MathDefinitionDec> definitions, Exp relatedByExp) {
        super(name.getLocation(), name);
        myDefinitions = definitions;
        myRelatedByExp = relatedByExp;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public final String asString(int indentSize, int innerIndentInc) {
        StringBuffer sb = new StringBuffer();
        printSpace(indentSize, sb);
        sb.append("Categorical Definition ");
        sb.append(myName.asString(0, innerIndentInc));
        sb.append(" introduces\n");

        Iterator<MathDefinitionDec> it = myDefinitions.iterator();
        while (it.hasNext()) {
            MathDefinitionDec definitionDec = it.next();
            sb.append(definitionDec.getName().asString(
                    indentSize + innerIndentInc, innerIndentInc));
            sb.append(" : ");
            sb.append(definitionDec.getReturnTy().asString(0, innerIndentInc));

            if (it.hasNext()) {
                sb.append(",\n");
            }
        }
        sb.append("\n");

        printSpace(indentSize, sb);
        sb.append("related by\n");
        sb.append(myRelatedByExp.asString(indentSize + innerIndentInc,
                innerIndentInc));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        MathCategoricalDefinitionDec that = (MathCategoricalDefinitionDec) o;

        if (!myDefinitions.equals(that.myDefinitions))
            return false;
        return myRelatedByExp.equals(that.myRelatedByExp);

    }

    /**
     * <p>This method returns all the definitions in this categorical
     * definition.</p>
     *
     * @return A list of {@link MathDefinitionDec} representation objects.
     */
    public final List<MathDefinitionDec> getDefinitions() {
        return myDefinitions;
    }

    /**
     * <p>This method returns all the expression relating all the
     * definitions in this categorical definition.</p>
     *
     * @return An {@link Exp} representation object.
     */
    public final Exp getRelatedByExpression() {
        return myRelatedByExp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        int result = super.hashCode();
        result = 31 * result + myDefinitions.hashCode();
        result = 31 * result + myRelatedByExp.hashCode();
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Categorical Definition ");
        sb.append(myName.toString());
        sb.append(" introduces\n");

        Iterator<MathDefinitionDec> it = myDefinitions.iterator();
        while (it.hasNext()) {
            MathDefinitionDec definitionDec = it.next();
            sb.append("\t");
            sb.append(definitionDec.getName().toString());
            sb.append(" : ");
            sb.append(definitionDec.getReturnTy().toString());

            if (it.hasNext()) {
                sb.append(",\n");
            }
        }
        sb.append("\n");

        sb.append("related by\n");
        sb.append("\t");
        sb.append(myRelatedByExp.toString());
        sb.append("\n");

        return sb.toString();
    }

    // ===========================================================
    // Protected Methods
    // ===========================================================

    /**
     * {@inheritDoc}
     */
    @Override
    protected final MathCategoricalDefinitionDec copy() {
        List<MathDefinitionDec> newDefinitions = new ArrayList<>();
        for (MathDefinitionDec definitionDec : myDefinitions) {
            newDefinitions.add((MathDefinitionDec) definitionDec.clone());
        }

        return new MathCategoricalDefinitionDec(myName.clone(), newDefinitions, myRelatedByExp.clone());
    }
}