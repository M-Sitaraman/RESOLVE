/*
 * GenericTypeVariableDeclRule.java
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
package edu.clemson.cs.rsrg.vcgeneration.proofrules.declaration;

import edu.clemson.cs.rsrg.absyn.declarations.variabledecl.VarDec;
import edu.clemson.cs.rsrg.absyn.statements.AssumeStmt;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.AbstractProofRuleApplication;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.ProofRuleApplication;
import edu.clemson.cs.rsrg.vcgeneration.utilities.AssertiveCodeBlock;
import edu.clemson.cs.rsrg.vcgeneration.utilities.Utilities;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

/**
 * <p>This class contains the logic for a variable declaration
 * rule with a generic program type.</p>
 *
 * @author Yu-Shan Sun
 * @version 1.0
 */
public class GenericTypeVariableDeclRule extends AbstractProofRuleApplication
        implements
            ProofRuleApplication {

    // ===========================================================
    // Member Fields
    // ===========================================================

    /** <p>The variable declaration we are applying the rule to.</p> */
    private final VarDec myVarDec;

    // ===========================================================
    // Constructors
    // ===========================================================

    /**
     * <p>This creates a new application for a variable declaration
     * rule with a generic program type.</p>
     *
     * @param varDec The variable declaration we are applying the
     *               rule to.
     * @param block The assertive code block that the subclasses are
     *              applying the rule to.
     * @param stGroup The string template group we will be using.
     * @param blockModel The model associated with {@code block}.
     */
    public GenericTypeVariableDeclRule(VarDec varDec, AssertiveCodeBlock block,
            STGroup stGroup, ST blockModel) {
        super(block, stGroup, blockModel);
        myVarDec = varDec;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /**
     * <p>This method applies the {@code Proof Rule}.</p>
     */
    @Override
    public final void applyRule() {
        // Create an "Is_Initial" predicate using the generic variable declaration and
        // add it to the assertive code block.
        AssumeStmt initAssumeStmt =
                new AssumeStmt(myVarDec.getLocation(), Utilities.createInitExp(
                        myVarDec,
                        myCurrentAssertiveCodeBlock.getTypeGraph().BOOLEAN),
                        false);
        myCurrentAssertiveCodeBlock.addStatement(initAssumeStmt);

        // Add this as a free variable
        myCurrentAssertiveCodeBlock.addFreeVar(Utilities.createVarExp(myVarDec
                .getLocation(), null, myVarDec.getName(), myVarDec
                .getMathType(), null));

        // Add the different details to the various different output models
        ST stepModel = mySTGroup.getInstanceOf("outputVCGenStep");
        stepModel.add("proofRuleName", getRuleDescription()).add(
                "currentStateOfBlock", myCurrentAssertiveCodeBlock);
        myBlockModel.add("vcGenSteps", stepModel.render());
    }

    /**
     * <p>This method returns a description associated with
     * the {@code Proof Rule}.</p>
     *
     * @return A string.
     */
    @Override
    public final String getRuleDescription() {
        return "Variable Declaration Rule (Generic Program Type)";
    }

}