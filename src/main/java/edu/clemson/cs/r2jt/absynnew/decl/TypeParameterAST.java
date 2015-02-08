/**
 * TypeParameterAST.java
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
package edu.clemson.cs.r2jt.absynnew.decl;

import edu.clemson.cs.r2jt.parsing.ResolveParser;
import org.antlr.v4.runtime.Token;

/**
 * <p>Certain modules can be parameterized by some generic; e.g. <tt>T</tt>,
 * <tt>Entry</tt>, etc. These generics would, ideally, be stored as a list
 * of <code>PosSymbol</code>s in the enclosing module,</p>
 */
public class TypeParameterAST extends DeclAST {

    public TypeParameterAST(Token start, Token stop, Token name) {
        super(start, stop, name);
    }

    public TypeParameterAST(ResolveParser.TypeParameterDeclContext ctx) {
        this(ctx.getStart(), ctx.getStop(), ctx.name);
    }
}