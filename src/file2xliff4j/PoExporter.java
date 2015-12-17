/*
 * PoExporter.java
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
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Export a GNU Portable Object file for a specified target language.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PoExporter implements Converter {
    
    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    /**
     * Create a GNU Portable Object exporter
     */
    public PoExporter() { }

    // Matcher to match and capture information about the original input file name
    private Matcher fNameMatcher 
        = Pattern.compile("^(.*)\\.pot?$").matcher("");
    
    // This matcher will identify lines with properties and placeholders
    private Matcher placeHolderMatcher 
        = Pattern.compile("<lTLt:tu id=(['\"])([-0-9A-Fa-f]+)\\1/>").matcher("");
    
    // Matcher for the Content-Type charset line:
    private Matcher charsetMatcher
        = Pattern.compile("^(.*Content-Type:\\s+text/plain;\\s+charset=)[-_a-zA-Z0-9]+(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher("");
    
    /**
     * Convert one set of targets (in the translation units of an XLIFF file) 
     * to a GNU Portable Object (.po) file, based on the original Portable
     * Object Template (.pot) file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use substitute in 
     *        the msgstr lines in the output PO file. The language is used in 
     *        constructing a unique name for the generated PO file. For example, 
     *        if a language of ja_JP is specified and the original template was
     *        named potapt.pot, the exported PO file will be named 
     *        potapt.ja_JP.po.
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
     * @param nativeEncoding The desired encoding of the output PO file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, either specify the UTF-8 Charset or specify null
     *        for the value of this parameter. (If null is specified, the PoExporter
     *        will use UTF-8 for the output PO file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.PO.)
     * @param nativeFileName The name of the original file (previously imported
     *        to XLIFF--not including the parent directory components). 
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton) will be read, and to which the output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored by all exporters.)
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
        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;
        String originalFileName = baseDir + File.separator + nativeFileName;

        if (!(new File(inXliff).exists())) {
            throw new ConversionException("Cannot locate XLIFF file "
                    + inXliff);
        }

        if (!(new File(inSkeleton).exists())) {
            throw new ConversionException("Cannot locate skeleton file "
                + inSkeleton);
        }
        
//        if (!(new File(originalFileName).exists())) {
//            throw new ConversionException("Cannot locate original DTD file "
//                + originalFileName);
//        }

        String outFileNameOnly;  // Just the name (no directories) of the output file
        // Figure out a name for the output properties file.
        if (fNameMatcher.reset(nativeFileName).find()) {
            // The original input file name had a reasonable name
            outFileNameOnly = fNameMatcher.group(1) + "." + language.toString()
                + ".po";
        }
        else {
            // Unable to make sense of the original file name, just tack the
            // language code and .po on the end of the original
            outFileNameOnly = originalFileName + "." + language.toString()
                + ".po";
        }
        
        String outPo = baseDir + File.separator + outFileNameOnly;
        
        if (generatedFileName != null) {
            // Tell caller the name of the output file (wo/directories)
            generatedFileName.write(outFileNameOnly);
        }

        // We created an empty map of TU strings when this class was loaded.
        // Make sure that actually happened.
        if (tuMap == null) {
            throw new ConversionException("Unable to get  target strings"
                + " from file " + inXliff);
        }

        // Now load that empty map with the target strings for the language
        // we are exporting.
        tuMap.loadStrings(inXliff, language, phaseName, maxPhase, false);

        // Open and read the skeleton and write the translated file
        BufferedReader inSkel = null;
        Charset outCharset = null;
        if (nativeEncoding == null) {
            outCharset = Charset.forName("UTF-8");
        }
        else {
            outCharset = nativeEncoding;
        }
        try {
            BufferedReader skelRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(inSkeleton), Charset.forName("UTF-8")));
            BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outPo), outCharset));

            String skelLine;
            
            // We need to replace the charset specification in the Content-Type
            // "header"
            boolean replacedCharset = false;  // Haven't replaced it yet ...

            // Read every line in the skeleton file
            while ((skelLine = skelRdr.readLine()) != null) {
                
                // If this line contains the Content-Type ... charset line,
                // update it to specify the charset we are using in the output
                // file.
                if (!replacedCharset && charsetMatcher.reset(skelLine).find()) {
                    skelLine = charsetMatcher.group(1) + outCharset.toString()
                        + charsetMatcher.group(2);
                    replacedCharset = true;     // Don't check again.
                }
                
                // If this one has a TU UUID placeholder, replace it with the target
                if (placeHolderMatcher.reset(skelLine).find()) {
                    String placeHolder = placeHolderMatcher.group(0);
                    String tuID = placeHolderMatcher.group(2);
                    String translation = tuMap.getTu(tuID);
                    
                    // Break the translation into multiple lines as necessary
                    String [] outLines = getOutLines(TuPreener.removeCoreMarks(translation));
                    // If we have at least one line of translation, output it
                    // in the po file
                    if (outLines.length > 0) {
                        // Write out the first line of the translation
                        outWriter.write(skelLine.replace(placeHolder, outLines[0]) + "\n");
                        for (int i = 1; i < outLines.length; i++) {
                            outWriter.write("\"" + outLines[i] + "\"\n");
                        }
                    }
                    else {
                        outWriter.write(skelLine.replace(placeHolder, "") + "\n");
                    }
                }
                else {
                    // Otherwise just copy the skeleton line to the output.
                    outWriter.write(skelLine + "\n");
                }
                outWriter.flush();
            }
            
            // Close both streams
            skelRdr.close();
            outWriter.close();
        }
        catch (FileNotFoundException e) {
            System.err.println("Cannot find the skeleton file: " + e.getMessage());
            throw new ConversionException("Cannot find the skeleton file: "
                    + e.getMessage());
        }
        
        catch (IOException e) {
            System.err.println("Error generating translated Java properties file: " 
                + e.getMessage());
            throw new ConversionException("Error generating translated Java properties file: " 
                + e.getMessage());
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert one set of targets (in the translation units of an XLIFF file) 
     * to a GNU Portable Object (.po) file, based on the original Portable
     * Object Template (.pot) file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use substitute in 
     *        the msgstr lines in the output PO file. The language is used in 
     *        constructing a unique name for the generated PO file. For example, 
     *        if a language of ja_JP is specified and the original template was
     *        named potapt.pot, the exported PO file will be named 
     *        potapt.ja_JP.po.
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
     * @param nativeEncoding The desired encoding of the output PO file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, either specify the UTF-8 Charset or specify null
     *        for the value of this parameter. (If null is specified, the PoExporter
     *        will use UTF-8 for the output PO file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.PO.)
     * @param nativeFileName The name of the original file (previously imported
     *        to XLIFF--not including the parent directory components). 
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton) will be read, and to which the output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored by all exporters.)
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
     * Convert one set of targets (in the translation units of an XLIFF file) 
     * to a GNU Portable Object (.po) file, based on the original Portable
     * Object Template (.pot) file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use substitute in 
     *        the msgstr lines in the output PO file. The language is used in 
     *        constructing a unique name for the generated PO file. For example, 
     *        if a language of ja_JP is specified and the original template was
     *        named potapt.pot, the exported PO file will be named 
     *        potapt.ja_JP.po.
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
     * @param nativeEncoding The desired encoding of the output PO file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, either specify the UTF-8 Charset or specify null
     *        for the value of this parameter. (If null is specified, the PoExporter
     *        will use UTF-8 for the output PO file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.PO.)
     * @param nativeFileName The name of the original file (previously imported
     *        to XLIFF--not including the parent directory components). 
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton) will be read, and to which the output file 
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

    // A pattern and matcher to find/capture XLIFF x elements ot ctype lb:
    private Pattern lbPattern 
        = Pattern.compile("<x\\s+id=(['\"])1\\1\\s+ctype=(['\"])lb\\2\\s*/>",
            Pattern.DOTALL);
    private Matcher lbMatcher = lbPattern.matcher("");

    // Matcher to find an lb ctype x tag at the (very) extreme end of a string:
    private Matcher lbTailMatcher
        = Pattern.compile("^(.*)<x\\s+id=(['\"])1\\2\\s+ctype=(['\"])lb\\3\\s*/>$",
            Pattern.DOTALL).matcher("");
    
    /**
     * Passed a string potentially consisting of multiple lines (with line breaks
     * indicated by ctype='lb' x tags), break the translation strings into multiple
     * Strings (one per line), converting the x tag into \\n (at the end of each
     * String). Additionally convert any double quote characters in the translation
     * to \\&quot;.
     * @param in The input string to split into zero or more strings.
     * @return An array of zero or more strings, each terminated by an XLIFF
     *         x tag of ctype lb. 
     */
    private String[] getOutLines(String in) {
        String inStr = in;
        // If no meaningful string, return a zero-length array of String
        if (inStr == null || inStr.trim().length() == 0) {
            return new String[0];    // Return an empty string
        }
        
        String [] retArray;     // We'll return this array.
        
        // If the String contains no x tags of ctype lb, return a single string
        // in a one-element array.
        if (!lbMatcher.reset(inStr).find()) {
            retArray = new String[1];
            retArray[0] = TuStrings.unEscapeTuString(inStr);
            return retArray;
        }
        
        // We're still here. Assume that the input String has an x tag of ctype
        // lb.
        // Before splitting on the x tag: if the very last characters in the
        // inStr are "<x id='1' ctype='lb'/>", we will need to handle them
        // specially--replacing them with \\n explicitly.
        if (lbTailMatcher.reset(inStr).find()) {
            inStr = lbTailMatcher.group(1) + "\\n";
        }
        
        retArray = lbPattern.split(TuStrings.unEscapeTuString(inStr));
        
        // We need to append \\n to the end of every string except the last.
        // (I know this is probably terribly inefficient, but it is simple to
        // program ... and it probably isn't that much of a performance killer.)
        for (int i = 0; i < (retArray.length - 1); i++) {
            retArray[i] += "\\n";
        }
        
        return retArray;
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
     * @return the Java Properties (Resource Bundle) file type.
     */
    public FileType getFileType() {
        return FileType.PO;
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
