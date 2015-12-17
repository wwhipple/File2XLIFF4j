/*
 * OOoTextImporter.java
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
import java.util.zip.*;

/**
 * The OOoTextImporter is used to import OpenOffice.org Text (odt)
 * documents, converting them to XLIFF.
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class OOoTextImporter extends OdfImporter implements Converter {
    
    private FileType myDataType = null;   // What kind of data do I convert?
    
    /**
     * Constructor for the OpenOffice.org Text importer
     */
    public OOoTextImporter() {
    }
    
    /**
     * Convert an OpenOffice.org text (odt), spreadsheet (ods), or presentations
     * (odp) file to XLIFF, creating xliff, skeleton and format files as output.
     * <p>In the present implementation, the OOoTextImporter extracts the
     * content.xml file from the input ODT file, then calls upon its superclass
     * (OdfImporter) to complete the conversion to XLIFF.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for OpenOffice.org text files.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If this importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".odt")
     * @param baseDir The directory that contains the input odt file--from which
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
            throw new ConversionException("OpenOffice.org Text Importer supports"
                    + " only conversions to XLIFF.");
        }

        if (nativeFileType == null) {
            System.err.println("Required native file type parameter omitted.");
            throw new ConversionException("Required native file type parameter omitted.");
        }
        else if ((nativeFileType == FileType.ODT)  // Text
            || (nativeFileType == FileType.ODS)    // Calc/Spreadsheet
            || (nativeFileType == FileType.ODP)){  // Presentations/Impress
            // We are actually converting from an OpenOffice.org text document
            // (rather than from something like Word.) In that case, we need
            // to set the inherited xliffOriginalFileName to nativeFileName
            // so that it can be set as the value of the "original" attribute
            // in the file element of the XLIFF later on.
            xliffOriginalFileName = nativeFileName;
        }

        // Extract the content.xml file from the odt file, so that we can call our
        // superclass to convert that to XLIFF.
        try {
            // Wordaround for WinDoze JDK's inability to handle Japanese kanji
            // characters in ZIP file names:
            File f = new File(baseDir + File.separator + nativeFileName);
            f.renameTo(new File(baseDir + File.separator + "$$tszf$$.odt"));
    
//            ZipFile odtFile = new ZipFile(baseDir + File.separator + nativeFileName);
            ZipFile odtFile = new ZipFile(baseDir + File.separator + "$$tszf$$.odt");
            ZipEntry content = new ZipEntry("content.xml");
            InputStream is = odtFile.getInputStream(content);
            OutputStream contentXml = new FileOutputStream(baseDir + File.separator 
                    + "content.xml");
            int n = 0; byte[] b = new byte[this.BLKSIZE];
            while ((n = is.read(b)) > 0) {
                contentXml.write(b, 0, n);
            }

            is.close();
            contentXml.flush();
            contentXml.close();
            
            // WWhipple (12/22/06): Things like page headers and footers are
            // stored in styles.xml. We need to extract styles.xml as well
            ZipEntry styles = new ZipEntry("styles.xml");
            InputStream sis = odtFile.getInputStream(styles);
            OutputStream stylesXml = new FileOutputStream(baseDir + File.separator 
                    + "styles.xml");
            n = 0; 
            while ((n = sis.read(b)) > 0) {
                stylesXml.write(b, 0, n);
            }

            sis.close();
            stylesXml.flush();
            stylesXml.close();
            
            odtFile.close();

            // Now rename the odt file back to its original name.
            File ff = new File(baseDir + File.separator + "$$tszf$$.odt");
            ff.renameTo(new File(baseDir + File.separator + nativeFileName));
        }
        catch(Exception ex) {
            ex.printStackTrace();
            throw(new ConversionException("Unable to extract content.xml from"
                      + " OpenOffice.org Text document."));

        }
        
        // Now call superclass's convert method to complete the conversion.
        // Be optimistic that it will succeed ...
        return super.convert(mode, language, null, 0, null,
                    nativeFileType, "content.xml", baseDir, notifier,
                boundary, generatedFileName);
    }

    /**
     * Convert an OpenOffice.org text (odt) file to XLIFF, creating xliff, 
     * skeleton and format files as output.
     * <p>In the present implementation, the OOoTextImporter extracts the
     * content.xml file from the input ODT file, then calls upon its superclass
     * (OdfImporter) to complete the conversion to XLIFF.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for OpenOffice.org text files.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If this importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".odt")
     * @param baseDir The directory that contains the input odt file--from which
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
     * Convert an OpenOffice.org text (odt) file to XLIFF, creating xliff, 
     * skeleton and format files as output.
     * <p>In the present implementation, the OOoTextImporter extracts the
     * content.xml file from the input ODT file, then calls upon its superclass
     * (OdfImporter) to complete the conversion to XLIFF.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter is
     *        ignored for OpenOffice.org text files.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If this importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input file. (It will likely be a
     *        file whose filename ends in ".odt")
     * @param baseDir The directory that contains the input odt file--from which
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
