/*
 * Format.java
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

package file2xliff4j;

import java.io.*;
import java.util.regex.*; 

/**
 * Class to encapsulate the format file generated along with the XLIFF file that
 * represents a document stored in LingoDoc. (The format file currently maps
 * bx/ex/x tags back to their original HTML, ODF, etc. tags.)
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class Format {
    private String formatStr;
    private boolean usesCdata; // Determined from tags string's formatting attribute

    protected static final int BLKSIZE = 8192;

    /** 
     * Create a new instance of Format, taking a file name as input
     * @param fileName Name of file that contains the format information
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     */
    public Format(String fileName) throws FileNotFoundException,
        IOException {

        // Convert the file name into a stream and call the other ctor.
        this(new FileInputStream(fileName));
        
    }

    /** 
     * Create a new instance of Format
     * @param inStream The input stream with the format information
     * @throws java.io.IOException
     */
    public Format(InputStream inStream) throws IOException {

        // Read the specified file into the formatStr variable.
        InputStreamReader inRdr =  new InputStreamReader(inStream);
//        int ch;
        StringBuilder tBuf = new StringBuilder();
//        while ((ch = inRdr.read()) != -1) {
//            tBuf.append((char)ch);
//        }
//
        char[] buf = new char[BLKSIZE];
        int numRead;    // To count number of characters read
        while ((numRead = inRdr.read(buf)) != -1) {  // Read a block from the XLIFF
            tBuf.append(buf,0,numRead);     // ... and write it to the ZIP 
        }
        
        // Now convert the StringBuilder into a more versatile String
        formatStr = tBuf.toString();
        
        Matcher m = Pattern.compile("<tags formatting=(['\"])(.*?)\\1", Pattern.DOTALL).matcher(formatStr);
        if (m.find()) {
            if (!m.group(2).equals("&lt;")) {
                usesCdata = true;
            }
        }
    }

    /**
     * Passed the identifier of a bx, ex (or perhaps some other tag),
     * return the text that the tag identifier maps to in the format file.
     * @param tagID The identifier of the XLIFF tag who's substitution
     * value is being requested
     * @param prependLT true: prepend a '<' value before the value (if the
     *        value's length is greater than 0; false: Return the actual
     *        value from the format file with no modifications.
     * @return The actual value found in the original document
     */
    public String getReplacement(String tagID, boolean prependLT) {
        
        String returnInfo = "";    // Nothing to return yet
        
        // Create a regex pattern to match against in the format file (string)
        // Note (WWhipple 4/27/2007): AFAICT the recursive attribute is created
        // by MifTuPreener.java, but never used by anyone. For the XMLImporter,
        // we need to handle cases where the data inside the format entry contains
        // literal CDATA delimiters (!!). We will add an optional attribute
        // cdataIsLiteral='true' to handle that case.
        String patt = "<tag id='" + tagID 
                + "'(?: recursive='([^']*)')?(?: cdataTagIsLiteral='([^']*)')?>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</tag>";
    
        Pattern r = Pattern.compile(patt,Pattern.DOTALL);  // Compile the pattern
//        Pattern r = Pattern.compile(patt);  // Compile the pattern
        
        // Then look for the pattern in the format file string
        Matcher m = r.matcher(formatStr);
        if (m.find()) {
            returnInfo = m.group(3);   // Match for  ".*?" (in parens) in patt above
            if (m.group(2) != null && m.group(2).equals("true")) {
                returnInfo = "<![CDATA[" + returnInfo + "]]>";
            }
        }
        
        if ((returnInfo.length() > 0) && prependLT) {
            return ("<" + returnInfo);
        }   
        else {
            return returnInfo;
        }
    }

    /**
     * Passed the identifier of a bx, ex (or perhaps some other tag),
     * return the text that the tag identifier maps to in the format file.
     * @param tagID The identifier of the XLIFF tag who's substitution
     * value is being requested
     * @return The value to which the bx/ex/x tag maps.
     */
    public String getReplacement(String tagID) {
        if (usesCdata) {
            return this.getReplacement(tagID, false);  // Needn't be well-formed
        }
        else {
            return this.getReplacement(tagID, true);   // Must be well-formed
        }
    }
    
}
