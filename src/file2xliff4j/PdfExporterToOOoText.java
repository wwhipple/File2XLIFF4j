/*
 * PdfExporterToOOoText.java
 *
 * Copyright (C) 2007. Lingotek, Inc. All rights reserved.
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

import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;
import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;

/**
 * Exporter of original PDF document's translation to OpenOffice.org Text format.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PdfExporterToOOoText implements Converter {

    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    private Pattern formatPattern;  // For compiled regex that matches bx, ex, etc. tags.
    private Pattern charsetPattern; // For matching charset metatag
    
    private Format format;          // Maps bx/ex etc. to original format characters.
    
    private String imgPrefix = "";  // Prepend this path to the value of the src
                                    // attribute of img tags.
    
    /**
     * Creates a new instance of PdfExporterToOOoText
     */
    public PdfExporterToOOoText() { }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file
     * created from a PDF document) to OpenOffice.org Text Format.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     * @param nativeEncoding This parameter is ignored--the default encoding 
     *        is used in the output document in all cases.
     * @param nativeFileType This parameter is ignored by this converter.
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
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

    // Matcher for the tu lines in the skeleton file.
    private Matcher tuMatcher 
        = Pattern.compile("<tu id='(.+?)' length='\\d+' no='(\\d+)' of='(\\d+)'>").matcher("");

    private Matcher formatMatcher 
        = Pattern.compile("<[be]?x\\b.*?\\bid=(['\"])(.+?)\\1.*?>", 
            Pattern.DOTALL).matcher("");
        
    
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file
     * created from a PDF document) to OpenOffice.org Text Format.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     * @param nativeEncoding This parameter is ignored--the default encoding 
     *        is used in the output document in all cases.
     * @param nativeFileType This parameter is ignored by this converter.
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
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

        if ((language == null) || (nativeEncoding == null)
                || (nativeFileName == null)
                || (nativeFileName.length() == 0)
                || (baseDir == null)
                || (baseDir.length() == 0)
                || (! mode.equals(ConversionMode.FROM_XLIFF))) {
            throw new ConversionException("Required parameter(s)" 
                    + " omitted, incomplete or incorrect.");
        }
        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;
        
        // Assume that the nativeFileName ends with a period and
        // extension (something like .pdf ?). We will modify it with the addition
        // of a language code and .txt, then have OOo convert it to OpenOffice.org Text.
        String outOOoTextNameOnly = nativeFileName;
        String outOOoText;

        int lastDot = outOOoTextNameOnly.lastIndexOf(".");
        if (lastDot == -1) {  // Unusual, but no dot!
            outOOoText = baseDir + File.separator + outOOoTextNameOnly + "."
                + language.toString() + ".odt";
            outOOoTextNameOnly += (language.toString() + ".odt");
        }
        else {
            outOOoText = baseDir + File.separator 
                + outOOoTextNameOnly.substring(0,lastDot)
                + "." + language.toString() + ".odt";
            outOOoTextNameOnly = outOOoTextNameOnly.substring(0,lastDot)
                + "." + language.toString() + ".odt";
        }
        
        // Because OOo and Java don't quite have the file name flexibility we
        // need, we will use a file name we feel "good" about.
        String tempHtmlName = baseDir + File.separator + "$$tsjoof$$."
                + language.toString() + ".html";
        
        // We created an empty map of TU strings when this class was loaded.
        // Make sure that actually happened.
        if (tuMap == null) {
            throw new ConversionException("Unable to get  target strings"
                + " from file " + inXliff);
        }
        
        // Now load that empty map with the target strings for the language
        // we are exporting. (5th argument "false" indicates not to convert
        // ampersands to entities.)
        tuMap.loadStrings(inXliff, language, phaseName, maxPhase, false);

        //////////////////////////////////////////////////////////////////
        // Get readers/writers on the in/out files and necessary objects
        //////////////////////////////////////////////////////////////////

        // Skeleton (UTF-8, of course!)
        BufferedReader inSkel = null;
        try {
            inSkel =  new BufferedReader(new InputStreamReader(new FileInputStream(inSkeleton),
                "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {  // What!!?? Can't read UTF-8?
            System.err.println("Cannot decode UTF-8 skeleton file: " 
                    + e.getMessage());
            throw new ConversionException("Cannot decode UTF-8 skeleton file: " 
                    + e.getMessage());
        }
        catch (FileNotFoundException e) {
            System.err.println("Cannot find the skeleton file: ");
            System.err.println(e.getMessage());
            throw new ConversionException("Cannot find the skeleton file: "
                    + e.getMessage());
        }
        
        // Output a text document initially (OOo converts it to OpenOffice.org Text later.)
        BufferedWriter htmlWriter = null;  // Where final document is written
        try {
            // Write to a temporary file.
            htmlWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempHtmlName), Charset.forName("UTF-8")));
        }
        catch(FileNotFoundException e ) {
            System.err.println("Cannot write to intermediate plaintext file: " 
                    + e.getMessage());
            throw new ConversionException("Cannot write to intermediate plaintext file: "
                    + e.getMessage());
        }

        /*********************************
         * E X P O R T   T H E   H T M L *
         *********************************/
        boolean done = false;               // Not done yet
        String tuID = "";
        int num = 0;        // Tu number ?
        int tot = 0;        //   of the total in this series.
        String tuText = "";

        try {
            htmlWriter.write("<html><head>\n"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
                + "</head>\n<body>");
                    
            SKEL_LOOP:
            while (! done) {
                String skelLine = inSkel.readLine();
                if (skelLine == null) {
                    done = true;
                    break;
                }

                // Get TU ids from the skeleton
                if (tuMatcher.reset(skelLine).find()) {
                    tuID = tuMatcher.group(1);
                    num = Integer.parseInt(tuMatcher.group(2));
                    tot = Integer.parseInt(tuMatcher.group(3));

//                    // Get the only TU
//                    if (tot == 1) {
//                        tuText = tuMap.getTu(tuID,"html");  // Get the Tu Text.
//                    }
//                    // Get the next TU (of multiple TUs)
//                    else {
//                        tuText += tuMap.getTu(tuID,"html");  // Get the Tu Text.
//                        if (num < tot) {
//                            continue SKEL_LOOP;
//                        }
//                    }

                    tuText = tuMap.getTu(tuID,"html");  // Get the Tu Text.
                    
                    if (tuText.trim().length() == 0) {
                        // This TU was probably merged with its predecessor.
                        // Just skip it.
                        tuText = "";
                        continue SKEL_LOOP;
                    }

                    tuText = TuPreener.removeCoreMarks(tuText);

                    // Expand bx, ex, x, ... to nothing for now.
                    tuText = formatMatcher.reset(tuText).replaceAll("");

                    // Finally, expand all entities
                    tuText = TuStrings.unEscapeTuString(tuText);

                    if (tuText.trim().length() > 0) {
                         htmlWriter.write("<p>" + tuText + "</p>\n");
                         htmlWriter.flush();
                    }
                    tuText = "";
                }
            }
            
            htmlWriter.write("\n</body>\n</html>");
            
            // Close the text writer (so that OOo can read it in a moment).
            htmlWriter.close();
        }
        catch (IOException e) {
            System.err.println("Cannot convert to intermediate text file.");
            throw new ConversionException("Cannot convert to intermediate text file: "
                    + e.getMessage());
        }

        // Now have OOo convert to OpenOffice.org Text
        String tempOOoTextOutFileName = baseDir + File.separator + "$$tsjoof$$." 
            + language.toString() + ".odt";
        OpenOfficeConnection connection = new SocketOpenOfficeConnection();
        try {
                
            DefaultDocumentFormatRegistry formatReg = new DefaultDocumentFormatRegistry();
            
            // Then create input and output file objects with the names we came
            // up with above.
            File outputFile = new File(tempOOoTextOutFileName);
            File inputFile = new File(tempHtmlName);
                                
            connection.connect();       // Connect to the running soffice.
            if (connection.isConnected()) {
                DocumentConverter converter = new OpenOfficeDocumentConverter(connection);
                converter.convert(inputFile, formatReg.getFormatByFileExtension("html"),
                    outputFile, formatReg.getFormatByFileExtension("odt"));
            }
            else {
                throw(new OpenOfficeConnectException("Unable to connect to OpenOffice.org"
                    + " to generate OpenOffice.org Text translation of PDF document."));
            }
        }
        catch (java.net.ConnectException ce) {
            String tMessage = ce.getMessage();
            throw(new OpenOfficeConnectException("Unable to communicate with OpenOffice" +
                    " to generate target OpenOffice.org Text Document: " + tMessage));
        }
        catch(Exception ex) {
            String tMessage = ex.getMessage();
            System.err.println("Unable to generate target OpenOffice.org Text document using OpenOffice.org.");
            System.err.println(tMessage);
            throw new ConversionException("Unable to generate target OpenOffice.org Text document using OpenOffice.org: "
                    + tMessage);
        }

        if (connection != null) {
            connection.disconnect();
        }
        
        // 1/8/7: Now we need to rename the generated doc file to the name we really
        // want:
        File ffNewOOoText = new File(tempOOoTextOutFileName);
        ffNewOOoText.renameTo(new File(outOOoText));
        
        if (generatedFileName != null) {
            // Tell caller the name of the output file (wo/directories)
            generatedFileName.write(outOOoTextNameOnly);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original HTML format, using the skeleton and format files that
     * were generated when the XLIFF file was created. 
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     * @param nativeEncoding The encoding of the native document. This parameter 
     *        tells the converter what to convert the UTF-8 encoding back to.
     *        (Note: The choice of encoding may depend on the target language.
     *        Encodings like Shift_JIS and EUCJP, for example, are appropriate
     *        for Japanese targets only. UTF-8 can be used for all target
     *        languages.)
     * @param nativeFileType This parameter is ignored. (In conversions from XLIFF,
     *        the native file type stored in the XLIFF--specified when the XLIFF 
     *        was created--is used.)
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output HTML formatted file:
     *        &lt;nativeFileName&gt;.&lt;language&gt;.html (if the original file's
     *        "extension" wasn't ".html", the original extension is used instead.)
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
     * @param skipList (Not used by this converter.)
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
     * @return the PDF file type.
     */
    public FileType getFileType() {
        return FileType.ODT;
    }

    /**
     * Set a format-specific property that might affect the way that the
     * conversion occurs.
     * <p><i>Note:</i> This exporter needs to know of a directory prefix
     * to prepend to image file names that appear on the src attribute of img
     * tags in the intermediate HTML. This method is a way to communicate that
     * information.
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
