/*
 * WinRcExporter.java
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
 * Export the segments for a specified translation from XLIFF to a Windows
 * RC file.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class WinRcExporter implements Converter {
    
    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    /**
     * Create a Windows RC file exporter
     */
    public WinRcExporter() { }

    // Matcher to match and capture information about the original input file name
    private Matcher fNameMatcher 
        = Pattern.compile("^(.*)\\.rc$").matcher("");
    
    // This matcher will identify lines with properties and placeholders
    private Matcher placeHolderMatcher 
        = Pattern.compile("<lTLt:tu id=(['\"])([-0-9A-Fa-f]+)\\1/>").matcher("");
    
    // Matcher for strings that contain \xabcd (where abcd are hex digits)
    private Matcher hexMatcher
        = Pattern.compile(".*\\\\x[0-9a-fA-F]{4}.*",Pattern.DOTALL).matcher("");
//    // Matcher for the Content-Type charset line:
//    private Matcher charsetMatcher
//        = Pattern.compile("^(.*Content-Type:\\s+text/plain;\\s+charset=)[-_a-zA-Z0-9]+(.*)$",
//            Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher("");
    
    /**
     * Convert one set of targets (in the translation units of an XLIFF file) 
     * to a Windows rc file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to substitute in the 
     *        output RC file. The language is used in constructing a unique name
     *        for the generated RC file. For example, if a language of ja_JP is 
     *        specified and the original template was named shapes.rc, the 
     *        exported rc file will be named shapes.ja_JP.rc.
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
     * @param nativeEncoding The desired encoding of the output RC file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, you may specify null for this parameter. (If null
     *        is specified, WinRcExporter will use UTF-8 for the output RC file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.WINRC.)
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
        
        String outFileNameOnly;  // Just the name (no directories) of the output file
        // Figure out a name for the output properties file.
        if (fNameMatcher.reset(nativeFileName).find()) {
            // The original input file name had a reasonable name
            outFileNameOnly = fNameMatcher.group(1) + "." + language.toString()
                + ".rc";
        }
        else {
            // Unable to make sense of the original file name, just tack the
            // language code and .rc on the end of the original
            outFileNameOnly = originalFileName + "." + language.toString()
                + ".rc";
        }
        
        String outRc = baseDir + File.separator + outFileNameOnly;
        
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
                new FileOutputStream(outRc), outCharset));

            String skelLine;
            
            // We need to replace the charset specification in the Content-Type
            // "header"
            boolean replacedCharset = false;  // Haven't replaced it yet ...

            // Read every line in the skeleton file
            while ((skelLine = skelRdr.readLine()) != null) {
                
//                // If this line contains the Content-Type ... charset line,
//                // update it to specify the charset we are using in the output
//                // file.
//                if (!replacedCharset && charsetMatcher.reset(skelLine).find()) {
//                    skelLine = charsetMatcher.group(1) + outCharset.toString()
//                        + charsetMatcher.group(2);
//                    replacedCharset = true;     // Don't check again.
//                }
                
                // If this one has a TU UUID placeholder, replace it with the target
                if (placeHolderMatcher.reset(skelLine).find()) {
                    String placeHolder = placeHolderMatcher.group(0);
                    String tuID = placeHolderMatcher.group(2);
                    String translation = tuMap.getTu(tuID);
                    
                    String outLine = TuStrings.unEscapeTuString(
                        TuPreener.removeCoreMarks(translation));
                    
                    // If the translation contains any double quotes, double them
                    // (In Windows RC files the double quote character is indicated
                    // by *two* double quote characters in succession
                    if (outLine.contains("\"")) {
                        outLine = outLine.replace("\"", "\"\"");
                    }
                    
                    // See if any of the characters in the line have values > 0x7F.
                    // If they do, let's (for now) convert them to \x-encodings,
                    // using 4 hex characters;
                    outLine = hexify(outLine);
                    
                    // If the output translation has some WinRc-style hex literals,
                    // prepend "L" to the output string
                    if (hexMatcher.reset(outLine).matches()) {
                        outLine = "L\"" + outLine + "\"";
                    }
                    else {
                        outLine = "\"" + outLine + "\"";
                    }
                    
                    // Write out the expanded line.
                    outWriter.write(skelLine.replace(placeHolder, outLine) + "\r\n");
                }
                else {
                    // Otherwise just copy the skeleton line to the output.
                    outWriter.write(skelLine + "\r\n");
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
     * to a Windows rc file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to substitute in the 
     *        output RC file. The language is used in constructing a unique name
     *        for the generated RC file. For example, if a language of ja_JP is 
     *        specified and the original template was named shapes.rc, the 
     *        exported rc file will be named shapes.ja_JP.rc.
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
     * @param nativeEncoding The desired encoding of the output RC file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, you may specify null for this parameter. (If null
     *        is specified, WinRcExporter will use UTF-8 for the output RC file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.WINRC.)
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
     * to a Windows rc file.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to substitute in the 
     *        output RC file. The language is used in constructing a unique name
     *        for the generated RC file. For example, if a language of ja_JP is 
     *        specified and the original template was named shapes.rc, the 
     *        exported rc file will be named shapes.ja_JP.rc.
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
     * @param nativeEncoding The desired encoding of the output RC file.
     *        <p><i>Warning:</i> If specified, the encoding must "make sense"--
     *        it must be able to represent both the original-language source
     *        strings (in the msgid lines of the PO file), as well as the
     *        target translation strings (that appear in the translated msgstr
     *        lines)!
     *        <p>If uncertain, you may specify null for this parameter. (If null
     *        is specified, WinRcExporter will use UTF-8 for the output RC file.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.WINRC.)
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
        return FileType.WINRC;
    }

    /**
     * Passed a String of characters, convert the string and return a version
     * where all characters of value > 0x7f are represented as 4-hex-digit
     * hex characters of the format \xabcd (where abcd represent hex digits).
     * @param inStr The input string to convert/hexify
     * @return the Hexified string
     */
    private String hexify(String inStr) {
        if (inStr == null || inStr.length() == 0) {
            return "";
        }

        StringBuilder outStr = new StringBuilder();
        
        // Check every character in the string.
        for (int i = 0; i < inStr.length(); i++) {
            int charVal = inStr.charAt(i);
            if (charVal < 128) {
                // Just append the character if in the 7-bit ASCII range
                outStr.append(inStr.charAt(i));
            }
            else {
                // Otherwise append \x1234 (where 1234 are 4 hex digits)
                outStr.append(String.format("\\x%04x", charVal));
            }
        }
        
        // Now return the (possibly converted) string:
        return outStr.toString();
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
