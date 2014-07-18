/**
 * OutputVCs.java
 * ---------------------------------
 * Copyright (c) 2014
 * RESOLVE Software Research Group
 * School of Computing
 * Clemson University
 * All rights reserved.
 * ---------------------------------
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */
package edu.clemson.cs.r2jt.vcgeneration;

/*
 * Libraries
 */
import edu.clemson.cs.r2jt.absyn.Exp;
import edu.clemson.cs.r2jt.data.Location;
import edu.clemson.cs.r2jt.init.CompileEnvironment;
import edu.clemson.cs.r2jt.proving.Conjuncts;
import edu.clemson.cs.r2jt.proving.absyn.PExp;
import edu.clemson.cs.r2jt.proving2.Antecedent;
import edu.clemson.cs.r2jt.proving2.Consequent;
import edu.clemson.cs.r2jt.proving2.VC;
import edu.clemson.cs.r2jt.vcgeneration.vcs.VCCollector;
import edu.clemson.cs.r2jt.vcgeneration.vcs.VerificationCondition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * TODO: Write a description of this module
 */
public class OutputVCs {

    // ===========================================================
    // Global Variables
    // ===========================================================

    /**
     * <p>The current compile environment used throughout
     * the compiler.</p>
     */
    private CompileEnvironment myInstanceEnvironment;

    /**
     * <p>A list that will be built up with <code>AssertiveCode</code>
     * objects, each representing a VC or group of VCs that must be
     * satisfied to verify a parsed program.</p>
     */
    private Collection<AssertiveCode> myFinalAssertiveCode;

    /**
     * <p>A list of final immutable VCs for this module.</p>
     */
    private List<VC> myFinalImmutableVCs;

    /**
     * <p>A map that stores vc names. </p>
     */
    private Map<String, List<Location>> myVCDetails;

    /**
     * <p>This string buffer holds all the steps
     * the VC generator takes to generate VCs.</p>
     */
    private StringBuffer myVCSteps;

    // ===========================================================
    // Constructors
    // ===========================================================

    public OutputVCs(final CompileEnvironment env,
            Collection<AssertiveCode> assertiveCode, StringBuffer steps) {
        myInstanceEnvironment = env;
        myFinalAssertiveCode = assertiveCode;
        myVCDetails = new HashMap<String, List<Location>>();
        myVCSteps = steps;

        // Convert to each format
        proverVCs();
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /**
     * <p>Returns the list of Immutable VCs for the in house provers.</p>
     *
     * @return List of Immutable VCs.
     */
    public List<VC> getProverOutput() {
        return myFinalImmutableVCs;
    }

    /**
     * <p>Outputs the results from the VC Generator to a file.</p>
     *
     * @param filename Name of the output file.
     */
    public void outputToFile(String filename) {
        try {
            FileWriter w = new FileWriter(new File(filename));
            w.write(buildHeaderComment());
            w.write("\n=================================");
            w.write(" VC(s): ");
            w.write("=================================\n");
            w.write("\n");
            w.write(humanReadableVCs());
            w.write(myVCSteps.toString());
            w.flush();
            w.close();
        }
        catch (IOException ex) {
            System.err.println("File I/O error when writing: " + filename);
        }
    }

    // ===========================================================
    // Private Methods
    // ===========================================================

    /**
     * <p>Builds a comment header to identify VC files generated
     * by the compiler and from which RESOLVE source file the generated
     * file is derived.</p>
     */
    private String buildHeaderComment() {
        return "VCs for " + myInstanceEnvironment.getTargetFile().getName()
                + " generated " + new Date() + "\n";
    }

    /**
     * <p>Method to convert each VC to its immutable format.</p>
     *
     * @param vc The original VC.
     *
     * @return The immutable form of the VC.
     */
    private VC convertToImmutableVC(VerificationCondition vc) {
        List<PExp> newAntecedents = new LinkedList<PExp>();
        List<PExp> newConsequents = new LinkedList<PExp>();
        List<Location> locationList = new LinkedList<Location>();

        // Antecedents (Givens)
        Conjuncts oldAntecedents = vc.getAntecedents();
        for (Exp a : oldAntecedents) {
            newAntecedents.add(PExp.buildPExp(a));
        }

        // Consequents (Goals)
        Conjuncts oldConsequents = vc.getConsequents();
        for (Exp c : oldConsequents) {
            newConsequents.add(PExp.buildPExp(c));
            locationList.add(c.getLocation());
        }

        // Immutable VC
        VC retval =
                new VC(vc.getName(), new Antecedent(newAntecedents),
                        new Consequent(newConsequents));

        // Stores the location details in our Map
        myVCDetails.put(retval.getName(), locationList);

        return retval;
    }

    /**
     * <p>This method converts all <code>AssertiveCode</code> into
     * the format used by the output handler and our in house provers.</p>
     */
    private String humanReadableVCs() {
        String finalVCs = "";

        // Convert to human readable format
        for (VC vc : myFinalImmutableVCs) {
            List<Location> locationList = myVCDetails.get(vc.getName());
            Antecedent antecedent = vc.getAntecedent();
            Consequent consequent = vc.getConsequent();

            // Add vc details to string
            finalVCs += ("VC " + vc.getName() + "\n\n");

            // Location details
            Location loc = locationList.get(0);
            finalVCs += (loc.getDetails() + ": " + loc.toString() + "\n\n");

            // Goals
            finalVCs += ("Goal(s):\n\n" + consequent.toString() + "\n");

            // Givens
            finalVCs += "Given(s):\n\n";
            int numAntecedent = antecedent.size();
            for (int i = 0; i < numAntecedent; i++) {
                finalVCs +=
                        ((i + 1) + ". " + antecedent.get(i).toString() + "\n");
            }
            finalVCs += "\n";
        }

        return finalVCs;
    }

    /**
     * <p>This method converts all <code>AssertiveCode</code> into
     * the format used by the output handler and our in house provers.</p>
     */
    private void proverVCs() {
        // Convert to an iterable list of <code>VerificationCondition</code>
        Iterable<VerificationCondition> vcsToProve =
                new VCCollector(myFinalAssertiveCode);

        // Make the VCs immutable
        myFinalImmutableVCs = new LinkedList<VC>();
        for (VerificationCondition originalVC : vcsToProve) {
            myFinalImmutableVCs.add(convertToImmutableVC(originalVC));
        }
    }
}
