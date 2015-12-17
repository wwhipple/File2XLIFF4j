/*
 * XMLTuXPath.java
 *
 * Copyright (C) 2006. Lingotek, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  
 * 02110-1301, USA
 */

package f2xutils;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.lang.*;
import java.nio.charset.*;

/**
 * The XMLTuXPath represents the Xpath to an element (or attribute)
 * that "contains" a translation unit, together with its line numbeer and
 * column number.
 * 
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XMLTuXPath {
    
    
    private String xPath;          // Path to this element

    // Line number returned by the SAX locator
    private int lineNum;
    
    // Column number returned by the SAX locator
    private int columnNum;
    
    // Attribute name is necessary (together with the line and column numbers)
    // to guarantee uniqueness (for equality) ... since a given element and
    // all its attributes will have the same line and column coordinates.
    private String attribute;
    
    // What is this object's hash code?
    private int hashCode;
    
    /**
     * Constructor of XMLTuXPath objects
     * 
     * @param xPath The element's XPath. In the case of an XPath that ends in
     *        an attribute, the XPath should end "/@attrName" (where "attrName"
     *        is the actual name of the attribute. (The attribute name should 
     *        also appear--without the leading "@"--as the value of the
     *        attribute parameter [see below]).
     * @param lineNum The number of the line where it occurs
     * @param columnNum The number of the column where it occurs
     * @param attribute The name of the attribute. (Might be null if the leaf
     *        node is an element.)
     */
    public XMLTuXPath(String xPath, int lineNum, int columnNum,
            String attribute) {
        this.xPath = xPath;
        this.lineNum = lineNum;
        this.columnNum = columnNum;
        this.attribute = attribute;  // Might be null

        // Compute the hash code:
        this.hashCode = (lineNum << 16) | columnNum;
        if (attribute != null) {
            this.hashCode += 1;
        }
    }
    
    /** 
     * Return the element's line number.
     * @return The number of the line where the element occurs.
     */
    public int getLineNumber() {
        return this.lineNum;
    }

    /** 
     * Return the element's column number.
     * @return The number of the column where the element occurs.
     */
    public int getColumnNumber() {
        return this.columnNum;
    }

    /** 
     * Return the element's XPath
     * @return The element's XPath
     */
    public String getXPath() {
        return this.xPath;
    }

    /**
     * hashCode method to override Object's default hash code method.
     * @return This object's hash code
     */
    public int hashCode() {
//        int code = (lineNum << 16) | columnNum;
//        if (attribute != null) {
//            code += 1;
//        }
        return this.hashCode;
    }
    
    /**
     * Return a String representation of an XMLTuXPath object
     * @return A string representing the object
     */
    public String toString() {
        return
            String.format("%05d:%04d  ", lineNum, columnNum) +
            ((xPath == null) ? "" : xPath);
//            ((attribute == null) ? "" : ("[" + attribute + "]")) +    
    }
    
    /**
     * Checks for equality of some other XMLTuXPath and this one. (Used
     * to resolve hashCode collisions.)
     * 
     * @param inpath The Object to compare to this XMLTuXPath.
     * @return true if equal, else false.
     */
    public boolean equals(Object o) {
        return o instanceof XMLTuXPath &&
            lineNum != 0 &&
            columnNum != 0 &&
            lineNum == ((XMLTuXPath)o).lineNum &&
            columnNum == ((XMLTuXPath)o).columnNum &&
            ((attribute == null && ((XMLTuXPath)o).attribute == null) ||
                attribute.equals(((XMLTuXPath)o).attribute));
    }
}
