/*
 * HtmlExporter.java
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
import java.util.regex.*;

/**
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class HtmlExporter implements Converter {

    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();

    private Format format;          // Maps bx/ex etc. to original format characters.
    
    private BufferedWriter outWriter; // To export translation to.
    
    /** Creates a new instance of HtmlExporter */
    public HtmlExporter() { }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original HTML format, using the skeleton and format files that
     * were generated when the XLIFF file was created. 
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     *        tells the converter what to convert the UTF-8 encoding back to.
     *        (Note: The choice of encoding may depend on the target language.
     *        Encodings like Shift_JIS and EUCJP, for example, are appropriate
     *        for Japanese targets only. UTF-8 can be used for all target
     *        languages.)
     * @param nativeFileType This parameter is ignored. (In conversions from XLIFF,
     *        the native file type stored in the XLIFF--specified when the XLIFF 
     *        was created--is used.)
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output HTML formatted file:
     *        &lt;nativeFileName&gt;.&lt;language&gt;.html (if the original file's
     *        "extension" wasn't ".html", the original extension is used instead.)
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
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
        
    // Matcher for the Content-Type meta tag 
    private Matcher charsetMatcher 
        = Pattern.compile("^(.*<meta\\s+http-equiv=(['\"])content-type\\2 [^>]+?\\bcharset=)([^'\"]+)(.*)$",
            Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original HTML format, using the skeleton and format files that
     * were generated when the XLIFF file was created. 
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     *        tells the converter what to convert the UTF-8 encoding back to.
     *        (Note: The choice of encoding may depend on the target language.
     *        Encodings like Shift_JIS and EUCJP, for example, are appropriate
     *        for Japanese targets only. UTF-8 can be used for all target
     *        languages.)
     * @param nativeFileType This parameter is ignored. (In conversions from XLIFF,
     *        the native file type stored in the XLIFF--specified when the XLIFF 
     *        was created--is used.)
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output HTML formatted file:
     *        &lt;nativeFileName&gt;.&lt;language&gt;.html (if the original file's
     *        "extension" wasn't ".html", the original extension is used instead.)
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
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

        if ((language == null) || (nativeEncoding == null)
                || (nativeFileName == null)
                || (nativeFileName.length() == 0)
                || (baseDir == null)
                || (baseDir.length() == 0)
                || (! mode.equals(ConversionMode.FROM_XLIFF))) {
            throw new ConversionException("Required parameter(s)" 
                    + " omitted, incomplete or incorrect.");
        }
        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;
        String inFormat = baseDir + File.separator + nativeFileName
                + Converter.formatSuffix;
        
        // Assume that the nativeFileName ends with a period and
        // extension (something like .html ?). If it does, insert the
        // language before that final dot.
        String outHtmlNameOnly = nativeFileName;
        String outHtml;

        int lastDot = outHtmlNameOnly.lastIndexOf(".");
        if (lastDot == -1) {  // Unusual, but no dot!
            outHtml = baseDir + File.separator + outHtmlNameOnly + "."
                + language.toString();
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outHtmlNameOnly + "." + language.toString());
            }
        }
        else {
            outHtml = baseDir + File.separator 
                + outHtmlNameOnly.substring(0,lastDot)
                + "." + language.toString() + "."
                + outHtmlNameOnly.substring(lastDot+1);
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outHtmlNameOnly.substring(0,lastDot)
                    + "." + language.toString() + "." + outHtmlNameOnly.substring(lastDot+1));
            }
        }
        
        // We created an empty map of TU strings when this class was loaded.
        // Make sure that actually happened.
        if (tuMap == null) {
            throw new ConversionException("Unable to get  target strings"
                + " from file " + inXliff);
        }
        
        // Now load that empty map with the target strings for the language
        // we are exporting.
//        tuMap.loadStrings(inXliff, language, phaseName, maxPhase, true);
        // 4/17/2007 WLW: Don't convert ampersands to entities.
        tuMap.loadStrings(inXliff, language, phaseName, maxPhase, false);

        //////////////////////////////////////////////////////////////////
        // Get readers/writers on the in/out files and necessary objects
        //////////////////////////////////////////////////////////////////

        // Skeleton (UTF-8, of course!)
        BufferedReader inSkel = null;
        try {
            inSkel =  new BufferedReader(new InputStreamReader(new FileInputStream(inSkeleton),
                "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {  // What!!?? Can't read UTF-8?
            System.err.println("Cannot decode UTF-8 skeleton file: " 
                    + e.getMessage());
            throw new ConversionException("Cannot decode UTF-8 skeleton file: " 
                    + e.getMessage());
        }
        catch (FileNotFoundException e) {
            System.err.println("Cannot find the skeleton file: ");
            System.err.println(e.getMessage());
            throw new ConversionException("Cannot find the skeleton file: "
                    + e.getMessage());
        }
        
        // Format (to resolve bx/ex tags (etc.))
        try {
            format = new Format(inFormat);
        }
        catch (IOException e) {
            System.err.println("Cannot access the format file: " + e.getMessage());
            throw new ConversionException("Cannot access the format file "
                    + inFormat );
        }
        
        // HTML output (in correct encoding)
        try {
            outWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outHtml), nativeEncoding));
        }
        catch (FileNotFoundException e) {
            System.err.println("Cannot access the output HTML file.");
            System.err.println(e.getMessage());
            throw new ConversionException("Cannot access the output HTML file "
                    + outHtml );
        }

        /*********************************
         * E X P O R T   T H E   H T M L *
         *********************************/
        boolean done = false;               // Not done yet
        
        try {
            while (! done) {
                String skelLine = inSkel.readLine();
                if (skelLine == null) {
                    done = true;
                    break;
                }
            
                // Make sure the Content-Type header (if present) matches the specified
                // output encoding.
                if (skelLine.toLowerCase().indexOf("<meta http-equiv") != -1) {
                    charsetMatcher.reset(skelLine);
                    if (charsetMatcher.find()) {
                        skelLine = charsetMatcher.group(1) + nativeEncoding.name()
                            + charsetMatcher.group(4);
                    }
                }
                
                // If line has tu place-holder, substitute the TU in its place
                if (skelLine.indexOf("<lt:tu id") != -1) {
                    expandTus(skelLine); // This will write the output to outWriter
                }
                else {
                    // Otherwise--if no placeholders--we will write to the output
                    outWriter.write(skelLine + "\n");
                }
                // Flush and close before leaving
                outWriter.flush();
            }

            outWriter.close();
        }
        catch (IOException e) {
            System.err.println("Cannot read skeleton file");
            throw new ConversionException("Cannot read skeleton file "
                    + inSkeleton );
        }

        return ConversionStatus.CONVERSION_SUCCEEDED;
    }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) back
     * to the original HTML format, using the skeleton and format files that
     * were generated when the XLIFF file was created. 
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the XLIFF targets to use in constructing
     *        the native-format document.
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
     *        tells the converter what to convert the UTF-8 encoding back to.
     *        (Note: The choice of encoding may depend on the target language.
     *        Encodings like Shift_JIS and EUCJP, for example, are appropriate
     *        for Japanese targets only. UTF-8 can be used for all target
     *        languages.)
     * @param nativeFileType This parameter is ignored. (In conversions from XLIFF,
     *        the native file type stored in the XLIFF--specified when the XLIFF 
     *        was created--is used.)
     * @param nativeFileName The name of the original document that was originally
     *        converted to XLIFF. It is used to determine the name of the XLIFF
     *        file (&lt;nativeFileName&gt;.xliff), the skeleton file
     *        (&lt;nativeFileName&gt;.skeleton) and the format file
     *        (&lt;nativeFileName&gt;.format). It is also used in constructing
     *        the file name of the output HTML formatted file:
     *        &lt;nativeFileName&gt;.&lt;language&gt;.html (if the original file's
     *        "extension" wasn't ".html", the original extension is used instead.)
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which output file 
     *        will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
     * @param skipList (Not used by this converter.)
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

    // Regex matcher for the TU placeholders in the skeleton file.
    private Matcher placeHolderMatcher = Pattern.compile("<lt:tu (id=|ids=)(['\"])(.+?)\\2/>",
            Pattern.DOTALL).matcher("");

    // Matcher for multiple TU/format ids in a placeholder.
    private Matcher mMult = Pattern.compile("^\\s*(tu|format):([-\\w]+)(.*)$").matcher("");
    
    /**
     * Passed a line from the skeleton file, expand all &lt;lt:tu id[s]='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Write the results to the output writer.
     * @param skelLine A line from the skeleton file
     */
    private void expandTus(String skelLine) {
        
        placeHolderMatcher.reset(skelLine);
        // The next variables hold the start and end of the subsequence of non
        // placeholder characters just before the current placeholder.
        int copyFrom = 0;   // Where in the main string to copy non-placeholder chars from.
                            //   (i.e., where the previous placeholder ended) 
        int copyTo = 0;     // Where the current placeholder starts.
        int nextCopyFrom;   // Where the current placeholder ends. (We will copy
                            //   from here next time.)
        
        // Find each TU and extract its TU Identifier
        while (placeHolderMatcher.find()) {

            // The tu ID(s) retrieved in the next statement will be either
            // a single UUID (representing a single TU) or multiple blank-
            // separated IDs prefixed with "tu:" or "format:". (Those 
            // prefixed with "tu:" are UUID's; those prefixed with "Format:"
            // are integers.
            copyTo = placeHolderMatcher.start();     // Where this placeholder starts
            nextCopyFrom = placeHolderMatcher.end(); // Where this placeholder ends (+1)
            String placeHolder = placeHolderMatcher.group(0);  // The whole tag
            String tuID = placeHolderMatcher.group(3);  // Get the identifier(s) from the tag in skel
            boolean isSingleton = placeHolderMatcher.group(1).equals("ids=") ? false : true;

            String tuText = "";
            if (isSingleton) {
                tuText = tuMap.getTu(tuID,"html");  // Get the Tu Text.
            }
            else {
                // Multiple TU/format references
                String tail = tuID; // All the TU/format IDs
                boolean done = false;
                while (! done) {
                    mMult.reset(tail);
                    if (mMult.find()) {
                        String tuOrFormat = mMult.group(1);
                        String id = mMult.group(2);
                        tail = mMult.group(3);
                        if (tuOrFormat.equals("tu")) {
                            tuText += tuMap.getTu(id, "html");
                        }
                        else {  // Text from the format file
                            tuText += format.getReplacement(id);
                        }
                    }
                    else {
                        done = true;
                    }
                }
            }

            tuText = fixTuQuotes(tuText);  // ' -> &apos; ... " -> &quot;

            // Remove core mrks from the TU text
            tuText = TuPreener.removeCoreMarks(tuText);

            // Expand bx, ex, x, ...
            tuText = resolveFormatCodes(tuText);

            // Output the text between the previous placeholder and this one.
            try {
                outWriter.write(skelLine, copyFrom, copyTo-copyFrom);
            
                // Then output whatever the placeholder expanded to.
                outWriter.write(tuText);
            }
            catch(IOException e) {
                System.err.println("Caught exception writing expansion.");
            }
            
            // Then get where (in skelLine) this placeholder ended, so we can
            // write from that place next time.
            copyFrom = nextCopyFrom;
            
        }
        
        // We're done; append the text after the last placeholder in the skeleton
        try {
            outWriter.write(skelLine, copyFrom, skelLine.length()-copyFrom);
            outWriter.write("\n");
        }
        catch(IOException e) {
            System.err.println("Caught exception writing expansion.");
        }
    }

    private Matcher formatPlaceHolderMatcher = Pattern.compile("<lt:tu (id=|ids=)(['\"])(.+?)\\2/>",
            Pattern.DOTALL).matcher("");

    private Matcher mfmtMult = Pattern.compile("^\\s*(tu|format):([-\\w]+)(.*)$").matcher("");
    
    /**
     * Passed a line from the format file, expand all &lt;lt:tu id[s]='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Return the expanded string.
     * @param fmtLine A line from the format file
     * @return The same line, with TU placeholders expanded with the text of
     *         the TU in the appropriate language.
     */
    private String expandTusInFormat(String fmtLine) {

        StringBuilder fmtBuffer = new StringBuilder();

        formatPlaceHolderMatcher.reset(fmtLine);
        // The next variables hold the start and end of the subsequence of non
        // placeholder characters just before the current placeholder.
        int copyFrom = 0;   // Where in the main string to copy non-placeholder chars from.
                            //   (i.e., where the previous placeholder ended) 
        int copyTo = 0;     // Where the current placeholder starts.
        int nextCopyFrom;   // Where the current placeholder ends. (We will copy
                            //   from here next time.)
        
        // Find each TU and extract its TU Identifier
        while (formatPlaceHolderMatcher.find()) {
            copyTo = formatPlaceHolderMatcher.start();     // Where this placeholder starts
            nextCopyFrom = formatPlaceHolderMatcher.end(); // Where this placeholder ends (+1)

            // The tu ID(s) retrieved in the next statement will be either
            // a single UUID (representing a single TU) or multiple blank-
            // separated IDs prefixed with "tu:" or "format:". (Those 
            // prefixed with "tu:" are UUID's; those prefixed with "Format:"
            // are integers.
            String placeHolder = formatPlaceHolderMatcher.group(0);  // The whole tag
            String tuID = formatPlaceHolderMatcher.group(3);  // Get the identifier(s) from the tag in skel
            boolean isSingleton = formatPlaceHolderMatcher.group(1).equals("ids=") ? false : true;

            String tuText = "";
            if (isSingleton) {
                tuText = tuMap.getTu(tuID,"html");  // Get the Tu Text.
            }
            else {
                // Multiple TU/format references
                String tail = tuID; // All the TU/format IDs
                boolean done = false;
                while (! done) {
                    mfmtMult.reset(tail);
                    if (mfmtMult.find()) {
                        String tuOrFormat = mfmtMult.group(1);
                        String id = mfmtMult.group(2);
                        tail = mfmtMult.group(3);
                        if (tuOrFormat.equals("tu")) {
                            tuText += tuMap.getTu(id, "html");
                        }
                        else {  // Text from the format file
                            tuText += format.getReplacement(id);
                        }
                    }
                    else {
                        done = true;
                    }
                }
            }

            tuText = fixTuQuotes(tuText);  // ' -> &apos; ... " -> &quot;

            tuText = TuPreener.removeCoreMarks(tuText);

            // Expand bx, ex, x, ...
            tuText = resolveFormatCodes(tuText);

            // Now replace the Skeleton's TU tag with the (expanded) TU text
            // Copy the text between the previous placeholder and this one.
            fmtBuffer.append(fmtLine, copyFrom, copyTo);
            
            // Then copy what the placeholder expanded to.
            fmtBuffer.append(tuText);
            // Then get where (in skelLine) this placeholder ended, so we can
            // copy from that place next time.
            copyFrom = nextCopyFrom;
            
        }
        
        // We're done; append the text after the last placeholder in the skeleton
        fmtBuffer.append(fmtLine, copyFrom, fmtLine.length());
        
        return fmtBuffer.toString();
    }
    
    /**
     * Passed the text of a TU target or source (without the enclosing
     * source or target tags, but still including the mrk coretext tags), 
     * replace apostrophes and quotes that aren't part of tags with the
     * apos and quot entities.
     * @param tuText Text with possible quotes to fix
     * @return The fixed text (with single/double quotes changed to entities)
     */
    private String fixTuQuotes(String tuText) {
        // If no " or ', can't be anything to do.
        if (tuText == null) { return tuText; }
        if (!(tuText.contains("'") || tuText.contains("\"")
            || tuText.contains("&apos;"))) {
            return tuText;
        }
        
        // The first text we care about is preceded by (at least) a
        // <mrk mtype='x-coretext'> (formerly <lt:core>)
        // tag, so we can start with the the first tag (lt:core or earlier).
        // The middle set of capturing parens is what we care about.
        String nonTags = "^(.*?<[^>]*>)([^<]*)(<.*)";
        Pattern ntp = Pattern.compile(nonTags,Pattern.DOTALL);
        Matcher ntm = ntp.matcher("");
        
        String tail = tuText;
        String newString = "";
        
        while (tail.length() > 0) {
            ntm.reset(tail);
            if (ntm.find()) {
                String pfx = ntm.group(1);
                String candidate = ntm.group(2);
                tail = ntm.group(3);
                
                if (candidate.indexOf("'") != -1) {
                    candidate = candidate.replace("'", "&#x27;");
                }
                if (candidate.indexOf("&apos;") != -1) {
                    candidate = candidate.replace("&apos;", "&#x27;");
                }
                if (candidate.indexOf("\"") != -1) {
                    candidate = candidate.replace("\"", "&quot;");
                }
                newString += (pfx + candidate);
            }
            else {
                break;
            }
        }
        
        if (tail.length() > 0) {
            newString += tail;
        }
       
        return newString;
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
    
    private Matcher formatMatcher 
            = Pattern.compile("<[be]?x\\b.*?\\bid=(['\"])(.+?)\\1.*?>").matcher("");
    
    /** 
     * Passed a translation unit, look for bx/ex tags (beginning and ending)
     * and replace them with their original strings.
     * (Note: As we expand our XLIFF, we will need to look for <x> codes
     * as well).
     * @param tuText The text of the Translation Unit that needs to have
     *               its format codes resolved
     * @return The expanded TU, with bx/ex codes replaced by their equivalents
     */
    private String resolveFormatCodes(String tuText) {
        if (this.format == null) {
            // Without a format class, we can't resolve the codes
            return tuText;                     // Just return
        }

        String newStr = tuText;
        boolean moreCodes = true;              // Be optimistic
        
        while (moreCodes) {
            // Find each format code and extract its id
            formatMatcher.reset(newStr);
            if (formatMatcher.find()) {
                String formatID = formatMatcher.group(2);  // group 1 is 1st set of parens
                String wholeTag = formatMatcher.group(0);  // group 0 matches entire regex
                
                // Try to get the replacement text
                String formatText = format.getReplacement(formatID);
                if (formatText.length() > 0) {  // If successful
                    newStr = newStr.replace(wholeTag, formatText);
                    
                    // It is possible that the replacement text contains
                    // a <lt:tu id= ... empty element. If it does,
                    // resolve it.
                    if (newStr.indexOf("<lt:tu id=") != -1) {
                        // We found an embedded tu (probably a translatable
                        // string within an alt or title attr value of 
                        // something like an img or anchor tag or ...
                        newStr = expandTusInFormat(newStr);
                    }
                }
                else {
                    // Don't leave the format code unexpanded--or we'll loop 
                    // indefinitely
                    // Better to exit this loop. (WLW 4/18/2007)
                    // moreCodes = false;                            
                    // On second thought, let's just delete the code ...
                    newStr = newStr.replace(wholeTag, "");
                }
            }
            else {
                // We're done--no more format codes
                moreCodes = false;
            }
        }
        
        return newStr;       // The TU with all format codes expanded.
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
