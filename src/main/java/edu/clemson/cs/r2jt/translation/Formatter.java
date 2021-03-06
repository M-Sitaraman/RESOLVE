/*
 * Formatter.java
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
package edu.clemson.cs.r2jt.translation;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>A small class that automatically supplements a user supplied string
 * containing translated Java or C code with:</p>
 * <ul>
 * <li>Newlines after semicolons and left/right curly braces.</li>
 * <li>Appropriate tabification for each level of curly brace.</li>
 * </ul>
 */
public class Formatter {

    /**
     * <p>Annotates a tab-less, newline-less string containing Java or C code
     * with proper newlines and indentation.</p>
     *
     * @param code A raw string of Java or C code <em>lacking</em> newlines
     *             or tabs.
     *
     * @return The string, <code>code</code>, with newlines and indentation
     *         added.
     */
    public static String formatCode(String code) {

        StringBuilder formatted = new StringBuilder();
        code = normalizeWhitespace(code);

        String[] currentSplit = code.split("(?<=;)|(?<=\\{)|(?<=\\})");
        Deque<String> tabs = new LinkedList<String>();

        for (String s : currentSplit) {

            if (s.endsWith("}")) {
                tabs.pop();
            }

            formatted.append(writeTabs(tabs).toString());
            formatted.append(s).append("\n");

            if (s.endsWith("{")) {
                tabs.push("\t");
            }
        }
        // If others pop up requiring formatting after tabification,
        return formatted.toString().replaceAll("\n;", ";");
    }

    /**
     * <p>Creates a string of consecutive tabs of length |T|.</p>
     *
     * @param T A Deque of tab <code>'\t'</code> characters.
     * @return A string of consecutive tabs.
     */
    private static String writeTabs(Deque<String> T) {

        StringBuilder result = new StringBuilder();

        for (String t : T) {
            result.append(t);
        }
        return result.toString();
    }

    // somehow figure out a way to get at whitespace preceding semicolons

    /**
     * <p>Makes use of regular expressions to:
     * <ul>
     *      <li>Remove all trailing whitespace on ';' and '{'.</li>
     *      <li>Remove all preceding whitespace on '}'.</li>
     * </p>
     *
     * @param code The string whitespace should be removed from.
     * @return The string lacking excess whitespace.
     */
    private static String normalizeWhitespace(String code) {
        code = code.replaceAll("\\s \\s+", " ");
        code = code.replaceAll("\\s+ \\{", " {"); // preceding ws on {
        code = code.replaceAll("\\{\\s+", "{"); // proceeding ws on {
        code = code.replaceAll("\\;\\s+", ";"); // new
        code = code.replaceAll("\\}\\s+", "}"); // proceeding ws on }
        code = code.replaceAll("\\s+\\}", "}"); // preceding ws on }
        return code.toString();
    }
}