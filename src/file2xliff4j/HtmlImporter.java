/*
 * HtmlImporter.java
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
import org.apache.xerces.parsers.AbstractSAXParser;
import org.cyberneko.html.HTMLConfiguration;

import f2xutils.*;
import java.io.*;
import java.util.*;
import java.nio.charset.*;
import java.lang.*;
import java.util.regex.*;

/**
 * The HtmlImporter is used to import HTML to (what else?) XLIFF.
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class HtmlImporter extends AbstractSAXParser implements Converter {

    final static int HEAD_LINES = 1024;  // Number of lines to read for encoding detecting
    
    
    // Initial set of tags that can cause a <trans-unit> break. 
    private Set<String> tuBreakTags = new HashSet<String>(
            Arrays.asList(new String[] { 
            "address",
            "blockquote",
            "body",
            "br",
            "caption",  /* Added 3/5/7--ticket 878 */
            "center",
            "div",
            "dfn",
            "dd",
            "dt",
            "dl",
            "fieldset",
            "form",  /* Added 1/4/7--ticket 687 */
            "frameset",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "html",
            "input", /* Added 1/4/7--ticket 687 */
            "label", /* Added 3/27/7--ticket 898 */
            "li",
            "menu",
            "noframes",
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
            "tr",    /* Add 4/10/7--ticket 902 */
            "ul"
    }));
    
    /**
     * Constructor for the HTML importer. It calls its super
     * class, passing it a new HTMLConfiguration.
     */
    public HtmlImporter() {
         super(new HTMLConfiguration());  // A Neko Html "thing"
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
        return null;
    }

    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the HTML file type.
     */
    public FileType getFileType() {
        return FileType.HTML;
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
     * Convert an HTML file to XLIFF, creating xliff, skeleton and format files
     * as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter tells
     * the converter how to interpret the bytes read from the input file, so
     * that it can convert them to UTF-8 for XLIFF. (Note: The value of this
     * parameter is only a "suggestion." This converter will make an attempt
     * to check the input file for a meta tag that indicates the encoding. If
     * found, it will use that value rather than the value of this parameter.
     * @param nativeFileType The type of the native file. This value must be
     *        "HTML". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input HTML file (without directory
     * prefix). 
     * @param baseDir The directory that contains the input HTML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton</li>
     * <li><i>nativeFileName</i>.format</li>
     * </ul>
     * where <i>nativeFileName</i> is the file name specified in the nativeFileName
     * parameter.
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
            throw new ConversionException("HTML Importer supports only conversions"
                    + " from HTML to XLIFF.");
        }
        
        // Create output stream writers for the SAX handler to write to.
        OutputStreamWriter xliffOut = null;
        OutputStreamWriter tskeletonOut = null;
        OutputStreamWriter formatOut = null;
        
        if ((nativeFileType == null) || (! nativeFileType.equals(FileType.HTML))) {
            nativeFileType = FileType.HTML;
        }
        
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.xliffSuffix),
                    "UTF8");
            tskeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.tSkeletonSuffix),
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
            // Let's parse with the NekoHTML parser
            parser = XMLReaderFactory.createXMLReader("org.cyberneko.html.parsers.SAXParser");
            
            parser.setContentHandler(new HtmlHandler(tuBreakTags, xliffOut, 
                    tskeletonOut, formatOut, language, 
                    nativeFileType.toString(), nativeFileName, boundary));
            // Maintain the case (upper/lower) of tags (elements) and attributes 
            // found in the input document.
            parser.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
            parser.setProperty("http://cyberneko.org/html/properties/names/attrs", "match");
  
            // "In order to process HTML documents as XML, this feature should *not* be turned
            // off" (Note: "true" is the default value.)
            parser.setFeature("http://cyberneko.org/html/features/balance-tags", true);
            
            // Include namespaces in the StartElement() attlist:
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
            
            // Also include namespace-prefixes:
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
            
            // WWhipple 11/16/2006. When Neki parses XHTML documents in UTF8 that 
            // begin with a byte order mark and DOCTYPE, it fails to notice the
            // immediately following opening html and head tags, instead inserting
            // what it assumes to be missing parent tags. This should fix that
            // problem:
            //   "With this feature set, the tag balancer will not attempt to 
            //   insert a missing body elements around content and markup. 
            //   However, proper parents for elements contained within the 
            //   <body> element will still be inserted."
            parser.setFeature("http://cyberneko.org/html/features/balance-tags/document-fragment", true);
            
            // See if the HTML indicates its encoding.
            headerEncoding = guessEncoding(baseDir + File.separator 
                    + nativeFileName);
            
            Reader inReader = null;   // What the neko sax parser will read from
            
            if (headerEncoding != null) {   // Use what the input file says
                                            //   if available
                inReader = new InputStreamReader(new FileInputStream(
                    baseDir + File.separator + nativeFileName), headerEncoding);
            }
            else {                          // Otherwise use specified encoding
                inReader = new InputStreamReader(new FileInputStream(
                    baseDir + File.separator + nativeFileName), nativeEncoding);
            }
            
            InputSource htmlIn = new InputSource(inReader);
            parser.parse(htmlIn);
        }
        catch(SAXException e) {
            System.err.println("NekoHTML parser error.");
            System.err.println(e.getMessage());
            throw new ConversionException("NekoHTML parser error: " + e.getMessage());
        }
        catch(IOException e) {
            System.err.println("I/O error reading HTML input.");
            System.err.println(e.getMessage());
            throw new ConversionException("I/O error reading HTML input: " + e.getMessage());
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
                    + nativeFileName + Converter.tSkeletonSuffix);
            
            // We'll also read from the original input file
            FileInputStream nativeIn = new FileInputStream(baseDir + File.separator 
                    + nativeFileName);

            // We'll write to the (final) skeleton file
            FileOutputStream skeletonOut = new FileOutputStream(baseDir + File.separator 
                    + nativeFileName + Converter.skeletonSuffix);

            // The HtmlSkeletonMerger will do the deed.
            SkeletonMerger merger = new HtmlSkeletonMerger();

            if (merger != null) {
                // Before merging, pass the SkeletonMerger the list of TU break
                // tags
                merger.setProperty("http://www.lingotek.com/converters/properties/breaktags",
                        this.getTuDelimiterList());
                
                merger.merge(tSkeletonIn, nativeIn, skeletonOut, 
                    ((headerEncoding != null) ? headerEncoding : nativeEncoding));
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
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
                
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert an HTML file to XLIFF, creating xliff, skeleton and format files
     * as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter tells
     * the converter how to interpret the bytes read from the input file, so
     * that it can convert them to UTF-8 for XLIFF. (Note: The value of this
     * parameter is only a "suggestion." This converter will make an attempt
     * to check the input file for a meta tag that indicates the encoding. If
     * found, it will use that value rather than the value of this parameter.
     * @param nativeFileType The type of the native file. This value must be
     *        "HTML". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input HTML file (without directory
     * prefix). 
     * @param baseDir The directory that contains the input HTML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton</li>
     * <li><i>nativeFileName</i>.format</li>
     * </ul>
     * where <i>nativeFileName</i> is the file name specified in the nativeFileName
     * parameter.
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
            nativeFileType, nativeFileName, baseDir, notifier, 
            boundary, generatedFileName);
    }    
    
    /**
     * Convert an HTML file to XLIFF, creating xliff, skeleton and format files
     * as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file. 
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeEncoding The encoding of the input file. This parameter tells
     * the converter how to interpret the bytes read from the input file, so
     * that it can convert them to UTF-8 for XLIFF. (Note: The value of this
     * parameter is only a "suggestion." This converter will make an attempt
     * to check the input file for a meta tag that indicates the encoding. If
     * found, it will use that value rather than the value of this parameter.
     * @param nativeFileType The type of the native file. This value must be
     *        "HTML". (Note: The value is stored in the the datatype attribute of the 
     *        XLIFF's file element.)
     * @param nativeFileName The name of the input HTML file (without directory
     * prefix). 
     * @param baseDir The directory that contains the input HTML file--from which
     * we will read the input file. This is also the directory in which the output
     * xliff, skeleton and format files will be written. The output files will
     * be named as follows:
     * <ul>
     * <li><i>nativeFileName</i>.xliff</li>
     * <li><i>nativeFileName</i>.skeleton</li>
     * <li><i>nativeFileName</i>.format</li>
     * </ul>
     * where <i>nativeFileName</i> is the file name specified in the nativeFileName
     * parameter.
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
            nativeFileType, nativeFileName, baseDir, notifier, 
            SegmentBoundary.SENTENCE, null);
    }
        
    /**
     * Remove an HTML tag from the set of tags that signal the start of a 
     * <trans-unit> in the XLIFF generated from the input HTML.
     * @param tag HTML tag to remove from the set 
     * @return true=tag removed; false=tag wasn't present (so wasn't removed)
     */    
    public boolean removeTuDelimiter(String tag) {
        return tuBreakTags.remove(tag);
    }

    /**
     * Passed the name of an HTML file, look for a meta tag that indicates
     * what encoding the file uses. Return that encoding (or null) as a
     * Charset object.
     * @param htmlFileName The name of an HTML file
     * @return The encoding the file uses (or null if not apparent).
     * @throws file2xliff4j.ConversionException
     *         if an error is encountered.
     */
    public static Charset guessEncoding(String htmlFileName) 
            throws ConversionException {
        Charset encoding = null;      // Holds what we will return.
        String charsetStr = "";       // Holds what we read from meta tag
        
        // Pattern for comments
        String commentPatt = "<!--.*?-->";
        Pattern commentPattern = Pattern.compile(commentPatt, Pattern.DOTALL);
        Matcher m = commentPattern.matcher("");

        // Pattern for the content-type header we are interested in.
        String charsetPatt = "<meta\\s+http-equiv=['\"]?content-type['\"]?[^>]+?\\bcharset=([^'\"]+)";
        Pattern charsetPattern =  Pattern.compile(charsetPatt,Pattern.CASE_INSENSITIVE 
                | Pattern.DOTALL);
        Matcher n = charsetPattern.matcher("");
        
        BufferedReader htmlIn = null;
        try {
            htmlIn = new BufferedReader(new InputStreamReader(new FileInputStream(
                htmlFileName)));
            String line = "";
            StringBuilder head = new StringBuilder(); // Buffer to hold input lines

            // Read the first 1024 lines of the file.
            for (int i = 0; i < HEAD_LINES ; i++) {
                if ((line = htmlIn.readLine()) == null) {
                    break;
                };
                head.append(line + "\n");
            }
            
            String headStr = head.toString(); // Put it into a string

            // Delete all comments (in case a meta tag is commented out)
            m.reset(headStr);
            headStr = m.replaceAll("");
            
            // If we read the whole file (i.e. if we ran out of
            // input before 1024 lines), line will be null. If it isn't
            // null the last line read might be in the middle of a comment
            // that was missed in the above regex. Check for that condition.
            int openCommentPos = headStr.indexOf("<!--");
            if (openCommentPos != -1) {
                headStr = headStr.substring(0,openCommentPos);
            }
            
            // Now we have something to look for the charset within
            n.reset(headStr);
            if (n.find()) {
                charsetStr = n.group(1);
            }

            // We're done now.
            htmlIn.close();     // Close before leaving.
        }
        catch (IOException e) {
            System.err.println("Error reading XLIFF file: " + e.getMessage());
            throw new ConversionException("Error reading XLIFF file: " 
                    + e.getMessage());
        }

        if ((charsetStr != null) && (charsetStr.length() > 0)) {
            encoding = Charset.forName(charsetStr);
        }
        
        return encoding;   // If we found it; otherwise null
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
