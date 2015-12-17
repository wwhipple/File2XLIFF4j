/*
 * OdfImporter.java
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

import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import f2xutils.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;

/**
 * The OdfImporter is used to import Open Document Format to XLIFF.
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public abstract class OdfImporter implements Converter {
    
    /** The value assigned to the file element's "original" attribute in
     * the XLIFF
     */
    String xliffOriginalFileName = "";
    
    /**
     * Constructor for the ODF importer. 
     */
    public OdfImporter() {
    }
    
    /**
     * Convert the content.xml and styles.xml files (within an OpenOffice.org 
     * OpenDocument Format odt file) to XLIFF, creating xliff, skeleton and 
     * format files as output. 
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the input file. This parameter will
     *        normally be null, signifying that the default UTF-8 encoding be
     *        used.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If the ODF importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input ODF file. This will be either
     *        content.xml. (Additionally, the styles file is assumed to be 
     *        named styles.xml.)
     * @param baseDir The directory that contains the input ODF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     *
     * <ul>
     * <li>content.xml.xliff</li>
     * <li>content.xml.skeleton</li>
     * <li>content.xml.format</li>
     * </ul>
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
            throw new ConversionException("ODF Importer supports only conversions"
                    + " from Open Documet Format to XLIFF.");
        }
        
        // content.xml is "always" in UTF-8, so the expected value of null for
        // nativeEncoding signifies UTF-8. In the rare (if not non-existent)
        // case where nativeEncoding isn't null, use that value.
        Charset encoding = (nativeEncoding == null) ? 
                            Charset.forName("UTF-8") : nativeEncoding;
        
        // Create output stream writers for the SAX handler to write to.
        OutputStreamWriter xliffOut = null;
        OutputStreamWriter tskeletonOut = null;
        OutputStreamWriter formatOut = null;
        OutputStreamWriter stylesTSkeletonOut = null;

        if (nativeFileType == null) {
            System.err.println("Required native file type parameter omitted.");
            throw new ConversionException("Required native file type parameter omitted.");
        }
        else if (xliffOriginalFileName == null || xliffOriginalFileName.length() == 0) {
            System.err.println("Unable to determine the original file name for the XLIFF file.");
            throw new ConversionException("Unable to determine the original file name for the XLIFF file.");
        }
        
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + xliffOriginalFileName + Converter.xliffSuffix),
                    "UTF8");
            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + xliffOriginalFileName + Converter.tSkeletonSuffix),
                    "UTF8");
            formatOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + xliffOriginalFileName + Converter.formatSuffix),
                    "UTF8");
            stylesTSkeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + xliffOriginalFileName + Converter.stylesTSkeletonSuffix),
                    "UTF8");
            
        }
        catch (UnsupportedEncodingException e) {
            System.err.println("Unable to write XLIFF as UTF-8!!");
            System.err.println(e.getMessage());
            throw new ConversionException("Unable to write XLIFF as UTF-8: " + e.getMessage());
        }
        catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            throw new ConversionException(e.getMessage());
        }

        int tuDepth = 2;
        int stylesTuDepth = 2;

        // This will maintain the counters (etc.) between parsing of content.xml
        // and styles.xml:
        OdfStateObject odfState = new OdfStateObject();
        
        try {
            // Let's parse with the an XML Reader
            XMLReader parser = XMLReaderFactory.createXMLReader();

            // We need to verify that styles.xml even exists. Depending on whether
            // it does or not, we will pass a different last argument to the
            // odfHandler constructor
            String handlerMode = "";
            File stylesFile = new File(baseDir + File.separator + "styles.xml");
            if (stylesFile.exists()) {
                // If handlerMode is "content.xml", we will leave the XLIFF
                // "open" so that the handler for styles.xml can later add
                // TUs to the XLIFF. If "", the handler will write both the
                // XLIFF prolog and the epilog.
                handlerMode = "content.xml";
            }
            
            // On this first call, the xliffOriginalFileName will never be 
            // content.xml. On the second call it will be styles.xml, which
            // the handler will use as a cue not to open the file, etc.
            OdfHandler odfHandler = new OdfHandler(xliffOut, tskeletonOut, 
                    formatOut, language, nativeFileType.toString(), 
                    xliffOriginalFileName, boundary, odfState,
                    handlerMode);
            
            parser.setContentHandler(odfHandler);

            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            
            Reader inReader = new InputStreamReader(new FileInputStream(
                    baseDir + File.separator + nativeFileName), encoding);
            InputSource OdfIn = new InputSource(inReader);
            parser.parse(OdfIn);
            
            // Get the maxDepth of the TU tree
            tuDepth = odfHandler.getTuDepth();
        }
        catch(SAXException e) {
            System.err.println("XML parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("SAX parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading ODF input.");
            System.err.println("I/O error reading ODF input: " + e.getMessage());
        }

        try {
            /* Close the files we created above */
            tskeletonOut.close();
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating tskeleton for content.xml");
            System.err.println(e.getMessage());
        }
        
        // Now process the styles.xml file
        try {
            // Let's parse with the an XML Reader
            XMLReader parser = XMLReaderFactory.createXMLReader();
            
            // On this first call, the xliffOriginalFileName will never be 
            // content.xml. On the second call it will be styles.xml, which
            // the handler will use as a cue not to open the file, etc.
            
            OdfHandler odfHandler = new OdfHandler(xliffOut, stylesTSkeletonOut, 
                    formatOut, language, nativeFileType.toString(), 
                    "styles.xml", boundary, odfState, "styles.xml");
            
            parser.setContentHandler(odfHandler);

            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            
            Reader inReader = new InputStreamReader(new FileInputStream(
                    baseDir + File.separator + "styles.xml"), encoding);
            InputSource OdfIn = new InputSource(inReader);
            parser.parse(OdfIn);

            // Get the maxDepth of the TU tree
            stylesTuDepth = odfHandler.getTuDepth();
        }
        catch(SAXException e) {
            System.err.println("XML parser error (styles.xml).");
            System.err.println(e.getMessage());
            throw new ConversionException("SAX parser error (styles.xml): " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading ODF input (styles.xml).");
            System.err.println("I/O error reading ODF input (styles.xml): " + e.getMessage());
        }

        try {
            /* Close the files we created above */
            xliffOut.close();     
            stylesTSkeletonOut.close();
            formatOut.close();    
            
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating tskeleton for styles.xml");
            System.err.println(e.getMessage());
        }
        
        /* We have created temp skeleton files (intermediate skeleton files) for
         * both the content.xml and the styles.xml files.
         * We now need to merge those temporary skeleton files with the original 
         * input files to yield a "real" skeleton */

        // First content.xml
        try {

            // We'll read from the temporary skeleton
            FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator
                    + xliffOriginalFileName + Converter.tSkeletonSuffix);

            // We'll also read from the original input file
            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator
                    + nativeFileName);   // This is the content.xml file

            // We'll write to the (final) skeleton file
            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator
                    + xliffOriginalFileName + Converter.skeletonSuffix + ".1");

            // The OdfSkeletonMerger will do the deed.
            SkeletonMerger merger = new OdfSkeletonMerger();

            if (merger != null) {
                // Pass the merger a temporary file path "stem" before calling merger
                merger.setProperty("http://www.lingotek.com/converters/properties/skelTemp",
                        baseDir + File.separator + xliffOriginalFileName + ".content.skelpasses");
        
                merger.merge(tSkeletonIn, nativeIn, skeletonOut, encoding, tuDepth);
            }

            tSkeletonIn.close();
            nativeIn.close();
            skeletonOut.close();
        }
        catch(java.io.FileNotFoundException e) {
            System.err.println("Error creating final content.xml skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating final content.xml skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        
        // Before returning, see if the notifier is non-null. If it is, check the
        // skeleton to see if it is well-formed XML
        if (notifier != null) {
            String notice = "";
            File skelFile = new File(baseDir + File.separator
                    + xliffOriginalFileName + Converter.skeletonSuffix + ".1");
            // Does the skeleton even exist?
            if (!skelFile.exists()) {
                notice = "Document importer didn't create a skeleton file named "
                        + baseDir + File.separator
                        + xliffOriginalFileName + Converter.skeletonSuffix + ".1";
                System.err.println(notice);
                notifier.sendNotification("0001", "OdfImporter", Notifier.ERROR, notice);
            }
            // Is it well-formed?
            else {
                Charset charset = Charset.defaultCharset();
        
                try {
                    XMLReader parser = XMLReaderFactory.createXMLReader();

                    // We don't care about namespaces at the moment.
                    parser.setFeature("http://xml.org/sax/features/namespaces", false);

                    Reader inReader = new InputStreamReader(new FileInputStream(skelFile), charset);
                    InputSource skelIn = new InputSource(inReader);
                    if (skelIn != null) {
                        parser.parse(skelIn); 
                        inReader.close();
                    }
                    else {
                        notice = "Unable to read skeleton file " 
                                + baseDir + File.separator
                                + xliffOriginalFileName + Converter.skeletonSuffix + ".1";
                        System.err.println(notice);
                        notifier.sendNotification("0002", "OdfImporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    notice = "Skeleton file " + baseDir + File.separator
                            + xliffOriginalFileName + Converter.skeletonSuffix  + ".1"
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0003", "OdfImporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    notice = "Skeleton file " + baseDir + File.separator
                            + xliffOriginalFileName + Converter.skeletonSuffix + ".1"
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "OdfImporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    notice = "The validator of skeleton file " + baseDir + File.separator
                            + xliffOriginalFileName + Converter.skeletonSuffix + ".1"
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "OdfImporter", Notifier.ERROR, notice);
                }
            }
        }

        // Now repeat the above for styles.xml
        boolean haveStylesSkel = true;   // Assume we have a styles.xml skeleton
        
        // If the styles temporary skeleton is of length 0, then we found no translatable
        // text in the styles.xml, so don't bother further with that file.
        File tStylesFile = new File(baseDir + File.separator
                    + xliffOriginalFileName + Converter.stylesTSkeletonSuffix);
        if (tStylesFile.exists()) {
            if (tStylesFile.length() == 0L) {
                haveStylesSkel = false;
            }
        }
        
        // If we don't have a styles temporary skeleton, don't mess with styles.
        if (haveStylesSkel) {
            try {

                // We'll read from the temporary skeleton
                FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator
                        + xliffOriginalFileName + Converter.stylesTSkeletonSuffix);

                // We'll also read from the original input file
                FileInputStream nativeIn = new FileInputStream(baseDir + File.separator
                        + "styles.xml");   // This is the styles.xml file

                // We'll write to the (final) skeleton file
                FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator
                        + xliffOriginalFileName + Converter.skeletonSuffix + ".2");

                // The OdfSkeletonMerger will do the deed.
                SkeletonMerger merger = new OdfSkeletonMerger();

                if (merger != null) {
                    // Pass the merger a temporary file path "stem" before calling merger
                    merger.setProperty("http://www.lingotek.com/converters/properties/skelTemp",
                        baseDir + File.separator + xliffOriginalFileName + ".styles.skelpasses");
                    merger.merge(tSkeletonIn, nativeIn, skeletonOut, encoding, stylesTuDepth);
                }

                tSkeletonIn.close();
                nativeIn.close();
                skeletonOut.close();
            }
            catch(java.io.FileNotFoundException e) {
                System.err.println("Error creating final styles.xml skeleton file from temporary skeleton");
                System.err.println(e.getMessage());
            }
            catch(java.io.IOException e) {
                System.err.println("Error creating final styles.xml skeleton file from temporary skeleton");
                System.err.println(e.getMessage());
            }

            // Before returning, see if the notifier is non-null. If it is, check the
            // skeleton to see if it is well-formed XML
            if (notifier != null) {
                String notice = "";
                File skelFile = new File(baseDir + File.separator
                        + xliffOriginalFileName + Converter.skeletonSuffix + ".2");
                // Does the skeleton even exist?
                if (!skelFile.exists()) {
                    notice = "Document importer didn't create a skeleton file named "
                            + baseDir + File.separator
                            + xliffOriginalFileName + Converter.skeletonSuffix + ".2";
                    System.err.println(notice);
                    notifier.sendNotification("0001", "OdfImporter", Notifier.ERROR, notice);
                }
                // Is it well-formed?
                else {
                    Charset charset = Charset.defaultCharset();

                    try {
                        XMLReader parser = XMLReaderFactory.createXMLReader();

                        // We don't care about namespaces at the moment.
                        parser.setFeature("http://xml.org/sax/features/namespaces", false);

                        Reader inReader = new InputStreamReader(new FileInputStream(skelFile), charset);
                        InputSource skelIn = new InputSource(inReader);
                        if (skelIn != null) {
                            parser.parse(skelIn); 
                            inReader.close();
                        }
                        else {
                            notice = "Unable to read skeleton file " 
                                    + baseDir + File.separator
                                    + xliffOriginalFileName + Converter.skeletonSuffix + ".2";
                            System.err.println(notice);
                            notifier.sendNotification("0002", "OdfImporter", Notifier.ERROR, notice);
                        }
                    }
                    catch(SAXParseException e) {
                        notice = "Skeleton file " + baseDir + File.separator
                                + xliffOriginalFileName + Converter.skeletonSuffix + ".2"
                                + " is not well-formed at line "
                                + e.getLineNumber() + ", column " + e.getColumnNumber()
                                + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                        System.err.println(notice);
                        notifier.sendNotification("0003", "OdfImporter", Notifier.ERROR, notice);
                    }
                    catch(SAXException e) {
                        notice = "Skeleton file " + baseDir + File.separator
                                + xliffOriginalFileName + Converter.skeletonSuffix + ".2"
                                + " caused an XML parser error: " + e.getMessage()
                                + "\n" + this.getStackTrace(e);
                        System.err.println(notice);
                        notifier.sendNotification("0004", "OdfImporter", Notifier.ERROR, notice);
                    }
                    catch(IOException e) {
                        notice = "The validator of skeleton file " + baseDir + File.separator
                                + xliffOriginalFileName + Converter.skeletonSuffix + ".2"
                                + " experienced an I/O error while reading input: " + e.getMessage()
                                + "\n" + this.getStackTrace(e);
                        System.err.println(notice);
                        notifier.sendNotification("0005", "OdfImporter", Notifier.ERROR, notice);
                    }
                }
            }
        }

//        boolean haveStylesSkel = true;   // Assume we have a styles.xml skeleton
        Reader tSkelContent = null;     // content.xml temporary skeleton
        Reader tSkelStyles = null;      // styles.xml temporary skeleton
        // Combine the two skeletons into a single file:
        try {
            // Read the content.xml skeleton:
            tSkelContent = new InputStreamReader(new FileInputStream(baseDir 
                + File.separator + xliffOriginalFileName 
                + Converter.skeletonSuffix + ".1"),Charset.forName("UTF-8"));
        }
        catch (FileNotFoundException e) {
            System.err.println("OdfImporter: Cannot find temporary content.xml skeleton!");
            return ConversionStatus.ERROR_SKELETON_READ_FAILURE;
        }
        catch (IOException i) {
            System.err.println("OdfImporter: Error reading temporary content.xml skeleton!");
            return ConversionStatus.ERROR_SKELETON_READ_FAILURE;
        }

        if (haveStylesSkel) {
            ////////////////////////////////////////////////////////////
            // 2/27/7: Make sure a styles.xml skeleton file actually 
            // contains something--is of length > 0
            File skelFile2 = new File(baseDir + File.separator
                    + xliffOriginalFileName + Converter.skeletonSuffix + ".2");
            // Does the skeleton even exist?
            if (!skelFile2.exists()) {
                haveStylesSkel = false;
            }
            else if (skelFile2.length() == 0L) {  // Is it of zero length?
                haveStylesSkel = false;
            }
            /////////////////////////
            else {
                try {
                    // ... and the styles.xml skeleton:
                    tSkelStyles = new InputStreamReader(new FileInputStream(baseDir 
                        + File.separator + xliffOriginalFileName 
                        + Converter.skeletonSuffix + ".2"),Charset.forName("UTF-8"));
                }
                catch (FileNotFoundException e) {
                    System.err.println("OdfImporter: Cannot find temporary styles.xml skeleton!");
                    haveStylesSkel = false;      // ... But keep going
                }
                catch (IOException i) {
                    System.err.println("OdfImporter: Error reading temporary styles.xml skeleton!");
                    haveStylesSkel = false;      // ... But keep going
                }
            }
        }

        try {
            // ... writing to the (final) skeleton file
            Writer skeletonOut = new OutputStreamWriter(new FileOutputStream(baseDir 
                    + File.separator + xliffOriginalFileName 
                    + Converter.skeletonSuffix),Charset.forName("UTF-8"));
            
            if (haveStylesSkel) {    // We don't always have a styles skel ...
                // Write an XML declaration for the "super" skeleton
                skeletonOut.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<lt:skeleton xmlns:lt=\"http://www.lingotek.com/\">\n"
                        + "<lt:content_xml><![CDATA[\n");
            }

            // Then write the content.xml skeleton
            int num = 0; char[] buf = new char[BLKSIZE];
            while ((num = tSkelContent.read(buf)) != -1) {  // Copy every byte.
                skeletonOut.write(buf,0, num);
            }

            if (haveStylesSkel) {
                // Now close the content_xml element:
                skeletonOut.write("\n]]></lt:content_xml>\n<lt:styles_xml><![CDATA[\n");
                // Then write the styles.xml skeleton
                num = 0;
                while ((num = tSkelStyles.read(buf)) != -1) {  // Copy every byte.
                    skeletonOut.write(buf,0, num);
                }

                // Finally, end the styles.xml element, etc.
                skeletonOut.write("\n]]></lt:styles_xml>\n</lt:skeleton>\n");
            }
            skeletonOut.flush();
            
            tSkelContent.close();
            if (haveStylesSkel) {
                tSkelStyles.close();
            }
            skeletonOut.close();
        }
        catch (FileNotFoundException e) {
            System.err.println("OdfImporter: Cannot generate final skeleton: " 
                + e.getMessage());
            return ConversionStatus.ERROR_SKELETON_READ_FAILURE;
        }
        catch (IOException i) {
            System.err.println("OdfImporter: Cannot generate final skeleton: " 
                + i.getMessage());
            return ConversionStatus.ERROR_SKELETON_READ_FAILURE;
        }
        
        if (generatedFileName != null) {
            generatedFileName.write(xliffOriginalFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert the content.xml file (within an OpenOffice.org OpenDocument Format 
     * odt file) to XLIFF, creating xliff, skeleton and format files as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the input file. This parameter will
     *        normally be null, signifying that the default UTF-8 encoding be
     *        used.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If the ODF importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input ODF file. (Should likely be
     *        content.xml, but anything *might* work.)
     * @param baseDir The directory that contains the input ODF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:cd tu
     *
     * <ul>
     * <li>content.xml.xliff</li>
     * <li>content.xml.skeleton</li>
     * <li>content.xml.format</li>
     * </ul>
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
     * Convert the content.xml file (within an OpenOffice.org OpenDocument Format 
     * odt file) to XLIFF, creating xliff, skeleton and format files as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the input file. This parameter will
     *        normally be null, signifying that the default UTF-8 encoding be
     *        used.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This parameter 
     *        provides the value for the datatype attribute of the XLIFF's file 
     *        element. If the ODF importer is converting an intermediate file that
     *        began as (say) a Word file, the value should indicate the type of
     *        the original file ("WORD" for Word files).
     * @param nativeFileName The name of the input ODF file. (Should likely be
     *        content.xml, but anything *might* work.)
     * @param baseDir The directory that contains the input ODF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:cd tu
     *
     * <ul>
     * <li>content.xml.xliff</li>
     * <li>content.xml.skeleton</li>
     * <li>content.xml.format</li>
     * </ul>
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
     * Convert a stack trace to a string.
     * @param t Throwable whose stack trace will be returned
     * @return String that contains the strack trace.
     */
    private String getStackTrace(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);      // Print the stack trace
        pw.flush();
        sw.flush(); 
        return sw.toString();
    }    
}
