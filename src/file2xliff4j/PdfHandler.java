/*
 * PdfHandler.java
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
 * Class PdfHandler uses HotSAX to read and parse HTML documents
 * as if they were well-formed XML documents using a SAX-like API.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PdfHandler extends DefaultHandler {

    // Initial set of tags that can cause a <trans-unit> break. (Initialized in
    // our private initHashes() method.)
    // We will populate this one later.
    private HashMap<String,String> formatCtype = new HashMap<String,String>();
    
    // Keep track of the longest line.
    private int longestLineLen = 0;
    
    // This one we will populate only if actually used:
    private Stack<Integer> ridStack = new Stack<Integer>();
    
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

    // Trackers for characters() locator return values
    private int prevCharLine = 1;           // Line of previous characters() call
    
    private int curCharLine = 1;            // Line of current characters() call
  
    private int curTagNum = 1;              // Fos skeleton
    
    private String tuAttrName = "";         // If tu is an attr value (e.g. alt ...)
                                            //   this holds its name.
    
    private boolean preservingCRLF = false; // Not currently preserving carriage returns/
                                            // line feeds.
    
    /** For testing only */
    public PdfHandler() {}

    /**
     * This constructor sets up the PdfHandler to be notified as
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
    public PdfHandler(Set<String> tuTags, OutputStreamWriter outXliff, 
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
                + "' datatype='x-pdf'>\r\n");
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

	// Use qualified name if local name is of zero length
	if ("".equals(elementName)) {
	    elementName = qualifiedName;
	}

        /****************************************************************
         * Do we need to break a TU?
         ****************************************************************/
	if (tuDelims.contains(elementName.toLowerCase())) { 
            writeCandidateTu();
	}

        // Within pre elements, we preserve newlines.
        if (elementName.equalsIgnoreCase("pre")) {
            preservingCRLF = true;
        }

        // Write the start element to the intermediate skeleton file, ignoring 
        // the tags that become bx/ex/x tags.
        if (!formatCtype.containsKey(elementName.toLowerCase())) {
            try {
                outSkeleton.write("<" + elementName + " seq='" + curTagNum++ + "'");
                if (elementName.equals("TEXT")) {
                    outSkeleton.write(" pagenum='"); 
                    if (atts != null) {
                        String val = atts.getValue("pagenum"); 
                        if (val != null) {
                            outSkeleton.write(val);
                        }
                    }
                    outSkeleton.write("'"); 
                }
                outSkeleton.write(">\r\n");
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
        
        /****************************************************************
         * Check for the formatting tags that we preserve. If this is one
         * of them, output a <bx> tag (er, element)
         ****************************************************************/
        String fmtType = formatCtype.get(elementName.toLowerCase());
        if (fmtType != null) { // This is <i>, <a>, <b> or one of those tags
            if (elementName.equalsIgnoreCase("img") 
                || elementName.equalsIgnoreCase("param")
                || elementName.equalsIgnoreCase("spacecount")) {
                
                // img, br and param tags become <x/> tags
                candidateTu.append("<x id='" + bxExId + "' ");
            }
            else {
                candidateTu.append("<bx id='" + bxExId + "' ");
            }
            
            
            if (fmtType.length() > 0) {
                candidateTu.append("ctype='" + fmtType);
                
                // For spacecount, add indication of the number of spaces
                if (elementName.equalsIgnoreCase("spacecount")) {
                    candidateTu.append("-" + atts.getValue("space"));
                }

                candidateTu.append("'");
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
                                outFormat.write(atts.getValue(i) + "\"");
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
                //   input (of type button, reset, submit & text--not hidden, however)
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
                                || (aName.toLowerCase().equals("alt") 
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
                    tuAttrName = "value";     // For use in writeCandidateTu
                    writeAttrTuString(tuAttrName, elementName, 
                                        TuStrings.escapeTuString(valueValue));
                }
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
        // The contents of the title tag are ugly. Let's just delete that
        // tag's contenst.
        if (elementName.equalsIgnoreCase("title")) {
            candidateTu.setLength(0);
        }
        else if (tuDelims.contains(elementName.toLowerCase())) { 
            writeCandidateTu();
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
                    || elementName.equalsIgnoreCase("param")
                    || elementName.equalsIgnoreCase("spacecount"))) {
            
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

        
        // If this isn't a blank line or in a tag (like <script> or <style>) that
        // we ignore, add it to the candidate TU buffer.
        if (! ((theString.matches("^\\s*$") && (candidateTu.length() == 0)) 
            || ignoringText)) {

            // Escape the string so that we don't upset the SAX parser
            theString = TuStrings.escapeTuString(theString);
            
            // If we aren't inside a <pre> tag, change \n and \r to space
            if (! preservingCRLF) {
                theString = theString.replaceAll("\\s+", " ");
            }
            
            candidateTu.append(theString);  // Now append the string
        }
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
     * Return the number of characters in the longest line appearing in the 
     * document. (This method should be called <i>after</i> processing of the
     * document has completed.)
     * @return The number of characters in the longest line in the document.
     */
    /* package */ int getLongestLineLength() {
        return this.longestLineLen;
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
        formatCtype.put("abbr",    "x-pdf-abbr");     
        formatCtype.put("acronym", "x-pdf-acronym");     
        formatCtype.put("applet",  "x-pdf-applet");
        formatCtype.put("b",       "bold");
        formatCtype.put("big",     "x-pdf-big");
        formatCtype.put("blink",   "x-pdf-blink");
        formatCtype.put("cite",    "x-pdf-cite");
        formatCtype.put("code",    "x-pdf-code");
        formatCtype.put("del",     "x-pdf-del");     
        formatCtype.put("em",      "italic");     // Most browsers display em[phasized] as italic
        formatCtype.put("embed",   "x-pdf-embed");
        formatCtype.put("font",    "x-pdf-font");
        formatCtype.put("i",       "italic");     
        formatCtype.put("img",     "image");           // becomes <x/> 
        formatCtype.put("ins",     "x-pdf-ins");     
        formatCtype.put("kbd",     "x-pdf-kbd");
        formatCtype.put("nobr",    "x-pdf-nobr");
        formatCtype.put("object",  "x-pdf-object");
        formatCtype.put("param",   "x-pdf-param");    // becomes <x/> (child of applet/object)
        formatCtype.put("s",       "x-pdf-s");     
        formatCtype.put("samp",    "x-pdf-samp");     
        formatCtype.put("small",   "x-pdf-small");     
        formatCtype.put("spacecount", "x-pdf-spacecount");     
        formatCtype.put("span",    "x-pdf-span");     
        formatCtype.put("strike",  "x-pdf-strike");     
        formatCtype.put("strong",  "bold");   // Most browsers display strong as bold
        formatCtype.put("sub",     "x-pdf-sub"); 
        formatCtype.put("sup",     "x-pdf-sup");
        //formatCtype.put("sub", "subscript"); 
        //formatCtype.put("sup", "superscript");
        formatCtype.put("tt",      "x-pdf-tt");     
        formatCtype.put("u",       "underline"); 
        formatCtype.put("var",     "x-pdf-var");     
        
        
        
        // These tags probably include text that isn't meaningful for translation:
        nonTuText.add("style");
        nonTuText.add("script");
        nonTuText.add("comment");      // Internet Exploiter and WebTV only--non-displayed

        // These attributes might have text that we want to translate:
        tuAttrs.add("alt");      // Alternate text if img etc. can't be displayed
        tuAttrs.add("title");    // For "balloon" hints/"tool tips"
        tuAttrs.add("summary");  // Used in the table tag.
        
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
        
        // Get the core segments:
        SegmentInfo[] coreTus = TuPreener.getCoreSegments(candidateTu.toString(), 
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
                
                writeTuString(coreSeg, hasSuccessor, curTuID, paraID);


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
        int curLineLength = 0;
        if ((candidateTuStr == null) || ((curLineLength = candidateTuStr.trim().length()) == 0)) {
            return false;
        }
        
        if (curLineLength > this.longestLineLen) {
            this.longestLineLen = curLineLength;
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
                System.err.println("PdfHandler.writaAttrTuString: AttrName length is zero!");
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
                
                writeTuString(coreSeg, hasSuccessor, xid, paraID);

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
            }
            
            // Attribute segments are always translatable (aren't they?)
            
            xid = UUID.randomUUID(); // UUID for the next segment (or throw away).
        }
        
        return true;
    }
    
    private Matcher tagMatcher = Pattern.compile("<[^>]+>",Pattern.DOTALL).matcher("");
    
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
            System.err.println("PdfHandler.writeTuString: tuID is null!");
        }
        if (paraID == null) {
            System.err.println("PdfHandler.writeTuString: paraID is null!");
        }
        
        // Get the core(ed) Tu:
        String coreTu = TuPreener.markCoreTu(tuStr); // Complete TU now has core marks
        
        if (coreTu.length() > 0) {
            String coreTextOnly = TuPreener.getCoreText(coreTu); // Remove what's outside mrks
            tagMatcher.reset(coreTextOnly);
            int coreTextLen = tagMatcher.replaceAll("").length();
            if (coreTextLen > this.longestLineLen) {
                this.longestLineLen = coreTextLen;
            }
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
