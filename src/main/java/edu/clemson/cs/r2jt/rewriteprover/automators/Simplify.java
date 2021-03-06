/*
 * Simplify.java
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
package edu.clemson.cs.r2jt.rewriteprover.automators;

import edu.clemson.cs.r2jt.rewriteprover.absyn.PExp;
import edu.clemson.cs.r2jt.rewriteprover.applications.Application;
import edu.clemson.cs.r2jt.rewriteprover.model.Consequent;
import edu.clemson.cs.r2jt.rewriteprover.model.LocalTheorem;
import edu.clemson.cs.r2jt.rewriteprover.model.PerVCProverModel;
import edu.clemson.cs.r2jt.rewriteprover.transformations.EliminateTrueConjunctInConsequent;
import edu.clemson.cs.r2jt.rewriteprover.transformations.ReplaceSymmetricEqualityWithTrueInConsequent;
import edu.clemson.cs.r2jt.rewriteprover.transformations.ReplaceTheoremInConsequentWithTrue;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

/**
 * <p>The <code>Simplify</code> automator turns consequents that appear as
 * givens and symmetric equalities into "true" and eliminates true conjuncts 
 * until no more work can be done.</p>
 */
public class Simplify implements Automator {

    public static final Simplify INSTANCE = new Simplify();

    private Simplify() {

    }

    @Override
    public void step(Deque<Automator> stack, PerVCProverModel model) {
        //Turn local theorems in the consequent into true
        boolean replacedTheorem = simplifyTheorem(model);

        if (!replacedTheorem) {
            //Turn symmetric equalities into true
            Iterator<Application> symmetricEqualities =
                    ReplaceSymmetricEqualityWithTrueInConsequent.INSTANCE
                            .getApplications(model);

            if (symmetricEqualities.hasNext()) {
                symmetricEqualities.next().apply(model);
            }
            else {
                //Eliminate true conjuncts
                Iterator<Application> trueConjuncts =
                        EliminateTrueConjunctInConsequent.INSTANCE
                                .getApplications(model);

                if (trueConjuncts.hasNext()) {
                    trueConjuncts.next().apply(model);
                }
                else {
                    stack.pop();
                }
            }
        }
    }

    private boolean simplifyTheorem(PerVCProverModel model) {
        //As an optimization we first make sure there's something to find...
        Set<PExp> localTheoremSet = model.getLocalTheoremSet();
        Iterator<Consequent> consequents = model.getConsequentList().iterator();
        boolean foundMatch = false;
        Consequent c = null;
        while (consequents.hasNext() && !foundMatch) {
            c = consequents.next();

            foundMatch = localTheoremSet.contains(c.getExpression());
        }

        if (foundMatch) {
            //Now we actually go find it
            LocalTheorem t = null;
            Iterator<LocalTheorem> theorems =
                    model.getLocalTheoremList().iterator();

            LocalTheorem curTheorem;
            while (t == null && theorems.hasNext()) {
                curTheorem = theorems.next();

                if (curTheorem.getAssertion().equals(c.getExpression())) {
                    t = curTheorem;
                }
            }

            if (t == null) {
                throw new RuntimeException("uhhh...?");
            }

            new ReplaceTheoremInConsequentWithTrue(t).getApplications(model)
                    .next().apply(model);
        }

        return foundMatch;
    }
}
