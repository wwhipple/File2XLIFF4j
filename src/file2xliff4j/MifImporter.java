/*
 * MifImporter.java
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
import org.xml.sax.helpers.*;
import org.xml.sax.ext.LexicalHandler;

import f2xutils.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.util.regex.*;

/**
 * The MifImporter is used to normalize "outside" XLIFF to a smaller subset of
 * XLIFF. This importer assumes that the "owner" of the XLIFF file used as input
 * is responsible for its associated skeleton and other files. This converter
 * creates skeleton and format files, but only for the purpose of exporting the
 * reduced XLIFF file to the original format. (Upon export, the XLIFF file will
 * have additional translation unit targets, and existing targets might be 
 * modified.)
 *
 * <p>This importer replaces bpt, ept, sub, it and ph elements (which "mask off
 * codes left inline") with x, bx and ex elements (which "remove codes"). The
 * codes removed from the bpt, ept, sub, it and ph elements are placed in a
 * format file. At export time, the information from the format file is used
 * to restore the original bpt, ept, sub, it and ph elements.
 *
 * <p>This importer also replaces opening g tags with bx elements and closing
 * g tags with ex elements. 
 * 
 * @author Weldon Whipple &lt;weldon@whipple.org&gt;
 */
public class MifImporter extends DefaultHandler implements Converter {

    private OutputStreamWriter xliffOut;
    private OutputStreamWriter tskeletonOut;
    private OutputStreamWriter formatOut;
    private Locale sourceLanguage;         // For the source-language attribute
    private String dataType = "mif";       // For the datatype attribute
    private String originalFileName;       // For the original attribute

    private int curIndent = 0;              // How far to indent the next element
    private int curTagNum = 0;              // For skeleton    

    private boolean inPara = false;         // We aren't inside a Para tag.
    private boolean paraHasStrings = false; // until proven otherwise.
    private boolean inString = false;       // We're not inside a String either ...
    private boolean inPgfTag = false;       //    nor inside a PgfTag ...
    
//    private UUID curTuId;                   // The UUID of the current TU
    private StringBuilder sourceText = new StringBuilder();

    // If we determine that a TU is composed only of System variables, don't output it.
    private StringBuilder candidateTu = new StringBuilder();
    
    private int bxExXId = 1;           // Id of bx, ex or x tag

    // Each time a new bx is encountered, push its rid on the stack. The matching
    // ex tag pops the stack and uses the same rid.
    private Stack<Integer> ridStack = new Stack<Integer>();

    // rid's used by bx/ex use the next available rid:
    private int nextAvailRid = 1;

    private int commentDepth = 0;      // Not in a <Comment ... > tag
    

    private boolean inFontCatalog = false; // true as we read about fonts for the doc
    private boolean inFont = false;
    private boolean inFTag = false;
    private boolean inFWeight = false;
    private boolean inFAngle = false;
    private boolean inFPosition = false;

    // What is the current font tag?
    private String currentFTag = "";
    
    private Stack<String> tagStack = new Stack<String>(); // What is the current tag ...
    
    private HashSet<String> xTag = new HashSet<String>(); // Map to x instead of bx/ex

    private String uniqueID = "";
    
    private SegmentBoundary boundaryType;
    
    /**
     * Constructor for the MIF importer. 
     */
    public MifImporter() {
        xTag.add("aframe");
        xTag.add("fangle");  // ###  Has quotes
        xTag.add("fcase");
        xTag.add("fchangebar");
        xTag.add("fcolor");
        xTag.add("fdw");
        xTag.add("fdx");
        xTag.add("fdy");
        xTag.add("fencoding"); // Has quotes  (FrameRoman is MIF encoding)
        xTag.add("ffamily");
        xTag.add("flanguage");
        xTag.add("flocked");
        xTag.add("foutline");
        xTag.add("foverline");
        xTag.add("fpairkern");
        xTag.add("fplatformname");
        xTag.add("fposition");
        xTag.add("fpostscriptname"); // Has quotes
        xTag.add("fseparation");
        xTag.add("fshadow");
        xTag.add("fsize"); 
        xTag.add("fstretch");
        xTag.add("fstrike");
        xTag.add("ftag");    // ####  Already processed elsewhere with quotes 
        xTag.add("ftsume");
        xTag.add("funderlining"); 
        xTag.add("fvar");    // #### Has quotes
        xTag.add("fweight"); // #### Has quotes
        xTag.add("pgfalignment");
        xTag.add("pgfblocksize");
        xTag.add("pgffindent");
        xTag.add("pgfleading");
        xTag.add("pgfletterspace");
        xTag.add("pgflindent");
        xTag.add("pgfnexttag");
        xTag.add("pgfnumtabs");
        xTag.add("pgfspafter");
        xTag.add("pgfspbefore");
        xTag.add("pgftag");  // ### Has quoted arg!!
        xTag.add("pgfusenexttag");
        xTag.add("pgfwithnext");
        xTag.add("textrectid");
        xTag.add("tsleaderstr");
        xTag.add("tstype");
        xTag.add("tsx");
        xTag.add("unique");
        xTag.add("variablename");

    }
    
    /**
     * Convert a Maker Interchange Format file (MIF) to XLIFF. Additionally
     * create skeleton and format files. (The skeleton and format files are used on 
     * export to generate localized MIF files from seleced XLIFF targets.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary language of the MIF file to be imported. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input MIF. (MIF files are 
     *        FrameMaker documents encoded in ASCII format. On Unix, ISO Latin-1
     *        is used. MIF can also use Shift-JIS or EUC (Japanese), Big5, CNS or 
     *        GB2312-80.EUC (Chinese), or KSC5601-1992 for Korean.)
     * @param nativeFileType The type of the input file. This value is ignored.
     *        (The value "mif" is an official XLIFF attribute value and is used
     *        unconditionally by this importer.)
     * @param inputMifFileName The name of the input MIF file.
     * @param baseDir The directory that contains the input MIF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
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
    public ConversionStatus convert(
            ConversionMode mode,       // Must be TO_XLIFF
            Locale language,
            String phaseName,          // Ignored
            int maxPhase,              // Ignored
            Charset nativeEncoding,    
            FileType nativeFileType,   // Ignored--always "mif"
            String inputMifFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {

        if (! mode.equals(ConversionMode.TO_XLIFF)) {
            throw new ConversionException("MIF Importer supports only conversions"
                    + " from MIF to XLIFF.");
        }
        
        if (inputMifFileName == null || inputMifFileName.length() == 0) {
            throw new ConversionException("Name of input MIF file omitted.");
        }

        if (language == null) {
            throw new ConversionException("Source language omitted. (Required)");
        }
        
        // Trust the encoding specified in the input MIF file over the encoding
        // specified in the input arguments.
        Charset encoding = getEncoding(baseDir + File.separator + inputMifFileName);
        if (encoding == null) {
            encoding = nativeEncoding;  // Use what was specified by caller.
            if (encoding == null) {   // ... or just default to MIF's default
//                encoding = Charset.forName("X-MIF-FRAMEROMAN");  // Our own MIF encoding
                MifCharsetProvider mifP = new MifCharsetProvider();
                encoding = mifP.charsetForName("X-MIF-FRAMEROMAN");
            }
        }

        sourceLanguage = language;             // The input MIF file's primary language
        dataType = "mif";                      // "mif" is an XLIFF-defined value for MIF
        originalFileName = inputMifFileName;   // The name of the input MIF file
        if (boundary == null) {
            this.boundaryType = SegmentBoundary.SENTENCE; // Default to paragraph segments
        }
        else {
            this.boundaryType = boundary;
        }
                
        // Create output stream writers for the SAX handler to write to.
        // We will store our output as UTF-8
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputMifFileName + Converter.xliffSuffix),
                    "UTF8");
            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputMifFileName + Converter.tSkeletonSuffix),
                    "UTF8");
            formatOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputMifFileName + Converter.formatSuffix),
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
        
        try {
            // Let's parse with our MIF parser
            parser = new MifParser();
            
            parser.setContentHandler(this); // We're gonna handle content ourself

            // Create a reader to read from.
            Reader inReader = null;
//            if (encoding.displayName().equals(Charset.forName("X-MIF-FRAMEROMAN").displayName())) {
            if (encoding.displayName().equals("X-MIF-FRAMEROMAN")) {
                inReader = new InputStreamReader(new FileInputStream(
                    baseDir + File.separator + inputMifFileName), encoding);
            }
            else {
                inReader = new InputStreamReader(unescapeHexLits(new FileInputStream(
                   baseDir + File.separator + inputMifFileName)), encoding);
            }
            
            parser.setProperty("http://lingotek.com/mif/properties/encoding", encoding);
            
            InputSource MifIn = new InputSource(inReader);
            parser.parse(MifIn);
        }
        catch(SAXException e) {
            System.err.println("MIF parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("MIF parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading MIF input.");
            System.err.println("I/O error reading MIF input: " + e.getMessage());
        }

        try {
            /* Close the files we created above */
            xliffOut.close();
            tskeletonOut.close();
            formatOut.close();

            /* We have created a temp skeleton file (an intermediate skeleton file).
             * We now need to merge the temporary skeleton with the original input file to
             * yield a "real" skeleton */

            // We'll read from the temporary skeleton
            FileInputStream tSkeletonIn = new FileInputStream(baseDir + File.separator
                    + inputMifFileName + Converter.tSkeletonSuffix);

            // We'll also read from the original MIF file
            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator
                    + inputMifFileName);

            // We'll write to the (final) skeleton file
            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator
                    + inputMifFileName + Converter.skeletonSuffix);

            // The MifSkeletonMerger will do the deed.
            SkeletonMerger merger = new MifSkeletonMerger();

            if (merger != null) {
//                merger.merge(tSkeletonIn, nativeIn, skeletonOut, Charset.forName("UTF-8"));
                merger.merge(tSkeletonIn, nativeIn, skeletonOut, encoding);
            }

            tSkeletonIn.close();
            nativeIn.close();
            skeletonOut.close();
        }
        catch(java.io.FileNotFoundException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        catch(java.io.IOException e) {
            System.err.println("Error creating final skeleton file from temporary skeleton");
            System.err.println(e.getMessage());
        }
        
        if (generatedFileName != null) {
            generatedFileName.write(inputMifFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert a Maker Interchange Format file (MIF) to XLIFF. Additionally
     * create skeleton and format files. (The skeleton and format files are used on 
     * export to generate localized MIF files from seleced XLIFF targets.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary language of the MIF file to be imported. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input MIF. (MIF files are 
     *        FrameMaker documents encoded in ASCII format. On Unix, ISO Latin-1
     *        is used. MIF can also use Shift-JIS or EUC (Japanese), Big5, CNS or 
     *        GB2312-80.EUC (Chinese), or KSC5601-1992 for Korean.)
     * @param nativeFileType The type of the input file. This value is ignored.
     *        (The value "mif" is an official XLIFF attribute value and is used
     *        unconditionally by this importer.)
     * @param inputMifFileName The name of the input MIF file.
     * @param baseDir The directory that contains the input MIF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
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
    public ConversionStatus convert(
            ConversionMode mode,       // Must be TO_XLIFF
            Locale language,
            String phaseName,          // Ignored
            int maxPhase,              // Ignored
            Charset nativeEncoding,    
            FileType nativeFileType,   // Ignored--always "mif"
            String inputMifFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName,
            Set<XMLTuXPath> skipList) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputMifFileName, baseDir, notifier, boundary,
                generatedFileName);
    }    
    
    /**
     * Convert a Maker Interchange Format file (MIF) to XLIFF. Additionally
     * create skeleton and format files. (The skeleton and format files are used on 
     * export to generate localized MIF files from seleced XLIFF targets.)
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary language of the MIF file to be imported. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input MIF. (MIF files are 
     *        FrameMaker documents encoded in ASCII format. On Unix, ISO Latin-1
     *        is used. MIF can also use Shift-JIS or EUC (Japanese), Big5, CNS or 
     *        GB2312-80.EUC (Chinese), or KSC5601-1992 for Korean.)
     * @param nativeFileType The type of the input file. This value is ignored.
     *        (The value "mif" is an official XLIFF attribute value and is used
     *        unconditionally by this importer.)
     * @param inputMifFileName The name of the input MIF file.
     * @param baseDir The directory that contains the input MIF file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * <li>&lt;original_file_name&gt;.format</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */    
    @Deprecated
    public ConversionStatus convert(
            ConversionMode mode,       // Must be TO_XLIFF
            Locale language,
            String phaseName,          // Ignored
            int maxPhase,              // Ignored
            Charset nativeEncoding,    
            FileType nativeFileType,   // Ignored--always "mif"
            String inputMifFileName,
            String baseDir,
            Notifier notifier) throws ConversionException {
        return this.convert(mode, language, phaseName, maxPhase, nativeEncoding,
                nativeFileType, inputMifFileName, baseDir, notifier, null, null);
    }    
    
    /**
     * Method called by the SAX parser at the beginning of document parsing.
     * @throws org.xml.sax.SAXException
     *         I if any problems are found.
     */
    public void startDocument() throws SAXException {

        // Write the start of the output XLIFF document
        writeXliff(Converter.xmlDeclaration
                 + Converter.startXliff
                 + indent() + "<file original='" 
                       + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                       + "' source-language='" + sourceLanguage.toString() 
                       + "' datatype='mif'>\n"
                 + indent() + "<header");
        switch(boundaryType) {
            case PARAGRAPH:  
                writeXliff(" lt:segtype='paragraph'");
                break;
            case SENTENCE:
                writeXliff(" lt:segtype='sentence'");
                break;
            default:
                writeXliff(" lt:segtype='sentenct'");
        }
        writeXliff(">\n" + indent('0') + "</header>\n" 
                 + indent('0') + "<body>\n");
        
        // Also write the beginning of the format file
        writeFormat("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                  + "<lt:LT xmlns:lt=\"http://www.lingotek.com/\">"
                  + "<tags formatting=''>\n"); // no &lt; formatting value!
    }

    /**
     * Method called whenever a start element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if
     *        Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string 
     *        if qualified names are not available
     * @param atts The specified or defaulted attributes.
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {
                             
        tagStack.push(qualifiedName);
        
        // Check
        if (qualifiedName.equalsIgnoreCase("Comment")) {
            commentDepth++;
            return;
        }
        
        else if (commentDepth > 0) {
            return;
        }
        
        // We're now reading the fonts in the font catalog.
        else if (qualifiedName.equalsIgnoreCase("FontCatalog")) {
            inFontCatalog = true;
            return;
        }
        else if (qualifiedName.equalsIgnoreCase("Font")) {
            inFont = true;
            // If we're inside a paragraph, we need to save the format and
            // insert a bx tag.
            if (inPara) {  // ... Instead of reading from the fonts catalog (I lied ...)
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<Font ]]></tag>\n");

                // Now write a bx tag to XLIFF
                sourceText.append("<bx id='" + bxExXId + "' rid='" + nextAvailRid + "'");
                sourceText.append(" ctype='x-mif-Font'/>");

                // Bookkeeping at the end.
                ridStack.push(Integer.valueOf(nextAvailRid++));
                bxExXId++;              // Increment id val for next x, bx, ex tags.
                
            }
        }

        else if (qualifiedName.equalsIgnoreCase("FTag")) {
            if (inFontCatalog && inFont && (atts != null)) {
                currentFTag = atts.getValue(0);
            }
            // If we're inside a paragraph, we need to save the format and
            // insert a bx tag.
            else if (inPara) {  // ... instead of reading from the fonts catalog (I lied)
                String fTagVal = "";
                String ctype = "";
                if (atts != null) {
                    fTagVal = atts.getValue(0);
                    if (fTagVal.toLowerCase().indexOf("emphasis") > -1) {
                        ctype = "x-mif-italic";
                    }
                    else if (fTagVal.toLowerCase().indexOf("bold") > -1) {
                        ctype = "x-mif-bold";
                    }
                    else {
                        ctype = "x-mif-plain";
                    }
                }
                
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<FTag " + (fTagVal == null ? "" : fTagVal) + ">]]></tag>\n");

                // Now write a bx tag to XLIFF
                sourceText.append("<x id='" + bxExXId + "'");
                sourceText.append(" ctype='" + ctype + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
                
            }
       }

        // Check FontCatalog entries for names used for italic, bold, superscript, subscript
        else if (qualifiedName.equalsIgnoreCase("FAngle")) {
            if (inPara) {
                String ctype = "";
                String angle = "";
                if (atts != null) {
                    angle = atts.getValue(0).toLowerCase();
                    if (angle.indexOf("italic") > -1) {
                        ctype = "x-mif-italic";
                    }
                    else {
                        ctype = "x-mif-nonitalic";
                    }
                }
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<" + qualifiedName);
                for (int i = 0; i < atts.getLength(); i++) {
                    writeFormat(" " + atts.getValue(i));
                }
                writeFormat(">]]></tag>\n");

                // Now write a bx tag to XLIFF
                sourceText.append("<x id='" + bxExXId + "'");
                sourceText.append(" ctype='" + ctype + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
        }
        
        else if (qualifiedName.equalsIgnoreCase("FWeight")) {
            if (inPara) {
                String ctype = "";
                String weight = "";
                if (atts != null) {
                    weight = atts.getValue(0).toLowerCase();
                    if (weight.indexOf("bold") > -1) {
                        ctype = "x-mif-bold";
                    }
                    else {
                        ctype = "x-mif-nonbold";
                    }
                }
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<" + qualifiedName);
                for (int i = 0; i < atts.getLength(); i++) {
                    writeFormat(" " + atts.getValue(i));
                }
                writeFormat(">]]></tag>\n");

                // Now write a bx tag to XLIFF
                sourceText.append("<x id='" + bxExXId + "'");
                sourceText.append(" ctype='" + ctype + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
        }

        else if (qualifiedName.equalsIgnoreCase("FPosition")) {
            if (inPara) {
                String ctype = "";
                String position = "";
                if (atts != null) {
                    position = atts.getValue(0).toLowerCase();
                    if (position.indexOf("subscript") > -1) {
                        ctype = "x-mif-subscript";
                    }
                    else if (position.indexOf("superscript") > -1) {
                        ctype = "x-mif-superscript";
                    }
                    else {
                        ctype = "x-mif-normalposition";
                    }
                }
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<" + qualifiedName);
                for (int i = 0; i < atts.getLength(); i++) {
                    writeFormat(" " + atts.getValue(i));
                }
                writeFormat(">]]></tag>\n");

                // Now write a bx tag to XLIFF
                sourceText.append("<x id='" + bxExXId + "'");
                sourceText.append(" ctype='" + ctype + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
        }
        
        // If this is a tag that maps to an <x /> tag ...
        else if (xTag.contains(qualifiedName.toLowerCase())) {
            // If we're inside a paragraph, we need to save the format and
            // insert an ex tag.
            if (inPara) {
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "<" + qualifiedName);
                for (int i = 0; i < atts.getLength(); i++) {
                    writeFormat(" " + atts.getValue(i));
                    
                    // Capture the Unique id if this is a Unique tag
                    if (qualifiedName.equalsIgnoreCase("Unique") && i == 0) {
                        uniqueID = atts.getValue(i);
                    }
                }
                writeFormat(">]]></tag>\n");

                // Now write an x tag to XLIFF
                sourceText.append("<x id='" + bxExXId + "' ctype='x-mif-" 
                        + qualifiedName);
                
                // If this is a Unique tag, append the Unique identifier
                if (qualifiedName.equalsIgnoreCase("Unique") && (uniqueID.length() > 0)) {
                    sourceText.append("-" + uniqueID);
                }
                
                sourceText.append("'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
                
            }
        }

        // End of Font Catalog tags.
        
        // Para tags signal the beginning of a paragraph.
        else if (qualifiedName.equals("Para")) {
            inPara = true;                 // Now we're inside a paragraph
            paraHasStrings = false;        // so far ...
            
            writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "' unique='" + uniqueID + "'>\n");

            // Assign a UUID to the TU
//            curTuId = UUID.randomUUID();
            
            return;
        }
        
        // Strings can be part of ParaLine's, which can be part of Para's
        // They can also occur just about anywhere--in bar chart legends, etc.
        else if (qualifiedName.equals("String")) {
            inString = true;                 // Now we're inside a String
                                             // We may or may not be inside a
                                             // paragraph.
            if (! inPara) {
                writeTSkeleton("<" + qualifiedName + " seq='" + curTagNum++ + "'>\n");
                // Note: If we *are* inPara, then String maps to a bx tag in the
                // format file. We don't want format file entries to also appear
                // in the tSkeleton ...

                // Since this isn't in a paragraph, the contents of the
                // string are probably the entire TU. We need to start our
                // own TU entry

                // Assign the new TU's UUID
//                curTuId = UUID.randomUUID();
//                            
//                // Start a new new TU element 
//                candidateTu.append(indent(6) + "<trans-unit id='" + curTuId.toString() + "'>\n");
//            
//                // Begin a source element as well
//                candidateTu.append(indent('+') + "<source"
//                        + " xml:lang='" + sourceLanguage.toString() + "'>");
            }
            
            else { // We're within a Para. ... so the String element needs to become a
                   // bx element
                // The original tags go in the format file
                paraHasStrings = true;        // This is important!!
                StringBuilder fmtStr = new StringBuilder("<" + qualifiedName + " ");
                for (int i = 0; i < atts.getLength(); i++) {
                    fmtStr.append(" " + atts.getValue(i));
                }

                // Write out the format string.
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                            + fmtStr + "]]></tag>\n");
                fmtStr.setLength(0);      // In case any optimization occurs ...

                // Now write a bx tag to XLIFF
                sourceText.append("<bx id='" + bxExXId + "' rid='" + nextAvailRid + "'");
                sourceText.append(" ctype='x-mif-String'/>");

                // Bookkeeping at the end.
                ridStack.push(Integer.valueOf(nextAvailRid++));
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
            return;
        }
        
        else if (qualifiedName.equals("PgfTag")) {
            inPgfTag = true;      // These are formatting tags in quotes. They aren't text
        }

        else if (qualifiedName.equals("Char")) {
            // Handle extended FrameRoman characters, converting them to Strings
            if (inPara && !inPgfTag) {
                if (atts.getLength() > 0) {
                    String charName = atts.getValue(0);
                    handleChar(charName);
                }
            }
        }
        
        // Since a TU (i.e. Para) tag can include multiple ParaLine's and multiple
        // strings, we need to map ParaLine tags and String tags--actually *any*
        // tags within a Para--to bx/ex/x tags.
        else if (inPara) {  
            // The original tags go in the format file
            StringBuilder fmtStr = new StringBuilder("<" + qualifiedName);
            for (int i = 0; i < atts.getLength(); i++) {
                fmtStr.append(" " + atts.getValue(i));
            }

            // Write out the format string.
            writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                        + fmtStr + "]]></tag>\n");
            fmtStr.setLength(0);      // In case any optimization occurs ...

            // Now write a bx tag to XLIFF
            sourceText.append("<bx id='" + bxExXId + "' rid='" + nextAvailRid + "'");
            sourceText.append(" ctype='x-mif-" + qualifiedName + "'/>");

            // Bookkeeping at the end.
            ridStack.push(Integer.valueOf(nextAvailRid++));
            bxExXId++;              // Increment id val for next x, bx, ex tags.
        }   
    }

    /**
     * Method called whenever an end element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string if qualified names are not available
     */
    public void endElement(String namespaceURI, 
			   String localName,
			   String qualifiedName) throws SAXException {

        String topTag = tagStack.pop();
        // Double check
        if (!topTag.equalsIgnoreCase(qualifiedName)) {
            System.err.println("The endElement name " + qualifiedName
                    + " differs from the top of stack " + topTag);
        }

        // Check to see if we are out of a comment now
        if (qualifiedName.equalsIgnoreCase("Comment")) {
            commentDepth--;
            return;
        }
        
        else if (commentDepth > 0) {
            return;
        }
        
        if (qualifiedName.equalsIgnoreCase("FontCatalog")) {
            inFontCatalog = false;     // No longer reading font catalog
            return;
        }
        
        else if (qualifiedName.equalsIgnoreCase("Font")) {
            inFont = false;
            // If we're inside a paragraph, we need to save the format and
            // insert an ex tag.
            if (inPara) {
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                    + "> # end of " + qualifiedName + "]]></tag>\n");

                // Now write an ex tag to XLIFF
                int rid = 1;
                if (ridStack.size() > 0) {
                    rid = ridStack.pop().intValue();
                }
                sourceText.append("<ex id='" + bxExXId + "' rid='" + rid + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
                
            }
        }

        else if (qualifiedName.equalsIgnoreCase("Ftag")) {
            inFTag = false;
        }
        else if (qualifiedName.equalsIgnoreCase("FPosition")) {
            // Do nothing
        }
        else if (qualifiedName.equalsIgnoreCase("FWeight")) {
            inFWeight = false;
        }
        else if (qualifiedName.equalsIgnoreCase("FAngle")) {
            inFAngle = false;
        }
        
        else if (qualifiedName.equals("Para")) {
            if (paraHasStrings && (sourceText.length() > 0)) {
//                String preenedPara = MifTuPreener.preenTu(sourceText.toString(), this);
                String preenedPara = preenTu(sourceText.toString());

                // Get the core segments:
                SegmentInfo[] coreTus = TuPreener.getCoreSegments(preenedPara,
                    this.boundaryType, this.sourceLanguage);
                
                UUID paraId = null;
                
                for (int i = 0; i < coreTus.length; i++) {
                    if (coreTus[i].translatable()) {
                        String coreSeg = coreTus[i].getSegmentStr();
                        UUID curTuId = UUID.randomUUID();
                        
                        // The paragraph ID is the UUID of the first translation
                        // unit in the paragraph.
                        if (paraId == null) {
                            paraId = curTuId;
                        }

                        // We need to indicate whether this segment is "mergeable"
                        // with its successor--and record it in the XLIFF
                        boolean hasSuccessor = false;
                        if (i < (coreTus.length-1)) { // If this *isn't* the last segment ...
                            FIND_SUCCESSOR:
                            for (int j = (i + 1); j < coreTus.length; j++) {
                                if (coreTus[j].translatable()) {
                                    hasSuccessor = true;
                                    break FIND_SUCCESSOR;
                                }
                            }
                        }
                        
                        // Start a new new TU element 
                        candidateTu.append(indent(6) + "<trans-unit id='" + curTuId.toString() + "'"
                                + " lt:paraID='" + paraId + "'");
                        
                        // If this segment is mergeable, so note it:
                        if (hasSuccessor) {
                            candidateTu.append(" lt:mergeable='true'");
                        }
                        
                        // Close the opening TU tag
                        candidateTu.append(">\n");

                        // Begin a source element as well
                        candidateTu.append(indent('+') + "<source"
                                + " xml:lang='" + sourceLanguage.toString() + "'>");


//                        String preenedTu = MifTuPreener.preenTu(new String(sourceText), this);
//                        candidateTu.append(preenedTu);
                        candidateTu.append(coreSeg);

                        candidateTu.append("</source>\n");

                        candidateTu.append(indent(6) + "</trans-unit>\n");

//                        if (hasMeaningfulText(TuPreener.getCoreText(preenedTu))) {
                        writeXliff(candidateTu.toString());
                        // Note the current TU in the TSkeleton file
                        writeTSkeleton("<tu id='" + curTuId + "' parent='Para' unique='" + uniqueID + "' "
                                + "no='" + (i+1) + "' of='" + coreTus.length + "'>\n");
//                        }
                
                        candidateTu.setLength(0); // Clear out the candidateTu
                    }
                    else {  // Write a nontranslatable segment to the format file
                        // Write out the format string.
                        // (Note: This might never be executed ... ??)
                        writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                                    + coreTus[i].getSegmentStr() + "]]></tag>\n");
                        
                        // ... and a reference to it in tskeleton:
                        writeTSkeleton("<format id='" + bxExXId + "' parent='Para' unique='" + uniqueID + "' "
                                + "no='" + (i+1) + "' of='" + coreTus.length + "'>\n");
                        bxExXId++;
                    }
                }  // for

                inPara = false;  // We're outta the Para now
                sourceText.setLength(0);  // Clear out the <source> text
                paraHasStrings = false;   // Just in case. (We'll reset this next time
                                          // we start a new paragraph).
            }
            else {  // No strings in this para element, but we want the Skeleton merger
                    // to skip over it, so don't write the tu:id to the tskeleton
            
                sourceText.setLength(0);  // Clear out the <source> text

                inPara = false;  // We're outta the Para now
                paraHasStrings = false;   // Just in case. (We'll reset this next time
                                          // we start a new paragraph).
                
            }
            writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "' unique='" + uniqueID + "'>\n");
            return;
        }
        
        else if (qualifiedName.equals("String")) {
            inString = false;                // Outta the String

            if (! inPara) {  // This was a "self-contained" TU-in-a-string
//                String preenedString = MifTuPreener.preenTu(sourceText.toString(), this);
                String preenedString = preenTu(sourceText.toString());
                
                // Get the core segments:
                SegmentInfo[] coreTus = TuPreener.getCoreSegments(preenedString,
                    this.boundaryType, this.sourceLanguage);

                UUID paraId = null;
                
                for (int i = 0; i < coreTus.length; i++) {
                    if (coreTus[i].translatable()) {
                        String coreSeg = coreTus[i].getSegmentStr();
                        UUID curTuId = UUID.randomUUID();
                            
                        // The paragraph ID is the UUID of the first translation
                        // unit in the paragraph.
                        if (paraId == null) {
                            paraId = curTuId;
                        }

                        // We need to indicate whether this segment is "mergeable"
                        // with its successor--and record it in the XLIFF
                        boolean hasSuccessor = false;
                        if (i < (coreTus.length-1)) { // If this *isn't* the last segment ...
                            FIND_SUCCESSOR:
                            for (int j = (i + 1); j < coreTus.length; j++) {
                                if (coreTus[j].translatable()) {
                                    hasSuccessor = true;
                                    break FIND_SUCCESSOR;
                                }
                            }
                        }
                        
                        // Start a new new TU element 
                        candidateTu.append(indent(6) + "<trans-unit id='" + curTuId.toString() + "'"
                                + " lt:paraID='" + paraId + "'");
                        
                        // If this segment is mergeable, so note it:
                        if (hasSuccessor) {
                            candidateTu.append(" lt:mergeable='true'");
                        }
                        
                        // Close the opening TU tag
                        candidateTu.append(">\n");
            
                        // Begin a source element as well
                        candidateTu.append(indent('+') + "<source"
                            + " xml:lang='" + sourceLanguage.toString() + "'>");

                        // Write the source string.
//                        String preenedTu = MifTuPreener.preenTu(sourceText.toString(), this);
                        String preenedTu = preenTu(sourceText.toString());
//                        candidateTu.append(preenedTu);
                        candidateTu.append(coreSeg);
            
                        // Close the source element a
                        candidateTu.append("</source>\n");
                
                        // Close the TU
                        candidateTu.append(indent(6) + "</trans-unit>\n");

//                        if (hasMeaningfulText(TuPreener.getCoreText(preenedTu))) {
                        writeXliff(candidateTu.toString());
                        // Note the current TU in the TSkeleton file
                        writeTSkeleton("<tu id='" + curTuId + "' parent='String' "
                            + "no='" + (i+1) + "' of='" + coreTus.length + "'>\n");
//                        }
                        candidateTu.setLength(0); // Clear out the candidateTu
                    }
                    else {  // Write a nontranslatable segment to the format file
                        // Write out the format string.
                        // (Note: This might never be executed ... ??)
                        writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                                    + coreTus[i].getSegmentStr() + "]]></tag>\n");
                        
                        // ... and a reference to it in tskeleton:
                        writeTSkeleton("<format id='" + bxExXId + "' parent='String' "
                                + "no='" + (i+1) + "' of='" + coreTus.length + "'>\n");
                        bxExXId++;
                    }                
                }  // for

                sourceText.setLength(0);
                writeTSkeleton("</" + qualifiedName + " seq='" + curTagNum++ + "'>\n");
            }
            
            else { // We're within a Para. ... so the String element needs to become an
                   // ex element
                // The original tags go in the format file
                StringBuilder fmtStr = new StringBuilder(">"); // Not much to it ...

                // Write out the format string.
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                            + fmtStr + " # end of String]]></tag>\n");
                fmtStr.setLength(0);      // In case any optimization occurs ...

                // Now write the ex tag to XLIFF
                int rid = 1;
                if (ridStack.size() > 0) {
                    rid = ridStack.pop().intValue();
                }
                sourceText.append("<ex id='" + bxExXId + "' rid='" + rid + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
            
            return;
        }

        else if (qualifiedName.equals("PgfTag")) {
            inPgfTag = false;      // These are formatting tags in quotes. They aren't text
        }
        
        else if (qualifiedName.equalsIgnoreCase("Char")
            || qualifiedName.equalsIgnoreCase("FTag")
            || qualifiedName.equalsIgnoreCase("FLocked")) {
            return;   // Do nothing (These were x tags that closed in startElement
            // Actually, I partially lied about Char--they weren't x tags, but 
            // we converted them to Strings
        }
            
        
        // We're in a paragraph. (Under "String" above, we already covered the
        // case where we were inPara and a String ended. This covers other end
        // tags ...
        else if (inPara) {  
            // If these are x tags, do nothing. (They were already covered in
            // the startElement method.)
            if (!xTag.contains(qualifiedName.toLowerCase())) {
                // The original tags go in the format file
                StringBuilder fmtStr = new StringBuilder(">");  // Justa ">" ...

                // Write out the format string.
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA["
                            + fmtStr + " # end of " + qualifiedName + "]]></tag>\n");
                fmtStr.setLength(0);      // In case any optimization occurs ...

                // Now write a bx tag to XLIFF
                int rid = 1;
                if (ridStack.size() > 0) {
                    rid = ridStack.pop().intValue();
                }
                sourceText.append("<ex id='" + bxExXId + "' rid='" + rid + "'/>");

                // Bookkeeping at the end.
                bxExXId++;              // Increment id val for next x, bx, ex tags.
            }
        }   
    }
    
     /**
     * Called whenever characters are encountered
     * @param ch Array containing characters encountered
     * @param start Position in array of first applicable character
     * @param length How many characters are of interest?
     */
    public void characters(char[] ch,
			   int start,
			   int length) throws SAXException {

        if (commentDepth > 0) {
            return;
        }
        
        if (length > 0) {
            // Write to the candidate trans-unit buffer
            if (inString) /* || (inPara && !inPgfTag)) */ {
                sourceText.append(new String(ch, start, length));
            }
        }
    }

    /**
     * When the end-of-document is encountered, write what follows tqhe final
     * translation unit.
     */
    public void endDocument()
	throws SAXException {
        
        // Finish off the XLIFF file:
        writeXliff(indent('-') + "</body>\n"); // Close the body element
        writeXliff(indent('-') + "</file>\n"); // Close the file element
        writeXliff("</xliff>\n");               // Close the xliff element

        // Also finish off the format file:
        writeFormat("</tags>\n</lt:LT>\n");

        // Three flushes (not very water-wise, I admit)
        try {
            xliffOut.flush();             // Well-bred writers flush when finished
            formatOut.flush();
            tskeletonOut.flush();
        }
        catch(IOException e) {
            System.err.println("Error flushing skeleton stream.");
            System.err.println(e.getMessage());
        }
    }

    private static int HEAD_BYTES = 800;   // Number of bytes to read for MIFENcoding tag
    
    // Asian text MIFEncoding byte signatures
    /*package*/ static byte[] PREFIX  = {'<', 'M', 'I', 'F', 'E', 'n', 'c', 'o', 'd', 'i', 'n', 'g', ' ', '`'};
                                    // In unicode, Nihongo (Japanese Language) is \u65e5\u672c\u8a9e
                                    // FrameMaker encodes it not as Unicode or UTF-8, but as
                                    // Shift_JIS: 0x93fa967b8cea  
    
    // The two JA ones re-verified using nkf (WWhipple 8/28/06)
    /*package*/ static byte[] JA_SJIS_SIG = {(byte)0x93, (byte)0xfa, (byte)0x96, (byte)0x7b, 
                                        (byte)0x8c, (byte)0xea}; // Nihongo in SJIS
                                    // In eucJP, it becomes: 0xc6fccbdcb8ec ... which in Octal is:
    /*package*/ static byte[] JA_EUC_SIG  = {(byte)0xc6, (byte)0xfc, (byte)0xcb, (byte)0xdc, 
                                        (byte)0xb8, (byte)0xec}; // Nihongo in EUC
    
                                    // In unicode, Chinese writing is \u4e2d\u6587
                                    // FrameMaker encodes it not as Unicode or UTF-8, but as
                                    // Big5 (traditional): 0xa4a4a4e5 ... Converting to Octal for Java:
    /*package*/ static byte[] ZH_BIG5_SIG = {(byte)0xa4, (byte)0xa4, (byte)0xa4, (byte)0xe5};  // chuubun (Japanese reading) in Big5
                                     // FrameMaker might also encode it as
                                     // GB2312-80 (simplified) (eucCN): 0xd6d0cec4 ... Converting to octal
    /*package*/ static byte[] ZH_GB2312 = {(byte)0xd6, (byte)0xd0, (byte)0xce, (byte)0xc4};  // chuubun (Japanese reading) in GB2312-80
                                     // FrameMaker also claims to use CNS for Traditional Chinese. I'm not
                                     // sure what that is, but it *might* refer to eucTW, which incorporates
                                     // CNS-Roman, DNS 11643-1992 planes 1-7, where 
                                     // 0xc4e3c5c6 is the encoding for Chinese language (chuubun in Japanese).
                                     // In octal, that becomes:
    /*package*/ static byte[] ZH_EUCTW_SIG = {(byte)0xc4, (byte)0xe3, (byte)0xc5, (byte)0xc6};  // chuubun (Japanese reading) in eucTW
    
    
                                     // In unicode, the word(s) for Korean language is \ud55c\uad6d\uc5b4
                                     // I'm not sure what KSC-5601 is (FrameMaker's supported Korean encoding)
                                     // but in Microsoft's CP949, this translated to 0xc7d1b1b9beee
                                     // In octal, that becomes:
    /*package*/ static byte[] KR_KSC5601_SIG = {(byte)0xc7, (byte)0xd1, (byte)0xb1, (byte)0xb9, 
                                        (byte)0xbe, (byte)0xee };

    
    /** Enumeration of the non-Latin encodings that might appear in a MIFEncoding tag
     * in a MIF file. These are the canonical names for java.io and java.lang. (We need
     * this so that the toString() method fo the enum item will return the canonical
     * name.
     */
    private enum MIFEncoding {SJIS, EUC_JP, Big5, EUC_TW, EUC_CN, EUC_KR };
    
    /** Map of the MIFEncodings to their signatures */
    private HashMap<MIFEncoding, byte[]> sigMap = new HashMap<MIFEncoding, byte[]>();
    

    /**
     * Passed an array of bytes that is known to include the sub-string
     * &lt;MIFEncoding, search the value of the MIFEncoding for one of the
     * documented Asian languages that FrameMaker is known to support. 
     * Return a CharSet that corresponds to the encoding.
     */
    private Charset getMIFEncoding(byte[] headBytes) {
        sigMap.put(MIFEncoding.SJIS, MifImporter.JA_SJIS_SIG);
        sigMap.put(MIFEncoding.EUC_JP, MifImporter.JA_EUC_SIG);
        sigMap.put(MIFEncoding.Big5, MifImporter.ZH_BIG5_SIG); // Traditional
        sigMap.put(MIFEncoding.EUC_TW, MifImporter.ZH_EUCTW_SIG); // Traditional
        sigMap.put(MIFEncoding.EUC_CN, MifImporter.ZH_GB2312); // Simplified
        sigMap.put(MIFEncoding.EUC_KR, MifImporter.KR_KSC5601_SIG); // Hmmm ... *Some* Korean ...
        Charset encoding = null;

        int headPos = -1;  // Place in the headBytes array that might start a match.
        // Check for each encoding.
        CHECK_EACH_ENCODING_LOOP:
        for (MIFEncoding enc : MIFEncoding.values()) {
            CHECK_HEADBYTES_LOOP:
            for (int i = 0; i < headBytes.length; i++) {
                // See if the first byte matches
                byte [] curSig = sigMap.get(enc);
                if (headBytes[i] == curSig[0]) {
                    headPos = i;  // Remember where the possible match starts.
                    // See if there are more matches.
                    int curHead = -1;
                    int curSigPos = -1;
                    MATCH_REMAINDER_LOOP:
                    for (curHead = i+1, curSigPos = 1;
                            curHead < headBytes.length && curSigPos < curSig.length;
                            curHead ++, curSigPos++) {
                        if (headBytes[curHead] != curSig[curSigPos]) {
                            // Ah, nuts ... The whole thing doesn't match.
                            // Exit this loop.
                            break MATCH_REMAINDER_LOOP;
                        }
                    }
                    // Did we match for the whole signature?
                    if (curSigPos == curSig.length) {
                        // Set the appropriate encoding
                        encoding = Charset.forName(enc.toString());
                        break CHECK_EACH_ENCODING_LOOP; // All the way out ...
                    }
                }
                // Else check the next head byte for a match.
            }
        }
        
        return encoding;
    }

    /**
     * Passed the absolute name of a MIF file, check for the presence of  a
     * MIFENcoding tag that indicates the presence of Chinese, Japanese, or
     * Korean text. If present, return the encoding. Otherwise return null.
     * @return The encoding used by the XML file,
     */
    private Charset getEncoding(String fileName) throws ConversionException {
        Charset encoding = null;
        
        byte[] headBytes = new byte[HEAD_BYTES];
        BufferedInputStream mifIn = null;  // Contains bytes--not chars
        int numRead = -1;
        
        try {
            // We don't want these interpreted as UTF-8, but as raw bytes:
            mifIn = new BufferedInputStream(new FileInputStream(fileName));

            // Should be "near the beginning of the file"
            numRead = mifIn.read(headBytes);
            // We're done now.
            mifIn.close();     // Close before leaving.
        }
        catch (IOException e) {
            System.err.println("Error reading XLIFF file: " + e.getMessage());
            throw new ConversionException("Error reading XLIFF file: " 
                    + e.getMessage());
        }

        if (numRead > 0) {    // The file isn't empty (Good)
            if ((new String(headBytes).indexOf("<MIFEncoding")) > -1) {
                // Return the encoding.
                return getMIFEncoding(headBytes);
            }
        }

        // Hmmm ... Still here. Look for an FEncoding tag with non FrameRoman
        String fencodingPatt = "<FEncoding +`([^']+)'>";
        Pattern p = Pattern.compile(fencodingPatt, Pattern.DOTALL);
        Matcher m = p.matcher("");

        try {
            BufferedReader mifRdr = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
            String curLine;

            while ((curLine = mifRdr.readLine()) != null) {
                if (curLine.indexOf("FEncoding") == -1) {
                    continue;   // Look further
                }
                // This has an FEncoding line
                m.reset(curLine);
                if (m.find()) {
                    if (m.group(1).equalsIgnoreCase("FrameRoman")) {
                        continue;
                    }
                    // Not FrameRoman!!
                    if (m.group(1).equalsIgnoreCase("JISX0208.ShiftJIS")) {
                        return Charset.forName("Shift_JIS");
                    }
                    else if (m.group(1).equalsIgnoreCase("BIG5")) {
                        return Charset.forName("Big5");
                    }
                    else if (m.group(1).equalsIgnoreCase("GB2312-80.EUC")) {
                        return Charset.forName("GB2312");
                    }
                    else if (m.group(1).equalsIgnoreCase("KSC5601-1992")) {
                        return Charset.forName("EUC-KR");
                    }
                    else {
                        System.err.println("Unknown FEncoding value!!");
                    }
                }
            }
            mifRdr.close();     // Close before leaving.
        }
        catch (IOException e) {
            System.err.println("Error reading XLIFF file: " + e.getMessage());
            throw new ConversionException("Error reading XLIFF file: "
                    + e.getMessage());
        }

//        return Charset.forName("X-MIF-FRAMEROMAN");
        MifCharsetProvider mifP = new MifCharsetProvider();
        encoding = mifP.charsetForName("X-MIF-FRAMEROMAN");
        return encoding;
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
     * @return the MIF file type.
     */
    public FileType getFileType() {
        return FileType.MIF;
    }

    /** 
     * Passed an element's attribute list, look for the xml:lang attribute's
     * value. If not found, return null.
     * @param atts The attribute list to search for xml:lang 
     * @return The locale corresponding to the language; if the xml:lang attribute
     *         is not found in the list, return null.
     */ 
    private Locale getLocale(Attributes atts) {
        Locale theLocale = null;
        
        for (int i = 0; i < atts.getLength() ; i++) {
            String aName = atts.getQName(i); // Attr name
            if (aName.equals("xml:lang")) {
                String[] parts = atts.getValue(i).split("_");
                if (parts.length == 3) {
                    theLocale = new Locale(parts[0], parts[1], parts[2]);
                }
                else if (parts.length == 2) {
                    theLocale = new Locale(parts[0], parts[1]);
                }
                else {
                    theLocale = new Locale(atts.getValue(i));
                }
            }
        }
        
        return theLocale;
    }
    
    /**
     * Passed a FrameMaker CharacterName, convert it to a Unicode character
     * and write it to sourceText.
     * @param charName The Framemaker character name
     */ 
    private void handleChar(String charName) {
        char uChar = 0;   // Maps to nothing so far
        
        String ctype = "x-mif-String";
        
        if      (charName.equalsIgnoreCase("HardSpace"))    {uChar = '\u00a0';}
        else if (charName.equalsIgnoreCase("SoftHyphen"))   {/* Delete this */}
        else if (charName.equalsIgnoreCase("DiscHyphen"))   {uChar = '\u00ad';}
        else if (charName.equalsIgnoreCase("NoHyphen"))     {uChar = '\u200d';}
        else if (charName.equalsIgnoreCase("Tab"))          {
            ctype = "x-mif-tab";
            uChar = '\u0009';
        }
        else if (charName.equalsIgnoreCase("Cent"))         {uChar = '\u00a2';}
        else if (charName.equalsIgnoreCase("Pound"))        {uChar = '\u00a3';}
        else if (charName.equalsIgnoreCase("Yen"))          {uChar = '\u00a5';}
        else if (charName.equalsIgnoreCase("EnDash"))       {uChar = '\u2013';}
        else if (charName.equalsIgnoreCase("EmDash"))       {uChar = '\u2014';}
        else if (charName.equalsIgnoreCase("Dagger"))       {uChar = '\u2020';} 
        else if (charName.equalsIgnoreCase("DoubleDagger")) {uChar = '\u2021';}
        else if (charName.equalsIgnoreCase("Bullet"))       {uChar = '\u2022';}
        else if (charName.equalsIgnoreCase("NumberSpace"))  {uChar = '\u2007';}
        else if (charName.equalsIgnoreCase("ThinSpace"))    {uChar = '\u2009';}
        else if (charName.equalsIgnoreCase("EnSpace"))      {uChar = '\u2002';}
        else if (charName.equalsIgnoreCase("EmSpace"))      {uChar = '\u2003';}
        else if (charName.equalsIgnoreCase("HardReturn"))   {
            ctype = "lb";
            uChar = '\r';
        }


        if (uChar != 0) { 
            // Append to sourceText as a <String, which will have to be merged
            // with neighboring String's in a ParaLine, potentially (by MifTuPreener)

            if (ctype.equals("lb")) {   // A Line break
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA[<Char HardReturn>]]></tag>\n");
                sourceText.append("<x id='" + bxExXId++ + "' ctype='" + ctype + "'/>");
            }
            else if (ctype.equals("x-mif-tab")) {   // A tab
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA[<Char Tab>]]></tag>\n");
                sourceText.append("<x id='" + bxExXId++ + "' ctype='" + ctype + "'/>");
            }
            else  {
                // Write out a format string for the bx tag
                writeFormat("  <tag id='" + bxExXId + "'><![CDATA[<String ]]></tag>\n");

                // Now write a bx tag to XLIFF, incrementing the id value
                sourceText.append("<bx id='" + bxExXId++ + "' rid='" + nextAvailRid + "'");
                sourceText.append(" ctype='" + ctype + "'/>");
                sourceText.append(uChar);     // Write the actual character

                // Write the ex tag to XLIFF, incrementing the rid value 
                sourceText.append("<ex id='" + bxExXId + "' rid='" + nextAvailRid++ + "'/>");

                // Write out the format string, incrementing the id value afterward
                writeFormat("  <tag id='" + bxExXId++ + "'><![CDATA[> # end of String--Char]]></tag>\n");
            }
        }
    }
    
    /**
     * Return an indentation string of blanks, increasing the indentation by 1 space
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent() {
        return indent('+');
    }

    /**
     * Specify the number of characters to indent. Set the curIndent variable to
     * that value and return that number of space characters.
     * @param numSpaces The number of
     */
    private String indent(int numSpaces) {
        if (numSpaces >= 0) {
            curIndent = numSpaces;
        }
        return indent('0');
    }
    
    /**
     * Return an indentation string based on the current indentation
     * @param direction '+': increment by 1; '-': decrement by 1; '0': no change
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent(char direction) {
        switch(direction) {
            case '-': 
                curIndent -= 2;        // Decrease indentation
                break;
            case '0':               // No change
                break;

            case '+':               // Increase by one space.
            default:
                curIndent +=2;
                break;              // For completeness (redundancy)
        }
        if (curIndent < 0) curIndent = 0; // 0-length string is smallest
        
        char chars[] = new char[curIndent]; // This could be a zero-length string
        
        if (curIndent > 0) {
            Arrays.fill(chars, ' ');  // Fill the array with spaces ...
        }
        
        return new String(chars);   //   and return it.
    }

    /**
     * Passed values from two ctype attributes (of bx or x tags), pick the one
     * I prefer and return it.
     * @param ctype1 The ctype of the first tag
     * @param ctype2 The ctype of the second tag
     * @return The preferred ctype.
     */
    private String preferredCType(String ctype1, String ctype2) {
        if (ctype1.equals("x-mif-tab") || ctype2.equals("x-mif-tab")) {
            return "x-mif-tab";
        }
        if (ctype1.equalsIgnoreCase("x-mif-VariableName") || ctype2.equalsIgnoreCase("x-mif-VariableName")) {
            return "x-mif-VariableName";
        }
        if (ctype1.equalsIgnoreCase("x-mif-Variable") || ctype2.equalsIgnoreCase("x-mif-Variable")) {
            return "x-mif-Variable";
        }
        if (ctype1.equals("x-mif-bold") || ctype2.equals("x-mif-bold")) {
            return "x-mif-bold";
        }
        if (ctype1.equals("x-mif-italic") || ctype2.equals("x-mif-italic")) {
            return "x-mif-italic";
        }
        if (ctype1.equals("x-mif-plain") || ctype2.equals("x-mif-plain")) {
            return "x-mif-plain";
        }
        if (ctype1.equals("x-mif-superscript") || ctype2.equals("x-mif-superscript")) {
            return "x-mif-superscript";
        }
        if (ctype1.equals("x-mif-subscript") || ctype2.equals("x-mif-subscript")) {
            return "x-mif-subscript";
        }
        if (ctype1.equals("x-mif-normalposition") || ctype2.equals("x-mif-normalposition")) {
            return "x-mif-normalposition";
        }
        if (ctype1.equals("lb") || ctype2.equals("lb")) {
            return "lb";
        }
        if (ctype1.equals("link") || ctype2.equals("link")) {
            return "link";
        }
        
        return ctype2;    // Wussy cop-out
    }
    
    /**
     * Write a string to the format file. 
     * @param text The string to write to the format document. (No newlines, etc.
     *             are added. The caller must supply all desired formatting.)
     */
    /* package */ void writeFormat(String text) {
        // Write format file ...
        if (text != null) {
            try {
                formatOut.write(text);
                formatOut.flush();   // For debugging only
            }
            catch(IOException e) {
                System.err.println("Error writing format file.");
                System.err.println(e.getMessage());
            }        
        }
    }

    /**
     * Write a string to the temporary (intermediate) skeleton file
     * @param text The string to write to the tskeleton. (No newlines, etc.
     *             are added. The caller must supply all desired formatting.)
     */
    private void writeTSkeleton(String text) {
        // Write tskeleton file ...
        if (text != null) {
            try {
                tskeletonOut.write(text);
                tskeletonOut.flush();    // For debugging
            }
            catch(IOException e) {
                System.err.println("Error writing tskeleton file.");
                System.err.println(e.getMessage());
            }        
        }
    }

    /** 
     * Write the specified text to the output XLIFF stream.
     * @param text What to write
     */
    private void writeXliff(String text) {
        
        if ((text == null) || (text.length() == 0)) {
            return; // Hmmm ... Nothing to output
        }

        try {
            xliffOut.write(text);
            xliffOut.flush();   // For debugging
        }
        catch(IOException e) {
            System.err.println("Error writing to XLIFF: " + e.getMessage());
        }
    }
    
//    /**
//     * Return the value of the next available id to be used by bx, ex and x
//     * tags in the format file.
//     * @return The value to use next.
//     */
//    /* package */ int getCurBxExXId() {
//        return bxExXId;
//    }
    
    /** 
     * Does the core text from the candidate TU buffer, does it contain 
     * meaningful text? (Specifically, if it contains only system variables and 
     * white space, it doesn't contain meaningful text. Otherwise it does.)
     * @param buf A buffer that contains a potential translation unit
     * @return true if has meaningful text; else false.
     */ 
    private boolean hasMeaningfulText(String buf) {
        if (buf == null || buf.length() == 0) {
            return false;
        }
        
        // System variables begin with <$ ... if not present, then return true
        if (buf.indexOf("&lt;$") == -1) { 
            return true;
        }

        // Regex to match core TU strings consisting solely of system variables
        // and whitespace.
        String sysVarStr = "(&lt;\\$[A-Za-z0-9]+&gt;|" + TuPreener.WHITE_SPACE_CLASS +")+";
        Pattern sysVarPat = Pattern.compile(sysVarStr,Pattern.DOTALL);
        Matcher sysVarMatcher = sysVarPat.matcher("");
        
        sysVarMatcher.reset(buf);
        if (sysVarMatcher.matches()) {
            return false;         // No meaningful text
        }
        
        return true;
    }
    
//    /**
//     * Increment the value of the next available id to be used by bx, ex and x
//     * tags in the format file.
//     * @return The new value (following the increment)
//     */
//    /* package */ int incBxExXId() {
//        return ++bxExXId;
//    }

//    /**
//     * Return the value of the next available rid to be used by bx and ex
//     * tags in the format file.
//     * @return The value to use next.
//     */
//    /* package */ int getCurRid() {
//        return nextAvailRid;
//    }
    
//    /**
//     * Increment the value of the next available rid to be used by bx and ex 
//     * tags in the format file.
//     * @return The new value (following the increment)
//     */
//    /* package */ int incRid() {
//        return ++nextAvailRid;
//    }
    
    /**
     * Unescape hex digits that appear in an input stream.
     * @param src The InputStream that contains potential escaped hex characters
     * @return The converted (unescaped) InputStream
     */
    private InputStream unescapeHexLits(InputStream src) throws IOException {
        // We'll read input (with potentially escaped hex chars) from a buffered
        // input stream
        BufferedInputStream in = new BufferedInputStream(src);
        
        // We will write output to a pipe
        PipedOutputStream pipeOut = new PipedOutputStream();
        
        // ... which will pipe it to another input stream (pipeIn)
        PipedInputStream pipeIn = new PipedInputStream(pipeOut);
        
        // ... A print stream will print the PipedOutputStream's output
        PrintStream out = new PrintStream(pipeOut);
        
        // Start the thread
        new NonFrameRomanUnescaper(in, out).start();
        
        return pipeIn;    // Return the pipe to read from
    }
    
    /**
     * This class reads from a non-FrameRoman-encoded (likely) input stream
     * and echos the input to an output stream, unescaping MIF characters of the
     * format "\xab " (where a and b represent hex digits). Note that it extends
     * Thread, so that it can be part of a Pipe.
     */
    public class NonFrameRomanUnescaper extends Thread {
        private BufferedInputStream in; 
        private PrintStream out;

        /**
         * Constructor for a NonFrameRomanUnescaper
         * @param out A Writer that I write to
         * @param in A Reader that I read from.
         */
        public NonFrameRomanUnescaper(BufferedInputStream in, PrintStream out) {
            this.in = in;
            this.out = out;
        }

        /**
         * This starts a thread that does the actual processing.
         */
        public void run() {
            if (out != null && in != null) {
                try {
                    int b;
                    // Read until end of stream.
                    while ((b = in.read()) != -1) {
                        if (b == '\\') {
                            // Possibly an escaped hex byte literal
                            int bx = in.read();
                            if (bx == -1) {
                                // What!? The \\ was the last thing in the stream.
                                // Write it out and we're done
                                out.write(b);  // Write the \\ that we thought might introduct an xab
                                break;
                            }
                            else if (bx == 'x') {
                                // \x should be followed by two hex chars and a space
                                byte[] threeChars = new byte[3];  // 2 hex and space
                                int numRead = in.read(threeChars);
                                if (numRead < 3) {
                                    System.err.println("Input stream truncated. Exiting");
                                    System.exit(2);
                                }
                                else {
                                    // Decode the two digits and write them as a byte to output
                                    String literal = "0x" + new String(threeChars, 0, 2);
                                    out.write(Integer.decode(literal));
                                }

                            }
                        }
                        else {
                            // Just write the byte we read to output.
                            out.write(b);
                        }
                    } // while
                    
                    // I *guess* we should close?
                    in.close();
                    out.close();
                }
                catch (FileNotFoundException e) {
                    System.err.println("Caught FileNotFoundException: " + e.getMessage());
                    System.exit(1);
                }
                catch (IOException e) {
                    System.err.println("Caught IOException: " + e.getMessage());
                    System.exit(1);
                }
            }
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
        return;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    //
    //  Methods for preening MIF TUs:
    //
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Passed the full text of a MIF Translation Unit source (not including the 
     * source begin/end tags), simplify, factor, etc. the string and return its
     * simplified form.
     * @param in The full text of the Translation Unit source.
     * @return The modified text of the Translation Unit.
     */
    private String preenTu(String in) {
        
        // Call the following (in order)
        // 1. TuPreener.markCoreTu (to mark the core TU)
        // 2. subsumeChars (to merge Chars into adjacent Strings)
        // 3. promoteMifRepeatingPairs (to combine TUs composed solely of
        //      generic String segments into one string and promote the
        //      bx/ex tags outside the TU core text mrk tags)
        // 4. combineGenericStringSegments (to merge adjacent string segments
        //      in TUs not affected by promoteMifRepeatingPairs)
        // 5. mergeFontBlocks (to replace the numerous tags of a font block
        //      with a single tag.
        // 6. mergeXRefBlocks (to do the same with XRef's)'
        // 5. deleteGenericStringTags (to delete bx/ex tags for generic string
        //      segments altogether, leaving only "special" string segments
        // 6. finalPreen (to do final cleanup, etc. This also deletes
        //      the core text mrk tags inserted in step 1 above, to allow sentence-
        //      level segmentation to occur.)
        
        String newTu =   finalPreen(
                           deleteGenericStringTags(
                             mergeXRefBlocks(
                               mergeFontBlocks(
                                 combineGenericStringSegments(
                                   promoteMifRepeatingPairs(
                                     subsumeChars(
                                       TuPreener.markCoreTu(in)))))))); 
        // If newTu includes any bx/ex/x tags, pass the core through the
        // generic preener one more time. Pass the result through collapseNestedBxEx
        String core = TuPreener.getCoreText(newTu);
        
        if ((core.indexOf("<bx ") != -1) || (core.indexOf("<x ") != -1)
            || (core.indexOf("<ex ") != -1)) {
            // Try preening just the core
            String newCore = TuPreener.markCoreTu(core);
            if (! TuPreener.getCoreText(newCore).equals(core)) {
                newTu =
                    // The new (possibly expanded) prefix
                    TuPreener.getPrefixText(newTu) 
                        + TuPreener.getPrefixText(newCore)
                        + TuPreener.CORE_START_MRK
                    // plus the new (possibly shortened) core
                  + TuPreener.getCoreText(newCore)
                  + TuPreener.CORE_END_MRK
                    // plus the new (possibly expanded) suffix
                  + TuPreener.getSuffixText(newCore)
                      + TuPreener.getSuffixText(newTu);
            }
            newTu = collapseNestedBxEx(newTu);
        }
        
//        return newTu;
        return TuPreener.removeCoreMarks(newTu);
    }

    // Start by looking for immediately adjacent x tags with no intervening
    // space/text
    private String twoXesPattern = "^(.*?)"      // Text before the 1st x tag
        // Then the first x tag:
        + "(<x id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^/>]*/>"
        // and an immediately following second x tag:
        + "<x id=(['\"])\\d+\\6[^>]+?ctype=(['\"])([^'\"]*)\\7[^/>]*/>)"
        // ... and text following the second x tag:
        +  "(.*)$"; 

    private Matcher twoXesMatcher = Pattern.compile(twoXesPattern,
        Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");
            
    // Regex for bx-bx-text-ex-ex block
    private String bxBxExExPattern = "^(.*?)"
        // Match the first of two adjacent bx tags
        // Group 5 is 1st ctype; group 7 is 1st rid
        + "(<bx id=(['\"])\\d+\\3[^>]+?rid=(['\"])(\\d+)\\4[^/>]+?ctype=(['\"])([^'\"]*)\\6[^>]*/>\\s*"
        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
        // important of the two ctypes
        // 2nd ctype is group 10
        // 2nd rid is group 12
        + "<bx id=(['\"])\\d+\\8[^>]+?rid=(['\"])(\\d+)\\9[^/>]+?ctype=(['\"])([^'\"]*)\\11[^>]*/>)" 
        // Then stuff between the above bxes and the closing exes
        + "([^<].*?)"                            // At least 1 character long, group 13
        // Then the first ex (with rid matching the second bx above)
        +  "(<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\10\\16 */>\\s*"
        // And the second ex (with rid matching the first bx above)
        +  "<ex id=(['\"])\\d+\\17[^>]+?rid=(['\"])\\5\\18 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    private Matcher bxBxExExMatcher = Pattern.compile(bxBxExExPattern,
            Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");

//    // Regex for bx-bx-ex-ex block (no intervening text) to map to x tag.
//    private String bxExToXPattern = "^(.*?)"
//        // Match the first of two adjacent bx tags
//        + "(<bx id=(['\"])\\d+\\3[^>]+?rid=(['\"])(\\d+)\\4[^/>]+?ctype=(['\"])([^'\"]*)\\6[^>]*/>\\s*"
//        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
//        // important of the two ctypes
//        // 2nd ctype is group 10
//        // 2nd rid is group 12
//        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>" 
//        // Then the first ex (with rid matching the second bx above)
//        +  "<ex id=(['\"])\\d+\\13[^>]+?rid=(['\"])\\12\\14 */>\\s*"
//        // And the second ex (with rid matching the first bx above)
//        +  "<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\7\\16 */>)"
//        // Text that follows the span:
//        +  "(.*)$";  // What follows
//
//    private Matcher bxExToXMatcher = Pattern.compile(bxExToXPattern,
//            Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");

    // Now look for a bx and ex surrounding (with no intervening space
    // or text) an x element. Convert them to a single x element
    private String bxXExPattern = "^(.*?)"   // Leading characters before first bx
        // Then a bx tag.
        + "(<bx id=(['\"])\\d+\\3[^>]+?rid=(['\"])(\\d+)\\4[^/>]+?ctype=(['\"])([^'\"]*)\\6[^>]*/>"
        // and an x tag
        + "<x id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^/>]*/>"
        // ... followed by a matching ex tag
        + "<ex id=(['\"])\\d+\\11[^>]+?rid=(['\"])\\5\\12 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    private Matcher bxXExMatcher = Pattern.compile(bxXExPattern,
        Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");
    
    // Now look for a bx and ex with no intervening space or text. 
    // Convert them to a single x element.
    private String bxExPattern = "^(.*?)"   // Leading characters before first bx
        // Then a bx tag.
        + "(<bx id=(['\"])\\d+\\3[^>]+?rid=(['\"])(\\d+)\\4[^/>]+?ctype=(['\"])([^'\"]*)\\6[^>]*/>"
        // ... followed by a matching ex tag
        + "<ex id=(['\"])\\d+\\8[^>]+?rid=(['\"])\\5\\9 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    private Matcher bxExMatcher = Pattern.compile(bxExPattern,
        Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");
    
    
    /**
     * Passed the full text of a Translation Unit source, check the core text 
     * for nested bx and ex tags, x tags nested in bx/ex tags, and immediately
     * adjacent x tags.
     * <ul>
     * <li>If two x tags are immediately adjacent and not separated by text,
     * replace them with a single x tag, noting the replacement in the format
     * file.<li>
     * <li>Replace multiply nested bx/ex tags with a single pair
     * of bx/ex tags, adding a format file entry to map the single bx tag to the
     * bx tags it replaces and the single ex tag to the ex tags it replaces.</li>
     * <li>Replace an x tag surrounded by a bx and ex pair (with no intervening
     * text) with a single x tag.</li>
     * <li>If a bx/ex pair has no intervening text, replace it with an x
     * tag, noting it in the format file for later expansion during export.</li>
     * </ul>
     * <p>Do the above iteratively until the bx/ex/x tags are maximally collapsed.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseNestedBxEx (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains core text mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("combineGenericStringSegments: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before mrk tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk mtype='x-coretext'> ... </mrk>

        // See if the input string includes a bx or x tag. If it doesn't return now
        if (coreText.indexOf("<bx ") == -1 && coreText.indexOf("<x") == -1) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.

        // We will repeatedly loop through coreText, looking for simplifications/
        // collapses we can perform. As soon as we have an iteration during which
        // no change occurs, we will quit and return
        
        String curCore = coreText;   // Start with initial core text
        
        boolean modified = true;  // Loop through while at least once
        
        while (modified) {
            modified = false;     // If anything matches, set this to true in the
                                  // loop.

            twoXesMatcher.reset(curCore);
            if (twoXesMatcher.find()) {
                modified = true;             // We'll modify something this time
                String prefix      = twoXesMatcher.group(1);
                String twoXes      = twoXesMatcher.group(2);
                String firstCType  = twoXesMatcher.group(5);
                String secondCType = twoXesMatcher.group(8);
                String suffix      = twoXesMatcher.group(9);

                // Then write the two x tags to the format file.
                writeFormat("  <tag id='" + bxExXId + "' recursive='yes'>"
                        + "<![CDATA[" + twoXes + "]]></tag>\r\n");

                curCore = prefix + "<x id='" + bxExXId + "' ctype='"
                    + preferredCType(firstCType,secondCType) + "'/>" + suffix;
                // Increment the x tag's id attribute for next use
                bxExXId++;
                
            }

            // Now look for adjacent bx tags followed (with or without intervening
            // text) followed by matching adjacent ex tags. If found, replace them
            // with a single bx and ex
            // Check the Core as it currently stands.
            bxBxExExMatcher.reset(curCore);   // Search what's left of the TU
            if (bxBxExExMatcher.find()) {
                modified = true;             // We'll modify something this time
                String prefix = bxBxExExMatcher.group(1);  // Before the first bx
                String bxBx   = bxBxExExMatcher.group(2);  // First two adjacent bxes
                String enclosed = bxBxExExMatcher.group(13); // Enclosed by bxbx exex 
                String exEx   = bxBxExExMatcher.group(14); // Matching adjacent exes
                String suffix = bxBxExExMatcher.group(19); // AFter last ex
                String outerCtype = bxBxExExMatcher.group(7);  // Outer ctype
                String innerCtype = bxBxExExMatcher.group(12); // Inner ctype
                
                int bxID = bxExXId++;
                int exID = bxExXId++;

                // Write the two bx tags to the format file.
                writeFormat("  <tag id='" + bxID + "' recursive='yes'>"
                    + "<![CDATA[" + bxBx + "]]></tag>\r\n");

                // Then the two adjacent ex tags.
                writeFormat("  <tag id='" + exID + "' recursive='yes'>"
                    + "<![CDATA[" + exEx + "]]></tag>\r\n");

                // Now adjust the current core text
                curCore = prefix + "<bx id='" + bxID + "' rid='" + nextAvailRid + "' ctype='" 
                    + preferredCType(outerCtype,innerCtype)
                    + "'/>"
                    + enclosed
                    + "<ex id='" + exID + "' rid='" + nextAvailRid + "'/>"
                    + suffix;

                // Increment nextAvailRid
                nextAvailRid++;                   // Increment our local copy
            }

            // Look for an x tag tightly surrounded by a bx and ex. Convert them
            // to a single x.
            bxXExMatcher.reset(curCore);   // Search what's left of the TU
            if (bxXExMatcher.find()) {
                modified = true;             // We'll modify something this time
                String prefix = bxXExMatcher.group(1);  // Before the first bx
                String bxXEx   = bxXExMatcher.group(2); // Original bx-x-ex
                String suffix = bxXExMatcher.group(13); // After last ex
                String bxExCtype = bxXExMatcher.group(7);  // Outer ctype
                String xCtype = bxXExMatcher.group(10);    // Inner ctype
                
                // Write the three tags to the format file.
                writeFormat("  <tag id='" + bxExXId + "' recursive='yes'>"
                    + "<![CDATA[" + bxXEx + "]]></tag>\r\n");

                // Now adjust the current core text
                curCore = prefix + "<x id='" + bxExXId + "' ctype='" 
                    + preferredCType(bxExCtype,xCtype) + "'/>"
                    + suffix;

                // Increment the bx/x/ex id for next use.
                bxExXId++;                   
            }
        
            // Look for adjacent bx and ex with no intervening characters/spaces
            bxExMatcher.reset(curCore);   // Search what's left of the TU
            if (bxExMatcher.find()) {
                modified = true;             // We'll modify something this time
                String prefix = bxExMatcher.group(1);  // Before the first bx
                String bxEx   = bxExMatcher.group(2); // Original bx-x-ex
                String suffix = bxExMatcher.group(10); // After last ex
                String ctype  = bxExMatcher.group(7);  // Ctype
                
                // Write the two tags to the format file.
                writeFormat("  <tag id='" + bxExXId + "' recursive='yes'>"
                    + "<![CDATA[" + bxEx + "]]></tag>\r\n");

                // Now adjust the current core text
                curCore = prefix + "<x id='" + bxExXId + "' ctype='" 
                    + ctype + "'/>" + suffix;

                // Increment the bx/x/ex id for next use.
                bxExXId++;                   
            }
        }
        
        // We've done all we can. Return the fixed up string.'
        return (pfxText + TuPreener.CORE_START_MRK + curCore + 
                TuPreener.CORE_END_MRK + sfxText); 
    }
    
    // The following matches two bx's (with ctype x-mif-ParaLine and 
    // x-mif-String), some text, and matching ex's to match the bx's. The
    // rid of the second ex *must* match the rid of the first bx. (Note the
    // \\2 meta character in the rid-value position of the second ex tag.)
    // The rid of the second bx will generally match the rid of the first
    // ex, unless the string is interrupted by a Char tag (which closes
    // a String tag but not a ParaLine).
    private String genericExpected = "^(.*?)"
                 + "<bx id=['\"](\\d+)['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-ParaLine['\"] */>"
                 + "<bx id=['\"](\\d+)['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>"
                 + "(.+?)"   // Captures the text of this string segment
                 + "<ex id=['\"](\\d+)['\"] +rid=['\"]\\5['\"] */>"
                 + "<ex id=['\"](\\d+)['\"] +rid=['\"]\\3['\"] */>"
                 // Followed by "the rest" (i.e., the "tail")
                 + "(.*)";
    private Matcher genericMatcher = Pattern.compile(genericExpected,Pattern.DOTALL).matcher("");
    
    /**
     * Similar to promoteMifRepeatingPairs, this method is called if 
     * promoteMifRepeatingPairs fails to move all bx/ex tags outside the core
     * TU.
     * <p>Unlike promoteMifRepeatingPairs, which combines <i>all</i> string 
     * segments to create a single string for the entire TU, 
     * combineGenericStringSegments combines adjacent plaintext string segments.
     * (Example: A TU consists of 8 string segments, the first 5 of which are 
     * plaintext [tagged in the "standard" way], the 6th is italicized, and the 
     * 7th and 8th are plaintext like the first 5. This method will merge the 
     * first 5 into a single segment, the 7th and 8th into a single segment, 
     * and leave the 6th substring untouched.
     * @param in The full text of a Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String combineGenericStringSegments(String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("combineGenericStringSegments: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains core text mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("combineGenericStringSegments: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before mrk tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk mtype='x-coretext'> ... </mrk>

        // See if the input string includes a bx tag. If it doesn't return now
        if (coreText.indexOf("<bx ") == -1) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.

        
        // Loop through core text looking for a repeating pattern as defined in
        // method's introductory comment
        
        // We will retain the id, rid and ctype attributes of the first two bx and
        // first two ex tags, discarding the other bx and ex tags.
        
        // Non-matching prefatory characters:
        String prefix = "";
        
        // id attrs of the opening bx pair in the first of a series of substrings
        // with repeating tags:
        String outerBxId = "", innerBxId = "";
        
        // Used by bx and matching ex
        String outerRid = "", innerRid = "";

        // After two bx tags come a text string, followed by the inner and
        // outer ex tags that match the bx tags
        String innerExId = "", outerExId = "";
        

        
        String tuText = coreText;   // We'll "eat' this as we loop through a matching TU
        String curAdjacentText = ""; // Text accumulated in currently adjacent segments
        String curText = "";        // The text in this iteration.
        String newTu = pfxText + TuPreener.CORE_START_MRK;    // The new TU we will return
        int curNumAdjacentSegs = 0; // Number of current adjacent segments
        boolean isRepeat = false;   // No a repeat ... yet
        
        MAIN_LOOP:
        while (tuText.trim().length() > 0) {
            // Search on the remainder of the
            genericMatcher.reset(tuText);   // On subsequent iterations this is the "tail" of the string
            if (genericMatcher.find()) {
                
                // If the current string begins with some non-matching characters, then 
                // we just read some characters that aren't a part of an adjacent string
                // segment (possibly followed by the first of one or more adjacent string
                // segments.
                if (genericMatcher.group(1).length() > 0) {
                    // If the non-matching characters were preceded by matching segments,
                    // merge them together and append them to the "newTu" text we've
                    // accumulated so far.
                    if (curNumAdjacentSegs > 0) {
                        // Add a combined segment to the newTu that will be output
                        newTu += "<bx id='" + outerBxId + "' rid='" + outerRid + "' ctype='x-mif-ParaLine'/>"
                               + "<bx id='" + innerBxId + "' rid='" + innerRid + "' ctype='x-mif-String'/>"
                               + curAdjacentText   // Text accumulated in this run
                               + "<ex id='" + innerExId + "' rid='" + innerRid + "'/>"
                               + "<ex id='" + outerExId + "' rid='" + outerRid + "'/>";
                        curNumAdjacentSegs = 0;    // Reset a bunch of variables
                        curAdjacentText = "";
                        outerBxId = innerBxId = outerRid = innerRid = innerExId = outerExId = "";
                    }
                    
                    // Then append the non-adjacent prefix of this match
                    newTu += genericMatcher.group(1);
                }
                
                if (curNumAdjacentSegs == 0) {
                    // This is the first of string segment in a run.
                    curNumAdjacentSegs++;    // ... so now we have one "adjacent" segment
                    outerBxId = genericMatcher.group(2);
                    outerRid  = genericMatcher.group(3);
                    innerBxId = genericMatcher.group(4);
                    innerRid  = genericMatcher.group(5);
                    curAdjacentText = genericMatcher.group(6);
                    innerExId = genericMatcher.group(7);
                    outerExId = genericMatcher.group(8);
                }
                else {
                    curNumAdjacentSegs++;
                    curAdjacentText += genericMatcher.group(6);
                }
                
                tuText = genericMatcher.group(9);    // For the next match
            }
            
            else {      // No match.
                break;  // Quit looking.
            }
        }
        
        // Before returning the newTu, be sure we process any outstanding accumulations.
        
        // First, any accumulated adjacent pairs
        if (curNumAdjacentSegs > 0) {
            // Add a combined segment to the newTu that will be output
            newTu += "<bx id='" + outerBxId + "' rid='" + outerRid + "' ctype='x-mif-ParaLine'/>"
                   + "<bx id='" + innerBxId + "' rid='" + innerRid + "' ctype='x-mif-String'/>"
                   + curAdjacentText   // Text accumulated in this run
                   + "<ex id='" + innerExId + "' rid='" + innerRid + "'/>"
                   + "<ex id='" + outerExId + "' rid='" + outerRid + "'/>";
        }
        
        // Then, append what is left of the tuText to newTu (if anything is left)
        newTu += tuText;
        
        // Then return what is left (appending the ending mrk tag and what follows it ...)
        return (newTu + TuPreener.CORE_END_MRK + sfxText); 
    }

    private String enclosing = "^(<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-ParaLine['\"] */>"
                 //    + "(?:<x id=['\"]\\d+['\"] ctype=['\"]x-mif-TextRectID['\"] */>)?" // Optional TextRectID
        + "<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>)"
        + "(.+)"   // Captures everything inside the enclosing pairs
        + "(<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>"
        + "<ex id=['\"]\\d+['\"] +rid=['\"]\\2['\"] */>)$";
    private Matcher enclosingMatcher = Pattern.compile(enclosing,Pattern.DOTALL).matcher("");

    // The following matches two bx's (with ctype x-mif-ParaLine and 
    // x-mif-String), some text, and matching ex's to match the bx's. The
    // rid of the second ex *must* match the rid of the first bx. (Note the
    // \\2 meta character in the rid-value position of the second ex tag.)
    // The rid of the second bx will generally match the rid of the first
    // ex, unless the string is interrupted by a Char tag (which closes
    // a String tag but not a ParaLine).
    private String expected2 = "^(.*?)"
                 + "<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-ParaLine['\"] */>"
                 + "<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>"
                 + "(.+?)"   // Captures the text of this string segment
                 + "<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>"
                 + "<ex id=['\"]\\d+['\"] +rid=['\"]\\2['\"] */>"
                 // Followed by "the rest" (i.e., the "tail")
                 + "(.*)";
    private Matcher expected2Matcher = Pattern.compile(expected2,Pattern.DOTALL).matcher("");
        
    
    /**
     * Identify generic substrings within the TU--string segments that
     * are introduced by (for example--the id and rid will vary):
     * <pre>
     * &lt;bx id="360" rid="159" ctype="x-mif-ParaLine"/&gt;
     * &lt;bx id="361" rid="160" ctype="x-mif-String"/&gt;
     * </pre>
     * and followed immediately by:
     * <pre>
     * &lt;ex id="362" rid="160"/&gt;
     * &lt;ex id="363" rid="159"/&gt;
     * </pre>
     * After identifying the tags around those segments, delete those tags.
     * @param in The full text of a Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String deleteGenericStringTags(String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("deleteGenericStringTags: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains core text mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("deleteGenericStringTags: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk> ... </mrk>

        // See if the input string includes a bx tag. If it doesn't return now
        if (coreText.indexOf("<bx ") == -1) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.

        // Before deleting anything, see if the core is enclosed by ParaLine/String
        // duples. If it is, move them outside the x-coretext mrk tag.
        // Note: a TextRectID can optionally occur between the ParaLine and String.
        enclosingMatcher.reset(coreText);
        if (enclosingMatcher.find()) {
            pfxText += enclosingMatcher.group(1);
            coreText = enclosingMatcher.group(4);
            sfxText = enclosingMatcher.group(5) + sfxText;
        }
        
        // Loop through core text looking for the tags to delete.
        
        // Non-matching prefatory characters:
        String prefix = "";
        
        // id attrs of the opening bx pair in the first of a series of substrings
        // with repeating tags:
        String outerBxId = "", innerBxId = "";
        
        // Used by bx and matching ex
        String outerRid = "", innerRid = "";

        // After two bx tags come a text string, followed by the inner and
        // outer ex tags that match the bx tags
        String innerExId = "", outerExId = "";

        String tuText = coreText;   // We'll "eat' this as we loop through a matching TU
        String curAdjacentText = ""; // Text accumulated in currently adjacent segments
        String curText = "";        // The text in this iteration.
        String newTu = pfxText + TuPreener.CORE_START_MRK;    // The new TU we will return
        int curNumAdjacentSegs = 0; // Number of current adjacent segments
        boolean isRepeat = false;   // No a repeat ... yet
        
        MAIN_LOOP:
        while (tuText.trim().length() > 0) {
            // Search on the remainder of the
            expected2Matcher.reset(tuText);   // On subsequent iterations this is the "tail" of the string
            if (expected2Matcher.find()) {
                newTu += (expected2Matcher.group(1) + expected2Matcher.group(4));
                tuText = expected2Matcher.group(5);    // The tail--what's left
            }
            else {      // No match.
                break;  // Quit looking.
            }
        }
        
        
        // Then, append what is left of the tuText to newTu (if anything is left)
        newTu += tuText;
        
        // Append the ending mrk tag and what follows it ... and return
        return (newTu + TuPreener.CORE_END_MRK + sfxText); 
    }

    // Matcher of string tags.
    private String stringTags = "^(.*?)"        // Leading characters
                 + "(<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>)"
                 + "(.+)"   // The string text
                 + "(<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>)"
                 + "(.*)$";
    private Matcher stringTagMatcher = Pattern.compile(stringTags,Pattern.DOTALL).matcher("");

    // Matcher of paraline tags
    private String paraTags = "^(.*?)"        // Leading characters
                 + "(<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-ParaLine['\"] */>)"
                 + "(.+)"   // The string text
                 + "(<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>)"
                 + "(.*)$";
    private Matcher paraTagsMatcher = Pattern.compile(paraTags,Pattern.DOTALL).matcher("");
    
    /**
     * Identify (and remove) remaining String and ParaLine bx/ex tags from
     * the TU. Then make sure that the core prefix and suffix contain ParaLine
     * and String tags.
     * @param in The full text of a Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String finalPreen(String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("finalPreen: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains x-coretext mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("finalPreen: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk> ... </mrk>

        // See if the input string includes a bx tag. If it doesn't return now
        if (coreText.indexOf("<bx ") == -1) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.

        // Delete String pairs.
        String newCore = "";          // We'll put the new preened core here
        String tuTail = coreText;     // The rest of the TU 
        boolean deletedSomething = false; // Haven't deleted anything yet.
        
        while (tuTail.trim().length() > 0) {
            stringTagMatcher.reset(tuTail);
            if (stringTagMatcher.find()) {
                deletedSomething = true;
                newCore += (stringTagMatcher.group(1) + stringTagMatcher.group(4));
                tuTail = stringTagMatcher.group(6);
            }
            else {
                break;
            }
        }
        newCore += tuTail;  // Either zero-length tail or non-matching tail.
        
        // We've removed remaining String pairs. Now let's do the same for ParaLine's'
        tuTail = newCore;     // The tail begins with the entire new core.
        newCore = "";
        
        while (tuTail.trim().length() > 0) {
            paraTagsMatcher.reset(tuTail);
            if (paraTagsMatcher.find()) {
                deletedSomething = true;
                newCore += (paraTagsMatcher.group(1) + paraTagsMatcher.group(4));
                tuTail = paraTagsMatcher.group(6);
            }
            else {
                break;
            }
        }
        newCore += tuTail;  // Either zero-length tail or non-matching tail.
        
        // We have now deleted all ParaLine and String tags from the core
        // We need to make sure there are some immediately outside the core
        if (deletedSomething) {
            if ((pfxText.indexOf("x-mif-ParaLine") == -1)
                && (pfxText.indexOf("x-mif-String") == -1)) {
                // No ParaLine/String at all in TU. Add some
//                int ridId = mifConverter.getCurRid();  // Ditto for rid

                // Then write the XLIFF tags of the Font element and opening String to the format file.
                // First the opening elements (to the core prefix)
                String tagStr = "  <tag id='" + bxExXId + "'><![CDATA[<ParaLine]]></tag>\r\n";
                writeFormat(tagStr);
                pfxText += ("<bx id='" + bxExXId + "' rid='" + nextAvailRid + "' ctype='x-mif-ParaLine'/>");

//                mifConverter.incBxExXId();       // Have the converter increment the bx id
                bxExXId++;                        // Then increment our copy
                
                // Close the Paraline
                tagStr = "  <tag id='" + bxExXId + "'><![CDATA[> # end of ParaLine]]></tag>\r\n";
                writeFormat(tagStr);
                sfxText = "<ex id='" + bxExXId + "' rid='" + nextAvailRid + "'/>" + sfxText;
                
//                mifConverter.incBxExXId();       // Have the converter increment the bx id
                bxExXId++;                        // Then increment our copy
//                mifConverter.incRid();
                nextAvailRid++;
                
                // Now ditto for the String tag:
                tagStr = "  <tag id='" + bxExXId + "'><![CDATA[<String ]]></tag>\r\n";
                writeFormat(tagStr);
                pfxText += ("<bx id='" + bxExXId + "' rid='" + nextAvailRid + "' ctype='x-mif-String'/>");

//                mifConverter.incBxExXId();       // Have the converter increment the bx id
                bxExXId++;                        // Then increment our copy
                
                // Close the Paraline
                tagStr = "  <tag id='" + bxExXId + "'><![CDATA[> # end of String]]></tag>\r\n";
                writeFormat(tagStr);
                sfxText = "<ex id='" + bxExXId + "' rid='" + nextAvailRid + "'/>" + sfxText;
                
                bxExXId++;  
//                mifConverter.incBxExXId();       // Have the converter increment the bx id
                nextAvailRid++;
//                mifConverter.incRid();
                // No need to increment our copies of id nums ...
            }
        }
        
        return (pfxText + TuPreener.CORE_START_MRK + newCore + TuPreener.CORE_END_MRK + sfxText); 
    }

    
    // Regex for Font block
    private String fontRE = "^(.*?)"
                 + "(<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-Font['\"] */>"
                 + "(?:<[be]?x id=['\"]\\d+['\"][^/>]*/>)*?"          // Intervening xliff tags
                 + "<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>"    // End of x-mif-Font
                 // Followed by an x-mif-String bx
                 + "<bx id=['\"]\\d+['\"] rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>)"
                 // Followed by the printed string
                 + "(.*?)"
                 // Followed by the ex that closes the x-mif-String
                 + "(<ex id=['\"]\\d+['\"] +rid=['\"]\\4['\"] */>)"
                 // Followed by "the rest" (i.e., the "tail")
                 + "(.*)$";

    private Matcher fontMatcher = Pattern.compile(fontRE,Pattern.CASE_INSENSITIVE).matcher("");

    // Regex to match/capture interesting font happenings.
    private String cTypeRE = "(?:x-mif-)?(subscript\\b|superscript\\b|bold\\b"
        + "|nonbold\\b|italic\\b|nonitalic\\b|normalposition\\b)";
    private Matcher cTypeMatcher = Pattern.compile(cTypeRE,Pattern.CASE_INSENSITIVE).matcher("");
    
    /**
     * Passed the full text of a MIF Translation Unit source (including
     * core start and end markers), check for Font blocks and merge the
     * entire font block into a single bx/ex pair (adding two entries in the 
     * format file (one for the bx, one for the ex) that will map the bx and ex 
     * tags to their original XLIFF tags (which can in turn be mapped to their 
     * original MIF). A typical Font block might look something
     * like:
     * <pre>
     * &lt;bx id="221" rid="69" ctype="x-mif-Font"/&gt;
     * &lt;x id="222" ctype="x-mif-italic"/&gt;
     * &lt;x id="223" ctype="x-mif-FPairKern"/&gt;
     * &lt;x id="224" ctype="x-mif-FLocked"/&gt;
     * &lt;ex id="225" rid="69"/&gt;
     * </pre>
     * It is then immediately followed by a sequence like:
     * <pre>
     * <bx id="226" rid="70" ctype="x-mif-String"/>
     * This is an italicized string.
     * <ex id="227" rid="70"/>
     * </pre>
     * @param in The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String mergeFontBlocks (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
//        if (mifConverter == null) {
//            System.err.println("mergeFontBlocks: mifConverter parameter is null; "
//                    + "returning input string unchanged.");
//            return in;
//        }
        
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("mergeFontBlocks: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains x-coretext mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("mergeFontBlocks: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk> ... </mrk>

        // See if the input string includes any tags of interest. If it doesn't,
        // return now
        if ((coreText.indexOf("<bx ") == -1) || (coreText.indexOf("x-mif-Font") == -1)) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.
        
        // Loop through core text looking for multiple adjacent x, bx and ex tags
        
        // When multiples are found, create a new x tag that maps to the multiple
        // adjacent tags. 
        

        
        String newTu = pfxText + TuPreener.CORE_START_MRK;  // The beginnings of a new TU
        String tuTail = coreText;    // We'll "eat' this as we loop through a matching TU
        String fontTags = "";
        String enclosedText = "";
        String closeTag = "";
        
        while (tuTail.length() > 0) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            fontMatcher.reset(tuTail);   // Search what's left of the TU
            if (fontMatcher.find()) {
                newTu += fontMatcher.group(1);      // Append characters before the Font tag
                fontTags = fontMatcher.group(2);    // The font element and opening String we'll replace
                enclosedText = fontMatcher.group(5);
                closeTag = fontMatcher.group(6);    // Closing string bx.
                tuTail   = fontMatcher.group(7);    // Remainder of core text ... for next iteration.

                // Look for interesting ctype candidate in the Font block
                cTypeMatcher.reset(fontTags);
                
                String ctype = "";
                if (cTypeMatcher.find()) {
                    if (cTypeMatcher.group(1).equals("bold")
                        || cTypeMatcher.group(1).equals("italic")) {
                        ctype = cTypeMatcher.group(1);
                    }
                    else {
                        ctype = "x-mif-" + cTypeMatcher.group(1);
                    }
//                }
//                else {
//                    ctype = "x-mif-Font-basic";     // We won't record this in xliff/format
//                }
                
                    // Get the next available bx/ex/x id
//                    int xId = mifConverter.getCurBxExXId();
//                    int ridId = mifConverter.getCurRid();  // Ditto for rid

                    // Then write the XLIFF tags of the Font element and opening String to the format file.
                    String tagStr = "  <tag id='" + bxExXId + "' recursive='yes'>"
                            + "<![CDATA[" + fontTags + "]]></tag>\r\n";
                    writeFormat(tagStr);

                    // Substitute an x tag for the Font block (in the new TU)
                    newTu += "<bx id='" + bxExXId + "' rid='" + nextAvailRid + "' ctype='" + ctype + "'/>";

                    // Increment the x/bx/ex id for the next format file writer
//                    mifConverter.incBxExXId();
                    bxExXId++;                   // Increment our local copy

                    // Now write the enclosed text to newTu
                    newTu += enclosedText;

                    // Then write the remapped closing font tag to format and newTu
//                    xId = mifConverter.getCurBxExXId();  // Get the next id

                    // Then write the XLIFF tags of the Font element and opening String to the format file.
                    tagStr = "  <tag id='" + bxExXId + "' recursive='yes'>"
                            + "<![CDATA[" + closeTag + "]]></tag>\r\n";
                    writeFormat(tagStr);

                    // Substitute an ex tag match the new x-font bx tag.
                    newTu += "<ex id='" + bxExXId + "' rid='" + nextAvailRid + "'/>";

                    // Increment the x/bx/ex id for the next format file writer
//                    mifConverter.incBxExXId();
                    bxExXId++;
//                    mifConverter.incRid();
                    nextAvailRid++;
                }
                else {
                    // Plain (basic) font. Ignore
                    // Just save the text.
                    newTu += enclosedText;
                }
                
            }
            else {  // We're done!
                break;
            }
        }
        
        // If there are non-Font-matching characters in tuTail, append them to
        // newTu, followed by the ending mrk marker and the core suffix
        return (newTu + tuTail + TuPreener.CORE_END_MRK + sfxText);
    }
    

    // Regex for xref
    private String xrefRE = "^(.*?)"
                 + "(<bx id=['\"]\\d+['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-XRef['\"] */>"
                 + "(?:<[be]?x id=['\"]\\d+['\"][^/>]*/>)*?"          // Intervening xliff tags
                 + "<ex id=['\"]\\d+['\"] +rid=['\"]\\3['\"] */>"    // End of x-mif-Font
                 // Followed by an x-mif-String bx
                 + "<bx id=['\"]\\d+['\"] rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-String['\"] */>)"
                 // Followed by the printed string
                 + "(.*?)"
                 // Followed by the ex that closes the x-mif-String
                 + "(<ex id=['\"]\\d+['\"] +rid=['\"]\\4['\"] */>"
                 // Followed by the bx/ex for the XRefEnd
                 + "<bx id=['\"]\\d+['\"] rid=['\"](\\d+)['\"] +ctype=['\"]x-mif-XRefEnd['\"] */>"
                 + "<ex id=['\"]\\d+['\"] +rid=['\"]\\7['\"] */>)"
                 // Followed by "the rest" (i.e., the "tail")
                 + "(.*)$";

    private Matcher xrefMatcher = Pattern.compile(xrefRE,Pattern.CASE_INSENSITIVE).matcher("");
        
    /**
     * Passed the full text of a MIF Translation Unit source (including
     * core start and end markers), check for XRef blocks and merge the
     * entire font block into a single bx/ex pair (adding two entries in the 
     * format file (one for the bx, one for the ex) 
     * @param in The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String mergeXRefBlocks (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
//        if (mifConverter == null) {
//            System.err.println("mergeXRefBlocks: mifConverter parameter is null; "
//                    + "returning input string unchanged.");
//            return in;
//        }
        
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("mergeXRefBlocks: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains x-coretext mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("mergeXRefBlocks: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk mtype='x-coretext'> ... </mrk>

        // See if the input string includes any tags of interest. If it doesn't,
        // return now
        if ((coreText.indexOf("<bx ") == -1) || (coreText.indexOf("x-mif-XRef") == -1)) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.
        
        String newTu = pfxText + TuPreener.CORE_START_MRK;  // The beginnings of a new TU
        String tuTail = coreText;    // We'll "eat' this as we loop through a matching TU
        String xrefTags = "";
        String enclosedText = "";
        String closeTags = "";
        
        while (tuTail.length() > 0) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            xrefMatcher.reset(tuTail);   // Search what's left of the TU
            if (xrefMatcher.find()) {
                newTu += xrefMatcher.group(1);      // Append characters before the Font tag
                xrefTags = xrefMatcher.group(2);    // The font element and opening String we'll replace
                enclosedText = xrefMatcher.group(5);
                closeTags = xrefMatcher.group(6);    // Closing string bx.
                tuTail   = xrefMatcher.group(8);    // Remainder of core text ... for next iteration.

                
                // Get the next available bx/ex/x id
//                int xId = mifConverter.getCurBxExXId();
//                int ridId = mifConverter.getCurRid();  // Ditto for rid

                // Then write the XLIFF tags of the XRef elements and opening String to the format file.
                String tagStr = "  <tag id='" + bxExXId + "' recursive='yes'>"
                        + "<![CDATA[" + xrefTags + "]]></tag>\r\n";
                writeFormat(tagStr);

                // Substitute a bx tag for the Font block (in the new TU)
                newTu += "<bx id='" + bxExXId + "' rid='" + nextAvailRid + "' ctype='link'/>";

                // Increment the x/bx/ex id for the next format file writer
//                mifConverter.incBxExXId();
                bxExXId++;                   

                // Now write the enclosed text to newTu
                newTu += enclosedText;

                // Then write the remapped closing font tag to format and newTu
//                xId = mifConverter.getCurBxExXId();  // Get the next id

                // Then write the XLIFF tags of the Font element and opening String to the format file.
                tagStr = "  <tag id='" + bxExXId + "' recursive='yes'>"
                        + "<![CDATA[" + closeTags + "]]></tag>\r\n";
                writeFormat(tagStr);

                // Substitute an ex tag match the new x-font bx tag.
                newTu += "<ex id='" + bxExXId + "' rid='" + nextAvailRid + "'/>";

                // Increment the x/bx/ex id for the next format file writer
//                mifConverter.incBxExXId();
                bxExXId++;
//                mifConverter.incRid();
                nextAvailRid++;
                
            }
            else {  // We're done!
                break;
            }
        }
        
        // If there are non-Font-matching characters in tuTail, append them to
        // newTu, followed by the ending x-coretext mrk tag marker and the core suffix
        return (newTu + tuTail + TuPreener.CORE_END_MRK + sfxText);
    }

    // The following matches two bxes, some text and matching exes to match the bxes:
    private String repeatingPairRE = 
             "^<bx id=['\"](\\d+)['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]([^'\"]+)['\"] */>"
        + "\\s*<bx id=['\"](\\d+)['\"] +rid=['\"](\\d+)['\"] +ctype=['\"]([^'\"]+)['\"] */>"
        +     "(.+)"
        +     "<ex id=['\"](\\d+)['\"] +rid=['\"]\\5['\"] */>"
        + "\\s*<ex id=['\"](\\d+)['\"] +rid=['\"]\\2['\"] */>(.*)";
    private Matcher repeatingMatcher = Pattern.compile(repeatingPairRE,Pattern.DOTALL).matcher("");
    
    /**
     * Passed the full text of a MIF Translation Unit source (including
     * core start and end markers) check for repeating pairs of tags that can
     * be factored out and placed outside the core markers. In MIF, this will
     * convert a segment that looks like this:
     * <pre>
     * &lt;mrk mtype='x-coretext'&gt;
     *  &lt;bx id="162" rid="50" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="163" rid="51" ctype="x-mif-String"/&gt;
     *  Common frequency modulation may lead to 
     *  &lt;ex id="164" rid="51"/&gt;
     *  &lt;ex id="165" rid="50"/&gt;
     *  &lt;bx id="166" rid="52" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="167" rid="53" ctype="x-mif-String"/&gt;
     *  common amplitude modulation as energy 
     *  &lt;ex id="168" rid="53"/&gt;
     *  &lt;ex id="169" rid="52"/&gt;
     *  &lt;bx id="170" rid="54" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="171" rid="55" ctype="x-mif-String"/&gt;
     *  shifts channels (Saberi and Hafter, 1995)
     *  &lt;ex id="172" rid="55"/&gt;
     *  &lt;ex id="173" rid="54"/&gt;
     * &lt;/mrk&gt;
     * </pre>
     * to look like:
     * <pre>
     * &lt;bx id="162" rid="50" ctype="x-mif-ParaLine"/&gt;
     * &lt;bx id="163" rid="51" ctype="x-mif-String"/&gt;
     * &lt;mrk mtype='x-coretext'&gt;
     *  Common frequency modulation may lead to 
     *  common amplitude modulation as energy 
     *  shifts channels (Saberi and Hafter, 1995)
     * &lt;/mrk&gt;
     * &lt;ex id="164" rid="51"/&gt;
     * &lt;ex id="165" rid="50"/&gt;
     * </pre>
     *
     * @param in The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The modified text of the Translation Unit.
     */
    private String promoteMifRepeatingPairs (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
//            System.err.println("promoteRepeatingPairs: No input string found; "
//                    + "returning zero-length string.");
            return "";                       // Return zero-length string
        }

        // Second of all, if the string doesn't contains core text mrk tags, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("promoteRepeatingPairs: No x-coretext mrk tags found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }
        

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk> ... </mrk>

        // See if the input string includes a bx tag. If it doesn't return now
        if (coreText.indexOf("<bx ") == -1) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>.

        
        // Loop through core text looking for a repeating pattern as defined in
        // method's introductory comment
        
        // We will retain the id, rid and ctype attributes of the first two bx and
        // first two ex tags, discarding the other bx and ex tags.
        
        // id attrs of the opening bx pair
        String firstOuterBxId = "";   
        String firstInnerBxId = "";

        String firstOuterRid = "";   // Used by bx and matching ex
        String firstInnerRid = "";   //  [ditto]
        
        String firstOuterCtype = ""; 
        String firstInnerCtype = "";

        // After two bx tags come a text string, followed by the inner and
        // outer ex tags that match the bx tags
        
        String firstInnerExId = "";
        String firstOuterExId = "";
        
        // We will loop through the text trying to match what we are expecting. If
        // we complete the loop, assume there is a match, ... and make a new
        // TU. Otherwise, just return the original input string
        boolean prematureExit = false;    // If true, this can't match'

        String tuText = coreText;   // We'll "eat' this as we loop through a matching TU
        String allText = "";  // The textual portion (without tags)
                
        int i = -1;  // Counter for "for" loop
        for (i = 0; tuText.trim().length() > 0 ; i++) {
            // Search on the remainder of the
            repeatingMatcher.reset(tuText);   // On subsequent iterations this is the "tail" of the string
            if (repeatingMatcher.find()) {
                if (i == 0) {  // Need more information on the first match.
                    firstOuterBxId  = repeatingMatcher.group(1);   // id of first bx
                    firstOuterRid   = repeatingMatcher.group(2);   // rid of first bx
                    
                    firstOuterCtype = repeatingMatcher.group(3);   // ctype of first bx
                    
                    firstInnerBxId  = repeatingMatcher.group(4);   // id of second bx
                    firstInnerRid   = repeatingMatcher.group(5);   // rid of second bx

                    firstInnerCtype = repeatingMatcher.group(6);   // ctype of second bx

                    allText = repeatingMatcher.group(7);
                    
                    // After two bx tags come a text string, followed by the inner and
                    // outer ex tags that match the bx tags
        
                    firstInnerExId = repeatingMatcher.group(8);    // id of first (inner) ex
                    firstOuterExId = repeatingMatcher.group(9);    // id of second (outer) ex
                    tuText = repeatingMatcher.group(10);           // For the next iteration
                }
                
                // This is the second (or later) set of bx/ex doubles
                // All we need to do is see if the rids of the bx/ex pairs match
                // between themselves, and make sure that the ctypes match those of
                // the first and second bx tags first encountered.  Also, capture
                // The text and append it to what has been captured so far.
                else {
                    if (!firstOuterCtype.equals(repeatingMatcher.group(3))) {
                        prematureExit = true;
                        break;                       // Can't work
                    }
                    else if (!firstInnerCtype.equals(repeatingMatcher.group(6))) {
                        prematureExit = true;
                        break;                       // Can't work
                    }
                    else {  // Matches so far. Capture the text and append it to the
                            // accumulated text.
                        allText += repeatingMatcher.group(7);

                        tuText = repeatingMatcher.group(10);  // For next iteration. ... the "tail"
                    }
                }
            }
            
            else {    // This TU text doesn't match what we're looking for
                prematureExit = true;
                break;
            }
        }
        
        // If prematureExit is true, it means that something didn't match what
        // we are looking for. If i is still 0, it means we have a single pair of
        // doubled tags--this doesn't match what we are trying to do.
        if (prematureExit || i < 2) {    // Something didn't match'
            return in;                    // Return what we received as input
        }
        else {  // This TU matched what we were looking for; Return the new TU
            String newTu = pfxText + "<bx id=\"" + firstOuterBxId + "\" rid=\"" + firstOuterRid 
                    + "\" ctype=\"" + firstOuterCtype + "\"/>"
                    + "<bx id=\"" + firstInnerBxId + "\" rid=\"" + firstInnerRid 
                    + "\" ctype=\"" + firstInnerCtype + "\"/>"
                    + TuPreener.CORE_START_MRK
                    + allText
                    + TuPreener.CORE_END_MRK
                    + "<ex id=\"" + firstInnerExId + "\" rid=\"" + firstInnerRid + "\"/>"
                    + "<ex id=\"" + firstOuterExId + "\" rid=\"" + firstOuterRid + "\"/>"
                    + sfxText;
            return newTu;
        }
    }

    // Regular expression that matches a string containing a special character.
    private String strWChar = "^(.*?)"    // Possible characters before 1st string (save these)
          // Matches the opening bx of 1st string, capturing the id and rid
        + "<bx id=['\"](\\d+)['\"] rid=['\"](\\d+)['\"] ctype=['\"]x-mif-String['\"] */>"
        + "(.+?)"                 // The text of the string (possibly one char)
          // Closing ex tag after the first string; rid matches bx's rid
        + "<ex id=['\"](\\d+)['\"] rid=['\"]\\3['\"] */>"
          // ... followed immediately by another x-mif-String
        + "<bx id=['\"]\\d+['\"] rid=['\"](\\d+)['\"] ctype=['\"]x-mif-String['\"] */>"
        + "(.*?)"                 // string text of the second string.
          // Closing ex tag after the second string; rid matches bx's rid
        + "<ex id=['\"]\\d+['\"] rid=['\"]\\6['\"]/>"
        + "(.*)$";               // Followed by the rest of the string.

    // Pattern for the above string with Char
    private Matcher strWithCharMatcher = Pattern.compile(strWChar,Pattern.DOTALL).matcher("");
    
    /**
     * During the import of Maker Interchange Files into XLIFF, Char elements 
     * are converted to one-character unicode strings in the XLIFF. XLIFF generated
     * from two adjacent ParaLine's in a MIF file might generally look like this,
     * with a single x-mif-String ctype within an x-mif-ParaLine:
     * <pre>
     *  &lt;bx id="162" rid="50" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="163" rid="51" ctype="x-mif-String"/&gt;
     *  Common frequency modulation may lead to 
     *  &lt;ex id="164" rid="51"/&gt;
     *  &lt;ex id="165" rid="50"/&gt;
     *  &lt;bx id="166" rid="52" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="167" rid="53" ctype="x-mif-String"/&gt;
     *  common amplitude modulation as energy 
     *  &lt;ex id="168" rid="53"/&gt;
     *  &lt;ex id="169" rid="52"/&gt;
     * </pre>
     * With our treatment of MIF Char tags as strings, a Yen Char within a
     * string would lead to the following XLIFF snippet:
     * <pre>
     *  &lt;bx id="162" rid="50" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="163" rid="51" ctype="x-mif-String"/&gt;
     *  I was paid
     *  &lt;ex id="164" rid="51"/&gt;
     *  &lt;bx id="165" rid="52" ctype="x-mif-String"/&gt;
     *  &yen;
     *  &lt;ex id="166" rid="52"/&gt;
     *  &lt;bx id="167" rid="53" ctype="x-mif-String"/&gt;
     *  50.
     *  &lt;ex id="168" rid="53"/&gt;
     *  &lt;ex id="169" rid="50"/&gt;
     * </pre>
     * ... with the &yen; string resulting from a MIF Char tag.
     * <p>When this method is invoked, it will return a string that
     * looks like:
     * <pre>
     *  &lt;bx id="162" rid="50" ctype="x-mif-ParaLine"/&gt;
     *  &lt;bx id="163" rid="51" ctype="x-mif-String"/&gt;
     *  I was paid &yen;50.
     *  &lt;ex id="164" rid="51"/&gt;
     *  &lt;ex id="169" rid="50"/&gt;
     * </pre>
     * @param in The input string. (<b>Important:</b> Before calling this
     *        method, the string should have been preened by the TuPreener's
     *        markCoreTu method. If core text mrk tags are not found, this method
     *        will return the input string unchanged.
     * @return The transformed string (or the original if no mapping is
     *         found.
     */
    private String subsumeChars (String in) {
        // Return the input string if there is nothing to process
        if ((in == null) || (in.length() == 0) || (in.trim().length() == 0)) {
            return in;
        }
        
        // Second of all, if the string doesn't contains x-coretext mrk, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_MRK) == -1) {
            System.err.println("subsumeChars: No x-coretext mrk tag found; "
                    + "returning string unchanged.");
            return in;                       // Just return what was received.
        }

        String pfxText = TuPreener.getPrefixText(in);  // Stuff before <mrk> tag
        String coreText = TuPreener.getCoreText(in);   // Within <mrk> ... </mrk>

        // See if the input string includes any tags of interest. If it doesn't,
        // return now
        if ((coreText.indexOf("<bx ") == -1) && (coreText.indexOf("<ex ") == -1)) {
            return in;
        }

        String sfxText = TuPreener.getSuffixText(in);  // After </mrk>
        
        String prefix = "";           // Before any match
        String bxId = "";             // id of 1st bx tag (the one we'll retain)
        String rid  = "";             // rid of the first pair of tags
        String text1 = "";            // Text of the first string segment
        String exId = "";             // id of 1st ex tag (we'll retain this one)
        String text2 = "";            // Text of the second string segment
        String suffix = "";
        
        String working = coreText;    // The text we'll work with, starting w/ coreText
        
        boolean foundAMatch = false;  // For optimization
        
        for ( ; ; ) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            strWithCharMatcher.reset(working);   // working will change as we merge strings
            if (strWithCharMatcher.find()) {     // Any (more) adjacent strings?
                foundAMatch = true;
                prefix = strWithCharMatcher.group(1);       // Up to (not including) first string
                bxId   = strWithCharMatcher.group(2);       // id of 1st bx tag
                rid    = strWithCharMatcher.group(3);       // rid of first bx and ex
                text1  = strWithCharMatcher.group(4);       // first text segment
                exId   = strWithCharMatcher.group(5);
                text2  = strWithCharMatcher.group(7);
                suffix = strWithCharMatcher.group(8);                
                
                working = prefix 
                    + "<bx id='" + bxId + "' rid='" + rid + "' ctype='x-mif-String'/>"
                    + text1 + text2
                    + "<ex id='" + exId + "' rid='" + rid + "'/>"
                    + suffix;
            }
            else {   // No (more) match(es)
                break;
            }
        }

        // Return what we came up with
                
        // If we have something to return, return it.
        if (foundAMatch) {
            return pfxText 
                + TuPreener.CORE_START_MRK
                + working
                + TuPreener.CORE_END_MRK
                + sfxText;
        }
        else {  // Nothing changed; just return what we received.
            return in;
        }
    }
    
}
