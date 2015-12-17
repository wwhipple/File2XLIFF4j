/*
 * PPTExporter.java
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

import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;
import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;

/**
 * Class to export XLIFF to an Microsoft PowerPoint format.
 * @author weldon
 */
public class PPTExporter extends OOoTextExporter implements Converter {
    
    /** Creates a new instance of OdfExporter */
    public PPTExporter() { }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to a document in the original Microsoft PowerPoint format. Use (besides the XLIFF
     * file) the skeleton and format files that were generated when the XLIFF file was created.
     * <p>This implementation first calls its superclass (OOoTextExporter), which
     * exports the XLIFF to OpenDocument.org Text format. After that call returns,
     * this method uses OpenOffice.org to convert the OpenOffice.org OpenDocument 
     * Text (odp) file to PowerPoint format.
     * @param mode The mode of conversion (FROM_XLIFF in this case.).
     * @param language The language of the XLIFF targets to use in constructing
     *        the PowerPoint document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odp, the output file name
     *        is myfile.ja_jp.odp. (Note that the Java Locale's toString
     *        method lowercases the characters of language codes.)
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
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for PowerPoint documents.
     * @param nativeFileType The type of the original native file. (This value
     *        isn't really needed for export, for two reasons:
     * <ol>
     * <li>Because the PowerPoint Exporter is being called, we assume that the type
     *     is PPT.
     * <li>The XLIFF that will be exported includes the original native file
     *     type in the datatype attribute of its file element.
     * </ol>
     * This parameter is present solely for consistency with the convert method
     * of importers--importers need the file type to place in the XLIFF file.)
     * @param nativeFileName The name of the original file that was previously 
     *        converted to XLIFF. (It will likely be a file whose filename ends 
     *        in ".ppt"). This name is used for (at least) two purposes:
     * <ol>
     * <li>As a "root" file name to which suffixes (like ".xliff", ".skeleton",
     *     etc.) can be appended to construct the xliff, skeleton, etc. files
     *     that will be exported to the output document.
     * <li>(Hmmm... When I wrote this yesterday, I had two purposes in mind.
     *     ... but someone interrupted me, and today I can't recall what the
     *     second purpose was. Feel free to edit this if you can figure it
     *     out.)
     * </ol>
     * @param baseDir The directory that contains the input ppt file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files (and any other intermediate or
     * temporary files) will be written.
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

        ConversionStatus status = ConversionStatus.CONVERSION_SUCCEEDED; // so far
        
        /****************************************
         * V A L I D A T E    A R G S,   E T C. *
         ****************************************/
        if (! mode.equals(ConversionMode.FROM_XLIFF)) {
            throw new ConversionException("The PowerPoint Exporter supports"
                    + " only conversions from XLIFF to PowerPoint.");
        }
        
        if ((language == null) || (nativeFileName == null)
                || (nativeFileName.length() == 0) || (baseDir == null) 
                || (baseDir.length() == 0)) {
            System.err.println("One or more required parameters omitted.");
            throw new ConversionException("One or more required parameters omitted.");

        }
        
        // The most distant relative of the OdfExporter abstract ancestor class
        // should set the xliffOriginalFileName variable, so that it can be used
        // by ancestors as they do their part of the conversion from XLIFF to
        // the final format.
        if (xliffOriginalFileName == null || xliffOriginalFileName.length() == 0) {
            xliffOriginalFileName = nativeFileName;
        }

        StringWriter outName = new StringWriter();
        /** 
         * Have our parent (OOoTextExporter) class convert the XLIFF to an 
         * OpenOffice.org OpenDocument Text file. If it throws a ConversionException
         * we will just let it propagate to whoever called us.
         */
        status = super.convert(mode, language, phaseName, maxPhase, nativeEncoding, 
                nativeFileType, xliffOriginalFileName + ".odp", baseDir, notifier,
                boundary, outName);

        // 1/8/6: WWhipple. The latest JOOConverter can't handle extended characters
        // in file names. Rename the odt file (just generated above) to one that
        // OOo can handle:
        File f = new File(baseDir + File.separator + outName.toString());
        f.renameTo(new File(baseDir + File.separator + "$$tsjoof$$."
                + language.toString() + ".odp"));

        // The above will be the *file* to the OOo conversion call
        
        /** 
         * Now have OpenOffice.org convert the OpenOffice.org odp file to PowerPoint.
         */
        String docOutFileName = baseDir + File.separator + "$$tsjoof$$." + language.toString() + ".ppt";
        OpenOfficeConnection connection = new SocketOpenOfficeConnection();
	try {
		
            DefaultDocumentFormatRegistry formatReg = new DefaultDocumentFormatRegistry();
            
//            // Come up with a unique output file name that ends in "<language>.ppt"
//            String docOutFileName = "";
//            // If the name ends with .ppt ...
//            if (xliffOriginalFileName.endsWith(".ppt")) {
//                // Find where the .ppt suffix is
//                int extensionPos = xliffOriginalFileName.lastIndexOf(".ppt");
//                // Insert the language code before the .ppt suffix.
//                docOutFileName = baseDir + File.separator 
//                        + xliffOriginalFileName.substring(0,extensionPos)
//                        + "." + language.toString() + ".ppt";
//                if (generatedFileName != null) {
//                    // Tell caller the name of the output file (wo/directories)
//                    generatedFileName.write(xliffOriginalFileName.substring(0,extensionPos)
//                        + "." + language.toString() + ".ppt");
//                }
//            }
//            else {  // Doesn't end with .ppt (Hey, whassup?)
//                // Just tack the language and .ppt on the end.
//                docOutFileName = baseDir + File.separator + xliffOriginalFileName
//                        + "." + language.toString() + ".ppt";
//                if (generatedFileName != null) {
//                    // Tell caller the name of the output file (wo/directories)
//                    generatedFileName.write(xliffOriginalFileName
//                        + "." + language.toString() + ".ppt");
//                }
//            }
            
            // Then create a file object with the name we came up with above.
            File outputFile = new File(docOutFileName);

            // The odp input file (created by our superclass) is the original file name
            // with <language>.odp concatinated on the end (something like
            // somefileName.ppt.<language>.odp)
            File inputFile = new File(baseDir + File.separator + "$$tsjoof$$."
                    + language.toString() + ".odp");
				
            connection.connect();       // Connect to the running soffice.
            if (connection.isConnected()) {
                DocumentConverter converter = new OpenOfficeDocumentConverter(connection);
                converter.convert(inputFile, formatReg.getFormatByFileExtension("odp"),
                    outputFile, formatReg.getFormatByFileExtension("ppt"));
            }
            else {
                // Restore the target odp file back to its original name:
                File ff = new File(baseDir + File.separator + "$$tsjoof$$."
                        + language.toString() + ".odp");
                ff.renameTo(new File(baseDir + File.separator + outName.toString()));

                throw(new OpenOfficeConnectException("Unable to connect to OpenOffice.org"
                    + " to generate target PowerPoint Document."));
            }
        }
        catch (java.net.ConnectException ce) {
            String tMessage = ce.getMessage();
            
            // Restore the target odp file back to its original name:
            File ff = new File(baseDir + File.separator + "$$tsjoof$$."
                    + language.toString() + ".odp");
            ff.renameTo(new File(baseDir + File.separator + outName.toString()));

            throw(new OpenOfficeConnectException("Unable to communicate with OpenOffice.org" +
                    " to generate target PowerPoint Document: " + tMessage));
        }
        catch(Exception ex) {
            String tMessage = ex.getMessage();
            
            // Restore the target odp file back to its original name:
            File ff = new File(baseDir + File.separator + "$$tsjoof$$."
                    + language.toString() + ".odp");
            ff.renameTo(new File(baseDir + File.separator + outName.toString()));

            System.err.println("Unable to generate target PowerPoint document using OpenOffice.org.");
            System.err.println(tMessage);
            throw new ConversionException("Unable to generate target PowerPoint document using OpenOffice.org: "
                    + tMessage);
        }

        if (connection != null) {
            connection.disconnect();
        }

        // 1/8/7: Now we need to rename the generated ppt file to the name we really
        // want:
        // Come up with a unique output file name that ends in "<language>.ppt"
        String newDocOutFileName = "";
        // If the name ends with .ppt ...
        if (xliffOriginalFileName.toLowerCase().endsWith(".ppt")) {
            // Find where the .ppt suffix is
            int extensionPos = xliffOriginalFileName.toLowerCase().lastIndexOf(".ppt");
            // Insert the language code before the .ppt suffix.
            newDocOutFileName = baseDir + File.separator 
                    + xliffOriginalFileName.substring(0,extensionPos)
                    + "." + language.toString() + ".ppt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName.substring(0,extensionPos)
                    + "." + language.toString() + ".ppt");
            }
        }
        else {  // Doesn't end with .ppt (Hey, whassup?)
            // Just tack the language and .ppt on the end.
            newDocOutFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString() + ".ppt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString() + ".ppt");
            }
        }

        File ffNewDoc = new File(docOutFileName);
        ffNewDoc.renameTo(new File(newDocOutFileName));
        
        // Also rename the target odp file back to its original name:
        File ff = new File(baseDir + File.separator + "$$tsjoof$$."
                + language.toString() + ".odp");
//        ff.renameTo(new File(baseDir + File.separator + xliffOriginalFileName
//                + "." + language.toString() + ".odp"));
        ff.renameTo(new File(baseDir + File.separator + outName.toString()));
        
        return status;
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to a document in the original Microsoft PowerPoint format. Use (besides the XLIFF
     * file) the skeleton and format files that were generated when the XLIFF file was created.
     * <p>This implementation first calls its superclass (OOoTextExporter), which
     * exports the XLIFF to OpenDocument.org Text format. After that call returns,
     * this method uses OpenOffice.org to convert the OpenOffice.org OpenDocument 
     * Text (odp) file to PowerPoint format.
     * @param mode The mode of conversion (FROM_XLIFF in this case.).
     * @param language The language of the XLIFF targets to use in constructing
     *        the PowerPoint document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odp, the output file name
     *        is myfile.ja_jp.odp. (Note that the Java Locale's toString
     *        method lowercases the characters of language codes.)
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
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for PowerPoint documents.
     * @param nativeFileType The type of the original native file. (This value
     *        isn't really needed for export, for two reasons:
     * <ol>
     * <li>Because the PowerPoint Exporter is being called, we assume that the type
     *     is PPT.
     * <li>The XLIFF that will be exported includes the original native file
     *     type in the datatype attribute of its file element.
     * </ol>
     * This parameter is present solely for consistency with the convert method
     * of importers--importers need the file type to place in the XLIFF file.)
     * @param nativeFileName The name of the original file that was previously 
     *        converted to XLIFF. (It will likely be a file whose filename ends 
     *        in ".ppt"). This name is used for (at least) two purposes:
     * <ol>
     * <li>As a "root" file name to which suffixes (like ".xliff", ".skeleton",
     *     etc.) can be appended to construct the xliff, skeleton, etc. files
     *     that will be exported to the output document.
     * <li>(Hmmm... When I wrote this yesterday, I had two purposes in mind.
     *     ... but someone interrupted me, and today I can't recall what the
     *     second purpose was. Feel free to edit this if you can figure it
     *     out.)
     * </ol>
     * @param baseDir The directory that contains the input ppt file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files (and any other intermediate or
     * temporary files) will be written.
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
            StringWriter generatedFileName,
            Set<XMLTuXPath> skipList) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, nativeFileName, baseDir, notifier, boundary, 
                generatedFileName);
    }

     /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to a document in the original Microsoft PowerPoint format. Use (besides the XLIFF
     * file) the skeleton and format files that were generated when the XLIFF file was created.
     * <p>This implementation first calls its superclass (OOoTextExporter), which
     * exports the XLIFF to OpenDocument.org Text format. After that call returns,
     * this method uses OpenOffice.org to convert the OpenOffice.org OpenDocument 
     * Text (odp) file to PowerPoint format.
     * @param mode The mode of conversion (FROM_XLIFF in this case.).
     * @param language The language of the XLIFF targets to use in constructing
     *        the PowerPoint document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odp, the output file name
     *        is myfile.ja_jp.odp. (Note that the Java Locale's toString
     *        method lowercases the characters of language codes.)
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
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for PowerPoint documents.
     * @param nativeFileType The type of the original native file. (This value
     *        isn't really needed for export, for two reasons:
     * <ol>
     * <li>Because the PowerPoint Exporter is being called, we assume that the type
     *     is PPT.
     * <li>The XLIFF that will be exported includes the original native file
     *     type in the datatype attribute of its file element.
     * </ol>
     * This parameter is present solely for consistency with the convert method
     * of importers--importers need the file type to place in the XLIFF file.)
     * @param nativeFileName The name of the original file that was previously 
     *        converted to XLIFF. (It will likely be a file whose filename ends 
     *        in ".ppt"). This name is used for (at least) two purposes:
     * <ol>
     * <li>As a "root" file name to which suffixes (like ".xliff", ".skeleton",
     *     etc.) can be appended to construct the xliff, skeleton, etc. files
     *     that will be exported to the output document.
     * <li>(Hmmm... When I wrote this yesterday, I had two purposes in mind.
     *     ... but someone interrupted me, and today I can't recall what the
     *     second purpose was. Feel free to edit this if you can figure it
     *     out.)
     * </ol>
     * @param baseDir The directory that contains the input ppt file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files (and any other intermediate or
     * temporary files) will be written.
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
     * @return the PPT file type.
     */
    public FileType getFileType() {
        return FileType.PPT;
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
