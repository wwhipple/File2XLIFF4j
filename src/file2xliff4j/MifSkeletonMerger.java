/*
 * MifSkeletonMerger.java
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
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/**
 * This class merges the following:
 * <ol>
 * <li>The pseudoSkeleton stream generated while XLIFF was created
 *     from the original Maker Interchange File input.</li>
 * <li>The original MIF stream from which the XLIFF was created.
 * </ul>
 * It outputs a final Skeleton file that can be used (in combination
 * with the format and XLIFF files) to generate a new MIF file in a
 * target language.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class MifSkeletonMerger implements SkeletonMerger {
    
//    private StringBuilder skelBuf;              // Starts with copy of original document
//    private int mifStreamPos = 0;               // How far have we read in the original MIF?
    private boolean hasMacros = false;          // Does this file have any macros?
//    private static final int BLOCK_SIZE = 8192; // For reading stuff in.

    private BufferedReader mif;
    private BufferedWriter skel;
    
    private String partialMifLine;

    // Matcher for "tu" lines in the tskeleton file.
    String tagPatt = "^<(format|tu) id='([^\']+)' parent='([^\']+)'(?: unique='([^\']*)')? no='([^\']*)' of='([^\']*)'>";
    Matcher tuMatcher = Pattern.compile(tagPatt).matcher("");

    Matcher tagMatcher = Pattern.compile("^([^ ]+) seq='(\\d+)'(?: unique='(\\d*)')?>").matcher("");
    
    /**
     * Constructor for the skeleton merger object
     */
    public MifSkeletonMerger()  { 
    }
    
    /**
     * Create a skeleton file from the pseudo skeleton and the original MIF file.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param mifInStream The original MIF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the MIF. (Question: Does this really
     *        matter for the skeleton?)
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream mifInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException {
        this.merge(tSkelInStream, mifInStream, skeletonOutStream, encoding, 1);
    }
        
    /**
     * Create a skeleton file from the pseudo skeleton and the original MIF file.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param mifInStream The original MIF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the MIF. (Question: Does this really
     *        matter for the skeleton?)
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs.
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream mifInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException {

        if (tSkelInStream == null || mifInStream == null
                || skeletonOutStream == null || encoding == null) {
            throw (new IllegalArgumentException());
        }

        int retCode = 0;                        // OK so far
        
        BufferedReader tSkel = new BufferedReader(new InputStreamReader(tSkelInStream, encoding));

        mif =  new BufferedReader(new InputStreamReader(mifInStream, encoding));
        skel  = new BufferedWriter(new OutputStreamWriter(skeletonOutStream, encoding));
        
        // Does the MIF file contain macros?
//        Pattern macrop = Pattern.compile("\\bde(?:fi|ﬁ)ne[(]", Pattern.DOTALL);
//        Pattern macrop = Pattern.compile("\\bdefine[(]", Pattern.DOTALL);
//        Matcher macrom = macrop.matcher("");
            
        // These refer to lines read from the temporary Skeleton input stream:
        String curSkelLine = "";  // The temp skel line read in this iteration
        int numRemaining = 0;     // How many more TUs in this run (sequence)?
        
        // The outer loop is driven by the temporary skeleton file, which includes (one per line)
        // tags from the original file that don't map to bx/ex/x tags in the XLIFF.
        OUTER_FOR:
        for (;;) {
            // Read a line from the temporary skeleton file:
            curSkelLine = tSkel.readLine();
            if (curSkelLine == null) {        // When we reach end of stream,
                break;                        //   quit looping
            }
            if (curSkelLine.startsWith("<tu id=") ||
                    curSkelLine.startsWith("<format id=")) {  // Refers to a translation unit
                // Substitute <trans-unit lt:tu-id /> in new skeleton
                numRemaining = insertTu(curSkelLine);  // 0 might be a dummy (????)
                INNER_WHILE:
                while (numRemaining > 0) {
                    curSkelLine = tSkel.readLine();
                    if (curSkelLine == null) {        // When we reach end of stream,
                        break OUTER_FOR;                        //   quit looping
                    }
                    if (!(curSkelLine.startsWith("<tu id=") ||
                        curSkelLine.startsWith("<format id="))) {
                        System.err.println("Unexpected skeleton line encountered "
                                + "while reading multi-segment sequence.");
                        break INNER_WHILE;
                    }
                    numRemaining = insertTu(curSkelLine);
                }

                // We just inserted a TU. Delete the original text up to
                // the next tag (that we are just about to read)
                curSkelLine = tSkel.readLine();
                if (curSkelLine == null) {
                    break;                    // End of stream (prematurely!)
                }
                else {
                    // Read the input MIF until end of tag is reached, without
                    // copying to output skeleton.
                    this.nextMifChar();       // Read (and discard) '<'
                    retCode = skipToEndOfTag();
                    if (retCode == -1) {
                        break;
                    }
                }
            }
            // If this is a "begin" tag, seek to it
            else if (!curSkelLine.substring(0,2).equals("</")) {
                // We didn't just insert a TU, so
                // we can just keep walking the XLIFF.
                // Copy MIF input to the skeleton until the tag in curSkelLine
                // is reached.
                retCode = copyToTag(curSkelLine);     
                if (retCode == -1) {
                    break;
                }
            }
            else {  // This must be an end tag (with no preceding tu id= tag
                // The previous tag was a pseudo begin tag that matches this 
                // pseudo end tag. This can happen is the tskeleton has
                // a <String on one line, followed immediately by an </String
                // on the next, with no intervening tu line. (Perhaps because
                // we decided that the string wasn't translatable or something)
                // The "String" above could also be "Para".
                retCode = copyToEndOfTag();
                if (retCode == -1) {
                    break;
                }
            }
        }
        
        // Even if we've reached the end of the tskeleton, we need to copy the rest
        // of mif to skel
        for ( ; ; ) {
            int tChar = -1;
            tChar = mif.read();
            if (tChar == -1) {
                // We're done (EOF)
                break;
            }
            skeletonOutStream.write(tChar);
        }
        
        // Let's close the input stream
        mif.close();
        
        // Flush as you leave
        skel.flush();
        skel.close();
    }

    /**
     * Set a format-specific property that might affect the way that the
     * merger process is conducted.
     * @param property The name of the property
     * @param value The value of the property
     * @throws file2xliff4j.ConversionException
     *         If the property or value can't be recognized.
     */
    public void setProperty(String property, Object value)
            throws ConversionException {
        return;
    }
    
    /**
     * From wherever we are in the MIF stream (read--and copy to skel--all the 
     * we read until the end of the current tag is reached (by counting unescaped 
     * &lt; and &gt;)
     * @return 0 if successful, else -1 if error
     */
    private int copyToEndOfTag() {
        int angleCount = 0;         // We're done when count is -1
        int i = -1;                 // The character index ... bogus so far.
        boolean inFMQuote = false;  // Inside FrameMaker quote? ` ''
        
        int curChar = -1;           // Use int so we have room for negative sign 
        char prevChar = (char)curChar;  // The character before the current one

        boolean isFirstChar = true;
        
        try {
            FORLOOP:
            while (((curChar = nextMifChar()) != -1) && (angleCount >= 0)) {
                if (isFirstChar && (curChar == '<')) {
                    angleCount = -1;  
                }
                isFirstChar = false;
                switch ((char)curChar) {
                    case '`':
                        if (prevChar != '\\') {
                            inFMQuote = true;
                        }
                        break;
                    case '\'':
                        if (prevChar != '\\') {
                            inFMQuote = false;
                        }
                        break;
                    case '<':    // Another "child" tag?
                        if (!inFMQuote) {   // < doesn't need escaping in a quote
                            angleCount++;   // Increment only if not escaped
                        }
                        break;
                    case '>':    // Either this tag or a child ended?
                        if (prevChar != '\\') {
                            angleCount--;
                        }
                        if (angleCount < 0) {
                            skel.write(curChar);
                            skel.flush();
                            break FORLOOP;  // We're done
                        }
                        break;   // End this case ...
                    default:
                        // Do nothing 
                }
                skel.write(curChar);
                skel.flush();
                prevChar = (char)curChar;
            }
        }
        catch(IOException e) {
            System.err.println("Unexpected IOException in MifSkeletonMerger.copyToEndOfTag.");
            return -1;
        }
        
        if (curChar == -1) {
            // EOF (Bad)
            return -1;
        }
        
        return 0;    // A OK
    }

    /**
     * From wherever we are in the MIF stream (read--without copying to skel--characters
     * until the end of the current tag is reached (by counting unescaped 
     * &lt; and &gt;)
     * @return 0 if successful, else -1 if error
     */
    private int skipToEndOfTag() {
        int angleCount = 0;         // We're done when count is -1
        int i = -1;                 // The character index ... bogus so far.
        boolean inFMQuote = false;  // Inside FrameMaker quote? ` ''
        
        int curChar = -1;           // Use int so we have room for negative sign 
        char prevChar = (char)curChar;  // The character before the current one
        
//        boolean isFirstChar = true; // In case first character is (still) '<'
        
        FORLOOP:
        while (((curChar = nextMifChar()) != -1) && (angleCount >= 0)) {
            switch ((char)curChar) {
                case '`':
                    if (prevChar != '\\') {
                        inFMQuote = true;
                    }
                    break;
                case '\'':
                    if (prevChar != '\\') {
                        inFMQuote = false;
                    }
                    break;
                case '<':    // Another "child" tag?
//                    if (!inFMQuote && !isFirstChar) {   // < doesn't need escaping in a quote
                    if (!inFMQuote) {   // < doesn't need escaping in a quote
                        angleCount++;   // Increment only if not escaped
                    }
                    break;
                case '>':    // Either this tag or a child ended?
                    if (prevChar != '\\') {
                        angleCount--;
                    }
                    if (angleCount < 0) {
                        break FORLOOP;  // We're done
                    }
                    break;   // End this case ...
                default:
                    // Do nothing 
            }
            prevChar = (char)curChar;
//            isFirstChar = false;
        }
        
        if (curChar == -1) {
            // EOF (Bad)
            return -1;
        }
        
        return 0;    // A OK
    }

    /**
     * Passed a tu tag string (from the intermediate skel file), insert a
     * tu place holder of the format <lt:tu id='[id]'/> in the current
     * skel position. 
     * @param tuTagString The line from the intermediate skeleton file that
     *        represents the TU holder we are to insert
     * @return The number of segments remaining in this "run" (i.e. sequence)
     *         of adjacent segments.
     */
    private int insertTu(String tuTagString) {
        String placeHolder = "";
        
        int numLeft = 0;
        String tuOrFormat = "";
        String curTuID = "";
        String parent = "";
        String uniqueID = "";
        String curNum = "";
        String totNum = "";
        // Get the TU ID (number, UUID, etc.) from the intermediate skeleton file
        tuMatcher.reset(tuTagString);
        if (tuMatcher.find()) {
            tuOrFormat = tuMatcher.group(1);
            curTuID = tuMatcher.group(2);
            parent = tuMatcher.group(3);
            uniqueID = tuMatcher.group(4);
            curNum = tuMatcher.group(5);
            totNum = tuMatcher.group(6);
            numLeft = Integer.parseInt(totNum) - Integer.parseInt(curNum); 
        }
        else {
            System.err.println("MifSkeletonMerger cannot parse tu tag from tskeleton.");
            return 0;   // I guess we'll have to skip this TU; we can't figure where
                            // to insert it (sigh)
        }
        
        // Construct the placeholder we will insert
        if (parent.equals("String")) {
            if (curNum.equals("1")) {
                placeHolder += "<String `";
            }

            placeHolder += "<lt:" + tuOrFormat + " id=\"" + curTuID + "\" parent=\""
                    + parent + "\" no=\"" + curNum + "\" of=\"" + totNum + "\"/>";

            if (numLeft == 0) {
                placeHolder += "'>";
            }
        }
        
        else {  // Parent is "Para"
            if (curNum.equals("1")) {
                placeHolder += "<Para\r\n  ";
            }

            placeHolder += "<lt:" + tuOrFormat + " id=\"" + curTuID + "\" parent=\"" 
                    + parent + "\" unique=\"" + uniqueID + "\" no=\"" + curNum 
                    + "\" of=\"" + totNum + "\"/>";

            if (numLeft == 0) {
                placeHolder += "\r\n >";
            }
            
        }
        
        try {
            skel.write(placeHolder);  // Insert it now!
            skel.flush();
        }
        catch(IOException e) {
            System.err.println("Unexpected IOException in MifSkeletonMerger.insertTu.");
            return 0;
        }
        
        return numLeft;
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), read the input mif
     * until the next occurrence of that tag is found, copying what is read to
     * the skel output. If the tagString occurs int he middle of a line, leave
     * the remainder of the last line read (beginning with the tagString) in the
     * partialMifLine variable for later reading by others (or myself).
     * Upon return, the output skel should end at the last character before the
     * '<' that introduces the tagString.
     * <p>The tagString passed in should begin either &lt;Para or &lt;String
     * @param tagString The tag string read from the intermediate skeleton stream
     * @return 0 on success, else -1.
     */
    private int copyToTag(String tagString) {
        // Find more about this tag from the intermediate skeleton file
        String tagPrefix = null;
        String uniqueID = "";
        tagMatcher.reset(tagString);
        if (tagMatcher.find()) {
            tagPrefix = tagMatcher.group(1);  // <Para or <String
            uniqueID = tagMatcher.group(3);
        }

        try {
            // First check partialMifLine to see if it has any residue from the
            // last read operation
            if ((partialMifLine != null) && (partialMifLine.length() > 0)) {
                int tagPos = partialMifLine.indexOf(tagPrefix);
                if (tagPos != -1) {
                    // We found our sought-after tag in the saved partial MIF line!
                    // (Highly unusual)
                    skel.write(partialMifLine,0,tagPos);
                    skel.flush();   // for DEBUGGING
                    partialMifLine = partialMifLine.substring(tagPos);
                    // Now the partialMifLine's 1st character should be the '<'
                    // that introduces String or Para
                    return 0;       // Mission accomplished ... for now
                }
                else {
                    // Write the entire partialMifLine to the skel
                    skel.write(partialMifLine);
                    skel.flush();
                    partialMifLine = "";
                }
            }

            // We're still here. We need to read through the mif file until we find
            // the sought after tagPrefix
            String curMifLine = "";
            int tagPos = -1;
            for ( ; ; ) {
                curMifLine = mif.readLine();
                if (curMifLine == null) { // end of file?
                    return -1;            // Error--premature eof
                }
                curMifLine += "\r\n";

                tagPos = curMifLine.indexOf(tagPrefix);
                if (tagPos != -1) {
                    // We found our sought-after tag in the saved partial MIF line!
                    // (Highly unusual)
                    skel.write(curMifLine,0,tagPos);
                    skel.flush();         // for DEBUGGING
                    partialMifLine = curMifLine.substring(tagPos);
                    // Now the partialMifLine's 1st character should be the '<'
                    // that introduces String or Para
                    return 0;       // Mission accomplished ... for now
                }
                else {
                    // Write the entire partialMifLine to the skel
                    skel.write(curMifLine);
                    skel.flush();
                    curMifLine = "";
                }
            }
        }
        catch(IOException e) {
            System.err.println("Unexpected IOException in MifSkeletonMerger.copyToTag.");
            return -1;
        }
    }
    
    /**
     * Return the next unprocessed character from the MIF input stream--either
     * from "uncomsumed" characters in
     * @return The next character or -1 if EOF
     */
    private int nextMifChar() {
        int nextChar = -1;

        try {
            // If our partial MIF line buffer is empty, fill it.
            if ((partialMifLine != null) && (partialMifLine.length() == 0)) {
                partialMifLine = mif.readLine();
                if (partialMifLine == null) {
                    return -1;
                }
                else {
                    partialMifLine += "\r\n";  // Readline strips newlines; add one back
                }
            }
        }
        catch(IOException e) {
            System.err.println("Unexpected IOException in MifSkeletonMerger.nextMifChar.");
            return -1;
        }
        // Now we should have *something* in partialMifLine. Return the first char.
        nextChar = partialMifLine.charAt(0);
        partialMifLine = partialMifLine.substring(1);
        
        return nextChar;
    }
    
    /**
     * Passed a (sub-)string, look for a closing right parenthesis that isn't
     * part of a quote comment.
     * @param str The string to search
     * @return the index of the paren, or -1 if none found.
     */
    private int getEndParen(String str) {
        boolean inquote = false, incomment = false;
        
        for (int i = 0; i < str.length(); i++) {
            switch (str.charAt(i)) {
                case ')':
                    if (!inquote & !incomment) {
                        return i;
                    }
                    break;
                case '`':
                    if (!incomment) {
                        inquote = true;
                    }
                    break;
                case '\'':
                    if (!incomment) {
                        inquote = false;
                    }
                    break;
                case '#':
                    if (!inquote) {
                        incomment = true;
                    }
                    break;
                case '\r':
                case '\n':
                    incomment = false;
                    break;
                default:
                    // do nothing
            }
            
        }
        
        return -1;
    }
}
