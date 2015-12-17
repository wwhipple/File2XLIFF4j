/*
 * PlaintextImporter.java
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
import java.io.*;
import java.util.*;
import java.lang.*;
import java.nio.charset.*;
import java.util.regex.*;

/**
 * The PlaintextImporter identifies translatable text in plaintext files, 
 * creating an XLIFF file with source elements containing segments of the text.
 * (The XLIFF spec defines plaintext as having "no formatting other than,
 * possibly, wrapping.")
 *
 * <p>Because plaintext has no formatting other than wrapping, the resulting
 * XLIFF source elements will include no bx, ex, or x tags; no format file will
 * be needed to map bx/ex/x tags back to their original formatting.
 * 
 * @author Weldon Whipple &lt;weldon@whipple.org&gt;
 */
public class PlaintextImporter implements Converter {

    private OutputStreamWriter xliffOut;
    private OutputStreamWriter skeletonOut;
    private Locale sourceLanguage;         // For the source-language attribute
    private final String dataType = "plaintext";  // For the datatype attribute
    private String originalFileName;       // For the original attribute

    private int curIndent = 0;              // How far to indent the next element

    
    private SegmentBoundary boundaryType;
    
    // Space characters that might appear at the end of a line. (We omit
    // non-breaking spaces, etc., since by definition, then won't appear
    // at the end of a line.) When these appear at the end of a line, we
    // don't add a space at the end when joining to the next line.
    private final String SPACE_CHAR = 
            "\\u0020"  // "Standard" space 
          + "\\u2003"  // Em space
          + "\\u3000"  // Ideographic space
          + "\\u2002"  // En space
          + "\\u2007"  // Figure space
          + "\\u2004"  // Three-per-em space
          + "\\u2005"  // Four-per-em space
          + "\\u2006"  // Six-per-em space
          + "\\u2009"  // Thin space
          + "\\u205F"  // Medium mathematical space
          + "\\u2008"  // Punctuation space
          + "\\u200A"  // Hair space
          + "\\u200B"; // Zero width space
    
    // If these appear at the end of a line, we don't add a space when
    // we join to the next line.
    private final String DASH_HYPHEN =
            "\\u002d"  // Hyphen-minus
          + "\\u2010"  // Hyphen
          + "\\u2013"  // En dash
          + "\\u2012"  // Figure dash
          + "\\u2212"  // Minus sign
          + "\\u2014"  // Em dash
          + "\\u2015"  // Horizontal bar
          + "\\u058A"  // Armenian hyphen
          + "\\u301C"  // Wave dash (Japanese)
          + "\\u3030"; // Wavy dash (Japanese)
    
    // If these appear at the end of a line, we can join the next line to this
    // one without inserting another space.
    private final Matcher dashHyphenSpaceClass 
            = Pattern.compile("[" + SPACE_CHAR + DASH_HYPHEN + "]").matcher("");
    
    // Delete the next character (which appears at the end of a line) before
    // joining it with the next line.
    private final char END_SOFT_HYPHEN = '\u00ad';
    
    // The Mongolian soft hyphen is placed at the *beginning* of the next line
    // Delete it if it appears as the first character of a subsequent line ...
    // before joining the subsequent line to the current line.
    private final char MONGOLIAN_TODO_SOFT_HYPHEN = '\u1806';
    
    
    /**
     * Constructor for the Plaintext importer. 
     */
    public PlaintextImporter() {
        
    }
    
    
    /**
     * Convert a plaintext file to XLIFF. In the process, generate a skeleton 
     * file to be used in generating a translated version of the original file, 
     * following translation.
     * @param mode The mode of conversion (to or from XLIFF). The value must be
     *        TO_XLIFF.
     * @param language The primary language of the plaintext file to be imported. 
     * @param phaseName The target phase-name. This value is ignored by importers.
     * @param maxPhase The maximum phase number. This value is ignored by importers.
     * @param nativeEncoding The encoding of the input plaintext file. If this
     *        argument is null, the importer will make the following "guesses,"
     *        based on primary language:
     * <dl>
     *    <dt>English:</dt>
     *       <dd>ISO-8859-1</dd>
     *    <dt>Danish, Dutch, Finnish, French, German, Icelandic, Italian, 
     *        Norwegian, Portuguese, Spanish, Swedish:</dt>
     *       <dd>ISO-8859-1</dd>
     *    <dt>All other languages:</dt>
     *       <dd>UTF-8</dd>
     * </dl>
     * @param nativeFileType The type of the input file. This value is ignored.
     *        (The value "plaintext" is an official XLIFF attribute value and is
     *        used unconditionally by this importer.)
     * @param inputFileName The name of the plaintext input file.
     * @param baseDir The directory that contains the input file--from which
     * we will read input. This is also the directory to which the output
     * XLIFF and skeleton files will be written. The output files will be named 
     * as follows:
     * <ul>
     * <li>&lt;original_file_name&gt;.xliff</li>
     * <li>&lt;original_file_name&gt;.skeleton</li>
     * </ul>
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary The boundary on which to segment translation units (e.g.,
     *        on paragraph or sentence boundaries). If null, sentence boundaries
     *        are used.
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        XLIFF file was written.     
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */    
    public ConversionStatus convert(
            ConversionMode mode,       // Must be TO_XLIFF
            Locale language,           // Must be non-null
            String phaseName,          // Ignored
            int maxPhase,              // Ignored
            Charset nativeEncoding,    // Has defaults if null
            FileType nativeFileType,   // Ignored--always "plaintext"
            String inputFileName, 
            String baseDir,
            Notifier notifier,         // May be null
            SegmentBoundary boundary,  // If null, defaults to sentence
            StringWriter generatedFileName) throws ConversionException {

        if (! mode.equals(ConversionMode.TO_XLIFF)) {
            throw new ConversionException("Plaintext Importer supports only conversions"
                    + " from plaintext to XLIFF.");
        }
        
        if (inputFileName == null || inputFileName.length() == 0) {
            throw new ConversionException("Name of input plaintext file omitted.");
        }

        if (language == null) {
            throw new ConversionException("Source language omitted. (Required)");
        }
        
        // If caller specifies no encoding, choose a default encoding based on
        // source language.
        Charset encoding = nativeEncoding;
        if (encoding == null) {
            // @todo: Consider using the non-Unicode ISO encodings as defaults
            // What about 0xFEFF?
            // If Japanese, try to guess encoding using one of the tools on the
            // net.
            encoding = getEncoding(language);  // Get a default encoding for the language
        }

        sourceLanguage = language;             // The input file's primary language
        originalFileName = inputFileName;   // The name of the input MIF file
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
                    baseDir + File.separator + inputFileName + Converter.xliffSuffix),
                    "UTF8");
            skeletonOut = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + inputFileName + Converter.skeletonSuffix),
                    "UTF8");

            // Write out the XLIFF preliminaries
            this.writeXliffProlog();
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

        // sourceText holds/accumulates a paragraph as it is read from the input file.
        StringBuilder sourceText = new StringBuilder();

        try {
            
            // Open the input plaintext file for reading.
            BufferedReader inPlain = new BufferedReader(new InputStreamReader(new FileInputStream(
                baseDir + File.separator + inputFileName), encoding));
            
            String curInline = "";
            boolean done = false;
            
            MAIN_LOOP:
            while (! done) {
                curInline = inPlain.readLine(); // Removes
                if (curInline == null) {  // null means end of file
                    done = true;
                    break;
                }
                
                // A blank line indicates a paragraph break (at least by our
                // definition).
                if (curInline.trim().length() == 0) {  // A blank line
                    // If we've accumulated some characters, write some XLIFF
                    int numTus = 0;
                    if (sourceText.length() > 0) {
                        numTus = this.processParagraphBuff(sourceText.toString());
                        if (numTus > 0) {
                            // If more than 0 TUs, then processCandidateBuff()
                            // will have written some TU place holders to the
                            // skeleton file. We just need to add a newline.
                            this.writeSkeleton("\n");
                        }
                        else {
                            // The accumulated source text didn't include much
                            // meaningful (no translatable text at least). Let's
                            // just write it to the skeleton.
                            this.writeSkeleton(sourceText + "\n");
                        }
                    }
                    sourceText.setLength(0);
                    // Now write the current input line (paragraph separator)
                    this.writeSkeleton(curInline + "\n");
                    
                }
                
                // There must be something (kind of?) interesting in the line 
                // just read. Append it to the sourceText buffer
                else {
                    int sourceLen = sourceText.length();
                    if (sourceLen > 0) {
                        // Add trailing space (or remove soft hyphens) if needed
                        // --before appending the new input line to what we've
                        // accumulated in sourceText (so far).
                        int numToAdjust = this.checkTrailingSpace(sourceText);


                        if (numToAdjust > 0) {
                            sourceText.append(" ");
                        }
                        else if (numToAdjust < 0) {
                            sourceText.setLength(sourceLen -1);
                        }

                        // Does curInline start with a Mongolian soft hyphen?
                        if (curInline.charAt(0) == MONGOLIAN_TODO_SOFT_HYPHEN) {
                            curInline = curInline.substring(1); // delete soft hyphen
                        }
                    }
                        
                    sourceText.append(curInline);
                }
            }  // end while

            // If we hit the end of file and left characters in the sourceText
            // buffer, print them out to the XLIFF.
            int numTus = 0;
            if (sourceText.length() > 0) {
                numTus = this.processParagraphBuff(sourceText.toString());
                if (numTus > 0) {
                    // If more than 0 TUs, then processCandidateBuff()
                    // will have written some TU place holders to the
                    // skeleton file. We just need to add a newline.
                    this.writeSkeleton("\n");
                }
                else {
                    // The accumulated source text didn't include much
                    // meaningful (no translatable text at least). Let's
                    // just write it to the skeleton.
                    this.writeSkeleton(sourceText + "\n");
                }
                sourceText.setLength(0);
            }
        }

        catch(IOException e) {
            System.err.println("I/O error reading plaintext input: " + e.getMessage());
            throw new ConversionException("I/O error reading plaintext input: " 
                    + e.getMessage());
        }

        // We've finished reading the input file. Now finish writing the XLIFF file
        writeXliffEpilog();
        
        // ... And we're done.
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
     * Passed a locale, return a default encoding.
     * @param locale The locale whose encoding is being requested
     * @return A default encoding for the locale.
     */
    private Charset getEncoding(Locale locale) throws ConversionException {
        Charset encoding = null;
        String langStr = locale.getLanguage();
        
        // English: ISO-8859-1
        if (langStr.equals(new Locale("en", "", "").getLanguage())) {
            return Charset.forName("ISO-8859-1");
        }
        
        // Other Western European Languages: ISO-8859-1
        //     Danish:
        if (langStr.equals(new Locale("da", "", "").getLanguage())
            // Dutch:
            || langStr.equals(new Locale("nl", "", "").getLanguage())
            // Finnish:
            || langStr.equals(new Locale("fi", "", "").getLanguage())
            // French:
            || langStr.equals(new Locale("fr", "", "").getLanguage())
            // German:
            || langStr.equals(new Locale("de", "", "").getLanguage())
            // Icelandic:
            || langStr.equals(new Locale("is", "", "").getLanguage())
            // Italian:
            || langStr.equals(new Locale("it", "", "").getLanguage())
            // Norwegian (both):
            || langStr.equals(new Locale("nb", "", "").getLanguage())
            || langStr.equals(new Locale("no", "", "").getLanguage())
            // Portuguese:
            || langStr.equals(new Locale("pt", "", "").getLanguage())
            // Spanish:
            || langStr.equals(new Locale("es", "", "").getLanguage())
            // Swedish:
            || langStr.equals(new Locale("sv", "", "").getLanguage())
            ) {
            return Charset.forName("ISO-8859-1");
        }
        
        // Default to UTF-8 for everything else
        return Charset.forName("UTF-8");
    }

    /**
     * Passed one or two ints, see if they represent a Unicode Chinese/Japanese/Korean
     * "han" character.
     * <p>The following Unicode character ranges are considered to be CJK
     * characters:
     * <ul>
     * <li>0x2e80-0x9fff</li>
     * <li>0xf900-0xfaff</li>
     * <li>0xfe30-0xfe4f</li>
     * <li>0x20000-0x2a6df</li>
     * <li>0x2f800-0x2fa1f</li>
     * </ul>
     * The last two ranges (above) must be represented as surrogate pairs, since
     * they are greater than 65535 (0xffff). 
     * <p>To convert the two surrogate characters to Unicode, do the following:
     * <ol>
     * <li>Extract the bits (shown as x's) from the previous and mainChar parameters:
     * <br/>previous: 1101 10xx xxxx xxxx  (high surrogate)
     * <br/>mainChar: 1101 11xx xxxx xxxx  (low surrogate)</li>
     * <li>Create a number consisting of the high surrogate x bits concatenated 
     * with the low surrogate x-bits.
     * <li>Add 0x10000 to the resulting number
     * </ol>
     * Then compare the resulting number to the last two character ranges shown
     * above.
     * @param mainChar Ordinarily, this is an int that represents a Unicode
     *        character. This is the one to check to see if it is a CJK
     *        character.
     * @param previous If this is zero or <i>outside</i> the range 0xd800-0xdbff,
     *        then mainChar's value is the tested (in isolation) to see if it
     *        is a CJK character.
     *        <p>If this parameter is in the range 0xd800-0xdbff and mainChar is
     *        in the range 0xdc00-dfff, then they are treated as surrogate pairs
     *        and tested to see if they represent a CJK character.
     * @return true if a CJK character, else false.
     */
    private boolean isCJK(int mainChar, int previous) {
        if (previous < 0 || previous > 65535) {
            // Do some sanity checking
            return false;   // Bogus char range--definitely not CJK
        }
        if (previous == 0 || previous < 0xd800 || previous > 0xdbff) {
            // No surrogates involved; just check mainChar ranges
            if ((mainChar >= 0x2e80 && mainChar <= 0x9fff)
                || (mainChar >= 0xf900 && mainChar <= 0xfaff)
                || (mainChar >= 0xfe30 && mainChar <= 0xfe4f)) {
                return true;    // Yes, this is CJK
            }
            else {
                return false;   // Not a Han ideograph (including hiragana, katakana, ...)
            }
        }
        else {  // We have surrogates
            int highVal = (previous & 0x3ff);  // Extract low 10 bits
            int lowVal  = (mainChar & 0x3ff);  // Ditto for low surrogate
            // Combine high and low surrogates
            int combined = (highVal << 10) | lowVal;
            // Add 0x100000
            combined += 0x10000;
            
            if ((combined >= 0x20000 && combined <= 0x2a6df)
                || (combined >= 0x2f800 && combined <= 0x2fa1f)) {
                return true;
            }
            else {
                return false;
            }
        }
    }
    
    /**
     * Method called whenever an end of paragraph is encountered in a plaintext
     * file, this method writes the segment(s) in the XLIFF.
     * @param text A buffer full of accumulated text to process and write to
     *             XLIFF.
     * @return The number of segments added to XLIFF.
     */
    private int processParagraphBuff(String text) {
        // candidateTu accumulates XLIFF to be written to the XLIFF file
        StringBuilder candidateTu = new StringBuilder();
        
        int numSegments = 0;
        if (text.length() > 0) {
            // Get the core segments:
            SegmentInfo[] coreTus = TuPreener.getCoreSegments(text,
                this.boundaryType, this.sourceLanguage);

            UUID paraId = null;

            numSegments = coreTus.length;  // We'll return this.
            
            boolean hasSuccessor = true;

            for (int i = 0; i < coreTus.length; i++) {
                String coreSeg = coreTus[i].getSegmentStr();
                UUID curTuId = UUID.randomUUID();

                // The paragraph ID is the UUID of the first translation
                // unit in the paragraph.
                if (paraId == null) {
                    paraId = curTuId;
                }

                // Because there are no well-formedness constraints, we
                // will consider all segments mergeable with their
                // successors (and record it in XLIFF? (Is this necessary?)
                // We need to indicate whether this segment is "mergeable"
                // with its successor--and record it in the XLIFF
                if (i == (coreTus.length-1)) { // If this is the last segment ...
                    hasSuccessor = false;      // ... it doesn't have a successor
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

                // Write the segment to the source element
                candidateTu.append(
                        TuStrings.escapeTuString(TuPreener.getPrefixText(coreSeg))
                      + TuPreener.CORE_START_MRK
                      + TuStrings.escapeTuString(TuPreener.getCoreText(coreSeg))
                      + TuPreener.CORE_END_MRK
                      + TuStrings.escapeTuString(TuPreener.getSuffixText(coreSeg)));

                candidateTu.append("</source>\n");  // & close the element

                candidateTu.append(indent(6) + "</trans-unit>\n"); // & the TU.

                writeXliff(candidateTu.toString());  // Write this TU

                // Write a Tu placeholder in the skeleton file.
                if (i > 0) {
                    // Write a space between placeholders.
                    writeSkeleton(" ");
                }
                writeSkeleton("<lTLtLT:tu id='" + curTuId + "'/>");

                candidateTu.setLength(0); // Clear out the candidateTu
            }  // for
        }
        
        return numSegments;
    }

    /**
     * Determine whether the string passed as a parameter requires a trailing
     * space. 
     * @param text The text to examine to determine if its last character should
     *             be followed by a space.
     * @return The number of spaces to add or remove from the end of the string.
     */
    private int checkTrailingSpace(StringBuilder text) {
        if (text == null) {
            return 1;              // If passed a null, add a space (I suppose)
        }
        int textLen = text.length();
        boolean needsSpace = true;     // We need a space by default.
        boolean lastIsSoft = false;    // Last character isn't soft hyphen ...
        if (textLen > 0) {
            // We are adding to previously existing text, we might
            // need to add a space at the end (in place of the newline
            // at the end of the previous line.)

            // See if the last character of what we've accumulated
            // so far is a dash/hyphen or a space. If it is, we
            // can probably just concatenate
            int lastChar = text.charAt(textLen-1);
            int nextToLastChar = 0;    // Possible high-order surrogate
            if (text.length() > 1) {
                nextToLastChar = text.charAt(textLen-2);
            }

            // Reset the precompiled matcher for dash/hyphen/space
            dashHyphenSpaceClass.reset(Character.toString((char)lastChar));

            //////////////////////////////////////////////////
            // 1. Does existing buffer end with a space or dash?
            if (dashHyphenSpaceClass.matches()) {
                needsSpace = false;     // Don't sandwich a space in between ...
            }
            // The next checks are only if the character might be 
            // a Han character. (Most CJKV characters words don't 
            // have spaces between them.)

            //////////////////////////////////////////////////
            // 2. Does existing buffer end with CJK surrogate pair?
            else if ((nextToLastChar >= 0xd800) && (nextToLastChar <= 0xdbff)
                    && isCJK(lastChar, nextToLastChar)) {
                needsSpace = false;
            }

            //////////////////////////////////////////////////
            // 3. Does existing buffer end with CJK <= 65535?
            else if (lastChar > 0x2e7f && isCJK(lastChar, 0)) {
                needsSpace = false;
            }

            //////////////////////////////////////////////////
            // 4. Does existing buffer end with soft hyphen?
            else if (lastChar == END_SOFT_HYPHEN) {
                needsSpace = false;
                lastIsSoft = true;
            }
        }
        if (needsSpace) {
            return 1;          // Caller should add a space
        }
        else if (lastIsSoft) {
            return -1;         // Caller should remove the soft hyphen.
        }

        return 0;              // Caller should neither
    }
    
    /**
     * When the end-of-document is encountered, write what follows tqhe final
     * translation unit.
     */
    private void writeXliffEpilog() {
        
        // Finish off the XLIFF file:
        writeXliff(indent('-') + "</body>\n"); // Close the body element
        writeXliff(indent('-') + "</file>\n"); // Close the file element
        writeXliff("</xliff>\n");               // Close the xliff element


        // Two flushes (more water-wise than three ...)
        try {
            xliffOut.flush();             // Well-bred writers flush when finished
            skeletonOut.flush();
        }
        catch(IOException e) {
            System.err.println("Error flushing skeleton stream.");
            System.err.println(e.getMessage());
        }
    }

    /**
     * Write things that appear at the beginning of an XLIFF file
     */
    private void writeXliffProlog() {
        // Write the start of the output XLIFF document
        writeXliff(Converter.xmlDeclaration
                 + Converter.startXliff
                 + indent() + "<file original='" 
                       + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                       + "' source-language='" + sourceLanguage.toString() 
                       + "' datatype='plaintext'>\n"
                 + indent() + "<header");
        switch(boundaryType) {
            case PARAGRAPH:  
                writeXliff(" lt:segtype='paragraph'");
                break;
            case SENTENCE:
                writeXliff(" lt:segtype='sentence'");
                break;
            default:
                writeXliff(" lt:segtype='sentence'");
        }
        writeXliff(">\n" + indent('0') + "</header>\n" 
                 + indent('0') + "<body>\n");
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
     * @return the PLAINTEXT file type.
     */
    public FileType getFileType() {
        return FileType.PLAINTEXT;
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
     * Write a string to the temporary (intermediate) skeleton file
     * @param text The string to write to the tskeleton. (No newlines, etc.
     *             are added. The caller must supply all desired formatting.)
     */
    private void writeSkeleton(String text) {
        // Write tskeleton file ...
        if (text != null) {
            try {
                skeletonOut.write(text);
                skeletonOut.flush();    // For debugging
            }
            catch(IOException e) {
                System.err.println("Error writing skeleton file.");
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
