/*
 * XMLImporter.java
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

import com.sun.star.awt.CharSet;
import f2xutils.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.xml.sax.ext.LexicalHandler;

import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.util.regex.*;

/**
 * The generic XML importer will convert most XML to XLIFF. 
 * 
 * @author Weldon Whipple &lt;weldon@whipple.org&gt;
 */
public class XMLImporter extends DefaultHandler implements Converter, LexicalHandler {

    private BufferedWriter xliffOut;        // Where to write the XLIFF
//    private OutputStreamWriter xliffOut;   // Where to write the XLIFF
    private BufferedWriter tskeletonOut;
//    private OutputStreamWriter tskeletonOut;
    private BufferedWriter formatOut;
//    private OutputStreamWriter formatOut;
    private Locale sourceLanguage;         // For the source-language attribute
    private String dataType;               // For the datatype attribute
    private String originalFileName;       // For the original attribute

    private int curIndent = 0;              // How far to indent the next element
    private int curTagNum = 0;              // For skeleton    
        
    private Locale curTargetLang;           // Language of the current <target> string

    private SegmentBoundary boundaryType;   // Paragraph or Sentence segments?
    private HashSet<XMLTuXPath> tuXPathSet; // Set of candidate XPaths to TUs
    private Set<XMLTuXPath> skipSet;        // Set of the above to omit.

    // The following accumulates the text from an element.
    private StringBuilder candidateTuSource = new StringBuilder();
    
    // The following accumulates CDATA (in case it ends up being within a text
    // node). If within a text node, it will become an x tag in the XLIFF.
    private StringBuilder candidateCdata = new StringBuilder();
    
    // Are we processing an entity at the moment?
    private boolean inEntity = false;
    
    // Are we in a CDATA section?
    private boolean inCdata = false;

    private int bxExXId = 1;           // Id of bx, ex or x tag

    // Each time a new bx is encountered, push its rid on the stack. The matching
    // ex tag pops the stack and uses the same rid.
    private Stack<Integer> ridStack = new Stack<Integer>();

    // rid's used by bx/ex use the next available rid:
    private int nextAvailRid = 1;

    // The following variable will be incremented with each startElement call and
    // decremented with each endElement call.
    private int curXPathDepth = 0;

    /** Locator to keep track of where each startElement occurs */
    private Locator locator;
    
    // If we are inside a (translatable) text element, descendant elements will all
    // be represented as bx/ex tags. If we are not inside a text element, as each
    // new element is encountered, we will check the tuXPathSet and skipSet to
    // determine if we need to set insideTextElement to true.
    private boolean insideTextElement = false;
    
    // When we set the above variable to true, we will record the current text
    // element in curTextElement. Then--when we encounter the corresponding
    // end element tag--we will clear the curTextElement variable and set
    // insideTextElement to false.
    private String curTextElement = "";
    
    // Whenever we set insideTextElement to true, we will remember the current
    // XPathDepth. This will prevent a premature exit from a text element (if,
    // for example, a text element includes a descendant of the same name as
    // the parent.
    private int curTextElementDepth = -1;
    
    // Matcher for XML's predefined entities:
    private Matcher entityMatcher = Pattern.compile("lt|gt|apos|quot|amp").matcher("");
    
    /**
     * Constructor for the XML importer. 
     */
    public XMLImporter() {
    }
    
    /**
     * Convert an XML file to XLIFF. Additionally create skeleton and format 
     * files. (The skeleton and format files are used to export translated 
     * targets back to the original XML format.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The language of the XML file to be imported.
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XML file. This value is
     *        currently ignored, allowing the SAX parser to interpret any byte
     *        order marks and encoding specified in the input file.
     * @param nativeFileType The type of the input file. Must be XML.
     * @param inputXmlFileName The name of the input XML file.
     * @param baseDir The directory that contains the input XML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff.</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        XLIFF file was written.
     * @param skipList A set of potential translatable structures to omit. This
     *        converter requires that the Set consist of XMLTuXPath objects.
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */    
    public ConversionStatus convert(ConversionMode mode,
            Locale language,
            String phaseName,            
            int maxPhase,
            Charset nativeEncoding,
            FileType nativeFileType,
            String inputXmlFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName,
            Set<XMLTuXPath> skipList) throws ConversionException {

        if (! mode.equals(ConversionMode.TO_XLIFF)) {
            throw new ConversionException("XML Importer supports only conversions"
                    + " from XML to XLIFF.");
        }
        
        if (inputXmlFileName == null || inputXmlFileName.length() == 0) {
            throw new ConversionException("Name of input XML file omitted.");
        }

        if (language == null) {
            throw new ConversionException("Source language omitted. (Required)");
        }

        // Create an object to generate a set of candidate XPaths to translatable
        // text.
        XMLCandidateTuXPathGenerator xPathGen =  new XMLCandidateTuXPathGenerator();        
        
        // Then ask that object for a set of XMLTuXPaths that likely contain
        // translatable text
        try {
            this.tuXPathSet
                = xPathGen.getCandidateTuXPaths(new FileInputStream(
                    baseDir + File.separator + inputXmlFileName));
            
            // Write out the Candidate Paths (for posterity)
            if (tuXPathSet != null && tuXPathSet.size() > 0) {
                OutputStreamWriter candOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXmlFileName + "candidates"));
                Iterator<XMLTuXPath> xpIter = tuXPathSet.iterator();
                while (xpIter.hasNext()) {
                    candOut.write(xpIter.next().toString() + "\n");
                    candOut.flush();
                }
                candOut.close();
            }
            
        }
        catch(FileNotFoundException e) {
            System.err.println("Cannot locate input XML file: " + e.getMessage());
            throw new ConversionException("Cannot locate input XML file: " + e.getMessage());
        }
        catch(IOException ioex) {
            System.err.println("Error saving copy of Candidate TU XPaths. Continuing ...");
        }
        
        // See if the XPath set contains any elements. If not, we have nowhere
        // to look for content.
        if ((tuXPathSet == null) || (tuXPathSet.size() == 0)) {
            System.err.println("Unable to identify any translatable text in file " 
                    + baseDir + File.separator + inputXmlFileName);
            throw new ConversionException("Unable to identify any translatable text in file " 
                    + baseDir + File.separator + inputXmlFileName);
        }
        
        // Now that we have a set of candidate TU XPaths, see if there is also
        // subset of those XPaths to skip. (There *might* not be.)
        if (skipList != null) {
            skipSet = (Set<XMLTuXPath>)skipList;

            if (skipSet.size() > 0) {
                try {
                    // Write out the Skip list (for posterity)
                    OutputStreamWriter skipOut  = new OutputStreamWriter(new FileOutputStream(
                        baseDir + File.separator + inputXmlFileName + "skiplist"));
                    Iterator<XMLTuXPath> skipIter = skipSet.iterator();
                    while (skipIter.hasNext()) {
                        skipOut.write(skipIter.next().toString() + "\n");
                        skipOut.flush();
                    }
                    skipOut.close();
                }
                catch(FileNotFoundException e) {
                    System.err.println("Cannot save copy skiplist: " + e.getMessage()
                        + " (continuing ...)");
                }
                catch(IOException ioex) {
                    System.err.println("Error saving copy of skiplist: "
                            + ioex.getMessage() + " (continuing ...)");
                }
            }
        }
        
        sourceLanguage = language;             // The input XML file's primary language
        dataType = nativeFileType.toString();  // XML is being imported
        originalFileName = inputXmlFileName;   // The name of the input XML file
        
        boundaryType = boundary;               // Keep track of the segment boundary.
        if (boundaryType == null) {              // Default to Paragraph if unspecified.
            boundaryType = SegmentBoundary.SENTENCE;
        }
                
        // Create output stream writers for the SAX handler to write to.
        // We will store our output as UTF-8
        try {
            xliffOut  = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXmlFileName + Converter.xliffSuffix),
                    "UTF8"));
            tskeletonOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXmlFileName + Converter.tSkeletonSuffix),
                    "UTF8"));
            formatOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXmlFileName + Converter.formatSuffix),
                    "UTF8"));
        }
        catch (UnsupportedEncodingException e) {
            System.err.println("Unable to write XLIFF as UTF-8!!");
            System.err.println(e.getMessage());
            throw new ConversionException("Unable to write XLIFF as UTF-8: " + e.getMessage());
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            throw new ConversionException(e.getMessage());
        }

        XMLReader parser = null;
        Charset encoding = null;
        
        try {
            // Let's parse with the an XML Reader
            parser = XMLReaderFactory.createXMLReader();
            
            parser.setContentHandler(this); // We're gonna handle content ourself

            // We want to preserve entities in the document as entities in the TUs
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", this);
            
            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);

            // The Reader prevents SAX from recognizing the byte-order mark (BOM)
            // Using an InputStream will let SAX read the BOM and detect the
            // proper encoding (Cool!)
            InputStream inStream = new FileInputStream(
                    baseDir + File.separator + inputXmlFileName);
            
            InputSource xmlIn = new InputSource(inStream);
            
            // We need the encoding to pass to the skeleton merger.
            String encodingStr = xmlIn.getEncoding();
            if (encodingStr != null && encodingStr.length() > 0) {
                encoding = Charset.forName(encodingStr);
            }
            else {
                encoding = Charset.forName("UTF-8");
            }
            parser.parse(xmlIn);
        }
        catch(SAXException e) {
            System.err.println("XML parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("SAX parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading XML input: " + e.getMessage());
            throw new ConversionException("I/O error reading XML input: " + e.getMessage());
        }

        // Null out some objects we don't need any more:
        this.candidateCdata.setLength(0);    this.candidateCdata.trimToSize();    this.candidateCdata = null;
        this.candidateTuSource.setLength(0); this.candidateTuSource.trimToSize(); this.candidateTuSource = null;
        this.tuXPathSet.clear(); this.tuXPathSet = null;
        if (this.skipSet != null) {
            this.skipSet.clear(); this.skipSet = null;
        }
        this.ridStack.setSize(0); this.ridStack.clear(); this.ridStack = null;
        
        try {
            /* Close the files we created above */
            xliffOut.close(); xliffOut = null;
            tskeletonOut.close(); tskeletonOut = null;
            formatOut.close(); formatOut = null;

            /* We have created a temp skeleton file (an intermediate skeleton file).
             * We now need to merge the temporary skeleton with the original input file to
             * yield a "real" skeleton */

            // We'll read from the temporary skeleton
            FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator
                    + inputXmlFileName + Converter.tSkeletonSuffix);

            // We'll also read from the original input file
            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator
                    + inputXmlFileName);   // This is the content.xml file

            // We'll write to the (final) skeleton file
            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator
                    + inputXmlFileName + Converter.skeletonSuffix);

            // The XmlSkeletonMerger will do the deed.
            SkeletonMerger merger = new XMLSkeletonMerger();

            if (merger != null) {
                merger.merge(tSkeletonIn, nativeIn, skeletonOut, encoding);
            }

            tSkeletonIn.close();
            nativeIn.close();
            skeletonOut.close();
        }
        catch(java.io.FileNotFoundException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        
        if (generatedFileName != null) {
            generatedFileName.write(inputXmlFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert an XML file to XLIFF. Additionally create skeleton and format 
     * files. (The skeleton and format files are used to export translated 
     * targets back to the original XML format.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The language of the XML file to be imported.
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XML file. This value is
     *        currently ignored, allowing the SAX parser to interpret any byte
     *        order marks and encoding specified in the input file.
     * @param nativeFileType The type of the input file. Must be XML.
     * @param inputXmlFileName The name of the input XML file.
     * @param baseDir The directory that contains the input XML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff.</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        XLIFF file was written.
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */    
    public ConversionStatus convert(ConversionMode mode,
            Locale language,
            String phaseName,
            int maxPhase,
            Charset nativeEncoding,
            FileType nativeFileType,
            String inputXmlFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputXmlFileName, baseDir, notifier, boundary, 
                generatedFileName, null);
    }
    
    /**
     * Convert an XML file to XLIFF. Additionally create skeleton and format 
     * files. (The skeleton and format files are used to export translated 
     * targets back to the original XML format.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The language of the XML file to be imported.
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XML file. This value is
     *        currently ignored, allowing the SAX parser to interpret any byte
     *        order marks and encoding specified in the input file.
     * @param nativeFileType The type of the input file. Must be XML.
     * @param inputXmlFileName The name of the input XML file.
     * @param baseDir The directory that contains the input XML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff.</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */ 
    @Deprecated
    public ConversionStatus convert(ConversionMode mode,
            Locale language,
            String phaseName,
            int maxPhase,
            Charset nativeEncoding,
            FileType nativeFileType,
            String inputXmlFileName,
            String baseDir,
            Notifier notifier) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputXmlFileName, baseDir, notifier, null, null, null);
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
     * Method called by the SAX parser at the beginning of document parsing.
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startDocument() throws SAXException {

        // Write the start of the output XLIFF document
        writeXliff(Converter.xmlDeclaration
                 + Converter.startXliff
                 + indent() + "<file original='" 
                       + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                       + "' source-language='" + sourceLanguage.toString() 
                       + "' datatype='xml'>\r\n"
                 + indent() + "<header");
        
        switch(boundaryType) {
            case PARAGRAPH:
                writeXliff(" lt:segtype='paragraph'");
                break;
            case SENTENCE:
                writeXliff(" lt:segtype='sentence'");
                break;
            default:
                writeXliff(" lt:segtype='sentence'");
        }

        writeXliff(">\r\n" 
                 + indent('0') + "</header>\r\n" 
                 + indent('0') + "<body>\r\n");
        
        // Also write the beginning of the format file
        writeFormat("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n"
                  + "<lt:LT xmlns:lt=\"http://www.lingotek.com/\">"
                  + "<tags formatting=''>\r\n"); // no &lt; formatting value!
    }

    /**
     * Method called whenever a start element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if
     *        Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string 
     *        if qualified names are not available
     * @param atts The specified or defaulted attributes.
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {

        // If we are in the midst of a CDATA section, return immediately.
        // This should never happen, BTW, AFAIK
        if (inCdata) {
            System.err.println("FYI: startElement was called within a CDATA section!!");
            return;
        }
        
        // Prefer the qualifiedName. If unable to find a qualified name, use
        // the local name. 
        String elementName = qualifiedName;
        if ((elementName == null) || (elementName.length() == 0)) {
            elementName = localName;
        }
        
        // Keep track of how deep we are in the XPath hierarchy.
        curXPathDepth++;
        
        // Where does SAX think this element starts?
        int curLine = locator.getLineNumber();
        int curCol = locator.getColumnNumber();
        
        
        // If we're already inside a text element, convert this tag to a bx tag in the
        // XLIFF and add a format file entry.
        if (insideTextElement) {
            writeTSkeleton("<" + elementName + " inText='inside'>\r\n");

            // Map the tag to an XLIFF bx tag
            StringBuilder fmtStr = new StringBuilder("<" + elementName);
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }

                // We need to handle the case where the value of an attribute 
                // (of this element *within* translatable text) also contains
                // translatable text. In this case, the value of the attribute
                // within the format file will be replaced by a tu placeholder,
                // which we will recursively expand during the export.
                if (isTextElement(curLine, curCol, aName)) {
                    // Write the attribute and retrieve its seg IDs
                    ArrayList<UUID> segList = writeTu(attVal); // Write 1+ segments
                    if (segList == null || segList.isEmpty()) { // If no UUIDs
                        fmtStr.append(attVal + "\"");           // Just use the attr val from the XML
                    }
                    else if (segList.size() == 1) {
                        fmtStr.append("&lt;lTLt:tu id='" + segList.get(0) + "'&gt;\"");
                    }
                    else { // 2+ segments in this attribute.
                        fmtStr.append("&lt;lTLt:tu ids='tu:" + segList.get(0));
                        // Add the 2nd+ segment IDs
                        for (int j = 1; j < segList.size(); j++) {
                            fmtStr.append(" tu:" + segList.get(j));
                        }
                        fmtStr.append("'&gt;\"");
                    }
                    // Note, for translatable attribute values of elements within
                    // ancestral translatable elements, there will be no note of 
                    // the attribute values in the skeleton. Then will be resolved
                    // recursively as format values are retrieved.
                }
                else {  // Not a text element
                    fmtStr.append(attVal + "\"");
                }
            }

            fmtStr.append(">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr.toString().replace("\n","&#xa;") + "]]></tag>\r\n");
//                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<bx id='" + bxExXId + "'");
            // bx tags have reference ID attrs that match the rid of the
            // "closing" (matching) ex tag.
            appendTuText(" rid='" + nextAvailRid + "'");
                
            // Stack the rid for popping by matching ex
            ridStack.push(Integer.valueOf(nextAvailRid));
            nextAvailRid++;
            
            appendTuText(" ctype='x-" + qualifiedName + "'");

            // Finally (!!), close the bx tag
            appendTuText("/>");
            
            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }
        
        else {
            // This *could* be the beginning of translatable text! Check its location,
            // then compare it with the tuXPathSet and skipSet to see if this is
            // the beginning of a text run!
            if (isTextElement(curLine, curCol, null)) {
                insideTextElement = true;     // This starts a TU
                curTextElement = elementName; // Needed to know when to leave text element
                curTextElementDepth = curXPathDepth; // Used with above for positive identification
                writeTSkeleton("<" + elementName + " inText='entering'>\r\n");
            }
            // Else just stay "outside the text"
            else {
                writeTSkeleton("<" + elementName + " inText='outside'>\r\n");
            }
        }

        // Check attributes to see if we are interested in them.
        for (int i = 0; i < atts.getLength(); i++) {
            String attName = atts.getQName(i);
            if (isTextElement(curLine, curCol, attName)) {
                writeTSkeleton("<lTLt:attr name='" + attName + "' tag='" + elementName + "'>\r\n");
                // The attribute value might have "bare" [&<>'"'] characters, since
                // the parser graciously converts &amp;, &lt;, &gt;, &quot;, and &apos;
//                writeTu(TuStrings.escapeTuString(atts.getValue(i)));     // Write 1+ segments
                writeTu(TuStrings.escapeTuString(atts.getValue(i)).replace("\n","&#xa;"));     // Write 1+ segments
                
                writeTSkeleton("</lTLt:attr name='" + attName + "' tag='" + elementName + "'>\r\n");
            }
        }        
    }

    /**
     * Method called whenever an end element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string if qualified names are not available
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endElement(String namespaceURI, 
			   String localName,
			   String qualifiedName) throws SAXException {

        // If we are in the midst of a CDATA section, return immediately.
        // This should never happen, BTW, AFAIK
        if (inCdata) {
            System.err.println("FYI: endElement was called within a CDATA section!!");
            return;
        }
        
        String elementName = qualifiedName;
        if ((elementName == null) || (elementName.length() == 0)) {
            elementName = localName;
        }

        
        // We're inside a text element?
        if (insideTextElement) {
            // Does this tag close a text element?
            if ((this.curXPathDepth == this.curTextElementDepth)
                && (elementName.equals(curTextElement))) {

                insideTextElement = false;     // This ends a TU
                curTextElement = "";           // Needed to know when to leave text element
                curTextElementDepth = -1;      // Used with above for positive identification

                writeTu(this.candidateTuSource.toString());
                this.candidateTuSource.setLength(0);  // Clear the candidateTu buffer.

                writeTSkeleton("</" + elementName + " inText='leaving'>\r\n");
            }
            else {
                // This is the end of a descendant element of the text element
                // We need to represent the tag as an XLIFF ex element and
                // add an entry to the format file that maps the ex element to
                // the original file.
                writeTSkeleton("</" + elementName + " inText='inside'>\r\n");

                // Map the end tag to an XLIFF ex tag
                // Write out the format string.
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                            + "</" + elementName + ">" + "]]></tag>\r\n");

                // Write an ex tag to the TU's source text:
                int curRid = ridStack.pop().intValue();
                appendTuText("<ex id='" + bxExXId + "' rid='" + curRid + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
        }
        else { // We're not inside a text element
            // Just add a line to the temporary skeleton file
            writeTSkeleton("</" + elementName + " inText='outside'>\r\n");
        }

        // Adjust our XPath depth.
        curXPathDepth--;
    }
    
     /**
     * Called whenever characters are encountered
     * @param ch Array containing characters encountered
     * @param start Position in array of first applicable character
     * @param length How many characters are of interest?
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void characters(char[] ch,
			   int start,
			   int length) throws SAXException {

//        // If we are in the midst of a CDATA section, return immediately.
//        if (inCdata) {
//            return;
//        }

        // If this/these character/s represent an entity, don't output
        // it/them--the startEntity()/endEntity methods (below) will output
        // the entities.
        if (inEntity) {
            return;
        }
        
        // Write to the candidate trans-unit buffer
        if (this.insideTextElement) {
            String charString = new String(ch, start, length);
            if (inCdata) {  // These characters are part of the CDATA ... within
                            // a text node.
                this.candidateCdata.append(ch, start, length);
            }
            else {  // This is part of the TU segment text.
                appendTuText(charString);
            }
        }
    }

    /**
     * When the end-of-document is encountered, write what follows the final
     * translation unit.
     * @throws java.lang.IOException 
     *         If unable to flush the output streams.
     */
    public void endDocument()
	throws SAXException {
        
        // Finish off the XLIFF file:
        writeXliff(indent('-') + "</body>\r\n"); // Close the body element
        writeXliff(indent('-') + "</file>\r\n"); // Close the file element
        writeXliff("</xliff>\r\n");               // Close the xliff element

        // Also finish off the format file:
        writeFormat("</tags>\r\n</lt:LT>\r\n");

        // Three flushes (not very water-wise, I admit)
        try {
            xliffOut.flush();             // Well-bred writers flush when finished
            formatOut.flush();
            tskeletonOut.flush();
        }
        catch(IOException e) {
            System.err.println("Error flushing skeleton stream.");
            System.err.println(e.getMessage());
        }
    }

    /************************************************************************
     * L e x i c a l H a n d l e r   m e t h o d s
     ***********************************************************************/
    /**
     * Method that the SAX parser calls whenever it encounters an entity 
     * (e.g. gt, lt, apos, ...). We implement this method (an implementation
     * of the method by the same name in the LexicalHandler interface) in order
     * to preserve the XML entities in the original XLIFF as we import it into
     * "our" XLIFF.
     * <p>The inEntity instance variable is checked by the characters method
     * of the ContentHandler (DefaultHandler) extension (above). The SAX
     * parser calls the characters method whenever it expands an entity, passing
     * it *only* the expansion of the entity it just encountered. Since we want
     * to write out the unexpanded version of the entity, this (startEntity)
     * method writes out the entity, and characters() just returns without
     * outputting the expansion of the entity (if inEntity is true).
     * <p>Note: the endEntity method (below) sets the inEntity variable to
     * false.
     * @param name The name of the entity (e.g. "lt", "gt", etc.--without a
     *             leading ampersand or trailing semicolon.)
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void startEntity(String name)
        throws SAXException {
        
        inEntity = true;
        
        if (this.insideTextElement) {
            if (this.inCdata) {
                this.candidateCdata.append("&" + name);
            }
            else {   // Add it to the text buffer
                appendTuText("&" + name);
            }
        }
//        // Match this entity against XML's default 5 entities:
//        entityMatcher.reset(name);
//        if (entityMatcher.matches()) {
//            // If one of the standard 5, maintain its format
//            appendTuText("&");
//        }
//        else {
//            // Otherwise, make the initial & into an &amp; entity.
//            appendTuText("&amp;");
//        }
    }
    
    /**
     * Method that the SAX parser calls whenever it reaches the end of an entity 
     * (e.g. gt, lt, apos, ...). See comments for startEntity (above) for more
     * information on how this works.
     * @param name The name of the entity (e.g. "lt", "gt", etc.--without a
     *             leading ampersand or trailing semicolon.)
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endEntity(String name) throws SAXException {

        if (this.insideTextElement) {
            if (this.inCdata) {
                this.candidateCdata.append(";");
            }
            else {
                appendTuText(";");
            }
        }
        
        inEntity = false;
    }

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
        
        // If the CDATA is within a translatable text element, we will save it
        // as format data (in the format file). Otherwise, we will just skip 
        // over it.
        if (this.insideTextElement) {
            // The candidate Cdata buffer will accumulate the format file mapping.
            this.candidateCdata.setLength(0);  // In case any leftovers are present
            this.candidateCdata.append("<![CDATA[");
        }
        else {
            // Not in text; put an entry in the temporary skeleton, so that we
            // can skip over CDATA (leaving it in place untouched) when we make
            // the skeleton file.
            writeTSkeleton("<![CDATA[ >\r\n");
        }
    }

    /** 
     * Method called by the SAX parser when it encounters the end of a CDATA
     * section. We will (for now, at least) treat CDATA the same way as 
     * comments (ignore them, skipping past them).
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void endCDATA() throws SAXException {
        if (this.insideTextElement) {
            // The candidate Cdata buffer will accumulate the format file mapping.
            this.candidateCdata.append("]]>");
            // Write the CDATA to the format file:
            writeFormat("  <tag id='" + bxExXId + "' cdataTagIsLiteral='true'>"
                        + candidateCdata.toString() + "</tag>\r\n");
            candidateCdata.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<x id='" + bxExXId + "'");
            appendTuText(" ctype='x-ctype'/>");
            bxExXId++;              // Increment id val for next x, bx, ex tags.

        }
        else {
            // Not in text; put an entry in the temporary skeleton, so that we
            // can skip over CDATA (leaving it in place untouched) when we make
            // the skeleton file.
            writeTSkeleton("]]> >\r\n");
        }
        inCdata = false;
    }

    /** 
     * Method defined by the LexicalHandler interface that we *probably* don't 
     * care about. If it is called, however (and if we are inside a CDATA section
     * within a text node [!!!???]) then save the comment with the rest of the
     * CDATA in the format file to be mapped to by an x tag ...
     * @param text Array containing characters encountered
     * @param start Position in array of first applicable character
     * @param length How many characters are of interest?
     * @throws org.xml.sax.SAXException
     *         If the SAX parser needs to report errors.
     */
    public void comment (char[] text, int start, int length) throws SAXException {
        if (this.insideTextElement && this.inCdata) {
            this.candidateCdata.append(text, start, length);
        }
    }
    
    
        
    /**
     * Passed a character string encountered during traversal of either the 
     * <source> or one of the <target> elements of a <trans-unit>, write those
     * characters to the specified location in the tuList. 
     * @param text The text to append
     */
    private void appendTuText(String text) {
        if ((text == null) || (text.length() == 0)) {
            return;
        }
        
        candidateTuSource.append(text);
        
        return;
    }
    
    /** 
     * Return an object representing a format-specific (and converter-specific) 
     * property.
     * @param property The name of the property to return.
     * @return An Object that represents the property's value.
     */
    public Object getConversionProperty(String property) {
        return null;
    }

    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the XML file type.
     */
    public FileType getFileType() {
        return FileType.XML;
    }

    /**
     * Return an indentation string of blanks, increasing the indentation by 1 space
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent() {
        return indent('+');
    }

    /**
     * Specify the number of characters to indent. Set the curIndent variable to
     * that value and return that number of space characters.
     * @param numSpaces The number of
     */
    private String indent(int numSpaces) {
        if (numSpaces >= 0) {
            curIndent = numSpaces;
        }
        return indent('0');
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
     * Passed the line and column coordinates of a startElement, together with
     * a (possibly null) attribute name, determine if the element/attribute at
     * the specified location contains translatable text.
     */
    private boolean isTextElement(int line, int col, String attrName) {
        // In the following, we don't care about the first argument--the string
        // representation of the XPath. It isn't used in determing set membership.
        XMLTuXPath xPath = new XMLTuXPath("", line, col, attrName);
        if (this.tuXPathSet.contains(xPath)) {
            if (this.skipSet == null) {
                return true;
            }
            else if (this.skipSet.contains(xPath)) {
                return false;     // Skip this one.
            }
            else {  // This XPath isn't to be skipped
                return true;
            }
        }
        else { // Not in the XPath Set; return false.
            return false;
        }
    }    
    
    /**
     * Write a string to the format file. 
     * @param text The string to write to the format document. (No newlines, etc.
     *             are added. The caller must supply all desired formatting.)
     */
    private void writeFormat(String text) {
        // Write format file ...
        if (text != null) {
            try {
                formatOut.write(text);
                formatOut.flush();   // For debugging only
            }
            catch(IOException e) {
                System.err.println("Error writing format file.");
                System.err.println(e.getMessage());
            }        
        }
    }

    /**
     * Write a string to the temporary (intermediate) skeleton file
     * @param text The string to write to the tskeleton. (No newlines, etc.
     *             are added. The caller must supply all desired formatting.)
     */
    private void writeTSkeleton(String text) {
        // Write tskeleton file ...
        if (text != null) {
            try {
                int lastGt = text.lastIndexOf('>');
                String textWithSeq = "";
                if (lastGt != -1) {
                    textWithSeq = text.substring(0, lastGt) + " seq='" + 
                       (curTagNum++) + "'" + text.substring(lastGt);
                }
                else {
                    textWithSeq = text;
                }
                tskeletonOut.write(textWithSeq);
                tskeletonOut.flush();    // For debugging
            }
            catch(IOException e) {
                System.err.println("Error writing tskeleton file.");
                System.err.println(e.getMessage());
            }        
        }
    }

    /**
     * Passed a string of text (and possibly bx/ex tags), write it as one or more
     * translation units. (If paragraph segmentation is in effect, output a 
     * single trans-unit element; if sentence segmentation is in effect, output 
     * one or more trans-units.)
     * @param tuBuf A string of text to output to one or more TUs. 
     * @return An ArrayList of the TU IDs of the segments written to XLIFF.
     */
    private ArrayList<UUID> writeTu(String tuBuf) {

        // Get the core segments:
        SegmentInfo[] coreTus = TuPreener.getCoreSegments(tuBuf,
            this.boundaryType, this.sourceLanguage, true);
        
        ArrayList<UUID> retUUIDs = new ArrayList<UUID>();
        
        UUID idOfCurSegment = UUID.randomUUID();  // The first seg's TU ID

        // The (parent?) paragraph ID is the UUID of the first (or possibly
        // *only*) TU.
        UUID paraId = idOfCurSegment;

        // Process each segment. If a segment contains translatable text,
        // create a TU for it (in the XLIFF) and write a "tu" line to the temporary
        // skeleton. If the segment doesn't contain translatable text, prepend or
        // append it to a neighboring segment (where it will appear outside the
        // core, of course.)

        // Untranslatable "segment" that hasn't been merged with neighboring
        // translatable segment:
        String accumulatedUntranslatable = "";
        boolean encounteredTranslatableSegment = false;

        // We will note (with the lt:mergeable attribute) TUs that are mergeable--
        // followed by another sentence segment within the same paragraph. The
        // lt:mergeable attribute will confirm that a younger sibling exists. 
        StringBuilder siblingTuSuffix = new StringBuilder();

        for (int i = 0; i < coreTus.length; i++) {
            if (coreTus[i].translatable()) {
                retUUIDs.add(idOfCurSegment);
                if (encounteredTranslatableSegment /* previously */) {
                    // We left the source and trans-unit elements open to
                    // receive untranslatable appendages. ... So we need to
                    // close the earlier source and trans-unit.
                    if (siblingTuSuffix.length() > 0) {
                        // Write a link from previous segment to this one
//                        writeXliff("lt:next-tu-id='" + idOfCurSegment + "'");
                        writeXliff("lt:mergeable='true'");

                        // Then write out the rest of the previous
                        // segment
                        writeXliff(siblingTuSuffix.toString());
                        siblingTuSuffix.setLength(0);
                    }

                    // Close the source element
                    writeXliff("</source>\r\n");

                    // Close the trans-unit element
                    writeXliff(indent('-') + "</trans-unit>\r\n");
                }

                encounteredTranslatableSegment = true;  // Meaningful if this is the first one
                String coreSeg = coreTus[i].getSegmentStr();  // Includes lt:core tags

                // Open the trans-unit element (No offset attribute in the following (yet?))
                writeXliff(indent('0')
                    + "<trans-unit id='" + idOfCurSegment.toString() + "' "
                    + "lt:paraID='" + paraId + "' ");
                
                siblingTuSuffix.append(">\r\n");

                // Open the source tag
                siblingTuSuffix.append(indent('+') + "<source xml:lang='" + sourceLanguage.toString() + "'>");

                // If there is any residual untranslatable text, prepend it to this <source>
                // (where it will fall before the opening lt:core tag.)
                if (accumulatedUntranslatable.length() > 0) {
                    siblingTuSuffix.append(accumulatedUntranslatable);
                    accumulatedUntranslatable = "";
                }

                // Write the actual text of the TU:
                // It should be already escaped (shouldn't it?)
                siblingTuSuffix.append(coreSeg);

                // Don't close the source or trans-unit elements yet--in case some trailing
                // untranslatable "segments" need to be appended after this source

                // Write to the intermediate skeleton file
                // Note: The no=x of=y attributes are a ceiling values.
                // Because some segments will be merged with neighbors, the
                // no= sequence will probably skip some numbers, and
                // the of= value generally be high.

                writeTSkeleton("<lTLt:tu id='" + idOfCurSegment.toString() + "' length='"
                    + coreSeg.length() + "' no='" + (i+1) + "' of='" + coreTus.length + "'>\r\n");

                idOfCurSegment = UUID.randomUUID();  // UUID for next segment, if necessary.

            }
            else {  // Not translatable
                // This particular segment (of the set of 2+ segments in the
                // candidate TU buffer--1+ segment of which has translatable
                // text) doesn't have translatable text. We still need to
                // capture that non-translatable text when we construct a
                // target translation from the skeleton file and targets.
                // ... so store the text in the format file and reference it
                // in the tskeleton
                // Save the untranslatable text in the format file,
                // with a reference to it in an x tag (which will be
                // placed outside the core of a neighboring translatable
                // segment).
                accumulatedUntranslatable += "<x id='" + bxExXId + "'/>";

                writeFormat("  <tag id='" + bxExXId + "'>");
                writeFormat(coreTus[i].getSegmentStr());
                writeFormat("</tag>\r\n");

                bxExXId++;
            }
        } // for

        // Finished looping, we now need to close remaining source and
        // trans-unit elements that are still open.
        if (coreTus.length > 0) {
            if (siblingTuSuffix.length() > 0) {
                // Then write out the rest of the previous translatable
                // segment
                writeXliff(siblingTuSuffix.toString());
                siblingTuSuffix.setLength(0);
            }

            // If there is any accumulated untranslatable text that hasn't been
            // included in an adjoining segment, add it now:
            if (accumulatedUntranslatable.length() > 0) {
                writeXliff(accumulatedUntranslatable);
            }
            // Close the source element
            writeXliff("</source>\r\n");

            // ... and the trans-unit element
            writeXliff(indent('-') + "</trans-unit>\r\n");
        }
        
        return retUUIDs;       // Callers may choose to ignore this.
    }
    
    /** 
     * Write the specified text to the output XLIFF stream.
     * @param text What to write
     */
    private void writeXliff(String text) {
        
        if ((text == null) || (text.length() == 0)) {
            return; // Hmmm ... Nothing to output
        }

        try {
            xliffOut.write(text);
            xliffOut.flush();   // For debugging
        }
        catch(IOException e) {
            System.err.println("Error writing to XLIFF: " + e.getMessage());
        }
    }
    
    /**
     * Set a format-specific property that might affect the way that the
     * conversion occurs.
     * <p><i>Note:</i> This converter needs no format-specific properties.
     * If any are passed, they will be silently ignored.
     * @param property The name of the property
     * @param value The value of the property
     * @throws file2xliff4j.ConversionException
     *         If the property isn't recognized (and if it matters).
     */
    public void setConversionProperty(String property, Object value)
            throws ConversionException {
        return;
    }
    
}
