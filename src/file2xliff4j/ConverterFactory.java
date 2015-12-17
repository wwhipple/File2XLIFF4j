/*
 * ConverterFactory.java
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

import java.io.*;
import java.util.regex.*;

/**
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class ConverterFactory {
    
    /** Passed the "from" and "to" types of a conversion request, return
     * an appropriate converter that knows how to perform the conversion. 
     * @param fromType The type that will be converted from
     * @param toType Type of file that will be converted to
     * @return An instance of the requested converter
     * @throws file2xliff4j.ConversionException
     *         if unable to create the requested Converter
     */
    public static Converter createConverter(FileType fromType,
            FileType toType) throws ConversionException {
        
        // Third argument in the following calls is the "original" file type.
        // If we are converting to XLIFF (from some document format), then the
        // original file type will always be the same as the fromType.
        if (toType == FileType.XLIFF) {
            return ConverterFactory.createConverter(fromType, toType, fromType);
        }
        
        // However, when converting a translation of a PDF document (for example)
        // back from XLIFF, we will (currently) create some more used format
        // such as Word, ODT or Plaintext. If you want such a converter, don't
        // use this method. Instead, use the 3-argument version of createConverter.
        // (This 2-argument version assumes that the return trip will always be
        // to a document in the same format as the original document.)
        // Changed my mind. If the toType is PDF, make it HTML instead--at least
        // for now.
        else if (toType == FileType.PDF) {
            return ConverterFactory.createConverter(fromType, FileType.HTML, toType);
        }
        
        else {  // the fromType is XLIFF (and toType *isn't* PDF)
            return ConverterFactory.createConverter(fromType, toType, toType);
        }
    }
        
    /** Passed the "from" and "to" types of a conversion request, return
     * an appropriate converter that knows how to perform the conversion. 
     * <p><i>New functionality:</i> A new third parameter--originalType--is now
     * used for converters that convert from XLIFF to a format that is different
     * from the original format. This occurs, for example, with PDF, where the
     * export of target documents is currently to Word, ODT or plaintext. (Some
     * future release of the PDF Exporters might actually be smart enough to
     * export back to PDF format and have it look reasonable [sigh].)
     * @param fromType The type that will be converted from
     * @param toType Type of file that will be converted to
     * @return An instance of the requested converter
     * @throws file2xliff4j.ConversionException
     *         if unable to create the requested Converter
     */
    public static Converter createConverter(FileType fromType,
            FileType toType, FileType originalType) throws ConversionException {
        
        Converter retConverter = null;
        
        if (fromType == null) {
            throw new ConversionException("ConverterFactory.createConverter: fromType " 
                    + "argument has a null value. Cannot create a converter.");
        }
        if (toType == null) {
            throw new ConversionException("ConverterFactory.createConverter: toType " 
                    + "argument has a null value. Cannot create a converter.");
        }
        if (originalType == null) {
            throw new ConversionException("ConverterFactory.createConverter: originalType " 
                    + "argument has a null value. Cannot create a converter.");
        }
        
        if (fromType == FileType.XLIFF) {
            // Handle non-roundtrip exporters first:
            if (originalType == FileType.PDF) {
                switch (toType) {
                    case WORD:      return new PdfExporterToWord();
                    case ODT:       return new PdfExporterToOOoText();
                    case PLAINTEXT: return new PdfExporterToPlaintext();
                    case HTML:      return new PdfExporterToHtml();
                default:
                    throw new ConversionException("Currently unable"
                            + " to convert " + originalType.toString() 
                            + " translations from " + fromType.toString() 
                            + " to " + toType.toString());
                }
            }
            
            // Implied else (since the above if statement will always return
            // something ...):
            switch (toType) {
                case HTML:  return new HtmlExporter();
                
                case MSOFFICEDOC:
                case WORD:  return new WordExporter();
                
                case RTF:   return new RTFExporter();
                case EXCEL: return new ExcelExporter();
                case PPT:   return new PPTExporter();
                
                case ODS:
                            retConverter = new OOoTextExporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODS);
                            return retConverter;
                case ODP:
                            retConverter = new OOoTextExporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODP);
                            return retConverter;
                case ODT:   
                            retConverter = new OOoTextExporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODT);
                            return retConverter;
                
                case MIF:   return new MifExporter();
                
                case XML:   return new XMLExporter();

                case PLAINTEXT:  return new PlaintextExporter();
                
                case JAVA_PROPERTIES: return new JavaPropertiesExporter();

                case XULDTD: return new XulDtdExporter();

                case PO:    return new PoExporter();

                case WINRC: return new WinRcExporter();
                
                default:
                    throw new ConversionException("Currently unable"
                            + " to convert from " + fromType.toString() 
                            + " to " + toType.toString());
            }
        }
        else if (toType == FileType.XLIFF) {
            switch (fromType) {
                case HTML:  return new HtmlImporter();
                
                case MSOFFICEDOC:
                case WORD:  return new WordImporter();
                
                case RTF:   return new RTFImporter();
                case EXCEL: return new ExcelImporter();
                case PPT:   return new PPTImporter();
                
                case ODS:
                            retConverter = new OOoTextImporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODS);
                            return retConverter;
                case ODP:
                            retConverter = new OOoTextImporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODP);
                            return retConverter;
                case ODT:   
                            retConverter = new OOoTextImporter();
                            retConverter.setConversionProperty("http://www.lingotek.com/converters/properties/datatype",
                                    FileType.ODT);
                            return retConverter;
                
                case MIF:   return new MifImporter();
                
                case XML:   return new XMLImporter();
                
                case PLAINTEXT:  return new PlaintextImporter();
                
                case PDF:   return new PdfImporter();
                
                case JAVA_PROPERTIES: return new JavaPropertiesImporter();
                
                case XULDTD: return new XulDtdImporter();
                
                case PO:    return new PoImporter();
                
                case WINRC: return new WinRcImporter();
                
                default:
                    throw new ConversionException("Currently unable"
                            + " to convert from " + fromType.toString() 
                            + " to " + toType.toString());
            }
        }
        else {
            throw new ConversionException("Currently unable"
                    + " to convert from " + fromType.toString() 
                    + " to " + toType.toString());
        }
    }

    /** Passed the "from" and "to" types of a conversion request, as well as
     * an InputStream from which to read the from file's contents, return
     * an appropriate converter that knows how to perform the conversion. 
     * @param fromType The type that will be converted from
     * @param toType Type of file that will be converted to
     * @param signature An array of bytes at the beginning of the "from" file that
     *        will be converted to the new file format. If the conversion is from
     *        XLIFF (back) to a native type, the signature is the beginning of
     *        the XLIFF file.
     * @param extension The file extension (for differentiating among Microsoft
     *        Office Document types).
     * @return An instance of the requested converter
     * @throws file2xliff4j.ConversionException
     *         if unable to create the requested Converter
     */
    /* package */ static Converter createConverter(FileType fromType,
            FileType toType, byte[] signature, String extension) throws ConversionException {

        // Handle case where from-type is XLIFF and to-type is null: Read the 
        // XLIFF file to find the type originally imported. 
        if (fromType == FileType.XLIFF && toType == null) {
            // We are exporting a target from XLIFF to a native file type.
            FileType originalDataType = getDataTypeFromXliff(signature);
            if (originalDataType == null) {
                throw new ConversionException("Cannot identify the datatype "
                        + "of the source file previously converted to XLIFF.");
            }
            return ConverterFactory.createConverter(fromType,originalDataType);
        }
        
        // Does our detector agree with the caller's opinion on the filetype?
        boolean typeMatches = ConverterFactory.isFileType(signature, fromType);
        
        // Our detector agrees with the caller's assessment of the file type
        if (typeMatches 
                && !(fromType.equals(FileType.EXCEL)
                  || fromType.equals(FileType.MSOFFICEDOC)
                  || fromType.equals(FileType.WORD)
                  || fromType.equals(FileType.PPT))) {
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        // What type *is* this?
        FileType detectedType = ConverterFactory.identifyFormat(signature);
        if (detectedType == null) {  // Dunno
            // Just go with the caller's indication
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        // Try to differentiate among Microsoft Office Documents by extension, if
        // given.
        if (detectedType.equals(FileType.MSOFFICEDOC)) {
            if ((extension != null) && (extension.length() > 0)) {
                if (extension.toLowerCase().endsWith("doc")) {
                    detectedType = FileType.WORD;
                }
                else if (extension.toLowerCase().endsWith("ppt")) {
                    detectedType = FileType.PPT;
                }
                else if (extension.toLowerCase().endsWith("xls")) {
                    detectedType = FileType.EXCEL;
                }
            }
        }
        
        if (detectedType.equals(FileType.MSOFFICEDOC)
            && (fromType.equals(FileType.EXCEL)
                || fromType.equals(FileType.PPT)
                || fromType.equals(FileType.WORD))) {
            
            // All MS Office Documents *can* (sometimes) look alike.
            // Trust the user
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        // Hmmm... Let's go with the detected type. ... But first, make sure 
        // the detected "from" type isn't the same as the specified "to" type.
        if (detectedType.equals(toType)) {
            // They are the same; use the specified from type
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        // Let's guess that we're smarter than the client and use our detected
        // format.
        return ConverterFactory.createConverter(detectedType, toType);
    }

    /** Passed the "from" and "to" types of a conversion request, as well as
     * an InputStream from which to read the from file's contents, return
     * an appropriate converter that knows how to perform the conversion. 
     * @param fromType The type that will be converted from
     * @param toType Type of file that will be converted to
     * @param inStream An InputStream from which to read the contents of the
     *        "from" file we'll be converting.
     * @param extension The file extension (for differentiating among Microsoft
     *        Office Document types).
     * @return An instance of the requested converter
     * @throws file2xliff4j.ConversionException
     *         if unable to create the requested Converter
     */
    /* package */ static Converter createConverter(FileType fromType,
            FileType toType, InputStream inStream, String extension) throws ConversionException {

        // If no input stream with a signature to check, just call the factory
        // method as requested.
        if (inStream == null) {
            return ConverterFactory.createConverter(fromType, toType);
        }

        byte[] sigBytes = new byte[SIG_LENGTH];  // Allocate space to hold the signature
        
        try {
            int numRead = inStream.read(sigBytes);
        }
        catch(IOException e) {
            System.err.println("ConverterFactory.createConverter: Caught an IOException: "
                    + e.getMessage());
            System.err.println("Creating converter without autodetection.");
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        // We're still here. I guess we have a buffer that contains a signature ...
        return ConverterFactory.createConverter(fromType, toType, sigBytes, extension);

    }

    /** Passed the "from" and "to" types of a conversion request, as well as
     * the file name of the "from" file (with the full path specified) return
     * an appropriate converter that knows how to perform the conversion. 
     * @param fromType The type that will be converted from
     * @param toType Type of file that will be converted to
     * @param fromFileName The full path/file name of the file converting from
     * @return An instance of the requested converter
     * @throws file2xliff4j.ConversionException
     *         if unable to create the requested Converter
     */
    public static Converter createConverter(FileType fromType,
            FileType toType, String fromFileName) throws ConversionException {
        
        if (fromType == null) {
            throw new ConversionException("ConverterFactory.createConverter: fromType " 
                    + "argument has a null value. Cannot create a converter.");
        }
        if (toType == null) {
            throw new ConversionException("ConverterFactory.createConverter: toType " 
                    + "argument has a null value. Cannot create a converter.");
        }

        if ((fromFileName == null) || (fromFileName.trim().length() == 0)) {
            // No file name specified. Just create the specified converter.
            return ConverterFactory.createConverter(fromType, toType);
        }
        
        InputStream inStream = null;
        
        try  {
            inStream = new FileInputStream(fromFileName);
        }
        catch (FileNotFoundException e) {
            System.err.println("ConverterFactory.createConverter: File "
                    + fromFileName + " not found. Creating converter without "
                    + "autodection.");
            return ConverterFactory.createConverter(fromType, toType);
        }
               
        int lastDot = fromFileName.lastIndexOf(".");
        String extension = "";
        if (lastDot > -1) {
            extension = fromFileName.substring(lastDot); // May as well include the dot
        }
        
        Converter result = ConverterFactory.createConverter(fromType, toType, inStream,
                extension);
        
        if (inStream != null) {
            try {
                inStream.close();
            }
            catch (IOException e) { 
                /* Ignore the exception */
            }
        }
        
        return result;
    }

    /** A default number of bytes to attempt to read */
    public static final int SIG_LENGTH = 3072; 
    
    /**
     * Passed the beginning of an XLIFF file, retrieve the value of the datatype
     * attribute of the first file element in the signature. Based on its value
     * return the native file type of the file that created the XLIFF
     */
    /* package */ static FileType getDataTypeFromXliff(byte[] signature) {
        if (signature != null) {
            // We'll take what we can get as far as length is concerned.
            Matcher dataTypeMatcher 
                = Pattern.compile("<file\\s[^>]*datatype=(['\"])(.*?)\\1",
                    Pattern.DOTALL).matcher(new String(signature));
            if (dataTypeMatcher.find()) {
                String dataType = dataTypeMatcher.group(2);
                if (dataType == null)                { return null; }
                else if (dataType.equals("xml" ))    { return FileType.XML; }
                else if (dataType.equals("html"))    { return FileType.HTML; }
                else if (dataType.equals("mif"))     { return FileType.MIF; }
                else if (dataType.equals("rtf"))     { return FileType.RTF; }
                else if (dataType.equals("x-word"))  { return FileType.WORD; }
                else if (dataType.equals("x-excel")) { return FileType.EXCEL; }
                else if (dataType.equals("x-odp"))   { return FileType.ODP; }
                else if (dataType.equals("x-ods"))   { return FileType.ODS; }
                else if (dataType.equals("x-odt"))   { return FileType.ODT; }
                else if (dataType.equals("x-ppt"))   { return FileType.PPT; }
                else if (dataType.equals("x-pdf"))   { return FileType.PDF; }
                else if (dataType.equals("javapropertyresourcebundle"))   { return FileType.JAVA_PROPERTIES; }
                else if (dataType.equals("xmldtd"))  { return FileType.XULDTD; }
                else if (dataType.equals("po"))      { return FileType.PO; }
                else if (dataType.equals("winres"))  { return FileType.WINRC; }
            }
        }
        return null;          // Couldn't find the FileType
    }
    
    /**
     * Passed an array of initial bytes of a file or stream, return its
     * filetype.
     * <p><i>Note:</i>Many of the Microsoft formats (Word, Excel, PowerPoint,
     * for example), are indistinguishable in this converter. In many cases,
     * this method will just return FileType.MSOFFICEDOCUMENT if passed
     * a Microsoft format. More definitive checking forthcoming (?)
     * @param signature The opening bytes of a file or stream
     * @return Its FileType or null if unknown.
     */
    /* package */ static FileType identifyFormat(byte[] signature) {
        for (FileType ft : FileType.values()) {
            if (isFileType(signature, ft)) {
                return ft;
            }
        }
        return null;
    }

    /**
     * Passed a document's input stream, identify its format.
     * @param inStream A stream from which to read the document
     * @return Its FileType or null if unknown
     */
    /* package */ static FileType identifyFormat(InputStream inStream) {
        if (inStream == null) {
            return null;
        }

        byte[] sigBytes = new byte[SIG_LENGTH];  // Allocate space to hold the signature
        
        try {
            int numRead = inStream.read(sigBytes);
        }
        catch(IOException e) {
            System.err.println("ConverterFactory.identifyFormat: Caught an IOException: "
                    + e.getMessage());
            return null;
        }
        
        return ConverterFactory.identifyFormat(sigBytes);
    }

    /**
     * Passed the name of a file, read its initial bytes and identify its format.
     * @param fileName The name of the file (including full path name) 
     *              whose format will be confirmed.
     * @return Its FileType or null if unknown.
     */
    public static FileType identifyFormat(String fileName) {
        if ((fileName == null) || (fileName.trim().length() == 0)) {
            return null;
        }
        
        InputStream inStream = null;
        
        try  {
            inStream = new FileInputStream(fileName);
        }
        catch (FileNotFoundException e) {
            System.err.println("ConverterFactory.identifyFormat: File "
                    + fileName + " not found.");
            return null;
        }
        
        FileType result = ConverterFactory.identifyFormat(inStream);

        if (inStream != null) {
            try {
                inStream.close();
            }
            catch (IOException e) { /* Just ignore */    }
        }

        // Try to differentiate among Microsoft Office Documents by looking at
        // the "extension" at the end of the file name.
        if ((result != null) && result.equals(FileType.MSOFFICEDOC)) {
            if (fileName.toLowerCase().endsWith(".doc")) {
                result = FileType.WORD;
            }
            else if (fileName.toLowerCase().endsWith(".xls")) {
                result = FileType.EXCEL;
            }
            else if (fileName.toLowerCase().endsWith(".ppt")) {
                result = FileType.PPT;
            }
        }
        
        // We don't have a signature for Java property resource bundle'
        if (inStream == null || result == null) {
            if (fileName.toLowerCase().endsWith(".properties")) {
                result = FileType.JAVA_PROPERTIES;
            }
            else if (fileName.toLowerCase().endsWith(".dtd")) {
                result = FileType.XULDTD; // A xul dtd.
            }
            else if (fileName.toLowerCase().endsWith(".pot") 
                || fileName.toLowerCase().endsWith(".po")) {
                result = FileType.PO;     // For the time being, at least.
            }
            else if (fileName.toLowerCase().endsWith(".rc")) {
                result = FileType.WINRC;  // Windows resource file
            }
        }
        
        return result;
    }

    /**
     * Indicate whether or not the specified signature matches the file type
     * indicated
     * @param signature The opening bytes of a file or stream
     * @param fType The file type to be verified
     * @return true if the signature is of type fType, else false
     */
    /* package */ static boolean isFileType(byte[] signature, FileType fType) {
        if ((signature == null) || (signature.length == 0) 
            || (fType == null)) {
            System.err.println("ConverterFactory.isFileType: Parameters "
                    + "incomplete.");
            return false;
        }
        
        int len = 0;
        switch(fType) {
            case EXCEL:
                if ((signature.length > 7)
                    && (signature[0] == (byte)0x09) && (signature[1] == (byte)0x04)
                    && (signature[2] == (byte)0x06) && (signature[3] == (byte)0x00)
                    && (signature[4] == (byte)0x00) && (signature[5] == (byte)0x00)
                    && (signature[6] == (byte)0x10) && (signature[7] == (byte)0x00)) {
                    return true;
                }
                if (signature.length > 2112) {
                    String excelSig = new String(signature, 2080, "Foglio di lavoro Microsoft Exce".length());
                    if (excelSig.startsWith("Microsoft Excel 5.0 Worksheet")  // English
                        || excelSig.startsWith("Foglio di lavoro Microsoft Exce")) { // Spanish
                        return true;
                    }
                }
                // Polish Excel
                if (signature.length >= 2114 + "Biff5".length()) {
                    String wordSig = new String(signature, 2114, "Biff5".length());
                    if (wordSig.startsWith("Biff5")) {
                        return true;
                    }
                }
                // Italian Excel
                if (signature.length >= 2121 + "Biff5".length()) {
                    String wordSig = new String(signature, 2121, "Biff5".length());
                    if (wordSig.startsWith("Biff5")) {
                        return true;
                    }
                }

                // Microsoft Office Document
                if ((signature.length > 7)
                    && (signature[0] == (byte)0xd0) && (signature[1] == (byte)0xcf)
                    && (signature[2] == (byte)0x11) && (signature[3] == (byte)0xe0)
                    && (signature[4] == (byte)0xa1) && (signature[5] == (byte)0xb1)
                    && (signature[6] == (byte)0x1a) && (signature[7] == (byte)0xe1)) {
                    return true;
                }
                return false;

            case HTML:
                if (signature.length < 5) {
                    return false;
                }
                else if (signature.length < 256) {
                    len = signature.length;
                }
                else {
                    len = 256;    // We care only about the first few bytes.
                }
                String htmlSig = new String(signature, 0, len);
                if (htmlSig.length() > len) {
                    htmlSig = htmlSig.substring(0,len);
                }
                if (Pattern.compile("(.{0,3}?<\\?xml[^>]+?version[^>]*?>.*?)?<!DOCTYPE\\s+html.*",
                        Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(htmlSig).matches()) {
                    return true;
                }
                else if (Pattern.compile(".*<head\\b.*",
                        Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(htmlSig).matches()) {
                    return true;
                }
                else if (Pattern.compile(".*<title\\b.*",
                        Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(htmlSig).matches()) {
                    return true;
                }
                else if (Pattern.compile(".*<html\\b.*",
                        Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(htmlSig).matches()) {
                    return true;
                }
                return false;
            case MIF:
                if (signature.length < 12) {
                    return false;
                }
                String mifSig = new String(signature, 0, 12 );
                if (mifSig.matches("<MIFFile \\d\\.\\d.*")) {
                    return true;
                }

                return false;
                
            case ODT:
                if ((signature.length > 80)
                    && (signature[0] == 'P') && (signature[1] == 'K')
                    && (signature[2] == 0x03) && (signature[3] == 0x04)) { 
                
                    // We have a ZIP file, look some more:
                    String mimeType = new String(signature, 30, 8);
                    if (!mimeType.equals("mimetype")) {   // Can't be ODT'
                        return false;
                    }
                    
                    String vndSunXML = new String(signature, 50, "vnd.oasis.opendocument.text".length());
                    if (!vndSunXML.equals("vnd.oasis.opendocument.text")) {
                        return false;
                    }
                    
                    if (signature[77] == '-') {
                        // Not an ODT file, but a -template, -web or -master ...
                        return false;
                    }
                    
                    return true;
                }
                
                return false;

            case ODS:           // OpenOffice.org Cale (spreadsheet)
                if ((signature.length > 90)
                    && (signature[0] == 'P') && (signature[1] == 'K')
                    && (signature[2] == 0x03) && (signature[3] == 0x04)) { 
                
                    // We have a ZIP file, look some more:
                    String mimeType = new String(signature, 30, 8);
                    if (!mimeType.equals("mimetype")) {   // Can't be ODT'
                        return false;
                    }
                    
                    String vndSunXML = new String(signature, 50, "vnd.oasis.opendocument.spreadsheet".length());
                    if (!vndSunXML.equals("vnd.oasis.opendocument.spreadsheet")) {
                        return false;
                    }
                    
                    if (signature[84] == '-') {
                        // Not an ODS file, but a -template, -web or -master ...
                        return false;
                    }
                    
                    return true;
                }
                
                return false;

            case ODP:           // OpenOffice.org Impressa (Presentation)
                if ((signature.length > 90)
                    && (signature[0] == 'P') && (signature[1] == 'K')
                    && (signature[2] == 0x03) && (signature[3] == 0x04)) { 
                
                    // We have a ZIP file, look some more:
                    String mimeType = new String(signature, 30, 8);
                    if (!mimeType.equals("mimetype")) {   // Can't be ODT'
                        return false;
                    }
                    
                    String vndSunXML = new String(signature, 50, "vnd.oasis.opendocument.presentation".length());
                    if (!vndSunXML.equals("vnd.oasis.opendocument.presentation")) {
                        return false;
                    }
                    
                    if (signature[85] == '-') {
                        // Not an ODP file, but a -template
                        return false;
                    }
                    
                    return true;
                }
                
                return false;

            case RTF:
                if (signature.length < 11) {
                    return false;
                }
                else if (signature.length < 20) {
                    len = signature.length;
                }
                else {
                    len = 20;
                }

                String rtfSig = new String(signature, 0, len);
//                if (rtfSig.matches("\\{\\\\rtf[0-9]\\\\(?:ansi|mac|pca?).*")) {
//                    return true;
//                }
                if (rtfSig.startsWith("{\\rtf") 
                    && (signature[5] >= '0' && signature[5] <= '9')) {
                    String charSet = new String(rtfSig.substring(6,11));
                    if (charSet.startsWith("\\ansi")
                        || charSet.startsWith("\\mac")
                        || charSet.startsWith("\\pc")) {
                        return true;
                    }
                }
                return false;

            case PPT:
                // Microsoft Office Document
                if ((signature.length > 7)
                    && (signature[0] == (byte)0xd0) && (signature[1] == (byte)0xcf)
                    && (signature[2] == (byte)0x11) && (signature[3] == (byte)0xe0)
                    && (signature[4] == (byte)0xa1) && (signature[5] == (byte)0xb1)
                    && (signature[6] == (byte)0x1a) && (signature[7] == (byte)0xe1)) {
                    return true;
                }

                return false;
                
            case WORD:
                // Microsoft Office Document
                if ((signature.length > 7)
                    && (signature[0] == (byte)0xd0) && (signature[1] == (byte)0xcf)
                    && (signature[2] == (byte)0x11) && (signature[3] == (byte)0xe0)
                    && (signature[4] == (byte)0xa1) && (signature[5] == (byte)0xb1)
                    && (signature[6] == (byte)0x1a) && (signature[7] == (byte)0xe1)) {
                    return true;
                }
                // Microsoft Office Document
                if ((signature.length > 3)
                    && (signature[0] == (byte)0xfe) && (signature[1] == (byte)0x37)
                    && (signature[2] == (byte)0x00) && (signature[3] == (byte)0x23)) {
                    return true;
                }
                // Microsoft Office Document
                if ((signature.length > 5)
                    && (signature[0] == (byte)0xdb) && (signature[1] == (byte)0xa5)
                    && (signature[2] == '-') && (signature[3] == (byte)0x00)
                    && (signature[4] == (byte)0x00) && (signature[5] == (byte)0x00)) {
                    return true;
                }
                // Microsoft Word Document
                if ((signature.length > 3)
                    && (signature[0] == (byte)0x31) && (signature[1] == (byte)0xbe)
                    && (signature[2] == (byte)0x00) && (signature[3] == (byte)0x00)) {
                    return true;
                }
                if ((signature.length > 4)
                    && (signature[0] == 'P') && (signature[1] == 'O')
                    && (signature[2] == '^') && (signature[3] == 'Q')
                    && (signature[4] == '`')) {
                    return true;
                }
                if (signature.length > 2112) {
                    String wordSig = new String(signature, 2080, "Microsoft Word 6.0 Document".length());
                    if (wordSig.startsWith("Microsoft Word 6.0 Document")  // English
                        || wordSig.startsWith("Documento Microsoft Word 6")) { // Spanish
                        return true;
                    }
                }
                // Polish Word Document
                if (signature.length >= 2112 + "MSWordDoc".length()) {
                    String wordSig = new String(signature, 2112, "MSWordDoc".length());
                    if (wordSig.startsWith("MSWordDoc")) {
                        return true;      // Polish Word Doc
                    }
                }
                return false;

            case XLIFF:
                // XML Localisation Interchange File Format
                if (signature.length > 6) {   // Make sure we reference good offsets!!
                    String xliffSig = new String(signature);
                    if (Pattern.compile(".{0,3}?<\\?xml[^>]+?version[^>]*?>.*?<xliff.*", Pattern.DOTALL).matcher(xliffSig).matches()) {
                        return true;
                    }
                }
                
                return false;

            case XML:
                // Extensible Markup Language
                if (signature.length > 6) {   // Make sure we reference good offsets!!
                    String xmlSig = new String(signature);
                    if (Pattern.compile(".{0,3}?<\\?xml[^>]+?version[^>]*?>.*", Pattern.DOTALL).matcher(xmlSig).matches()) {
                        return true;
                    }
                }
                
                return false;
                
            case PDF:
                // Portable Document Format
                if (signature.length > 7) {
                    String pdfSig = new String(signature);
                    if (pdfSig.matches("(?s)%PDF-\\d+[.]\\d+.*")) {
                        return true;
                    }
                }
                
                return false;
                
            case MSOFFICEDOC:
                // Microsoft Office Document
                if ((signature.length > 7)
                    && (signature[0] == (byte)0xd0) && (signature[1] == (byte)0xcf)
                    && (signature[2] == (byte)0x11) && (signature[3] == (byte)0xe0)
                    && (signature[4] == (byte)0xa1) && (signature[5] == (byte)0xb1)
                    && (signature[6] == (byte)0x1a) && (signature[7] == (byte)0xe1)) {
                    return true;
                }
                // Microsoft Office Document
                if ((signature.length > 3)
                    && (signature[0] == (byte)0xfe) && (signature[1] == (byte)0x37)
                    && (signature[2] == (byte)0x00) && (signature[3] == (byte)0x23)) {
                    return true;
                }
                // Microsoft Office Document
                if ((signature.length > 5)
                    && (signature[0] == (byte)0xdb) && (signature[1] == (byte)0xa5)
                    && (signature[2] == '-') && (signature[3] == (byte)0x00)
                    && (signature[4] == (byte)0x00) && (signature[5] == (byte)0x00)) {
                    return true;
                }

                return false;
            
            default:
                return false;
        }
    }

    /**
     * Indicate whether or not the specified input stream matches the file type
     * indicated
     * @param inStream A stream from which to read the document
     * @param fType The file type to be verified
     * @return true if the signature is of type fType, else false
     */
    /* package */ static boolean isFileType(InputStream inStream, FileType fType) {
        if (inStream == null) {
            return false;
        }

        byte[] sigBytes = new byte[SIG_LENGTH];  // Allocate space to hold the signature
        
        int numRead = -1;
        try {
            numRead = inStream.read(sigBytes);
        }
        catch(IOException e) {
            System.err.println("ConverterFactory.isFileType: Caught an IOException: "
                    + e.getMessage());
            return false;
        }
        
        if (numRead < SIG_LENGTH) {
            byte[] sigBytes2 = new byte[numRead];
            for (int i = 0; i < numRead; i++) {
                sigBytes2[i] = sigBytes[i];
            }
            sigBytes = sigBytes2;
        }
        return ConverterFactory.isFileType(sigBytes, fType);
    }

    /**
     * Indicate whether or not the specified file is of the file type
     * indicated
     * @param fileName The name of the file (including full path name) 
     * whose format will be confirmed.
     * @param fType The file type to be verified
     * @return true if the signature is of type fType, else false
     */
    /* package */ static boolean isFileType(String fileName, FileType fType) {
        if ((fileName == null) || (fileName.trim().length() == 0)) {
            return false;
        }
        
        InputStream inStream = null;
        
        try  {
            inStream = new FileInputStream(fileName);
        }
        catch (FileNotFoundException e) {
            System.err.println("ConverterFactory.isFileType: File "
                    + fileName + " not found.");
            return false;
        }
               
        boolean result = ConverterFactory.isFileType(inStream,fType);
        
        if (inStream != null) {
            try {
                inStream.close();
            }
            catch (IOException e) { 
                /* Ignore the exception */
            }
        }
        
        return result;
    }

}
