/*
 * MifExporter.java
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
import java.nio.*;
import java.util.regex.*;

/**
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class MifExporter implements Converter {

    /** The following maps TU identifiers to target strings: */
    private TuStrings tuMap = new TuStrings();
    
    private Matcher tuMatcher
        = Pattern.compile("<lt:(tu|format) id=['\"]([^'\"]+)['\"] parent=['\"]([^'\"]+)['\"].*?/>").matcher("");
    
    // For matching bx, ex, x
    private Matcher formatMatcher
        = Pattern.compile("^(.*?)(<[be]?x\\b.*?\\bid=['\"]([^'\"]+)['\"].*?>)(.*)$").matcher("");

    // Alternate pattern for matching bx, ex and x tags within format strings
    String pattStr = "^(.*?)(<([be]?x)\\b.*?\\bid=['\"]([^'\"]+)['\"](.*?)>)(.*)$";
    private Matcher formatMatcher2 = Pattern.compile(pattStr,Pattern.DOTALL).matcher("");
    
    private Format format;          // Maps bx/ex etc. to original format characters.
    private int curIndent = 5;               // Probably a good amount?
    private final static int INIT_INDENT = 5;

    private Map<String,String> fLanguageMap = new HashMap<String,String>();
    
    /** Creates a new instance of MifExporter */
    public MifExporter() {
        // Initialize the FLanguage map
        
        fLanguageMap.put("en_US", "USEnglish");
        fLanguageMap.put("en_UK", "UKEnglish");
        fLanguageMap.put("en", "USEnglish");
        fLanguageMap.put("de", "German");
        fLanguageMap.put("de_CH", "SwissGerman");
        fLanguageMap.put("fr", "French");
        fLanguageMap.put("fr_CA", "CanadianFrench");
        fLanguageMap.put("es", "Spanish");
        fLanguageMap.put("ca", "Catalan");
        fLanguageMap.put("it", "Italian");
        fLanguageMap.put("pt", "Portuguese");
        fLanguageMap.put("pt_BR", "Brazilian");
        fLanguageMap.put("da", "Danish");
        fLanguageMap.put("nl", "Dutch");
        fLanguageMap.put("nb", "Norwegian"); // Bokmål ?
        fLanguageMap.put("nn", "Nynorsk");
        fLanguageMap.put("fi", "Finnish");
        fLanguageMap.put("sv", "Swedish");
        fLanguageMap.put("ja", "Japanese");
        fLanguageMap.put("zh_TW", "TraditionalChinese");
        fLanguageMap.put("zh_HK", "TraditionalChinese");
        fLanguageMap.put("zh_CN", "SimplifiedChinese");
        fLanguageMap.put("zh_SG", "SimplifiedChinese");
        fLanguageMap.put("ko", "Korean");
    }
    
    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) to
     * the original Maker Interchange Format.
     * @param mode The mode of conversion (must be FROM_XLIFF)
     * @param locale The Java Locale of the XLIFF targets to use in constructing
     *        the MIF document. The language is used in constructing a unique
     *        name for the output file. For example, if es_ES is specified,
     *        the output file name contains the "es_ES" in its name.
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
     * @param nativeEncoding The encoding of the native document. (For the time
     *        being, this is ignored and assumed to be X-MIF-FRAMEROMAN.)
     *        If the language is Japanese, output will be in EUC Japanese.
     * @param nativeFileType This parameter is ignored. (The MIF filetype was
     *        stored in the original MIF file was imported.)
     * @param nativeFileName The name of the original MIF file. This is used as 
     *        the basis for the names of the XLIFF, format and skeleton files
     *        in the baseDir directory (specified in the next parameter), from
     *        which the exported MIF file will be constructed. Those three
     *        input files will be assumed to exist in the following locations:
     *        <ul>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.xliff</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.format</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.skeleton</li>
     *        </ul>
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */
    public ConversionStatus convert(ConversionMode mode,
            Locale locale,
            String phaseName,
            int maxPhase,
            Charset nativeEncoding,
            FileType nativeFileType,
            String nativeFileName,
            String baseDir,
            Notifier notifier,
            SegmentBoundary boundary,
            StringWriter generatedFileName) throws ConversionException {

        
        // Verify input arguments
        if ((locale == null) /*|| (nativeEncoding == null) */
            || (nativeFileName == null)
                || (nativeFileName.length() == 0)
                || (baseDir == null)
                || (baseDir.length() == 0)
                || (! mode.equals(ConversionMode.FROM_XLIFF))) {
            throw new ConversionException("Required parameter(s)"
                    + " omitted, incomplete or incorrect.");
        }

        // Get language and country
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String both = language;
        if ((country != null) && (country.length() > 0)) {
            both += ("_" + country);
        }
        
        // Get the value that goes in the FLanguage tag. Try the more specific first
        String fLanguage = fLanguageMap.get(both);
        if (fLanguage == null) {
            // Try just the language
            fLanguage = fLanguageMap.get(language);
        }
        
        // If FLanguage is still null, use the standard MIF NoLanguage value
        if (fLanguage == null) {
            fLanguage = "NoLanguage";
        }
        
        Charset mifEncoding = null;
        if (language.equals(new Locale("ja", "", "").getLanguage())) {
            // Use eucJP for Japanese
            mifEncoding = Charset.forName("Shift-JIS");
        }
        else if (language.equals(new Locale("ko", "", "").getLanguage())) {
            // Use  KSC5601-1992 for Japanese
            mifEncoding = Charset.forName("EUC-KR");
        }
        else if (language.equals(new Locale("zh", "", "").getLanguage())) {
            // Chinese has two supported encodings--for traditional and simplified Chinese
            if (country.equals("TW")    // Taiwan uses traditional Chinese
                || country.equals("HK")) {
                // Hong Kong used to use Traditional Chinese as well. I wonder what it
                // uses now that it has been subsumed by the Beijing government--which uses
                // simplified Chinese. (For the moment, we will be forward looking and
                // assume that Hong Kong now uses simplified Chinese ... or that
                // translators will specify zh_TW for Traditional Chinese
                mifEncoding = Charset.forName("Big5");  // 
            }
            else {
                // I *hope* Java's EUC-CN is right for GB2312-80.EUC
                mifEncoding = Charset.forName("GB2312");
            }
        }
        else {
//            mifEncoding = Charset.forName("X-MIF-FRAMEROMAN");
            // The above doesn't work on Tomcat ...
            MifCharsetProvider mifP = new MifCharsetProvider();
            mifEncoding = mifP.charsetForName("X-MIF-FRAMEROMAN");
        }

        
        String inXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String inSkeleton = baseDir + File.separator + nativeFileName
                + Converter.skeletonSuffix;
        String inFormat = baseDir + File.separator + nativeFileName
                + Converter.formatSuffix;

        String outMif = nativeFileName;
        
        int lastDot = outMif.lastIndexOf(".");
        if (lastDot == -1) {  // Unusual, but no dot!
            outMif = baseDir + File.separator + outMif + "."
                + locale.toString();
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outMif + "." + locale.toString());
            }
        }
        else {
            outMif = baseDir + File.separator
                + outMif.substring(0,lastDot)
                + "." + locale.toString() + "."
                + outMif.substring(lastDot+1);
            if (generatedFileName != null) {
                // Tell caller the name of the output file (wo/directories)
                generatedFileName.write(outMif.substring(0,lastDot)
                    + "." + locale.toString() + "." + outMif.substring(lastDot+1));
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
        tuMap.loadStrings(inXliff, locale, phaseName, maxPhase, false);
        
        //////////////////////////////////////////////////////////////////
        // Get readers/writers on the in/out files and necessary objects
        //////////////////////////////////////////////////////////////////

        // Skeleton (UTF-8, of course!)
        BufferedReader inSkel = null;
        try {
            inSkel = new BufferedReader(new InputStreamReader(new FileInputStream(inSkeleton),
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
            System.err.println("Cannot access the format file.");
            System.err.println(e.getMessage());
        }

        // Mif output file 
        OutputStreamWriter output = null;  // Where final document is written
        try {
            output = new OutputStreamWriter(new FileOutputStream(outMif), mifEncoding);
        }
        catch(FileNotFoundException e ) {
            System.err.println("Cannot write to the Mif file: " + e.getMessage());
            throw new ConversionException("Cannot write to the Mif file: "
                    + e.getMessage());
        }
        
        boolean needMifEncoding = false;
        // Check for Japanese
        if (locale.getLanguage().equals(new Locale("ja", "", "").getLanguage())  // Japanese
            || locale.getLanguage().equals(new Locale("zh", "", "").getLanguage()) // Chinese
            || locale.getLanguage().equals(new Locale("ko", "", "").getLanguage())) { // Korean
            needMifEncoding = true;
        }

        /*******************************
         * E X P O R T   T H E   M I F *
         *******************************/
        try {
            for (;;) {
                String skelLine = inSkel.readLine();
                if (skelLine == null) {
                    break;
                }
            
                // If line has tu place-holder, substitute the TU in its place
                if ((skelLine.indexOf("<lt:tu id=") > -1)
                    || (skelLine.indexOf("<lt:format id=") > -1)) {
                    curIndent = getIndentDepth(skelLine); 
                    skelLine = expandTus(skelLine);
                }
                else {
                    skelLine += "\r\n";
                }
            
                // Replace FLanguage with the target language
                if (skelLine.contains("<FLanguage")) {
                    skelLine = skelLine.replaceAll("(<FLanguage +)[^>]+(>)",
                          "$1" + fLanguage + "$2");
                }
                
                // Then write the line to the output stream; check for non-Latin1 first
                // For Japanese, how about a (fairly??) common WinDoze font:
                if (language.equals(new Locale("ja", "", "").getLanguage())) {
                    if (skelLine.toLowerCase().indexOf("fencoding") != -1) {
                        skelLine = skelLine.replaceAll("(<FEncoding +`)[^']*('>)",
                                "$1" + "JISX0208.ShiftJIS" + "$2");
                    }
                    // skelLine could actually be multiple lines. So don't use "else if"
                    if (skelLine.toLowerCase().indexOf("fplatformname") != -1) {
                        skelLine = skelLine.replaceAll("(<FPlatformName +`)[^']*('>)",
                                "$1" + "W.MS Mincho.R.400" + "$2");
                    }
                    if (skelLine.toLowerCase().indexOf("ffamily") != -1) {
                        skelLine = skelLine.replaceAll("(<FFamily +`)[^']*('>)",
                                "$1" + "MS Mincho" + "$2");
                    }
                }
                // We need to handle Korean differently as well
                else if (language.equals(new Locale("ko", "", "").getLanguage())) {
                    if (skelLine.toLowerCase().indexOf("fencoding") != -1) {
                        skelLine = skelLine.replaceAll("(<FEncoding +`)[^']*('>)",
                                "$1" + "KSC5601-1992" + "$2");
                    }
                    // skelLine could actually be multiple lines. So don't use "else if"
                    if (skelLine.toLowerCase().indexOf("fplatformname") != -1) {
                        skelLine = skelLine.replaceAll("(<FPlatformName +`)[^']*('>)",
                                "$1" + "M.Times.P" + "$2");
                    }
                    if (skelLine.toLowerCase().indexOf("ffamily") != -1) {
                        skelLine = skelLine.replaceAll("(<FFamily +`)[^']*('>)",
                                "$1" + "Adobe Myungjo Std M" + "$2");
                    }
                }
                // Handle the two Chinese encodings that FM supports.
                else if (language.equals(new Locale("zh", "", "").getLanguage())) {
                    // Traditional Chinese
                    if (country.equals("TW") // Taiwan uses traditional Chinese
                        || country.equals("HK")) {   
                        /* Hong Kong is a dilemma of sorts. The (old--presumption by PRC)
                         * I18n books indicate that Hong Kong uses traditional Chinese like
                         * Taiwan. However, Wikipedia states that Hong Kong now uses
                         * simplified Chinese more than traditional. Perhaps HK shouldn't
                         * even exist as a country code any more??? */
                        if (skelLine.toLowerCase().indexOf("fencoding") != -1) {
                            skelLine = skelLine.replaceAll("(<FEncoding +`)[^']*('>)",
                                    "$1" + "BIG5" + "$2");
                        }
                        // skelLine could actually be multiple lines. So don't use "else if"
                        if (skelLine.toLowerCase().indexOf("fplatformname") != -1) {
                            skelLine = skelLine.replaceAll("(<FPlatformName +`)[^']*('>)",
                                    "$1" + "M.Times.P" + "$2");
                        }
                        if (skelLine.toLowerCase().indexOf("ffamily") != -1) {
                            skelLine = skelLine.replaceAll("(<FFamily +`)[^']*('>)",
                                    "$1" + "Adobe Ming Std L" + "$2");
                        }
                    }
                    // Simplified Chinese
                    else {  // Otherwise assume simplified Chinese (Mainland China and
                            // Singapore ...)
                        if (skelLine.toLowerCase().indexOf("fencoding") != -1) {
                            skelLine = skelLine.replaceAll("(<FEncoding +`)[^']*('>)",
                                    "$1" + "GB2312-80.EUC" + "$2");
                        }
                        // skelLine could actually be multiple lines. So don't use "else if"
                        if (skelLine.toLowerCase().indexOf("fplatformname") != -1) {
                            skelLine = skelLine.replaceAll("(<FPlatformName +`)[^']*('>)",
                                    "$1" + "M.Times.P" + "$2");
                        }
                        if (skelLine.toLowerCase().indexOf("ffamily") != -1) {
                            skelLine = skelLine.replaceAll("(<FFamily +`)[^']*('>)",
                                    "$1" + "Adobe Song Std L" + "$2");
                        }
                    }
                }
                output.write(skelLine);
                output.flush();     // For debugging
                
                // If we need a MIFEncoding statement, output one.
                // (We will put it out as the second line of the file.)
                // Note: Because Java is so accommodating, we can just specify the following
                // using their Unicode values, and Java will automatically convert them to
                // either Shift_JIS, of one of the two Chinese encodings that FM supports or
                // the Korean encoding that FM supports (which we specified when we created
                // the Writer above).
                if (needMifEncoding) {
                    if (language.equals(new Locale("ja", "", "").getLanguage())) {
                        output.write("<MIFEncoding `\u65e5\u672c\u8a9e'>\r\n"); // Nihongo
                    }
                    else if (language.equals(new Locale("zh", "", "").getLanguage())) {
                        output.write("<MIFEncoding `\u4e2d\u6587'>\r\n"); // Chinese (either kind)
                    }
                    else if (language.equals(new Locale("ko", "", "").getLanguage())) {
                        output.write("<MIFEncoding `\ud55c\uad6d\uc5b4'>\r\n"); // Korean
                    }
                    needMifEncoding = false;
                }
            }

            // Flush and close before leaving
            output.flush();

            output.close();
        }
        catch (IOException e) {
            System.err.println("Cannot read skeleton file");
            throw new ConversionException("Cannot read skeleton file "
                    + inSkeleton );
        }

        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) to
     * the original Maker Interchange Format.
     * @param mode The mode of conversion (must be FROM_XLIFF)
     * @param locale The Java Locale of the XLIFF targets to use in constructing
     *        the MIF document. The language is used in constructing a unique
     *        name for the output file. For example, if es_ES is specified,
     *        the output file name contains the "es_ES" in its name.
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
     * @param nativeEncoding The encoding of the native document. (For the time
     *        being, this is ignored and assumed to be X-MIF-FRAMEROMAN.)
     *        If the language is Japanese, output will be in EUC Japanese.
     * @param nativeFileType This parameter is ignored. (The MIF filetype was
     *        stored in the original MIF file was imported.)
     * @param nativeFileName The name of the original MIF file. This is used as 
     *        the basis for the names of the XLIFF, format and skeleton files
     *        in the baseDir directory (specified in the next parameter), from
     *        which the exported MIF file will be constructed. Those three
     *        input files will be assumed to exist in the following locations:
     *        <ul>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.xliff</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.format</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.skeleton</li>
     *        </ul>
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @param boundary (Ignored. The boundary on which to segment translation 
     *        units (e.g., on paragraph or sentence boundaries) is meaningful
     *        only for importers--converters that generate XLIFF from documents.)
     * @param generatedFileName If non-null, the converter will write the name
     *        of the file (without parent directories) to which the generated
     *        output file was written.
     * @param skipList (Not used by this converter.)
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */
    public ConversionStatus convert(ConversionMode mode,
            Locale locale,
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
        return this.convert(mode, locale, phaseName, maxPhase, nativeEncoding,
            nativeFileType, nativeFileName, baseDir, notifier, boundary, 
            generatedFileName);
    }

    /** 
     * Convert one set of targets (in the translation units of an XLIFF file) to
     * the original Maker Interchange Format.
     * @param mode The mode of conversion (must be FROM_XLIFF)
     * @param locale The Java Locale of the XLIFF targets to use in constructing
     *        the MIF document. The language is used in constructing a unique
     *        name for the output file. For example, if es_ES is specified,
     *        the output file name contains the "es_ES" in its name.
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
     * @param nativeEncoding The encoding of the native document. (For the time
     *        being, this is ignored and assumed to be X-MIF-FRAMEROMAN.)
     *        If the language is Japanese, output will be in EUC Japanese.
     * @param nativeFileType This parameter is ignored. (The MIF filetype was
     *        stored in the original MIF file was imported.)
     * @param nativeFileName The name of the original MIF file. This is used as 
     *        the basis for the names of the XLIFF, format and skeleton files
     *        in the baseDir directory (specified in the next parameter), from
     *        which the exported MIF file will be constructed. Those three
     *        input files will be assumed to exist in the following locations:
     *        <ul>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.xliff</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.format</li>
     *        <li>&lt;baseDir&gt;/&lt;nativeFileName&gt;.skeleton</li>
     *        </ul>
     * @param baseDir The directory (in the file system) from which input files
     *        (XLIFF, skeleton and format files) will be read, and to which the 
     *        output file will be written.
     * @param notifier Instance of a class that implements the Notifier
     *        interface (to send notifications in case of conversion error).
     * @return Indicator of the status of the conversion.
     * @throws file2xliff4j.ConversionException
     *         If a conversion exception is encountered.
     */
    @Deprecated
    public ConversionStatus convert(ConversionMode mode,
            Locale locale,
            String phaseName,
            int maxPhase,
            Charset nativeEncoding,
            FileType nativeFileType,
            String nativeFileName,
            String baseDir,
            Notifier notifier) throws ConversionException {
        return this.convert(mode, locale, phaseName, maxPhase, nativeEncoding,
            nativeFileType, nativeFileName, baseDir, notifier, null, null);
    }
    
    /**
     * Passed a line from the skeleton file, expand all &lt;lt:tu id='?'/> tags
     * by replacing with them with the actual translation unit (in the appropriate
     * language. Return the expanded string.
     * @param skelLine Represents a line to be expanded.
     */
    private String expandTus(String skelLine) {
        String newString = skelLine;
        String parent = "";
        String cat = "";     // The built-up, con*cat*enated string
        boolean firstTime = true;
        
        while (true) {
            tuMatcher.reset(newString);
            // Find each TU and extract its TU Identifier
            if (tuMatcher.find()) {
                String placeHolder = tuMatcher.group(0); // Complete text of placeholder tag
                String tuOrFormat = tuMatcher.group(1);
                String tuID = tuMatcher.group(2);    // Get the identifier from the tag in skel
                parent = tuMatcher.group(3);  // Is parent Para or String?
                
                if (tuOrFormat.equals("tu")) {
                    String tuText = tuMap.getTu(tuID, null, false, false);   // Get the TU target text

                    String coreTu = TuPreener.getCoreText(tuText);
                    String corePfx = TuPreener.getPrefixText(tuText);
                    String coreSfx = TuPreener.getSuffixText(tuText);

                    if (firstTime && parent.equalsIgnoreCase("Para")) {
                        curIndent++;
                    }

                    String wholeTu = corePfx + coreTu + coreSfx;
                    newString = newString.replace(placeHolder,wholeTu);
                }
                else {
                    System.err.println("MifExporter encountered lt:format placeholder.");
                }
            }
            else {
                // No more matches
                break;                       // Kinda redundant ...
            }
            firstTime = false;
        }
        
///////////////

        if (newString.indexOf("&") > -1) {  // Convert XML entities to FM
            newString = newString.replace("&amp;","&").replace("&gt;","\\>").replace("&lt;","<").replace("&apos;","\\q").replace("&quot;","\"");
        }
        // Expand bx, ex, x, ... in prefix, core, and suffix
        if (parent.equalsIgnoreCase("Para")) {
            newString = formatParaString(newString);
        }
        else {
            newString = resolveFormatCodes(newString, "core", parent);
        }
        
///////////////        
        if (parent.equalsIgnoreCase("String")) {
            newString += "\r\n";
        }
            
        return newString;
    }
    
    /** 
     * Passed the text of a translation unit that is part of a
     * String within a ParaLine within a Para, format and return the string as
     * well-formed MIF. Expand any bx/ex/x codes along the way. Things that 
     * this method do include:
     * <ul>
     * <li>Map extended ASCII characters (and others that are unique to
     *     FrameMaker) back to the FrameMaker characters of the form
     *     \xab (where "ab" represents a hex character, followed by a
     *     required space</li>
     * <li>Whenever a bx/ex/x tag is encountered in the string, expand it
     *     back to the original FrameMaker codes (ending ParaLine/String
     *     segments as required).</li>
     * </ul>
     * 
     * @param coreText The text of the Translation Unit that needs to have
     *               its format codes resolved
     * @return The expanded/formatted core TU, with bx/ex/x codes replaced as
     *         appropriate.
     */
    private String formatParaString(String coreText) {
        if (this.format == null) {
            System.err.println("MifExporter.formatParaString: Cannot access "
                    + "format file to format Paragraph string.");
            // Without a format class, we can't resolve the codes
            return coreText;      // Just return
        }

        // If no text, return 
        if (coreText == null) {
            return "";       // Don't return null--return a zero-length string
        }
        
        // If no meaningful text, there can't be any tags (maybe just some
        // whitespace, which we want to preserve).
        if (coreText.trim().length() == 0) {
            return coreText;   // Nothing ... or just whitespace.
        }
        
        // If string contains no bx, ex or x tags, just return the original string.
        if ((coreText.indexOf("<bx ") == -1) && (coreText.indexOf("<ex ") == -1)
            && (coreText.indexOf("<x ") == -1)) {
            return (indent(curIndent) + "<String `" + coreText + "'>\r\n");
        }
        
        boolean foundEndStr = false;           // Found > that signals end of <String
        boolean moreCodes = true;              // Be optimistic
        String beforeCode = "";                // Text before first bx/ex/x
        String curCode = "";                   // First (next) bx/ex/x tag
        String formatID = "";                  // The id attr val of bx, ex, x
        String tail = coreText;                // What's left
        boolean needFontReset = false;  
//        boolean isFirstString = true;          // Toggle this after we output the first string
        boolean stringOpened = false;          // We didn't just open a string
        
//        // Pattern for matching bx, ex and x tags within format strings
//        String pattStr = "^(.*?)(<([be]?x)\\b.*?\\bid=['\"]([^'\"]+)['\"](.*?)>)(.*)$";
//        Pattern formatPat = Pattern.compile(pattStr,Pattern.DOTALL);
//        Matcher formatM = formatPat.matcher("");
        
        StringBuilder tempStr = new StringBuilder(); // Where we put the new stuff
        boolean firstTime = true;   // This is the first time through
        
        while (tail.length() > 0) {
            // Find each format code and extract its id
            formatMatcher2.reset(tail);
            if (formatMatcher2.find()) {

                // This is leading code before any tags:
                beforeCode = formatMatcher2.group(1);  // Up to the first bx/ex/x
                
                if (stringOpened) {     // Last iteration
                    tempStr.append(" `" + beforeCode + "'>\r\n");
                }
                
                // 2007/06/22 WLW Eliminate spurious <String `  '> at beginning
                // of <Para by checking for trimmed length of characters before
                // code--on the first iteration only.
//                if ((beforeCode.length() > 0) && (!stringOpened)) {
                if (firstTime) {
                    if ((beforeCode.trim().length() > 0) && (!stringOpened)) {
                        // Open a string (and close it)
                        tempStr.append(indent(curIndent) + "<String `" + beforeCode + "'>\r\n");
                    }
                    firstTime = false;
                }
                else {  // Not the first time--this might be interior characters.
                        // ... so don't trim
                    if ((beforeCode.length() > 0) && (!stringOpened)) {
                        // Open a string (and close it)
                        tempStr.append(indent(curIndent) + "<String `" + beforeCode + "'>\r\n");
                    }
                }
                
                stringOpened = false;
                
                String tagName = formatMatcher2.group(3);
                String cTypeEtc = formatMatcher2.group(5); // Empty with ex tags, else ctype?

                // End a paraLine unless the tag is a bx tag
                if (tagName.charAt(0) != 'b') {
                    if (needFontReset) {
                        tempStr.append(indent(curIndent) + "<Font\r\n");
                        tempStr.append(indent(curIndent+1) + "<FTag `'>\r\n");
                        tempStr.append(indent(curIndent+1) + "<FLocked No>\r\n");
                        tempStr.append(indent(curIndent) + "> # end of Font\r\n");
                        needFontReset = false;
                    }
                }
                else {    // it *is* a bx
                    // Is this a font-related bx?
                    if (cTypeEtc.toLowerCase().contains("italic")
                        || cTypeEtc.toLowerCase().contains("bold")
                        || cTypeEtc.toLowerCase().contains("superscript")
                        || cTypeEtc.toLowerCase().contains("subscript")
                        || cTypeEtc.toLowerCase().contains("font")) {
                        needFontReset = true;
                    }

                }

                curCode = formatMatcher2.group(2);     // The entire next bx/ex/x element

                // formatID is the tag id of the x, bx or ex element in the TU
                formatID = formatMatcher2.group(4);  

                
                // Store the "rest" of the string in tail
                tail = formatMatcher2.group(6);
                        
                // formatText is what this format file entry maps to.
                // (Note: We allow recursion, and will check the format text for 
                // bx, ex and x tags. If they exist, we will expand them.
                String formatText = format.getReplacement(formatID, false);
                
                // Check for recursion.
                if ((formatText.indexOf("<x id=") != -1)
                    || (formatText.indexOf("<bx id=") != -1)
                    || (formatText.indexOf("<ex id=") != -1)) {
                    // If the returned format text in turn includes more bx/ex/x
                    // tags, resolve them ...
                    formatText = resolveFormatCodes(formatText, "core", "Para").trim();
                    // Above will strip off leading spaces.
                }

                if (formatText.trim().endsWith("<String")) {
                    // The format text could be a series of tags recursively expanded
                    // in the above recursion check, the last of which was <String.
                    stringOpened = true;     // We need this for the next iteration.
                }
                
                if (formatText.length() > 0) {  // If successful
                    // If this isn't a String, then try to reconstruct
                    if (formatText.trim().startsWith("<")) {   // Starts with <
                        // Handle the case where the format text is "<Char HardReturn>"
                        if (formatText.trim().equalsIgnoreCase("<Char HardReturn>")) {
                            // If this is in the middle of a string, we need to close
                            // the string before adding the hard return.
                            if (stringOpened) {
                                tempStr.append(">\r\n");
                                stringOpened = false; // .. In case more text follows-- so
                                                      //    we can re-open the string.
                            }
                            // Then add the HardReturn
                            tempStr.append(indent(curIndent) + "<Char HardReturn>\r\n");
                            tempStr.append(indent(curIndent-1) + "> # end of ParaLine\r\n");
                            tempStr.append(indent(curIndent-1) + "<ParaLine\r\n");
                        }
                        else {    // This isn't a HardReturn character
                            tempStr.append(indent(curIndent) + formatText);
                            if ((!formatText.trim().endsWith(">")) && (!formatText.trim().endsWith("<String"))) {
                                curIndent++;
                            }
                            foundEndStr = false;
                        }
                    }
                    else if (formatText.trim().startsWith(">")) {
                        if (formatText.indexOf(" # end of String") == -1) {
                            curIndent--;
                            tempStr.append(indent(curIndent) + formatText);
                            foundEndStr = false;
                        }
                        else {
                            foundEndStr = true;  // This *was* > # end of String
                        }
                    }
                    if ((! /* just */ foundEndStr) && (!formatText.trim().endsWith("<String"))) {
                        tempStr.append("\r\n");
                    }
                }
                else {
                    // Leave the format code unexpanded (Might not be any ...) if end string
                }
            }
            else {
                // We're done--no more format codes
                break;
            }
        }

        if (tail.length() > 0) {
            System.err.println("MifExporter.formatParaString: Supposedly unreachable code"
                    + " visited!");
            tempStr.append(indent(curIndent) + "> # end of ParaLine\r\n");
            tempStr.append(indent(curIndent) + "<ParaLine\r\n");

            if (needFontReset) {
                tempStr.append(indent(curIndent+1) + "<Font\r\n");
                tempStr.append(indent(curIndent+2) + "<FTag `'>\r\n");
                tempStr.append(indent(curIndent+2) + "<FLocked No>\r\n");
                tempStr.append(indent(curIndent+1) + "> # end of Font\r\n");
                needFontReset = false;
            }
            tempStr.append(indent(curIndent+1) + "<String `" + tail + "'>\r\n");
        }
            
        return tempStr.toString();
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
     * Passed a string that might have an opening < preceded by spaces,
     * count how many spaces that < is from the left, and return one more
     * than that number.
     * @param inString The input string to scan for <
     * @return the indent level (plus 1) of the input string
     */
    private int getIndentDepth(String inString) {
        if ((inString == null) || (inString.length() == 0)
            || (inString.indexOf('<') == -1)) {
            return MifExporter.INIT_INDENT; // Use the default indent
        }
        
        int i = 0;
        for (i = 0; i < inString.length(); i++) {
            if ((inString.charAt(i) != ' ')
                && (inString.charAt(i) != '<')) {
                return MifExporter.INIT_INDENT;  // No leading <
            }
            
            if (inString.charAt(i) == '<') {
                return i;
            }
        }
        
        // We've gone all the way through the string without finding a <
        // Return the default INIT_INDENT
        return MifExporter.INIT_INDENT;
    }

//    /**
//     * Passed the name of an XLIFF file, search for the beginning <file> tag
//     * and extract the value of the "original" attribute (which is the
//     * native file name).
//     * @param xliffFileName The name of an xliff file
//     * @return The name of the original (native) file or a zero-length
//     *         string if unable to determine the file name.
//     * @throws file2xliff4j.ConversionException
//     *         if an error is encountered.
//     */
//    private String getNativeFileName(String xliffFileName) 
//            throws ConversionException {
//        String nativeFileName = "";      // Holds what we will return.
//        BufferedReader xliffIn = null;
//        try {
//            xliffIn = new BufferedReader(new InputStreamReader(new FileInputStream(
//                xliffFileName), "UTF8"));
//
//            int fileStart = -1;     // Position of start of <file ... tag
//            int fileEnd = -1;       // Where the opening <file ...> tag ends
//            String line = xliffIn.readLine();   // Read the first XLIFF line
//        
//            // Keep reading until we find a line that contains an opening <file ... tag
//            // ... or until we come to end of file.
//            while ((line != null) && ((fileStart = line.indexOf("<file")) == -1)) {
//                line = xliffIn.readLine();
//            }
//
//            // If we found a <file tag ... then do something with it.
//            if (fileStart > -1) {
//                // Get a copy of the start of the beginning file tag.
//                String fileTag = line.substring(fileStart);
//
//                // Read the entire <file ... > opening tag (up to the closing '>')
//                while ((line != null) && (fileTag.indexOf(">", 4) == -1)) {
//                     line = xliffIn.readLine();
//                     fileTag = fileTag + "\n" + line;
//                }
//                
//                // Look for the original attribute's value
//                Pattern p = Pattern.compile("<file [^>]*?\\boriginal=(['\"])(.+?)\\1",
//                        Pattern.DOTALL);
//                Matcher m = p.matcher(fileTag);  // Look in the fileTag var
//                if (m.find()) {    // Did we find a match?
//                    nativeFileName = m.group(2);
//                }
//            }
//
//            // We're done now.
//            xliffIn.close();     // Close before leaving.
//        }
//        catch (IOException e) {
//            System.err.println("Error reading XLIFF file: " + e.getMessage());
//            throw new ConversionException("Error reading XLIFF file: " 
//                    + e.getMessage());
//        }
//        
//        return nativeFileName;   // If we found it; otherwise ""
//    }
    
    /**
     * Return a String of blanks of the length specified in the numChars argument.
     * @param numChars The number of blank characters to return in the indention
     *        string.
     * @return A string of spaces of length specified in numChars.
     */
    private String indent(int numChars) {
        if (numChars < 1) {
            return "";
        }
        
        char chars[] = new char[numChars]; // This could be a zero-length string
        Arrays.fill(chars, ' ');  // Fill the array with spaces ...
        
        return new String(chars);   //   and return it.
    }

    /** 
     * Passed a translation unit, look for bx/ex tags (beginning and ending)
     * and replace them with their original strings.
     * (Note: As we expand our XLIFF, we will need to look for <x> codes
     * as well).
     * @param tuText The text of the Translation Unit that needs to have
     *               its format codes resolved
     * @param section One of "prefix", "core", "suffix";
     * @param parent Is the parent a String or a Para element? (Strings are easier)
     * @return The expanded TU, with bx/ex codes replaced by their equivalents
     */
    private String resolveFormatCodes(String tuText, String section, String parent) {
        if (this.format == null) {
            // Without a format class, we can't resolve the codes
            return tuText;                     // Just return
        }

        // If no text, return 
        if (tuText == null) {
            return "";       // Don't return null--return a zero-length string'
        }
        
        // If no meaningful text, there can't be any tags (maybe just some
        // whitespace, which we want to preserve).
        if (tuText.trim().length() == 0) {
            return tuText;   // Nothing ... or just whitespace.
        }
        
//        boolean foundParaLine = false;
//        boolean foundStringTag = false;  // Need to remember this
        boolean endOfParaStr = false;    // Just saw end of String within Para
        String trailingChars = tuText;   // Leftovers--starts with whole TU
        boolean moreCodes = true;        // Be optimistic
        StringBuilder tempStr = new StringBuilder();
        StringBuilder newStr = new StringBuilder();
        
        while (moreCodes) {
            // "^(.*?)(<([be]?x)\\b.*?\\bid=['\"]([^'\"]+)['\"].*?>)(.*)$"

            formatMatcher.reset(trailingChars); // Look at the rest of the string
            // Find each format code and extract its id
            if (formatMatcher.find()) {
                // Look for next bx/ex/x tag in tuText
                
                String leadingChars = formatMatcher.group(1);

                // Handle the case where some leading characters outside the core need
                // to be enclosed in Paraline/String. (Things like tabs, etc.)
                if ((leadingChars.length() > 0) && (!section.equalsIgnoreCase("core"))
                    && parent.equalsIgnoreCase("Para")) {
                    leadingChars = /* indent(curIndent) + "<ParaLine\r\n" 
                            + */ indent(curIndent+1) + "<String `"
                            + leadingChars + "'" /*
                            + indent(curIndent) + "> # end of ParaLine\r\n" */;
                    
                    // Should this be moved outside if (after the end of this
                    // if)?
                    newStr.append(leadingChars);
                }
                
                // wholeTag matches an x, bx or ex element in the TU
                String wholeTag = formatMatcher.group(2);  
                
                // formatID is the tag id of the x, bx or ex element in the TU
                String formatID = formatMatcher.group(3);  
                
                trailingChars = formatMatcher.group(4);  // The "rest"

                // formatText is what this format file entry maps to.
                // (Note: We allow a second level of recursion. We will check the
                // format text for bx, ex and x tags. If they exist, we will
                // expand them.
                String formatText = format.getReplacement(formatID, false);

                // Note if this is a ParaLine tag.
//                if (formatText.startsWith("<ParaLine")) {
//                    foundParaLine = true;
//                }
                
                if ((formatText.indexOf("<x id=") != -1)
                    || (formatText.indexOf("<bx id=") != -1)
                    || (formatText.indexOf("<ex id=") != -1)) {
                    // If the returned format text in turn includes more bx/ex/x
                    // tags, resolve them ...
                    formatText = resolveFormatCodes(formatText, section, parent);
//                    formatText = resolveFormatCodes(tuText, section, parent);
                }
                
                if (formatText.length() > 0) {  // If successful
                    // If this isn't a String, then try to reconstruct'
                    if (!formatText.startsWith("<String")) {
                        if (formatText.startsWith("<")) {
                            tempStr.setLength(0);
                            tempStr.append(indent(curIndent) + formatText);
                            formatText = tempStr.toString();
                            if (! formatText.endsWith(">")) {
                               curIndent++;
                            }
                        }
                        else if (formatText.startsWith(">")) {
                            if (formatText.indexOf(" # end of String") != -1) {
                                endOfParaStr = true;
                            }
                            else {
                                curIndent--;
                                if (curIndent < 0) { curIndent = 0;} 
                                tempStr.setLength(0);
                                tempStr.append(indent(curIndent) + formatText);
                                formatText = tempStr.toString();
                            }
                        }
                        if (! endOfParaStr) {
                            formatText += "\r\n";
                            newStr.append(formatText);
                        }
                        else {   // End of ParaStr (aready in Skeleton; don't append
                            //  newStr = newStr.replaceFirst(wholeTag, "");
                        }
                        endOfParaStr = false;
                    }
                    else {
                        // This *is* a String (x-mif-String aka <String `whatever'>)
//                        foundStringTag = true;
                        if (parent.equalsIgnoreCase("Para")) {
                            // We are being called recursively by the paragraph formatting
                            // method to expand a single bx/ex/x tag that maps to another
                            // level of tags. Append the String tag!!
                            newStr.append(indent(curIndent) + formatText);
                        }
                        // Otherwise don't 
                    }
                }
                else {
                    // Leave the format code unexpanded (not good!)
                }
            }
            else {
                // We're done--no more format codes
                moreCodes = false;
            }
        }

        // Handle the case where some trailing characters in prefix or suffix need to
        // be enclosed in a ParaLine/String
        if (trailingChars.length() > 0) {
            if (parent.equalsIgnoreCase("Para")) {
                trailingChars = /* indent(curIndent) + "<ParaLine\n" 
                    + */ indent(curIndent+1) + "<String `"
                    + trailingChars + "'" /*
                    + indent(curIndent) + "> # end of ParaLine\n" */ ;

                // Should this be moved outside if (after the end of this
                // if)?
                newStr.append(trailingChars);
            }
            else {     // Parent is String
                newStr.append(trailingChars);
            }
        }
        
        
        return newStr.toString();       // The TU with all format codes expanded.
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
