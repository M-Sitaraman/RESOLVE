/*
 * QuantExp.java
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
package edu.clemson.cs.r2jt.absyn;

import edu.clemson.cs.r2jt.collections.Iterator;
import edu.clemson.cs.r2jt.collections.List;
import edu.clemson.cs.r2jt.data.Location;

public class QuantExp extends Exp {

    // ===========================================================
    // Constants
    // ===========================================================

    public static final int NONE = 0;
    public static final int FORALL = 1;
    public static final int EXISTS = 2;
    public static final int UNIQUE = 3;

    // ===========================================================
    // Variables
    // ===========================================================

    /** The location member. */
    private Location location;

    /** The operator member. */
    private int operator;

    /** The vars member. */
    private List<MathVarDec> vars;

    /** The where member. */
    private Exp where;

    /** The body member. */
    private Exp body;

    // ===========================================================
    // Constructors
    // ===========================================================

    public QuantExp() {};

    public QuantExp(Location location, int operator, List<MathVarDec> vars,
            Exp where, Exp body) {
        this.location = location;
        this.operator = operator;
        this.vars = vars;
        this.where = where;
        this.body = body;
    }

    public Exp substituteChildren(java.util.Map<Exp, Exp> substitutions) {
        return new QuantExp(location, operator, vars, substitute(where,
                substitutions), substitute(body, substitutions));
    }

    // ===========================================================
    // Accessor Methods
    // ===========================================================

    // -----------------------------------------------------------
    // Get Methods
    // -----------------------------------------------------------

    /** Returns the value of the location variable. */
    public Location getLocation() {
        return location;
    }

    /** Returns the value of the operator variable. */
    public int getOperator() {
        return operator;
    }

    /** Returns the value of the vars variable. */
    public List<MathVarDec> getVars() {
        return vars;
    }

    /** Returns the value of the where variable. */
    public Exp getWhere() {
        return where;
    }

    /** Returns the value of the body variable. */
    public Exp getBody() {
        return body;
    }

    // -----------------------------------------------------------
    // Set Methods
    // -----------------------------------------------------------

    /** Sets the location variable to the specified value. */
    public void setLocation(Location location) {
        this.location = location;
    }

    /** Sets the operator variable to the specified value. */
    public void setOperator(int operator) {
        this.operator = operator;
    }

    /** Sets the vars variable to the specified value. */
    public void setVars(List<MathVarDec> vars) {
        this.vars = vars;
    }

    /** Sets the where variable to the specified value. */
    public void setWhere(Exp where) {
        this.where = where;
    }

    /** Sets the body variable to the specified value. */
    public void setBody(Exp body) {
        this.body = body;
    }

    // ===========================================================
    // Public Methods
    // ===========================================================

    /** Accepts a ResolveConceptualVisitor. */
    public void accept(ResolveConceptualVisitor v) {
        v.visitQuantExp(this);
    }

    /** Returns a formatted text string of this class. */
    public String asString(int indent, int increment) {

        StringBuffer sb = new StringBuffer();

        printSpace(indent, sb);
        sb.append("QuantExp\n");

        printSpace(indent + increment, sb);
        sb.append(printConstant(operator) + "\n");

        if (vars != null) {
            sb.append(vars.asString(indent + increment, increment));
        }

        if (where != null) {
            sb.append(where.asString(indent + increment, increment));
        }

        if (body != null) {
            sb.append(body.asString(indent + increment, increment));
        }

        return sb.toString();
    }

    public String toString(int indent) {
        //Environment   env	= Environment.getInstance();
        //if(env.isabelle()){return toIsabelleString(indent);};

        StringBuffer sb = new StringBuffer();
        printSpace(indent, sb);
        if (where != null)
            sb.append(where.toString(1));
        sb.append(printConstant(operator));

        List<MathVarDec> list = vars;
        Iterator<MathVarDec> i = list.iterator();

        while (i.hasNext()) {
            MathVarDec tmp = i.next();
            sb.append(" ");
            sb.append(tmp.toString(0));
            if (i.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(" such that ");
        if (body != null)
            sb.append(body.toString(0));
        return sb.toString();
    }

    public String toIsabelleString(int indent) {
        StringBuffer sb = new StringBuffer();
        printSpace(indent, sb);
        if (where != null)
            sb.append(where.toString(1));
        sb.append(printIsabelleConstant(operator));

        List<MathVarDec> list = vars;
        Iterator<MathVarDec> i = list.iterator();

        while (i.hasNext()) {
            MathVarDec tmp = i.next();
            sb.append(" ");
            sb.append(tmp.toString(0));
        }
        sb.append(", ");
        if (body != null)
            sb.append(body.toString(0));
        return sb.toString();
    }

    public String split(int indent) {
        StringBuffer sb = new StringBuffer();
        printSpace(indent, sb);
        if (where != null)
            sb.append(where.toString(1));
        sb.append(printConstant(operator));

        List<MathVarDec> list = vars;
        Iterator<MathVarDec> i = list.iterator();

        while (i.hasNext()) {
            MathVarDec tmp = i.next();
            sb.append(" ");
            sb.append(tmp.toString(0));
        }
        sb.append(", ");
        if (body != null)
            sb.append(body.toString(0));
        return sb.toString();
    }

    /** Returns true if the variable is found in any sub expression   
        of this one. **/
    public boolean containsVar(String varName, boolean IsOldExp) {
        if (where != null && where.containsVar(varName, IsOldExp)) {
            return true;
        }
        if (body.containsVar(varName, IsOldExp)) {
            return true;
        }
        return false;
    }

    public Object clone() {
        QuantExp clone = new QuantExp();

        clone.setOperator(this.operator);

        List<MathVarDec> newVars = new List<MathVarDec>();
        Iterator<MathVarDec> i = vars.iterator();
        while (i.hasNext()) {
            newVars.add(i.next().copy());
        }
        clone.setVars(newVars);
        if (where != null)
            clone.setWhere(Exp.copy(this.getWhere()));
        if (body != null)
            clone.setBody(Exp.copy(this.getBody()));
        clone.setLocation(this.getLocation());
        return clone;
    }

    public Exp replace(Exp old, Exp replacement) {
        if (!(old instanceof QuantExp)) {
            if (where != null) {
                Exp whr = Exp.replace(where, old, replacement);
                if (whr != null)
                    this.setWhere(whr);
            }
            if (body != null) {
                Exp bdy = Exp.replace(body, old, replacement);
                if (bdy != null)
                    this.setBody(bdy);
                // Not used anywhere below. - YS 
                //String str = bdy.toString(0, 0);
            }
            if (vars != null && old instanceof VarExp
                    && replacement instanceof VarExp) {
                //What if Replacement isn't VarExp?
                List<MathVarDec> newVars = new List<MathVarDec>();
                Iterator<MathVarDec> i = vars.iterator();
                while (i.hasNext()) {
                    MathVarDec tmp = i.next();
                    if (tmp.getName().toString().equals(
                            ((VarExp) old).getName().toString())) {
                        tmp.setName(((VarExp) replacement).getName());
                    }

                    newVars.add(tmp);
                }
            }
            return this;
        }
        else
            return this;
    }

    private String printConstant(int k) {
        StringBuffer sb = new StringBuffer();
        switch (k) {
        case 1:
            sb.append("for all");
            break;
        case 2:
            sb.append("there exists");
            break;
        case 3:
            sb.append("UNIQUE");
            break;
        default:
            sb.append(k);
        }
        return sb.toString();
    }

    private String printIsabelleConstant(int k) {
        StringBuffer sb = new StringBuffer();
        switch (k) {
        case 1:
            sb.append("ALL");
            break;
        case 2:
            sb.append("there exists");
            break;
        case 3:
            sb.append("UNIQUE");
            break;
        default:
            sb.append(k);
        }
        return sb.toString();
    }

    public List<Exp> getSubExpressions() {
        List<Exp> list = new List<Exp>();
        list.add(where);
        list.add(body);
        return list;
    }

    public void setSubExpression(int index, Exp e) {
        switch (index) {
        case 0:
            where = e;
            break;
        case 1:
            body = e;
            break;
        }
    }

    public boolean shallowCompare(Exp e2) {
        if (!(e2 instanceof QuantExp)) {
            return false;
        }
        if (operator != ((QuantExp) e2).getOperator()) {
            return false;
        }
        return true;
    }

    public Exp remember() {
        if (where instanceof OldExp)
            this.setWhere(((OldExp) (where)).getExp());
        if (body instanceof OldExp)
            this.setBody(((OldExp) (body)).getExp());

        if (where != null)
            where = where.remember();
        if (body != null)
            body = body.remember();

        return this;
    }

    public void prettyPrint() {
        if (operator == FORALL)
            System.out.print("For all ");
        else if (operator == EXISTS)
            System.out.print("There exists ");
        else if (operator == UNIQUE)
            System.out.print("There exists unique ");
        Iterator<MathVarDec> it = vars.iterator();
        if (it.hasNext()) {
            it.next().prettyPrint();
        }
        while (it.hasNext()) {
            System.out.print(", ");
            it.next().prettyPrint();
        }
        if (where != null) {
            System.out.print(", ");
            where.prettyPrint();
        }
        System.out.print(", ");
        body.prettyPrint();
    }

    public Exp copy() {
        int newOperator = operator;
        Iterator<MathVarDec> it = vars.iterator();
        List<MathVarDec> newVars = new List<MathVarDec>();
        while (it.hasNext()) {
            newVars.add(it.next().copy());
        }
        Exp newWhere = null;
        if (where != null) {
            newWhere = Exp.copy(where);
        }
        Exp newBody = Exp.copy(body);
        Exp retval =
                new QuantExp(null, newOperator, newVars, newWhere, newBody);
        return retval;
    }

}
