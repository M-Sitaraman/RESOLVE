/*
 * VCGenerator.java
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
package edu.clemson.cs.rsrg.vcgeneration;

import edu.clemson.cs.rsrg.absyn.clauses.AffectsClause;
import edu.clemson.cs.rsrg.absyn.clauses.AssertionClause;
import edu.clemson.cs.rsrg.absyn.declarations.Dec;
import edu.clemson.cs.rsrg.absyn.declarations.facilitydecl.FacilityDec;
import edu.clemson.cs.rsrg.absyn.declarations.moduledecl.*;
import edu.clemson.cs.rsrg.absyn.declarations.operationdecl.OperationProcedureDec;
import edu.clemson.cs.rsrg.absyn.declarations.operationdecl.ProcedureDec;
import edu.clemson.cs.rsrg.absyn.declarations.paramdecl.ConstantParamDec;
import edu.clemson.cs.rsrg.absyn.declarations.paramdecl.ModuleParameterDec;
import edu.clemson.cs.rsrg.absyn.declarations.typedecl.TypeFamilyDec;
import edu.clemson.cs.rsrg.absyn.declarations.variabledecl.ParameterVarDec;
import edu.clemson.cs.rsrg.absyn.declarations.variabledecl.VarDec;
import edu.clemson.cs.rsrg.absyn.expressions.Exp;
import edu.clemson.cs.rsrg.absyn.expressions.mathexpr.VarExp;
import edu.clemson.cs.rsrg.absyn.expressions.programexpr.ProgramExp;
import edu.clemson.cs.rsrg.absyn.expressions.programexpr.ProgramFunctionExp;
import edu.clemson.cs.rsrg.absyn.items.mathitems.SpecInitFinalItem;
import edu.clemson.cs.rsrg.absyn.items.programitems.EnhancementSpecRealizItem;
import edu.clemson.cs.rsrg.absyn.rawtypes.NameTy;
import edu.clemson.cs.rsrg.absyn.statements.*;
import edu.clemson.cs.rsrg.absyn.statements.MemoryStmt.StatementType;
import edu.clemson.cs.rsrg.init.CompileEnvironment;
import edu.clemson.cs.rsrg.init.flag.Flag;
import edu.clemson.cs.rsrg.init.flag.FlagDependencies;
import edu.clemson.cs.rsrg.parsing.data.Location;
import edu.clemson.cs.rsrg.parsing.data.PosSymbol;
import edu.clemson.cs.rsrg.statushandling.exception.SourceErrorException;
import edu.clemson.cs.rsrg.treewalk.TreeWalkerVisitor;
import edu.clemson.cs.rsrg.typeandpopulate.entry.FacilityEntry;
import edu.clemson.cs.rsrg.typeandpopulate.entry.OperationEntry;
import edu.clemson.cs.rsrg.typeandpopulate.entry.ProgramTypeEntry;
import edu.clemson.cs.rsrg.typeandpopulate.entry.SymbolTableEntry;
import edu.clemson.cs.rsrg.typeandpopulate.exception.NoSuchSymbolException;
import edu.clemson.cs.rsrg.typeandpopulate.mathtypes.MTType;
import edu.clemson.cs.rsrg.typeandpopulate.programtypes.PTType;
import edu.clemson.cs.rsrg.typeandpopulate.query.EntryTypeQuery;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.MathSymbolTable.FacilityStrategy;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.MathSymbolTable.ImportStrategy;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.MathSymbolTableBuilder;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.ModuleScope;
import edu.clemson.cs.rsrg.typeandpopulate.typereasoning.TypeGraph;
import edu.clemson.cs.rsrg.typeandpopulate.utilities.ModuleIdentifier;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.ProofRuleApplication;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.declaration.GenericTypeVariableDeclRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.declaration.KnownTypeVariableDeclRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.declaration.ProcedureDeclRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.statement.AssumeStmtRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.statement.CallStmtRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.statement.ConfirmStmtRule;
import edu.clemson.cs.rsrg.vcgeneration.proofrules.statement.RememberStmtRule;
import edu.clemson.cs.rsrg.vcgeneration.sequents.Sequent;
import edu.clemson.cs.rsrg.vcgeneration.utilities.AssertiveCodeBlock;
import edu.clemson.cs.rsrg.vcgeneration.utilities.Utilities;
import edu.clemson.cs.rsrg.vcgeneration.utilities.VerificationCondition;
import java.util.*;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

/**
 * <p>This class generates verification conditions (VCs) using the provided
 * RESOLVE abstract syntax tree. This visitor logic is implemented as
 * a {@link TreeWalkerVisitor}.</p>
 *
 * @author Heather Keown Harton
 * @author Yu-Shan Sun
 * @author Nighat Yasmin
 * @version 3.0
 */
public class VCGenerator extends TreeWalkerVisitor {

    // ===========================================================
    // Member Fields
    // ===========================================================

    /** <p>The symbol table we are currently building.</p> */
    private final MathSymbolTableBuilder myBuilder;

    /**
     * <p>The current job's compilation environment
     * that stores all necessary objects and flags.</p>
     */
    private final CompileEnvironment myCompileEnvironment;

    /**
     * <p>The module scope for the file we are generating
     * {@code VCs} for.</p>
     */
    private ModuleScope myCurrentModuleScope;

    /**
     * <p>This is the math type graph that indicates relationship
     * between different math types.</p>
     */
    private final TypeGraph myTypeGraph;

    /** <p>The mathematical type Z.</p> */
    private MTType Z;

    // -----------------------------------------------------------
    // Operation Declaration-Related
    // -----------------------------------------------------------

    /**
     * <p>While walking a procedure, this is set to the entry for the operation
     * or {@link OperationProcedureDec} that the procedure is attempting to implement.</p>
     */
    private OperationEntry myCorrespondingOperation;

    /**
     * <p>While walking a procedure, this stores all the local {@link VarDec VarDec's}
     * {@code finalization} specification item if we were able to generate one.</p>
     */
    private final Map<VarDec, SpecInitFinalItem> myVariableSpecFinalItems;

    // -----------------------------------------------------------
    // VC Generation-Related
    // -----------------------------------------------------------

    /**
     * <p>The current {@link AssertiveCodeBlock} that the inner declarations
     * will operate on.</p>
     */
    private AssertiveCodeBlock myCurrentAssertiveCodeBlock;

    /**
     * <p>All the completed {@link AssertiveCodeBlock AssertiveCodeBlocks}
     * that only contain the final {@link Sequent Sequents}.</p>
     */
    private final List<AssertiveCodeBlock> myFinalAssertiveCodeBlocks;

    /**
     * <p>A list that stores all the module level {@code constraint}
     * clauses for the various different declarations.</p>
     */
    private final Map<Dec, List<AssertionClause>> myGlobalConstraints;

    /**
     * <p>A list that stores all the module level {@code requires}
     * clauses.</p>
     */
    private final List<AssertionClause> myGlobalRequires;

    /**
     * <p>All the {@link AssertiveCodeBlock AssertiveCodeBlocks} generated by
     * the various different declaration and statements that still needs more
     * proof rule applications.</p>
     */
    private final Deque<AssertiveCodeBlock> myIncompleteAssertiveCodeBlocks;

    // -----------------------------------------------------------
    // Output-Related
    // -----------------------------------------------------------

    /** <p>String template for the each of the assertive code blocks.</p> */
    private final Map<AssertiveCodeBlock, ST> myAssertiveCodeBlockModels;

    /**
     * <p>A map that stores all the details associated with
     * a particular {@link Location}.</p>
     */
    private final Map<Location, String> myLocationDetails;

    /** <p>String template groups for storing all the VC generation details.</p> */
    private final STGroup mySTGroup;

    /** <p>String template for the VC generation details model.</p> */
    private final ST myVCGenDetailsModel;

    // ===========================================================
    // Flag Strings
    // ===========================================================

    private static final String FLAG_SECTION_NAME = "VCGenerator";
    private static final String FLAG_DESC_VERIFY_VC = "Generate VCs.";
    private static final String FLAG_DESC_PERF_VC = "Generate Performance VCs";

    // ===========================================================
    // Flags
    // ===========================================================

    /**
     * <p>Tells the compiler to generate VCs.</p>
     */
    public static final Flag FLAG_VERIFY_VC =
            new Flag(FLAG_SECTION_NAME, "VCs", FLAG_DESC_VERIFY_VC);

    /**
     * <p>Tells the compiler to generate performance VCs.</p>
     */
    private static final Flag FLAG_PVCS_VC =
            new Flag(FLAG_SECTION_NAME, "PVCs", FLAG_DESC_PERF_VC);

    /**
     * <p>Add all the required and implied flags for the {@code VCGenerator}.</p>
     */
    public static void setUpFlags() {
        FlagDependencies.addImplies(FLAG_PVCS_VC, FLAG_VERIFY_VC);
    }

    // ===========================================================
    // Constructors
    // ===========================================================

    /**
     * <p>This creates an object that overrides methods to generate VCs from
     * a {@link ModuleDec}.</p>
     *
     * @param builder A scope builder for a symbol table.
     * @param compileEnvironment The current job's compilation environment
     *                           that stores all necessary objects and flags.
     */
    public VCGenerator(MathSymbolTableBuilder builder, CompileEnvironment compileEnvironment) {
        myAssertiveCodeBlockModels = new LinkedHashMap<>();
        myBuilder = builder;
        myCompileEnvironment = compileEnvironment;
        myFinalAssertiveCodeBlocks = new LinkedList<>();
        myGlobalConstraints = new LinkedHashMap<>();
        myGlobalRequires = new LinkedList<>();
        myIncompleteAssertiveCodeBlocks = new LinkedList<>();
        myLocationDetails = new LinkedHashMap<>();
        mySTGroup = new STGroupFile("templates/VCGenVerboseOutput.stg");
        myTypeGraph = myBuilder.getTypeGraph();
        myVariableSpecFinalItems = new LinkedHashMap<>();
        myVCGenDetailsModel = mySTGroup.getInstanceOf("outputVCGenDetails");
    }

    // ===========================================================
    // Visitor Methods
    // ===========================================================

    // -----------------------------------------------------------
    // Module Declarations
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed before visiting a {@link ModuleDec}.</p>
     *
     * @param dec A module declaration.
     */
    @Override
    public final void preModuleDec(ModuleDec dec) {
        // Set the current module scope
        try {
            myCurrentModuleScope =
                    myBuilder.getModuleScope(new ModuleIdentifier(dec));

            // Get "Z" from the TypeGraph
            Z = Utilities.getMathTypeZ(dec.getLocation(), myCurrentModuleScope);

            // Apply the facility declaration rule to imported facility declarations.
            List<FacilityEntry> results =
                    myCurrentModuleScope
                            .query(new EntryTypeQuery<>(
                                    FacilityEntry.class,
                                    ImportStrategy.IMPORT_NAMED,
                                    FacilityStrategy.FACILITY_INSTANTIATE));

            for (SymbolTableEntry s : results) {
                if (s.getSourceModuleIdentifier().compareTo(
                        myCurrentModuleScope.getModuleIdentifier()) != 0) {
                    // Do all the facility declaration logic, but don't add this
                    // to our incomplete assertive code stack. We shouldn't need to
                    // verify facility declarations that are imported.
                    FacilityDec facDec =
                            (FacilityDec) s.toFacilityEntry(dec.getLocation())
                                    .getDefiningElement();

                    // Store all requires/constraint from the imported concept
                    PosSymbol conceptName = facDec.getConceptName();
                    ModuleIdentifier coId = new ModuleIdentifier(conceptName.getName());
                    storeConceptAssertionClauses(conceptName.getLocation(), coId, true);

                    // Store all requires/constraint from the imported concept realization
                    // if it is not externally realized
                    if (!facDec.getExternallyRealizedFlag()) {
                        PosSymbol conceptRealizName = facDec.getConceptRealizName();
                        ModuleIdentifier coRealizId = new ModuleIdentifier(conceptRealizName.getName());
                        storeConceptRealizAssertionClauses(conceptRealizName.getLocation(),
                                coRealizId, true);
                    }

                    for (EnhancementSpecRealizItem specRealizItem : facDec.getEnhancementRealizPairs()) {
                        // Store all requires/constraint from the imported enhancement(s)
                        PosSymbol enhancementName = specRealizItem.getEnhancementName();
                        ModuleIdentifier enId = new ModuleIdentifier(enhancementName.getName());
                        storeEnhancementAssertionClauses(enhancementName.getLocation(),
                                enId, true);

                        // Store all requires/constraint from the imported enhancement realization(s)
                        PosSymbol enhancementRealizName = specRealizItem.getEnhancementRealizName();
                        ModuleIdentifier enRealizId = new ModuleIdentifier(enhancementRealizName.getName());
                        storeEnhancementRealizAssertionClauses(enhancementRealizName.getLocation(),
                                enRealizId, true);
                    }
                }
            }
        }
        catch (NoSuchSymbolException e) {
            Utilities.noSuchModule(dec.getLocation());
        }
    }

    /**
     * <p>Code that gets executed after visiting a {@link ModuleDec}.</p>
     *
     * @param dec A module declaration.
     */
    @Override
    public final void postModuleDec(ModuleDec dec) {
        // Loop through our incomplete assertive code blocks until it is empty
        while (!myIncompleteAssertiveCodeBlocks.isEmpty()) {
            // Use the first assertive code block in the incomplete blocks list
            // as our current assertive code block.
            myCurrentAssertiveCodeBlock =
                    myIncompleteAssertiveCodeBlocks.removeFirst();

            applyStatementRules(myCurrentAssertiveCodeBlock);

            // Render the assertive block model
            ST blockModel =
                    myAssertiveCodeBlockModels
                            .remove(myCurrentAssertiveCodeBlock);
            myVCGenDetailsModel.add("assertiveCodeBlocks", blockModel.render());

            // Add this to our final assertive code block list
            myFinalAssertiveCodeBlocks.add(myCurrentAssertiveCodeBlock);

            // Set the current assertive code block to null
            myCurrentAssertiveCodeBlock = null;
        }

        // Assign a name to all of the VCs
        int blockCount = 0;
        for (AssertiveCodeBlock block : myFinalAssertiveCodeBlocks) {
            // Obtain the final list of vcs
            int vcCount = 1;
            List<VerificationCondition> vcs = block.getVCs();
            List<VerificationCondition> namedVCs = new ArrayList<>(vcs.size());
            for (VerificationCondition vc : vcs) {
                namedVCs.add(new VerificationCondition(vc.getLocation(),
                        blockCount + "_" + vcCount,
                        vc.getAssociatedSequents()));
                vcCount++;
            }

            // Store the named VCs and increase the block number
            block.setVCs(namedVCs);
            blockCount++;
        }
    }

    // -----------------------------------------------------------
    // Concept Module
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed before visiting a {@link ConceptModuleDec}.</p>
     *
     * @param concept A concept module declaration.
     */
    @Override
    public final void preConceptModuleDec(ConceptModuleDec concept) {
        PosSymbol conceptName = concept.getName();

        // Store the enhancement realization requires clause
        storeRequiresClause(conceptName.getName(), concept.getRequires());

        // Add to VC detail model
        ST header =
                mySTGroup.getInstanceOf("outputConceptHeader").add(
                        "conceptName", conceptName.getName());
        myVCGenDetailsModel.add("fileHeader", header.render());
    }

    // -----------------------------------------------------------
    // Enhancement Realization Module
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed before visiting an {@link EnhancementRealizModuleDec}.</p>
     *
     * @param enhancementRealization An enhancement realization module declaration.
     */
    @Override
    public final void preEnhancementRealizModuleDec(
            EnhancementRealizModuleDec enhancementRealization) {
        PosSymbol enhancementRealizName = enhancementRealization.getName();

        // Store the enhancement realization requires clause
        storeRequiresClause(enhancementRealizName.getName(),
                enhancementRealization.getRequires());

        // Store all requires/constraint from the imported concept
        PosSymbol conceptName = enhancementRealization.getConceptName();
        ModuleIdentifier coId = new ModuleIdentifier(conceptName.getName());
        storeConceptAssertionClauses(conceptName.getLocation(), coId, false);

        // Store all requires/constraint from the imported enhancement
        PosSymbol enhancementName = enhancementRealization.getEnhancementName();
        ModuleIdentifier enId = new ModuleIdentifier(enhancementName.getName());
        storeEnhancementAssertionClauses(enhancementName.getLocation(), enId,
                false);

        // Add to VC detail model
        ST header =
                mySTGroup.getInstanceOf("outputEnhancementRealizHeader").add(
                        "realizName", enhancementRealizName.getName()).add(
                        "enhancementName", enhancementName.getName()).add(
                        "conceptName", conceptName.getName());
        myVCGenDetailsModel.add("fileHeader", header.render());
    }

    // -----------------------------------------------------------
    // Operation-Related
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed before visiting a {@link ProcedureDec}.</p>
     *
     * @param dec A procedure declaration.
     */
    @Override
    public final void preProcedureDec(ProcedureDec dec) {
        // Store the associated OperationEntry for future use
        List<PTType> argTypes = new LinkedList<>();
        for (ParameterVarDec p : dec.getParameters()) {
            argTypes.add(p.getTy().getProgramType());
        }
        myCorrespondingOperation =
                Utilities.searchOperation(dec.getLocation(), null, dec
                        .getName(), argTypes, myCurrentModuleScope);

        // TODO: Add the performance logic
        // Obtain the performance duration clause
        /*if (myInstanceEnvironment.flags.isFlagSet(FLAG_ALTPVCS_VC)) {
            myCurrentOperationProfileEntry =
                    Utilities.searchOperationProfile(dec.getLocation(), null,
                            dec.getName(), argTypes, myCurrentModuleScope);
        }*/

        // Check to see if this a local operation
        boolean isLocal =
                Utilities.isLocationOperation(dec.getName().getName(),
                        myCurrentModuleScope);

        // Create a new assertive code block
        myCurrentAssertiveCodeBlock =
                new AssertiveCodeBlock(myTypeGraph, dec, dec.getName());

        // Create the top most level assume statement and
        // add it to the assertive code block as the first statement
        // TODO: Add convention/correspondence if we are in a concept realization and it isn't local
        AssumeStmt topLevelAssumeStmt = new AssumeStmt(dec.getLocation().clone(),
                Utilities.createTopLevelAssumeExps(dec.getLocation(), myCurrentModuleScope,
                        myCurrentAssertiveCodeBlock, myLocationDetails, myGlobalRequires, myGlobalConstraints,
                        myCorrespondingOperation, isLocal),
                false);
        myCurrentAssertiveCodeBlock.addStatement(topLevelAssumeStmt);

        // Create Remember statement
        MemoryStmt rememberStmt = new MemoryStmt(dec.getLocation().clone(), StatementType.REMEMBER);
        myCurrentAssertiveCodeBlock.addStatement(rememberStmt);

        // TODO: NY - Add any procedure duration clauses

        // Create a new model for this assertive code block
        ST blockModel = mySTGroup.getInstanceOf("outputAssertiveCodeBlock");
        blockModel.add("blockName", dec.getName());
        ST stepModel = mySTGroup.getInstanceOf("outputVCGenStep");
        stepModel.add("proofRuleName", "Procedure Declaration Rule (Part 1)").add(
                "currentStateOfBlock", myCurrentAssertiveCodeBlock);
        blockModel.add("vcGenSteps", stepModel.render());
        myAssertiveCodeBlockModels.put(myCurrentAssertiveCodeBlock, blockModel);
    }

    /**
     * <p>Code that gets executed after visiting a {@link ProcedureDec}.</p>
     *
     * @param dec A procedure declaration.
     */
    @Override
    public final void postProcedureDec(ProcedureDec dec) {
        // Create the final confirm expression
        // TODO: Replace facility actuals variables in the ensures clause
        Exp finalConfirmExp =
                Utilities.createFinalConfirmExp(dec.getLocation(),
                        myCurrentModuleScope, myTypeGraph, myLocationDetails,
                        myCorrespondingOperation);

        // Apply procedure declaration rule
        // TODO: Recheck logic to make sure everything still works!
        ProofRuleApplication declRule =
                new ProcedureDeclRule(dec.getLocation(), dec.getVariables(),
                        myVariableSpecFinalItems, dec.getStatements(),
                        finalConfirmExp, myCurrentAssertiveCodeBlock,
                        mySTGroup, myAssertiveCodeBlockModels
                                .remove(myCurrentAssertiveCodeBlock));
        declRule.applyRule();

        // Update the current assertive code block and its associated block model.
        myCurrentAssertiveCodeBlock =
                declRule.getAssertiveCodeBlocks().getFirst();
        myAssertiveCodeBlockModels.put(myCurrentAssertiveCodeBlock, declRule
                .getBlockModel());

        // Add this as a new incomplete assertive code block
        myIncompleteAssertiveCodeBlocks.add(myCurrentAssertiveCodeBlock);

        myVariableSpecFinalItems.clear();
        myCurrentAssertiveCodeBlock = null;
        myCorrespondingOperation = null;
    }

    // -----------------------------------------------------------
    // Variable Declaration-Related
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed after visiting a {@link VarDec}.</p>
     *
     * @param dec A variable declaration.
     */
    @Override
    public final void postVarDec(VarDec dec) {
        // Ty should always be a NameTy
        if (dec.getTy() instanceof NameTy) {
            NameTy nameTy = (NameTy) dec.getTy();

            // Query for the type entry in the symbol table
            SymbolTableEntry ste =
                    Utilities.searchProgramType(nameTy.getLocation(), nameTy
                            .getQualifier(), nameTy.getName(),
                            myCurrentModuleScope);
            ProgramTypeEntry typeEntry;
            if (ste instanceof ProgramTypeEntry) {
                typeEntry = ste.toProgramTypeEntry(nameTy.getLocation());
            }
            else {
                typeEntry =
                        ste.toTypeRepresentationEntry(nameTy.getLocation())
                                .getDefiningTypeEntry();
            }

            // Generate the corresponding proof rule
            ProofRuleApplication declRule;
            if (typeEntry.getDefiningElement() instanceof TypeFamilyDec) {
                // Variable declaration rule for known types
                TypeFamilyDec type =
                        (TypeFamilyDec) typeEntry.getDefiningElement();
                AssertionClause initEnsures =
                        type.getInitialization().getEnsures();
                AssertionClause modifiedInitEnsures =
                        Utilities.getTypeEnsuresClause(initEnsures, dec
                                .getLocation(), null, dec.getName(), type
                                .getExemplar(), typeEntry.getModelType(), null);

                // TODO: Logic for types in concept realizations

                declRule =
                        new KnownTypeVariableDeclRule(dec, modifiedInitEnsures,
                                myCurrentAssertiveCodeBlock, mySTGroup,
                                myAssertiveCodeBlockModels
                                        .remove(myCurrentAssertiveCodeBlock));

                // Store the variable's finalization item for
                // future use.
                AffectsClause finalAffects =
                        type.getFinalization().getAffectedVars();
                AssertionClause finalEnsures =
                        type.getFinalization().getEnsures();
                if (!VarExp.isLiteralTrue(finalEnsures.getAssertionExp())) {
                    myVariableSpecFinalItems.put(dec, new SpecInitFinalItem(
                            type.getFinalization().getLocation(), type
                                    .getFinalization().getClauseType(),
                            finalAffects, Utilities.getTypeEnsuresClause(
                                    finalEnsures, dec.getLocation(), null, dec
                                            .getName(), type.getExemplar(),
                                    typeEntry.getModelType(), null)));
                }
            }
            else {
                // Variable declaration rule for generic types
                declRule =
                        new GenericTypeVariableDeclRule(dec,
                                myCurrentAssertiveCodeBlock, mySTGroup,
                                myAssertiveCodeBlockModels
                                        .remove(myCurrentAssertiveCodeBlock));
            }

            // Apply the variable declaration rule.
            declRule.applyRule();

            // NY YS
            // TODO: Initialization duration for this variable

            // Update the current assertive code block and its associated block model.
            myCurrentAssertiveCodeBlock =
                    declRule.getAssertiveCodeBlocks().getFirst();
            myAssertiveCodeBlockModels.put(myCurrentAssertiveCodeBlock,
                    declRule.getBlockModel());
        }
        else {
            // Shouldn't be possible but just in case it ever happens
            // by accident.
            Utilities.tyNotHandled(dec.getTy(), dec.getLocation());
        }
    }

    // -----------------------------------------------------------
    // Other
    // -----------------------------------------------------------

    /**
     * <p>Code that gets executed after visiting an {@link AssertionClause}.</p>
     *
     * @param clause An assertion clause declaration.
     */
    @Override
    public final void postAssertionClause(AssertionClause clause) {
        if (clause.getWhichEntailsExp() != null) {
            // Create a new assertive code block
            PosSymbol name =
                    new PosSymbol(clause.getLocation(),
                            "Which_Entails Expression Located at  "
                                    + clause.getLocation());
            AssertiveCodeBlock block =
                    new AssertiveCodeBlock(myTypeGraph, clause, name);

            // Apply the rule
            block.addStatement(new AssumeStmt(clause.getLocation().clone(),
                    clause.getAssertionExp(), false));
            block.addStatement(new ConfirmStmt(clause.getLocation().clone(),
                    clause.getWhichEntailsExp(), false));

            // Add the location detail if it doesn't exist
            if (!myLocationDetails.containsKey(clause.getWhichEntailsExp()
                    .getLocation())) {
                myLocationDetails.put(
                        clause.getWhichEntailsExp().getLocation(), name
                                .getName());
            }

            // Create a new model for this assertive code block
            ST blockModel = mySTGroup.getInstanceOf("outputAssertiveCodeBlock");
            blockModel.add("blockName", name);
            ST stepModel = mySTGroup.getInstanceOf("outputVCGenStep");
            stepModel.add("proofRuleName", "Which_Entails Declaration Rule")
                    .add("currentStateOfBlock", block);
            blockModel.add("vcGenSteps", stepModel.render());
            myAssertiveCodeBlockModels.put(block, blockModel);

            // Add this as a new incomplete assertive code block
            myIncompleteAssertiveCodeBlocks.add(block);
        }
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /**
     * <p>This method returns the final {@link AssertiveCodeBlock AssertiveCodeBlocks}
     * containing the generated {@link Sequent Sequents}.</p>
     *
     * @return A list containing {@link AssertiveCodeBlock AssertiveCodeBlocks}.
     */
    public final List<AssertiveCodeBlock> getFinalAssertiveCodeBlocks() {
        return myFinalAssertiveCodeBlocks;
    }

    /**
     * <p>This method returns a map containing all the details associated with a
     * {@link Location} that we have encountered during the generation process.</p>
     *
     * @return A map containing location details.
     */
    public final Map<Location, String> getLocationDetails() {
        return myLocationDetails;
    }

    /**
     * <p>This method returns the verbose mode output with how we generated
     * the {@code VCs} for this {@link ModuleDec}.</p>
     *
     * @return A string containing lots of details.
     */
    public final String getVerboseModeOutput() {
        return myVCGenDetailsModel.render();
    }

    // ===========================================================
    // Private Methods
    // ===========================================================

    /**
     * <p>Applies each of the proof rules. This <code>AssertiveCode</code> will be
     * stored for later use and therefore should be considered immutable after
     * a call to this method.</p>
     */
    private void applyStatementRules(AssertiveCodeBlock assertiveCodeBlock) {
        // Obtain the assertive code block model
        ST blockModel = myAssertiveCodeBlockModels.remove(assertiveCodeBlock);

        // Apply a statement proof rule to each of the assertions.
        while (assertiveCodeBlock.hasMoreStatements()) {
            // Work our way from the last statement
            Statement statement = assertiveCodeBlock.removeLastSatement();

            // Generate one of the statement proof rule applications
            ProofRuleApplication ruleApplication;
            if (statement instanceof AssumeStmt) {
                // Apply the assume rule.
                ruleApplication =
                        new AssumeStmtRule((AssumeStmt) statement,
                                assertiveCodeBlock, mySTGroup, blockModel);
            }
            else if (statement instanceof CallStmt) {
                CallStmt callStmt = (CallStmt) statement;
                ProgramFunctionExp functionExp = callStmt.getFunctionExp();

                // Call a method to locate the operation dec for this call
                List<PTType> argTypes = new LinkedList<>();
                for (ProgramExp arg : functionExp.getArguments()) {
                    argTypes.add(arg.getProgramType());
                }
                OperationEntry opEntry =
                        Utilities.searchOperation(callStmt.getLocation(),
                                functionExp.getQualifier(), functionExp.getName(),
                                argTypes, myCurrentModuleScope);

                // Find all the replacements that needs to happen to the requires
                // and ensures clauses
                List<ProgramExp> callArgs = functionExp.getArguments();
                List<Exp> replaceArgs = modifyArgumentList(callArgs);

                // Apply the call rule.
                ruleApplication =
                        new CallStmtRule(callStmt, opEntry, replaceArgs,
                                myCurrentModuleScope, assertiveCodeBlock, mySTGroup, blockModel);
            }
            else if (statement instanceof ConfirmStmt) {
                // Apply the confirm rule.
                ruleApplication =
                        new ConfirmStmtRule((ConfirmStmt) statement,
                                assertiveCodeBlock, mySTGroup, blockModel);

                // Since the ConfirmStmt's location might be different than
                // it's assertion's location. We need to copy over the details
                // for the inner assertion and set it as the detail for the
                // ConfirmStmt's location.
                myLocationDetails.put(statement.getLocation(),
                        myLocationDetails.get(((ConfirmStmt) statement).getAssertion().getLocation()));
            }
            else if (statement instanceof MemoryStmt) {
                if (((MemoryStmt) statement).getStatementType() == StatementType.REMEMBER) {
                    // Apply the remember rule.
                    ruleApplication =
                            new RememberStmtRule(assertiveCodeBlock, mySTGroup, blockModel);
                }
                else {
                    throw new SourceErrorException(
                            "[VCGenerator] Forget statements are not handled.",
                            statement.getLocation());
                }
            }
            else {
                throw new SourceErrorException(
                        "[VCGenerator] Statement type not handled: "
                                + statement.getClass().getSimpleName(),
                        statement.getLocation());
            }

            // Apply the proof rule
            ruleApplication.applyRule();

            // Some of the proof rules might generate more than more
            // than one assertive code block. The first one is always
            // the one we passed in to the rule. We add the rest to the
            // front of the incomplete stack.
            Deque<AssertiveCodeBlock> resultingBlocks = ruleApplication.getAssertiveCodeBlocks();
            assertiveCodeBlock = resultingBlocks.removeFirst();
            while (!resultingBlocks.isEmpty()) {
                myIncompleteAssertiveCodeBlocks.addFirst(resultingBlocks.removeLast());
            }

            // Add any new location details
            myLocationDetails.putAll(ruleApplication.getNewLocationString());

            // Update our block model
            blockModel = ruleApplication.getBlockModel();

            // Apply each statement rule here.
            /*else if (lastStatement instanceof FuncAssignStmt) {
                applyFuncAssignStmtRule((FuncAssignStmt) statement);
            }
            else if (lastStatement instanceof IfStmt) {
                applyIfStmtRule((IfStmt) statement);
            }
            else if (lastStatement instanceof PresumeStmt) {
                applyPresumeStmtRule((PresumeStmt) statement);
            }
            else if (lastStatement instanceof SwapStmt) {
                applySwapStmtRule((SwapStmt) statement);
            }
            else if (lastStatement instanceof WhileStmt) {
                applyWhileStmtRule((WhileStmt) statement);
            }*/
        }

        myAssertiveCodeBlockModels.put(assertiveCodeBlock, blockModel);
    }

    /**
     * <p>Modify the argument expression list if we have a
     * nested function call.</p>
     *
     * @param callArgs The original list of arguments.
     *
     * @return The modified list of arguments.
     */
    private List<Exp> modifyArgumentList(List<ProgramExp> callArgs) {
        // Find all the replacements that needs to happen to the requires
        // and ensures clauses
        List<Exp> replaceArgs = new ArrayList<>();
        for (ProgramExp p : callArgs) {
            /* TODO: Add the logic for nested function calls
            // Check for nested function calls in ProgramDotExp
            // and ProgramParamExp.
            if (p instanceof ProgramDotExp || p instanceof ProgramParamExp) {
                NestedFuncWalker nfw =
                        new NestedFuncWalker(myCurrentOperationEntry,
                                myOperationDecreasingExp, mySymbolTable,
                                myCurrentModuleScope, myCurrentAssertiveCode,
                                myInstantiatedFacilityArgMap);
                TreeWalker tw = new TreeWalker(nfw);
                tw.visit(p);

                // Add the requires clause as something we need to confirm
                Exp pRequires = nfw.getRequiresClause();
                if (!pRequires.isLiteralTrue()) {
                    myCurrentAssertiveCode.addConfirm(pRequires.getLocation(),
                            pRequires, false);
                }

                // Add the modified ensures clause as the new expression we want
                // to replace in the CallStmt's ensures clause.
                replaceArgs.add(nfw.getEnsuresClause());
            }
            // For all other types of arguments, simply add it to the list to be replaced
            else {
                replaceArgs.add(p);
            }*/
            replaceArgs.add(p);
        }

        return replaceArgs;
    }

    /**
     * <p>An helper method for storing the imported {@code concept's}
     * {@code requires} clause and its associated location detail for
     * future use.</p>
     *
     * @param loc The location of the imported {@code module}.
     * @param id A {@link ModuleIdentifier} referring to an
     *           importing {@code concept}.
     * @param isFacilityImport A flag that indicates whether or not
     *                         we are storing information that originated
     *                         from a {@link FacilityDec}.
     */
    private void storeConceptAssertionClauses(Location loc,
            ModuleIdentifier id, boolean isFacilityImport) {
        try {
            ConceptModuleDec conceptModuleDec =
                    (ConceptModuleDec) myBuilder.getModuleScope(id)
                            .getDefiningElement();

            // We only need to store these if they are part of a FacilityDec
            if (!isFacilityImport) {
                // Store the concept's requires clause
                storeRequiresClause(conceptModuleDec.getName().getName(),
                        conceptModuleDec.getRequires());

                // Store the concept's type constraints from the module parameters
                storeModuleParameterTypeConstraints(conceptModuleDec
                        .getLocation(), conceptModuleDec.getParameterDecs());
            }

            // Store the concept's module constraints
            if (!conceptModuleDec.getConstraints().isEmpty()) {
                myGlobalConstraints.put(conceptModuleDec, conceptModuleDec
                        .getConstraints());
                myLocationDetails.put(conceptModuleDec.getLocation(),
                        "Constraint Clause for " + conceptModuleDec.getName());
            }
        }
        catch (NoSuchSymbolException e) {
            Utilities.noSuchModule(loc);
        }
    }

    /**
     * <p>An helper method for storing the imported {@code concept realization's}
     * {@code requires} clause and its associated location detail for
     * future use.</p>
     *
     * @param loc The location of the imported {@code module}.
     * @param id A {@link ModuleIdentifier} referring to an
     *           importing {@code concept realization}.
     * @param isFacilityImport A flag that indicates whether or not
     *                         we are storing information that originated
     *                         from a {@link FacilityDec}.
     */
    private void storeConceptRealizAssertionClauses(Location loc,
            ModuleIdentifier id, boolean isFacilityImport) {
        try {
            ConceptRealizModuleDec realizModuleDec =
                    (ConceptRealizModuleDec) myBuilder.getModuleScope(id)
                            .getDefiningElement();

            // We only need to store these if they are part of a FacilityDec
            if (!isFacilityImport) {
                // Store the concept realization's requires clause
                storeRequiresClause(realizModuleDec.getName().getName(),
                        realizModuleDec.getRequires());

                // Store the concept realization's type constraints from the module parameters
                storeModuleParameterTypeConstraints(realizModuleDec
                        .getLocation(), realizModuleDec.getParameterDecs());
            }
        }
        catch (NoSuchSymbolException e) {
            Utilities.noSuchModule(loc);
        }
    }

    /**
     * <p>An helper method for storing the imported {@code enhancement's}
     * {@code requires} clause and its associated location detail for
     * future use.</p>
     *
     * @param loc The location of the imported {@code module}.
     * @param id A {@link ModuleIdentifier} referring to an
     *           importing {@code enhancement}.
     * @param isFacilityImport A flag that indicates whether or not
     *                         we are storing information that originated
     *                         from a {@link FacilityDec}.
     */
    private void storeEnhancementAssertionClauses(Location loc,
            ModuleIdentifier id, boolean isFacilityImport) {
        try {
            EnhancementModuleDec enhancementModuleDec =
                    (EnhancementModuleDec) myBuilder.getModuleScope(id)
                            .getDefiningElement();

            // We only need to store these if they are part of a FacilityDec
            if (!isFacilityImport) {
                // Store the enhancement's requires clause
                storeRequiresClause(enhancementModuleDec.getName().getName(),
                        enhancementModuleDec.getRequires());

                // Store the enhancement's type constraints from the module parameters
                storeModuleParameterTypeConstraints(enhancementModuleDec
                        .getLocation(), enhancementModuleDec.getParameterDecs());
            }
        }
        catch (NoSuchSymbolException e) {
            Utilities.noSuchModule(loc);
        }
    }

    /**
     * <p>An helper method for storing the imported {@code enhancement realization's}
     * {@code requires} clause and its associated location detail for
     * future use.</p>
     *
     * @param loc The location of the imported {@code module}.
     * @param id A {@link ModuleIdentifier} referring to an
     *           importing {@code enhancement realization}.
     * @param isFacilityImport A flag that indicates whether or not
     *                         we are storing information that originated
     *                         from a {@link FacilityDec}.
     */
    private void storeEnhancementRealizAssertionClauses(Location loc,
            ModuleIdentifier id, boolean isFacilityImport) {
        try {
            EnhancementRealizModuleDec realizModuleDec =
                    (EnhancementRealizModuleDec) myBuilder.getModuleScope(id)
                            .getDefiningElement();

            // We only need to store these if they are part of a FacilityDec
            if (!isFacilityImport) {
                // Store the enhancement realization's requires clause
                storeRequiresClause(realizModuleDec.getName().getName(),
                        realizModuleDec.getRequires());

                // Store the enhancement realization's type constraints from the module parameters
                storeModuleParameterTypeConstraints(realizModuleDec
                        .getLocation(), realizModuleDec.getParameterDecs());
            }
        }
        catch (NoSuchSymbolException e) {
            Utilities.noSuchModule(loc);
        }
    }

    /**
     * <p>An helper method for storing all the {@code constraint} clauses
     * for a list of {@link ModuleParameterDec ModuleParameterDecs}.</p>
     *
     * @param loc The location of the {@code module} that contains the
     *            module parameters.
     * @param moduleParameterDecs A list of {@link ModuleParameterDec}.
     */
    private void storeModuleParameterTypeConstraints(Location loc,
            List<ModuleParameterDec> moduleParameterDecs) {
        for (ModuleParameterDec m : moduleParameterDecs) {
            Dec wrappedDec = m.getWrappedDec();
            if (wrappedDec instanceof ConstantParamDec) {
                ConstantParamDec dec = (ConstantParamDec) wrappedDec;
                ProgramTypeEntry typeEntry;

                if (dec.getVarDec().getTy() instanceof NameTy) {
                    NameTy pNameTy = (NameTy) dec.getVarDec().getTy();

                    // Query for the type entry in the symbol table
                    SymbolTableEntry ste =
                            Utilities.searchProgramType(pNameTy.getLocation(),
                                    pNameTy.getQualifier(), pNameTy.getName(),
                                    myCurrentModuleScope);

                    if (ste instanceof ProgramTypeEntry) {
                        typeEntry =
                                ste.toProgramTypeEntry(pNameTy.getLocation());
                    }
                    else {
                        typeEntry =
                                ste.toTypeRepresentationEntry(
                                        pNameTy.getLocation())
                                        .getDefiningTypeEntry();
                    }

                    // Make sure we don't have a generic type
                    if (typeEntry.getDefiningElement() instanceof TypeFamilyDec) {
                        // Obtain the original dec from the AST
                        TypeFamilyDec type =
                                (TypeFamilyDec) typeEntry.getDefiningElement();

                        if (!VarExp.isLiteralTrue(type.getConstraint()
                                .getAssertionExp())) {
                            AssertionClause constraintClause =
                                    type.getConstraint();
                            AssertionClause modifiedConstraint =
                                    Utilities.getTypeConstraintClause(
                                            constraintClause,
                                            dec.getLocation(), null, dec
                                                    .getName(), type
                                                    .getExemplar(), typeEntry
                                                    .getModelType(), null);

                            // Store the constraint and its associated location detail for future use
                            myGlobalConstraints.put(dec, Collections
                                    .singletonList(modifiedConstraint));
                            myLocationDetails.put(modifiedConstraint
                                    .getLocation(), "Constraint Clause of "
                                    + dec.getName());
                        }
                    }
                }
                else {
                    Utilities.tyNotHandled(dec.getVarDec().getTy(), loc);
                }
            }
        }
    }

    /**
     * <p>An helper method for storing a {@code requires} clause and its
     * associated location detail for future use.</p>
     *
     * @param decName Name of the declaration that contains
     *                the {@code requiresClause}.
     * @param requiresClause An {@link AssertionClause} containing a {@code requires} clause.
     */
    private void storeRequiresClause(String decName,
            AssertionClause requiresClause) {
        if (!VarExp.isLiteralTrue(requiresClause.getAssertionExp())) {
            myGlobalRequires.add(requiresClause);

            // Add the location details for both the requires and
            // which_entails expressions (if any).
            myLocationDetails.put(requiresClause.getAssertionExp()
                    .getLocation(), "Requires Clause of " + decName);
            if (requiresClause.getWhichEntailsExp() != null) {
                myLocationDetails.put(requiresClause.getWhichEntailsExp()
                        .getLocation(), "Which_Entails Expression Located at "
                        + requiresClause.getWhichEntailsExp().getLocation());
            }
        }
    }

}