/*
 * HtmlHandler.java
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

import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.cyberneko.html.filters.DefaultFilter;
import org.apache.xerces.xni.*;

import java.util.*;
import java.util.regex.*;
import java.lang.*;
import java.io.*;

/**
 * Class HtmlHandler uses HotSAX to read and parse HTML documents
 * as if they were well-formed XML documents using a SAX-like API.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class HtmlHandler extends DefaultHandler {

    // Initial set of tags that can cause a <trans-unit> break. (Initialized in
    // our private initHashes() method.)
    // We will populate this one later.
    private HashMap<String,String> formatCtype = new HashMap<String,String>();
    
    // This one we will populate only if actually used:
    private Stack<Integer> ridStack = new Stack<Integer>();

    // Stack to manage table elements.
    private Stack<String> tableStack = new Stack<String>();
    
    /**
     * Moving "empty" bookmark anchors "outside" the TU
     * We have received a request to identify anchor ("a") tags that meet
     * the following criteria:
     * <ul>
     * <li>They have a name attribute but no href attribute
     * <li>The opening and closing a tag has no text between the tags
     * </ul>
     * If such tags are identified and they are within the text of the TU
     * (i.e., if they are within the &lt;mrk mtype='x-coretext'&gt; ... &lt;/mrk&gt; element),
     * move them to the "left" (i.e. before) the opening mrk tag.
     * <p>The following array will record rid's of bookmark anchor tags
     * as they are mapped to bx tags.
     * <p>At the point of writing the TU to an XLIFF &lt;source&gt; element,
     * check the bx and ex tags that correspond to the rid(s) in the
     * bookMarkRids array. If the bx and ex tags have no characters between
     * them, move them to just before the opening &lt;mrk mtype='x-coretext'&gt; tag.
     * Then clear out the bookMarkRids for the next TU.
     */
    ArrayList<Integer> bookMarkRids = new ArrayList<Integer>();
    
    // We don't want to include text that is inside <style> or <script> tags (and 
    // maybe others) (Initialized in our private initHashes() method.)
    private HashSet<String> nonTuText = new HashSet<String>();
    private boolean ignoringText = false;   // We ignore test in <style>, <script> etc. tags

    // Some attributes have text that we want to translate. Specify their names in
    // the tuAttrs set. (Initialized in our private initHashes() method.)
    private HashSet<String> tuAttrs = new HashSet<String>();
    
    private Set<String> tuDelims;           // Tags that signal TU break
    private OutputStreamWriter outXliff;    // Where to write XLIFF
    private OutputStreamWriter outSkeleton;   // Where to write Target file
    private OutputStreamWriter outFormat;   // Where to write Format file
    private Locale sourceLang;              // Natural language of original
    private String docType;                 // Should be HTML
    private String originalFileName;        // For <file>'s "original" attribute
    
    private StringBuilder candidateTu = new StringBuilder(); // Working TU candidate
    private int curIndent = 0;              // How far to indent the next element
    
    private SegmentBoundary boundaryType;
    
    // Counters to store the current bx/ex reference IDs for the formatting tags:
    private int aRid = 1;                   // <a>
    private int bRid = 1;                   // <b>
    private int strongRid = 1;              // <strong>
    private int iRid = 1;                   // <i>
    private int emRid = 1;                  // <em>
    private int uRid = 1;                   // <u>
    private int subRid = 1;                 // <sub>
    private int supRid = 1;                 // <sup>

    // All of the above share a common RID numbering space. The following is used each
    // time a new bx is encountered.
    private int nextAvailRid = 1;           // All of the above use the same numbering space
    
    // Counter for the unique identifiers for the bx/ex formatting tags:
    private int bxExId = 1;
    // The next few variables are for finding where in the original HTML file we are
    //    (so that we can make a skeleton)
    private Locator locator;                // For tracking where we are in the in stream

    // Trackers for startElement() locator return values
    private int prevStartLine = 1;          // Line of previous start element
    
    private int curStartLine = 1;           // Line of current start element
    private int curStartColumn = 1;         // Column of current start element

    // Trackers for characters() locator return values
    private int prevCharLine = 1;           // Line of previous characters() call
    
    private int curCharLine = 1;            // Line of current characters() call
  
    private int curTagNum = 1;              // Fos skeleton
    
    private String tuAttrName = "";         // If tu is an attr value (e.g. alt ...)
                                            //   this holds its name.
    
    private boolean preservingCRLF = false; // Not currently preserving carriage returns/
                                            // line feeds.

    private HashSet<String> tableTags = new HashSet<String>();

    // Regex for bx-bx-text-ex-ex block
    String bxExPattern = "^(.*?)"
        // Match the first of two adjacent bx tags
        // Group 5 is 1st ctype; group 7 is 1st rid
        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>\\s*"
        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
        // important of the two ctypes
        // 2nd ctype is group 10
        // 2nd rid is group 12
        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>)" 
        // Then stuff between the above bxes and the closing exes
        + "([^<].*?)"                            // At least 1 character long, group 13
        // Then the first ex (with rid matching the second bx above)
        +  "(<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\12\\16 */>\\s*"
        // And the second ex (with rid matching the first bx above)
        +  "<ex id=(['\"])\\d+\\17[^>]+?rid=(['\"])\\7\\18 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    Matcher bxExMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");

    // Regex for bx-bx-ex-ex block (no intervening text) to map to x tag.
    String bxExToXPattern = "^(.*?)"
        // Match the first of two adjacent bx tags
        // Group 5 is 1st ctype; group 7 is 1st rid
        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>\\s*"
        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
        // important of the two ctypes
        // 2nd ctype is group 10
        // 2nd rid is group 12
        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>" 
        // Then the first ex (with rid matching the second bx above)
        +  "<ex id=(['\"])\\d+\\13[^>]+?rid=(['\"])\\12\\14 */>\\s*"
        // And the second ex (with rid matching the first bx above)
        +  "<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\7\\16 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    Matcher bxExToXMatcher = Pattern.compile(bxExToXPattern,Pattern.CASE_INSENSITIVE).matcher("");
    
    /** For testing only */
    public HtmlHandler() {}

    /**
     * This constructor sets up the HtmlHandler to be notified as
     * each tag (etc.) is encountered in the HTML input stream.
     * This handler then does whatever is appropriate with the HTML
     * input.
     * @param tuTags The set of HTML tags that signal a TU break.
     * @param outXliff Where to write the XLIFF 
     * @param outSkeleton Where to write the target
     * @param outFormat Where to write the format
     * @param sourceLang The language of this original
     * @param docType What kind of document is this (Probably HTML)
     * @param originalFileName The original file's name (required as an attribute
     *        in the "file" element)
     */
    public HtmlHandler(Set<String> tuTags, OutputStreamWriter outXliff, 
            OutputStreamWriter outSkeleton, OutputStreamWriter outFormat,
            Locale sourceLang, String docType, String originalFileName,
            SegmentBoundary boundary) {
        
        this.tuDelims = tuTags;
        this.outXliff = outXliff;
        this.outSkeleton = outSkeleton;
        this.outFormat = outFormat;
        this.sourceLang = sourceLang;       // Natural language of original
        this.docType = docType;             // HTML most of the time. 
        this.originalFileName = originalFileName;
        this.boundaryType = boundary;       // paragraph or sentence?
        if (this.boundaryType == null) {
            this.boundaryType = SegmentBoundary.SENTENCE;
        }

        initHashes();                       // Hashes tell what to skip and not.
    }

    /**
     * Method (inherited from Default Handler or one if its ancestors)
     * that sets the "locator"--in this case the org.xml.sax.helpers.LocatorImpl
     * class--which has methods that (among other things) return the current
     * line and column numbers in the stream being parsed.
     * @param locator A reference to the locator implementation class
     */
    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
    
    /**
     * Method called by SAX parser at the beginning of document parsing.
     */
    public void startDocument() throws SAXException {

        // Write the beginning of the XLIFF document
        try {
            // Write the "prolog" of the XLIFF file
            outXliff.write(Converter.xmlDeclaration);
            outXliff.write(Converter.startXliff);
            outXliff.write(indent() + "<file original='" 
                + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                + "' source-language='" + sourceLang.toString() 
                + "' datatype='" + docType.toLowerCase() + "'>\r\n");
            outXliff.write(indent() + "<header");
            switch(boundaryType) {
                case PARAGRAPH:  
                    outXliff.write(" lt:segtype='paragraph'");
                    break;
                case SENTENCE:
                    outXliff.write(" lt:segtype='sentence'");
                    break;
                default:
                    outXliff.write(" lt:segtype='sentence'");
            }
            
            outXliff.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
            outXliff.flush();

        }
        catch(IOException e) {
            System.err.println("Error writing XLIFF's XML declaration and preliminaries.");
            System.err.println(e.getMessage());
        }
        
        // Also write the beginning of the format file
        try {
            outFormat.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n");
            outFormat.write("<tags formatting='&lt;'>\r\n");
            outFormat.flush();
        }
        catch(IOException e) {
            System.err.println("Error writing format file's declaration and preliminaries.");
            System.err.println(e.getMessage());
        }
    }
    
    /**
     * Method called whenever a start element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if
     *        Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string 
     *        if qualified names are not available
     * @param atts The specified or defaulted attributes.
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {
	String elementName = localName;

        // The "cur" values are the coordinates of the next character *after*
        // the last character in this elementName:
        prevStartLine = curStartLine;                    // Save last time's value
        curStartLine = locator.getLineNumber();       // This time's value
        curStartColumn = locator.getColumnNumber();   //    Ditto
       
	// Use qualified name if local name is of zero length
	if ("".equals(elementName)) {
	    elementName = qualifiedName;
	}

        /****************************************************************
         * Do we need to break a TU?
         ****************************************************************/
	if (tuDelims.contains(elementName.toLowerCase())) { 
            writeCandidateTu();
            // See if we need to implicitly close table tags.
            if (tableTags.contains(elementName.toLowerCase())) {
                String [] implicit = this.handleTableTag(elementName);
                try {
                    for (int i = 0; i < implicit.length; i++) {
                        outSkeleton.write("</" + implicit[i] + " seq='" + curTagNum++ + "'>\r\n");
                    }
                    outSkeleton.flush();
                }
                catch(IOException e) {
                    System.err.println("Error while writing skeleton file.");
                    System.err.println(e.getMessage());
                }
            }
	}

        // Within pre elements, we preserve newlines.
        if (elementName.equalsIgnoreCase("pre")) {
            preservingCRLF = true;
        }

        // Write the start element to the intermediate skeleton file, ignoring 
        // the tags that become bx/ex/x tags.
        if (!formatCtype.containsKey(elementName.toLowerCase())) {
            try {
                outSkeleton.write("<" + elementName + " seq='" + curTagNum++ + "'>\r\n");
                outSkeleton.flush();
            }
            catch(IOException e) {
                System.err.println("Error while writing skeleton file.");
                System.err.println(e.getMessage());
            }
        }
        else {
            curTagNum++;
        }

        // See if this tag introduces text that we ignore.
        if (nonTuText.contains(elementName.toLowerCase())) {
            ignoringText = true;
        }
        else { 
            // Handle the case where a tag like <tt> is at the beginning
            // of a line, in the middle of a TU: We need to insert a newline
            // before the tag to give a break between words.
            if ((candidateTu.length() > 0 ) 
                && (curStartColumn == (elementName.length() + 3))
                && (curStartLine > prevStartLine)) {
                if (preservingCRLF) {
                    candidateTu.append("\n");
                }
                else {
                    candidateTu.append(" ");
                }
            }
        }
        
        /****************************************************************
         * Check for the formatting tags that we preserve. If this is one
         * of them, output a <bx/> tag (er, element)
         ****************************************************************/
        String fmtType = formatCtype.get(elementName.toLowerCase());
        if (fmtType != null) { // This is <i>, <a>, <b> or one of those tags
            if (elementName.equalsIgnoreCase("img") 
//                    || elementName.equalsIgnoreCase("br")
                    || elementName.equalsIgnoreCase("param")) {
                
                // img, br and param tags become <x/> tags
                candidateTu.append("<x id='" + bxExId + "' ");
            }
            else {
                candidateTu.append("<bx id='" + bxExId + "' ");
            }
            
            
            if (fmtType.length() > 0) {
                candidateTu.append("ctype='" + fmtType + "'");
            }

            UUID xid = null;      // Might point to TU ID of translatable attr.
            //////////////////////////////////////////
            // Time out: write the format file, looking for
            // attributes (within the bx/ex/x mapping) that
            // are translatable.
            // Write format file ...
            try {
                outFormat.write("  <tag id='" + bxExId + "'>" 
                        + elementName );
                
                // Now write the attributes (if they exist)
                if (atts != null) {
                    for (int i = 0; i < atts.getLength(); i++) {
                        String aName = atts.getLocalName(i); // Attr name

                        if ("".equals(aName)) {
                            aName = atts.getQName(i);
                        }
                        outFormat.write(" " + aName + "=\""); // Write attr=
                        if (atts.getValue(i) != null) {
                            // If this attribute has translatable text, write it to the
                            // TU buffer. (The tuAttrs map has attrs we translate)
                            if (tuAttrs.contains(aName.toLowerCase())) {
                                if (!writeAttrTuString(aName, elementName, 
                                        TuStrings.escapeTuString(atts.getValue(i)))) {
                                    outFormat.write("\"");  // Empty alt, title, etc.
                                }
                            }
                            else {
                                // Convert any newlines (!!!) to &#10;
                                outFormat.write(atts.getValue(i).replace("\n","&#10;") + "\"");
                            }
                        }
                        else {
                            outFormat.write("\"\"");   // Empty attribute value
                        }
                    }
                }
                
                // Then close the tag
                outFormat.write("></tag>\r\n");
                outFormat.flush();
            }
            catch(IOException e) {
                System.err.println("Error writing " + elementName + " start tag (id "
                        + bxExId + ") to format file");
                System.err.println(e.getMessage());
            }
            
            /////////////////////////////////////////////////////////////////////
            // Time in: resume writing the bx/ex/x to the candidate TU buffer
            
            // If this bx/ex/x tag references another TU (for a translateable attr) as
            // evidenced by a non-null xid, write that xid as part of the bx/ex/x tag.
            if (xid != null) {
                candidateTu.append(" xid='" + xid.toString() + "'");
            }
            
            if (elementName.equalsIgnoreCase("img") 
//                || elementName.equalsIgnoreCase("br")
                || elementName.equalsIgnoreCase("param")) {
                candidateTu.append("/>");  // x tags don't have rid's
            }
            else {
                candidateTu.append(" rid='" + nextAvailRid + "'/>");

                // Save current rid for matching closing tag; increment next avail for next time
                ridStack.push(Integer.valueOf(nextAvailRid));     
                nextAvailRid++;
            }
            
            // Then (I didn't forget!)
            bxExId++;      // For next time
        }
        
        // The tag doesn't map to bx/ex/x tags, ... but still might include
        // translatable text.
        else {  // Check for a translatable attribute in this tag
                //    and write it (if it exists)
            if (atts != null) {
                
                boolean isValueTag = false; // Is this a tag with potential value
                                            // attribute?
                String inputType = "";      // We don't know the input type yet
                String valueValue = "";     // Value of the value attribute
                
                // WWhipple 10/30/06: The value attribute of button, input and
                // option tags can contain translatable text. Unfortunately, 
                // changing that text (on submit and reset buttons, for example)
                // can break incompletely internationalized CGI scripts. (That
                // problem is outside the scope of this software ...)
                if (elementName.equalsIgnoreCase("button")
                    || elementName.equalsIgnoreCase("input")
                    || elementName.equalsIgnoreCase("option")) {
                    isValueTag = true;
                }
                // In the following loop, we unconditionally treat attributes
                // named in the tuAttrs hash as containing translatable text.
                // We also check for the "value" attribute of the following
                // tags:
                //   button
                //   input (of type button, reset, submit & text [add image type 4/13/7 wlw]--not hidden, however)
                //   option
                // We don't care about (for now, at least) the value attribute of
                // the li tag (deprecated) or of the param tag.
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getLocalName(i); // Attr name
                    if ("".equals(aName)) { 
                        aName = atts.getQName(i);
                    }
                        
                    if (atts.getValue(i) != null) {
                        // If this attribute has translatable text, write it to the
                        // TU buffer
                        if (tuAttrs.contains(aName.toLowerCase())) {
                            candidateTu.append(TuStrings.escapeTuString(atts.getValue(i)));
                            tuAttrName = aName;       // For use in writeCandidateTu
                            // Added 3/6/7 by WWhipple
                            // Should this always be writeAttrTuString?
                            // Not sure, so we will do it for table's summary attribute
                            if ((aName.toLowerCase().equals("summary") 
                                && elementName.toLowerCase().equals("table"))
                                || (aName.toLowerCase().equals("title") 
                                && elementName.toLowerCase().equals("link"))
                                || ((aName.toLowerCase().equals("alt") 
                                    || aName.toLowerCase().equals("title"))
                                && elementName.toLowerCase().equals("input"))) {
                                
                                writeAttrTuString(aName, elementName, 
                                        TuStrings.escapeTuString(candidateTu.toString()));
                                candidateTu.setLength(0);
                            }
                            else {
                                writeCandidateTu();       // Write placeholder in skel and write TU
                            }
                        }
                        // 10/30/2006 WWhipple: Handle value tag
                        else if (isValueTag) {
                            if (aName.equalsIgnoreCase("value")) {
                                valueValue = atts.getValue(i);
                            }
                            else if (aName.equalsIgnoreCase("type")) {
                                inputType = atts.getValue(i);
                            }
                        }
                    }
                }
                
                // If we have found a visible, translatable "value" attribute in
                // an input, button or option tag/element, create a TU
                if (isValueTag && (!inputType.equalsIgnoreCase("hidden"))
                    && (valueValue != null) && (valueValue.trim().length() > 0)) {
//                    candidateTu.append(TuStrings.escapeTuString(valueValue));
                    tuAttrName = "value";     // For use in writeCandidateTu
//                    writeCandidateTu();       // Write placeholder in skel and write TU
                    writeAttrTuString(tuAttrName, elementName, 
                                        TuStrings.escapeTuString(valueValue));
                }
            }
        }
        
        // Make note of the rid of an anchor that has a name attribute (but not an href)
        // so that we can handle it later if the anchor is "empty".
        if (elementName.equalsIgnoreCase("a") && isBookmarkAnchor(atts)) {
            if (ridStack.size() > 0) {
                bookMarkRids.add(ridStack.peek());
            }
        }
    }

    /**
     * Method called whenever an end element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string if qualified names are not available
     */
    public void endElement(String namespaceURI, 
			   String localName,
			   String qualifiedName) throws SAXException {
	
        String elementName = localName;

	// Use qualified name if local name is of zero length
	if ("".equals(elementName)) {
	    elementName = qualifiedName;
	}

        /****************************************************************
         * Do we need to break a TU?
         ****************************************************************/
	if (tuDelims.contains(elementName.toLowerCase())) { 
            writeCandidateTu();
            // See if we need to implicitly close table tags.
            if (tableTags.contains(elementName.toLowerCase())) {
                String [] implicit = this.handleTableTag("/" + elementName);
                try {
                    for (int i = 0; i < implicit.length; i++) {
                        outSkeleton.write("</" + implicit[i] + " seq='" + curTagNum++ + "'>\r\n");
                    }
                    outSkeleton.flush();
                }
                catch(IOException e) {
                    System.err.println("Error while writing skeleton file.");
                    System.err.println(e.getMessage());
                }
            }
            
	}

        // Except in pre elements, we convert newlines to spaces.
        if (elementName.equalsIgnoreCase("pre")) {
            preservingCRLF = false;
        }
        
        // Write the end element to the intermediate skeleton file, ignoring
        // the tags that become bx/ex tags.
        if (!formatCtype.containsKey(elementName.toLowerCase())) {
            try {
                outSkeleton.write("</" + elementName + " seq='" + curTagNum++ + "'>\r\n");
                outSkeleton.flush();
            }
            catch(IOException e) {
                System.err.println("Error while writing skeleton file.");
                System.err.println(e.getMessage());
            }
        }
        else {
            curTagNum++;
        }

        // See if this tag ends text that we ignore. (things like styles, scripts, etc.)
        if (nonTuText.contains(elementName.toLowerCase())) {
            ignoringText = false;
        }
        
        /****************************************************************
         * Check for the formatting tags that we preserve. If this is one
         * of them, output a <bx> tag (er, element)
         ****************************************************************/
        String fmtType = formatCtype.get(elementName.toLowerCase());
        if (fmtType != null) { // This is <i>, <a>, <b> or one of those tags
            // Skip ending img/br/param tags--they were <x/> tags when we saw
            // them in startElement()
            if (!(elementName.equalsIgnoreCase("img") 
                    || elementName.equalsIgnoreCase("br")
                    || elementName.equalsIgnoreCase("param"))) {
            
                candidateTu.append("<ex id='" + bxExId + "' "); 

                String ridStr = ridStack.pop().toString();
                candidateTu.append("rid='" + ridStr + "'/>");
        
                // Write format file ...
                try {
                    outFormat.write("  <tag id='" + bxExId + "'>/"
                            + elementName + "></tag>\r\n");
                    outFormat.flush();
                }
                catch(IOException e) {
                    System.err.println("Error writing " + elementName + " start tag (id "
                            + bxExId + ") to format file");
                    System.err.println(e.getMessage());
                }
            }
        
            // Then (I didn't forget!)
            bxExId++;      // For next time (Each bx and ex has a unique id attr
        }
    }

    /**
     * Called whenever characters are encountered
     * @param ch Array containing characters encountered
     * @param start Position in array of first applicable character
     * @param length How many characters are of interest?
     */
    public void characters(char[] ch,
			   int start,
			   int length) throws SAXException {
        String theString = new String(ch, start, length);

        // The "cur" values are the coordinates of the next character *after*
        // the last character in this sequence:
        prevCharLine = curCharLine;                    // Save last time's value
//        prevCharColumn = curCharColumn;                //    Ditto
        curCharLine = locator.getLineNumber();       // This time's value
//        curCharColumn = locator.getColumnNumber();   //    Ditto
        
        // If this isn't a blank line or in a tag (like <script> or <style>) that
        // we ignore, add it to the candidate TU buffer.
//        if (! (theString.matches("^\\s*$") || ignoringText)) {
        if (! ((theString.matches("^\\s*$") && (candidateTu.length() == 0)) 
            || ignoringText)) {

            // Escape the string so that we don't upset the SAX parser
            theString = TuStrings.escapeTuString(theString);
            // If we are appending to already-existing characters in the
            // candidateTu buffer and the previous sequence ended on a line
            // number (in the source file) that is before this one, then append
            // a newline to the buffer. (This will handle the case where this sequence
            // started on a new line with a tag that we ignore.)
            if (candidateTu.length() > 0) {
                int numLinesLater = curCharLine - prevCharLine;
                if (numLinesLater > 0) {
                    // On different lines. Find number of newlines in "theString"
                    int numNewLines = getNumNewlines(theString);
                    if ((curCharLine - prevCharLine) > numNewLines) {
                        if (preservingCRLF) {
                            candidateTu.append("\n");
                        }
                        else {
                            candidateTu.append(" ");
                        }
                    }
                }
            }
            
            // If we aren't inside a <pre> tag, change \n and \r to space
            if (! preservingCRLF) {
                theString = theString.replaceAll("\\s+", " ");
            }
            
            candidateTu.append(theString);  // Now append the string
        }
    }

    /**
     * Passed the full text of a Translation Unit source, check for nested
     * bx and ex tags. Replace multiply nested bx/ex tags with a single pair
     * of bx/ex tags, adding a format file entry to map the single bx tag to the
     * bx tags it replaces and the single ex tag to the ex tags it replaces.
     * <p>If the nested bx-bx-ex-ex tags have no intervening spaces, replace them
     * with a single x tag.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseNestedBxEx (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // If the input string doesn't include at least one bx tag, return the
        // input string unchanged.
        if (in.indexOf("<bx") == -1) {
            return in;
        }

        // Loop through the text looking for a bx-bx-text-ex-ex sequence. Replace
        // it with bx-text-ex.
        
//        // Regex for span-tab-endspan block
//        String bxExPattern = "^(.*?)"
//            // Match the first of two adjacent bx tags
//            // Group 5 is 1st ctype; group 7 is 1st rid
//            + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
//            // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
//            // important of the two ctypes
//            // 2nd ctype is group 10
//            // 2nd rid is group 12
//            + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>)" 
//            // Then stuff between the above bxes and the closing exes
//            + "([^<].*?)"                            // At least 1 character long, group 13
//            // Then the first ex (with rid matching the second bx above)
//            +  "(<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\12\\16 */>"
//            // And the second ex (with rid matching the first bx above)
//            +  "<ex id=(['\"])\\d+\\17[^>]+?rid=(['\"])\\7\\18 */>)"
//            // Text that follows the span:
//            +  "(.*)$";  // What follows
//
//        Matcher bxExMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");

        String curTuState = in;   // Start with what was passed in.
        
        // Two conditions might exist:
        // 1. 3+ levels of adjacent nesting might occur
        // 2. Nesting at the beginning of the TU might be followed by more
        //    nesting later on in the TU.
        boolean done = false;
        
        while (! done) {
            // Search on the remainder of the text
            // Start by checking the next nested tag.
            bxExMatcher.reset(curTuState);   // Search what's left of the TU
            if (bxExMatcher.find()) {
                String prefix = bxExMatcher.group(1);  // Before the first bx
                String bxBx   = bxExMatcher.group(2);  // First two adjacent bxes
                String enclosed = bxExMatcher.group(13); // Enclosed by bxbx exex 
                String exEx   = bxExMatcher.group(14); // Matching adjacent exes
                String suffix = bxExMatcher.group(19); // AFter last ex
                String outerCtype = bxExMatcher.group(5);  // Outer ctype
                String innerCtype = bxExMatcher.group(10); // Inner ctype
                
                int bxID = bxExId++;
                int exID = bxExId++;
                try {
                    
                    // Write adjacent bxes to format file
                    outFormat.write("  <tag id='" + bxID + "'>" 
                            + bxBx.substring(1));
                    // Then close the tag
                    outFormat.write("</tag>\r\n");

                    //Then write adjacent exes
                    outFormat.write("  <tag id='" + exID + "'>" 
                            + exEx.substring(1));
                    // Then close the tag
                    outFormat.write("</tag>\r\n");

                    
                    outFormat.flush();
                }
                catch(IOException e) {
                    System.err.println("Error writing nested bx/ex tags to format file");
                    System.err.println(e.getMessage());
                }
                
                // Select a ctype that will make most sense to the translator
                // If both start with "x-", use the inner
                // If one starts with "x-", use the other one
                // If neither starts with "x-", prefer "link", else inner ctype
                String preferredCtype = "";
                if (outerCtype.equals("link") || innerCtype.equals("link")) {
                    preferredCtype = "link";
                }
                else if (outerCtype.startsWith("x-")) {
                    preferredCtype = innerCtype;
                }
                else { // outer *doesn't* start with "x-"
                    if (innerCtype.startsWith("x-")) {
                        preferredCtype = outerCtype;
                    }
                    else {  // Neither starts with "x-"; prefer inner
                        preferredCtype = innerCtype;
                    }
                }
                
                // Reconstruct the string with fewer bx/ex elements.
                curTuState = prefix + "<bx id='" + bxID + "' ctype='" + preferredCtype
                        + "' rid='" + nextAvailRid + "'/>"
                        + enclosed
                        + "<ex id='" + exID + "' rid='" + nextAvailRid + "'/>"
                        + suffix;

                // Increment nextAvailRid
                nextAvailRid++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }

//    // Regex for bx-bx-ex-ex block (no intervening text) to map to x tag.
//    String bxExToXPattern = "^(.*?)"
//        // Match the first of two adjacent bx tags
//        // Group 5 is 1st ctype; group 7 is 1st rid
//        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
//        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
//        // important of the two ctypes
//        // 2nd ctype is group 10
//        // 2nd rid is group 12
//        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>" 
//        // Then the first ex (with rid matching the second bx above)
//        +  "<ex id=(['\"])\\d+\\13[^>]+?rid=(['\"])\\12\\14 */>"
//        // And the second ex (with rid matching the first bx above)
//        +  "<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\7\\16 */>)"
//        // Text that follows the span:
//        +  "(.*)$";  // What follows
//
//    Matcher bxExToXMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");
        
        // 4/12/2007: Convert <bx/><bx/><ex/><ex/> (with no intervening text) to a single x.
        done = false;
        
        while (! done) {
            // Search on the remainder of the text
            // Start by checking the next nested tag.
            bxExToXMatcher.reset(curTuState);   // Search what's left of the TU
            if (bxExToXMatcher.find()) {
                String prefix = bxExToXMatcher.group(1);     // Before the first bx
                String bxBxExEx = bxExToXMatcher.group(2);   // All four adjacent bx/bx/ex/ex
                String suffix = bxExToXMatcher.group(17);    // After last ex
                String outerCtype = bxExToXMatcher.group(5);  // Outer ctype
                String innerCtype = bxExToXMatcher.group(10); // Inner ctype

                
//                // Write adjacent bxes to format file
//                writeFormatLiteral(bxBxExEx,bxExXId);
//                // and increment the id
                int bxBxExExId = bxExId++;
                
                try {
                    
                    // Write adjacent bxes to format file
                    outFormat.write("  <tag id='" + bxBxExExId + "'>" 
                            + bxBxExEx.substring(1));
                    // Then close the tag
                    outFormat.write("</tag>\r\n");
                    
                    outFormat.flush();
                }
                catch(IOException e) {
                    System.err.println("Error writing nested bx/ex tags to format file");
                    System.err.println(e.getMessage());
                }
                
                // Select a ctype that will make most sense to the translator
                // If both start with "x-", use the inner
                // If one starts with "x-", use the other one
                // If neither starts with "x-", prefer "link", else inner ctype
                String preferredCtype = "";
                if (outerCtype.equals("link") || innerCtype.equals("link")) {
                    preferredCtype = "link";
                }
                else if (outerCtype.startsWith("x-")) {
                    preferredCtype = innerCtype;
                }
                else { // outer *doesn't* start with "x-"
                    if (innerCtype.startsWith("x-")) {
                        preferredCtype = outerCtype;
                    }
                    else {  // Neither starts with "x-"; prefer inner
                        preferredCtype = innerCtype;
                    }
                }
                
                // Reconstruct the string with fewer bx/ex elements.
                curTuState = prefix + "<x id='" + bxBxExExId + "' ctype='" + preferredCtype
                        + "'/>"
                        + suffix;

                // Increment nextAvailRid
                nextAvailRid++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }
        
        return (curTuState);  // Return the collapsed TU
    }
    
    /**
     * When the end-of-document is encountered, save the "candidate
     * epilog" (the characters that follow the final TU), etc.
     */
    public void endDocument()
	throws SAXException {
        
        // Finish off the XLIFF file:
        try {
            outXliff.write(indent('-') + "</body>\r\n"); // Close the body element
            outXliff.write(indent('-') + "</file>\r\n"); // Close the file element
            outXliff.write("</xliff>\r\n");               // Close the xliff element
            outXliff.flush();             // Well-bred writers flush when finished
            
        }
        catch(IOException e) {
            System.err.println("Error writing tags at end of XLIFF document.");
            System.err.println(e.getMessage());
        }

        // Also finish off the format file:
        try {
            outFormat.write("</tags>\r\n");
            outFormat.flush();
        }
        catch(IOException e) {
            System.err.println("Error writing tags at end of format document.");
            System.err.println(e.getMessage());
        }

        // Flush the skeleton file
        try {
            outSkeleton.flush();
        }
        catch(IOException e) {
            System.err.println("Error flushing skeleton stream.");
            System.err.println(e.getMessage());
        }
    }

    /**
     * Passed an un-"preened" TU string (i.e. one in which the "core" area of 
     * the string has not been surrounded with <mrk mtype='x-coretext'> and </mrk> tags), 
     * look for matching (opening and closing) anchor tags that have no 
     * characters between them and are not at the very beginning of the TU. 
     * If such bookmarks are identified, move them to the very beginning of
     * the string.
     * <p>Note: By the time we are called, the anchor tags have been changed
     * to a bx/ex XLIFF tag pair, "linked" with a common rid attribute.
     * @param tuString The TU string with possible empty bookmark anchors
     * @return The string passed in, modified (if appropriate) so that the
     *         empty bookmark anchor(s) are at the beginning of the string.
     */
    String moveUnpreenedEmptyBookmarks(String tuString) {
        String newString = tuString;
        
        // Make certain that we have rid's to check.
        if ((bookMarkRids == null) || bookMarkRids.isEmpty()) {
            return newString;
        }
        
        // For every RID in the bookMarkRids array (starting at the last one, to
        // maintain their relative order.
        for (int i = bookMarkRids.size()-1; i >= 0; i--) {

            Integer rid = bookMarkRids.get(i); // Get the next rid (working from the end)
            // Here is a regex to find (and capture) the adjacent bx/ex tags with the current
            // rid (from bookMarkRids):
            String p = "^(.+)(<bx\\s.*?rid=(['\"])" + rid.toString() + "\\3[^>]*?>"
                + "<ex\\s+.*?rid=(['\"])" + rid.toString() + "\\4[^>]*?>)(.*)$";
            Matcher m = Pattern.compile(p, Pattern.DOTALL).matcher("");

            // If we find two adjacent bookmark-only anchors, move them to the start
            // of the string.
            m.reset(newString);
            if (m.find()) {
                newString = m.group(2) + m.group(1) + m.group(5);
            }
        }
        
        return newString;
    }
    
    /**
     * Passed a buffer of characters, count the number of newlines. (A newline
     * consists of any of these:
     * <ul>
     * <li>carriage return</li>
     * <li>linefeed</li>
     * <li>carriage return and linefeed adjacent to each other</li>
     * </ul>
     * @param sequence a sequence of characters
     * @return the number of lines (minus 1) in the sequence
     */
    private int getNumNewlines(String sequence) {
        int total = 0;          // Total newlines so far
        char prevCh = 0;        // Previous char (We don't care about char 0)
        char curCh = 0;         // Current char
        for (int i = 0; i < sequence.length(); i++) {
            prevCh = curCh;    // Keep track of last iteration ...
            curCh = sequence.charAt(i);
            // Count adjacent CR and LF as one new line
            if (((curCh == '\n') && (prevCh == '\r')) 
                || ((curCh == '\r') && (prevCh == '\n'))) {
                // We've already counted this newline when what is now the
                // prevCh was encountered
                curCh = 0; // In case a sequence of \r\n\r\n for example
            }
            else if ((curCh == '\n') || (curCh == '\r')) {
                total++;
            }
        }
        return total;
    }

    /**
     * Method to help deal with the "autoclosing" of table subelements, which
     * the Neko parser closes--but not always predictably. Its input string can
     * be any of the following: 
     * <ol>
     * <li>"table", "/table"
     * <li>"tr", "/tr"
     * <li>"td", "/td"
     * <li>"th", "/th" 
     * <li>"thead", "/thead"
     * <li>"tbody", "/tbody"
     * <li>"tfoot", "/tfoot"
     * </ol>
     * Depending on the input string, handleTableTag may do any of the following:
     * <dl>
     * <dt><b>table</b>
     *   <dd>Push table onto the stack. Return nothing
     * <dt><b>/table</b>
     *   <dd>If table is at the top of the stack, pop the stack. Otherwise,
     *       pop the stack until table is found; then pop table. Return (in order
     *       starting at index 0 of the return array of String) the elements
     *       popped off the stack.
     * <dt><b>tr</b>
     *   <dd><ul>
     *       <li>If table, thead, tbody or tfoot is at the top of the stack, push
     *       tr onto the stack, returning nothing.</li>
     *       <li>If tr is at the top of the stack, leave it there, but return
     *           an array of one element, "tr"
     *       <li>If td is at the top of the stack, pop the stack return that
     *           element at position 0 of the return array. 
     *           <ul>
     *           <li>If the new top of stack is tr, leave it there, returning
     *               tr in position 1 of the return array.</li>
     *           <li>If the new top of stack is thead, tbody or tfoot, push
     *               tr onto the stack and return tr in position 1 of the return
     *               array.</li>
     *           </ul>
     *       <li> If th is at the top of the stack, see "If td is at the top of
     *           the stack" above, do the same (substituting th for td).</li></ul></dd>
     * <dt><b>/tr</b>
     *   <dd>If tr is at the top of the stack, pop it (returning nothing); if
     *       td is at the top of the stack, pop it and the tr, returning "td"
     *       as element 0 of the return array. If anything else is at the top
     *       of the stack print an error to stderr and do nothing else.
     * <dt><b>td></b>
     *   <dd>If td is at the top of the stack, leave it, returning "td" as the
     *       sole element of the return array; otherwise push "td" onto the
     *       stack.
     * <dt><b>/td</b>
     *   <dd>If td is at the top of the stack, pop it, returning nothing. If
     *       td isn't at the top of the stack, mention it in stderr.
     * <dt><b>th</b>
     *   <dd>See td above. (Behave similarly.)
     * <dt><b>/th</b>
     *   <dd>See /td above. (Behave similarly.)
     * <dt><b>thead</b>, <b>tbody</b>, <b>tfoot</b>
     *   <dd>If table is at the top of the stack, push new element onto stack.
     *       If anything else is at the top of the stack, pop them off until
     *       table is at the top of the stack. Return elements popped off the
     *       stack (including "missing" intermediate tr, thead, tbody, tfoot).
     *       Then push thead, tbody or tfoot onto the stack (above table).
     * <dt><b>/thead</b>, <b>/tbody</b>, <b>/tfoot</b>
     *   <dd><ul>
     *       <li>If thead, tbody or tfoot is at the top of the stack, pop it off;
     *           return nothing.
     *       <li>If td, tr or th is at the top of the stack, pop it off and return 
     *           as element 0 of the return array. Also return implied intermediate
     *           elements if missing (specifically, tr).
     *       <li>If anything else is at the top of the stack, mention it on
     *           std err and return (nothing).</li>
     *       </ul></dd>
     * </dl>
     * @param tag The table tag that was encountered
     * @return array of strings of tags that were implicitly closed, in order
     *       (with the top of stack in position 0 of the return array).
     */
    private String[] handleTableTag(String tag) {
        String[] empty = new String[0];
        if ("".equals(tag)) {
            return empty;
        }
        
        // This might be a close tag that we closed ourselves earlier. If it is,
        // just ignore.
        if (tag.charAt(0) == '/' && tableStack.empty()) {
            return empty;
        } 
            
        String lcTag = tag.toLowerCase();
        // Always safe to push table, since they can be imbedded. (We assume that
        // it is within a td/th if imbedded.)
        if (lcTag.equals("table")) {
            this.tableStack.push(lcTag);
            return empty;
        }
        // End of table.
        else if (lcTag.equals("/table")) {
            if (tableStack.peek().equals("table")) {
                tableStack.pop();        // Pop table off the top
                return empty;
            }
            // This table implicitly closes other tags.
            else if (tableStack.peek().equals("td")) {  // Unclosed td, tr
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "td", "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("tr")) {  // Unclosed tr
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("th")) {   // Unclosed th, tr
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "th", "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("thead")) {
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "thead" };
                return tagArray;
            }
            else if (tableStack.peek().equals("tbody")) {
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "tbody" };
                return tagArray;                
            }
            else if (tableStack.peek().equals("tfoot")) {
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("table") && !tableStack.empty());
                String[] tagArray = { "tfoot" };
                return tagArray;                                
            }
        }

        // The thead start tag is a child of table
        else if (lcTag.equals("thead")) {
            if (tableStack.peek().equals("table")) {
                tableStack.push("thead");        // thead is a child of table
                return empty;
            }
            else if (tableStack.peek().equals("tr")) {  // Close tr
                tableStack.pop();    // Pop the tr
                tableStack.push("thead");
                String[] tagArray = { "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("td")) {  // Close td and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("thead");
                String[] tagArray = { "td", "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("th")) {  // Close th and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("thead");
                String[] tagArray = { "th", "tr" };
                return tagArray;                                
            }
        }

        // End of thead
        else if (lcTag.equals("/thead")) {
            if (tableStack.peek().equals("table")) { // end of thead without start
                return empty;                        // Just do nothing
            }
            else if (tableStack.peek().equals("thead")) { 
                tableStack.pop();                    // Close the thead element
                return empty;                        
            }
            // This table implicitly closes other tags.
            else if (tableStack.peek().equals("td")) {  // Unclosed td, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "td", "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("tr")) {  // Unclosed tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("th")) {   // Unclosed th, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "th", "tr" };
                return tagArray;
            }
        }

        // The tbody start tag is a child of table
        else if (lcTag.equals("tbody")) {
            // These 4 don't need to be closed yet.
            if (tableStack.peek().equals("table")) {
                tableStack.push("tbody");        // tbody is a child of table
                return empty;
            }
            else if (tableStack.peek().equals("thead")) {  // Probably bogus
                // Pop and push the stack
                tableStack.pop();
                tableStack.push("tbody");
                String[] tagArray = { "thead" }; // Let's say we closed thead
                return tagArray;                                
            }
            else if (tableStack.peek().equals("tr")) {  // Close tr
                tableStack.pop();    // Pop the tr
                tableStack.push("tbody");
                String[] tagArray = { "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("td")) {  // Close td and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("tbody");
                String[] tagArray = { "td", "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("th")) {  // Close th and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("tbody");
                String[] tagArray = { "th", "tr" };
                return tagArray;                                
            }
        }

        // End of tbody
        else if (lcTag.equals("/tbody")) {
            if (tableStack.peek().equals("table")) { // end of thead without start
                return empty;                        // Just do nothing
            }
            else if (tableStack.peek().equals("tbody")) { 
                tableStack.pop();                    // Close the tbody element
                return empty;                        
            }
            // This tbody implicitly closes other tags.
            else if (tableStack.peek().equals("td")) {  // Unclosed td, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "td", "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("tr")) {  // Unclosed tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("th")) {   // Unclosed th, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "th", "tr" };
                return tagArray;
            }
        }

        // The tfoot start tag is a child of table
        else if (lcTag.equals("tfoot")) {
            if (tableStack.peek().equals("table")) {
                tableStack.push("tfoot");        // tfoot is a child of table
                return empty;
            }
            else if (tableStack.peek().equals("thead")) {  // Probably bogus
                // Pop and push the stack
                tableStack.pop();
                tableStack.push("tfoot");
                String[] tagArray = { "thead" }; // Let's say we closed thead
                return tagArray;                                
            }
            else if (tableStack.peek().equals("tbody")) {  // Probably bogus
                // Pop and push the stack
                tableStack.pop();
                tableStack.push("tfoot");
                String[] tagArray = { "tbody" }; // Let's say we closed thead
                return tagArray;                                
            }
            else if (tableStack.peek().equals("tr")) {  // Close tr
                tableStack.pop();    // Pop the tr
                tableStack.push("tfoot");
                String[] tagArray = { "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("td")) {  // Close td and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("tfoot");
                String[] tagArray = { "td", "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("th")) {  // Close th and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                tableStack.push("tfoot");
                String[] tagArray = { "th", "tr" };
                return tagArray;                                
            }
        }

        // End of tfoot
        else if (lcTag.equals("/tfoot")) {
            if (tableStack.peek().equals("table")) { // end of thead without start
                return empty;                        // Just do nothing
            }
            else if (tableStack.peek().equals("tfoot")) { 
                tableStack.pop();                    // Close the tfoot element
                return empty;                        
            }
            // This tfoot implicitly closes other tags.
            else if (tableStack.peek().equals("td")) {  // Unclosed td, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "td", "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("tr")) {  // Unclosed tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "tr" };
                return tagArray;
            }
            else if (tableStack.peek().equals("th")) {   // Unclosed th, tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("table"))) {   
                    tableStack.pop();    // Pop to (not including) table
                };
                String[] tagArray = { "th", "tr" };
                return tagArray;
            }
        }
        
        // The tr start tag implicitly closes th, td, tr.
        else if (lcTag.equals("tr")) {
            // These 4 don't need to be closed yet.
            if (tableStack.peek().equals("table")
                || tableStack.peek().equals("thead")
                || tableStack.peek().equals("tbody")
                || tableStack.peek().equals("tfoot")) {
                tableStack.push("tr");        // tr logically follows the above
                return empty;
            }
            else if (tableStack.peek().equals("tr")) {  // Previous tr not closed
                // Leave the stack; return the tr that we closed.
                String[] tagArray = { "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("td")) {  // Close td and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("tr"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                String[] tagArray = { "td", "tr" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("th")) {  // Close th and tr
                while ((!tableStack.empty()) && (!tableStack.peek().equals("tr"))) {   
                    tableStack.pop();    // Pop to (not including) tr
                };
                String[] tagArray = { "th", "tr" };
                return tagArray;                                
            }
        }

        // The tr end tag implicitly closes th and td (and explicitly tr).
        else if (lcTag.equals("/tr")) {
            if (tableStack.peek().equals("tr")) {  // Just pop the stack
                tableStack.pop();
                return empty;                                
            }
            else if (tableStack.peek().equals("td")) {  // Close td
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("tr") && !tableStack.empty());
                String[] tagArray = { "td"};
                return tagArray;
            }
            else if (tableStack.peek().equals("th")) {  // Close th
                String top;
                do {   
                    top = tableStack.pop();    // Pop thru table
                } while(!top.equals("tr") && !tableStack.empty());
                String[] tagArray = { "th"};
                return tagArray;
            }
        }

        // td start tag will implicitly close td and th(?)
        else if (lcTag.equals("td")) {
            // If preceded by tr, just push td
            if (tableStack.peek().equals("tr")) {
                tableStack.push("td");        // td logically follows tr
                return empty;
            }
            else if (tableStack.peek().equals("td")) {  // Previous td not closed
                // Leave the stack; return the td that we closed.
                String[] tagArray = { "td" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("th")) {  // th not closed (unusual??)
                tableStack.pop();     // Pop/discard the th
                tableStack.push("td");
                String[] tagArray = { "th" };    // We closed a th
                return tagArray;                                
            }
        }

        // td end tag will close only td start tag
        else if (lcTag.equals("/td")) {
            if (tableStack.peek().equals("td")) {  // td to close
                tableStack.pop();   // Remove from stack
                return empty;
            }
        }
    
        // th start tag will implicitly close td(?) and th
        else if (lcTag.equals("th")) {
            // If preceded by tr, just push th
            if (tableStack.peek().equals("tr")) {
                tableStack.push("th");        // th logically follows tr
                return empty;
            }
            else if (tableStack.peek().equals("th")) {  // Previous th not closed
                // Leave the stack; return the th that we closed.
                String[] tagArray = { "th" };
                return tagArray;                                
            }
            else if (tableStack.peek().equals("td")) {  // td not closed
                tableStack.pop();     // Pop/discard the td
                tableStack.push("th");
                String[] tagArray = { "th" };    // We closed a th
                return tagArray;                                
            }
        }

        // th end tag will close only th start tag
        else if (lcTag.equals("/th")) {
            if (tableStack.peek().equals("th")) {  // th to close
                tableStack.pop();   // Remove from stack
                return empty;
            }
        }
        
        return empty;
    }
    
    /**
     * Return an indentation string of blanks, increasing the indentation by 1 space
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent() {
        return indent('+');
    }
    
    /**
     * Return an indentation string based on the current indentation
     * @param direction '+': increment by 1; '-': decrement by 1; '0': no change
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent(char direction) {
        switch(direction) {
            case '-': 
                curIndent -= 2;        // Decrease indentation
                break;
            case '0':               // No change
                break;

            case '+':               // Increase by one space.
            default:
                curIndent +=2;
                break;              // For completeness (redundancy)
        }
        if (curIndent < 0) curIndent = 0; // 0-length string is smallest
        
        char chars[] = new char[curIndent]; // This could be a zero-length string
        
        if (curIndent > 0) {
            Arrays.fill(chars, ' ');  // Fill the array with spaces ...
        }
        
        return new String(chars);   //   and return it.
    }

    /**
     * Initialize hash sets and maps and other structures that are used in parsing
     * HTML.
     */
    private void initHashes() {
        
        // We maintain a record (in the XLIFF) of the following HTML tags--well,
        // at least those that always appear only as part of text:
        formatCtype.put("a",       "link");
        formatCtype.put("abbr",    "x-html-abbr");     
        formatCtype.put("acronym", "x-html-acronym");     
        formatCtype.put("applet",  "x-html-applet");
        formatCtype.put("b",       "bold");
        formatCtype.put("big",     "x-html-big");
        formatCtype.put("blink",   "x-html-blink");
//        formatCtype.put("br",      "lb");              // becomes <x/> 
        formatCtype.put("cite",    "x-html-cite");
        formatCtype.put("code",    "x-html-code");
        formatCtype.put("del",     "x-html-del");     
        formatCtype.put("em",      "italic");     // Most browsers display em[phasized] as italic
        formatCtype.put("embed",   "x-html-embed");
        formatCtype.put("font",    "x-html-font");
        formatCtype.put("i",       "italic");     
        formatCtype.put("img",     "image");           // becomes <x/> 
        formatCtype.put("ins",     "x-html-ins");     
        formatCtype.put("kbd",     "x-html-kbd");
        formatCtype.put("nobr",    "x-html-nobr");
        formatCtype.put("object",  "x-html-object");
        formatCtype.put("param",   "x-html-param");    // becomes <x/> (child of applet/object)
        formatCtype.put("s",       "x-html-s");     
        formatCtype.put("samp",    "x-html-samp");     
        formatCtype.put("small",   "x-html-small");     
        formatCtype.put("span",    "x-html-span");     
        formatCtype.put("strike",  "x-html-strike");     
        formatCtype.put("strong",  "bold");   // Most browsers display strong as bold
        formatCtype.put("sub",     "x-html-sub"); 
        formatCtype.put("sup",     "x-html-sup");
        //formatCtype.put("sub", "subscript"); 
        //formatCtype.put("sup", "superscript");
        formatCtype.put("tt",      "x-html-tt");     
        formatCtype.put("u",       "underline"); 
        formatCtype.put("var",     "x-html-var");     
        
        
        
        // These tags probably include text that isn't meaningful for translation:
        nonTuText.add("style");
        nonTuText.add("script");
        nonTuText.add("comment");      // Internet Exploiter and WebTV only--non-displayed

        // These attributes might have text that we want to translate:
        tuAttrs.add("alt");      // Alternate text if img etc. can't be displayed
        tuAttrs.add("title");    // For "balloon" hints/"tool tips"
        tuAttrs.add("summary");  // Used in the table tag.
        
        // Table tags we handle.
        tableTags.add("table");
        tableTags.add("thead");
        tableTags.add("tbody");
        tableTags.add("tfoot");
        tableTags.add("tr");
        tableTags.add("td");
        tableTags.add("th");
        
    }

    /**
     * Do the attributes (from an anchor tag) passed to this method indicate
     * that it is a bookmark (i.e., has a name attribute) but not an href?
     * @param atts The attribute list of the anchor tag
     * @return true if the list includes a name attribute but no href
     *          attribute; else returns false.
     */ 
    private boolean isBookmarkAnchor(Attributes atts) {
        boolean hasNameAttr = false;
        boolean hasHrefAttr = false;
        
        if (atts != null) {   // Don't mess with a null list
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getLocalName(i); // Attr name

                // Get the qualified name if the local name doesn't exist.
                if ("".equals(aName)) {          // Not very likely ...
                    aName = atts.getQName(i);
                }

                // Watch for name and href attributes
                if (aName.equalsIgnoreCase("name")) {
                    hasNameAttr = true;
                }
                else if (aName.equalsIgnoreCase("href")) {
                    hasHrefAttr = true;
                }
            }
        }
        
        // A single anchor tag can be both a bookmark and an
        // href (at the same time). We are interested in only those
        // that are a bookmark but *not* an href.
        if (hasNameAttr && !hasHrefAttr) {
            return true;
        }
        else {
            return false;
        }
    }
    
    /**
     * Process the contents of the candidateTu buffer. Close the current 
     * translation unit (if one is in progress). Essentially, what this
     * amounts to is checking the candidateTu buffer to see if there is anything 
     * meaningful in it. If so, create a TU from scratch, placing the contents of the
     * candidateTu buffer inside the TU within the XLIFF file.
     * <p>If there is something meaningful in the TU buffer, also write one or
     * two lines to the temporary skeleton:
     * <ol>
     * <li>an "attr name" line if this is a value of an attribute
     * <li>a "tu id=' '" line (always).
     * </ol>
     */
    private void writeCandidateTu() {
        if (candidateTu.length() == 0) {
            return;                // Nothing to write.
        }
        // Create a string from the string builder, replacing runs of 2+ spaces with
        // a single space.
        String candidateTuStr = candidateTu.toString().replaceAll("  +", " ");

        // Before breaking the candidate TU into segments, move any empty bookmarks
        // to the beginning of the string.
        if (bookMarkRids.size() > 0) {
            candidateTuStr = moveUnpreenedEmptyBookmarks(candidateTuStr);
            bookMarkRids.clear();  // Empty the list.
        }
        
        // Identify any nested bx-bx ex-ex pairs and collapse them to fewer tags.
        candidateTuStr = this.collapseNestedBxEx(candidateTuStr);
        
        // Get the core segments:
//        SegmentInfo[] coreTus = TuPreener.getCoreSegments(new String(candidateTu), 
//                this.boundaryType, this.sourceLang);
        SegmentInfo[] coreTus = TuPreener.getCoreSegments(candidateTuStr, 
                this.boundaryType, this.sourceLang);
        
        // UUID for the first (or possibly *only*) segment:
        UUID curTuID = UUID.randomUUID();
        UUID paraID = curTuID;  // Paragraph identified by its first sentence.
        
        // Process each segment. If a segment contains translatable text,
        // create a TU for it (in the XLIFF) and write a "tu" line to the temporary
        // skeleton. If the segment doesn't contain translatable text, write the
        // segment to the format file, and write a "format" line in the temporary
        // skeleton that references the segment in the format file.
        for (int i = 0; i < coreTus.length; i++) {
            if (coreTus[i].translatable()) { 
                String coreSeg = coreTus[i].getSegmentStr();
            
                // See if this segment has a translatable successor. It it does,
                // then it is mergeable.
                boolean hasSuccessor = false;
                if (i < (coreTus.length-1)) {  // If this *isn't* the last segment ...
                    FIND_SUCCESSOR:
                    for (int j = (i + 1); j < coreTus.length; j++) {
                        if (coreTus[j].translatable()) {
                            hasSuccessor = true;
                            break FIND_SUCCESSOR;
                        }
                    }
                }
                
//                UUID curTuId = 
                writeTuString(coreSeg, hasSuccessor, curTuID, paraID);

//                if (curTuId != null) {  // It *should* never be null!!

                // Write to the intermediate skeleton file
                try {
                    outSkeleton.write("<tu id='" + curTuID.toString() + "' length='" 
                        + (coreSeg.length() - (TuPreener.CORE_START_MRK + TuPreener.CORE_END_MRK).length()) 
                        + "' " + "no='" + (i+1) + "' of='" + coreTus.length + "'>\r\n");
                    outSkeleton.flush();
                }
                catch(IOException e) {
                    System.err.println("Error while writing skeleton file.");
                    System.err.println(e.getMessage());
                }
//                }
            }
            else {  // Write the non-translatable segment to the format file
                try {
                    outFormat.write("  <tag id='" + bxExId + "'>");
                    outFormat.write(coreTus[i].getSegmentStr());
                    outFormat.write("</tag>\r\n");
                    outFormat.flush();

                    // and a reference to it in tskeleton
                    outSkeleton.write("<format id='" + bxExId + "' length='" 
                        + (coreTus[i].getSegmentStr().length()) + "' "
                        + "no='" + (i+1) + "' of='" + coreTus.length + "'>\r\n");
                    outSkeleton.flush();
                    bxExId++;
                }
                catch(IOException e) {
                    System.err.println("Error while writing non-translatable "
                            + "segment to format or skeleton file.");
                    System.err.println(e.getMessage());
                }
            }
            
            // UUID for the next TU in this paragraph (or possibly a throw-away
            // UUID ...)
            curTuID = UUID.randomUUID();
        }

        // Clear out the attribute name for the future.
        this.tuAttrName = "";
        candidateTu.setLength(0);       // Also clear the candidateTu buffer

    }

    /**
     * Passed a string that appears in an attribute of an HTML tag--and might
     * have translatable text--segment it as requested and generate zero or more
     * TUs. Add lines to the XLIFF, format and tskeleton files as appropriate.
     * @param attrName The name of the attribute that contains the possible TU
     *        string.
     * @param parentElement The name of the parent element that has this attribute.
     * @param candidateTuStr The text from the value of the attribute.
     * @return true if the attribute string contained translatable text; false
     *         otherwise.
     */
    private boolean writeAttrTuString(String attrName, String parentElement,
            String candidateTuStr) {
        if ((candidateTuStr == null) || (candidateTuStr.trim().length() == 0)) {
            return false;
        }
        
        // Get the core segments:
        SegmentInfo[] coreTus = TuPreener.getCoreSegments(candidateTuStr, 
                this.boundaryType, this.sourceLang);

        // UUID for the first (or possibly *only*) segment:
        UUID xid = UUID.randomUUID();
        UUID paraID = xid;  // Paragraph identified by its first sentence.

        // If the attribute's value has translatable text, so indicate it in
        // the tskeleton file.
        if (coreTus.length > 0) {
            if (attrName.length() > 0) {
                try {
                    outSkeleton.write("<attr name='" + attrName + "' tag='" 
                        + parentElement + "'>\r\n");
                    outSkeleton.flush();
                }
                catch(IOException e) {
                    System.err.println("Error while writing skeleton file.");
                    System.err.println(e.getMessage());
                }
            }
            else {
                System.err.println("HtmlHandler.writaAttrTuString: AttrName length is zero!");
            }
        }

        // Process each segment. If a segment contains translatable text,
        // create a TU for it (in the XLIFF) and write a "tu" line to the temporary
        // skeleton. If the segment doesn't contain translatable text, write the
        // segment to the format file, and write a "format" line in the temporary
        // skeleton that references the segment in the format file.
        for (int i = 0; i < coreTus.length; i++) {
            if (coreTus[i].translatable()) { 
                String coreSeg = coreTus[i].getSegmentStr();

                // See if this segment has a translatable successor. It it does,
                // then it is mergeable.
                boolean hasSuccessor = false;
                if (i < (coreTus.length-1)) {  // If this *isn't* the last segment ...
                    FIND_SUCCESSOR:
                    for (int j = (i + 1); j < coreTus.length; j++) {
                        if (coreTus[j].translatable()) {
                            hasSuccessor = true;
                            break FIND_SUCCESSOR;
                        }
                    }
                }
                
//                UUID xid = 
                writeTuString(coreSeg, hasSuccessor, xid, paraID);

//                if (xid != null) {  // It *should* never be null!!
                // Write to the intermediate skeleton file
                try {
                    // Write placeholder in format file. (No need for anything in skel)
                    outFormat.write("<lt:tu id='" + xid.toString() + "'/>\"");
                    outFormat.flush();

                    outSkeleton.write("<tu id='" + xid.toString() + "' length='" 
                        + (coreSeg.length() - (TuPreener.CORE_START_MRK + TuPreener.CORE_END_MRK).length()) 
                        + "' " + "no='" + (i+1) + "' of='" + coreTus.length + "'>\r\n");
                    outSkeleton.flush();
                }
                catch(IOException e) {
                    System.err.println("Error while writing skeleton file.");
                    System.err.println(e.getMessage());
                }
//                }
            }
            
            // Attribute segments are always translatable (aren't they?)
            
            xid = UUID.randomUUID(); // UUID for the next segment (or throw away).
        }
        
        return true;
    }
    
    /**
     * Passed a string, create a TU entry in the XLIFF, returning the UUID of
     * the new TU just written. This doesn't write anything to the temporary
     * skeleton.
     * @param tuStr A string to write to the <source> element of a new TU
     * @param mergeable If true, this TU can be merged with its successor; if
     *                  false, it can't
     * @param tuID The UUID for this trans-unit
     * @param paraID The UUID of the paragraph that contains this trans-unig.
     */
    private void writeTuString(String tuStr, boolean mergeable,
            UUID tuID, UUID paraID) {
        if (tuID == null) {
            System.err.println("HtmlHandler.writeTuString: tuID is null!");
        }
        if (paraID == null) {
            System.err.println("HtmlHandler.writeTuString: paraID is null!");
        }
        
        // Get the core(ed) Tu:
        String coreTu = TuPreener.markCoreTu(tuStr); // Complete TU now has core marks
//        UUID curTuId = null;
        
        if (coreTu.length() > 0) {
//            curTuId = UUID.randomUUID();    // Get a TU ID for this one
            try {
                // Open the trans-unit element (No offset attribute in the following (yet?))
                outXliff.write(indent('0') 
                    + "<trans-unit id='" + tuID.toString() + "'"
                    + " lt:paraID='" + paraID.toString() + "'");
                
                if (mergeable) {
                    outXliff.write(" lt:mergeable='true'");
                }
                
                outXliff.write(">\r\n");
                
                // Open the source tag
                outXliff.write(indent('+') + "<source xml:lang='" + sourceLang.toString() + "'>");
                
                // Write the actual text of the TU:
                outXliff.write(coreTu);
                
                // Close the source element
                outXliff.write("</source>\r\n");
                
                // Close the trans-unit element
                outXliff.write(indent('-') + "</trans-unit>\r\n");
                
                outXliff.flush();  // For debugging ... at least.
            }
            catch(IOException e) {
                System.err.println("Error writing translation-unit characters to the XLIFF file.");
                System.err.println(e.getMessage());
            }
        }

        return /* curTuId */;
    }
}
