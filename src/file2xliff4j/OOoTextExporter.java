/*
 * OOoTextExporter.java
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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Class to export XLIFF to an OpenOffice.org OpenDocument Text (odt), 
 * Spreadsheet (ods) or Presentation (odp) document.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class OOoTextExporter extends OdfExporter implements Converter {

    // For reading ...
    protected static final int BLKSIZE = 8192;

    private FileType myDataType = null;   // What kind of data do I convert?
    
    /** Creates a new instance of OdfExporter */
    public OOoTextExporter() { }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to a document in OpenOffice.org OpenDocument Text (odt) format. Use 
     * (besides the XLIFF file) the skeleton and format files that were 
     * generated when the XLIFF file was created.
     * <p>Note: This conversion actually uses the convert method of its superclass 
     * (OdfExporter) to generate a content.&lt;language&gt;.xml file--which is
     * a special target-language-specific version of the "standard" content.xml 
     * file that is found in ZIP-formated OpenOffice.org odt files. After the 
     * superclass creates the content.&lt;language&gt;.xml file, this converter
     * copies the original (used for import) odt file to one that inserts the
     * target-language between the "stem" and the odt extension of the file
     * name. It replaces the original content.xml (in the new odt copy) with
     * the contents of the new content.&lt;language&gt;.xml file.
     * @param mode The mode of conversion (FROM_XLIFF in this case).
     * @param language The language of the XLIFF target to use in constructing
     *        the ODF document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odt, the output file name
     *        is myfile.ja_jp.odt. (Note that the Java Locale's toString
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
     * @param nativeEncoding The encoding of the native document. This parameter
     *        is ignored for OpenDocument Format, since the content.xml file
     *        (which we will place in the new odt file) will always be encoded in
     *        UTF-8.
     * @param nativeFileType This parameter is ignored. For export, the original
     *        native file type is stored in the XLIFF, and is retrieved from that
     *        location.
     * @param nativeFileName The name of the original native document that was
     *        first converted to XLIFF. If it was an OpenOffice.org OpenDocument
     *        Text file, it ends in ".odt". (It might also end with something other
     *        than ".odt")
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

        ConversionStatus status = ConversionStatus.CONVERSION_SUCCEEDED; // so far
        /****************************************
         * V A L I D A T E    A R G S,   E T C. *
         ****************************************/
        if (! mode.equals(ConversionMode.FROM_XLIFF)) {
            throw new ConversionException("The OpenOffice.org Text Exporter supports"
                    + " only conversions from XLIFF to OpenDocument Text format.");
        }
        
        if ((language == null) 
                || (nativeFileName == null)
                || (nativeFileName.length() == 0)
                || (baseDir == null)
                || (baseDir.length() == 0)) {
            throw new ConversionException("Required parameter(s)"
                    + " omitted, incomplete or incorrect.");
        }

        // The most distant relative of the OdfExporter abstract ancestor class
        // should set the xliffOriginalFileName variable, so that it can be used
        // by ancestors as they do their part of the conversion from XLIFF to
        // the final format. If the original inport was an odt document (unlikely
        // at the time of this writine), xliffOriginalFileName will be null or
        // have length of zero. Otherwise we will set it with our native file name.
        if (xliffOriginalFileName == null || xliffOriginalFileName.length() == 0) {
            xliffOriginalFileName = nativeFileName;
        }
        
        // Start by having our superclass (odfExporter) create a language-
        // specific content.xml file that contains the new target strings. If
        // our superclass throws a ConversionException, we will just pass it on
        // to our caller.
        status = super.convert(mode, language, phaseName, maxPhase, nativeEncoding, 
            nativeFileType, xliffOriginalFileName, baseDir, notifier, boundary, null);
        
        /********************************************************************
         * Copy the original odt file into a language-specific odt file,
         * substituting the contents of the new contents.<language>.xml file
         * (created by our superclass) in place of the original contents.xml
         * file. (Note: as we copy the new contents.<language>.xml file into
         * the odt file, we will name the file contents.xml, so that 
         * OpenOffice.org will find it.)
         ********************************************************************/
        
        String oldOdtFileName = "";   // The one used during import to XLIFF
        String newOdtFileName = "";   // The one we will export to.
        
        if (xliffOriginalFileName.toLowerCase().endsWith(".odt")) { // Imported odt file!!
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName;
            
            // New odt file name will insert language code before .odt
            int extPos = xliffOriginalFileName.toLowerCase().lastIndexOf(".odt");
            newOdtFileName = baseDir + File.separator
                    + xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".odt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".odt");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".ods")) { // Imported ods file!!
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName;
            
            // New ods file name will insert language code before .ods
            int extPos = xliffOriginalFileName.toLowerCase().lastIndexOf(".ods");
            newOdtFileName = baseDir + File.separator
                    + xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".ods";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".ods");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".odp")) { // Imported odp file!!
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName;
            
            // New ods file name will insert language code before .ods
            int extPos = xliffOriginalFileName.toLowerCase().lastIndexOf(".odp");
            newOdtFileName = baseDir + File.separator
                    + xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".odp";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName.substring(0,extPos) + "."
                    + language.toString() + ".odp");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".ppt")) { // PowerPoint
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + ".odp";
            newOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString() + ".odp";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString() + ".odp");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".xls")) { // Excel
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + ".ods";
            newOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString() + ".ods";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString() + ".ods");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".doc")) { // Word
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + ".odt";
            newOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString() + ".odt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString() + ".odt");
            }
        }
        else if (xliffOriginalFileName.toLowerCase().endsWith(".rtf")) { // Rich Text Format
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + ".odt";
            newOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString() + ".odt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString() + ".odt");
            }
        }
        else { // Probably had no extension (!!)
            oldOdtFileName = baseDir + File.separator + xliffOriginalFileName;
//                    + ".odt";
            newOdtFileName = baseDir + File.separator + xliffOriginalFileName
                    + "." + language.toString(); // + ".odt";
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(xliffOriginalFileName
                    + "." + language.toString());
            }
        }
        
        try {
            byte[] byteBuf = new byte[BLKSIZE];
            int numRead;    // To count number of characters read
            
            // We'll READ from the old (original) odt/p/s file (which is a ZIP file):
            ZipFile odfZipFile = new ZipFile(oldOdtFileName);
            
            // We'll WRITE to the new odt/p/s file (also in ZIP format ...):
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(newOdtFileName));
//            ZipWriter zipWriter = new ZipWriter(zipOut);
            
            // Get an enumeration of the contents of the original input file.
            Enumeration all = odfZipFile.entries();
            
            // Go through every element in the input zip file (i.e. original odt file)
            while (all.hasMoreElements()) {
                ZipEntry nextFile = (ZipEntry) all.nextElement();
                
                // If we're getting the ODF file, replace it in the new file
                // with out new file
                if (nextFile.getName().equals("content.xml")) {
                    // Replace it with new content.xml
                    zipOut.putNextEntry(new ZipEntry("content.xml"));
                    
                    // Read the new content.<language>.xml from where our superclass
                    // put it.
                    InputStream inOdt = new FileInputStream(baseDir 
                        + File.separator + "content." + language.toString() + ".xml");
                    
                    // Read every byte and write it to the output ZIP file.
//                    int b;
//                    while ((b = inOdt.read()) != -1) {
//                        zipOut.write(b);
//                    }
//
                    while ((numRead = inOdt.read(byteBuf)) != -1) {  
                        zipOut.write(byteBuf,0,numRead);     
                    }
                    
//                    
                    inOdt.close();
                    zipOut.flush();
                    // Close entry after else ... (belowl)
                }
                // New (1/2/2007) code to handle styles.xml (optionally)
                else if (nextFile.getName().equals("styles.xml")) {
                    // We will write a styles.xml file
//                    zipOut.putNextEntry(new ZipEntry("styles.xml"));
//
//                    // See if a styles.ll_LL.xml file exists
//                    File stylesLl = new File(baseDir + File.separator 
//                        + "styles." + language.toString() + ".xml");
//                    
//                    InputStream inStyles = null;
//                    
//                    if (stylesLl.exists()) {
//                        // Read the styles.<language>.xml from where our superclass
//                        // put it.
//                        inStyles = new FileInputStream(baseDir 
//                            + File.separator + "styles." + language.toString() + ".xml");
//                    }
//                    else {
//                        // Otherwise, just copy the existing styles.xml from the old 
//                        // zip file.
//                        inStyles = odfZipFile.getInputStream(nextFile);
//                    }
//                        
//                    // Read every byte and write it to the output ZIP file.
////                    int b;
////                    while ((b = inStyles.read()) != -1) {
////                        zipOut.write(b);
////                    }
//                    
//                    while ((numRead = inStyles.read(byteBuf)) != -1) {  
//                        zipOut.write(byteBuf,0,numRead);     
//                    }
//                    
//                    inStyles.close();
//                    zipOut.flush();
//                    // Close entry after else ... (belowl)
                    
                    zipOut.putNextEntry(new ZipEntry("styles.xml"));

                    StringBuilder stylesBuf = new StringBuilder();

                    // See if a styles.ll_LL.xml file exists
                    File stylesLl = new File(baseDir + File.separator 
                        + "styles." + language.toString() + ".xml");
                    
                    InputStreamReader inStyles = null;
                    
                    if (stylesLl.exists()) {
                        // Read the styles.<language>.xml from where our superclass
                        // put it.
                        inStyles = new InputStreamReader(new FileInputStream(baseDir 
                            + File.separator + "styles." + language.toString() + ".xml"), 
                                Charset.forName("UTF-8"));
                    }
                    else {
                        // Otherwise, just copy the existing styles.xml from the old 
                        // zip file.
                        inStyles = new InputStreamReader(odfZipFile.getInputStream(nextFile),
                                Charset.forName("UTF-8"));
                    }

                    int b;
                    char[] charBuf = new char[BLKSIZE];
                    while ((numRead = inStyles.read(charBuf)) != -1) {
                        stylesBuf.append(charBuf,0, numRead);
                    }

                    // Change all fo:language, number:language, style:language-complex
                    // and style:language-asian attribute values to the target 
                    // locale's language component.
                    String stylesContent = stylesBuf.toString().replaceAll(
                            "(?s)(fo:language|number:language|style:language-complex|style:language-asian)=(['\"])[^'\"]*\\2",
                            "$1=$2" + language.getLanguage() + "$2");
                    
                    // Change all fo:country, number:country, style:country-complex
                    // and style:country-asian attribute values to the target 
                    // locale's country component.
                    stylesContent = stylesContent.replaceAll(
                            "(?s)(fo:country|number:country|style:country-complex|style:country-asian)=(['\"])[^'\"]*\\2",
                            "$1=$2" + language.getCountry() + "$2");
                    
                    // We have been working with chars, but need to write bytes
                    // to the ZIP file. A CharsetEncoder can convert from chars
                    // to bytes.
                    CharsetEncoder c2b = Charset.forName("UTF-8").newEncoder();
                    ByteBuffer stylesInBytes 
                        = c2b.encode(CharBuffer.wrap(stylesContent.subSequence(0, 
                            stylesContent.length()), 0, stylesContent.length()));
                    int numBytes = stylesInBytes.limit();
                    zipOut.write(stylesInBytes.array(), 0, numBytes);
//                    for (int i = 0; i < stylesContent.length(); i++) {
//                        zipOut.write(stylesContent.charAt(i));     
//                    }
                    
                    inStyles.close();
                    zipOut.flush();
                    // Close entry after else ... (belowl)
                }
                else if (nextFile.getName().equals("mimetype")) {
                    // The mimetype file is the first file. It is stored
                    // undeflated, per the OOo documentation
                    ZipEntry nextOutFile = new ZipEntry(nextFile.getName());
                    nextOutFile.setMethod(ZipEntry.STORED);
                    nextOutFile.setSize(nextFile.getSize());
                    nextOutFile.setCrc(nextFile.getCrc());
                    
                    zipOut.putNextEntry(nextOutFile);
                    
                    // Create a ZIP input stream to read from the ZIP entry we
                    // are copying to the new zip file.
                    InputStream zin = odfZipFile.getInputStream(nextFile);
//                    int b;
//                    while ((b = zin.read()) != -1) {
//                        zipOut.write(b);
//                    }

                    while ((numRead = zin.read(byteBuf)) != -1) {  
                        zipOut.write(byteBuf,0,numRead);     
                    }
                    
                    zipOut.flush();
                    zin.close();              // Close the input zip entry
                }
                else if (nextFile.getName().equals("meta.xml")) {
                    // The meta.xml file includes a cd:language element whose
                    // text is the ISO language code. We need to change the
                    // text of that element to indicate the language of the
                    // file we are exporting.
                    zipOut.putNextEntry(new ZipEntry("meta.xml"));
                    StringBuilder metaBuf = new StringBuilder();
                    
                    // Create a ZIP input stream to read the meta.xml file
                    // from the existing odt|s|p file.
                    InputStreamReader zin = new InputStreamReader(odfZipFile.getInputStream(nextFile));
                    int b;
                    char[] charBuf = new char[BLKSIZE];
                    while ((numRead = zin.read(charBuf)) != -1) {
                        metaBuf.append(charBuf,0, numRead);
                    }

                    String metaContent = metaBuf.toString().replaceFirst("(?s)^(.*?<dc:language>).*?(</dc:language>.*)$",
                            "$1" + language.toString() + "$2");
                    
                    for (int i = 0; i < metaContent.length(); i++) {
                        zipOut.write(metaContent.charAt(i));     
                    }
                    
                    zipOut.flush();
                    zin.close();
                    // Close entry after else ... (belowl)
                }
                else {
                    // Just write the old ZIP entry to the new ZIP entry
                    
                    // Create a new zip entry for the out file, giving it the same
                    // name as the nextFile we just read from the old file.
                    ZipEntry nextOutFile = new ZipEntry(nextFile.getName());
                    zipOut.putNextEntry(nextOutFile);
                    
                    // Create a ZIP input stream to read from the ZIP entry we
                    // are copying to the new zip file.
                    InputStream zin = odfZipFile.getInputStream(nextFile);
//                    int b;
//                    while ((b = zin.read()) != -1) {
//                        zipOut.write(b);
//                    }
                    while ((numRead = zin.read(byteBuf)) != -1) {  
                        zipOut.write(byteBuf,0,numRead);     
                    }

                    zipOut.flush();
                    zin.close();              // Close the input zip entry
                }
                zipOut.closeEntry();
            }
            
            // Now close the input and the output ZIP streams
            odfZipFile.close();
            zipOut.close();
        }
        catch(IOException e) {
            System.err.println("Cannot create (ZIP format) OpenOffice Text file " 
                    + newOdtFileName + ": " + e.getMessage());
            throw new ConversionException("Cannot create (ZIP format) OpenOffice Text file " 
                    + newOdtFileName + ": " + e.getMessage());
        }
        
        return status;
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to a document in OpenOffice.org OpenDocument Text (odt) format. Use 
     * (besides the XLIFF file) the skeleton and format files that were 
     * generated when the XLIFF file was created.
     * <p>Note: This conversion actually uses the convert method of its superclass 
     * (OdfExporter) to generate a content.&lt;language&gt;.xml file--which is
     * a special target-language-specific version of the "standard" content.xml 
     * file that is found in ZIP-formated OpenOffice.org odt files. After the 
     * superclass creates the content.&lt;language&gt;.xml file, this converter
     * copies the original (used for import) odt file to one that inserts the
     * target-language between the "stem" and the odt extension of the file
     * name. It replaces the original content.xml (in the new odt copy) with
     * the contents of the new content.&lt;language&gt;.xml file.
     * @param mode The mode of conversion (FROM_XLIFF in this case).
     * @param language The language of the XLIFF targets to use in constructing
     *        the ODF document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odt, the output file name
     *        is myfile.ja_jp.odt. (Note that the Java Locale's toString
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
     * @param nativeEncoding The encoding of the native document. This parameter
     *        is ignored for OpenDocument Format, since the content.xml file
     *        (which we will place in the new odt file) will always be encoded in
     *        UTF-8.
     * @param nativeFileType This parameter is ignored. For export, the original
     *        native file type is stored in the XLIFF, and is retrieved from that
     *        location.
     * @param nativeFileName The name of the original native document that was
     *        first converted to XLIFF. If it was an OpenOffice.org OpenDocument
     *        Text file, it ends in ".odt". (It might also end with something other
     *        than ".odt")
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
     * to a document in OpenOffice.org OpenDocument Text (odt) format. Use 
     * (besides the XLIFF file) the skeleton and format files that were 
     * generated when the XLIFF file was created.
     * <p>Note: This conversion actually uses the convert method of its superclass 
     * (OdfExporter) to generate a content.&lt;language&gt;.xml file--which is
     * a special target-language-specific version of the "standard" content.xml 
     * file that is found in ZIP-formated OpenOffice.org odt files. After the 
     * superclass creates the content.&lt;language&gt;.xml file, this converter
     * copies the original (used for import) odt file to one that inserts the
     * target-language between the "stem" and the odt extension of the file
     * name. It replaces the original content.xml (in the new odt copy) with
     * the contents of the new content.&lt;language&gt;.xml file.
     * @param mode The mode of conversion (FROM_XLIFF in this case).
     * @param language The language of the XLIFF targets to use in constructing
     *        the ODF document. The language is used in constructing a unique
     *        name for the output file. For example, if ja_JP is specified and
     *        the original input file was named myfile.odt, the output file name
     *        is myfile.ja_jp.odt. (Note that the Java Locale's toString
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
     * @param nativeEncoding The encoding of the native document. This parameter
     *        is ignored for OpenDocument Format, since the content.xml file
     *        (which we will place in the new odt file) will always be encoded in
     *        UTF-8.
     * @param nativeFileType This parameter is ignored. For export, the original
     *        native file type is stored in the XLIFF, and is retrieved from that
     *        location.
     * @param nativeFileName The name of the original native document that was
     *        first converted to XLIFF. If it was an OpenOffice.org OpenDocument
     *        Text file, it ends in ".odt". (It might also end with something other
     *        than ".odt")
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
     * @return the ODT file type.
     */
    public FileType getFileType() {
        if (this.myDataType != null) {
            return this.myDataType;
        }
        else {
            return FileType.ODT;
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
        if (property.equals("http://www.lingotek.com/converters/properties/datatype")) {

            if (value != null) {
                this.myDataType = (FileType)value;
            }
        }
        
        return;
    }
    
}
