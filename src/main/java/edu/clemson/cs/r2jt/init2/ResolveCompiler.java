/**
 * ResolveCompiler.java
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
package edu.clemson.cs.r2jt.init2;

import edu.clemson.cs.r2jt.archiving.Archiver;
import edu.clemson.cs.r2jt.congruenceclassprover.CongruenceClassProver;
import edu.clemson.cs.r2jt.congruenceclassprover.SMTProver;
import edu.clemson.cs.r2jt.init2.file.ModuleType;
import edu.clemson.cs.r2jt.init2.file.ResolveFile;
import edu.clemson.cs.r2jt.init2.misc.CompileEnvironment;
import edu.clemson.cs.r2jt.misc.Flag;
import edu.clemson.cs.r2jt.misc.FlagDependencies;
import edu.clemson.cs.r2jt.misc.FlagDependencyException;
import edu.clemson.cs.r2jt.rewriteprover.AlgebraicProver;
import edu.clemson.cs.r2jt.rewriteprover.Prover;
import edu.clemson.cs.r2jt.translation.CTranslator;
import edu.clemson.cs.r2jt.translation.JavaTranslator;
import edu.clemson.cs.r2jt.typeandpopulate.MathSymbolTableBuilder;
import edu.clemson.cs.r2jt.vcgeneration.VCGenerator;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ANTLRInputStream;

/**
 * TODO: Description for this class
 */
public class ResolveCompiler {

    private boolean myCompileAllFilesInDir = false;
    private String[] myCompilerArgs;
    private final String myCompilerVersion = "Summer 2015";
    private final List<File> myFilesToCompile;

    // ===========================================================
    // Flag Strings
    // ===========================================================

    private static final String FLAG_DESC_NO_DEBUG =
            "Remove debugging statements from the compiler output.";
    private static final String FLAG_DESC_ERRORS_ON_STD_OUT =
            "Change the output to be more web-friendly for the Web Interface.";
    private static final String FLAG_DESC_XML_OUT =
            "Changes the compiler output files to XML";
    private static final String FLAG_DESC_WEB =
            "Change the output to be more web-friendly for the Web Interface.";
    private static final String FLAG_SECTION_GENERAL = "General";
    private static final String FLAG_SECTION_NAME = "Output";

    // ===========================================================
    // Flags
    // ===========================================================

    public static final Flag FLAG_HELP =
            new Flag(FLAG_SECTION_GENERAL, "help",
                    "Displays this help information.");

    public static final Flag FLAG_EXTENDED_HELP =
            new Flag(FLAG_SECTION_GENERAL, "xhelp",
                    "Displays all flags, including development flags and many others "
                            + "not relevant to most users.");

    /**
     * <p>Tells the compiler to send error messages to std_out instead
     * of std_err.</p>
     */
    public static final Flag FLAG_ERRORS_ON_STD_OUT =
            new Flag(FLAG_SECTION_NAME, "errorsOnStdOut",
                    FLAG_DESC_ERRORS_ON_STD_OUT, Flag.Type.HIDDEN);

    /**
     * <p>Tells the compiler to remove debugging messages from the compiler
     * output.</p>
     */
    public static final Flag FLAG_NO_DEBUG =
            new Flag(FLAG_SECTION_NAME, "nodebug", FLAG_DESC_NO_DEBUG);

    /**
     * <p>Tells the compiler to remove debugging messages from the compiler
     * output.</p>
     */
    public static final Flag FLAG_XML_OUT =
            new Flag(FLAG_SECTION_NAME, "XMLout", FLAG_DESC_XML_OUT);

    /**
     * <p>The main web interface flag.  Tells the compiler to modify
     * some of the output to be more user-friendly for the web.</p>
     */
    public static final Flag FLAG_WEB =
            new Flag(FLAG_SECTION_NAME, "webinterface", FLAG_DESC_WEB,
                    Flag.Type.HIDDEN);

    // ===========================================================
    // Constructors
    // ===========================================================

    public ResolveCompiler(String[] args) {
        myCompilerArgs = args;
        myFilesToCompile = new LinkedList<File>();

        // Make sure the flag dependencies are set
        setUpFlagDependencies();
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    public void invokeCompiler() {
        // Handle all arguments to the compiler
        CompileEnvironment compileEnvironment = handleCompileArgs();

        // Print Compiler Messages
        System.out.println("RESOLVE Compiler/Verifier - " + myCompilerVersion
                + " Version.");
        System.out.println("  Use -help flag for options.");

        // Compile files/directories listed in the argument list
        compileFiles(myFilesToCompile, compileEnvironment);
    }

    // ===========================================================
    // Private Methods
    // ===========================================================

    private void compileFiles(List<File> fileList,
            CompileEnvironment compileEnvironment) {
        // Compile all files
        for (File file : fileList) {
            // Recursively compile all RESOLVE files in the specified directory
            if (file.isDirectory()) {
                if (myCompileAllFilesInDir) {
                    compileFilesInDir(file, compileEnvironment);
                }
                else {
                    System.err.println("Skipping directory " + file.getName());
                }
            }
            // Error if we can't locate the file
            else if (!file.isFile()) {
                System.err.println("Cannot find the file " + file.getName()
                        + " in this directory.");
            }
            // Process this file
            else {
                ModuleType moduleType = getModuleType(file.getName());

                // Print error message if it is not a valid RESOLVE file
                if (moduleType == null) {
                    System.err.println("The file " + file.getName()
                            + " is not a RESOLVE file.");
                }
                else {
                    // Convert to the internal representation of a RESOLVE file
                    String name = getFileName(file.getName(), moduleType);
                    String workspacePath =
                            compileEnvironment.getWorkspaceDir()
                                    .getAbsolutePath();
                    List<String> pkgList =
                            getPackageList(file.getAbsolutePath(),
                                    workspacePath);
                    ANTLRInputStream inputStream =
                            new ANTLRInputStream(file.getAbsolutePath());
                    ResolveFile f =
                            new ResolveFile(name, moduleType, inputStream,
                                    workspacePath, pkgList, file
                                            .getAbsolutePath());

                    // Invoke the compiler
                    compileMainFile(f, compileEnvironment);
                }
            }
        }
    }

    private void compileFilesInDir(File directory,
            CompileEnvironment compileEnvironment) {
        File[] filesInDir = directory.listFiles();
        List<File> fileList = new LinkedList<File>();

        // Obtain all RESOLVE files in the directory and add those as new files
        // we need to compile.
        for (File f : filesInDir) {
            if (getModuleType(f.getName()) != null) {
                fileList.add(f);
            }
        }

        // Compile these files first
        compileFiles(fileList, compileEnvironment);
    }

    private void compileMainFile(ResolveFile file,
            CompileEnvironment compileEnvironment) {
        Controller controller = new Controller(compileEnvironment);
        controller.compileTargetFile(file);
    }

    /*
     * Converts the specified pathname to a <code>File</code> representing
     * the absolute path to the pathname.
     */
    private File getAbsoluteFile(String pathname) {
        return new File(pathname).getAbsoluteFile();
    }

    private String getFileName(String fileName, ModuleType moduleType) {
        return fileName.substring(0, fileName.lastIndexOf(moduleType
                .getExtension()) - 1);
    }

    private List<String> getPackageList(String filePath, String workspacePath) {
        // Obtain the relative path using the workspace path and current file path.
        String relativePath = filePath.substring(workspacePath.length() + 1);

        // Add all package names using the Java Collections
        List<String> pkgList = new LinkedList<String>();
        Collections.addAll(pkgList, relativePath.split(Pattern
                .quote(File.separator)));

        return pkgList;
    }

    private File getWorkspaceDir(String path) {
        File resolvePath = null;
        String resolveDirName = "RESOLVE";

        // Look in the specified path
        if (path != null) {
            resolvePath = new File(path);

            // Not a valid path
            if (!resolvePath.exists()) {
                System.err.println("Warning: Directory '" + resolveDirName
                        + "' not found, using current " + "directory.");

                resolvePath = null;
            }
        }

        // Attempt to locate the folder containing the folder "RESOLVE"
        if (resolvePath == null) {
            File currentDir = getAbsoluteFile("");

            // Check to see if our current path is the path that contains
            // the RESOLVE folder.
            if (currentDir.getName().equals(resolveDirName)) {
                resolvePath = currentDir;
            }

            // Attempt to locate the "RESOLVE" folder
            while ((resolvePath == null)
                    && (currentDir.getParentFile() != null)) {
                currentDir = currentDir.getParentFile();
                if (currentDir.getName().equals(resolveDirName)) {
                    // We store the path that contains the "RESOLVE" folder
                    resolvePath = currentDir.getParentFile();
                }
            }

            // Probably will crash because we can't find "RESOLVE"
            if (resolvePath == null) {
                System.err.println("Warning: Directory '" + resolveDirName
                        + "' not found, using current directory.");

                resolvePath = getAbsoluteFile("");
            }
        }

        return resolvePath;
    }

    private CompileEnvironment handleCompileArgs() {
        CompileEnvironment compileEnvironment = null;
        try {
            compileEnvironment = new CompileEnvironment(myCompilerArgs);

            // Change Workspace Directory
            String workspaceDir = null;

            // Handle remaining arguments
            String[] remainingArgs = compileEnvironment.getRemainingArgs();
            if (remainingArgs.length >= 1
                    && !compileEnvironment.flags.isFlagSet(FLAG_HELP)) {
                for (int i = 0; i < remainingArgs.length; i++) {
                    if (remainingArgs[i].equals("-R")) {
                        myCompileAllFilesInDir = true;
                    }
                    else if (remainingArgs[i].equals("-PVCs")) {
                        compileEnvironment.setPerformanceFlag();
                    }
                    else if (remainingArgs[i].equalsIgnoreCase("-workspaceDir")) {
                        if (i + 1 < remainingArgs.length) {
                            i++;
                            workspaceDir = remainingArgs[i];
                        }
                    }
                    else if (remainingArgs[i].equals("-o")) {
                        if (i + 1 < remainingArgs.length) {
                            String outputFile;
                            i++;
                            outputFile = remainingArgs[i];
                            compileEnvironment.setOutputFileName(outputFile);
                        }
                    }
                    else {
                        myFilesToCompile.add(getAbsoluteFile(remainingArgs[i]));
                    }
                }

                // Turn off debugging messages
                if (compileEnvironment.flags
                        .isFlagSet(ResolveCompiler.FLAG_NO_DEBUG)) {
                    compileEnvironment.setDebugOff();
                }

                // Store the workspace directory to the compile environment
                compileEnvironment
                        .setWorkspaceDir(getWorkspaceDir(workspaceDir));

                // Store the symbol table
                MathSymbolTableBuilder symbolTable =
                        new MathSymbolTableBuilder();
                compileEnvironment.setSymbolTable(symbolTable);
            }
            else {
                printHelpMessage(compileEnvironment);
            }
        }
        catch (FlagDependencyException fde) {
            System.out.println("RESOLVE Compiler/Verifier - "
                    + myCompilerVersion + " Version.");
            System.out.println("  Use -help flag for options.");
            System.err.println(fde.getMessage());
        }

        return compileEnvironment;
    }

    /*
     * Determines if the specified filename is a valid RESOLVE file type.
     */
    private ModuleType getModuleType(String filename) {
        ModuleType type = null;

        if (filename.endsWith(ModuleType.THEORY.getExtension())) {
            type = ModuleType.THEORY;
        }
        else if (filename.endsWith(ModuleType.CONCEPT.getExtension())) {
            type = ModuleType.CONCEPT;
        }
        else if (filename.endsWith(ModuleType.ENHANCEMENT.getExtension())) {
            type = ModuleType.ENHANCEMENT;
        }
        else if (filename.endsWith(ModuleType.REALIZATION.getExtension())) {
            type = ModuleType.REALIZATION;
        }
        else if (filename.endsWith(ModuleType.FACILITY.getExtension())) {
            type = ModuleType.FACILITY;
        }
        else if (filename.endsWith(ModuleType.PROFILE.getExtension())) {
            type = ModuleType.PROFILE;
        }

        return type;
    }

    private void printHelpMessage(CompileEnvironment e) {
        System.out.println("Usage: java -jar RESOLVE.jar [options] <files>");
        System.out.println("where options include:");

        printOptions(e);
    }

    private void printOptions(CompileEnvironment e) {
        System.out.println("  -R             Recurse through directories.");
        System.out.println("  -D <dir>       Use <dir> as the main directory.");
        System.out.println("  -translate     Translate to Java code.");
        System.out.println("  -PVCs           Generate verification "
                + "conditions for performance.");
        System.out.println("  -VCs           Generate verification "
                + "conditions.");
        System.out.println("  -isabelle      Used with -VCs to generate "
                + "VCs for Isabelle.");

        System.out.println(FlagDependencies.getListingString(e.flags
                .isFlagSet(FLAG_EXTENDED_HELP)));
    }

    /*
     * This method sets up dependencies between compiler flags.  If you are
     * integrating your module into the compiler flag management system, this is
     * where to do it.
     */
    private synchronized void setUpFlagDependencies() {
        if (!FlagDependencies.isSealed()) {
            setUpFlags();
            Prover.setUpFlags();
            JavaTranslator.setUpFlags();
            CTranslator.setUpFlags();
            Archiver.setUpFlags();
            VCGenerator.setUpFlags();
            AlgebraicProver.setUpFlags();
            CongruenceClassProver.setUpFlags();
            SMTProver.setUpFlags();
            FlagDependencies.seal();
        }
    }

    private void setUpFlags() {
        FlagDependencies.addImplies(FLAG_EXTENDED_HELP, FLAG_HELP);
        FlagDependencies.addRequires(FLAG_ERRORS_ON_STD_OUT, FLAG_WEB);
        FlagDependencies.addImplies(FLAG_WEB, FLAG_ERRORS_ON_STD_OUT);
        FlagDependencies.addImplies(FLAG_WEB, FLAG_NO_DEBUG);
        FlagDependencies.addImplies(FLAG_WEB, FLAG_XML_OUT);
        FlagDependencies.addImplies(FLAG_WEB, Prover.FLAG_NOGUI);
    }

}