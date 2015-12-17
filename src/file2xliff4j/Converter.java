/*
 * Converter.java
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

/**
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public interface Converter {
    
    /** Used at the top of generated XML files: */
    public final static String xmlDeclaration =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n";
    
    /** Introduces the xliff element of an XLIFF document: */
    public final static String startXliff =
            "<xliff version=\"1.1\" xmlns=\"urn:oasis:names:tc:xliff:document:1.1\" "
            + "xmlns:lt=\"http://www.lingotek.com/\">\r\n";
    
    /** The xliff file associated with a native file has the same name as
     * the native file, with the following suffixed: */
    public final static String xliffSuffix = ".xliff";
    
    /** Some converters need to create an intermediate "temporary" skeleton 
     * file, which is later processed to create the final skeleton file.
     * This declares its suffix: */
    public final static String tSkeletonSuffix = ".tskeleton";

    /** Some converters need to create an intermediate "temporary" skeleton 
     * file for styles, which is later processed to help create the final 
     * skeleton file. This declares its suffix: */
    public final static String stylesTSkeletonSuffix = ".stylestskeleton";

    /** The skeleton file associated with a native file has the same name as
     * the native file, with the following suffixed: */
    public final static String skeletonSuffix = ".skeleton";

    /** The format file associated with a native file has the same name as
     * the native file, with the following suffixed: */
    public final static String formatSuffix = ".format";

    /** Block size constant for heirs to use in reading and writing files */
    static final int BLKSIZE = 8192;

    /** 
     * Convert a file from a Native format to XLIFF, or from XLIFF (back) to
     * the original native format.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the native document. If we are converting
     * from a native format to XLIFF, the language becomes the &lt;source&gt;
     * xml:lang in the XLIFF. If conversion is from XLIFF back to the native
     * format, the language selects which &lt;target&gt; (in the XLIFF's
     * translation-units) will provide the strings for the destination native
     * document.
     * @param phaseName The name of the phase to convert. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF. If converting from XLIFF back
     *        to a native format and the XLIFF target elements do not use the
     *        phase-name attribute, specify null for this parameter.
     *        <p><b>Special behavior:</b> If the phase name string consists
     *        entirely of numeric digits equivalent to an integer with value
     *        greater than 1 but less than or equal to maxPhase (see below), the 
     *        implementation may choose to search for targets with phase-names 
     *        of earlier numerically named phase names.
     * @param maxPhase The maximum phase number. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF.
     *        <p>If phaseName is specified as "0" and maxPhase is a non-negative 
     *        integer, search for the highest "numbered" phase, starting at 
     *        maxPhase, and searching down to phase "1".
     * @param nativeEncoding The encoding of the native document. If we are 
     * converting a native document to XLIFF, this parameter tells the converter
     * how to interpret the bytes read from the native document as it converts
     * to UTF-8 used by XLIFF. If conversion is from XLIFF back to the native
     * format, this parameter tells the converter what to convert the UTF-8-
     * encoding back to.
     * <p><i>Note:</i> If the native format offers no choice of encoding, then
     * this parameter's value is ignored.
     * @param nativeFileType The type of the native file. Examples: "HTML",
     *        "WORD". (Note: This parameter is needed only when converting
     *        to XLIFF, to provide a value for the datatype attribute of the
     *        XLIFF's file element.) If converting from XLIFF, the datatype
     *        is read from the XLIFF file; this parameter is ignored.
     * @param nativeFileName The name of the native file. If we are converting
     * from a native format to XLIFF, this specifies the name of the file to
     * convert to XLIFF. If conversion is from XLIFF back to the native format,
     * this parameter specifies the name of the file generated from XLIFF.
     * @param baseDir The directory (in the file system) from which input files
     * will be read, and to which output files will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries)
     * @param generatedFileName If this parameter is non-null, the converter
     *        implementation might write the name of the generated output file
     *        to this parameter (a StringWriter).
     * @param skipList A set of potential translatable structures to omit. (This
     *        is applicable to the import--conversion to XLIFF--of XML files,
     *        for example.).
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
            Set<XMLTuXPath> skipList) throws ConversionException;
    
    /** 
     * Convert a file from a Native format to XLIFF, or from XLIFF (back) to
     * the original native format.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the native document. If we are converting
     * from a native format to XLIFF, the language becomes the &lt;source&gt;
     * xml:lang in the XLIFF. If conversion is from XLIFF back to the native
     * format, the language selects which &lt;target&gt; (in the XLIFF's
     * translation-units) will provide the strings for the destination native
     * document.
     * @param phaseName The name of the phase to convert. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF. If converting from XLIFF back
     *        to a native format and the XLIFF target elements do not use the
     *        phase-name attribute, specify null for this parameter.
     *        <p><b>Special behavior:</b> If the phase name string consists
     *        entirely of numeric digits equivalent to an integer with value
     *        greater than 1 but less than or equal to maxPhase (see below), the 
     *        implementation may choose to search for targets with phase-names 
     *        of earlier numerically named phase names.
     * @param maxPhase The maximum phase number. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF.
     *        <p>If phaseName is specified as "0" and maxPhase is a non-negative 
     *        integer, search for the highest "numbered" phase, starting at 
     *        maxPhase, and searching down to phase "1".
     * @param nativeEncoding The encoding of the native document. If we are 
     * converting a native document to XLIFF, this parameter tells the converter
     * how to interpret the bytes read from the native document as it converts
     * to UTF-8 used by XLIFF. If conversion is from XLIFF back to the native
     * format, this parameter tells the converter what to convert the UTF-8-
     * encoding back to.
     * <p><i>Note:</i> If the native format offers no choice of encoding, then
     * this parameter's value is ignored.
     * @param nativeFileType The type of the native file. Examples: "HTML",
     *        "WORD". (Note: This parameter is needed only when converting
     *        to XLIFF, to provide a value for the datatype attribute of the
     *        XLIFF's file element.) If converting from XLIFF, the datatype
     *        is read from the XLIFF file; this parameter is ignored.
     * @param nativeFileName The name of the native file. If we are converting
     * from a native format to XLIFF, this specifies the name of the file to
     * convert to XLIFF. If conversion is from XLIFF back to the native format,
     * this parameter specifies the name of the file generated from XLIFF.
     * @param baseDir The directory (in the file system) from which input files
     * will be read, and to which output files will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries)
     * @param generatedFileName If this parameter is non-null, the converter
     *        implementation might write the name of the generated output file
     *        to this parameter (a StringWriter).
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
            StringWriter generatedFileName) throws ConversionException;

    /** 
     * Convert a file from a Native format to XLIFF, or from XLIFF (back) to
     * the original native format.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the native document. If we are converting
     * from a native format to XLIFF, the language becomes the &lt;source&gt;
     * xml:lang in the XLIFF. If conversion is from XLIFF back to the native
     * format, the language selects which &lt;target&gt; (in the XLIFF's
     * translation-units) will provide the strings for the destination native
     * document.
     * @param phaseName The name of the phase to convert. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF. If converting from XLIFF back
     *        to a native format and the XLIFF target elements do not use the
     *        phase-name attribute, specify null for this parameter.
     *        <p><b>Special behavior:</b> If the phase name string consists
     *        entirely of numeric digits equivalent to an integer with value
     *        greater than 1 but less than or equal to maxPhase (see below), the 
     *        implementation may choose to search for targets with phase-names 
     *        of earlier numerically named phase names.
     * @param maxPhase The maximum phase number. (This is meaningful
     *        only for conversions from XLIFF back to the native format, where
     *        there are multiple target elements for the same locale, differentiated
     *        only by the optional phase-name attribute.) This parameter is
     *        ignored if conversion is to XLIFF.
     *        <p>If phaseName is specified as "0" and maxPhase is a non-negative 
     *        integer, search for the highest "numbered" phase, starting at 
     *        maxPhase, and searching down to phase "1".
     * @param nativeEncoding The encoding of the native document. If we are 
     * converting a native document to XLIFF, this parameter tells the converter
     * how to interpret the bytes read from the native document as it converts
     * to UTF-8 used by XLIFF. If conversion is from XLIFF back to the native
     * format, this parameter tells the converter what to convert the UTF-8-
     * encoding back to.
     * <p><i>Note:</i> If the native format offers no choice of encoding, then
     * this parameter's value is ignored.
     * @param nativeFileType The type of the native file. Examples: "HTML",
     *        "WORD". (Note: This parameter is needed only when converting
     *        to XLIFF, to provide a value for the datatype attribute of the
     *        XLIFF's file element.) If converting from XLIFF, the datatype
     *        is read from the XLIFF file; this parameter is ignored.
     * @param nativeFileName The name of the native file. If we are converting
     * from a native format to XLIFF, this specifies the name of the file to
     * convert to XLIFF. If conversion is from XLIFF back to the native format,
     * this parameter specifies the name of the file generated from XLIFF.
     * @param baseDir The directory (in the file system) from which input files
     * will be read, and to which output files will be written.
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
            Notifier notifier) throws ConversionException;
    
    /** 
     * Return an object representing a format-specific (and converter-specific) 
     * property.
     * @param property The name of the property to return.
     * @return An Object that represents the property's value.
     */
    public Object getConversionProperty(String property);
    
    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return The file type that the converter handles.
     */
    public FileType getFileType ();
    
    /**
     * Set a format-specific property that might affect the way that the
     * conversion occurs.
     * @param property The name of the property
     * @param value The value of the property
     * @throws file2xliff4j.ConversionException
     *         If the property isn't recognized (and if it matters).
     */
    public void setConversionProperty(String property, Object value)
            throws ConversionException;
}
