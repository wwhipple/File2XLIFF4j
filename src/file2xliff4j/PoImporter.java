/*
 * PoImporter.java
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

import com.sun.star.awt.CharSet;
import f2xutils.*;
import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.util.regex.*;

// The next are for validating the XLIFF for well-formedness.
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Import a GNU Portable Object Template to XLIFF. 
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PoImporter implements Converter {
    

    private int curIndent = 0;
    
    private HashMap<Locale,Charset> langEncodingMap = new HashMap<Locale,Charset>();
    
    /**
     * Create a GNU Portable Object Template Importer 
     */
    public PoImporter() {
        
        // Initialize default non-UTF-8 encodings as listed in "GNU 'gettext'
        // utilities" (http://www.gnu.org/software/gettext/manual/gettext.html)
        // Afrikaans
        langEncodingMap.put(new Locale("af"), Charset.forName("ISO-8859-1"));
        // Albanian
        langEncodingMap.put(new Locale("sq"), Charset.forName("ISO-8859-1"));
        // Arabic
        langEncodingMap.put(new Locale("ar"), Charset.forName("ISO-8859-6"));
        // Basque
        langEncodingMap.put(new Locale("eu"), Charset.forName("ISO-8859-1"));
        // Bosnian
        langEncodingMap.put(new Locale("bs"), Charset.forName("ISO-8859-2"));
        // Breton
        langEncodingMap.put(new Locale("br"), Charset.forName("ISO-8859-1"));
        // Bulgarian
        langEncodingMap.put(new Locale("bg"), Charset.forName("CP1251"));
        // Byelorussian
        langEncodingMap.put(new Locale("be"), Charset.forName("CP1251"));
        // Catalan
        langEncodingMap.put(new Locale("ca"), Charset.forName("ISO-8859-1"));
        // Chinese traditional
        langEncodingMap.put(new Locale("zh", "TW"), Charset.forName("BIG5"));
        // Chinese simplified
        langEncodingMap.put(new Locale("zh", "CN"), Charset.forName("GB2312"));
        // Cornish
        langEncodingMap.put(new Locale("kw"), Charset.forName("ISO-8859-1"));
        // Croation
        langEncodingMap.put(new Locale("hr"), Charset.forName("ISO-8859-2"));
        // Czech
        langEncodingMap.put(new Locale("cs"), Charset.forName("ISO-8859-2"));
        // Danish
        langEncodingMap.put(new Locale("da"), Charset.forName("ISO-8859-1"));
        // Dutch
        langEncodingMap.put(new Locale("nl"), Charset.forName("ISO-8859-1"));
        // English
        langEncodingMap.put(new Locale("en"), Charset.forName("ISO-8859-1"));
        // Estonian
        langEncodingMap.put(new Locale("et"), Charset.forName("ISO-8859-1"));
        // Faroese
        langEncodingMap.put(new Locale("fo"), Charset.forName("ISO-8859-1"));
        // Finnish
        langEncodingMap.put(new Locale("fi"), Charset.forName("ISO-8859-1"));
        // French
        langEncodingMap.put(new Locale("fr"), Charset.forName("ISO-8859-1"));
        // Galician
        langEncodingMap.put(new Locale("gl"), Charset.forName("ISO-8859-1"));
//        // Georgian (Java doesn't know GEORGIAN-PS on my box).
//        langEncodingMap.put(new Locale("ka"), Charset.forName("GEORGIAN-PS"));
        // German
        langEncodingMap.put(new Locale("de"), Charset.forName("ISO-8859-1"));
        // Greek
        langEncodingMap.put(new Locale("el"), Charset.forName("ISO-8859-7"));
        // Greenlandic
        langEncodingMap.put(new Locale("kl"), Charset.forName("ISO-8859-1"));
        // Hebrew
        langEncodingMap.put(new Locale("he"), Charset.forName("ISO-8859-8"));
        // Hungarian
        langEncodingMap.put(new Locale("hu"), Charset.forName("ISO-8859-2"));
        // Icelandic
        langEncodingMap.put(new Locale("is"), Charset.forName("ISO-8859-1"));
        // Indonesian
        langEncodingMap.put(new Locale("id"), Charset.forName("ISO-8859-1"));
        // Irish
        langEncodingMap.put(new Locale("ga"), Charset.forName("ISO-8859-1"));
        // Italian
        langEncodingMap.put(new Locale("it"), Charset.forName("ISO-8859-1"));
        // Japanese
        langEncodingMap.put(new Locale("ja"), Charset.forName("EUC-JP"));
        // Korean
        langEncodingMap.put(new Locale("ko"), Charset.forName("EUC-KR"));
        // Latvian
        langEncodingMap.put(new Locale("lv"), Charset.forName("ISO-8859-13"));
        // Lithuanian
        langEncodingMap.put(new Locale("lt"), Charset.forName("ISO-8859-13"));
        // Macedonian
        langEncodingMap.put(new Locale("mk"), Charset.forName("ISO-8859-5"));
        // Malay
        langEncodingMap.put(new Locale("ms"), Charset.forName("ISO-8859-1"));
        // Maltese
        langEncodingMap.put(new Locale("mt"), Charset.forName("ISO-8859-3"));
        // Manx
        langEncodingMap.put(new Locale("gv"), Charset.forName("ISO-8859-1"));
        // Maori
        langEncodingMap.put(new Locale("mi"), Charset.forName("ISO-8859-13"));
        // Norwegian Nynorsk
        langEncodingMap.put(new Locale("nn"), Charset.forName("ISO-8859-1"));
        // Norwegian Bokmal
        langEncodingMap.put(new Locale("nb"), Charset.forName("ISO-8859-1"));
        // Occitan
        langEncodingMap.put(new Locale("oc"), Charset.forName("ISO-8859-1"));
        // Polish
        langEncodingMap.put(new Locale("pl"), Charset.forName("ISO-8859-2"));
        // Portuguese
        langEncodingMap.put(new Locale("pt"), Charset.forName("ISO-8859-1"));
        // Romanian
        langEncodingMap.put(new Locale("ro"), Charset.forName("ISO-8859-2"));
        // Russian
        langEncodingMap.put(new Locale("ru"), Charset.forName("KOI8-R"));
        // Serbian
        langEncodingMap.put(new Locale("sr"), Charset.forName("ISO-8859-5"));
        // Slovak
        langEncodingMap.put(new Locale("sk"), Charset.forName("ISO-8859-2"));
        // Slovenian
        langEncodingMap.put(new Locale("sl"), Charset.forName("ISO-8859-2"));
        // Spanish
        langEncodingMap.put(new Locale("es"), Charset.forName("ISO-8859-1"));
        // Swedish
        langEncodingMap.put(new Locale("sv"), Charset.forName("ISO-8859-1"));
        // Tagalog
        langEncodingMap.put(new Locale("tl"), Charset.forName("ISO-8859-1"));
//        // Tajik  (Java doesn't know KOI8-T on my box.)
//        langEncodingMap.put(new Locale("tg"), Charset.forName("KOI8-T"));
        // Thai
        langEncodingMap.put(new Locale("th"), Charset.forName("TIS-620"));
        // Turkish
        langEncodingMap.put(new Locale("tr"), Charset.forName("ISO-8859-9"));
//        // Ukrainian (Java doesn't know KOI8-U on my box.)
//        langEncodingMap.put(new Locale("uk"), Charset.forName("KOI8-U"));
        // Uzbek
        langEncodingMap.put(new Locale("uz"), Charset.forName("ISO-8859-1"));
        // Walloon
        langEncodingMap.put(new Locale("wa"), Charset.forName("ISO-8859-1"));
//        // Welsh (Java doesn't know ISO-8859-14 on my box.)
//        langEncodingMap.put(new Locale("cy"), Charset.forName("ISO-8859-14"));
        
    }

    // Matcher for msgid and msgstr lines
    private Matcher msgMatcher 
        = Pattern.compile("^(msgid|msgstr)\\s+\"(.*)\"\\s*").matcher("");
    
    // Matcher for continuation of msgid or msgstr
    private Matcher continuationMatcher
        = Pattern.compile("^\"(.*)\"\\s*").matcher("");
    
//    // Matcher for PO comments
//    private Matcher commentMatcher
//        = Pattern.compile("^(#[.:,|]?)\\s+(.*)$").matcher("");

    /**
     * Convert a GNU Portable Object Template to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the original messages (that are to be
     *        translated).
     * @param nativeEncoding The encoding of the PO template. If this parameter is
     *        null, the importer will read the file's Content-Type header, if 
     *        present. If not present in the file (or if unchanged from the 
     *        default "CHARSET"), use the default encoding for the language, as
     *        specified in the GNU gettext Utilities document.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be PO.)
     * @param nativeFileName The name of the Portable Object Template file.
     * @param baseDir The directory that contains the input PO template--from 
     *        which we will read the input file. This is also the directory in 
     *        which the output xliff and skeleton files are written. The output 
     *        files will be named as follows:
     * <ul>
     * <li><i>&lt;nativeFileName&gt;</i>.xliff</li>
     * <li><i>&lt;nativeFileName&gt;</i>.skeleton</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries). This value is ignored. (Each
     *        string becomes one segment in the XLIFF.)
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
            throw new ConversionException("PO Template Importer supports only conversions"
                    + " of GNU Portable Object Templates to XLIFF.");
        }

        // Make sure the name of the PO template file was specified.
        if (nativeFileName == null || nativeFileName.trim().length() == 0) {
            System.err.println("File name of input GNU Portable Object Template omitted. Cannot proceed.");
            throw new ConversionException("File name of input GNU Portable Object"
                    + " Template omitted. Cannot proceed.");
        }

        // Construct the names of the files we will be working with.
        String inPot = baseDir + File.separator + nativeFileName;
        String outXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String outSkel  = baseDir + File.separator + nativeFileName 
                + Converter.skeletonSuffix;
        
        File potFile = new File(inPot);
        if (!potFile.exists()) {
            System.err.println("Input GNU Portable Object Template does not exist.");
            throw new ConversionException("Input GNU Portable Object Template does not exist.");
        }

        // Determine encoding used in PO template:
        Charset potEncoding = readPoEncoding(baseDir + File.separator 
            + nativeFileName);

        // If unable to find encoding, use the one listed in the gettext manual.
        // (At some point--maybe now?--we will probably want to default to UTF-8,
        // since that is the way the world is leaning.)
        if (potEncoding == null) {
            String langStr = language.getLanguage();
            Locale encodingLang = null;
            if (langStr.equalsIgnoreCase("zh")) {  // For Chinese, differentiate between countries
                encodingLang = language;
            }
            else {
                // For other languages, we care only about the 2-letter language code.
                encodingLang = new Locale(langStr);
            }
            
            potEncoding = this.langEncodingMap.get(encodingLang);
        }
        
        if (potEncoding == null) { // still!
            // Just use good ole' UTF-8
            potEncoding = Charset.forName("UTF-8");
        }
        
        // Output the XLIFF prolog
        try {
            // Create input reader and output writers
            BufferedReader poRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(inPot), potEncoding));
            
            BufferedWriter xliffWtr  = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outXliff), Charset.forName("UTF-8")));

            // Let's write the skeleton in UTF-8 (I s'pose) ... because there's
            // no telling what languages the translation will end up in ...)
            BufferedWriter skelWtr  = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outSkel), Charset.forName("UTF-8")));
            
            // Write the "prolog" of the XLIFF file
            xliffWtr.write(Converter.xmlDeclaration);
            xliffWtr.write(Converter.startXliff);
            xliffWtr.write(indent() + "<file original='" 
                + nativeFileName.replace("&", "&amp;").replace("<",
                    "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                + "' source-language='" + language.toString() + "' datatype='po'>\r\n");
            xliffWtr.write(indent() + "<header lt:segtype='sentence'");
            xliffWtr.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
            xliffWtr.flush();
        
            String poLine = null;             // Holds the next line read from PO template
            String source = "";               // Source language text accumulated so far
            String prevLineType = "";         // What kind of line was *previous* line?
            UUID curTuID = UUID.randomUUID(); // ID for the next TU
            
            // Read through the PO Template line by line
            while ((poLine = poRdr.readLine()) != null) {

                // Is this a msgid (source) or msgstr (target) line?
                if (msgMatcher.reset(poLine).find()) {
                    String msgType = msgMatcher.group(1);
                    String content = msgMatcher.group(2);
                    prevLineType = msgType;  // For next time around--in case of
                                             // multiline strings.
                    if (msgType.equalsIgnoreCase("msgid")) {  // The "source"
                        source = content;
                        // Echo the message in the original language out to
                        // the skeleton (since we won't change it).
                        skelWtr.write(poLine + "\n");
                    }
                    else if (msgType.equals("msgstr")) {   
                        // A target--one of two conditions could apply:
                        // 1. This is the first msgstr in the file, and the
                        //    immediately preceding msgid had no contents.
                        //    (If this is the case, we will just echo the
                        //    contents of the line to the skeleton.)
                        //                    OR
                        // 2. The input file is a PO file (rather than a
                        //    POT--template), and contains a translation
                        //    already. (If this is the case, we will omit
                        //    the line from the skeleton, substituting in
                        //    its place a msgstr whose value is a TU placeholder.

                        if (source.trim().length() == 0) {
                            // This is part of the header; echo it to skeleton:
                            skelWtr.write(poLine + "\n");
                        }
                        else {
                            // It must be preceded by a real msgid; write a
                            // placeholder to the skeleton.
                            skelWtr.write("msgstr \"<lTLt:tu id='" 
                                + curTuID.toString() + "'/>\"\n");
                        }
                    }
                    skelWtr.flush();
                }

                // ... or is it a continuation line (or a source/target?)
                else if (continuationMatcher.reset(poLine).find()) {
                    String continuation = continuationMatcher.group(1);
                    if (prevLineType.equalsIgnoreCase("msgid")) {
                        skelWtr.write(poLine + "\n");
                        source += continuation;
                    }
                    else if (prevLineType.equalsIgnoreCase("msgstr")) {
                        // If the current msgid is of zero length, then
                        // this is probably another line of a header msgstr,
                        // which we should echo to the skeleton
                        if (source.trim().length() == 0) {
                            skelWtr.write(poLine + "\n");
                        }
                        else {
                            ; // Otherwise, we already wrote a placeholder above.
                        }
                    }
                }

                else if (poLine.trim().length() == 0) {
                    // If the source variable has meaningful characters, then
                    // it is time to output another translation unit
                    if (source.trim().length() > 0) {
                        xliffWtr.write(indent('0')
                            + "<trans-unit id='" + curTuID + "' "
                            + "lt:paraID='" + curTuID + "'>\r\n");
                        // Open the source element
                        String markedTu 
                            = TuPreener.markCoreTu(TuStrings.escapeTuString(source).replace("\\n","<x id='1' ctype='lb'/>"));
                        xliffWtr.write(indent('+') + "<source xml:lang='" 
                            + language.toString() + "'>" 
                            + TuPreener.getPrefixText(markedTu)
                            + "<mrk mtype='x-coretext'>"
                            + TuPreener.getCoreText(markedTu)
                            + "</mrk>" + TuPreener.getSuffixText(markedTu) 
                            + "</source>\r\n");
                        // ... and the trans-unit end tag
                        xliffWtr.write(indent('-') + "</trans-unit>\r\n");
                        xliffWtr.flush();

                        source = "";    // Clear out source for next time
                        curTuID = UUID.randomUUID();   // Get a new TU ID for next time
                    }
                    // Echo the blank line to the skeleton
                    skelWtr.write(poLine + "\n");
                    prevLineType = "";
                }
                else {  // A comment or some other type of line.
                    // Just print it to the skeleton:
                    skelWtr.write(poLine + "\n");
                    prevLineType = "";
                }
            }   // while
        
            // If the last line in the file isn't a blank line, then we haven't
            // yet written out the final trans-unit. Check to see if the variable
            // named "source" is of length greater than zero. If it is, we need
            // to write out one final TU
            if (source.trim().length() > 0) {
                xliffWtr.write(indent('0')
                    + "<trans-unit id='" + curTuID + "' "
                    + "lt:paraID='" + curTuID + "'>\r\n");
                // Open the source element
                String markedTu 
                    = TuPreener.markCoreTu(TuStrings.escapeTuString(source).replace("\\n","<x id='1' ctype='lb'/>"));
                xliffWtr.write(indent('+') + "<source xml:lang='" 
                    + language.toString() + "'>" 
                    + TuPreener.getPrefixText(markedTu)
                    + "<mrk mtype='x-coretext'>"
                    + TuPreener.getCoreText(markedTu)
                    + "</mrk>" + TuPreener.getSuffixText(markedTu) 
                    + "</source>\r\n");
                // ... and the trans-unit end tag
                xliffWtr.write(indent('-') + "</trans-unit>\r\n");
                xliffWtr.flush();

                source = "";    // Clear out source for next time
            }
            
            
            // Then finish off the XLIFF file
            xliffWtr.write(indent('0') + "</body>\r\n"); // Close the body element
            xliffWtr.write(indent('-') + "</file>\r\n"); // Close the file element
            xliffWtr.write("</xliff>\r\n");               // Close the xliff element
            xliffWtr.flush();             // Well-bred writers flush when finished
            
            /* Close the files we created above */
            xliffWtr.close();
            skelWtr.close();
        }
        catch(java.io.IOException e) {
            System.err.println("Error generating XLIFF and/or skeleton file: "
                    + e.getMessage());
        }
        
        // Before returning, see if the notifier is non-null. If it is, make sure
        // a skeleton exists and verify that the XLIFF is well-formed XML.
        if (notifier != null) {
            String notice = "";
            File skelFile = new File(outSkel);
            if (!skelFile.exists()) {
                notice = "Portable Object Template importer didn't create a skeleton file named " + outSkel;
                System.err.println(notice);
                notifier.sendNotification("0001", "PoImporter", Notifier.ERROR, notice);
            }
            
            // Does the output XLIFF file exist?
            notice = "";
            File xliffFile = new File(outXliff);
            if (!xliffFile.exists()) {
                notice = "Portable Object Template importer didn't create an XLIFF file named " + outXliff;
                System.err.println(notice);
                notifier.sendNotification("0002", "PoImporter", Notifier.ERROR, notice);
            }
            else {
                // The XLIFF exists. Is it well-formed?
                try {
                    XMLReader parser = XMLReaderFactory.createXMLReader();

                    // We don't care about namespaces at the moment.
                    parser.setFeature("http://xml.org/sax/features/namespaces", false);

                    Reader inReader = new InputStreamReader(new FileInputStream(xliffFile), 
                            Charset.forName("UTF-8"));
                    InputSource xliffIn = new InputSource(inReader);
                    if (xliffIn != null) {
                        parser.parse(xliffIn); 
                        inReader.close();
                    }
                    else {
                        notice = "Unable to read generated XLIFF file " 
                                + baseDir + File.separator
                                + nativeFileName + Converter.xliffSuffix;
                        System.err.println(notice);
                        notifier.sendNotification("0003", "PoImporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "PoImporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "PoImporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    notice = "The validator of XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0006", "PoImporter", Notifier.ERROR, notice);
                }
            }
        }        

        
        if (generatedFileName != null) {
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert a GNU Portable Object Template to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the original messages (that are to be
     *        translated).
     * @param nativeEncoding The encoding of the PO template. If this parameter is
     *        null, the importer will read the file's Content-Type header, if 
     *        present. If not present in the file (or if unchanged from the 
     *        default "CHARSET"), use the default encoding for the language, as
     *        specified in the GNU gettext Utilities document.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be PO.)
     * @param nativeFileName The name of the Portable Object Template file.
     * @param baseDir The directory that contains the input PO template--from 
     *        which we will read the input file. This is also the directory in 
     *        which the output xliff and skeleton files are written. The output 
     *        files will be named as follows:
     * <ul>
     * <li><i>&lt;nativeFileName&gt;</i>.xliff</li>
     * <li><i>&lt;nativeFileName&gt;</i>.skeleton</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries). This value is ignored. (Each
     *        string becomes one segment in the XLIFF.)
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
     * Convert a GNU Portable Object Template to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the original messages (that are to be
     *        translated).
     * @param nativeEncoding The encoding of the PO template. If this parameter is
     *        null, the importer will read the file's Content-Type header, if 
     *        present. If not present in the file (or if unchanged from the 
     *        default "CHARSET"), use the default encoding for the language, as
     *        specified in the GNU gettext Utilities document.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be PO.)
     * @param nativeFileName The name of the Portable Object Template file.
     * @param baseDir The directory that contains the input PO template file--from 
     *        which we will read the input file. This is also the directory in 
     *        which the output xliff and skeleton files are written. The output 
     *        files will be named as follows:
     * <ul>
     * <li><i>&lt;nativeFileName&gt;</i>.xliff</li>
     * <li><i>&lt;nativeFileName&gt;</i>.skeleton</li>
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
     * Return an object representing a format-specific (and converter-specific) 
     * property.
     * @param property The name of the property to return.
     * @return An Object that represents the property's value.
     */
    public Object getConversionProperty(String property) {
        return null;
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

    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the GNU Portable Object (Template) type.
     */
    public FileType getFileType() {
        return FileType.PO;
    }

    /**
     * Return an indentation string of blanks, increasing the indentation by 1 space
     * @return A string of spaces corresponding to the current indentation level
     */
    private String indent() {
        return indent('+');
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
     * Passed the name of a GNU Portable Object Template, try to determine its
     * encoding. Emacs creates the following PO boilerplate when invoked with
     * a non-existent .po file as an argument:
     * <tt><pre>
     * # SOME DESCRIPTIVE TITLE.
     * # Copyright (C) YEAR Free Software Foundation, Inc.
     * # FIRST AUTHOR <EMAIL@ADDRESS>, YEAR.
     * #
     * #, fuzzy
     * msgid ""
     * msgstr ""
     * "Project-Id-Version: PACKAGE VERSION\n"
     * "PO-Revision-Date: YEAR-MO-DA HO:MI +ZONE\n"
     * "Last-Translator: FULL NAME <EMAIL@ADDRESS>\n"
     * "Language-Team: LANGUAGE <LL@li.org>\n"
     * "MIME-Version: 1.0\n"
     * "Content-Type: text/plain; <b>charset=CHARSET</b>\n"
     * "Content-Transfer-Encoding: 8bit\n"
     * </tt></pre>
     * This method will (accordingly), look for the first msgstr value (which
     * includes all the immediately following quoted strings, concatenated
     * together) and look for a charset indication.
     * @param poFileName The name of a POT file--fully qualified
     * @return The encoding the file uses (or null if not apparent).
     * @throws file2xliff4j.ConversionException
     *         if an error is encountered.
     */
    public static Charset readPoEncoding(String poFileName) 
            throws ConversionException {
        Charset encoding = null;      // Holds what we will return.
        
        // Pattern for the content-type header we are interested in.
        Matcher charsetMatcher = Pattern.compile("Content-Type:\\s+text/plain;\\s+charset=([-_a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL ).matcher("");
        
        BufferedReader poIn = null;
        try {
            poIn = new BufferedReader(new InputStreamReader(new FileInputStream(
                poFileName)));
            String line = "";
            StringBuilder head = new StringBuilder(); // Buffer to hold input lines

            // Read until we find the first line that begins "msgstr "
            while ((line = poIn.readLine()) != null) {
                if (line.startsWith("msgstr ")) {
                    break;    // We found the first msgstr line
                }
            }

            // If we didn't find the first msgstr line, there must be no
            // charset indication:
            if (line == null) {
                return encoding;    // null
            }

            // See if character set is specified in the initial msgstr string:
            charsetMatcher.reset(line);
            if (charsetMatcher.find()) {
                // If it is, return it to the caller.
                String charsetStr = charsetMatcher.group(1);
                encoding = Charset.forName(charsetStr);
                return encoding;
            }
            
            // Otherwise, keep looking through immediately following lines
            // consisting of quoted strings to see if the character set is found.
            boolean endOfCat = false;    // Not found end of string concatenation ... yet
            while (!endOfCat) {
                line = poIn.readLine();
                if (line == null || line.trim().length() == 0  // Empty line
                    || line.indexOf("\"") == -1) {            // Or no quote mark
                    // No more input
                    return encoding;    // null
                }
                else if (charsetMatcher.reset(line).find()) {
                    String charsetStr = charsetMatcher.group(1);
                    if (!charsetStr.equalsIgnoreCase("CHARSET")) {
                        encoding = Charset.forName(charsetStr);
                        return encoding;
                    }
                    else {  // Default charset name was left unchanged
                        return null;   // We don't know the encoding
                    }
                }
                // Else keep reading ...
            }
        }
        // If anything goes wrong, return a null (unknown encoding).
        catch (Exception e) {
            return null;
        }

        // What?!! (Still here?). Return null encoding
        return null;   
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
