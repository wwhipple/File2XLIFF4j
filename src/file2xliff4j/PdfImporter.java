/*
 * PdfImporter.java
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
import org.jpedal.PdfDecoder;
import org.jpedal.exception.PdfException;
import org.jpedal.exception.PdfSecurityException;
import org.jpedal.grouping.PdfGroupingAlgorithms;
import org.jpedal.objects.PdfFileInformation;

import org.jpedal.objects.PdfPageData;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.io.*;
import java.util.*;
import java.nio.charset.*;
import java.lang.*;
import java.util.regex.*;



/**
 * The PdfImporter imports Portable Document Format to (what else?) XLIFF.
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PdfImporter extends DefaultHandler implements Converter {
    
    private String ownerPW;       // Master password
    private String userPW;        // User password
    
    private boolean isEncrypted = false;
    private boolean isExtractionAllowed = true;
    
    private PdfFileInformation pdfFileInfo;
    
    private int longestLineLength = 0;

    // Initial set of HTML that can cause a <trans-unit> break. 
    private Set<String> tuBreakTags = new HashSet<String>(
            Arrays.asList(new String[] { 
            "br",
            "center",
            "div",
            "dfn",
            "dd",
            "dt",
            "dl",
            "form",  /* Added 1/4/7--ticket 687 */
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "html",
            "input", /* Added 1/4/7--ticket 687 */
            "li",
            "menu",
            "noscript",
            "ol",
            "option", /* Added 1/4/7--ticket 687 */
            "p",
            "pre",
            "q",
            "script",
            "select",
            "table",
            "tbody",
            "td",
            "textarea",
            "tfoot",
            "th",
            "thead",
            "title",
            "ul"
    }));
    
    
    /**
     * Create a PDF Importer
     */
    public PdfImporter() {
    }

    /**
     * Add an HTML tag to the set of tags that signal the start of a <trans-unit>
     * in the XLIFF generated from HTML.
     * @param tag HTML tag to add to the set (Examples: "p", "h1", "dl", ...)
     * @return true=the tag was added; false=not added (already present).
     */    
    public boolean addTuDelimiter(String tag) {
        return tuBreakTags.add(tag);
    }

    /** 
     * Return an object representing a format-specific (and converter-specific) 
     * property.
     * @param property The name of the property to return.
     * @return An Object that represents the property's value.
     */
    public Object getConversionProperty(String property) {

        // Return the length of the longest line if requested
        if (property.equals("http://www.lingotek.com/converters/properties/longestlinelength")) {
            return Integer.toString(this.longestLineLength);
        }
        
        return null;
    }

    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the PDF file type.
     */
    public FileType getFileType() {
        return FileType.PDF;
    }

    /**
     * Remove an HTML tag from the set of tags that signal the start of a <trans-unit>
     * in XLIFF generated from the HTML.
     * @return an array of Strings containing the current list of tags that start
     * <trans-unit> in XLIFF
     */    
    public String[] getTuDelimiterList() {
        if (tuBreakTags.isEmpty()) {
            return new String[0];    // No delimiters; return a zero-length array.
        }
        else { // Return an array of all the HTML tags that are delimiters
            String [] returnTags = new String[tuBreakTags.size()];
            Iterator tags = tuBreakTags.iterator();
            int i = 0;  // Indexes the returnTags array.
            while (tags.hasNext()) {
                returnTags[i] = (String)tags.next();
                i++;    // Ready to hold the next tag
            }
            Arrays.sort(returnTags);
            return returnTags;
        }
    }
    
    /**
     * Convert a PDF file to XLIFF, creating (in some future release?) xliff, 
     * skeleton and format files as output.
     * @param mode The mode of conversion (to XLIFF in this case).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This value is
     *        ignored for PDF files.
     * @param nativeFileType The type of the native file. This value must be
     *        "PDF". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input PDF file (without directory
     *        prefix). 
     * @param baseDir The directory that contains the input PDF file--from which
     *        we will read the input file. This is also the directory in which 
     *        the output xliff, skeleton and format files will be written. The 
     *        output files will be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton (future?)</li>
     * <li><i>nativeFileName</i>.format (future?)</li>
     * </ul>
     *        where <i>nativeFileName</i> is the file name specified in the 
     *        nativeFileName parameter.
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
            throw new ConversionException("PDF Importer supports only conversions"
                + " from PDF to XLIFF.");
        }
        
        if ((nativeFileType == null) || (! nativeFileType.equals(FileType.PDF))) {
            nativeFileType = FileType.PDF;    // Let's live dangerously ...
        }
        
        if (boundary == null) {
            boundary = SegmentBoundary.SENTENCE;  // Default to sentence 
        }
        
        // We have seen that Java sometimes has trouble with non-ASCII characters 
        // file names, so let's rename the input file to some known name.
        String pdfFileName = baseDir + File.separator + nativeFileName;
        File f = new File(pdfFileName);
        String tempPdfFileName = baseDir + File.separator + "zzYZxtsjoofzzYZx.pdf";
        f.renameTo(new File(tempPdfFileName));
        int retCode = 0;
        
        // Let's begin by (for now) calling jPedal to convert the PDF document
        // document to XML ...
            retCode = pdfToXml("zzYZxtsjoofzzYZx.pdf", nativeFileName + ".xml", baseDir);
        
        if (retCode != 0) {
            // Restore original file name before leaving
            File ff = new File(tempPdfFileName);
            ff.renameTo(new File(pdfFileName));
            throw(new ConversionException("Unable to convert PDF document "
                    + nativeFileName + " to (intermediate) XML format."));
        }

        // Whew (!!) We made it. Now rename both files back
        File ff = new File(tempPdfFileName);
        ff.renameTo(new File(pdfFileName));

        // Create output stream writers for the SAX handler to write to.
        OutputStreamWriter xliffOut = null;
        OutputStreamWriter tskeletonOut = null;
        OutputStreamWriter formatOut = null;
        
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.xliffSuffix),
                    "UTF8");
//            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
//                    baseDir + File.separator + nativeFileName + Converter.tSkeletonSuffix),
//                    "UTF8");
            // For early versions, let's write directly to the skeleton file.
            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.skeletonSuffix),
                    "UTF8");
            formatOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.formatSuffix),
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
        
        XMLReader parser = null;
        Charset headerEncoding = null;
        
        try {
            // Create a SAX parser to read the XML file
            parser = XMLReaderFactory.createXMLReader();
            
            PdfHandler handler = new PdfHandler(tuBreakTags, xliffOut, 
                    tskeletonOut, formatOut, language, 
                    nativeFileType.toString(), nativeFileName, boundary);
            parser.setContentHandler(handler);
            
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);

            // The sax parser will read from this:
            Reader inReader = new InputStreamReader(new FileInputStream(
                baseDir + File.separator + nativeFileName + ".xml"), Charset.forName("UTF-8"));
            
            InputSource xmlIn = new InputSource(inReader);
            parser.parse(xmlIn);
            longestLineLength = handler.getLongestLineLength();
        }
        catch(SAXException e) {
            System.err.println("SAX parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("SAX parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading PDF file's XML input.");
            System.err.println(e.getMessage());
            throw new ConversionException("I/O error reading PDF file's XML input: " + e.getMessage());
        }
        
        try {
            /* Close the files we created above */
            xliffOut.close();
            tskeletonOut.close();
            formatOut.close();

//            /* We have created a temp skeleton file (an intermediate skeleton file).
//             * We now need to merge the temporary skeleton with the original input file to
//             * yield a "real" skeleton */
//            
//            // We'll read from the temporary skeleton
//            FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator 
//                    + nativeFileName + Converter.tSkeletonSuffix);
//            
//            // We'll also read from the original input file
//            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator 
//                    + nativeFileName + ".xml");
//
//            // We'll write to the (final) skeleton file
//            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator 
//                    + nativeFileName + Converter.skeletonSuffix);
//
//            // The PdfSkeletonMerger will do the deed.
//            SkeletonMerger merger = new PdfSkeletonMerger();
//
//            if (merger != null) {
//                // Before merging, pass the SkeletonMerger the list of TU break
//                // tags
//                merger.setProperty("http://www.lingotek.com/converters/properties/breaktags",
//                        this.getTuDelimiterList());
//                
//                merger.merge(tSkeletonIn, nativeIn, skeletonOut, Charset.forName("UTF-8"));
//            }
//
//            tSkeletonIn.close();
//            nativeIn.close();
//            skeletonOut.close();
        }
        catch(java.io.FileNotFoundException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        
        // If caller wants to know the name of the XLIFF file, tell her.
        if (generatedFileName != null) {
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert a PDF file to XLIFF, creating (in some future release?) xliff, 
     * skeleton and format files as output.
     * @param mode The mode of conversion (to XLIFF in this case).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This value is
     *        ignored for PDF files.
     * @param nativeFileType The type of the native file. This value must be
     *        "PDF". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input PDF file (without directory
     *        prefix). 
     * @param baseDir The directory that contains the input PDF file--from which
     *        we will read the input file. This is also the directory in which 
     *        the output xliff, skeleton and format files will be written. The 
     *        output files will be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton (future?)</li>
     * <li><i>nativeFileName</i>.format (future?)</li>
     * </ul>
     *        where <i>nativeFileName</i> is the file name specified in the 
     *        nativeFileName parameter.
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
     * Convert a PDF file to XLIFF, creating (in some future release?) xliff, 
     * skeleton and format files as output.
     * @param mode The mode of conversion (to XLIFF in this case).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This value is
     *        ignored for PDF files.
     * @param nativeFileType The type of the native file. This value must be
     *        "PDF". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input PDF file (without directory
     *        prefix). 
     * @param baseDir The directory that contains the input PDF file--from which
     *        we will read the input file. This is also the directory in which 
     *        the output xliff, skeleton and format files will be written. The 
     *        output files will be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton (future?)</li>
     * <li><i>nativeFileName</i>.format (future?)</li>
     * </ul>
     *        where <i>nativeFileName</i> is the file name specified in the 
     *        nativeFileName parameter.
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
    
    private final static int DECODER_INIT_ERROR = 1;
    private final static int DECODER_EXTRACTION_NOT_ALLOWED_ERROR = 2;
    private final static int DECODER_DECRYPTION_NOT_SUPPORTED_ERROR = 3;
    private final static int DECODER_PAGE_DECODING_ERROR = 4;
    
    /**
     * Convert a PDF file to an XML file, using jPedal
     * @param inPdfFileName The name (with no parent directory information) of
     *        a file to read the PDF file from.
     * @param outXmlFileName The name of the XML file to write to.
     * @param baseDir The fully qualified directory name that contains the input
     *        PDF file and to which the output HTML will be written.
     * @return 0 if conversion is successful, otherwise non-zero.
     * @throws file2xliff4j.PdfPasswordException
     *        if file is encrypted, extraction is not allowed, and no password(s)
     *        were provided.
     */
    private int pdfToXml(String inPdfFileName, String outXmlFileName, 
            String baseDir) throws PdfPasswordException {
        
        int retVal = 0;             // OK so far
        String text = null;         // The extracted text from one page.
        PdfDecoder decoder = null;  // The iPedal decoder
        String[] values;
        String[] fields;

        // Get file XML metadata
        String metaData = null;

        try {
            // Create a new PDF decoder, telling it that the output won't be
            // rendered ...
            decoder = new PdfDecoder(false); // false == not to be rendered

            // Extract only the text (for now, at least).
            decoder.setExtractionMode(PdfDecoder.TEXT);
            
            // true means widths in data are critical ...
            decoder.init(true);   // For text extraction only.
        
            // Open the file and read metadata including pages in the file
            decoder.openPdfFile(baseDir + File.separator + inPdfFileName);

            // Is this file encrypted?
            isEncrypted = decoder.isEncrypted();

            // Is extraction allowed?
            isExtractionAllowed = decoder.isExtractionAllowed();
    
            if (isEncrypted && !isExtractionAllowed) {
                // Try setting passwords
                if (this.ownerPW != null) {
                    decoder.setEncryptionPassword(this.ownerPW);
                }
                else if (this.userPW != null) {
                    decoder.setEncryptionPassword(this.userPW);
                }

                // Now see if extraction is allowed
                isExtractionAllowed = decoder.isExtractionAllowed();
                if (!isExtractionAllowed) {
                    throw new PdfPasswordException("Encrypted PDF file "
                            + "requires valid owner or user password.");
                }
            }
            
            // Get information about the file (if avaliable)
            pdfFileInfo = decoder.getFileInformationData(); // Might be null

        }
        catch (PdfException e) {
            retVal = DECODER_INIT_ERROR;
            System.err.println("Error creating/initializing PDF decoder: " 
                    + e.getMessage());
            return retVal;
        }
        
        // See if extraction is allowed
        if (!decoder.isExtractionAllowed()) {
            retVal = DECODER_EXTRACTION_NOT_ALLOWED_ERROR;
            System.err.println("The file " + inPdfFileName + " does not allow extraction."); 
            throw new PdfPasswordException("Encrypted PDF file "
                    + "requires valid owner or user password.");
        }

        // We don't support encrypted PDFs -- yet
        if (decoder.isEncrypted() && !decoder.isPasswordSupplied()) {
            retVal = DECODER_DECRYPTION_NOT_SUPPORTED_ERROR;
            // ################## @TODO Handle these. See SimpleViewer
            System.err.println("Encrypted files like " + inPdfFileName + " not (yet) supported."); 
            return retVal;
        }

        try {
            // This is the simple text extraction:
            // Now decode the pages:
            int startPage = 1;                      // From the first page
            int endPage = decoder.getPageCount();   // ... to the last
            OutputStreamWriter output_stream = new OutputStreamWriter(
                new FileOutputStream(baseDir + File.separator + outXmlFileName),
                        Charset.forName("UTF-8"));
            output_stream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

            output_stream.write("<DOCUMENT>");

            for (int page = startPage; page < endPage + 1; page++) { //read pages

                //decode the page
                decoder.decodePage(page);

                /** create a grouping object to apply grouping to data*/
                PdfGroupingAlgorithms currentGrouping = decoder.getGroupingObject();

                /** Use whole page size for */
                PdfPageData currentPageData = decoder.getPdfPageData();

                // Get the dimensions for this page.
                // x1,y1 is top left hand corner, x2,y2 bottom right 
                int x1 = currentPageData.getMediaBoxX(page);
                int x2 = currentPageData.getMediaBoxWidth(page)+x1;
                int y2 = currentPageData.getMediaBoxY(page);
                int y1 = currentPageData.getMediaBoxHeight(page)+y2;


                /**The call to extract the text*/
                text = currentGrouping.extractTextInRectangle(
                    x1, y1, x2, y2, page, false, true);

                if (text == null) {
                    // No text found on this page.
                    System.err.println("FYI: No text on page " + page);
                }
                else {
                    output_stream.write("<TEXT pagenum='" + page + "'>\n");
                    output_stream.write(text.replace("&", "&amp;")); //write retrieved text.
                    output_stream.write("</TEXT>");
                    output_stream.flush();
                }
            }
            //remove data once written out
            decoder.flushObjectValues(false);

            output_stream.write("</DOCUMENT>");

            output_stream.close();
            decoder.flushObjectValues(true);
        }
        catch (Exception e) {
            decoder.closePdfFile();
            System.err.println("Exception " + e.getMessage());
            e.printStackTrace();
            System.err.println(decoder.getObjectStore().getCurrentFilename());
            retVal = DECODER_PAGE_DECODING_ERROR;
        }
       
        decoder.closePdfFile();
        
        return retVal;
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
        
        // Set the owner password if supplied
        if (property.equals("http://www.lingotek.com/converters/properties/ownerpassword")) {
            if (value != null) {
                this.ownerPW = value.toString();
            }
        }

        // ... or set the user password ...
        else if (property.equals("http://www.lingotek.com/converters/properties/userpassword")) {
            if (value != null) {
                this.userPW = value.toString();
            }
        }
        return;
    }

    private void doRecursive(Node p) {
        if (p == null) {
            return;
        }
        NodeList nodes = p.getChildNodes();
        
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n == null) {
                continue;
            }
            
            doNode(n);
        }
    }
    
    private void doNode(Node n) {
        switch(n.getNodeType()) {
            case Node.ELEMENT_NODE:
                System.out.println("ELEMENT<" + n.getNodeName() + ">");
                doRecursive(n);
                break;
            case Node.TEXT_NODE:
                String text = n.getNodeValue();
                if (text == null ||
                    text.length() == 0 ||
                    text.equals("\n") ||
                    text.equals("\r")) {
                    break;
                }
                System.out.println("TEXT: " + text);
                break;
            default:
                System.err.println("OTHER NODE " +
                    n.getNodeType() + ": " + n.getClass());
                break;
        }
    }
}

