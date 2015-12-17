/*
 * XulDtdExporter.java
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
 * Export an XLIFF target translation to a DTD used by XUL.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XulDtdExporter implements Converter {
    
    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    /**
     * Create an XulDtdExporter
     */
    public XulDtdExporter() { }
    
    // Create a file name matcher to look for language components in the original
    // file name. The following will match language components such as en_US_NewYork
    // (for example).
    private Matcher fNameMatcher 
        = Pattern.compile("^(.*)\\.dtd$").matcher("");
    
    // This matcher will identify lines with properties and placeholders
    private Matcher placeHolderMatcher 
        = Pattern.compile("^([^'\"]+(['\"]))<lTLt:tu id=([-0-9A-Fa-f]+)/>(\\2>)$").matcher("");
    
    /**
     * Convert one set of targets (in the translation units of an XLIFF file) 
     * back to an original-format DTD in the target language.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use in constructing
     *        the translated DTD. The language is used in constructing a unique 
     *        name for the output file. For example, if ja_JP is specified for an
     *        original file named aboutDialog.dtd, the output file name is 
     *        aboutDialog.ja_JP.dtd. 
     *        <p>(<i>Note:</i> The output file name is solely to avoid file name
     *        collisions with the original file. In the Firefox/Thunderbird "scheme"
     *        of things, the original and target versions of the DTD files have
     *        the same file names, but are placed in locale-specific jar files
     *        in locale-specific directories.)
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
     * @param nativeEncoding The encoding of the DTD file. This parameter is 
     *        ignored. (UTF-8 encoding is assumed.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.XULDTD.)
     * @param nativeFileName The name of the original file (previously imported
     *        to XLIFF--not including the parent directory components). 
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton) will be read, and to which the output file 
     *        will be written.
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
        
        if (!(new File(originalFileName).exists())) {
            throw new ConversionException("Cannot locate original DTD file "
                + originalFileName);
        }

        String outFileNameOnly;  // Just the name (no directories) of the output file
        // Figure out a name for the output properties file.
        fNameMatcher.reset(nativeFileName);
        if (fNameMatcher.find()) {
            // The original input file name had a reasonable name
            outFileNameOnly = fNameMatcher.group(1) + "." + language.toString()
                + ".dtd";
        }
        else {
            // Unable to make sense of the original file name, just tack the
            // language code and .dtd on the end of the original
            outFileNameOnly = originalFileName + "." + language.toString()
                + ".dtd";
        }
        
        String outDtd = baseDir + File.separator + outFileNameOnly;
        
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
        try {
            BufferedReader skelRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(inSkeleton), Charset.forName("UTF-8")));
            BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outDtd), Charset.forName("UTF-8")));

            String skelLine;

            // Read every line in the skeleton file
            while ((skelLine = skelRdr.readLine()) != null) {
                placeHolderMatcher.reset(skelLine);
                // If this one has a TU UUID placeholder, replace it with the target
                if (placeHolderMatcher.find()) {
                    String linePrefix = placeHolderMatcher.group(1);
                    String tuID = placeHolderMatcher.group(3);
                    String lineSuffix = placeHolderMatcher.group(4);
                    String translation = tuMap.getTu(tuID);
                    outWriter.write(linePrefix + TuStrings.unEscapeTuString(
                        TuPreener.getCoreText(translation)) + lineSuffix + "\n");
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
     * back to an original-format DTD in the target language.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use in constructing
     *        the translated DTD. The language is used in constructing a unique 
     *        name for the output file. For example, if ja_JP is specified for an
     *        original file named aboutDialog.dtd, the output file name is 
     *        aboutDialog.ja_JP.dtd. 
     *        <p>(<i>Note:</i> The output file name is solely to avoid file name
     *        collisions with the original file. In the Firefox/Thunderbird "scheme"
     *        of things, the original and target versions of the DTD files have
     *        the same file names, but are placed in locale-specific jar files
     *        in locale-specific directories.)
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
     * @param nativeEncoding The encoding of the DTD file. This parameter is 
     *        ignored. (UTF-8 encoding is assumed.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.XULDTD.)
     * @param nativeFileName The name of the original file (previously imported
     *        to XLIFF--not including the parent directory components). 
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF and skeleton) will be read, and to which the output file 
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
     * back to an original-format DTD in the target language.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.FROM_XLIFF in this case.
     * @param language The language of the XLIFF targets to use in constructing
     *        the translated DTD. The language is used in constructing a unique 
     *        name for the output file. For example, if ja_JP is specified for an
     *        original file named aboutDialog.dtd, the output file name is 
     *        aboutDialog.ja_JP.dtd. 
     *        <p>(<i>Note:</i> The output file name is solely to avoid file name
     *        collisions with the original file. In the Firefox/Thunderbird "scheme"
     *        of things, the original and target versions of the DTD files have
     *        the same file names, but are placed in locale-specific jar files
     *        in locale-specific directories.)
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
     * @param nativeEncoding The encoding of the DTD file. This parameter is 
     *        ignored. (UTF-8 encoding is assumed.)
     * @param nativeFileType This parameter is ignored. (It is assumed to be 
     *        FileType.XULDTD.)
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
        return FileType.XULDTD;
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
