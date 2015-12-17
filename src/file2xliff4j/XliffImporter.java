/*
 * XliffImporter.java
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
import org.xml.sax.ext.LexicalHandler;

import f2xutils.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.util.regex.*;

/**
 * Utility class representing a candidate TU's text and UUID.
 * This class is necessary to handle potentially recursive &lt;sub&gt;
 * elements. For each &lt;sub&gt; element we find in the XLIFF being
 * imported, we create a new trans-unit element. All of the TU's generated from
 * sub tags are appended at the end of the resulting XLIFF file.
 * <p>XLIFF input files that have no <sub> elements will never have more than
 * a single TuListItem.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class TuListItem {
    // The following buffer accumulates the string that looks like
    //     <trans-unit>
    //        ...
    //     </trans-unit>
    private StringBuilder transUnitElement = new StringBuilder();
    private StringBuilder tuSource = new StringBuilder();
    private Attributes sourceAtts;    // Holds attributes from <source>
    private HashMap<Locale, TuTarget> tuTargets = new HashMap<Locale, TuTarget>();
    private UUID tuID = UUID.randomUUID();

    // The following stack shows what element we are currently working on. Elements'
    // tags are pushed onto the stack in startElement, and popped off in endElement.
    protected Stack<String> tagStack = new Stack<String>();
    
    // sub TUs (contained within the &lt;sub&gt; element) can themselves have their
    // own x, bx, ex, btp, ept, ph and it elements. If those elements contain *code*
    // (as opposed to text), we will maintain separate buffers so that we can accumulate
    // those codes FOR WRITING TO THE FORMAT FILE. Tags that delimit *text* only do not
    // require special buffers.
    /* Variables for the bpt and ept elements */
    protected StringBuilder bptBuffer = new StringBuilder(); // For storing codes of bpt
    protected StringBuilder eptBuffer = new StringBuilder(); // For storing codes of ept
    protected String bptCType;           // We'll use bpt's ctype in our bx tag

    /* Variables for the ph (placeholder) element */
    protected StringBuilder phBuffer = new StringBuilder(); // For storing codes of ph
    protected String phCType;            // We'll use ph's ctype in our x tag

    /* Variables for the it (isolated tag) element */
    protected StringBuilder itBuffer = new StringBuilder(); // For storing codes of it
    protected String itCType;            // We'll use it's ctype in our x tag
    protected String itPos;              // Is this an "open" or "close" isolated tag?
    
    /** Append a specified string to the TU's source string
     * @param text The text string to append to the source
     */
    public void appendSourceText(String text) {
        tuSource.append(TuStrings.escapeTuString(text));
    }
    
    /**
     * Append a specified string to the TU target for the specified locale.
     * @param text The text string to append to the target
     * @param locale The locale of the target to append to.
     */
    public void appendTargetText(String text, Locale locale) {
        if (locale == null) {
            return;            // This is a no-op of no locale specified
        }
        // If this is a new locale, create a new target
        if (!tuTargets.containsKey(locale)) {
            tuTargets.put(locale, new TuTarget());
        }

        tuTargets.get(locale).appendText(text);
    }

    /**
     * Append characters to the &lt;trans-unit&gt; ... &lt;/trans-unit&gt; 
     * element buffer
     * @param chars The characters to append.
     */
    public void appendTransUnitElement(String chars) {
        if (chars != null) {
            transUnitElement.append(chars);
        }
    }

    /** 
     * Clear the TU's source string
     */
    public void clearSourceText() {
        tuSource.setLength(0);
    }
    
    /** 
     * Passed a Locale that corresponds to a target, clear the Target text for
     * that Locale (if it exists already)
     * @param targetLocale The locale whose target text should be cleared
     *
     */
    public void clearTargetText(Locale targetLocale) {
        tuSource.setLength(0);
        if (tuTargets.containsKey(targetLocale)) {
            tuTargets.get(targetLocale).clearText();
        }
    }
    
    /*
     * Return the TU source's attribute list
     * @return The attribute List for the <source> element
     */
    public Attributes getSourceAttributes() {
        return sourceAtts;
    }
    
    /** Return the candidate Tu's source text as a String
     * @return the TU's text
     */
    public String getSourceText() { return tuSource.toString(); }

    /**
     * Passed a target's locale, return the attribute list associated
     * with the target.
     */
    public Attributes getTargetAttributes(Locale locale) {
        TuTarget target = null;        // Holds the target
        Attributes targetAtts = null;  // Holds its attribute list

        target = tuTargets.get(locale);  // Get the target object
        if (target != null) {
            targetAtts = target.getAttributes(); // Get its attributes
        }

        return targetAtts;
    }

    /**
     * Return the Locale's for which this translation unit has targets
     * @return An array of target locales
     */
    public Locale[] getTargetLocales() {
        // If no targets, return no Locales
        if (tuTargets.isEmpty()) {
            return new Locale[0];
        }

        // Otherwise get all the locales
        Locale[] locales = new Locale[tuTargets.size()];

        Iterator<Locale> localeIter = tuTargets.keySet().iterator();
        // The two conditions between the semicolons should become false at the
        // same time!
        for (int i = 0; (i < locales.length) && localeIter.hasNext() ; i++) {
            locales[i] = localeIter.next();
        }

        return locales;
    }

    /**
     * Return the candidate Tu Target's text (for a specified Locale) as a String
     * @param locale The language of the target being requested
     * @return The target text for the specified locale, or a zero-length
     *         string if no target found for the specified locale.
     */
    public String getTargetText(Locale locale) {
        TuTarget target = null;    // Initialize to keep compiler happy.
        String targetStr = "";     // Ditto.

        target = tuTargets.get(locale);  // Get the target object
        if (target != null) {
            targetStr = target.getText(); // Get its text
        }

        return targetStr;
    }

    /**
     * Return the characters of the &lt;trans-unit&gt; ... &lt;/trans-unit&gt; 
     * element buffer
     * @return A string containing the entire trans-unit element
     */
    public String getTransUnitElement() {
        return transUnitElement.toString();
    }
    
    /** Return TU entry's Identifier
     * @return the Translation Unit Identifier of this TU
     */
    public UUID getTuID() { return tuID;}

    /** Passed the attribute list of a source element, store the list.
     * @param attList The attribute list for the <source> element
     */
    public void setSourceAttributes(Attributes attList) {
        sourceAtts = attList;
    }
    
    /**
     * Passed the attributes of a target element identified by a Locale,
     * set the target element's attributes.
     * @param atts The attribute list to set
     * @param locale The locale of the target whose attributes we will set
     */
    public void setTargetAttributes(Attributes atts, Locale locale) {
        // If this is a new locale, create a new target
        if (!tuTargets.containsKey(locale)) {
            tuTargets.put(locale, new TuTarget());
        }

        tuTargets.get(locale).setAttributes(atts);
    }
}

/**
 * Utility class representing one Target of a Translation Unit. (Each TuListItem
 * object (see above definition) has a set of these TuTargets--one for each 
 * target string, i.e. one for each Locale.)
 */
class TuTarget {
    private Attributes targetAtts;    // Holds attributes from <source>
    private StringBuilder tuTarget = new StringBuilder(); // The target text

    /**
     * Return the text of the target as a String
     * @return the TU's target string
     */
    public String getText() { return tuTarget.toString(); }

    /**
     * Return the target's attribute list.
     * @return The list of Attributes
     */
    public Attributes getAttributes() { return targetAtts; }

    /*
     * Passed a string of text, append it to the existing target string
     * @param text The string to append
     */
    public void appendText(String text) {
        if (text != null) {
            tuTarget.append(TuStrings.escapeTuString(text));
        }
    }

    /*
     * Clear the target's text string.
     */
    public void clearText() {
        tuTarget.setLength(0);
    }

    /*
     * Passed the target's attribute list, remember the attributes.
     * @param attList The attribute list to save
     */
    public void setAttributes(Attributes attList) {
        targetAtts = attList;
    }
}


/**
 * The XliffImporter is used to normalize "outside" XLIFF to a smaller subset of
 * XLIFF. This importer assumes that the "owner" of the XLIFF file used as input
 * is responsible for its associated skeleton and other files. This converter
 * creates skeleton and format files, but only for the purpose of exporting the
 * reduced XLIFF file to the original format. (Upon export, the XLIFF file will
 * have additional translation unit targets, and existing targets might be 
 * modified.)
 *
 * <p>This importer replaces bpt, ept, sub, it and ph elements (which "mask off
 * codes left inline") with x, bx and ex elements (which "remove codes"). The
 * codes removed from the bpt, ept, sub, it and ph elements are placed in a
 * format file. At export time, the information from the format file is used
 * to restore the original bpt, ept, sub, it and ph elements.
 *
 * <p>This importer also replaces opening g tags with bx elements and closing
 * g tags with ex elements. 
 * 
 * @author Weldon Whipple &lt;weldon@whipple.org&gt;
 */
public class XliffImporter extends DefaultHandler implements Converter, LexicalHandler  {

    private OutputStreamWriter xliffOut;
    private OutputStreamWriter tskeletonOut;
    private OutputStreamWriter formatOut;
    private Locale sourceLanguage;         // For the source-language attribute
    private String dataType;               // For the datatype attribute
    private String originalFileName;       // For the original attribute

    private int curIndent = 0;              // How far to indent the next element
    private int curTagNum = 0;              // For skeleton    
        
    private Locale curTargetLang;           // Language of the current <target> string
    
    // Are we in the source or body?
    private boolean inSource = false;       // Keep track of when we're inside <source>
    private boolean inTarget = false;       // Ditto for <target>

    // Are we processing an entity at the moment?
    private boolean inEntity = false;
    
    // private StringBuilder candidateTu = new StringBuilder(); // Working TU candidate
    

    private int bxExXId = 1;           // Id of bx, ex or x tag

    // Each time a new bx is encountered, push its rid on the stack. The matching
    // ex tag pops the stack and uses the same rid.
    private Stack<Integer> ridStack = new Stack<Integer>();

    // rid's used by bx/ex use the next available rid:
    private int nextAvailRid = 1;
    
    // If the Xliff contains no sub tags, the tuList will always have a size
    // of 1 (or zero)--it will never grow longer than a size of 1. Each time
    // we encounter a begin sub tag the list will grow by one. When we
    // encounter an end sub tag, the list will shrink by one.
    public ArrayList<TuListItem> tuList = new ArrayList<TuListItem>();
    
    // Because the <sub> TUs have to be accessible to targets (not just the
    // source), when the source (and each subsequent target) of a specific
    // TU encounters an end sub tag, we need to remove that TU from the
    // above tuList, but save it for any possible succeeding target's. We
    // will do that by--before destroying the list entry from the end of
    // the tuList, insert it at the 0th position of the subTuList. Then--as
    // we encounter sub's in subsequent targets, we can--instead of creating
    // a new TuListItem and adding it to the tuList, we can remove the 0th
    // entry from the subTuList and place it on the end of the tuList (above).
    // If any of the targets for a given TU don't have matching sub's, then
    // all bets are off for the current TU.
    //
    // When the tuList (above) size goes to zero (indicating that we are
    // between "root-level" TU's, we will set the subTulist's size to zero
    // as well.
    public ArrayList<TuListItem> subTuList = new ArrayList<TuListItem>();

    // Each time we encounter an end sub tag, the trans-unit element that
    // is constructed from the tuList entry (see above) that is removed from
    // the list will be written to the xliffAppendix. After we reach the
    // end of the trans-units in the input XLIFF file, the contents of the
    // xliffappendix will we written to the output XLIFF file. The XLIFF
    // appendix contains the trans-unit elements that have accumulated for
    // all the &lt;sub&gt; tags encountered during the reading of the input
    // XLIFF file.
    private StringBuilder xliffAppendix = new StringBuilder();
    
    /**
     * Constructor for the XLIFF importer. 
     */
    public XliffImporter() {
    }
    
    /**
     * Convert an XLIFF file to a reduced subset of XLIFF for storage within a
     * repository. Create a second XLIFF file (normalized to meet our internal
     * constraints), as well as skeleton and format files. (The skeleton and format
     * files are used on export to "un-normalize" our XLIFF and return it to the
     * format used by the original XLIFF.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary source language of the XLIFF file to be 
     *        imported. (This is the language of the &lt;source&gt; elements in
     *        the input XLIFF file.)
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XLIFF file--probably 
     *        UTF-8, but the XLIFF specification allows UTF-16 as well). If null,
     *        the encoding specified in the input XLIFF file's XML header is used.
     * @param nativeFileType The type of the input file. Must be XLIFF.
     * @param inputXliffFileName The name of the input XLIFF file.
     * @param baseDir The directory that contains the input XLIFF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff. (<i>Note:</i> If the input file
     *     already ends in ".xliff", the output file will end in ".xliff.xliff"</li>
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
            String inputXliffFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {

        if (! mode.equals(ConversionMode.TO_XLIFF)) {
            throw new ConversionException("XLIFF Importer supports only conversions"
                    + " from XLIFF to (normalized) XLIFF.");
        }
        
        if (inputXliffFileName == null || inputXliffFileName.length() == 0) {
            throw new ConversionException("Name of input XLIFF file omitted.");
        }

        if (language == null) {
            throw new ConversionException("Source language omitted. (Required)");
        }
        
        // Trust the encoding specified in the input XLIFF file over the encoding
        // specified in the input arguments.
        Charset encoding = getEncoding(baseDir + File.separator + inputXliffFileName);
        if (encoding == null) {
            encoding = nativeEncoding;  // Use what was specified by caller.
            if (encoding == null) {   // ... or just default to UTF-8
                encoding = Charset.forName("UTF-8");
            }
        }

        sourceLanguage = language;             // The input XLIFF file's primary language
        dataType = nativeFileType.toString();  // XLIFF is being imported
        originalFileName = inputXliffFileName;     // The name of the input XLIFF file
                
        // Create output stream writers for the SAX handler to write to.
        // We will store our output as UTF-8
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXliffFileName + Converter.xliffSuffix),
                    "UTF8");
            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXliffFileName + Converter.tSkeletonSuffix),
                    "UTF8");
            formatOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputXliffFileName + Converter.formatSuffix),
                    "UTF8");
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
        
        try {
            // Let's parse with the an XML Reader
            parser = XMLReaderFactory.createXMLReader();
            
            parser.setContentHandler(this); // We're gonna handle content ourself

            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);

            // The Reader prevents SAX from recognizing the byte-order mark (BOM)
            //Reader inReader = new InputStreamReader(new FileInputStream(
            //        baseDir + File.separator + inputXliffFileName), encoding);
            // Using an InputStream will let SAX read the BOM and detect the
            // proper encoding (Cool!)
            InputStream inStream = new FileInputStream(
                    baseDir + File.separator + inputXliffFileName);
            InputSource XliffIn = new InputSource(inStream);
            parser.parse(XliffIn);
        }
        catch(SAXException e) {
            System.err.println("XML parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("SAX parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading XLIFF input.");
            System.err.println("I/O error reading XLIFF input: " + e.getMessage());
        }

        try {
            /* Close the files we created above */
            xliffOut.close();
            tskeletonOut.close();
            formatOut.close();

            /* We have created a temp skeleton file (an intermediate skeleton file).
             * We now need to merge the temporary skeleton with the original input file to
             * yield a "real" skeleton */

            // We'll read from the temporary skeleton
            FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator
                    + inputXliffFileName + Converter.tSkeletonSuffix);

            // We'll also read from the original input file
            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator
                    + inputXliffFileName);   // This is the content.xml file

            // We'll write to the (final) skeleton file
            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator
                    + inputXliffFileName + Converter.skeletonSuffix);

            // The XliffSkeletonMerger will do the deed.
            SkeletonMerger merger = new XliffSkeletonMerger();

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
            generatedFileName.write(inputXliffFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert an XLIFF file to a reduced subset of XLIFF for storage within a
     * repository. Create a second XLIFF file (normalized to meet our internal
     * constraints), as well as skeleton and format files. (The skeleton and format
     * files are used on export to "un-normalize" our XLIFF and return it to the
     * format used by the original XLIFF.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary source language of the XLIFF file to be 
     *        imported. (This is the language of the &lt;source&gt; elements in
     *        the input XLIFF file.)
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XLIFF file--probably 
     *        UTF-8, but the XLIFF specification allows UTF-16 as well). If null,
     *        the encoding specified in the input XLIFF file's XML header is used.
     * @param nativeFileType The type of the input file. Must be XLIFF.
     * @param inputXliffFileName The name of the input XLIFF file.
     * @param baseDir The directory that contains the input XLIFF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff. (<i>Note:</i> If the input file
     *     already ends in ".xliff", the output file will end in ".xliff.xliff"</li>
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
     * @param skipList (Not used by this converter.)
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
            String inputXliffFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName,
            Set<XMLTuXPath> skipList) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputXliffFileName, baseDir, notifier, boundary,
                generatedFileName);
    }
    
    /**
     * Convert an XLIFF file to a reduced subset of XLIFF for storage within a
     * repository. Create a second XLIFF file (normalized to meet our internal
     * constraints), as well as skeleton and format files. (The skeleton and format
     * files are used on export to "un-normalize" our XLIFF and return it to the
     * format used by the original XLIFF.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary source language of the XLIFF file to be 
     *        imported. (This is the language of the &lt;source&gt; elements in
     *        the input XLIFF file.)
     * @param phaseName The name of the phase to convert. (This parameter is
     *        currently ignored by this importer.)
     * @param maxPhase The maximum phase number. This value is currently ignored.
     * @param nativeEncoding The encoding of the input XLIFF file--probably 
     *        UTF-8, but the XLIFF specification allows UTF-16 as well). If null,
     *        the encoding specified in the input XLIFF file's XML header is used.
     * @param nativeFileType The type of the input file. Must be XLIFF.
     * @param inputXliffFileName The name of the input XLIFF file.
     * @param baseDir The directory that contains the input XLIFF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff. (<i>Note:</i> If the input file
     *     already ends in ".xliff", the output file will end in ".xliff.xliff"</li>
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
            String inputXliffFileName,
            String baseDir,
            Notifier notifier) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputXliffFileName, baseDir, notifier, null, null);
    }

    /**
     * Method called by the SAX parser at the beginning of document parsing.
     * @throws org.xml.sax.SAXException
     *         I if any problems are found.
     */
    public void startDocument() throws SAXException {

        // Write the start of the output XLIFF document
        writeXliff(Converter.xmlDeclaration
                 + Converter.startXliff
                 + indent() + "<file original='" 
                       + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                       + "' source-language='" + sourceLanguage.toString() 
                       + "' datatype='xml'>\r\n"
                 + indent() + "<header>\r\n" 
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
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {

        // Use the source-language attribute to override what was passed in ... if it
        // is present.
        // Note: The XLIFF spec specifies that an XLIFF document can contain multiple
        // <file> elements. This importer's output has only one <file> element. (We
        // write our own <file> begin and end tag.)
        if (qualifiedName.equals("file")) {
            for (int i = 0; i < atts.getLength(); i++) {
                if (atts.getQName(i).equals("source-language")) {
                    sourceLanguage = stringToISOLocale(atts.getValue(i));
                    break;
                }
            }
        }
        
        // We will try writing only <trans-unit>, </trans-unit>, <source>, 
        // </source>, <target>, </target> and <lt:tu ...> tags to tskeleton.
        // Is this a trans-unit tag?
        else if (qualifiedName.equals("trans-unit")) {
            writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");

            tuList.add(new TuListItem());  // Add a new TU list entry.
            // The above will always be added at position 0 in the list. <sub> elements
            // encountered within the TU may add additional entries--temporarily--to
            // the list.
            
            // Get the new TU's UUID
            UUID curTuId = tuList.get(tuList.size() - 1).getTuID();
            
            // Start a new new TU element 
            // Write *our* TU-ID in our own namespace (so we can maintain the original)
            tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent(6) + "<trans-unit lt:tu-id='" 
                    + curTuId.toString() + "'");

            // Then preserve all the original TU's att/vals
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    String aVal = atts.getValue(i);
                    if (aVal != null) {
                        tuList.get(tuList.size() - 1).appendTransUnitElement(
                                " " + aName + "='" + aVal + "'");
                    }
                }
            }
            tuList.get(tuList.size() - 1).appendTransUnitElement(">\r\n");

            return;
        }
        
        else if (qualifiedName.equals("source")) {
            // It is possible that this source tag was encountered as the oldest
            // child of an alt-trans element. If so, then its sibling targets
            // (if they exist) will correspond to *this* source element--
            // not the *global* source element. We had better clear out any
            // left-over tuLists ...
            // (Note: alternately, we *could* see if this source's parent is
            // alt-trans; if it is, start out with an empty subTuList, (saving
            // the "global" subTuList for later)
            if (subTuList.size() > 0) {
                for (TuListItem e : subTuList) {
                    xliffAppendix.append(e.getTransUnitElement());
                
                    // The above don't have the closing </trans-unit> tag. ... So
                    // write one. 
                    xliffAppendix.append(indent(6) + "</trans-unit>\r\n");
                }
                subTuList.clear();
            }
            
            // Also clear out the source string (in case this is a child of <alt-trans>)
            // We don't want to keep appending to the main (global) source text for this
            // TU
            if (tuList.size() > 0) {
                tuList.get(tuList.size()-1).clearSourceText();
            }
            
            inSource = true;               // We are now in the source
            writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");
            
            tuList.get(tuList.size() - 1).appendTransUnitElement(indent('+') + "<source");
            // Then preserve all the original TU's att/vals
            boolean foundXmlLang = false;
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    if (aName.equals("xml:lang")) {
                        foundXmlLang = true;
                    }
                    String aVal = atts.getValue(i);
                    if (aVal != null) {
                        tuList.get(tuList.size() - 1).appendTransUnitElement(
                            " " + aName + "='" + aVal + "'");
                    }
                }
            }
            if (!foundXmlLang) {
                tuList.get(tuList.size() - 1).appendTransUnitElement(
                        " xml:lang='" + this.sourceLanguage + "'");
            }
            tuList.get(tuList.size() - 1).appendTransUnitElement(">");    // No trailing whitespace!!
            
            curTargetLang = null;     // Clear out previous target language
            
            return;
        }

        // The first target might not be within an alt-trans, subsequent ones
        // are supposed to, though (according to the XLIFF spec)
        else if (qualifiedName.equals("alt-trans")) {
            writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");
            
            curTargetLang = this.getLocale(atts);  // Whatever is current
            // Start an alt-trans element
            tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent('0') + "<alt-trans");

            // Then output the original alt-trans's att/vals
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    String aVal = atts.getValue(i);
                    if (aVal != null) {
                        tuList.get(tuList.size() - 1).appendTransUnitElement(
                            " " + aName + "='" + aVal + "'");
                    }
                }
            }
            tuList.get(tuList.size() - 1).appendTransUnitElement(">\r\n");

            return;
        }
        
        else if (qualifiedName.equals("target")) {
            inTarget = true;
            writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");

            // Note what the current target locale is (if appropriate).
            Locale tLocale = this.getLocale(atts);
            if (tLocale != null) {
                curTargetLang = tLocale;
            }

            // In case this trans-unit has multiple alt-transes with duplicate
            // targets, clear our the target text buffer
            if (tuList.size() > 0) {
                tuList.get(tuList.size()-1).clearTargetText(curTargetLang);
            }

            // Now write the <target> tag
            tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent('0') + "<target");
            // Followed by it target's att/vals:
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    String aVal = atts.getValue(i);
                    if (aVal != null) {
                        tuList.get(tuList.size() - 1).appendTransUnitElement(
                            " " + aName + "='" + aVal + "'");
                    }
                }
            }
            tuList.get(tuList.size() - 1).appendTransUnitElement(">");

            return;
        }
        
        if (!inSource && !inTarget) {
            return;                       // Not within TU text, so return
        }

        // I guess we're in TU text (<source> or <target>). We'll map 
        // source/target subtags as follows:
        
        //   x   -->  x
        //   bx  -->  bx
        //   ex  -->  ex
        //   bpt -->  bx  See p. 390 of Yves Savourel, XML I18N & L10N
        //   ept -->  ex           [ditto]  
        //   ph  -->  x
        //   sub -->  x  
        //   g   -->  bx (if opening; use ctype="x-xliff-g")
        //   g   -->  ex (if closing; no ctype on ex tags)
        //   mrk -->  bx (if opening; use ctype="x-xliff-mrk")
        //   mrk -->  ex (if closing; no ctype on ex tags)
        //   it  -->  bx (if pos attribute is "open")
        //   it  -->  ex (if pos attribute is "close")
        
        // First, x/bx/ex map to themselves
        if (qualifiedName.equals("x") 
                || qualifiedName.equals("bx")
                || qualifiedName.equals("ex")) {
            
            // We'll use our own home-grown x/bx/ex tags in our XLIFF,
            // but save the original in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            String ourCType = null;
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                    
                // We will use their ctype, if it there is one.
                if (aName.equals("ctype")) {
                    ourCType = attVal;
                }

                fmtStr.append(attVal + "\"");
            }

            fmtStr.append("/>");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now write "our" bx/ex/x tag to XLIFF
            appendTuText("<" + qualifiedName + " id='" + bxExXId + "'");
            if (qualifiedName.equals("bx")) {
                // bx tags have reference ID attrs that match the rid of the
                // "closing" (matching) ex tag.
                appendTuText(" rid='" + nextAvailRid + "'");
                
                // Stack the rid for popping by matching ex
                ridStack.push(Integer.valueOf(nextAvailRid));
                nextAvailRid++;
            }
            else if (qualifiedName.equals("ex")) {
                // Pop the rid value pushed by bx earlier
                appendTuText(" rid='" + ridStack.pop().toString() + "'");
            }
            
            // If original XLIFF had a ctype, use it. (ex's don't have ctypes ...)
            if (!qualifiedName.equals("ex")) {
                if (ourCType != null) {
                    appendTuText(" ctype='" + ourCType + "'");
                }
                else {    // Make up our own ctype
                    appendTuText(" ctype='x-xliff-" + qualifiedName + "'");
                }
            }

            // Finally (!!), close the x/bx/ex tag
            appendTuText("/>");
            
            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }

        // This is the beginning of a "begin paired tag"
        /* 
         * bpt and ept are used something like this, according to Y. Savourel,
         * p. 390:
         * The <bpt id='1' rid='1' ctype='bold'>&lt;b></bpt>big
         *  <bpt id='2' rid='2' ctype='italic'>&lt;i></bpt>black
         *  <ept id='3' rid='2'>&lt;/i></ept><ept id='4' rid='1'>&lt;/b>
         *  </ept> dog runs fast.
         *
         * The above bpt/ept tags *could* map as follows:
         * <bpt id='1' rid='1' ctype='bold'>&lt;b></bpt>   --> <bx id='1' rid='1' ctype='bold'/>
         * <bpt id='2' rid='2' ctype='italic'>&lt;i></bpt> --> <bx id='2' rid='2' ctype='italic'/>
         * <ept id='3' rid='2'>&lt;/i></ept>               --> <ex id='3' rid='2'/>
         * <ept id='4' rid='1'>&lt;/b></ept>               --> <ex id='4' rid='1'/>
         * 
         */
        
        else if (qualifiedName.equals("bpt")) {  // Begin Paired Tag
            tuList.get(tuList.size() - 1).tagStack.push("bpt");         // The beginning of a bpt element
            tuList.get(tuList.size() - 1).bptBuffer.append("<" + qualifiedName);

            // Save bpt's attributes for storing in the format file.
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                tuList.get(tuList.size() - 1).bptBuffer.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                tuList.get(tuList.size() - 1).bptBuffer.append(attVal + "\"");
                
                if (aName.equals("ctype")) {
                    // We'll preserve their bpt's ctype in our bx tag
                    tuList.get(tuList.size() - 1).bptCType = attVal;
                }
            }

            tuList.get(tuList.size() - 1).bptBuffer.append(">");
        }

        // This is the beginning of an "end paired tag"
        else if (qualifiedName.equals("ept")) {
            tuList.get(tuList.size() - 1).tagStack.push("ept");         // The beginning of an ept element
            tuList.get(tuList.size() - 1).eptBuffer.append("<" + qualifiedName);

            // Save bpt's attributes for storing in the format file.
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                tuList.get(tuList.size() - 1).eptBuffer.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                tuList.get(tuList.size() - 1).eptBuffer.append(attVal + "\"");
            }

            tuList.get(tuList.size() - 1).eptBuffer.append(">");
        }
        
        // This is the beginning of an "place holder" tag
        // "The <ph> element is used to delimit a sequence of native stand-alone
        // codes in the translation unit." --XLIFF Spec
        else if (qualifiedName.equals("ph")) {  // Begin place holder
            tuList.get(tuList.size() - 1).tagStack.push("ph");         // The beginning of a ph element
            tuList.get(tuList.size() - 1).phBuffer.append("<" + qualifiedName);

            // Save bpt's attributes for storing in the format file.
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                tuList.get(tuList.size() - 1).phBuffer.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                tuList.get(tuList.size() - 1).phBuffer.append(attVal + "\"");
                
                if (aName.equals("ctype")) {
                    // We'll preserve their ph's ctype in our x tag
                    tuList.get(tuList.size() - 1).phCType = attVal;
                }
            }

            tuList.get(tuList.size() - 1).phBuffer.append(">");
        }

        // This is the beginning of an "isolated tag"
        // "The <it> element is used to delimit a beginning/ending sequence of 
        // native codes that does not have its corresponding ending/beginning 
        // within the translation unit. ... The required pos attribute specifies 
        // whether this is the begin or end code." --XLIFF Spec
        //
        // We will map to an x tag
        else if (qualifiedName.equals("it")) {  // Begin isolated tag
            tuList.get(tuList.size() - 1).tagStack.push("it");         // The beginning of a ph element
            tuList.get(tuList.size() - 1).itBuffer.append("<" + qualifiedName);

            // Save it's attributes for storing in the format file.
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                tuList.get(tuList.size() - 1).itBuffer.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                tuList.get(tuList.size() - 1).itBuffer.append(attVal + "\"");
                
                if (aName.equals("ctype")) {
                    // We'll preserve their it's ctype in our bx or ex tag
                    tuList.get(tuList.size() - 1).itCType = attVal;
                }
                
                // The pos value can be "open" or "close"
                else if (aName.equals("pos")) {
                    // We'll preserve their ph's ctype in our x tag
                    tuList.get(tuList.size() - 1).itPos = attVal; // "open" --> bx; "close" --> ex
                }
            }

            tuList.get(tuList.size() - 1).phBuffer.append(">");
        }

        // This is the beginning of a "sub-flow" tag
        // "The <sub> element is used to delimit sub-flow text inside a sequence of 
        // native code, for example: the definition of a footnote or the text of 
        // a title attribute in a HTML <a> element." --XLIFF Spec
        //
        // According to the XLIFF spec, <sub> *cannot* appear as a child of
        // <source> or <target>--but rather, solely, as a child of bpt, ept, ph 
        // and it.  
        //
        // Our implementation will handle <sub> similar to the way the ODF importer
        // handles text:note's: We will replace the opening/closing <sub> ... </sub>
        // (within its parent--non-source, non-target--element in the format
        // file) with an <x/> tag with an xid attribute that references a new
        // trans-unit that we will create for the text within the <sub> tag.
        // Then we will append a newly-constructed <trans-unit> at the end of
        // the output XLIFF. 
        
        // (Note: Creating a separate TU for a subelement is alluded to by Yves 
        // Savourel in <i>XML Internationalization and Localization</i> (SAMS,
        // c2001, ISBN 0-672-32096-7), p. 390. Regarding the <sub> element, he
        // writes: "You might consider going a step further and creating a
        // specific <trans-unit> for this type of text as well.")
        // 
        
        else if (qualifiedName.equals("sub")) {  // Begin place holder
            
            UUID curTuId = null;
    
            // Can we assume that if the source has a sub element all the
            // targets will have a corresponding sub element? (It makes sense
            // for that to be the case.)
            // If we're in the source, then we can create a new TU for the source
            // Otherwise we need to append to a partially completed one.
            if (inSource) {
                tuList.add(new TuListItem());
                
                // Get the TU ID of this new TU
                UUID subTuId = tuList.get(tuList.size() - 1).getTuID();
                
                // Start a new new TU element
                // Since *we* are creating our own TU "from scratch" (well, from
                // a <sub> element), our TU UUID can't conflict with a pre-existing
                // TU ID; Use the "standard" id attribute name.
                tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent(6) + "<trans-unit id='" 
                    + subTuId.toString() + "'");
                    // The indention will probably be skewed a bit, but that is
                    // probably OK, since we will be appending this TU to the
                    // end of the output XLIFF... and not worth the trouble of
                    // pretty printing at the moment.
                tuList.get(tuList.size() - 1).appendTransUnitElement(">\r\n");
                // This TU element (introduced above) will have only one attribute
                // at the moment. ... Maybe more in the future?
                
                // Now write an opening source tag:
                tuList.get(tuList.size() - 1).appendTransUnitElement(indent('+') + "<source"
                    + " xml:lang='" + this.sourceLanguage + "'>");  // No trailing white space!
                
            }
            
            // If we are in source, reuse a sub TU created when we were in the
            // source. (This assumes that for every sub element in the source there
            // is also a sub tag in the target.)
            else if (inTarget && subTuList.size() > 0) {
                tuList.add(subTuList.get(0));
                subTuList.remove(0);
                
                // We have reinstated (at the end of the tuList) the subTu thet we
                // created earlier when we were inSource. We assume that the source
                // tag has been closed. We will open a target tag as a child of
                // an alt-trans tag.
                tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent('0') + "<alt-trans" 
                        + " xml:lang='" + this.curTargetLang + "'>\r\n");

                // Now write an opening target tag (no trailing whitespace!)
                tuList.get(tuList.size() - 1).appendTransUnitElement(indent('+') + "<target>");
            }
            
            else {
                // Do some stuff and return
                // ########################
                return;
            }
            
            // Get the new TU's UUID
            curTuId = tuList.get(tuList.size() - 1).getTuID();
            
            // Now we need to construct something for the format file--something
            // that maps an x tag (which we will add to the *parent* TU) to
            // the sub element that we will put in the format file.
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            String ourCType = null;

            // Save sub's attributes for storing in the format file.
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }
                fmtStr.append(attVal + "\"");
                
                if (aName.equals("ctype")) {
                    // We'll preserve their sub's ctype in our x tag
                    ourCType = attVal;
                }
            }

            fmtStr.append(">");
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            bxExXId++;
            // We need to leave a way for the Exporter to know to retrieve
            // a TU when it is substituting the original codes from the
            // format file and inserting them back into the skeleton.
            // We will add an empty TU tag that references the UUID of
            // the TU we are adding.
            fmtStr.append("<lt:tu id=\"" + curTuId.toString() + "\"");
            
            if (inTarget) {     // If locale not specified, Exporter assumes source
                fmtStr.append(" locale=\"" + curTargetLang.toString() + "\"");
            }
            fmtStr.append("/>");
            
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now we need to insert an X tag in the parent TU--in place of
            // the sub tag
            // Now write "our" bx/ex/x tag to XLIFF
            // "true" in second argument means to append to the parent of the
            // current TU.
            appendToFormatString("<x id='" + (bxExXId-1) + "' xid='" 
                    + curTuId.toString() + "' ctype='x-xliff-sub'/>", true);

            bxExXId++;
        }

        // This is the beginning of a "generic group placeholder" tag
        // The <g> </g> tags map 1-to-1 to <bx/> and </ex> tags in the
        // Source/target. 
        else if (qualifiedName.equals("g")) {
            // Don't stack this on the tagStack. Otherwise it will cause
            // us to write what is between the <g> and </g> as format *codes*
            // (which is bogus, since g tags enclose text)
            
            // We'll use bx in our XLIFF,
            // but save the original in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            String ourCType = null;
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }

                // We will use their ctype, if it there is one.
                if (aName.equals("ctype")) {
                    ourCType = attVal;
                }
                
                fmtStr.append(attVal + "\"");
            }

            fmtStr.append(">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now write "our" bx/ex/x tag to XLIFF
            appendTuText("<bx id='" + bxExXId + "'");
            // bx tags have reference ID attrs that match the rid of the
            // "closing" (matching) ex tag.
            appendTuText(" rid='" + nextAvailRid + "'");
                
            // Stack the rid for popping by matching ex
            ridStack.push(Integer.valueOf(nextAvailRid));
            nextAvailRid++;
            
            if (ourCType != null) {
                appendTuText(" ctype='" + ourCType + "'");
            }
            else {    // Make up our own ctype
                appendTuText(" ctype='x-xliff-g'");
            }

            // Finally (!!), close the bx tag
            appendTuText("/>");
            
            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }   

        // This is the beginning of a marker tag
        // The <mrk> </mrk> tags map 1-to-1 to <bx/> and </ex> tags in the
        // Source/target. 
        else if (qualifiedName.equals("mrk")) {
            
            // We'll use bx in our XLIFF,
            // but save the original in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            String mType = "";
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }

                // Save the (required) mtype value
                if (aName.equals("mtype")) {
                    mType = attVal;
                }
                
                fmtStr.append(attVal + "\"");
            }

            fmtStr.append(">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now write "our" bx/ex/x tag to XLIFF
            appendTuText("<bx id='" + bxExXId + "'");
            // bx tags have reference ID attrs that match the rid of the
            // "closing" (matching) ex tag.
            appendTuText(" rid='" + nextAvailRid + "'");
                
            // Stack the rid for popping by matching ex
            ridStack.push(Integer.valueOf(nextAvailRid));
            nextAvailRid++;
            
            appendTuText(" ctype='x-xliff-mrk-mtype-" + mType + "'");

            // Finally (!!), close the bx tag
            appendTuText("/>");
            
            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }   
        
        // If we're still here, it can only mean that we have encountered a
        // tag that is in some other namespace (doesn't it?)
        
        // If the qualified name doesn't include a colon (:), throw a conversion
        // exception--this is bogus XLIFF
        else if (!qualifiedName.contains(":")) {
            throw new SAXException("Input XLIFF contains the invalid inline element "
                    + qualifiedName);
        }
        
        // It looks like the qualified name includes a colon. Let us just
        // map it to a bx code and accept it.
        else {  // It must be in some other namespace?
            // We'll use bx in our XLIFF,
            // but save the original in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            for (int i = 0; i < atts.getLength(); i++) {
                String aName = atts.getQName(i); // Attr name
                fmtStr.append(" " + aName + "=\""); // Write attr=
                String attVal = atts.getValue(i);
                    
                if (attVal == null) {  // Shouldn't ever happen (sure! :-)
                    attVal = "";
                }

                fmtStr.append(attVal + "\"");
            }

            fmtStr.append(">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<bx id='" + bxExXId + "'");
            // bx tags have reference ID attrs that match the rid of the
            // "closing" (matching) ex tag.
            appendTuText(" rid='" + nextAvailRid + "'");
                
            // Stack the rid for popping by matching ex
            ridStack.push(Integer.valueOf(nextAvailRid));
            nextAvailRid++;
            
            appendTuText(" ctype='x-xliff-" + qualifiedName + "'");

            // Finally (!!), close the bx tag
            appendTuText("/>");
            
            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
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

        if (qualifiedName.equals("body")) {
            // We're finished with all the translation units in this body.
            // Each <file> element has at most one <body> element. However
            // an <xliff> document can have multiple <file> children.
            // Let's write out any <sub> TUs now, and clear out the 
            // xliffAppendix buffer for a possible next <file> element.
            if (xliffAppendix.length() > 0) {
                writeXliff(xliffAppendix.toString());
                xliffAppendix.setLength(0);
            }
        }
        
        else if (qualifiedName.equals("trans-unit")) {
            writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");
            tuList.get(tuList.size() - 1).appendTransUnitElement(
                    indent(6) + "</trans-unit>\r\n");
            
            // Now write the trans-unit from the top of the tuList to the
            // output XLIFF and remove the TU from the end/top of the list of
            // TUs
            writeXliff(this.tuList.get(tuList.size() - 1).getTransUnitElement());
            
            // We're done with this TU list entry; remove it.  In fact, a trans-unit
            // end tag indicates that the size of the tuList should be zero. 
            // Let's just clear it out.
            //tuList.remove(tuList.size() - 1);
            tuList.clear();
            
            // Let's clear out the subTuList as well--after we write all the
            // sub-generated TUs to the xliffAppendix
            for (TuListItem e : subTuList) {
                xliffAppendix.append(e.getTransUnitElement());
                
                // The above don't have the closing </trans-unit> tag. ... So
                // write one. 
                xliffAppendix.append(indent(6) + "</trans-unit>\r\n");
            }
            subTuList.clear();
            
            return;
        }
        
        else if (qualifiedName.equals("source")) {
            
            // Write the text in the source buffer to the trans-unit element, 
            // preening it first.
            tuList.get(tuList.size() -1).appendTransUnitElement(
                TuPreener.markCoreTu(tuList.get(tuList.size() -1).getSourceText()));

            // Then write the source end tag that marks the end of the source element
            tuList.get(tuList.size() -1).appendTransUnitElement("</source>\r\n");  // No leading white space!

            // Write to the intermediate skeleton file
            writeTSkeleton("<tu id='" + tuList.get(tuList.size() -1).getTuID().toString()
                + "' istarget='no' xml:lang='" + sourceLanguage + "'>\r\n");
            writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");
            inSource = false;              // No longer in source  
            return;
        }

        else if (qualifiedName.equals("alt-trans")) {
            writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");
            
            // Write the end alt-trans tag that marks the end of the alt-trans element
            tuList.get(tuList.size() -1).appendTransUnitElement(indent('-') + "</alt-trans>\r\n");
            return;
        }

        else if (qualifiedName.equals("target")) {
            
            // Write the text in the current target buffer to the trans-unit element,
            // preening it first.
            tuList.get(tuList.size() -1).appendTransUnitElement(
                TuPreener.markCoreTu(tuList.get(tuList.size() -1).getTargetText(this.curTargetLang)));
            
            // Then write the target end tag that marks the end of the target element
            tuList.get(tuList.size() -1).appendTransUnitElement("</target>\r\n");  // No leading white space!

            // Write to the intermediate skeleton file
            writeTSkeleton("<tu id='" + tuList.get(tuList.size() -1).getTuID().toString()
                + "' istarget='yes' xml:lang='" 
                + curTargetLang.toString() + "'>\r\n");
            writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "'>\r\n");

            inTarget = false;
            return;
        }
        
        if (!inSource && !inTarget) {
            return;                       // Not within TU text, so return
        }
        
        
        // I N L I N E   T A G S :
        //
        // Ignore end x/bx/ex tags, because they don't really appear in the XLIFF. 
        // (They are always "empty" tags. We already closed them off/handled them/
        // etc., above in the startElement method (above).
        if (qualifiedName.equals("x") 
                || qualifiedName.equals("bx")
                || qualifiedName.equals("ex")) {
            return;
        }

        // This is the end of a "begin paired tag"
        if (qualifiedName.equals("bpt")) {
            tuList.get(tuList.size() - 1).bptBuffer.append("</" + qualifiedName + ">");

            // Write the bx tag that maps to the bpt to Xliff
            appendTuText("<bx id='" + bxExXId + "'");
            if (tuList.get(tuList.size() - 1).bptCType != null) {
                appendTuText(" ctype='" + tuList.get(tuList.size() - 1).bptCType + "'");
            }
            else {
                appendTuText(" ctype='x-xliff-bpt'");
            }
            appendTuText(" rid='" + nextAvailRid + "'/>");
            
            // It may seem strange to push a rid onto the stack in *end* element,
            // but the <bpt>...</bpt> element is really the *beginning* of a
            // pair is tags. (This pair maps to bx.)
            ridStack.push(Integer.valueOf(nextAvailRid)); //Hmmm... I wonder if this will always work ...
            nextAvailRid++;      
            
            // Write format file ...
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + tuList.get(tuList.size() - 1).bptBuffer + "]]></tag>\r\n");
            
            tuList.get(tuList.size() - 1).bptBuffer.setLength(0);       // Clear out the buffer
            bxExXId++;      // For next time (Each bx and ex has a unique id attr
            tuList.get(tuList.size() - 1).bptCType = null;
            if (tuList.get(tuList.size() - 1).tagStack.peek().equals("bpt")) {
                tuList.get(tuList.size() - 1).tagStack.pop();        
            }
            else {
                System.err.println("tagStack appears corrupted!! Top of stack"
                    + " is " + tuList.get(tuList.size() - 1).tagStack.peek() + " rather than expected bpt");
            }
        }

        // This is the end of an "end paired tag"
        else if (qualifiedName.equals("ept")) {
            tuList.get(tuList.size() - 1).eptBuffer.append("</" + qualifiedName + ">");
            
            // Write the bx tag that maps to the bpt to the TU buffer
            appendTuText("<ex id='" + bxExXId + "'");
            appendTuText(" rid='" + ridStack.pop().toString() + "'/>");
            
            // Write format file ...
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + tuList.get(tuList.size() - 1).eptBuffer + "]]></tag>\r\n");
            
            tuList.get(tuList.size() - 1).eptBuffer.setLength(0);       // Clear out the buffer
            bxExXId++;      // For next time (Each bx and ex has a unique id attr
            if (tuList.get(tuList.size() - 1).tagStack.peek().equals("ept")) {
                tuList.get(tuList.size() - 1).tagStack.pop();           // The beginning of a sub element
            }
            else {
                System.err.println("tagStack appears corrupted!! Top of stack"
                    + " is " + tuList.get(tuList.size() - 1).tagStack.peek() + " rather than expected ept");
            }
        }

        // This is the end of a "place holder" tag
        else if (qualifiedName.equals("ph")) {
            tuList.get(tuList.size() - 1).phBuffer.append("</" + qualifiedName + ">");

            // Write the x tag that maps to the ph to the TU buffer
            appendTuText("<x id='" + bxExXId + "'");
            if (tuList.get(tuList.size() - 1).phCType != null) {
                appendTuText(" ctype='" + tuList.get(tuList.size() - 1).phCType + "'");
            }
            else {
                appendTuText(" ctype='x-xliff-ph'");
            }
            appendTuText("/>");
            
            // Write format file ...
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + tuList.get(tuList.size() - 1).phBuffer + "]]></tag>\r\n");
            
            tuList.get(tuList.size() - 1).phBuffer.setLength(0);       // Clear out the buffer
            bxExXId++;      // For next time (Each bx and ex has a unique id attr
            tuList.get(tuList.size() - 1).phCType = null;
            if (tuList.get(tuList.size() - 1).tagStack.peek().equals("ph")) {
                tuList.get(tuList.size() - 1).tagStack.pop();           // The beginning of a sub element
            }
            else {
                System.err.println("tagStack appears corrupted!! Top of stack"
                    + " is " + tuList.get(tuList.size() - 1).tagStack.peek() + " rather than expected ph");
            }
        }

        // This is the end of an "isolated tag"
        else if (qualifiedName.equals("it")) {
            tuList.get(tuList.size() - 1).itBuffer.append("</" + qualifiedName + ">");

            // Maps to an x tag
            appendTuText("<x");
            
            appendTuText(" id='" + bxExXId + "'");
            
            if (tuList.get(tuList.size() - 1).itCType != null) {
                appendTuText(" ctype='" + tuList.get(tuList.size() - 1).itCType + "'");
            }
            else { // Concoct a ctype that includes tag name and pos attr
                appendTuText(" ctype='x-xliff-it-" + tuList.get(tuList.size() - 1).itPos + "'");
            }
            appendTuText("/>");
            
            // Write format file ...
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + tuList.get(tuList.size() - 1).itBuffer + "]]></tag>\r\n");
            
            tuList.get(tuList.size() - 1).itBuffer.setLength(0);       // Clear out the buffer
            bxExXId++;      // For next time (Each bx and ex has a unique id attr
            tuList.get(tuList.size() - 1).itCType = null;
            if (tuList.get(tuList.size() - 1).tagStack.peek().equals("it")) {
                tuList.get(tuList.size() - 1).tagStack.pop();           // The beginning of a sub element
            }
            else {
                System.err.println("tagStack appears corrupted!! Top of stack"
                    + " is " + tuList.get(tuList.size() - 1).tagStack.peek() + " rather than expected it");
            }
        }

        // This is the end of a "sub-flow" tag
        else if (qualifiedName.equals("sub")) {
            if (inSource && (tuList.size() > 0)) {  // size() check is a sanity check
                // Write the accumulated source to the TU element so far
                tuList.get(tuList.size() -1).appendTransUnitElement(
                    TuPreener.markCoreTu(tuList.get(tuList.size() -1).getSourceText()));
                // Then write the source end tag that marks the end of the source element
                tuList.get(tuList.size() -1).appendTransUnitElement("</source>\r\n");  // No leading white space!
                
                // Remove the top TuListItem from the tuList and insert it at the bottom
                // of the subTuList for possible later use by sub elements of targets
                // within this same TU
                subTuList.add(0, tuList.get(tuList.size() -1));
                tuList.remove(tuList.size() -1);

            }
            else if (inTarget && (tuList.size() > 0)) {
                // Write the accumulated target to the TU element so far
                tuList.get(tuList.size() -1).appendTransUnitElement(
                    TuPreener.markCoreTu(tuList.get(tuList.size() -1).getTargetText(curTargetLang)));
                // Then write the source end tag that marks the end of the source element
                tuList.get(tuList.size() -1).appendTransUnitElement("</target>\r\n");  // No leading white space!
                
                // ... as well as the end alt-trans tag.
                tuList.get(tuList.size() -1).appendTransUnitElement(indent('-') + "</alt-trans>\r\n");
                
                // Remove the top TuListItem from the tuList and insert it at the bottom
                // of the subTuList for possible later use by sub elements of targets
                // within this same TU
                subTuList.add(0, tuList.get(tuList.size() -1));
                tuList.remove(tuList.size() -1);
                
                // Now remove sub from the tag stack
                tuList.get(tuList.size() - 1).itCType = null;
                if (tuList.get(tuList.size() - 1).tagStack.peek().equals("sub")) {
                    tuList.get(tuList.size() - 1).tagStack.pop();           // The beginning of a sub element
                }
                else {
                    System.err.println("tagStack appears corrupted!! Top of stack"
                        + " is " + tuList.get(tuList.size() - 1).tagStack.peek() 
                        + " rather than expected sub");
                }
            }
        }

        // This is the end of a "generic group placeholder" tag
        // The <g> </g> tags map 1-to-1 to <bx/> and </ex> tags in the
        // Source/target. 
        else if (qualifiedName.equals("g")) {
            
            // We'll use ex in our XLIFF,
            // but save the original end tag (just </g>) in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("</" + qualifiedName + ">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<ex id='" + bxExXId + "'");
            appendTuText(" rid='" + ridStack.pop().toString() + "'/>");

            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }

        // This is the end of a marker tag
        // The <mrk> </mrk> tags map 1-to-1 to <bx/> and </ex> tags in the
        // Source/target. 
        else if (qualifiedName.equals("mrk")) {
            
            // We'll use ex in our XLIFF,
            // but save the original end tag (just </mrk>) in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("</" + qualifiedName + ">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<ex id='" + bxExXId + "'");
            appendTuText(" rid='" + ridStack.pop().toString() + "'/>");

            // Bookkeeping at the end.
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }

        // If we're still here, it can only mean that we have encountered a
        // tag that is in some other namespace (doesn't it?)
        
        // If the qualified name doesn't include a colon (:), throw a conversion
        // exception--this is bogus XLIFF
        else if (!qualifiedName.contains(":")) {
            throw new SAXException("Input XLIFF contains the invalid inline element "
                    + qualifiedName);
        }
        
        // It looks like the qualified name includes a colon. Let us just
        // map it to an ex element and accept it.
        else {  // It must be in some other namespace?
            // We'll use bx in our XLIFF,
            // but save the original in the format file.
            // Construct the string for the format file.
            StringBuilder fmtStr = new StringBuilder("</" + qualifiedName);

            fmtStr.append(">");

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\r\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now append "our" bx/ex/x tag to the candidateTu buffer
            appendTuText("<ex id='" + bxExXId + "'");
            // bx tags have reference ID attrs that match the rid of the
            // "closing" (matching) ex tag.
            appendTuText(" rid='" + ridStack.pop().toString() + "'/>");

            bxExXId++;              // Increment id val for next x, bx, ex tags.
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

        // If this/these character/s represent an entity, don't output
        // it/them--the startEntity()/endEntity methods (below) will output
        // the entities.
        if (inEntity) {
            return;
        }
        
        // We're in an element whose codes are left inline. (We need to store
        // those codes in the format file, rather than in the import results
        if ((tuList.size() > 0) &&
                tuList.get(tuList.size() - 1).tagStack.size() > 0) {
            appendToFormatString(new String(ch,start,length));
        }            
        else {
        // Write to the candidate trans-unit buffer
            appendTuText(new String(ch, start, length));
        }
    }

    /**
     * When the end-of-document is encountered, write what follows the final
     * translation unit.
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
     */
    public void startEntity(String name)
        throws SAXException {
        
        inEntity = true;

        appendTuText("&amp;" + name);
    }
    
    /**
     * Method that the SAX parser calls whenever it reaches the end of an entity 
     * (e.g. gt, lt, apos, ...). See comments for startEntity (above) for more
     * information on how this works.
     * @param name The name of the entity (e.g. "lt", "gt", etc.--without a
     *             leading ampersand or trailing semicolon.)
     */
    public void endEntity(String name) throws SAXException {

        appendTuText(";");
        inEntity = false;
    }

    /** Method defined by the LexicalHandler interface that we don't care about. */
    public void startDTD(String name, String publicId, String systemId)
        throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. */
    public void endDTD() throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. */
    public void startCDATA() throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. */
    public void endCDATA() throws SAXException {}

    /** Method defined by the LexicalHandler interface that we don't care about. */
    public void comment (char[] text, int start, int length) throws SAXException {}
    
    
    /** 
     * Passed a string from an XLIFF formatting element, append it to the 
     * correct temporary buffer that will eventually be written to the format
     * file.
     * @param text Text to append..
     */
    private void appendToFormatString(String text) {
        this.appendToFormatString(text, false);
    }

        /** 
     * Passed a string from an XLIFF formatting element, append it to the 
     * correct temporary buffer that will eventually be written to the format
     * file.
     * @param text Text to append..
     */
    private void appendToFormatString(String text, boolean isParent) {

        int offset = isParent ? 2 : 1;  // Amount to subtrace from size()

        if (tuList.size() < offset) {
            System.err.println("In appendToFormatString: tuList.size() is smaller than offset.");
            return;
        }
        
        if (!(tuList.get(tuList.size() - offset).tagStack.empty())) {
            String curTag = tuList.get(tuList.size() - offset).tagStack.peek();
            // If we're in the middle of a "Begin Paired Tag," write the chars there
            if (curTag.equals("bpt")) {
                tuList.get(tuList.size() - offset).bptBuffer.append(text);
            }

            // If we're in the middle of an "End Paired Tag," write the chars there
            else if (curTag.equals("ept")) {
                tuList.get(tuList.size() - offset).eptBuffer.append(text);
            }
        
            // If we're in the middle of a "Place Holder" tag, write the chars there
            else if (curTag.equals("ph")) {
                tuList.get(tuList.size() - offset).phBuffer.append(text);
            }
            
            // If we're in the middle of a "Generic group placeholder" tag, write the chars there
            else if (curTag.equals("it")) {
                tuList.get(tuList.size() - offset).itBuffer.append(text);
            }
            
            else {
                System.err.println("Unexpected tag " + curTag + " in tagStack!");
            }
        }
    }
        
    /**
     * Passed a character string encountered during traversal of either the 
     * <source> or one of the <target> elements of a <trans-unit>, write those
     * characters to the appropriate place.
     * @param text The text to append somewhere
     */
    private void appendTuText(String text) {
        // Second argument of false means that this text is to be appended
        // to the TU at the end of the TuList (not to the parent of that TU).
        this.appendTuText(text, false);
    }

    /**
     * Passed a character string encountered during traversal of either the 
     * <source> or one of the <target> elements of a <trans-unit>, write those
     * characters to the specified location in the tuList. 
     * @param text The text to append
     * @param isParent If true, append the text to the TU immediately before
     *        the TU at the end of the TU list, else append the text to the
     *        TU at the end of the list.
     */
    private void appendTuText(String text, boolean isParent) {
        if ((text == null) || (text.length() == 0)) {
            return;
        }

        int offset = isParent ? 2 : 1;  // Amount to subtrace from size()
        
        if (inSource && (tuList.size() >= offset)) {
            tuList.get(tuList.size() - offset).appendSourceText(text);
        }
        
        else if (curTargetLang == null) {
            // Not enough information to select the target
            return;
        }
        
        else if (inTarget && (tuList.size() >= offset)) {
            tuList.get(tuList.size() - offset).appendTargetText(text, curTargetLang);
        }
        
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
     * Passed the absolute name of an XML file, read the XML header and get the
     * encoding. Return it as a Charset object. If the encoding isn't specified
     * in the XML header, return null.
     * @return The encoding used by the XML file,
     */
    private Charset getEncoding(String fileName) throws ConversionException {
        Charset encoding = null;
        String charsetStr = "";       // Holds what we read from the XML header
        
        // Pattern for the encoding in the xml header
        String encodingPatt = "^<\\?xml .*?encoding=['\"]([^'\"]*)['\"]";
        Pattern encodingPattern = Pattern.compile(encodingPatt, Pattern.DOTALL);
        
        try {
            BufferedReader xliffIn = new BufferedReader(new InputStreamReader(new FileInputStream(
                fileName)));

            // Should--must!--be in the first line, without any leading whitespace,
            // etc. (if it is present at all).
            String line = xliffIn.readLine();

            if ((line != null) && (line.length() > 0)) {
                Matcher n = encodingPattern.matcher(line);
                if (n.find()) {
                    charsetStr = n.group(1);
                }
            }
            // We're done now.
            xliffIn.close();     // Close before leaving.
        }
        catch (IOException e) {
            System.err.println("Error reading XLIFF file: " + e.getMessage());
            throw new ConversionException("Error reading XLIFF file: " 
                    + e.getMessage());
        }

        if ((charsetStr != null) && (charsetStr.length() > 0)) {
            encoding = Charset.forName(charsetStr);
        }
        
        return encoding;   // Return what we found, or null if emptyhanded.
    }

    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the XLIFF file type.  (Note: This is an anomaly ...)
     */
    public FileType getFileType() {
        return FileType.XLIFF;
    }

    /** 
     * Passed an element's attribute list, look for the xml:lang attribute's
     * value. If not found, return null.
     * @param atts The attribute list to search for xml:lang 
     * @return The locale corresponding to the language; if the xml:lang attribute
     *         is not found in the list, return null.
     */ 
    private Locale getLocale(Attributes atts) {
        Locale theLocale = null;
        
        for (int i = 0; i < atts.getLength() ; i++) {
            String aName = atts.getQName(i); // Attr name
            if (aName.equals("xml:lang")) {
                theLocale = stringToISOLocale(atts.getValue(i));
                break;
            }
        }
        
        return theLocale;
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
     * Convert a string to an ISO-compliant locale.
     * (Freely plagiarized from Brian Hawkins.)
     * @param langString String version of a language identifier
     * @return A Locale that represents that language identifier.
     */
    
    private Locale stringToISOLocale(String langString) {
        if (langString == null || langString.length() == 0) {
            return null;
        }
		
        String[] split = langString.split("_");
        if (split.length == 3) {
            return (new Locale(split[0], split[1], split[2]));
        }
        else if (split.length == 2) {
            return (new Locale(split[0], split[1]));
        }
        else {
            return (new Locale(split[0]));
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
                tskeletonOut.write(text);
                tskeletonOut.flush();    // For debugging
            }
            catch(IOException e) {
                System.err.println("Error writing tskeleton file.");
                System.err.println(e.getMessage());
            }        
        }
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
