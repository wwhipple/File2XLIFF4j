/*
 * XMLSkeletonMerger.java
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
 * <li>The pseudoSkeleton stream generated as XLIFF is created
 *     from the original XML input.</li>
 * <li>The original XML stream from which the XLIFF was created.
 * </ul>
 * It outputs a final Skeleton file that can be used to generate
 * the original XML (with modified/added targets) XLIFF.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XMLSkeletonMerger implements SkeletonMerger {
    
    private StringBuilder skelBuf;            // Starts with copy of XML document

    private int prevTagSeekOffset;            // The offset of the previous tag to which we sought.
    private boolean inAttr = false;           // We're processing an attribute
    private int attrValOffset;                // Where attr value is that we will insert placeholder into.
            
    private boolean prevTagWasEmpty = false;  // The tag before this one was an empty tag.
    private int xmlStreamPos = 0;             // How far have we read in the original XML?
    private String curTuID = "";              // Keep track of the current TU ID for use
                                              // of the wildcard tag we insert before the
                                              // </trans-unit> tag
    private static final int BLKSIZE = 8192;  // How much to read at a time.

    private int tSkelSeqNo = -1;              // Line number in tskeleton file.
            
    /**
     * Constructor for the skeleton merger object
     */
    public XMLSkeletonMerger()  { 
    }
    
    /**
     * Create a skeleton file from the temporary skeleton and the original XML.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param xmlInStream The original XML to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the XML
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs. (Ignored for this merger.)
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream xmlInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException {
        this.merge(tSkelInStream, xmlInStream, skeletonOutStream, encoding);
    }

    /**
     * Create a skeleton file from the temporary skeleton and the original XML.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param xmlInStream The original XML to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the XML
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream xmlInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException {

        if (tSkelInStream == null || xmlInStream == null
                || skeletonOutStream == null || encoding == null) {
            throw (new IllegalArgumentException());
        }

        BufferedReader tSkel = new BufferedReader(new InputStreamReader(tSkelInStream, Charset.forName("UTF-8")));
        BufferedReader xml =  new BufferedReader(new InputStreamReader(xmlInStream, encoding));
        BufferedWriter skel  = new BufferedWriter(new OutputStreamWriter(skeletonOutStream, encoding));
        
        // Read the XML stream into a buffer for easy (quick and dirty for now)
        // manipulation
        skelBuf = new StringBuilder();
        char[] buf = new char [BLKSIZE];          // For reading
        int numRead;
        while ((numRead = xml.read(buf)) > 0) {
            skelBuf.append(buf,0,numRead);
        }
        
        // Matcher to capture the line sequence number from the tskeleton.
        Matcher seqMatcher = Pattern.compile(" seq='(\\d+)'(?:>|&gt;)$").matcher("");
        
        // Generic regular expression to capture information about each line in
        // the temporary skeleton
        Matcher lineMatcher 
            = Pattern.compile("^(?:<|&lt;|[]])(/?)(\\S+)"     // Optional / followed by tag name
                + " ([^=]+)=['\"]([^'\"]+)['\"]"          // First attribute and value
                + "(?: ([^=]+)=['\"]([^'\"]+)['\"]"       //       Optional 2nd attribute & value
                + "(?: ([^=]+)=['\"]([^'\"]+)['\"]"       //       Optional 3rd attribute & value
                + " ([^=]+)=['\"]([^'\"]+)['\"])?)?").matcher(""); //Optional 4th attribute & value
        
        // These refer to lines read from the temporary Skeleton input stream:
        String curSkelLine = "";              // The temp skel line read in this iteration
        String prevSkelLine = "";             // The line read last time
        int curTagSeq = 0;                    // Current tag sequence number
        int curTuId = 0;                      // Which Tu Number
        
        boolean done = false;     // Not done yet
        boolean tuOK = false;     // TU insertion not OK (yet)

        // Hmmm ... Maybe some future iteration should look for and "mark"
        // areas of the buffer that are "out-of-bounds" because they are comments?
        // <!-- ..... --> of CDATA <![CDATA[  ... ]]>
        // Then have sub deletion check to make sure we're not "out-of-bounds"
        
        boolean isEndTag = false;    // Signalled by '/' after '<'
        String curtag = "";
        String attrName1 = "";       // 1st attribute name
        String attrVal1  = "";       // 1st attribute value
        String attrName2 = "";       // 2nd attribute name
        String attrVal2  = "";       // 2nd attribute value
        int segNum       =  0;       // Which seg in this run? (3rd attr val)
        int numSegs      =  0;       // Total segments (4th attr val);
        
        // The outer loop is driven by the temporary skeleton file, which includes (one per line)
        // tags from the original XML file, in one of the following formats:
        // <tagname inText='inside|outside|entering'>  // tagName is actual tag name
        // </tagname inText='inside|outside|leaving'>  // tagName is actual tag name
        // <attr name='attrName' tag='tagName'>        // attrName is actual attribute name
        // </attr name='attrName' tag='tagName'>       //   ... tagname is parent tag's name
        // <tu id='UUID' length='n' no='n' of='m'>
        for (;;) {
            prevSkelLine = curSkelLine;       // Remember what we read last time
            // Read a line from the temporary skeleton file:
            curSkelLine = tSkel.readLine();
            if (curSkelLine == null) {        // When we reach end of stream,
                break;                        //   quit looping
            }
            
            seqMatcher.reset(curSkelLine);
            if (seqMatcher.find()) { this.tSkelSeqNo = Integer.parseInt(seqMatcher.group(1)); }
            
            lineMatcher.reset(curSkelLine);
            if (lineMatcher.find()) {
                isEndTag  = lineMatcher.group(1).equals("/");
                curtag    = lineMatcher.group(2);
                attrName1 = lineMatcher.group(3);
                attrVal1  = lineMatcher.group(4);
                attrName2 = lineMatcher.group(5);
                attrVal2  = lineMatcher.group(6);
                
                if (curtag.equals("lTLt:tu")) {
                    segNum = Integer.parseInt(lineMatcher.group(8));  // Which seg in this run?
                    numSegs = Integer.parseInt(lineMatcher.group(10)); // Total segs in run.
                }
                else {
                    segNum = 0;
                    numSegs = 0;
                }
            }
            else {
                // No more meaningful lines
                System.err.println("Unrecognizable line in temporary skeleton at seq=" + tSkelSeqNo);
                break;
            }
            if (curtag.equals("lTLt:tu")) {  // Refers to a translation unit
                
                // This is the first of several sub-paragraph segments
                if (numSegs == 1) {
                    // Insert the TU (pass its TU ID and number of segments).
                    insertTu(attrVal1, numSegs);
                }
                else {
                    String idString = ("tu:" + attrVal1);
                    for (int i = 2; i <= numSegs; i++) {
                        curSkelLine = tSkel.readLine();
                        lineMatcher.reset(curSkelLine);
                        if (!curSkelLine.startsWith("<lTLt:tu")) {
                            System.err.println("Unexpected TU line in temporary skeleton at seq=" + tSkelSeqNo);
                            break;   // Break out of this loop. We're hosed!
                        }
                        if (lineMatcher.find()) {
                            attrVal1  = lineMatcher.group(4);
                            idString += (" tu:" + attrVal1);
                        }
                        else {
                            System.err.println("Failed to find TU Id at seq=" + tSkelSeqNo);
                            break;
                        }
                    }
                    insertTu(idString, numSegs);
                }
            }
            // Beginning of an attribute
            else if (curSkelLine.startsWith("<lTLt:attr ")) {
                inAttr = true;
                // Note: The attr's noted in the temporary skeleton will never
                // be from within imbedded elements that are within TUs, but only
                // within opening tags that are "outside" text or "entering"
                // that text. Those tags will not have been deleted previously,
                // but will have been "sought" past. As we add placeholders,
                // we will seek backward to the previous tag.
                seekBackToAttr(attrVal1, attrVal2);
                // Upon return, we will be poised 
            }
            // End of an attribute
            else if (curSkelLine.startsWith("</lTLt:attr ")) {
                inAttr = false;
            }
            // Start of a CDATA section. Seek up to the section
            else if (curSkelLine.startsWith("<![CDATA[")) {
                seekToTag("<![CDATA[","begin","after");
            }
            // End of a CDATA section. Seek past the section
            else if (curSkelLine.startsWith("]]>")) {
                seekToTag("]]>","end","after");
            }
            // End of an XML tag in the document
            else if (curSkelLine.charAt(1) == '/') {
                if (prevSkelLine.startsWith("<lTLt:tu") ||attrVal1.equals("leaving")) {
                    deleteToTag(curtag,"end","before");
                }
                else if (this.prevTagWasEmpty) {
                    // If the previous tag was an empty tag (something like 
                    // <notes/>), then--even though SAX reported this end tag--
                    // it doesn't exist in the original file. In that case, 
                    // ignore the tag.
                    this.prevTagWasEmpty = false;  // Toggle this flag first
                    // Then continue with the next line.
                }
                else if (attrVal1.equals("inside")) {
                    deleteToTag(curtag,"end","after");
                }
                else if (attrVal1.equals("outside")) {
                    seekToTag(curtag,"end","after");
                }
                else {
                    System.err.println("XMLSkeletonMerger.merge: Unexpected end "
                        + "tag condition for tag " + curtag + " at seq=" + tSkelSeqNo);
                }
            }
            else { // The beginning of some other element
                if (attrVal1.equals("entering")) {
                    // We want to maintain all the ancestors of this tag in the
                    // original skeleton, so seek to this tag.
                    seekToTag(curtag, "begin", "after");
                }
                else if (attrVal1.equals("outside")) {
                    // Just seek
                    seekToTag(curtag, "begin", "after");
                }
                else if (attrVal1.equals("inside")) {
                    // This is part of what needs to be replaced by
                    // the TU placeholder. Delete everything up (and including)
                    // this tag.
                    deleteToTag(curtag, "begin", "after");
                }
                
            }
        }
        
        // Write the skeleton buffer to the skel stream
        for (int i = 0; i < skelBuf.length(); i++) {
        //for (int i = 0; i < xmlStreamPos; i++) {
            skel.write(skelBuf.charAt(i));
        }
        
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
     * Passed a tag name (from the intermediate skel file)--as well as an
     * indication of whether it is a start or end tag--delete everything between
     * xmlStreamPos (the current position in the XML stream) and the *beginning*
     * of the specified tag string.
     * @param tag The name of the tag to seek to.
     * @param beginOrEnd The above tag is either a "begin" or "end" tag.
     * @param deleteThru Delete to "before" or "after" the specified tag.
     */
    private void deleteToTag(String tag, String beginOrEnd, String deleteThru) {
        String tagPrefix = "";
        int tagStartsAt = -1;
        int deleteTo = -1;
        
        if (beginOrEnd.equals("end")) {
            tagPrefix = "</" + tag; 
        }
        else {
            tagPrefix = "<" + tag; 
        }

        // Where does the tag we're looking for start?
        tagStartsAt = skelBuf.indexOf(tagPrefix,xmlStreamPos);
        if (tagStartsAt == -1) {       // Ending tag doesn't exist
            System.err.println("XMLSkeletonMerger.deleteToTag: Can't find start tag at seq=" + tSkelSeqNo);
            prevTagWasEmpty = false;  // Not empty (nonexistent, in fact!)
            return;
        }
        // Now look for the end of tag. (First, make sure that the tag we
        // matched isn't a superstring of the one that we are really looking
        // for. (E.g., If we are looking for </abcd>, make sure we didn't
        // find </abcdef>.)
        boolean found = false;
        while (!found) {
            if (skelBuf.substring(tagStartsAt + tagPrefix.length(), 
                    tagStartsAt + tagPrefix.length() + 1).matches("[\\s/>]")) {
                // Yes, the substring is followed by whitespace or > (or / if empty element)
                found = true;
            }
            else { // We matched a longer tag whose prefix matches the one
                // we're looking for. Keep looking for an exact match.
                tagStartsAt = skelBuf.indexOf(tagPrefix,tagStartsAt + 1);
                if (tagStartsAt == -1) {
                    System.err.println("XMLSkeletonMerger.deleteToTag: Cannot find start tag at seq=" + tSkelSeqNo);
                    prevTagWasEmpty = false;  // Not empty (nonexistent, in fact!)
                    return;
                }
            }
        }
        if (deleteThru.equals("before")) {
            deleteTo = tagStartsAt;    // We'll delete up to the beginning of the tag.
        }
        else { // We need to delete through the end of the tag ("after")
            // Find the tag end:
            deleteTo = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (deleteTo > -1) {
                // If this tag is an empty tag, note it for later (when we delete to/seek
                // to the next end tag.
                if (beginOrEnd.equals("begin") && skelBuf.charAt(deleteTo -1) == '/') {
                    prevTagWasEmpty = true;
                }
                else {
                    prevTagWasEmpty = false;
                }
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                deleteTo++;
            }
            else {
                System.err.println("XMLSkeletonMerger found beginning of tag to"
                    + " delete to, but the end is missing at seq=" + tSkelSeqNo);
                return; // Can't delete (Serious!!)
            }
        }

        // Now delete everything we were asked to delete
        skelBuf.delete(xmlStreamPos, deleteTo);
        
        // Decrement the XML stream position by the number of characters we deleted
//        xmlStreamPos-= (deleteTo-xmlStreamPos);
        
        return;
    }

    /**
     * Passed information from the intermediate skeleton file, insert a TU 
     * placeholder at the current skeleton buffer position.
     * <p><i>Note:</i> Because we might be inserting a placeholder inside an
     * attribute value, we will represent less-than and greater-than characters
     * as entities, so that validation routines won't complain about greater- and
     * less-than characters within attribute value strings.
     * @param idString A string representing 1 or more TU segments. (The number
     *        of segments is in the second parameter.) If the place holder is
     *        to represent a single segment, this parameter's valud is the string
     *        value of a single UUID. If multiple segments are represented, the
     *        value is of the format: "tu:<UUID1> tu:<UUID2> ... tu:<UUIDn>"
     * @param numSegs A positive integer indicating the number of segments.
     */
    private void insertTu(String idString, int numSegs) {
        String idAttr = (numSegs == 1) ? "id" : "ids";
        String placeHolder = "&lt;lTLt:tu " + idAttr + "='" + idString + "'/&gt;";
        
        if (this.inAttr) {
            // If we're inserting a placeholder between the quotation marks of
            // a previously emptied attribute value, insert the placeholder at
            // the current attribute value position.
            skelBuf.insert(this.attrValOffset, placeHolder);
        }
        else {
            skelBuf.insert(xmlStreamPos, placeHolder);
        }
        
        xmlStreamPos+=placeHolder.length();  // Increment cur position in stream
    }
    
    /**
     * Passed an attribute name and the name of a parent element, find where
     * that attribute's value starts in the skeleton buffer, then delete the
     * contents of the attribute value's text. Adjust xmlStreamPos by decreasing
     * it by the numbers of characters deleted from the attribute value. Set
     * attrValOffset to the exact place (between two quotation marks) where a
     * TU placeholder will be inserted.
     * @param attrName The name of the attribute to seek backward to.
     * @param parentElement The name of the parent element.
     * @return true if no problems, else false.
     */
    private boolean seekBackToAttr(String attrName, String parentElement) {
        // Look for the attr name between the start of the previous tag and
        // the XmlStreamPos
        int startsAt = skelBuf.indexOf(attrName + "=", this.prevTagSeekOffset);
        
        if (startsAt == -1 || startsAt > xmlStreamPos) {
            // Out of range!! Bail out
            System.err.println("XMLSkeletonMerger.seekBackToAttr: Index (" 
                    + Integer.toString(startsAt) + ") of attribute "
                    + attrName + " is out of bounds at seq=" + tSkelSeqNo);
            return false;
        }
        
        // Make sure that the attribute found isn't a longer string (with
        // a suffix that matches the attribute we're searching for).
        boolean matched = false;
        while (!matched) {
//            if (skelBuf.substring(startsAt -1, startsAt).matches("\\s")) {
            if (skelBuf.charAt(startsAt-1) == ' ' ||
                skelBuf.charAt(startsAt-1) == '\n' ||
                skelBuf.charAt(startsAt-1) == '\r' ||
                skelBuf.charAt(startsAt-1) == '\t') {
                // Good. The starting point is preceded by a space (newline, etc.)
                matched = true;
            }
            else { // Look further
                startsAt = skelBuf.indexOf(attrName + "=", startsAt + 1);
                if (startsAt == -1 || startsAt > xmlStreamPos) {
                    // Out of range!! Bail out
                    System.err.println("XMLSkeletonMerger.seekBackToAttr: Index (" 
                            + Integer.toString(startsAt) + ") of attribute "
                            + attrName + " is out of bounds at seq=" + tSkelSeqNo);
                    return false;
                }
            }
        }
        
        // If we're still here, we ought to be positioned at the desired
        // attribute. Delete the current attribute value and adjust indexes, etc.
        char quotChar = skelBuf.charAt(startsAt + (attrName + "=").length());
        if (quotChar != '"' && quotChar != '\'') {
            System.err.println("XMLSkeletonMerger.seekBackToAttr: Invalid quote "
                    + "character: " + Character.toString(quotChar) + " at seq=" + tSkelSeqNo);
            return false;
        }
        // Where the attribute value begins:
        this.attrValOffset = startsAt + (attrName + "=").length() + 1;
        
        // Find the ending quote of the attribute value:
        int closeQuotePos = skelBuf.indexOf(Character.toString(quotChar), attrValOffset);
        if (closeQuotePos == -1) {
            System.err.println("XMLSkeletonMerger.seekBackToAttr: Cannot find "
                + "close quote of attribute value " + attrName + " at seq=" + tSkelSeqNo);
            return false;
        }
        
        // Compute the length of the existing attribute value
        int attrValLen = closeQuotePos - attrValOffset;
        
        // Then delete the attribute value from the skeleton buffer and adjust pointers.
        skelBuf.delete(attrValOffset, closeQuotePos);
        this.xmlStreamPos -= attrValLen;
        return true;
    }
    
    /**
     * Passed a tag name (from the intermediate skel file)--as well as an
     * indication of whether it is a start or end tag--find the next occurrence of
     * that tag (beginning at the current position) in the skeleton Buffer. 
     * Upon exit, the current position in the skeleton buffer should be one
     * position to the right of the closing '>' of the tag in argument 1.
     * <p>If the tag passed in is an end tag (</tag>) it *could* be a "dummy" tag
     * reported by SAX: Whenever SAX encounters an "empty" tag (<tag/>) it responds
     * by reporting a beginning tag followed immediately by an end tag. This method
     * checks to see if the previous tag in the original XML was an empty tag. If it was
     * then this method just exits ...
     * @param tag The tag to seek to
     * @param beginOrEnd Either "begin" or "end" (i.e. an opening or closing tag)
     * @param seekThru Seek to "before" or "after" the specified tag.
     */
    private void seekToTag(String tag, String beginOrEnd, String seekThru) {
        String tagPrefix = "";
        int tagStartsAt = -1;
        int endOfTag = -1;
        int seekTo = -1;
        
        // Handle CDATA delimiters--Seek just past the delimiter
        if (tag.equals("<![CDATA[")) {
            // Look for the start of CDATA, beginning at the current XML stream position.
            tagStartsAt = skelBuf.indexOf(tag,xmlStreamPos);
            if (tagStartsAt == -1) {
                System.err.println("XMLSkeletonMerger.seekToTag: Can't find CDATA start delimiter at seq=" + tSkelSeqNo);
                prevTagWasEmpty = false;  // Not empty (nonexistent, in fact! Actually, not a tag!)
                return;
            }
            else {
                xmlStreamPos = tagStartsAt + tag.length();
                return;
            }
        }
        else if (tag.equals("]]>")) {
            // Look for the end of CDATA, beginning at the current XML stream position.
            tagStartsAt = skelBuf.indexOf(tag,xmlStreamPos);
            if (tagStartsAt == -1) {
                System.err.println("XMLSkeletonMerger.seekToTag: Can't find CDATA end delimiter at seq=" + tSkelSeqNo);
                prevTagWasEmpty = false;  // Not empty (nonexistent, in fact! Actually, not a tag!)
                return;
            }
            else {
                xmlStreamPos = tagStartsAt + tag.length();
                return;
            }
        }

        
        if (beginOrEnd.equals("end")) {
            tagPrefix = "</" + tag; 
        }
        else {
            tagPrefix = "<" + tag; 
        }

        // Note: We need to handle the case where an end tag has whitespace
        // before the closing '>' (spans two lines, etc.)
        tagStartsAt = skelBuf.indexOf(tagPrefix,xmlStreamPos);
        if (tagStartsAt == -1) {
            System.err.println("XMLSkeletonMerger.seekToTag: Can't find start tag at seq=" + tSkelSeqNo);
            prevTagWasEmpty = false;  // Not empty (nonexistent, in fact!)
            return;
        }
        // Now look for the end of tag. (First, make sure that the tag we
        // matched isn't a superstring of the one that we are really looking
        // for. (E.g., If we are looking for </abcd>, make sure we didn't
        // find </abcdef>.)
        boolean found = false;
        char ch = 0;
        while (!found) {
//            if (skelBuf.substring(tagStartsAt + tagPrefix.length(), 
//                    tagStartsAt + tagPrefix.length() + 1).matches("[\\s/>]")) {
            if ((ch = skelBuf.charAt(tagStartsAt + tagPrefix.length())) == ' ' ||
                    ch == '\n' ||
                    ch == '\r' ||
                    ch == '/' ||
                    ch == '>') {
                // Yes, the substring is followed by whitespace or > (or / if empty element)
                found = true;
            }
            else { // We matched a longer tag whose prefix matches the one
                // we're looking for. Keep looking for an exact match.
                tagStartsAt = skelBuf.indexOf(tagPrefix,tagStartsAt + 1);
                if (tagStartsAt == -1) {
                    System.err.println("XMLSkeletonMerger.seekToTag: Cannot find start tag at seq=" + tSkelSeqNo);
                    prevTagWasEmpty = false;  // Not empty (nonexistent, in fact!)
                    return;
                }
            }
        }
        
        prevTagSeekOffset = tagStartsAt;  // Remember where tag starts (for attribute processing)
        
        if (seekThru.equals("before")) {
            seekTo = tagStartsAt;
        }
        else {  // Seek to *after* the tag.
            // Find the > at the end of the tag.
            endOfTag = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (endOfTag > -1) {     // Verify that there is a closing >
                // See if this is an empty element and note it duly
                if (skelBuf.charAt(endOfTag -1) == '/') {
                    prevTagWasEmpty = true;    // (Previous as far as next tag is concerned)
                }
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                seekTo = endOfTag + 1;
            }
            else {
                System.err.println("XMLSkeletonMerger.seekToTag: Cannot find end of tag " + tag + " at seq=" + tSkelSeqNo);
                prevTagWasEmpty = false;    // ... In fact, it was incomplete!
            }
        }
        xmlStreamPos = seekTo;  // The position either at the start of or after the end of tag.
        return;
    }
}
