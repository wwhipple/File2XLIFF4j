/*
 * PlaintextExporter.java
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

/**
 * Class to export an XLIFF target to plaintext document in the same format as the
 * original plaintext source document.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PlaintextExporter implements Converter {

    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    private Matcher placeHolderMatcher 
        = Pattern.compile("^(.*?)(<lTLtLT:tu id=(['\"])(.+?)\\3/>)(.*)").matcher("");

    /** Creates a new instance of PlaintextExporter */
    public PlaintextExporter() { }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original plaintext format. Use (besides the XLIFF file) the skeleton 
     * file that was generated when the XLIFF file was created. (Note: Because
     * plaintext files--as defined by this converter--contain no formatting,
     * no format file is used by this converter.)
     * @param mode The mode of conversion (to or from XLIFF). Must be from XLIFF
     *        for this converter.
     * @param language The language of the XLIFF targets to use in constructing
     *        the plaintext document. The language is used in constructing a 
     *        unique name for the output file. For example, if ja_JP is specified,
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
     * @param nativeEncoding This parameter--the encoding of the original 
     *        native plaintext file--is ignored by this converter. (UTF-8 will 
     *        always be used in the exported target-language document, to handle
     *        cases where a restrictive encoding [ISO-8859-1, for example--which
     *        handles only Western European languages] in a source document is 
     *        incapable of representing characters in a target language 
     *        [Japanese, for example].)
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always plaintext.
     * @param nativeFileName The name of the original source-language file that
     *        was previously converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff) and the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton). It is also used in 
     *        constructing the file name of the output plaintext file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton files) will be read, and to which the output 
     *        file will be written.
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
        
        nativeEncoding = Charset.forName("UTF-8");
        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;

        // Assume that the nativeFileName ends with a period and extension 
        // (something like .txt or .text). If it does, insert the language 
        // before that final dot.
        String outPlaintext = nativeFileName;

        int lastDot = outPlaintext.lastIndexOf(".");
        if (lastDot == -1) {  // Unusual, but no dot!
            outPlaintext = baseDir + File.separator + outPlaintext + "."
                + language.toString();
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outPlaintext + "." + language.toString());
            }
        }
        else {
            outPlaintext = baseDir + File.separator
                + outPlaintext.substring(0,lastDot)
                + "." + language.toString() + "."
                + outPlaintext.substring(lastDot+1);
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outPlaintext.substring(0,lastDot)
                    + "." + language.toString() + "." + outPlaintext.substring(lastDot+1));
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
       
        // (No format file for plaintext--no bx/ex tags, etc.)

        // Plaintext output file 
        OutputStreamWriter output = null;  // Where final document is written
        try {
            output = new OutputStreamWriter(new FileOutputStream(outPlaintext), Charset.forName("UTF-8"));
        }
        catch(FileNotFoundException e ) {
            System.err.println("Cannot write to the plaintext file: " + e.getMessage());
            throw new ConversionException("Cannot write to the plaintext file: "
                    + e.getMessage());
        }

        /*******************************************
         * E X P O R T   T H E   P L A I N T E X T *
         *******************************************/
        // Matcher to capture leading white space.
        Matcher leadingWhiteMatcher = Pattern.compile("^(\\s+)(.*)").matcher("");
        
        try {
            String skelLine = "";
            String prevLine = "";
            for (;;) {
                prevLine = skelLine;    // From last loop
                skelLine = inSkel.readLine();
                if (skelLine == null) {
                    break;
                }
            
                // If line has tu place-holder, substitute the TU in its place
                if (skelLine.indexOf("<lTLtLT:tu id") != -1) {
                    skelLine = expandTus(skelLine);
                }
            
                // If the first line in a paragraph, preserve leading white space.
                // but shrink subsequent white space to one space.
                if (prevLine.trim().length() == 0 && skelLine.trim().length() > 0) {
                    String leadingWhiteSpace = "";
                    leadingWhiteMatcher.reset(skelLine);
                    if (leadingWhiteMatcher.find()) {
                        leadingWhiteSpace = leadingWhiteMatcher.group(1);
                        skelLine = leadingWhiteMatcher.group(2);
                    }
                    skelLine = leadingWhiteSpace + skelLine.replaceAll("\\s+", " ");
                }
                else {
                    // Otherwise, shrink multiple adjacent spaces to single spaces
                    skelLine = skelLine.replaceAll("\\s+", " ");
                }
                    
                // Then write the line to the output stream;
                output.write(skelLine + "\n");
                output.flush();     // For debugging
            }

            // Flush and close before leaving
            output.flush();

            output.close();
        }
        catch (IOException e) {
            System.err.println("Cannot read skeleton file");
            throw new ConversionException("Cannot read skeleton file "
                    + inSkeleton );
        }

        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original plaintext format. Use (besides the XLIFF file) the skeleton 
     * file that was generated when the XLIFF file was created. (Note: Because
     * plaintext files--as defined by this converter--contain no formatting,
     * no format file is used by this converter.)
     * @param mode The mode of conversion (to or from XLIFF). Must be from XLIFF
     *        for this converter.
     * @param language The language of the XLIFF targets to use in constructing
     *        the plaintext document. The language is used in constructing a 
     *        unique name for the output file. For example, if ja_JP is specified,
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
     * @param nativeEncoding This parameter--the encoding of the original 
     *        native plaintext file--is ignored by this converter. (UTF-8 will 
     *        always be used in the exported target-language document, to handle
     *        cases where a restrictive encoding [ISO-8859-1, for example--which
     *        handles only Western European languages] in a source document is 
     *        incapable of representing characters in a target language 
     *        [Japanese, for example].)
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always plaintext.
     * @param nativeFileName The name of the original source-language file that
     *        was previously converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff) and the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton). It is also used in 
     *        constructing the file name of the output plaintext file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton files) will be read, and to which the output 
     *        file will be written.
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
     * to the original plaintext format. Use (besides the XLIFF file) the skeleton 
     * file that was generated when the XLIFF file was created. (Note: Because
     * plaintext files--as defined by this converter--contain no formatting,
     * no format file is used by this converter.)
     * @param mode The mode of conversion (to or from XLIFF). Must be from XLIFF
     *        for this converter.
     * @param language The language of the XLIFF targets to use in constructing
     *        the plaintext document. The language is used in constructing a 
     *        unique name for the output file. For example, if ja_JP is specified,
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
     * @param nativeEncoding This parameter--the encoding of the original 
     *        native plaintext file--is ignored by this converter. (UTF-8 will 
     *        always be used in the exported target-language document, to handle
     *        cases where a restrictive encoding [ISO-8859-1, for example--which
     *        handles only Western European languages] in a source document is 
     *        incapable of representing characters in a target language 
     *        [Japanese, for example].)
     * @param nativeFileType This parameter is ignored. The native file type is
     *        always plaintext.
     * @param nativeFileName The name of the original source-language file that
     *        was previously converted to XLIFF.  It is used to determine the name 
     *        of the XLIFF file (&lt;nativeFileName&gt;.xliff) and the skeleton 
     *        file (&lt;nativeFileName&gt;.skeleton). It is also used in 
     *        constructing the file name of the output plaintext file.
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton files) will be read, and to which the output 
     *        file will be written.
     * @param notifier Instance of a class that implements the Notifier
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
    
    /**
     * Passed a line from the skeleton file, expand all &lt;$lTLt$:tu id='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Return the expanded string.
     * @param skelLine Represents a line to be expanded.
     */
    private String expandTus(String skelLine) {
        String tail = skelLine;
        String newString = "";
        
        
        while (tail != null && tail.length() > 0) {
            // Find each TU and extract its TU Identifier
            // "^(.*?)(<$lTLt$:tu id=(['\"])(.+?)\\3/>)(.*)"
            
            placeHolderMatcher.reset(tail);
            if (placeHolderMatcher.find()) {
                if (placeHolderMatcher.group(1) != null) {
                    newString += placeHolderMatcher.group(1); // Stuff before the placeholder
                }
                String placeHolder = placeHolderMatcher.group(2); // The placeholder
                String tuID = placeHolderMatcher.group(4); // The UUID from the placeholder
                tail = placeHolderMatcher.group(5);        // After the placeholder
                
                String text = tuMap.getTu(tuID, null, false, false);  // Get the TU target text
                    
//                text = TuPreener.removeCoreMarks(text);

                text = TuStrings.unEscapeTuString(TuPreener.removeMergerMarks(TuPreener.removeCoreMarks(text)));
                
                // Just in case ...:
//                text = Pattern.compile("&lt;/?mrk\\s[^>]*&gt;", Pattern.DOTALL).matcher(text).replaceAll("");

                // Now replace the Skeleton's TU tag with the (expanded) TU text
                newString += text;
            }
            else {
                // No more matches
                break;                       
            }
        }

        // Append any residue after the last placeholder.
        if (tail != null && tail.length() > 0) {
            newString += tail;
        }
        
        return newString;
        //return newString.replaceAll(" +", " ");
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
     * @return the PLAINTEXT file type.
     */
    public FileType getFileType() {
        return FileType.PLAINTEXT;
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
