/*
 * ScopeSearchPath.java
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

import edu.clemson.cs.r2jt.typeandpopulate.searchers.TableSearcher;
import edu.clemson.cs.r2jt.typeandpopulate.entry.SymbolTableEntry;
import java.util.List;

/**
 * <p>A <code>ScopeSearchPath</code> defines which {@link Scope Scope}s
 * should be searched for symbol table matches and in what order.</p>
 * 
 * <p>All symbol table searches take place in the context of a 
 * <em>source scope</em>, which is the scope from which the request is made.  
 * I.e., if a procedure called <code>Foo</code> references a symbol called 
 * <code>X</code>, triggering a look-up for what <code>X</code> could be, then 
 * the scope for <code>Foo</code> is the source scope.</p>
 * 
 * <p>Given a {@link TableSearcher TableSearcher}, a source scope, and a 
 * {@link ScopeRepository ScopeRepository} containing any imports, a 
 * <code>ScopeSearchPath</code> will apply the <code>TableSearcher</code> 
 * appropriately to any <code>Scope</code>s that should be searched.</p>
 */
public interface ScopeSearchPath {

    /**
     * <p>Applies the given {@link TableSearcher TableSearcher} to the 
     * appropriate {@link Scope Scope}s, given a source scope and a
     * {@link ScopeRepository ScopeRepository} containing any imports, returning
     * a list of matching {@link SymbolTableEntry SymbolTableEntry}s.</p>
     * 
     * <p>If there are no matches, returns an empty list.  If more than one 
     * match is found and <code>searcher</code> expects no more than one match,
     * throws a {@link DuplicateSymbolException DuplicateSymbolException}.</p>
     * 
     * @param searcher A <code>TableSearcher</code> to apply to each scope along
     *            the search path.
     * @param table A symbol table containing any referenced modules.
     * @param context The current scope from which the search was spawned.
     * 
     * @return A list of matches.
     */
    public <E extends SymbolTableEntry> List<E> searchFromContext(
            TableSearcher<E> searcher, Scope source, ScopeRepository repo)
            throws DuplicateSymbolException;
}
