/*
 * Utilities.java
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
package edu.clemson.cs.rsrg.vcgeneration.utilities;

import edu.clemson.cs.r2jt.rewriteprover.immutableadts.ImmutableList;
import edu.clemson.cs.rsrg.absyn.clauses.AssertionClause;
import edu.clemson.cs.rsrg.absyn.clauses.AssertionClause.ClauseType;
import edu.clemson.cs.rsrg.absyn.declarations.Dec;
import edu.clemson.cs.rsrg.absyn.declarations.typedecl.TypeFamilyDec;
import edu.clemson.cs.rsrg.absyn.declarations.variabledecl.ParameterVarDec;
import edu.clemson.cs.rsrg.absyn.declarations.variabledecl.VarDec;
import edu.clemson.cs.rsrg.absyn.expressions.Exp;
import edu.clemson.cs.rsrg.absyn.expressions.mathexpr.*;
import edu.clemson.cs.rsrg.absyn.expressions.mathexpr.EqualsExp.Operator;
import edu.clemson.cs.rsrg.absyn.expressions.programexpr.*;
import edu.clemson.cs.rsrg.absyn.rawtypes.NameTy;
import edu.clemson.cs.rsrg.absyn.rawtypes.Ty;
import edu.clemson.cs.rsrg.parsing.data.Location;
import edu.clemson.cs.rsrg.parsing.data.PosSymbol;
import edu.clemson.cs.rsrg.statushandling.exception.SourceErrorException;
import edu.clemson.cs.rsrg.typeandpopulate.entry.*;
import edu.clemson.cs.rsrg.typeandpopulate.entry.ProgramParameterEntry.ParameterMode;
import edu.clemson.cs.rsrg.typeandpopulate.exception.DuplicateSymbolException;
import edu.clemson.cs.rsrg.typeandpopulate.exception.NoSuchSymbolException;
import edu.clemson.cs.rsrg.typeandpopulate.exception.SymbolNotOfKindTypeException;
import edu.clemson.cs.rsrg.typeandpopulate.mathtypes.MTCartesian;
import edu.clemson.cs.rsrg.typeandpopulate.mathtypes.MTType;
import edu.clemson.cs.rsrg.typeandpopulate.programtypes.PTGeneric;
import edu.clemson.cs.rsrg.typeandpopulate.programtypes.PTType;
import edu.clemson.cs.rsrg.typeandpopulate.query.NameQuery;
import edu.clemson.cs.rsrg.typeandpopulate.query.OperationQuery;
import edu.clemson.cs.rsrg.typeandpopulate.query.UnqualifiedNameQuery;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.MathSymbolTable.FacilityStrategy;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.MathSymbolTable.ImportStrategy;
import edu.clemson.cs.rsrg.typeandpopulate.symboltables.ModuleScope;
import edu.clemson.cs.rsrg.treewalk.TreeWalkerVisitor;
import edu.clemson.cs.rsrg.typeandpopulate.typereasoning.TypeGraph;
import edu.clemson.cs.rsrg.vcgeneration.VCGenerator;
import edu.clemson.cs.rsrg.vcgeneration.sequents.Sequent;
import java.util.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;

/**
 * <p>This class contains a bunch of utilities methods used by the {@link VCGenerator}
 * and all of its associated {@link TreeWalkerVisitor TreeWalkerVisitors}.</p>
 *
 * @author Yu-Shan Sun
 * @version 2.0
 */
public class Utilities {

    // ===========================================================
    // Public Methods
    // ===========================================================

    /**
     * <p>Converts the different types of {@link Exp} to the
     * ones used by the VC Generator.</p>
     *
     * @param oldExp The expression to be converted.
     * @param scope The module scope to start our search.
     *
     * @return A modified {@link Exp}.
     */
    public static Exp convertExp(Exp oldExp, ModuleScope scope) {
        Exp retExp;

        // Case #1: ProgramIntegerExp
        if (oldExp instanceof ProgramIntegerExp) {
            IntegerExp exp =
                    new IntegerExp(oldExp.getLocation(), null,
                            ((ProgramIntegerExp) oldExp).getValue());

            // At this point all programming integer expressions
            // should be greater than or equals to 0. Negative
            // numbers should have called the corresponding operation
            // to convert it to a negative number. Therefore, we
            // need to locate the type "N" (Natural Number)
            MathSymbolEntry mse =
                    searchMathSymbol(exp.getLocation(), "N", scope);
            try {
                exp.setMathType(mse.getTypeValue());
            }
            catch (SymbolNotOfKindTypeException e) {
                notAType(mse, exp.getLocation());
            }

            retExp = exp;
        }
        // Case #2: ProgramCharacterExp
        else if (oldExp instanceof ProgramCharExp) {
            CharExp exp =
                    new CharExp(oldExp.getLocation(),
                            ((ProgramCharExp) oldExp).getValue());
            exp.setMathType(oldExp.getMathType());
            retExp = exp;
        }
        // Case #3: ProgramStringExp
        else if (oldExp instanceof ProgramStringExp) {
            StringExp exp =
                    new StringExp(oldExp.getLocation(),
                            ((ProgramStringExp) oldExp).getValue());
            exp.setMathType(oldExp.getMathType());
            retExp = exp;
        }
        // Case #4: VariableDotExp
        else if (oldExp instanceof ProgramVariableDotExp) {
            List<ProgramVariableExp> segments =
                    ((ProgramVariableDotExp) oldExp).getSegments();
            List<Exp> newSegments = new ArrayList<>();

            // Need to replace each of the segments in a dot expression
            MTType lastMathType = null;
            MTType lastMathTypeValue = null;
            for (ProgramVariableExp v : segments) {
                // Can only be a ProgramVariableNameExp. Anything else
                // is a case we have not handled.
                if (v instanceof ProgramVariableNameExp) {
                    VarExp varExp =
                            new VarExp(v.getLocation(), v.getQualifier(),
                                    ((ProgramVariableNameExp) v).getName());
                    varExp.setMathType(v.getMathType());
                    varExp.setMathTypeValue(v.getMathTypeValue());
                    lastMathType = v.getMathType();
                    lastMathTypeValue = v.getMathTypeValue();
                    newSegments.add(varExp);
                }
                else {
                    expNotHandled(v, v.getLocation());
                }
            }

            // Set the segments and the type information.
            DotExp exp = new DotExp(oldExp.getLocation(), newSegments);
            exp.setMathType(lastMathType);
            exp.setMathTypeValue(lastMathTypeValue);
            retExp = exp;
        }
        // Case #5: VariableNameExp
        else if (oldExp instanceof ProgramVariableNameExp) {
            VarExp exp =
                    new VarExp(oldExp.getLocation(), ((ProgramVariableNameExp) oldExp).getQualifier(),
                            ((ProgramVariableNameExp) oldExp).getName());
            exp.setMathType(oldExp.getMathType());
            exp.setMathTypeValue(oldExp.getMathTypeValue());
            retExp = exp;
        }
        // Else simply return oldExp
        else {
            retExp = oldExp;
        }

        return retExp;
    }

    /**
     * <p>This method uses the {@code ensures} clause from the operation entry
     * and adds in additional {@code ensures} clauses for different parameter modes
     * and builds the appropriate {@code ensures} clause that will be an
     * {@link AssertiveCodeBlock AssertiveCodeBlock's} final {@code confirm} statement.</p>
     *
     * <p>See the {@code Procedure} declaration rule for more detail.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param scope The module scope to start our search.
     * @param g The current type graph.
     * @param locationStringsMap A map containing all the {@link Location} details.
     * @param correspondingOperationEntry The corresponding {@link OperationEntry}.
     *
     * @return The final confirm expression.
     */
    public static Exp createFinalConfirmExp(Location loc, ModuleScope scope,
            TypeGraph g, Map<Location, String> locationStringsMap,
            OperationEntry correspondingOperationEntry) {
        Exp retExp = null;

        // Add the operation's ensures clause (and any which_entails clause)
        AssertionClause ensuresClause =
                correspondingOperationEntry.getEnsuresClause();
        Exp ensuresExp = ensuresClause.getAssertionExp().clone();
        if (!VarExp.isLiteralTrue(ensuresExp)) {
            retExp = Utilities.formConjunct(loc, retExp, ensuresClause);

            // At the same time add the location details for these expressions.
            locationStringsMap.put(ensuresExp.getLocation(), "Ensures Clause of "
                    + correspondingOperationEntry.getName());
            if (ensuresClause.getWhichEntailsExp() != null) {
                locationStringsMap.put(ensuresClause.getWhichEntailsExp()
                                .getLocation(),
                        "Which_Entails expression for clause located at "
                                + ensuresClause.getWhichEntailsExp().getLocation());
            }
        }

        // Loop through each of the parameters in the operation entry.
        ImmutableList<ProgramParameterEntry> entries =
                correspondingOperationEntry.getParameters();
        for (ProgramParameterEntry entry : entries) {
            ParameterVarDec parameterVarDec =
                    (ParameterVarDec) entry.getDefiningElement();
            ParameterMode parameterMode = entry.getParameterMode();
            NameTy nameTy = (NameTy) parameterVarDec.getTy();

            // Parameter variable and incoming parameter variable
            VarExp parameterExp = Utilities.createVarExp(parameterVarDec.getLocation().clone(), null,
                    parameterVarDec.getName().clone(), nameTy.getMathTypeValue(), null);
            OldExp oldParameterExp = new OldExp(parameterVarDec.getLocation().clone(), parameterExp.clone());
            oldParameterExp.setMathType(nameTy.getMathTypeValue());

            // Query for the type entry in the symbol table
            SymbolTableEntry ste =
                    Utilities.searchProgramType(loc, nameTy.getQualifier(),
                            nameTy.getName(), scope);

            ProgramTypeEntry typeEntry;
            if (ste instanceof ProgramTypeEntry) {
                typeEntry = ste.toProgramTypeEntry(nameTy.getLocation());
            } else {
                typeEntry =
                        ste.toTypeRepresentationEntry(nameTy.getLocation())
                                .getDefiningTypeEntry();
            }

            // The restores mode adds an additional ensures
            // that the outgoing value is equal to the incoming value.
            // Ex: w = #w
            if (parameterMode == ParameterMode.RESTORES) {
                // Set the details for the new location
                Location restoresLoc = loc.clone();

                // Need to ensure here that the everything inside the type family
                // is restored at the end of the operation.
                Exp restoresConditionExp = null;
                if (typeEntry.getModelType() instanceof MTCartesian) {
                    MTCartesian cartesian =
                            (MTCartesian) typeEntry.getModelType();
                    List<MTType> elementTypes =
                            cartesian.getComponentTypes();

                    for (int i = 0; i < cartesian.size(); i++) {
                        // Create an Exp for the Cartesian product element
                        VarExp elementExp = Utilities.createVarExp(restoresLoc.clone(), null,
                                new PosSymbol(restoresLoc.clone(), cartesian.getTag(i)),
                                elementTypes.get(i), null);

                        // Create a list of segments. The first element should be the original
                        // parameterExp and oldParameterExp and the second element the cartesian product element.
                        List<Exp> segments = new ArrayList<>();
                        List<Exp> oldSegments = new ArrayList<>();
                        segments.add(parameterExp);
                        oldSegments.add(oldParameterExp);
                        segments.add(elementExp);
                        oldSegments.add(elementExp.clone());

                        // Create the dotted expressions
                        DotExp elementDotExp = new DotExp(restoresLoc.clone(), segments);
                        elementDotExp.setMathType(elementExp.getMathType());
                        DotExp oldElementDotExp = new DotExp(restoresLoc.clone(), oldSegments);
                        oldElementDotExp.setMathType(elementExp.getMathType());

                        // Create an equality expression
                        EqualsExp equalsExp = new EqualsExp(restoresLoc.clone(), elementDotExp, null,
                                Operator.EQUAL, oldElementDotExp);
                        equalsExp.setMathType(g.BOOLEAN);

                        // Add this to our final equals expression
                        if (restoresConditionExp == null) {
                            restoresConditionExp = equalsExp;
                        }
                        else {
                            restoresConditionExp = InfixExp.formConjunct(restoresLoc.clone(),
                                    restoresConditionExp, equalsExp);
                        }
                    }
                }
                else {
                    // Construct an expression using the expression and it's
                    // old expression equivalent.
                    restoresConditionExp =
                            new EqualsExp(restoresLoc.clone(), parameterExp.clone(), null,
                                    Operator.EQUAL, oldParameterExp.clone());
                    restoresConditionExp.setMathType(g.BOOLEAN);
                }

                AssertionClause restoresEnsuresClause = new AssertionClause(restoresLoc.clone(),
                        ClauseType.ENSURES, restoresConditionExp);
                retExp = Utilities.formConjunct(restoresLoc.clone(), retExp, restoresEnsuresClause);

                // Add the location details for this expression.
                locationStringsMap.put(restoresEnsuresClause.getLocation(), "Ensures Clause of "
                        + correspondingOperationEntry.getName()
                        + " (Condition from \"" + parameterMode + "\" parameter mode)");
            }
            // The clears mode adds an additional ensures
            // that the outgoing value is the initial value.
            else if (parameterMode == ParameterMode.CLEARS) {
                AssertionClause modifiedInitEnsures;
                if (typeEntry.getDefiningElement() instanceof TypeFamilyDec) {
                    // Parameter variable with known program type
                    TypeFamilyDec type =
                            (TypeFamilyDec) typeEntry.getDefiningElement();
                    AssertionClause initEnsures =
                            type.getInitialization().getEnsures();
                    modifiedInitEnsures =
                            Utilities.getTypeEnsuresClause(initEnsures,
                                    loc.clone(), null,
                                    parameterVarDec.getName(), type.getExemplar(),
                                    typeEntry.getModelType(), null);

                    // TODO: Logic for types in concept realizations
                }
                else {
                    VarDec parameterAsVarDec =
                            new VarDec(parameterVarDec.getName(), parameterVarDec.getTy());
                    modifiedInitEnsures =
                            new AssertionClause(loc.clone(), ClauseType.ENSURES,
                                    Utilities.createInitExp(parameterAsVarDec, g.BOOLEAN));
                }

                retExp = Utilities.formConjunct(loc.clone(), retExp, modifiedInitEnsures);

                // Add the location details for this expression.
                locationStringsMap.put(modifiedInitEnsures.getLocation(), "Ensures Clause of "
                        + correspondingOperationEntry.getName()
                        + " (Condition from \"" + parameterMode + "\" parameter mode)");
            }

            // TODO: See below!
            // If the type is a type representation, then our requires clause
            // should really say something about the conceptual type and not
            // the variable
        }

        // Check to see if it is null. If that is the case, then we simply return "true"
        if (retExp == null) {
            retExp = VarExp.getTrueVarExp(loc.clone(), g);

            // Add the location details for this expression.
            locationStringsMap.put(retExp.getLocation(),
                    "Ensures Clause of " + correspondingOperationEntry.getName());
        }

        return retExp;
    }

    /**
     * <p>This method returns a {@link FunctionExp} with the specified
     * name and arguments.</p>
     *
     * @param loc Location that wants to create
     *            this function expression.
     * @param qualifier Qualifier for the function expression.
     * @param name Name of the function expression.
     * @param argExpList List of arguments to the function expression.
     * @param functionNameType Mathematical type for the function name.
     * @param funcType Mathematical type for the function expression.
     *
     * @return The new {@link FunctionExp}.
     */
    public static FunctionExp createFunctionExp(Location loc,
            PosSymbol qualifier, PosSymbol name, List<Exp> argExpList,
            MTType functionNameType, MTType funcType) {
        // Create a VarExp for the function name
        VarExp functionName =
                Utilities.createVarExp(loc, qualifier, name, functionNameType,
                        null);

        // Create the function expression
        FunctionExp exp = new FunctionExp(loc, functionName, null, argExpList);
        exp.setMathType(funcType);

        return exp;
    }

    /**
     * <p>This method returns a {@link DotExp} with the {@link VarDec}
     * and its initialization ensures clause.</p>
     *
     * @param varDec The declared variable.
     * @param booleanType Mathematical boolean type.
     *
     * @return The new {@link DotExp}.
     */
    public static DotExp createInitExp(VarDec varDec, MTType booleanType) {
        // Convert the declared variable into a VarExp
        VarExp varExp =
                Utilities.createVarExp(varDec.getLocation(), null, varDec.getName(),
                        varDec.getTy().getMathTypeValue(), null);

        // Create a VarExp using the type
        VarExp typeNameExp = null;
        if (varDec.getTy() instanceof NameTy) {
            NameTy ty = (NameTy) varDec.getTy();
            typeNameExp =
                    Utilities.createVarExp(ty.getLocation(), ty.getQualifier(), ty.getName(),
                            ty.getMathType(), ty.getMathTypeValue());
        }
        else {
            Utilities.tyNotHandled(varDec.getTy(), varDec.getTy().getLocation());
        }

        // Create the "Is_Initial" FunctionExp
        List<Exp> isInitialArgs = new ArrayList<>();
        isInitialArgs.add(varExp);
        FunctionExp isInitialExp =
                createFunctionExp(varDec.getLocation(), null,
                        new PosSymbol(varDec.getLocation(), "Is_Initial"),
                        isInitialArgs, varDec.getTy().getMathType(), booleanType);

        // Create the DotExp
        List<Exp> segments = new ArrayList<>();
        segments.add(typeNameExp);
        segments.add(isInitialExp);
        DotExp exp = new DotExp(varDec.getLocation(), segments);

        return exp;
    }

    /**
     * <p>This method uses all the {@code requires} and {@code constraint}
     * clauses from the various different sources (see below for complete list)
     * and builds the appropriate {@code assume} clause that goes at the
     * beginning an {@link AssertiveCodeBlock}.</p>
     *
     * <p>List of different places where clauses can originate from:</p>
     * <ul>
     *     <li>{@code Concept}'s {@code requires} clause.</li>
     *     <li>{@code Concept}'s module {@code constraint} clause.</li>
     *     <li>{@code Shared Variables}' {@code constraint} clause.</li>
     *     <li>{@code Concept Realization}'s {@code requires} clause.</li>
     *     <li>{@code Shared Variables}' {@code convention} clause.</li>
     *     <li>{@code Shared Variables}' {@code correspondence} clause.</li>
     *     <li>{@code constraint} clauses for all the parameters with the
     *     appropriate substitutions made.</li>
     *     <li>The {@code operation}'s {@code requires} clause with the following
     *     change if it is an implementation for a {@code concept}'s operation:</li>
     *     <li>
     *         <ul>
     *             <li>Substitute the parameter name with {@code Conc.<name>} if this
     *             is the type we are implementing in a {@code concept realization}.</li>
     *         </ul>
     *     </li>
     *     <li>Any {@code which_entails} expressions that originated from any of the
     *     clauses above.</li>
     * </ul>
     *
     * <p>See the {@code Procedure} declaration rule for more detail.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param scope The module scope to start our search.
     * @param currentBlock The current {@link AssertiveCodeBlock} we are currently generating.
     * @param locationStringsMap A map containing all the {@link Location} details.
     * @param moduleLevelRequiresClauses A list containing all the module level {@code requires} clauses.
     * @param moduleLevelConstraintClauses A map containing all the module level {@code constraint} clauses.
     * @param correspondingOperationEntry The corresponding {@link OperationEntry}.
     * @param isLocalOperation {@code true} if it is a local operation, {@code false} otherwise.
     *
     * @return The top-level assumed expression.
     */
    public static Exp createTopLevelAssumeExps(Location loc, ModuleScope scope,
            AssertiveCodeBlock currentBlock,
            Map<Location, String> locationStringsMap,
            List<AssertionClause> moduleLevelRequiresClauses,
            Map<Dec, List<AssertionClause>> moduleLevelConstraintClauses,
            OperationEntry correspondingOperationEntry, boolean isLocalOperation) {
        Exp retExp = null;

        // Add the module level requires clause. Note that we don't
        // need to add their location details to the map because
        // it is there already.
        for (AssertionClause clause : moduleLevelRequiresClauses) {
            retExp = Utilities.formConjunct(loc, retExp, clause);
        }

        // Add the module level constraint clauses.
        // Note that we don't need to add their location details to the map
        // because it is there already.
        for (Dec dec : moduleLevelConstraintClauses.keySet()) {
            for (AssertionClause clause : moduleLevelConstraintClauses.get(dec)) {
                retExp = Utilities.formConjunct(loc, retExp, clause);
            }
        }

        // TODO: Add any shared variable's constraint, convention, correspondence here.

        // Add the operation's requires clause (and any which_entails clause)
        AssertionClause requiresClause =
                correspondingOperationEntry.getRequiresClause();
        Exp requiresExp = requiresClause.getAssertionExp().clone();
        if (!VarExp.isLiteralTrue(requiresExp)) {
            retExp = Utilities.formConjunct(loc, retExp, requiresClause);

            // At the same time add the location details for these expressions.
            locationStringsMap.put(requiresExp.getLocation(),
                    "Requires Clause of "
                            + correspondingOperationEntry.getName());
            if (requiresClause.getWhichEntailsExp() != null) {
                locationStringsMap.put(requiresClause.getWhichEntailsExp()
                        .getLocation(),
                        "Which_Entails expression for clause located at "
                                + requiresClause.getWhichEntailsExp()
                                        .getLocation());
            }
        }

        // Loop through each of the parameters in the operation entry.
        ImmutableList<ProgramParameterEntry> entries =
                correspondingOperationEntry.getParameters();
        for (ProgramParameterEntry entry : entries) {
            ParameterVarDec parameterVarDec =
                    (ParameterVarDec) entry.getDefiningElement();
            PTType declaredType = entry.getDeclaredType();
            ParameterMode parameterMode = entry.getParameterMode();

            // Only deal with actual types and don't deal
            // with entry types passed in to the concept realization
            if (!(declaredType instanceof PTGeneric)) {
                // Query for the type entry in the symbol table
                NameTy nameTy = (NameTy) parameterVarDec.getTy();
                SymbolTableEntry ste =
                        Utilities.searchProgramType(loc, nameTy.getQualifier(),
                                nameTy.getName(), scope);

                ProgramTypeEntry typeEntry;
                if (ste instanceof ProgramTypeEntry) {
                    typeEntry = ste.toProgramTypeEntry(nameTy.getLocation());
                }
                else {
                    typeEntry =
                            ste.toTypeRepresentationEntry(nameTy.getLocation())
                                    .getDefiningTypeEntry();
                }

                // Obtain the original dec from the AST
                TypeFamilyDec typeFamilyDec =
                        (TypeFamilyDec) typeEntry.getDefiningElement();

                // Other than the replaces mode, constraints for the
                // other parameter modes needs to be added
                // to the requires clause as conjuncts.
                if (parameterMode != ParameterMode.REPLACES) {
                    if (!VarExp.isLiteralTrue(typeFamilyDec.getConstraint()
                            .getAssertionExp())) {
                        AssertionClause constraintClause =
                                typeFamilyDec.getConstraint();
                        AssertionClause modifiedConstraintClause =
                                getTypeConstraintClause(constraintClause, loc,
                                        null, parameterVarDec.getName(),
                                        typeFamilyDec.getExemplar(), typeEntry
                                                .getModelType(), null);
                        retExp =
                                Utilities.formConjunct(loc, retExp,
                                        modifiedConstraintClause);

                        // At the same time add the location details for these expressions.
                        locationStringsMap.put(modifiedConstraintClause
                                .getAssertionExp().getLocation(),
                                "Constraint Clause of "
                                        + parameterVarDec.getName());
                        if (constraintClause.getWhichEntailsExp() != null) {
                            locationStringsMap.put(modifiedConstraintClause
                                    .getWhichEntailsExp().getLocation(),
                                    "Which_Entails expression for clause located at "
                                            + modifiedConstraintClause
                                                    .getWhichEntailsExp()
                                                    .getLocation());
                        }
                    }
                }

                // TODO: Handle type representations from concept realizations
                /*
                // If the type is a type representation, then our requires clause
                // should really say something about the conceptual type and not
                // the variable
                if (ste instanceof RepresentationTypeEntry && !isLocal) {
                    requires =
                            Utilities.replace(requires, parameterExp,
                                    Utilities
                                            .createConcVarExp(opLocation,
                                                    parameterExp,
                                                    parameterExp
                                                            .getMathType(),
                                                    BOOLEAN));
                    requires.setLocation((Location) opLocation.clone());
                }

                // If the type is a type representation, then we need to add
                // all the type constraints from all the variable declarations
                // in the type representation.
                if (ste instanceof RepresentationTypeEntry) {
                    Exp repConstraintExp = null;
                    Set<VarExp> keys =
                            myRepresentationConstraintMap.keySet();
                    for (VarExp varExp : keys) {
                        if (varExp.getQualifier() == null
                                && varExp.getName().getName().equals(
                                pNameTy.getName().getName())) {
                            if (repConstraintExp == null) {
                                repConstraintExp =
                                        myRepresentationConstraintMap
                                                .get(varExp);
                            }
                            else {
                                Utilities.ambiguousTy(pNameTy, pNameTy
                                        .getLocation());
                            }
                        }
                    }

                    // Only do the following if the expression is not simply true
                    if (!repConstraintExp.isLiteralTrue()) {
                        // Replace the exemplar with the actual parameter variable expression
                        repConstraintExp =
                                Utilities.replace(repConstraintExp,
                                        exemplar, parameterExp);

                        // Add this to our requires clause
                        requires =
                                myTypeGraph.formConjunct(requires,
                                        repConstraintExp);
                        requires.setLocation((Location) opLocation.clone());
                    }
                }*/
            }

            // Add the current variable to our list of free variables
            currentBlock.addFreeVar(Utilities.createVarExp(parameterVarDec
                    .getLocation(), null, parameterVarDec.getName(),
                    declaredType.toMath(), null));

        }

        return retExp;
    }

    /**
     * <p>This method returns a newly created {@link VarExp}
     * with the {@link PosSymbol} and math type provided.</p>
     *
     * @param loc New {@link VarExp VarExp's} {@link Location}.
     * @param qualifier New {@link VarExp VarExp's} qualifier.
     * @param name New {@link VarExp VarExp's} name.
     * @param type New {@link VarExp VarExp's} math type.
     * @param typeValue New {@link VarExp VarExp's} math type value.
     *
     * @return The new {@link VarExp}.
     */
    public static VarExp createVarExp(Location loc, PosSymbol qualifier,
            PosSymbol name, MTType type, MTType typeValue) {
        // Create the VarExp
        VarExp exp = new VarExp(loc, qualifier, name);
        exp.setMathType(type);
        exp.setMathTypeValue(typeValue);

        return exp;
    }

    /**
     * <p>An helper method that creates a new "question mark" variable.</p>
     *
     * @param currentBlock The current {@link AssertiveCodeBlock} we are currently generating.
     * @param varExp The original expression.
     *
     * @return A {@link VCVarExp}.
     */
    public static VCVarExp createVCVarExp(AssertiveCodeBlock currentBlock,
            Exp varExp) {
        VCVarExp exp = new VCVarExp(varExp.getLocation(), varExp);
        if (currentBlock.containsFreeVar(exp)) {
            exp = createVCVarExp(currentBlock, exp);
        }

        return exp;
    }

    /**
     * <p>An helper method to print a list of expressions.</p>
     *
     * @param exps A list of {@link Exp Exps}.
     *
     * @return A formatted string.
     */
    public static String expListAsString(List<Exp> exps) {
        StringBuffer sb = new StringBuffer();

        Iterator<Exp> expIterator = exps.iterator();
        while (expIterator.hasNext()) {
            Exp nextExp = expIterator.next();
            sb.append(nextExp.asString(0, 0));

            if (expIterator.hasNext()) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * <p>An helper method that throws the appropriate message that
     * the expression type that we found isn't handled.</p>
     *
     * @param exp An expression.
     * @param loc Location where this expression was found.
     */
    public static void expNotHandled(Exp exp, Location loc) {
        String message =
                "[VCGenerator] Exp type not handled: "
                        + exp.getClass().getCanonicalName();
        throw new SourceErrorException(message, loc);
    }

    /**
     * <p>An helper method that uses the assertion expression and any
     * {@code which_entails} expressions to {@code exp} to form a new
     * conjuncted expression.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param exp The original expression.
     * @param clause An {@link AssertionClause}.
     *
     * @return A new {@link Exp}.
     */
    public static Exp formConjunct(Location loc, Exp exp, AssertionClause clause) {
        Exp retExp;

        // Add the assertion expression
        Exp assertionExp = clause.getAssertionExp().clone();
        if (exp == null) {
            retExp = assertionExp;
        }
        else {
            retExp = InfixExp.formConjunct(loc, exp, assertionExp);
        }

        // Add any which_entails
        if (clause.getWhichEntailsExp() != null) {
            retExp =
                    InfixExp.formConjunct(loc, retExp, clause
                            .getWhichEntailsExp());
        }

        return retExp;
    }

    /**
     * <p>Returns the math type for "Z".</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param scope The module scope to start our search.
     *
     *
     * @return The <code>MTType</code> for "Z".
     */
    public static MTType getMathTypeZ(Location loc, ModuleScope scope) {
        // Locate "Z" (Integer)
        MathSymbolEntry mse = searchMathSymbol(loc, "Z", scope);
        MTType Z = null;
        try {
            Z = mse.getTypeValue();
        }
        catch (SymbolNotOfKindTypeException e) {
            notAType(mse, loc);
        }

        return Z;
    }

    /**
     * <p>Given the original {@code constraint} clause, use the provided information
     * on the actual parameter variable to substitute the {@code exemplar} in the
     * {@code constraint} clause and create a new {@link AssertionClause}.</p>
     *
     * @param originalConstraintClause The {@link AssertionClause} containing the original
     *                                 {@code constraint} clause.
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param qualifier The parameter variable's qualifier.
     * @param name The parameter variable's name.
     * @param exemplarName The {@code exemplar} name for the corresponding type.
     * @param type The mathematical type associated with this type.
     * @param typeValue The mathematical type value associated with this type.
     *
     * @return A modified {@link AssertionClause} containing the new {@code constraint}
     * clause.
     */
    public static AssertionClause getTypeConstraintClause(AssertionClause originalConstraintClause, Location loc,
            PosSymbol qualifier, PosSymbol name, PosSymbol exemplarName, MTType type, MTType typeValue) {
        // Create a variable expression from the declared variable
        VarExp varDecExp = Utilities.createVarExp(loc, qualifier, name, type, typeValue);

        // Create a variable expression from the type exemplar
        VarExp exemplar = Utilities.createVarExp(loc, null, exemplarName, type, typeValue);

        // Create a replacement map
        Map<Exp, Exp> substitutions = new HashMap<>();
        substitutions.put(exemplar, varDecExp);

        // Create new assertion clause by replacing the exemplar with the actual
        Location newLoc = loc.clone();
        Exp constraintWithReplacements =
                originalConstraintClause.getAssertionExp().substitute(substitutions);
        Exp whichEntailsWithReplacements = null;
        if (originalConstraintClause.getWhichEntailsExp() != null) {
            whichEntailsWithReplacements =
                    originalConstraintClause.getWhichEntailsExp().substitute(substitutions);
        }

        return new AssertionClause(newLoc, AssertionClause.ClauseType.CONSTRAINT,
                constraintWithReplacements, whichEntailsWithReplacements);
    }

    /**
     * <p>Given the original {@code ensures} clause, use the provided
     * information on the actual parameter variable to substitute the {@code exemplar} in
     * the {@code initialization ensures} clause and create a new {@link AssertionClause}.</p>
     *
     * @param originalEnsuresClause The {@link AssertionClause} containing the
     *                              original {@code ensures} clause.
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param qualifier The parameter variable's qualifier.
     * @param name The parameter variable's name.
     * @param exemplarName The {@code exemplar} name for the corresponding type.
     * @param type The mathematical type associated with this type.
     * @param typeValue The mathematical type value associated with this type.
     *
     * @return A modified {@link AssertionClause} containing the new
     * {@code ensures} clause.
     */
    public static AssertionClause getTypeEnsuresClause(AssertionClause originalEnsuresClause,
            Location loc, PosSymbol qualifier, PosSymbol name,
            PosSymbol exemplarName, MTType type, MTType typeValue) {
        // Create a variable expression from the declared variable
        VarExp varDecExp = Utilities.createVarExp(loc, qualifier, name, type, typeValue);

        // Create a variable expression from the type exemplar
        VarExp exemplar = Utilities.createVarExp(loc, null, exemplarName, type, typeValue);

        // Create a replacement map
        Map<Exp, Exp> substitutions = new HashMap<>();
        substitutions.put(exemplar, varDecExp);

        // Create new assertion clause by replacing the exemplar with the actual
        Location newLoc = loc.clone();
        Exp constraintWithReplacements =
                originalEnsuresClause.getAssertionExp().substitute(substitutions);
        Exp whichEntailsWithReplacements = null;
        if (originalEnsuresClause.getWhichEntailsExp() != null) {
            whichEntailsWithReplacements =
                    originalEnsuresClause.getWhichEntailsExp().substitute(substitutions);
        }

        return new AssertionClause(newLoc, AssertionClause.ClauseType.ENSURES,
                constraintWithReplacements, whichEntailsWithReplacements);
    }

    /**
     * <p>Given the name of an operation check to see if it is a
     * local operation</p>
     *
     * @param name The name of the operation.
     * @param scope The module scope we are searching.
     *
     * @return {@code true} if it is a local operation, {@code false} otherwise.
     */
    public static boolean isLocationOperation(String name, ModuleScope scope) {
        boolean isIn;

        // Query for the corresponding operation
        List<SymbolTableEntry> entries =
                scope.query(new NameQuery(null, name,
                        ImportStrategy.IMPORT_NONE,
                        FacilityStrategy.FACILITY_IGNORE, true));

        // Not found
        if (entries.size() == 0) {
            isIn = false;
        }
        // Found one
        else if (entries.size() == 1) {
            // If the operation is declared here, then it will be an OperationEntry.
            // Thus it is a local operation.
            isIn = entries.get(0) instanceof OperationEntry;
        }
        // Found more than one
        else {
            //This should be caught earlier, when the duplicate symbol is
            //created
            throw new RuntimeException();
        }

        return isIn;
    }

    /**
     * <p>An helper method that throws the appropriate message that
     * the symbol table entry that we found isn't a type.</p>
     *
     * @param entry A symbol table entry.
     * @param loc Location where this entry was found.
     */
    public static void notAType(SymbolTableEntry entry, Location loc) {
        throw new SourceErrorException("[VCGenerator] "
                + entry.getSourceModuleIdentifier()
                        .fullyQualifiedRepresentation(entry.getName())
                + " is not known to be a type.", loc);
    }

    /**
     * <p>An helper method that throws the appropriate no module found
     * message.</p>
     *
     * @param loc Location where this module name was found.
     */
    public static void noSuchModule(Location loc) {
        throw new SourceErrorException(
                "[VCGenerator] Module does not exist or is not in scope.", loc);
    }

    /**
     * <p>An helper method that throws the appropriate no symbol found
     * message.</p>
     *
     * @param qualifier The symbol's qualifier.
     * @param symbolName The symbol's name.
     * @param loc Location where this symbol was found.
     */
    public static void noSuchSymbol(PosSymbol qualifier, String symbolName,
            Location loc) {
        String message;

        if (qualifier == null) {
            message = "[VCGenerator] No such symbol: " + symbolName;
        }
        else {
            message =
                    "[VCGenerator] No such symbol in module: "
                            + qualifier.getName() + "." + symbolName;
        }

        throw new SourceErrorException(message, loc);
    }

    /**
     * <p>This method is used to check for there is a path
     * in either direction from {@code seq1} to {@code seq2}
     * in the reduction tree.</p>
     *
     * @param g The reduction tree.
     * @param seq1 A {@link Sequent}.
     * @param seq2 Another {@link Sequent}.
     *
     * @return {@code true} if there is a path in the reduction tree,
     * {@code false} otherwise.
     */
    public static boolean pathExist(DirectedGraph<Sequent, DefaultEdge> g, Sequent seq1, Sequent seq2) {
        boolean retVal = true;

        // Check to see if the seq1 and seq2 is in the tree.
        // YS: Should be in here, but just in case it isn't...
        if (g.containsVertex(seq1) && g.containsVertex(seq2)) {
            // Check to see if there is a path from seq1 to seq2 or from seq2 to seq1.
            // YS: We choose to use Dijkstra's algorithm, but we could chose
            // other ones if needed.
            ShortestPathAlgorithm<Sequent, DefaultEdge> pathAlgorithm = new DijkstraShortestPath<>(g);
            if (pathAlgorithm.getPath(seq1, seq2) == null &&
                    pathAlgorithm.getPath(seq2, seq1) == null) {
                retVal = false;
            }
        }
        else {
            retVal = false;
        }

        return retVal;
    }

    /**
     * <p>This method is used to check for there are paths from
     * {@code originalSequent} to each sequent in {@code resultSequents}
     * in the reduction tree.</p>
     *
     * @param g The reduction tree.
     * @param originalSequent The original {@link Sequent}.
     * @param resultSequents The {@link Sequent} that resulted from the
     *                       sequent reduction applications.
     *
     * @return {@code true} if all the {@link Sequent} in {@code resultSequents}
     * have a path from {@code originalSequent} in the reduction tree,
     * {@code false} otherwise.
     */
    public static boolean pathsExist(DirectedGraph<Sequent, DefaultEdge> g,
            Sequent originalSequent, List<Sequent> resultSequents) {
        boolean retVal = true;

        // Check to see if the originalSequent is in the tree.
        // YS: Should be in here, but just in case it isn't...
        if (!g.containsVertex(originalSequent)) {
            retVal = false;
        }

        // Check to see if there is a path from originalSequent to each
        // sequent in resultSequents.
        // YS: We choose to use Dijkstra's algorithm, but we could chose
        // other ones if needed.
        ShortestPathAlgorithm<Sequent, DefaultEdge> pathAlgorithm = new DijkstraShortestPath<>(g);
        Iterator<Sequent> iterator = resultSequents.iterator();
        while(iterator.hasNext() && retVal) {
            Sequent next = iterator.next();

            // Check to see if there is a path from
            if (pathAlgorithm.getPath(originalSequent, next) == null) {
                retVal = false;
            }
        }

        return retVal;
    }

    /**
     * <p>Given a math symbol name, locate and return
     * the {@link MathSymbolEntry} stored in the
     * symbol table.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param name The string name of the math symbol.
     * @param scope The module scope to start our search.
     *
     * @return A {@link MathSymbolEntry} from the
     *         symbol table.
     */
    public static MathSymbolEntry searchMathSymbol(Location loc, String name,
            ModuleScope scope) {
        // Query for the corresponding math symbol
        MathSymbolEntry ms = null;
        try {
            ms =
                    scope.queryForOne(
                            new UnqualifiedNameQuery(name,
                                    ImportStrategy.IMPORT_RECURSIVE,
                                    FacilityStrategy.FACILITY_IGNORE, true,
                                    true)).toMathSymbolEntry(loc);
        }
        catch (NoSuchSymbolException nsse) {
            noSuchSymbol(null, name, loc);
        }
        catch (DuplicateSymbolException dse) {
            //This should be caught earlier, when the duplicate symbol is
            //created
            throw new RuntimeException(dse);
        }

        return ms;
    }

    /**
     * <p>Given the qualifier, name and the list of argument
     * types, locate and return the {@link OperationEntry}
     * stored in the symbol table.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param qualifier The qualifier of the operation.
     * @param name The name of the operation.
     * @param argTypes The list of argument types.
     * @param scope The module scope to start our search.
     *
     * @return An {@link OperationEntry} from the
     *         symbol table.
     */
    public static OperationEntry searchOperation(Location loc,
            PosSymbol qualifier, PosSymbol name, List<PTType> argTypes,
            ModuleScope scope) {
        // Query for the corresponding operation
        OperationEntry op = null;
        try {
            op =
                    scope.queryForOne(new OperationQuery(qualifier, name,
                            argTypes));
        }
        catch (NoSuchSymbolException nsse) {
            noSuchSymbol(null, name.getName(), loc);
        }
        catch (DuplicateSymbolException dse) {
            //This should be caught earlier, when the duplicate operation is
            //created
            throw new RuntimeException(dse);
        }

        return op;
    }

    /**
     * <p>Given the name of the type locate and return
     * the {@link SymbolTableEntry} stored in the
     * symbol table.</p>
     *
     * @param loc The location in the AST that we are
     *            currently visiting.
     * @param qualifier The qualifier of the type.
     * @param name The name of the type.
     * @param scope The module scope to start our search.
     *
     * @return A {@link SymbolTableEntry} from the
     *         symbol table.
     */
    public static SymbolTableEntry searchProgramType(Location loc,
            PosSymbol qualifier, PosSymbol name, ModuleScope scope) {
        SymbolTableEntry retEntry = null;

        List<SymbolTableEntry> entries =
                scope.query(new NameQuery(qualifier, name,
                        ImportStrategy.IMPORT_NAMED,
                        FacilityStrategy.FACILITY_INSTANTIATE, true));

        if (entries.size() == 0) {
            noSuchSymbol(qualifier, name.getName(), loc);
        }
        else if (entries.size() == 1) {
            retEntry = entries.get(0).toProgramTypeEntry(loc);
        }
        else {
            // When we have more than one, it means that we have a
            // type representation. In that case, we just need the
            // type representation.
            for (int i = 0; i < entries.size() && retEntry == null; i++) {
                SymbolTableEntry ste = entries.get(i);
                if (ste instanceof TypeRepresentationEntry) {
                    retEntry = ste.toTypeRepresentationEntry(loc);
                }
            }

            // Throw duplicate symbol error if we don't have a type
            // representation
            if (retEntry == null) {
                //This should be caught earlier, when the duplicate type is
                //created
                throw new RuntimeException();
            }
        }

        return retEntry;
    }

    /**
     * <p>An helper method that throws the appropriate raw type
     * not handled message.</p>
     *
     * @param ty A raw type.
     * @param loc Location where this raw type was found.
     */
    public static void tyNotHandled(Ty ty, Location loc) {
        throw new SourceErrorException("[VCGenerator] Ty not handled: "
                + ty.toString(), loc);
    }

    // ===========================================================
    // Private Methods
    // ===========================================================

}