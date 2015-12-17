/*
 * XMLExporter.java
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

import f2xutils.*;
import java.io.*;
import java.util.*;
import java.nio.charset.*;
import java.util.regex.*;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Class to export an XLIFF target to an XML document in the same format as the
 * original XML source document.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XMLExporter implements Converter {

    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    private Format format;          // Maps bx/ex etc. to original format characters.

    private Matcher multipleTuMatcher = Pattern.compile("^\\s*(tu|format):([-\\w]+)(.*)$").matcher("");
    
    private Matcher fmtMatcher = 
            Pattern.compile("<[be]?x\\b.*?\\bid=['\"]([^'\"]+)['\"].*?>").matcher("");

//    private StringBuilder expansionBuf = new StringBuilder();
    private BufferedWriter outWriter;  // To write exports to.

    
    /** Creates a new instance of XMLExporter */
    public XMLExporter() { }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original XML format. Use (besides the XLIFF file) the skeleton and 
     * format files that were generated when the XLIFF file was created.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the XML document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified,
     *        the output file name will include the substring ja_JP in its name.
     * @param phaseName The name of the phase to export. If this parameter's
     *        value is not null, it is matched against the value of the 
     *        optional phase-name attribute of the target elements of the
     *        XLIFF document. If null, no check is made against a phase-name
     *        attribute.
     *        <p>If the phase name string consists entirely of numeric digit(s) 
     *        equivalent to an integer with value greater than 1 but less than 
     *        or equal to maxPhase (see next parameter) search for targets with 
     *        lower numbered phase names.
     * @param maxPhase The maximum phase number. If phaseName is specified as 
     *        "0" and maxPhase is a non-negative integer, search for the highest 
     *        "numbered" phase, starting at maxPhase, and searching down to phase
     *        "1".
     * @param nativeEncoding The encoding of the original native source document.
     *        This is to correctly read the skeleton file, which uses the
     *        encoding of the original-language source document. (UTF-8 will 
     *        always be used in the exported target-language document, to handle
     *        cases where a restrictive encoding [ISO-8859-1, for example--which
     *        handles only Western European languages] in a source document is 
     *        incapable of representing characters in a target language 
     *        [Japanese, for example].) If nativeEncoding is null, assume that
     *        the skeleton uses UTF-8 encoding.
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always XML
     * @param nativeFileName The name of the original source-language file that
     *        was earlier converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff), the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output XML formatted file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
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
            String nativeFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {
        
        // Verify input arguments
        if ((language == null) || (nativeFileName == null)
                || (nativeFileName.length() == 0)
                || (baseDir == null)
                || (baseDir.length() == 0)
                || (! mode.equals(ConversionMode.FROM_XLIFF))) {
            throw new ConversionException("Required parameter(s)"
                    + " omitted, incomplete or incorrect.");
        }
        
        if (nativeEncoding == null) {
            nativeEncoding = Charset.forName("UTF-8");
        }
        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;
        String inFormat = baseDir + File.separator + nativeFileName
                + Converter.formatSuffix;

        // Assume that the nativeFileName ends with a period and
        // extension (something like .xml ?). If it does, insert the
        // language before that final dot.
        String outXml = nativeFileName;

        int lastDot = outXml.lastIndexOf(".");
        if (lastDot == -1) {  // Unusual, but no dot!
            outXml = baseDir + File.separator + outXml + "."
                + language.toString();
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outXml + "." + language.toString());
            }
        }
        else {
            outXml = baseDir + File.separator
                + outXml.substring(0,lastDot)
                + "." + language.toString() + "."
                + outXml.substring(lastDot+1);
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outXml.substring(0,lastDot)
                    + "." + language.toString() + "." + outXml.substring(lastDot+1));
            }
        }
        
        // We created an empty map of TU strings when this class was loaded.
        // Make sure that actually happened.
        if (tuMap == null) {
            throw new ConversionException("Unable to get  target strings"
                + " from file " + inXliff);
        }

        // Now load that empty map with the target strings for the language
        // we are exporting.
        tuMap.loadStrings(inXliff, language, phaseName, maxPhase, true);

        // Before trying to export, check if notifier is non-null. If it is,
        // check to see if the skeleton is well-formed XML. If it isn't,
        // call the notifier. 
        boolean skelOK = true;
        if (notifier != null) {
            String notice = "";
            File skelFile = new File(inSkeleton);
            // Does the skeleton even exist?
            if (!skelFile.exists()) {
                skelOK = false;
                notice = "Document exporter cannot find a skeleton file named "
                        + inSkeleton;
                System.err.println(notice);
                notifier.sendNotification("0001", "XMLExporter", Notifier.ERROR, notice);
            }
            // Is it well-formed?
            else {
                try {
                    XMLReader parser = XMLReaderFactory.createXMLReader();

                    // We don't care about namespaces at the moment.
                    parser.setFeature("http://xml.org/sax/features/namespaces", false);

                    // Use an InputStream (instead of Reader), for XML that has
                    // a byteorder mark.
                    InputStream inStream = new FileInputStream(skelFile);
                    InputSource skelIn = new InputSource(inStream);
                    if (skelIn != null) {
                        parser.parse(skelIn); 
                        inStream.close();
                    }
                    else {
                        skelOK = false;
                        notice = "Unable to read skeleton file " 
                                + inSkeleton;
                        System.err.println(notice);
                        notifier.sendNotification("0002", "XMLExporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    skelOK = false;
                    notice = "Skeleton file " + inSkeleton
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0003", "XMLExporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    skelOK = false;
                    notice = "Skeleton file " + inSkeleton
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "XMLExporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    skelOK = false;
                    notice = "The validator of skeleton file " + inSkeleton
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "XMLExporter", Notifier.ERROR, notice);
                }
            }
        }

        // If the skeleton isn't OK, there is no point proceeding. The export 
        // will fail.
        if (! skelOK) {
            throw new ConversionException("Problems encountered reading the "
                    + "skeleton file. Support has been notified.");
        }
        
        //////////////////////////////////////////////////////////////////
        // Get readers/writers on the in/out files and necessary objects
        //////////////////////////////////////////////////////////////////

        // Skeleton (UTF-8, of course!)
        BufferedReader inSkel = null;
        try {
            inSkel = new BufferedReader(new InputStreamReader(new FileInputStream(inSkeleton),
                    nativeEncoding));
        }
        catch (FileNotFoundException e) {
            System.err.println("Cannot find the skeleton file: ");
            System.err.println(e.getMessage());
            throw new ConversionException("Cannot find the skeleton file: "
                    + e.getMessage());
        }
       
        // Format (to resolve bx/ex tags (etc.))
        try {
            format = new Format(inFormat);
        }
        catch (IOException e) {
            System.err.println("Cannot access the format file.");
            System.err.println(e.getMessage());
        }

        // XML output file 
//        OutputStreamWriter output = null;  // Where final document is written
        try {
            outWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outXml), Charset.forName("UTF-8")));
        }
        catch(FileNotFoundException e ) {
            System.err.println("Cannot write to the XML file: " + e.getMessage());
            throw new ConversionException("Cannot write to the XML file: "
                    + e.getMessage());
        }

        /*******************************
         * E X P O R T   T H E   X M L *
         *******************************/
        boolean need2WriteLine = true;  // Assume we need to write the skel line
        
        try {
            for (;;) {
                String skelLine = inSkel.readLine();
                if (skelLine == null) {
                    break;
                }
            
                // If line has tu place-holder, substitute the TU in its place
                if ((skelLine.indexOf("&lt;lTLt:tu id") != -1)
                    || (skelLine.indexOf("&lt;lTLt:format id") != -1)) {
                    expandTus(skelLine);  // Expands and writes output
                }
                // No placeholder(s); just echo line to output
                else {
                    // Didn't expand anything. Just echo to the output
                    outWriter.write(skelLine + "\n");
                }
                
                outWriter.flush();     // Hygienic ...
            }

            // Flush and close before leaving
            outWriter.flush();

            outWriter.close();
        }
        catch (IOException e) {
            System.err.println("Cannot read skeleton file");
            throw new ConversionException("Cannot read skeleton file "
                    + inSkeleton );
        }

        // Before returning, make sure that the generated output file
        // is valid XML. If not, throw an exception. 
        boolean contentOK = true;
        Charset charset = Charset.forName("UTF-8");
        
        try {
            XMLReader parser = XMLReaderFactory.createXMLReader();

            // We don't care about namespaces at the moment.
            parser.setFeature("http://xml.org/sax/features/namespaces", false);

            // Use an InputStream instead of a Reader, for cases where the XML
            // begins with a byteorder mark.
            InputStream inStream = new FileInputStream(outXml);
            InputSource contentIn = new InputSource(inStream);
            if (contentIn != null) {
                parser.parse(contentIn); 
                inStream.close();
            }
            else {
                contentOK = false;
                if (notifier != null) {
                    String notice = "Unable to read generated file " 
                            + outXml;
                    System.err.println(notice);
                    notifier.sendNotification("0011", "XMLExporter", Notifier.ERROR, notice);
                }
            }
        }
        catch(SAXParseException e) {
            contentOK = false;
            if (notifier != null) {
                String notice = "Generated file " + outXml
                        + " is not well-formed at line "
                        + e.getLineNumber() + ", column " + e.getColumnNumber()
                        + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                System.err.println(notice);
                notifier.sendNotification("0012", "XMLExporter", Notifier.ERROR, notice);
            }
        }
        catch(SAXException e) {
            contentOK = false;
            if (notifier != null) {
                String notice = "Generated file " + outXml
                        + " caused an XML parser error: " + e.getMessage()
                        + "\n" + this.getStackTrace(e);
                System.err.println(notice);
                notifier.sendNotification("0013", "XMLExporter", Notifier.ERROR, notice);
            }
        }
        catch(IOException e) {
            contentOK = false;
            if (notifier != null) {
                String notice = "The validator of generated file " + outXml
                        + " experienced an I/O error while reading input: " + e.getMessage()
                        + "\n" + this.getStackTrace(e);
                System.err.println(notice);
                notifier.sendNotification("0014", "XMLExporter", Notifier.ERROR, notice);
            }
        }

        // If the generated XML file isn't OK, we can still proceed. But 
        // return a status.
        if (! contentOK) {
            String message = "The export process generated an invalid XML file " 
                + outXml + ".";
            if (notifier != null) {
                message += " Support has been notified.";
            }
            System.err.println(message);
            return ConversionStatus.WARNING_INVALID_XML_EXPORTED;
//            throw new ConversionException(message);
        }

        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original XML format. Use (besides the XLIFF file) the skeleton and 
     * format files that were generated when the XLIFF file was created.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the XML document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified,
     *        the output file name will include the substring ja_JP in its name.
     * @param phaseName The name of the phase to export. If this parameter's
     *        value is not null, it is matched against the value of the 
     *        optional phase-name attribute of the target elements of the
     *        XLIFF document. If null, no check is made against a phase-name
     *        attribute.
     *        <p>If the phase name string consists entirely of numeric digit(s) 
     *        equivalent to an integer with value greater than 1 but less than 
     *        or equal to maxPhase (see next parameter) search for targets with 
     *        lower numbered phase names.
     * @param maxPhase The maximum phase number. If phaseName is specified as 
     *        "0" and maxPhase is a non-negative integer, search for the highest 
     *        "numbered" phase, starting at maxPhase, and searching down to phase
     *        "1".
     * @param nativeEncoding The encoding of the native document. This value
     *        if ignored. (UTF-8 will always be used in the target document,
     *        to handle cases where a restrictive encoding [ISO-8859-1,
     *        for example--which handles only Western European languages] in
     *        a source document in incapable of representing characters in a
     *        target language [Japanese, for example].)
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always XML
     * @param nativeFileName The name of the original source-language file that
     *        was earlier converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff), the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output XML formatted file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
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
            String nativeFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName,
            Set<XMLTuXPath> skipList) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, nativeFileName, baseDir, notifier, boundary, 
                generatedFileName);
    }
    
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original XML format. Use (besides the XLIFF file) the skeleton and 
     * format files that were generated when the XLIFF file was created.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the XML document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified,
     *        the output file name will include the substring ja_JP in its name.
     * @param phaseName The name of the phase to export. If this parameter's
     *        value is not null, it is matched against the value of the 
     *        optional phase-name attribute of the target elements of the
     *        XLIFF document. If null, no check is made against a phase-name
     *        attribute.
     *        <p>If the phase name string consists entirely of numeric digit(s) 
     *        equivalent to an integer with value greater than 1 but less than 
     *        or equal to maxPhase (see next parameter) search for targets with 
     *        lower numbered phase names.
     * @param maxPhase The maximum phase number. If phaseName is specified as 
     *        "0" and maxPhase is a non-negative integer, search for the highest 
     *        "numbered" phase, starting at maxPhase, and searching down to phase
     *        "1".
     * @param nativeEncoding The encoding of the native document. This value
     *        if ignored. (UTF-8 will always be used in the target document,
     *        to handle cases where a restrictive encoding [ISO-8859-1,
     *        for example--which handles only Western European languages] in
     *        a source document in incapable of representing characters in a
     *        target language [Japanese, for example].)
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always XML
     * @param nativeFileName The name of the original source-language file that
     *        was earlier converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff), the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output XML formatted file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
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
            String nativeFileName,
            String baseDir,
            Notifier notifier) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, nativeFileName, baseDir, notifier, null, null);
    }
    
    /** Matcher to match doubly encoded entities (e.g. where &lt; is &amp;lt;
     * etc. */
    private Matcher ampMatcher = Pattern.compile("&amp;(lt|gt|quot|apos|amp);",
            Pattern.DOTALL).matcher("");

    /** Matcher for placeholders in the skeleton */
    private Matcher placeHolderMatcher = Pattern.compile("&lt;lTLt:(tu|format) (id=|ids=)(['\"])(.+?)\\3/&gt;",
            Pattern.DOTALL).matcher("");

    private Matcher miscMatcher = Pattern.compile("&lt;/?mrk\\s[^>]*&gt;", Pattern.DOTALL).matcher("");
    
    /**
     * Passed a line from the skeleton file, expand all &lt;lTLt:tu id='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Write the expanded lilne in the expansionBuffer
     * @param skelLine Represents a line to be expanded.
     */
    private void expandTus(String skelLine) {

        placeHolderMatcher.reset(skelLine);
        // The next variables hold the start and end of the subsequence of non
        // placeholder characters just before the current placeholder.
        int copyFrom = 0;   // Where in the main string to copy non-placeholder chars from.
                            //   (i.e., where the previous placeholder ended) 
        int copyTo = 0;     // Where the current placeholder starts.
        int nextCopyFrom;   // Where the current placeholder ends. (We will copy
                            //   from here next time.)

        // Find each TU and extract its TU Identifier
        while (placeHolderMatcher.find()) {
            copyTo = placeHolderMatcher.start();     // Where this placeholder starts
            nextCopyFrom = placeHolderMatcher.end(); // Where this placeholder ends (+1)
            String placeHolder = placeHolderMatcher.group(0);
            String tuOrFormat = placeHolderMatcher.group(1);
            boolean isSingleton = placeHolderMatcher.group(2).equals("ids=") ? false : true;
            String tuID = placeHolderMatcher.group(4);     // Get the identifier(s) from the tag in skel

            String text = "";
            if (tuOrFormat.equals("tu")) {
                if (isSingleton) {
                    text = tuMap.getTu(tuID, null, false, false);  // Get the TU target text
                }
                else { // Multiple TU (segment) references
                    String tail = tuID; // All the TU/format IDs
                    boolean done = false;
                    while (! done) {
                        multipleTuMatcher.reset(tail);
                        if (multipleTuMatcher.find()) {
                            String tOrF = multipleTuMatcher.group(1);
                            String id = multipleTuMatcher.group(2);
                            tail = multipleTuMatcher.group(3);
                            if (tOrF.equals("tu")) {
                                text += tuMap.getTu(id, null, false, false);
                            }
                            else {  // Text from the format file
                                text += format.getReplacement(id);
                            }
                        }
                        else {
                            done = true;
                        }
                    }
                }

                text = TuPreener.removeCoreMarks(text);
                // Validate entire TU (sequence)
                text = TuPreener.validateAndRepairTu(text);
            }
            else { // Read the format file
                text = format.getReplacement(tuID);
            }

            // Expand bx, ex, x, ...
            text = resolveFormatCodes(text);
            text = TuPreener.removeMergerMarks(TuPreener.removeCoreMarks(text));
            text = text.replace("&lt;lt:core&gt;", "");
            text = text.replace("&lt;/lt:core&gt;", "");

            // Just in case ...:
//            text = Pattern.compile("&lt;/?mrk\\s[^>]*&gt;", Pattern.DOTALL).matcher(text).replaceAll("");
            text = miscMatcher.reset(text).replaceAll("");

            // Ticket 921: Replace \r\n and \n with &xa;
            if (text.indexOf(10) > -1) {   // Newline has a value of 10
//                    text = text.replace("\r\n", "&xa;");
                text = text.replace("\n","&#xa;");   
            }

            // Ticket 955: Resolve doubly encoded entities (i.e., change
            // strings like &amp;lt; to &lt;)
            if (text.indexOf('&') > -1) {
                text = ampMatcher.reset(text).replaceAll("&$1;");
            }

            // Output the text between the previous placeholder and this one.
            try {
                outWriter.write(skelLine, copyFrom, copyTo-copyFrom);
            
                // Then output whatever the placeholder expanded to.
                outWriter.write(text);
            }
            catch(IOException e) {
                System.err.println("Caught exception writing expansion.");
            }
            
            // Then get where (in skelLine) this placeholder ended, so we can
            // write from that place next time.
            copyFrom = nextCopyFrom;
        }

        // We're done; append the text after the last placeholder in the skeleton
        try {
            outWriter.write(skelLine, copyFrom, skelLine.length()-copyFrom);
            outWriter.write("\n");
        }
        catch(IOException e) {
            System.err.println("Caught exception writing expansion.");
        }
    }

    /** Matcher for placeholders in the skeleton */
    private Matcher formatPlaceHolderMatcher = Pattern.compile("&lt;lTLt:(tu|format) (id=|ids=)(['\"])(.+?)\\3/&gt;",
            Pattern.DOTALL).matcher("");
    
    /**
     * Passed a line from the format file, expand all &lt;lTLt:tu id='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Return the expanded line.
     * @param fmtLine Represents a line to be expanded.
     */
    private String expandTusInFormat(String fmtLine) {

        StringBuilder fmtBuffer = new StringBuilder();
        
        formatPlaceHolderMatcher.reset(fmtLine);
        // The next variables hold the start and end of the subsequence of non
        // placeholder characters just before the current placeholder.
        int copyFrom = 0;   // Where in the main string to copy non-placeholder chars from.
                            //   (i.e., where the previous placeholder ended) 
        int copyTo = 0;     // Where the current placeholder starts.
        int nextCopyFrom;   // Where the current placeholder ends. (We will copy
                            //   from here next time.)

        // Find each TU and extract its TU Identifier
        while (formatPlaceHolderMatcher.find()) {
            copyTo = formatPlaceHolderMatcher.start();     // Where this placeholder starts
            nextCopyFrom = formatPlaceHolderMatcher.end(); // Where this placeholder ends (+1)
            String placeHolder = formatPlaceHolderMatcher.group(0);
            String tuOrFormat = formatPlaceHolderMatcher.group(1);
            boolean isSingleton = formatPlaceHolderMatcher.group(2).equals("ids=") ? false : true;
            String tuID = formatPlaceHolderMatcher.group(4);     // Get the identifier(s) from the tag in skel

            String text = "";
            if (tuOrFormat.equals("tu")) {
                if (isSingleton) {
                    text = tuMap.getTu(tuID, null, false, false);  // Get the TU target text
                }
                else { // Multiple TU (segment) references
                    String tail = tuID; // All the TU/format IDs
                    boolean done = false;
                    while (! done) {
                        multipleTuMatcher.reset(tail);
                        if (multipleTuMatcher.find()) {
                            String tOrF = multipleTuMatcher.group(1);
                            String id = multipleTuMatcher.group(2);
                            tail = multipleTuMatcher.group(3);
                            if (tOrF.equals("tu")) {
                                text += tuMap.getTu(id, null, false, false);
                            }
                            else {  // Text from the format file
                                text += format.getReplacement(id);
                            }
                        }
                        else {
                            done = true;
                        }
                    }
                }

                text = TuPreener.removeCoreMarks(text);
                // Validate entire TU (sequence)
                text = TuPreener.validateAndRepairTu(text);
            }
            else { // Read the format file
                text = format.getReplacement(tuID);
            }

            // Expand bx, ex, x, ...
            text = resolveFormatCodes(text);
            text = TuPreener.removeMergerMarks(TuPreener.removeCoreMarks(text));
            text = text.replace("&lt;lt:core&gt;", "");
            text = text.replace("&lt;/lt:core&gt;", "");

            // Just in case ...:
            text = miscMatcher.reset(text).replaceAll("");

            // Ticket 921: Replace \r\n and \n with &xa;
            if (text.indexOf(10) > -1) {   // Newline has a value of 10
                text = text.replace("\n","&#xa;");   
            }

            // Ticket 955: Resolve doubly encoded entities (i.e., change
            // strings like &amp;lt; to &lt;)
            if (text.indexOf('&') > -1) {
                text = ampMatcher.reset(text).replaceAll("&$1;");
            }

            // Now replace the Skeleton's TU tag with the (expanded) TU text
            // Copy the text between the previous placeholder and this one.
            fmtBuffer.append(fmtLine, copyFrom, copyTo);
            
            // Then copy what the placeholder expanded to.
            fmtBuffer.append(text);
            // Then get where (in skelLine) this placeholder ended, so we can
            // copy from that place next time.
            copyFrom = nextCopyFrom;
        }

        // We're done; append the text after the last placeholder in the skeleton
        fmtBuffer.append(fmtLine, copyFrom, fmtLine.length());
        
        return fmtBuffer.toString();
    }
    
    /** 
     * Passed a translation unit, look for bx/ex tags (beginning and ending)
     * and replace them with their original strings.
     * (It is possible that a "native" tag a bx tag expands to might include
     * an attribute with translatable text. Such an attribute's value will 
     * be an lTLt:tu empty element placeholder.)
     * @param tuText The text of the Translation Unit that needs to have
     *               its format codes resolved
     * @return The expanded TU, with bx/ex codes replaced by their equivalents
     */
    private String resolveFormatCodes(String tuText) {
        if (this.format == null) {
            // Without a format class, we can't resolve the codes
            return tuText;                     // Just return
        }

        String newStr = tuText;
        boolean moreCodes = true;              // Be optimistic
        
        while (moreCodes) {
            // Find each format code and extract its id
            fmtMatcher.reset(newStr);
            if (fmtMatcher.find()) {
                String formatID = fmtMatcher.group(1);  // group 1 is 1st set of parens
                String wholeTag = fmtMatcher.group(0);  // group 0 matches entire regex
                
                // Just get the replacement text for the bx/ex/x
                String formatText = format.getReplacement(formatID);
                if (formatText.length() > 0) {  // If successful
                    // Does this format string have further xliff tags?
                    if (formatText.matches(".*<[be]?x [^>]*>.*")) {
                        // I hope this works!
                        formatText = resolveFormatCodes(formatText);
                    }

                    // The resolved format text *might* include attribute(s)
                    // that themselves include translatable text. See if
                    // there are any TU placeholders in the resolved format
                    // text. If there are, expand them.
                    if (formatText.contains("&lt;lTLt:tu id")) {
                        formatText = this.expandTusInFormat(formatText);
                    }

                    newStr = newStr.replace(wholeTag, formatText);
                }
                else {
                    // Leave the format code unexpanded (not good!)
                }
            }
            else {
                // We're done--no more format codes
                moreCodes = false;
            }
        }
        
        return newStr;       // The TU with all format codes expanded.
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
     * Convert a stack trace to a string.
     * @param t Throwable whose stack trace will be returned
     * @return String that contains the strack trace.
     */
    private String getStackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);      // Print the stack trace
        pw.flush();
        sw.flush(); 
        return sw.toString();
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
