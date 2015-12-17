/*
 * XMLCandidateTuXPathGenerator.java
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

//import file2xliff4j.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.xml.sax.ext.LexicalHandler;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.lang.*;
import java.nio.charset.*;

/**
 * The XMLCandidateTuXPathGenerator class encapsulates and generates the XPaths 
 * of XML elements/attributes that likely contain translatable text in an XML 
 * file.
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XMLCandidateTuXPathGenerator extends DefaultHandler implements LexicalHandler {
    
    /** Number of XPaths found.
     */
    private long numXPaths = 0;
    
    /** Locator to keep track of where each startElement occurs */
    private Locator locator;

    /** Stack to hold element entries */ 
    private Stack<ElementStackEntry> elementStack;
    
    /** 
     * Hash set to keep track of TUs. Each entry is an instance of class
     * XMLTuXPath.  This can be returned to the caller, if requested. 
     */
    private HashSet<XMLTuXPath> segmentXPaths = null;  // Not necessary ... but explicit ...
    
    /** 
     * Alternate mechanism for returning the XPaths to the caller--as an
     * XML file. */
    private BufferedWriter outWriter;
//    private OutputStreamWriter outWriter;
    
    /**
     * This status variable is how the getCandidateTuXPaths method that writes
     * to a stream knows whether the processing was successful.
     */
    private int streamStatus = 0; 
    
    /**
     * A bag to hold XPaths
     */
    private XPathBag xPathBag;
   
    /**
     * White-space from TuPreener (file2xliff4j) 
     */
    // u00a0 is a non-breaking space
    // u00b7 is a middle dot
    // u2002 is an en space
    // u2003 is an em space
    // u2022 is a bullet character
    // u2023 is a triangular bullet
    // u2043 is a hyphen bullet
    // u204c is a black leftwards bullet
    // u204d is a black rightwards bullet
    // u2219 is a bullet operator
    // u25c9 is a tainome (Japanese "fish eye" bullet)
    // u25d8 is an inverse bullet
    // u25e6 is a white bullet
    // u005f spacing underscore
    // u2381 continuous underline symbol
    // u2382 discontinuous underline symbol
    // u0332 underline
    // u0333 double underline
    // u2017 double underscore, spacing
    // uf3ff Unicode byte-order mark
    public static final String WHITE_SPACE 
            = "\\s\\u0009\\u0020\\u002d\\u00a0\\u00b7\\u2002\\u2003\\u2022\\u2023"
                + "\\u2043\\u204c\\u204d\\u2219\\u25c9\\u25d8\\u25e6\\u005f"
                + "\\u2381\\u2382\\u0332\\u0333\\u2017\\ufeff";
    
    public static final String CDATA_PAT_STR
            = "<\\[CDATA\\[.*?]]>";
    
    /** Matcher for identifying ignorable white space. (Note: We will use the
     *  matches() method, so begin/end line anchors aren't necessary.) 
     *  Add CDATA sections to the set of ignorable white space.
     */
    private final Matcher ignM = 
//            Pattern.compile("[" + WHITE_SPACE + "]*",Pattern.DOTALL).matcher("");
            Pattern.compile("(?:[" + WHITE_SPACE + "]|" + CDATA_PAT_STR + ")*",Pattern.DOTALL).matcher("");

    /** Ignorable attribute names */
    public static final String IGNORABLE_ATTR_NAMES
        = "xml:space|xml:lang|xml:id|xmlns(?::.+)?";
    
    /** Matcher for the above. We will use the matches method, so ^ and $ anchors
     * not necessary. */
    private final Matcher ignAttrNameM =
            Pattern.compile(IGNORABLE_ATTR_NAMES).matcher("");
    
    /** Ignorable attribute values: 
     * <ol>
     * <li>boolean values (yes, not, true, false)
     * <li>http/https URLs
     * <li>Values beginning ..
     * <li>Values beginning with #
     * <li>Values consisting of exactly 6 hex digits (colors)
     * <li>Values consisting of numbers, punctuation, and white space
     * </ul>
     */
    public static final String IGNORABLE_ATTR_VALUES
        = "yes|no|true|false|https?://.*||\\.\\..*|#.*|[a-fA-F0-9]{6}|[0-9\\pP" + WHITE_SPACE + "]*";
    
    /** Matcher for the above. Ditto about maches() instead of find() */
    private final Matcher ignAttrValueM =
            Pattern.compile(IGNORABLE_ATTR_VALUES).matcher("");
    
    /** Matcher to match two+ "space" separated words: */
    private final Matcher twoWordsM =
            Pattern.compile(".*\\S+\\s+\\S+.*",Pattern.DOTALL).matcher("");
    
    /** Matcher for a string consisting entirely of characters in the range
     *  u0020-u007e (7-bit ascii range)
     */
    private final Matcher allAsciiM =
            Pattern.compile("[\\u0020-\\u007f]*",Pattern.DOTALL).matcher("");

    /**
     * Variable to trace when we're in CDATA
     */
    private boolean inCdata = false;
    
    /**
     * Constructor for the XMLTuXPath class
     */
    public XMLCandidateTuXPathGenerator() {
    }
    
    /**
     * Passed an input stream, return an XML output stream that includes an
     * element for each candidate translation unit XPath. The format is
     * <pre>
     *  &lt;candidates&gt;
     *    &lt;xpath row='rownum' col='colnum' attr='optionalattrname'&gt;XPath1&lt;/xpath&gt;
     *    &lt;xpath row='rownum' col='colnum' attr='optionalattrname'&gt;XPath2&lt;/xpath&gt;
     *    &lt;xpath row='rownum' col='colnum' attr='optionalattrname'&gt;XPath3&lt;/xpath&gt;
     *       ...
     *    &lt;xpath row='rownum' col='colnum' attr='optionalattrname'&gt;XPathn&lt;/xpath&gt;
     *  &lt;/candidates&gt;
     * </pre>
     * @param xmlIn An input stream from which to read XML.
     * @param xmlOut Output stream to which to write the XML described above.
     * @return The number of XPaths in the returned output stream, or a negative
     *         value if an error occurs. If no candidate XPaths are generated
     *         (and no detected errors occur), the return value is 0 (zero).
     */
    public long getCandidateTuXPaths(InputStream xmlIn, OutputStream xmlOut) {

        if (xmlIn == null) {
            System.err.println("Cannot read from input stream xmlIn.");
            numXPaths = -1;
        }
        
        else if (xmlOut == null) {
            System.err.println("Cannot write to output stream xmlOut.");
            numXPaths = -2;
        }
        
        else {
            // Setting outWriter (a member field) is what makes getCandidateTuXPaths
            // write to xmlOut, rather than creating a HashSet of XMLTuXPath ...
            outWriter = new BufferedWriter(new OutputStreamWriter(xmlOut, Charset.forName("UTF-8")));
            
            if (outWriter == null) {
                numXPaths = -3;
            }
            else {
                // Call the original method, which will set streamStatus to
                // indicate errors.
                this.getCandidateTuXPaths(xmlIn);
            }
        }
        
        return numXPaths;
    }
    
    /**
     * Passed an input stream, read the stream and determine the XPaths of
     * elements/attributes that likely contain translatable text.
     * 
     * @param xmlIn An input stream from which to read XML.
     * @return A hash set of XMLTuXPath candidates.
     */    
    public HashSet<XMLTuXPath> getCandidateTuXPaths(InputStream xmlIn) {

        if (xmlIn == null) {
            System.err.println("Cannot read from input stream xmlIn.");
            return null;
        }
        
        // Create new datastructures (in case this object is called multiple
        // times for different files).
        if (outWriter == null) {
            // If we're not writing XML to an output stream, then create an
            // XPath hash set.
//            segmentXPaths = new HashSet<XMLTuXPath>(3000);        
            segmentXPaths = new HashSet<XMLTuXPath>();        
        }
        elementStack = new Stack<ElementStackEntry>();    
        xPathBag = new XPathBag();
        
        try {
            // Let's parse with the an XML Reader
            XMLReader parser = XMLReaderFactory.createXMLReader();
            
            parser.setContentHandler(this);

            // Make us aware of when we're in CDATA ...
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", this);
            
            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            
            InputSource XMLIn = new InputSource(xmlIn);
            parser.parse(XMLIn);
            parser = null;
            
        }
        catch(SAXException e) {
            System.err.println("XML parser error.");
            System.err.println(e.getMessage());
            return null;
        }
        catch(IOException e) {
            System.err.println("I/O error reading XML input: " + e.getMessage());
        }

        // Try to encourage garbage collection by removing references to memory ...
        this.elementStack.setSize(0); this.elementStack = null;
        this.outWriter = null;   // Wrapper for possible output stream.
        this.xPathBag.clear(); this.xPathBag = null;
        
        return segmentXPaths;    // Could be null, if we are writing to output stream.
    }

    /**
     * Method called by the SAX parser before it calls startDocument. (The locator
     * provides access to the line and column number where a start tag occurs.)
     * @param locator A reference to a document locator
     */
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
    
    /**
     * Method called by SAX parser (actually HotSAX in this case) at the beginning of
     * document parsing.
     * @throws org.xml.sax.SAXException if error occurs.
     */
    public void startDocument() throws SAXException {

        // If we are writing to an output stream, write the initial lines of
        // the output XML file.
        if (this.outWriter != null) {
            // Write the beginning of the output XML file.
            try {
                outWriter.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n");
                outWriter.write("<candidates>\r\n");
                outWriter.flush();

            }
            catch(IOException e) {
                System.err.println("Error writing to output XML file.");
                System.err.println(e.getMessage());
                this.streamStatus = 4;
            }
        }
    }
    
    /**
     * Method called whenever a start element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if 
     *        Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty 
     *        string if qualified names are not available
     * @param atts The specified or defaulted attributes.
     * @throws org.xml.sax.SAXException if error occurs.
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {
        
        String elementName = qualifiedName;
        if ((elementName == null) || (elementName.length() == 0)) {
            elementName = localName;
        }

        // If we're in a CDATA section, return immediately.
         if (inCdata) {
            return;
        }

        // Get the full XPath of this element (without a final subscript
        // following this element).
        String xPath = getXPathPrefix() + "/" + elementName;
        
        // Add this XPath (without the trailing square-bracket-delimited
        // subscript) to the XPath bag.
        xPathBag.add(xPath);

        this.elementStack.push(new ElementStackEntry(elementName,
                                   locator.getLineNumber(), 
                                   locator.getColumnNumber()));
        
        int numAtts = atts.getLength();  // How many attributes?
        
        // If there are some, examine them.
        if (numAtts > 0) {

            // Get the cardinality of my XPath (in case I need it for a subscript
            // in creating a attribute's full XPath)
            int myXPathCardinality = xPathBag.cardinality(xPath);
            
            // Check for potentially translatable attributes
            for (int i = 0; i < atts.getLength(); i++) {
                String attrName = atts.getQName(i);     // Attr name
                ignAttrNameM.reset(attrName);           // Attr name matcher

                String attrVal = atts.getValue(i);      // Attr value
                ignAttrValueM.reset(attrVal);           // Attr value matcher

                // Do either of the "ignore lists match?"
                if (ignAttrNameM.matches() || ignAttrValueM.matches()) {
                    // If so, skip this attribute
                    continue;
                }

//                // Try this heuristic: If the value has fewer than two words and
//                // all the characters are in the 7-bit ASCII range, skip it.
//                twoWordsM.reset(attrVal);
//                allAsciiM.reset(attrVal);
//                if ((!twoWordsM.matches()) && allAsciiM.matches()) {
//                    continue;
//                }

                // We're here (in the loop), so let's assume that this attribute's
                // value probably has translatable text of three or more words.?
//                segmentXPaths.add(new XMLTuXPath(xPath + "[" + myXPathCardinality +"]/@" + attrName, 
//                    locator.getLineNumber(), locator.getColumnNumber(), attrName));
                this.addXPath(xPath + "[" + myXPathCardinality +"]/@" + attrName, 
                    locator.getLineNumber(), locator.getColumnNumber(), attrName);
                
//                System.err.println("Currently " + segmentXPaths.size() + " segment XPaths.");
                
            }
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

        // If we're in a CDATA section, return immediately.
        if (inCdata) {
            return;
        }

        // See if the string doesn't consist exclusively of ignorable characters
        ignM.reset(theString);
        
        // If the string doesn't consist exclusively of ignorable characters
        // (such as bullets, dashes, etc.--not to mention spaces, etc.), then
        // Mark this element as having meaningful characters.
        // 4/27/2007 WLW: Add CDATA sequences to the set of ignorable characters
        // (I.e., if the string consists of CDATA Sections, bullets, dashes (etc.)--
        // only--then it is ignorable.
        if (!ignM.matches()) {
            if (this.elementStack.size() > 0) {
                this.elementStack.peek().setHasMeaningfulChars();
            }
            else {
                System.err.println("Characters found outside root element! (Continuing ...)");
            }
        }
    }

    /**
     * Method called whenever an end element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if 
     *                  Namespace processing is not being performed. 
     * @param qualifiedName The qualified name (with prefix), or the empty 
     *                  string if qualified names are not available.
     */
    public void endElement(String namespaceURI, 
			   String localName,
			   String qualifiedName) throws SAXException {

        // If we're in a CDATA section, return immediately.
        if (inCdata) {
            return;
        }

        String xPath = getXPathPrefix();
       
        ElementStackEntry entry = elementStack.pop();
        String elementString = entry.getName();
        
        // Compare the element name from the stack to the local/qualified name(s)
        if (!(elementString.equals(localName) || elementString.equals(qualifiedName))) {
            System.err.println("Element " + elementString + " appears out of order.");
        }
        
        // If this entry isn't ignorable (because it contains no meaningful
        // characters), add it to the map (with its line/column coordinates) of
        // delimiters.
        if (!entry.isIgnorable()) {
//            segmentXPaths.add(new XMLTuXPath(xPath, entry.getLineNumber(), 
//                entry.getColumnNumber(), null));
            this.addXPath(xPath, entry.getLineNumber(), entry.getColumnNumber(), null);
            
        }
    }
    
    /**
     * Method called by SAX parser at the end of document parsing.
     * @throws org.xml.sax.SAXException if error occurs.
     */
    public void endDocument() throws SAXException {

        // If we are writing to an output stream, write the initial lines of
        // the output XML file.
        if (this.outWriter != null) {
            // Close the root element.
            try {
                outWriter.write("</candidates>\r\n");
                outWriter.flush();

            }
            catch(IOException e) {
                System.err.println("Error writing to output XML file.");
                System.err.println(e.getMessage());
                this.streamStatus = 6;
            }
        }
    }
    
    /************************************************************************
     * L e x i c a l H a n d l e r   m e t h o d s
     ***********************************************************************/
    /** Method defined by the LexicalHandler interface that we don't care about. 
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startEntity(String name) throws SAXException {}
    
    /** Method defined by the LexicalHandler interface that we don't care about. 
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endEntity(String name) throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. 
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startDTD(String name, String publicId, String systemId)
        throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. 
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endDTD() throws SAXException {}

    /** 
     * Method called by the SAX parser when it encounters the start of a CDATA
     * section. We will (for now, at least) treat CDATA the same way as 
     * comments (ignore them, skipping past them)
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startCDATA() throws SAXException { 
        inCdata = true;
    }

    /** 
     * Method called by the SAX parser when it encounters the end of a CDATA
     * section. We will (for now, at least) treat CDATA the same way as 
     * comments (ignore them, skipping past them).
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endCDATA() throws SAXException {
        inCdata = false;
    }

    /** Method defined by the LexicalHandler interface that we don't care about. 
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void comment (char[] text, int start, int length) throws SAXException {}
    
    /**
     * Passed XPath information, add the path to the appropriate output medium--
     * either to the segmentXPaths hash set or write it to the output stream--
     * whichever is non-null.
     * <p>If addXPath encounters problems it will set the streamStatus variable
     * to a non-zero value.
     * @param fullPath The full XPath to add
     * @param row The number of the row (in the original XML file) where this
     *        XPath occurs.
     * @param col The number of the column where the XPath occurs
     * @param attrName The name of the attribute (at the end of the XPath)--if
     *        there *is* one. Otherwise null.
     */
    private void addXPath(String fullPath, int row, int col, String attrName) {
        if (this.outWriter != null) {
            try {
                outWriter.write(" <xpath row='" + row + "' col='" + col + "'");
                if (attrName != null) {
                    outWriter.write(" attr='" + attrName + "'");
                }
                outWriter.write(">" + fullPath + "</xpath>\r\n");
                outWriter.flush();
                numXPaths++;
            }
            catch (IOException e) {
                System.err.println("Unable to write XPath to output stream: " + e.getMessage());
                streamStatus = 5;
            }
        }

        else {
            segmentXPaths.add(new XMLTuXPath(fullPath, row, col, attrName));            
            numXPaths++;
        }
    }
    
    /**
     * Return the XPath of the element at the top of the element stack.
     * (The XPath does not include a bracketed [n] subscript.)
     * @return String representing the XPath of the current element
     */ 
    private String getXPathPrefix() {
        String xPath = "";
        
        if (this.elementStack.size() < 1) {
            return xPath;                      // Nothing to return.
        }
        
        for (int i = 0; i < elementStack.size(); i++) {
            String elementName = elementStack.get(i).getName();
            xPath += ("/" + elementName + "[" + 
                    xPathBag.cardinality(xPath + "/" + elementName) + "]");
        }
        
        return xPath;
    }

    
    /**
     * A class to represent a bag of XPaths. 
     */
    class XPathBag {
        private HashMap<String,Integer> theBag = new HashMap<String,Integer>(20000);
        
        /** Create a new XPathBag */
        public XPathBag() {}
        
        /**
         * Add a new XPath to the bag
         * @param xPath The XPath to add to the bag.
         */
        public void add(String xPath) {
            if ((xPath == null) || (xPath.trim().length() == 0)) {
                System.err.println("Tried to add empty XPath to the XPathBag.");
                return;
            }
            
            // If this is a dupe, just increment its count
            if (theBag.containsKey(xPath)) {
                theBag.put(xPath, Integer.valueOf(theBag.get(xPath).intValue() + 1));
            }
            else {
                // Otherwise, add it as a new singleton XPath
                theBag.put(xPath, Integer.valueOf(1));
            }
            
//            System.err.println("Currently " + theBag.size() + " bag entries.");
        }
        
        /**
         * How many occurrences of the specified XPath are in the bag?
         * @param xPath The XPath whose cardinality is being tested
         * @return The number of occurrences of the spceified XPath in
         *         the bag.
         */
        public int cardinality(String xPath) {
            Integer num = theBag.get(xPath);
            if (num == null) {
                return 0;
            }
            else {
                return num.intValue();
            }
        }
        
        /**
         * Remove all the mappings from the bag.
         */
        public void clear() {
            this.theBag.clear();
        }
    }
    
    /**
     * The ElementStackEntry class represents the name of and location (in a 
     * file) of an element. Instances of this class are stored in an  element stack.
     */
    class ElementStackEntry {
        private String name;    // The element's name
        private int line;       // The line number where it occurs
        private int column;     // Its column number
        private boolean hasMeaningfulChars; // Chars that aren't preened out by TU Preeners
        
        /**
         * The constructor takes three arguments
         * @param name The element name
         * @param line The number of the line where it occurs
         * @param column The number of the column where it occurs
         * @param cardinality The number of XPath instances that
         *        are the same as me (so far).
         */
        public ElementStackEntry(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }
        
        /** 
         * Return the element's column number.
         * @return The number of the column where the element occurs.
         */
        public int getColumnNumber() {
            return this.column;
        }

        /** 
         * Return the element's line number.
         * @return The number of the line where the element occurs.
         */
        public int getLineNumber() {
            return this.line;
        }

        /** 
         * Return the element's name.
         * @return The element's name
         */
        public String getName() {
            return this.name;
        }
        
        /**
         * Tell whether this element's text can be ignored (because the
         * text is made up exclusively of whitespace, bullets, underscores,
         * etc.)
         * @return true if this element is ignorable, else false
         */
        public boolean isIgnorable() {
            return !this.hasMeaningfulChars;
        }
        
        /**
         * Set this entry as having meaningful characters
         */
        public void setHasMeaningfulChars() {
            this.hasMeaningfulChars = true;
        }
    }
}
