/*
 * ExcelImporter.java
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
import java.lang.*;
import java.nio.charset.*;
import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;
import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;

/**
 * The ExcelImporter is used to import Excel documents, converting them
 * to XLIFF.
 * 
 * @author Shane Perry &lt;shane@lingotek.com>
 *
 */
public class ExcelImporter extends OOoTextImporter implements Converter {
    
    /**
     * Constructor for the word importer
     */
    public ExcelImporter() {
    }
    
    /**
     * Convert a Excel document to XLIFF, creating xliff, skeleton and format 
     * files as output.
     * <p>In the present implementation, the ExcelImporter uses OpenOffice.org
     * to convert the Excel Document to an OpenOffice.org OpenDocument Text (ods)
     * document, then calls upon its superclass (OOoTextImporter) to complete the
     * conversion of the resulting file to XLIFF.
     * @param mode The mode of conversion (TO_XLIFF in this case).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for Excel documents.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. For this importer, use EXCEL.
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".xls")
     * @param baseDir The directory that contains the input xls file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files (and any other intermediate or
     * temporary files) will be written.
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
            String nativeFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {
        
        if (! mode.equals(ConversionMode.TO_XLIFF)) {
            throw new ConversionException("The Excel Importer supports"
                    + " only conversions from Excel to XLIFF.");
        }

        if (nativeFileType == null) {
            System.err.println("Required native file type parameter omitted.");
            throw new ConversionException("Required native file type parameter omitted.");
        }
        else if (nativeFileType == FileType.EXCEL) {
            // We need to save the native file type (Excel) of the original file
            // so that it will end up in the resulting XLIFF.
            // Note: xliffOriginalfileName is declared in the abstradt OdfImporter class.
            xliffOriginalFileName = nativeFileName;
        }

        // 1/8/6: WWhipple. The latest JOOConverter can't handle extended characters
        // in file names. Rather than take the time to match the filename with a
        // regex, let's just (always) rename it.
        File f = new File(baseDir + File.separator + nativeFileName);
        f.renameTo(new File(baseDir + File.separator + "$$tsjoof$$.xls"));
        
        // We will call OpenOffice.org to convert the Excel Document to an
        // OpenDocument Text file.
        OpenOfficeConnection connection = new SocketOpenOfficeConnection();
        try {
            String odsFileName = baseDir + File.separator + "$$tsjoof$$.xls" + ".ods";

            // FIXME: This registry should be created once and used over and over again
            DefaultDocumentFormatRegistry formatReg = new DefaultDocumentFormatRegistry();

            File inputFile = new File(baseDir + File.separator + "$$tsjoof$$.xls");
            File outputFile = new File(odsFileName);

            connection.connect();
            if (connection.isConnected()) {
                DocumentConverter converter = new OpenOfficeDocumentConverter(connection);
                converter.convert(inputFile, formatReg.getFormatByFileExtension("xls"),
                                  outputFile, formatReg.getFormatByFileExtension("ods"));
            }
            else {
                // Rename the original Excel spreadsheet back before exiting
                File ff = new File(baseDir + File.separator + "$$tsjoof$$.xls");
                ff.renameTo(new File(baseDir + File.separator + nativeFileName));
                
                throw(new OpenOfficeConnectException("Unable to connect to OpenOffice.org"
                    + " to convert Excel Document."));
            }

        }
        catch (java.net.ConnectException ce) {
            String tMessage = ce.getMessage();
            // Rename the original Excel spreadsheet back before exiting
            File ff = new File(baseDir + File.separator + "$$tsjoof$$.xls");
            ff.renameTo(new File(baseDir + File.separator + nativeFileName));
            throw(new OpenOfficeConnectException("Unable to communicate with OpenOffice" +
                    " to convert Excel Document: " + tMessage));
        }
        catch(Exception ex) {
            // Rename the original Excel spreadsheet back before exiting
            File ff = new File(baseDir + File.separator + "$$tsjoof$$.xls");
            ff.renameTo(new File(baseDir + File.separator + nativeFileName));
            throw(new ConversionException("Unable to convert Excel document to"
                    + " OpenDocument Text (ods) format."));
        }
        
        if (connection != null) {
            connection.disconnect();
        }

        // 1/8/6: WWhipple. Now rename both files back
        File ff = new File(baseDir + File.separator + "$$tsjoof$$.xls");
        ff.renameTo(new File(baseDir + File.separator + nativeFileName));
        File ffOdt = new File(baseDir + File.separator + "$$tsjoof$$.xls.ods");
        ffOdt.renameTo(new File(baseDir + File.separator + nativeFileName + ".ods"));

        // Now call (ods) superclass's convert method to complete the conversion.
        return super.convert(mode, language, null, 0, nativeEncoding,
                    nativeFileType, nativeFileName + ".ods", baseDir, notifier,
                boundary, generatedFileName);
    }

    /**
     * Convert a Excel document to XLIFF, creating xliff, skeleton and format 
     * files as output.
     * <p>In the present implementation, the ExcelImporter uses OpenOffice.org
     * to convert the Excel Document to an OpenOffice.org OpenDocument Text (ods)
     * document, then calls upon its superclass (OOoTextImporter) to complete the
     * conversion of the resulting file to XLIFF.
     * @param mode The mode of conversion (TO_XLIFF in this case).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for Excel documents.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. For this importer, use EXCEL.
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".xls")
     * @param baseDir The directory that contains the input xls file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files (and any other intermediate or
     * temporary files) will be written.
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
     * Convert a Excel document to XLIFF, creating xliff, skeleton and format 
     * files as output.
     * <p>In the present implementation, the ExcelImporter uses OpenOffice.org
     * to convert the Excel Document to an OpenOffice.org OpenDocument Text (ods)
     * document, then calls upon its superclass (OOoTextImporter) to complete the
     * conversion of the resulting file to XLIFF.
     * @param mode The mode of conversion (TO_XLIFF in this case).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for Excel documents.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. For this importer, use EXCEL.
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".xls")
     * @param baseDir The directory that contains the input xls file--from which
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
     * @return the Excel file type.
     */
    public FileType getFileType() {
        return FileType.EXCEL;
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
