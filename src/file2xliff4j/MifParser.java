/*
 * MifParser.java
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

//import com.thoughtworks.xstream.converters.collections.CharArrayConverter;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.nio.*;
import java.util.regex.*;

/** 
 * Our implementation of a SAX/like parser for Maker Interchange Format (MIF), a
 * representation of the content of Adobe FrameMaker files.
 * <p>Although MIF isn't XML, this parser handles it as if it were. It
 * <ol>
 * <li>Calls startElement for tokens immediately following an open angle
 *     bracket (&lt;)</li>
 * <li>Calls endElement when the corresponding closing angle bracket (&gt;) is
 *     encountered</li>
 * <li>Calls characters when the characters of a quoted character stream are 
 *     found in the input</li>
 * <li>Calls startDocument and endDocument at the beginning and end of the
 *     input
 * </ol>
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class MifParser implements org.xml.sax.XMLReader {
//public class MifParser extends AbstractXMLReader {
    private EntityResolver entityResolver;
    private DTDHandler dtdHandler;
    private ContentHandler contentHandler; // Who I call back to
    private ErrorHandler errorHandler;
    
    // Keep track of where we are in the input
    private Stack<String> tagStack = new Stack<String>();
    private ArrayList<String> curTagArgs = new ArrayList<String>();

    private MifBufferedReader bReader;   // Out own partial implementation

    // A buffer to accumulate characters in ...
    private StringBuilder charactersBuf = new StringBuilder();
    
    boolean insideQuotedString = false;  // Are we between ` and ' ?
    boolean insideParagraph = false;     // Not yet ...
    
    // Current position in the charBuff.
    private int charPos;
    
    // An empty attribute list to call back with.
    private static final Attributes EMPTY_ATTR = new AttributesImpl();
    
    // Is a non-single-byte encoding used?
    private String encodingStr = "";
    private Charset encoding;

    /**
     * This is the class that actually parses the MIF file. (It is the
     * only method we care about in this parser.)
     * <p>The caller should have created the InputSource using the
     * correct encoding before calling ... by checking for the
     * MIFEncoding tag (for CJK encodings).
     * @param input Where to read input from.
     * @throws java.io.IOException
     *         If a problem is encountered while reading input
     * @throws org.xml.sax.SAXException
     *         If invalid MIF input is encountered.
     */    
    public void parse(InputSource input) throws IOException, SAXException {

        // Prepare to read the input ... as a BufferedReader.
        if (input.getCharacterStream() != null) {
            bReader = new MifBufferedReader(input.getCharacterStream());
            
            // encoding/encodingStr *might* have been set by a setProperty call.
            if ((encodingStr == null) || encodingStr.length() == 0) {
                encodingStr = input.getEncoding();
                if (encodingStr != null) {
                    encoding = Charset.forName(encodingStr);
                }
                else {
                    encoding = Charset.forName("X-MIF-FRAMEROMAN");
                }
            }
        } 
        else {
            throw new SAXException("Invalid InputSource object");
        }

        // Who are we supposed to tell about what we find in the document?
        contentHandler = getContentHandler();
        if (contentHandler == null) {
            throw new SAXException("Cannot identify the content handler.");
        }
        
        // Tell client that we're starting the document.
        contentHandler.startDocument();

        // read each line of the file until EOF is reached
        // All we care about is paragraphs
        int curChar = -1;
        String curTag = null;
        
        PARSE_LOOP:
        while ((curChar = bReader.read( )) != -1) {
            switch (curChar) {
                case '#':
                    if (! insideQuotedString) {
                        // Skip through the end of line:
                        bReader.readLine();  // Read the rest of the line and discard
                    }
                    else {
                        // Hmmm... Shouldn't have # inside string? (Maybe?)
                        // Let's just add it to the charactersBuf for now.
                        charactersBuf.append("#");
                    }
                    break;
                case '<':
                    if (! insideQuotedString) {
                        // This is the beginning of a tag. Read it, stack it
                        // and report it
                        curTag = readTag();
                        if (curTag.length() > 0) {
                            tagStack.push(curTag);
                            
                            // Handle cases where the curTag has "arguments" (and the
                            // tag name isn't String
                            if (!curTag.equalsIgnoreCase("String")) {
                                String[] tagArgs = readArgs();
                                AttributesImpl atts = new org.xml.sax.helpers.AttributesImpl();
                                for (int i = 0; i < tagArgs.length;  i++) {
                                    atts.addAttribute("", "", Integer.toString(i), "", tagArgs[i]);
                                }
                                contentHandler.startElement("",curTag, curTag, atts);
                            }
                            else {
                                contentHandler.startElement("",curTag, curTag, EMPTY_ATTR);
                            }
                        }
                        else {
                            // No tag! We're done
                            break PARSE_LOOP;
                        }
                    }
                    else {
                        // We *are* inside a string. Let's do like the neko parser
                        // and output an entity. (This isn't how XML parsers work ...)
                        charactersBuf.append("&lt;");  // Hmmm... What *should* we output?
                    }
                    break;   // End of this "case"

                case '\\':    // An escape character
                    processEscape();
                    break; 
                
                case '>':
                    // Not really an end tag in the XML sense ... but every opening
                    // <tag has a matching > ...
                    if (tagStack.size() > 0) {
                        String endTag = tagStack.pop();
                        contentHandler.endElement("",endTag, endTag);
                    }
                    else {
                        System.err.println("Tag stack empty when about to pop tag off stack.");
                        throw new SAXException("Tag stack empty when about to pop tag off stack.");
                    }
                    break;
                case '`':
                    insideQuotedString = true;
                    break;
                case '\'':    // This is the end of a string
                    insideQuotedString = false;
                    if (charactersBuf.length() > 0) {
                        char[] chars = new char[charactersBuf.length()];
                        for (int i = 0; i < charactersBuf.length(); i++) {
                            chars[i] = charactersBuf.charAt(i);
                        }
                        contentHandler.characters(chars,0, chars.length);
                        charactersBuf.setLength(0);
                    }
                    else {
                        // This is an empty string (such as the argument top FTag,
                        // when returning to plain font.
                        char[] chars = new char[0];
                        contentHandler.characters(chars, 0, 0);
                    } 
                    break;
                    
                case '&':     // Make &'s into entities (for XML's benefit
                    if (insideQuotedString) {
                        charactersBuf.append("&amp;"); 
                    }
                    break;

                default:
                    if (insideQuotedString) {
                        charactersBuf.append((char)curChar);
                    }
            }
        }
        
        // We're done. Tell 'em.
        contentHandler.endDocument( );
    }

    /**
     * Process the input stream following the occurrence of an
     * escape character ('\'). At the point this method is called,
     * the backslash has just been read. This method is poised to read
     * the character following the backslash. 
     * <p>As it processes the escape sequence, the method may call the
     * content handler's methods.
     * @throws org.xml.sax.SAXException
     *         If unable to complete the processing of the escape sequence.
     */
    private void processEscape() throws SAXException {
        int nextChar = -1;
        try {
            nextChar = bReader.read();
        }
        catch(IOException e) {
            throw new SAXException("Unexpected IOException in processEscape.");
        }
        switch (nextChar) {
            case -1:
                // We're at the end of stream!
                System.err.println("Unexpected end of stream after escape character.");
                throw new SAXException("Unexpected end of stream.");
            case 't':
                if (insideQuotedString) {
                    // Flush the charactersBuf to the content handler
                    if (charactersBuf.length() > 0) {
                        char[] chars = new char[charactersBuf.length()];
                        for (int i = 0; i < charactersBuf.length(); i++) {
                            chars[i] = charactersBuf.charAt(i);
                        }
                        contentHandler.characters(chars,0, chars.length);
                        charactersBuf.setLength(0);
                    }
                    
                    // Then send some pseudo tags/elements
                    contentHandler.startElement("", "x-mif-tab", "x-mif-tab", EMPTY_ATTR);
                    contentHandler.endElement("", "x-mif-tab", "x-mif-tab");
                    
                    // Leave "insideQuotedString" true
                }
                else if (insideParagraph) {  // But outside of a quoted string
                    // What if we call startElement and endElement for
                    // a tab character of some kind?
                    contentHandler.startElement("", "x-mif-tab", "x-mif-tab", EMPTY_ATTR);
                    contentHandler.endElement("", "x-mif-tab", "x-mif-tab");
                    // Now the program we're calling back to can map this to an
                    // XLIFF x tag if it wants.
                }
                // else ignore this
                break;
            case '>':
                if (insideQuotedString) {
                    // Let's do like the neko HTML parser and output an entity:
                    charactersBuf.append("&gt;");
                }
                // Otherwise is it safe to ignore this?
                break;
            case 'q':
                if (insideQuotedString) {
                    // Let's do like the neko HTML parser and output an entity:
                    charactersBuf.append("&apos;");
                }
                // Otherwise is it safe to ignore this?
                break;
            case 'Q':   // A backquote (left of 1 key on my keyboard).
                if (insideQuotedString) {
                    // Let's do like the neko HTML parser and output a char ref.
                    charactersBuf.append("&#x60;");
                }
                // Otherwise is it safe to ignore this?
                break;
            case '\\':
                if (insideQuotedString) {
                    // How about a character ref?
                    charactersBuf.append("&#x5c;");
                }
                // Otherwise is it safe to ignore this?
                break;
            case 'x':  
                if (insideQuotedString) {
                    charactersBuf.append(getHexCharRef());
//                    if (encoding.length() > 0) {
//                        System.err.println("Hex-escaped strings appear in document with "
//                                + "Asian encoding! Possible misinterpretation.");
//
//                        // ##### ToDo: Try to convert them myself? ... to what?
//                    }
//                    else {
//                        // mifEncoding might incidate Shift_JIS, EUC (Japanese)
//                        // BIG 5, CNS or GB2312-80 (Chinese), or KSC5601 (Korean).
//                        // How about a character ref?
//                        charactersBuf.append(getHexCharRef());
//                    }
                }
                // Otherwise is it safe to ignore this?
                break;
            default:
                System.err.println("Unexpected character encountered following" 
                        + " a backslash.");
                throw new SAXException("Unexpected character encountered following" 
                        + " a backslash.");
        }  // End inner switch
        
    }

    /**
     * Called following an encounter with a tag that isn't String, read
     * its argument(s). It considers the argument list exhausted when it
     * encounters either '>' or '<'.
     * @return An array arguments.
     * @throws org.xml.sax.SAXException 
     *         If unable to successfully read the value
     */
    private String [] readArgs() throws SAXException {
        StringBuilder argToken = new StringBuilder();  
        ArrayList<String> theArgs = new ArrayList<String>();
        int ch = -1;
        boolean done = false;
        boolean pastLeadingSpace = false;
        boolean inString = false;
        
        do {
            try {
                // Skip reading whitespace, if present
                do {
                    ch = bReader.read();
                    if ((ch != ' ') && (!pastLeadingSpace)) {
                        pastLeadingSpace = true;
                    }
                } while (!pastLeadingSpace);
                if ((ch == '\n' || ch == '\r' || ch == '>' || ch == '<' || ch == -1) 
                    && (!inString)) {
                    done = true;
                    // If this was a '>' (and it closed a tag), we need to pop the tag
                    // stack.
                    if (ch == '>' || ch == '<') {
                        // Oops, we read too far. Unread that last character!
                        int ret = bReader.unread();
                        if (ret == -1) {
                            System.err.println("Unable to return '>' to input stream.");
                        }
                    }
                    if (argToken.length() > 0) {
                        theArgs.add(argToken.toString());
                    }
                    break;
                }
                else if (ch == '`') {  // Back quote signals start of string
                    inString = true;
                    argToken.append((char)ch);
                }
                else if (ch == '\'') { // Apostropye signals end of string
                    inString = false;
                    argToken.append((char)ch);
                }
                // If not inside a string, space signals end of arg.
                else if (ch == ' ' && !inString && (argToken.length() > 0)) {
                    theArgs.add(argToken.toString());
                    argToken.setLength(0);
                }
                else {
                    argToken.append((char)ch);
                }
            }
            catch(IOException e) {
                System.err.println("Unexpected error reading argument(s).");
                throw new SAXException("Unexpected error reading argument(s).");
            }
        }
        while (! done);
        
        if (theArgs.size() == 0) {
            return new String[0];
        }
        else {
            String[] retArray = new String[theArgs.size()];
            for (int i = 0; i < theArgs.size(); i++) {
                retArray[i] = theArgs.get(i);
            }
            return retArray;
        }
        
    }
    
    /**
     * Read the next tag and return it. (This method is called after the caller
     * reads a '<')
     * @return A String that contains the next tag.
     * @throws org.xml.sax.SAXException 
     *         If unable to successfully read the next tag.
     */
    private String readTag() throws SAXException {
        StringBuilder tag = new StringBuilder();
        int ch = -1;
        boolean done = false;
        boolean pastLeadingSpace = false;
        
        do {
            try {
                // There can (apparently) be leading space after the opening
                // '<' and before the first non-space character. Skip those
                // spaces (only)
                do {
                    ch = bReader.read();
                    if ((ch != ' ') && (!pastLeadingSpace)) {
                        pastLeadingSpace = true;
                    }
                } while (!pastLeadingSpace);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == -1) {
                    done = true;
                }
                else tag.append((char)ch);
            }
            catch(IOException e) {
                System.err.println("Unexpected error reading the next tag.");
                throw new SAXException("Unexpected error reading the next tag.");
            }
        }
        while (! done);
        
        return tag.toString();
    }

    /**
     * The caller has just read \x from within a string. Read the next
     * three bytes (hex-digit, hex-digit, space), and return a character
     * reference.
     * @return A String that contains the character ref.
     * @throws org.xml.sax.SAXException 
     *         If anything other than two hex-digits and a space are 
     *         encountered.
     */
    private String getHexCharRef() throws SAXException {
        StringBuilder tag = new StringBuilder();
        int ch = -1;
        boolean done = false;
        tag.append("&#x");      // Start of the reference
        int val = 0;            // Numeric value of this hex char
        
        try {
            for (int i = 0; i < 2; i++) {  // Read first two characters
                ch = bReader.read();
                if (((ch >= '0') && (ch <= '9')) || ((ch >= 'a') && (ch <= 'f'))
                        || ((ch >= 'A') && (ch <= 'F'))) {
                    tag.append(Character.toString((char)ch));
                    val = (val * 16) + Character.digit(ch, 16);
                }
                else {
                    System.err.println("Non-hexadecimal digit encountered in hex"
                            + " escape character sequence.");
                    throw new SAXException("Non-hexadecimal digit encountered in hex"
                            + " escape character sequence.");
                }
            }
        
            // Now read the required space
            ch = bReader.read();
            if (ch == ' ') {
                tag.append(";");
            }
            else {
                System.err.println("Non-space encountered following two hex"
                        + " digits in escape sequence.");
                throw new SAXException("Non-space encountered following two hex"
                        + " digits in escape sequence.");
            }
        }
        catch (IOException e) {
            System.err.println("IOException received while getting HEX character reference.");
            throw new SAXException("IOException received while getting HEX character reference.");
        }
        // We're still here... I guess we can return.
        // Before returning, though, if our encoding is MifFrameRoman, don't return
        // a character reference. Instead, convert to Unicode.
        CharBuffer mifChar = null;
        if (encoding != null) {     // Probably Frame Roman?
            CharsetDecoder decoder = encoding.newDecoder();
            byte theByte[] = { (byte)val };
            ByteBuffer mifByte = ByteBuffer.wrap(theByte);
            try {
                mifChar = decoder.decode(mifByte);
            }
            catch(CharacterCodingException e) {
                System.err.println("Error decoding " + tag);
            }
        }

        if (mifChar != null) {
            return mifChar.toString();
        }
        
        return tag.toString();
    }
    
    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    } 
    
    public boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotSupportedException("The MifParser does not meaningfully"
                + " support the getFeature method.");
    }
 
    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotSupportedException("The MifParser does not meaningfully"
                + " support the setFeature method.");
    }
 
    public Object getProperty(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        throw new SAXNotSupportedException("The MifParser does not meaningfully"
                + " support the getProperty method.");
    }
 
    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        
        if (name != null && name.equals("http://lingotek.com/mif/properties/encoding")) {
            if (value != null) {
                encoding = (Charset)value;
                encodingStr = encoding.toString();
            }
        }
        else {
            throw new SAXNotSupportedException("The MifParser does not meaningfully"
                + " support the setProperty method.");
        }
    }

    /** Some token implementations that we don't need ... yet :-) */
    public void setEntityResolver(EntityResolver entityResolver) { }
    public EntityResolver getEntityResolver( ) { return entityResolver; }
    public void setDTDHandler(DTDHandler dtdHandler) { }
    public DTDHandler getDTDHandler( ) { return dtdHandler; }
    public void setContentHandler(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }
 
    public ContentHandler getContentHandler( ) { return contentHandler; }
    public void setErrorHandler(ErrorHandler errorHandler) { }
    public ErrorHandler getErrorHandler( ) { return errorHandler; }

    /**
     * Our own private Buffered Reader that skips lines defining
     * macros, that are all comments, etc.
     * 6/5/7 WLW: Skip lines that are parts of facets.
     */
    private class MifBufferedReader {
        private BufferedReader input;
        private String curLine = "";         // Where we're reading from now ...
        private int nextPos = 0;
        private HashMap<String,String> macs = new HashMap<String,String>();
        private Pattern macPat;
        private Matcher macMatcher;
        private Pattern hashPat;
        private Matcher hashMatcher;
        
        // Facets are sections of MIF files that represent graphics, encapsulated
        // postscript files, etc. The beginning of a facet is signalled by a
        // line that starts with an equals sign, immediately followed by
        // something like:
        //     TIFF
        //     EPSI (Encapsulated PostScript)
        //     OLE  (OLE code)
        //     OLE2
        //     QuickDraw
        //     PICT
        //     WMF
        //     FrameImage
        //     FrameVector
        //     QuickTime
        //     [etc.]
        // The facet is ended when a line beginning =EndInset is encountered
        // The lines within the facet all start with &
        private boolean inFacet = false;
        
        
        /**
         * Create a MifBufferedReader
         * @param inStream The BufferedReader we will read from.
         */
        MifBufferedReader(Reader inStream) {
            input = new BufferedReader(inStream);
            // * after ['`‘] is temporary!!!! ######################################################
            macPat = Pattern.compile("^\\s*de.+?ne\\(([^, ]+)\\s*,\\s*['`‘]*([^']+)'\\)\\s*$", Pattern.DOTALL);
            macMatcher = macPat.matcher("");
            
            // Pattern that matches lines that are exclusively comments
            hashPat = Pattern.compile("^\\s*#.*$", Pattern.DOTALL);
            hashMatcher = hashPat.matcher("");
        }
        
        
        /**
         * Read the next character from the input stream.
         * @return An integer representing the next character, or -1
         *         on EOF.
         * @throws java.io.IOException
         *         If an error occurs.
         */
        int read() throws IOException {
            // If we're out of characters to return, get some more!
            if (nextPos >= curLine.length()) {
                curLine = this.readLine();
                if (curLine == null) {
                    // End of stream! EOF
                    return -1;
                }
            }
            
            // We should have a line now. If it has at least one character, return
            // the first (or next) character.
            if (nextPos < curLine.length()) {
                return curLine.charAt(nextPos++);
            }
            else {
                return this.read();    // Handle case of completely blank lines.
//                return -1;
            }
        }
        
        private Matcher facetMatcher = Pattern.compile("=\\S.*",Pattern.DOTALL).matcher("");
        /**
         * Return the characters from the current position to the end of line.
         * @return The remaining characters in the line (without trailing newlines ...)
         *         or null if end of stream.
         * @throws java.io.IOException
         *         If an error occurs.
         */
        String readLine() throws IOException {
            String retString = "";
            if (curLine == null) { //Already read to EOF
                return null;
            }
            else if (nextPos < curLine.length()) {
                retString = curLine.substring(nextPos);
                nextPos = curLine.length();
                return retString;  // Return the remainder of the line--at least one character
            }
            else {  // nextPos >= curLine.length(), i.e.
                // We've exhausted the current buffer; read another
                // If we're out of characters to return, get some more!
                boolean haveSome = false;    // We don't have any characters yet.
                do {
                    curLine = input.readLine();
                    if (curLine == null) {
                        // End of stream! EOF
                        return null;
                    }
                    
                    // WW 6/5/7: Skip over facets
                    if (curLine.startsWith("=EndInset")) {
                        // We are finished with the current facet
                        this.inFacet = false;
                        continue;    // The next line read is probably meaningful
                    }
                    else if (this.inFacet) {
                        // We're inside a facet. Keep reading
                        continue;
                    }
                    // An = followed by a facet name signals the start of a facet.
                    else if (facetMatcher.reset(curLine).matches()) {
                        this.inFacet = true;
                        continue;
                    }
                    // I guess we're not in a facet. Proceed
                    
                    // Skip lines that are macros or all comments
                    macMatcher.reset(curLine);
                    hashMatcher.reset(curLine);
                    if (macMatcher.find()) {
                        // Keep track of the macros (in case we need them in the
                        // future
                        macs.put(macMatcher.group(1), macMatcher.group(2));
                    }
                    else if (hashMatcher.find()) {  // Is line all comment?
                        // Keep looking past this line. It is all comment
                    }
                    else {
                        haveSome = true;  // If not macros, consider to macros,
                                          // I suppose.
                        nextPos = 0;
                    }
                }
                while(! haveSome);
            }
            return curLine;
        }
        /**
         * If possible, return the character just read to the input buffer
         * @return An integer representing the character just unread, or -1
         *         if unable to unread the character.
         * @throws java.io.IOException
         *         If a significant error occurs.
         */
        int unread() throws IOException {
            // If we're already at the very beginning of the curLine, we can't
            // unread. Return -1.
            if (nextPos <= 0) {   // Include negative numbers for good measure
                return -1;
            }
            
            // Otherwise see if we can unread the character.
            // (Note: it can be one larger than the buffer--poised to asked for
            // another line on the next read call; hence the use of <= rather
            // than just <)
            else if (nextPos <= curLine.length()) {
                return curLine.charAt(--nextPos);
            }
            
            // nextPos must be too big
            else {
                return -1;
            }
        }
    }
}
