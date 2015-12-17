/*
 * WinRcImporter.java
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
 * Utility class representing an entry in a Windows RC file STRINGTABLE.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class StringTableEntry {
    private String text = null;
    
    private UUID tuID = UUID.randomUUID();  // UUID to identify this TU string
    
    public StringTableEntry(String strText) {
        this.text = strText;
    }
    
    /** Return TU entry's Identifier
     * @return the Translation Unit Identifier of this TU
     */
    public UUID getTuID() { return tuID;}
       
    /** Return the Tu text as a String
     * @return the TU's text
     */
    public String getTuText() { 
        if (this.text != null) {
            return this.text;
        }
        else {
            return "";
        }
    }
}

/**
 * Import a Microsoft Windows rc (resource) file.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class WinRcImporter implements Converter {

    private int curIndent = 0;
    
    /**
     * Create a Microsoft Windows rc file importer
     */
    public WinRcImporter() {
    }
            
    // Matcher for rc lines with keywords that are followed immediately by 
    // translatable text strings (exception: STRINGTABLE, handled separately):
    private Matcher rcMatcher 
        = Pattern.compile("^(\\s*)"
          + "(AUTO3STATE|AUTOCHECKBOX|AUTORADIOBUTTON|CAPTION|CHECKBOX|CONTROL|"
          + "CTEXT|DEFPUSHBUTTON|GROUPBOX|LTEXT|MENUITEM|POPUP|PUSHBOX|PUSHBUTTON|"
          + "RADIOBUTTON|RTEXT|SCROLLBAR|STATE3)"
          + "(\\s+)(L)?\"((?:[\"]{2}|[^\"])+)\"([,\\s].*)?$",    // "" means " within text.
            Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher("");
    
    // Note: VERSIONINFO's StringFileInfo Block has loads of language and charset
    //       info. Its VarFileInfo Block does as well.
    // Note: We need to do something about STRINGTABLE and VERSIONINFO

    /**
     * Convert a Microsoft Windoes rc file to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the translatable strings in the rc file. 
     * @param nativeEncoding The encoding of the rc file. If this parameter is
     *        null, the importer will attempt to determine the encoding from the
     *        rc file itself.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be WINRC.)
     * @param nativeFileName The name of the Windows rc file.
     * @param baseDir The directory that contains the input rc file--from 
     *        which we will read the input file. This is also the directory to 
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
            throw new ConversionException("Microsoft Windows RC Importer supports"
                    + " only conversions of RC files to XLIFF.");
        }

        // Make sure the name of the RC file was specified.
        if (nativeFileName == null || nativeFileName.trim().length() == 0) {
            System.err.println("File name of input RC file omitted. Cannot proceed.");
            throw new ConversionException("File name of input RC file omitted. "
                    + "Cannot proceed.");
        }

        // Construct the names of the files we will be working with.
        String inRc = baseDir + File.separator + nativeFileName;
        String outXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String outSkel  = baseDir + File.separator + nativeFileName 
                + Converter.skeletonSuffix;
        
        File rcFile = new File(inRc);
        if (!rcFile.exists()) {
            System.err.println("Input Windows RC file does not exist.");
            throw new ConversionException("Input Windows RC file does not exist.");
        }

        // Determine encoding used in PO template:
        Charset rcEncoding = readRcEncoding(baseDir + File.separator 
            + nativeFileName);

        /**
         * MSDN indicates that--if not specified otherwise--
         * <blockquote>By default, the characters listed between the double 
         * quotation marks are ANSI characters, and escape sequences are 
         * interpreted as byte escape sequences. If the string is preceded by
         * the "L" prefix, the string is a wide-character string and escape 
         * sequences are interpreted as 2-byte escape sequences that specify 
         * Unicode characters.</blockquote> 
         *    --http://msdn2.microsoft.com/en-us/library/aa380902.aspx
         */
        if (rcEncoding == null) {
            rcEncoding = Charset.forName("windows-1252");
        }
        
        // Output the XLIFF prolog
        try {
            // Create input reader and output writers
            BufferedReader rcRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(inRc), rcEncoding));
            
            // We'll use UTF-8 for the XLIFF and skeleton ...
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
                + "' source-language='" + language.toString() + "' datatype='winres'>\r\n");
            xliffWtr.write(indent() + "<header lt:segtype='sentence'");
            xliffWtr.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
            xliffWtr.flush();
        
            String rcLine = null;             // Holds the next line read from PO template
//            String source = "";               // Source language text accumulated so far
            
            // Read through the PO Template line by line
            while ((rcLine = rcRdr.readLine()) != null) {
                // Is this an RC line that begins with a keyword and a token 
                // (immediately following) that consists of translatable text?
                if (rcMatcher.reset(rcLine).find()) {
                    String prefix   = rcMatcher.group(1);
                    String keyword  = rcMatcher.group(2);
                    String filler   = rcMatcher.group(3);
                    String bigL     = rcMatcher.group(4);  // Possible unicode indicator
                    String text     = rcMatcher.group(5);
//                    String closeQuote = rcMatcher.group(5);
                    String postText = rcMatcher.group(6);
                    if (text == null || text.trim().length() == 0) { 
                        // No text for segment. Write line unchanged to skeleton
                        skelWtr.write(rcLine + "\r\n");
                    }
                    else {   
                        
                        UUID curTuID = UUID.randomUUID(); // ID for this TU
                        // It seems to be real. Write a trans-unit element to
                        // the XLIFF:
                        xliffWtr.write(indent('0')
                            + "<trans-unit id='" + curTuID + "' "
                            + "lt:paraID='" + curTuID + "'>\r\n");
                        
                        // If the text contains any \x escaped characters, 
                        // resolve them.
                        if (text.contains("\\x")) {
                            boolean hasLPrefix = (bigL != null && bigL.equals("L")) ? true : false;
                            text = getUnicodeString(text, hasLPrefix);
                        }
                        
                        text = TuStrings.escapeTuString(text);
                        
                        // If the text contains double double quotes (i.e. "")
                        // change them to &quot;. (In Win rc files, if a double
                        // quotation mark is required in the text, "you must
                        // include the double quotation mark twice."
                        if (text.contains("&quot;&quot;")) {
                            // This non-regex version of replace replaces all
                            // instances of "" with &quot;
                            text = text.replace("&quot;&quot;","&quot;");
                        }
                        
                        // Open the source element
                        String markedTu = TuPreener.markCoreTu(text);
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
                        
                        // Write a placeholder to the skeleton.
                        skelWtr.write(prefix + keyword + filler + "<lTLt:tu id='" 
                            + curTuID.toString() + "'/>" 
                            + ((postText == null) ? "" : postText) + "\r\n");
                    }
                }
                
                // Get the strings from a STRINGTABLE.
                else if (rcLine.toUpperCase().contains("STRINGTABLE")) {
                    StringTableEntry [] stEntry = getTableStrings(rcLine, rcRdr, skelWtr);
                    for (int i = 0; i < stEntry.length; i++) {
                        UUID tuID = stEntry[i].getTuID();
                        xliffWtr.write(indent('0')
                            + "<trans-unit id='" + tuID + "' "
                            + "lt:paraID='" + tuID + "'>\r\n");
                        // Open the source element
                        String curText = stEntry[i].getTuText();

                        curText = TuStrings.escapeTuString(curText);
                        // If the text contains double double quotes (i.e. "")
                        // change them to &quot;. (In Win rc files, if a double
                        // quotation mark is required in the text, "you must
                        // include the double quotation mark twice."
                        if (curText.contains("&quot;&quot;")) {
                            // This non-regex version of replace replaces all
                            // instances of "" with &quot;
                            curText = curText.replace("&quot;&quot;","&quot;");
                        }
                        
                        String markedTu = TuPreener.markCoreTu(curText);
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
                    }
                }

                else {  // A comment or some other type of line.
                    // Just print it to the skeleton:
                    skelWtr.write(rcLine + "\r\n");
                }
                skelWtr.flush();
            }   // while
        
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
                notice = "Windows RC importer didn't create a skeleton file named " + outSkel;
                System.err.println(notice);
                notifier.sendNotification("0001", "WinRcImporter", Notifier.ERROR, notice);
            }
            
            // Does the output XLIFF file exist?
            notice = "";
            File xliffFile = new File(outXliff);
            if (!xliffFile.exists()) {
                notice = "Windows RC importer didn't create an XLIFF file named " + outXliff;
                System.err.println(notice);
                notifier.sendNotification("0002", "WinRcImporter", Notifier.ERROR, notice);
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
                        notifier.sendNotification("0003", "WinRcImporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "WinRcImporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "WinRcImporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    notice = "The validator of XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0006", "WinRcImporter", Notifier.ERROR, notice);
                }
            }
        }        

        if (generatedFileName != null) {
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
* Convert a Microsoft Windoes rc file to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the translatable strings in the rc file. 
     * @param nativeEncoding The encoding of the rc file. If this parameter is
     *        null, the importer will attempt to determine the encoding from the
     *        rc file itself.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be WINRC.)
     * @param nativeFileName The name of the Windows rc file.
     * @param baseDir The directory that contains the input rc file--from 
     *        which we will read the input file. This is also the directory to 
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
* Convert a Microsoft Windoes rc file to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the translatable strings in the rc file. 
     * @param nativeEncoding The encoding of the rc file. If this parameter is
     *        null, the importer will attempt to determine the encoding from the
     *        rc file itself.
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be WINRC.)
     * @param nativeFileName The name of the Windows rc file.
     * @param baseDir The directory that contains the input rc file--from 
     *        which we will read the input file. This is also the directory to 
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

    // Matcher for a STRINGTABLE with entries in curlies
    private Matcher stCurlyMatcher 
        = Pattern.compile("^(.*?[{])(.*?)([}].*)", Pattern.DOTALL).matcher("");
    
    // Matcher for a STRINGTABLE with entries in a BEGIN/END block
    // Note: Example STRINGTABLE at http://msdn2.microsoft.com/en-us/library/aa381050.aspx
    // Shows BEGIN immediately followed (with no intervening whitespace whatever) 
    // by IDS_CHINESESTRING ...
    private Matcher stBeginEndMatcher
        = Pattern.compile("^(.*?\\bBEGIN)(.*?)(\\bEND\\b.*)", Pattern.DOTALL).matcher("");
    
    // Matcher for one string entry (the "next" string) in the string table:
    private Matcher strEntryMatcher
        = Pattern.compile("^(\\s*\\S*[,\\s]+?)(L)?\"([^\"]*)\"(.*)$",Pattern.DOTALL).matcher("");
    
    // A string continuation matcher:
    private Matcher continuationMatcher
        = Pattern.compile("\\\\\\r?\\n", Pattern.DOTALL).matcher("");
    
    /**
     * Return an array of STRINGTABLE entries.
     * @param rcLine The first line of the STRINGTABLE entry in the RC file
     * @param rcRdr The BufferedReader from which to read additional lines of
     *              the STRINGTABLE, if necessary.
     * @param skelWtr A BufferedWriter to which to write the skeleton lines
     *              that correspond to this STRINGTABLE.
     * @return An array of StringTableEntries
     * @throws IOException If caused during an I/O operation.
     */
    private StringTableEntry[] getTableStrings(String rcLine, BufferedReader rcRdr,
            BufferedWriter skelWtr) throws IOException {
        StringBuilder rawEntry = new StringBuilder();
        rawEntry.append(rcLine);
        boolean curliesMatch = false;   // { .... }
        boolean blockMatches = false;   // BEGIN ... END block
        
        // While neither:
        //   The stringtable curly matcher matches NOR
        //   The stringtable beginend matcher matches ...:
        while(!((curliesMatch = stCurlyMatcher.reset(rawEntry).find())
            || (blockMatches = stBeginEndMatcher.reset(rawEntry).find()))) {
            // Read another line and add it to the rawEntry:
            String nextLine = null;
            try {
                nextLine = rcRdr.readLine();
                if (nextLine == null) {
                    // This is bad; we don't have a complete STRINGTABLE entry.
                    return new StringTableEntry[0];  // Return an empty array.
                }
                else {
                    rawEntry.append("\r\n" + nextLine);
                }
            }
            catch (IOException e) {
                return new StringTableEntry[0];
            }
        }
        
        // If we made it this far, we should have a complete STRINGTABLE in rawEntry
        
        String preStrings  = "";
        String theStrings  = "";
        String postStrings = "";
        // If this one uses curlies, then work with the information in the Matcher
        if (curliesMatch) {
            preStrings  = stCurlyMatcher.group(1);
            theStrings  = stCurlyMatcher.group(2);
            postStrings = stCurlyMatcher.group(3);
        }
        else { // blockMatches, I suppose
            preStrings  = stBeginEndMatcher.group(1);
            theStrings  = stBeginEndMatcher.group(2);
            postStrings = stBeginEndMatcher.group(3);
        }
        
        // (Note: We need to keep the preStrings and postStrings intact, so that
        // we can write them out to the skeleton.)
        ArrayList<StringTableEntry> stArrayList = new ArrayList<StringTableEntry>();
                
        // Now let's charge through the strings:
        StringBuilder skelSection = new StringBuilder();
        skelSection.append(preStrings);   // Add the STRINGBUILDER before the strings

//      private Matcher strEntryMatcher
//          = Pattern.compile("^(\\s*\\S*[,\\s]+?)(L)?\"([^\"]*)\"(.*)$",Pattern.DOTALL).matcher("");
        
        while (strEntryMatcher.reset(theStrings).find()) {
            skelSection.append(strEntryMatcher.group(1));  // Append up to the string
            String bigL = strEntryMatcher.group(2);        // If not null, then "L" signals unicode
            String thisString = strEntryMatcher.group(3);  // If L above, then \xaaaa ... ("a" hex digits)
            theStrings = strEntryMatcher.group(4);         // The "rest" (for next iteration)
            
            // Handle the case where the actual string ("thisString)" has C-style
            // line continuation backslashes (\) at the end of the line, something
            // like \\\r\n. If present, remove those, making the string single
            // continuous line.
            if (thisString.contains("\\")) {
                thisString = continuationMatcher.reset(thisString).replaceAll("");
            }
            
            // Convert the "thisString" variable to an appropriate UTF string (if it
            // consists of x-escaped characters). Then store the string in the stArrayList
            // Then write a TU placeholder in the skelSection ...
            boolean hasLPrefix = (bigL != null && bigL.equals("L")) ? true : false;
            
            StringTableEntry ste = null;
            // If the string contains an \x escape sequence, decode it.
            if (thisString.contains("\\x")) {
                ste = new StringTableEntry(getUnicodeString(thisString, hasLPrefix));
            }
            else {
                ste = new StringTableEntry(thisString);
            }
            
            // Add this string table entry to the list of string table entries:
            stArrayList.add(ste);
            
            // Add rest of skeleton line:
            // Skip the L for now (we will add it on export, if necessary.
//            skelSection.append("<lTLt:tu id='" + ste.getTuID().toString() + "'/>\r\n");
            skelSection.append("<lTLt:tu id='" + ste.getTuID().toString() + "'/>");
        }
        
        
        // Add what's left to the skeleton section
        skelSection.append("\r\n" + postStrings + "\r\n");
        
        // Then write out the skelSection to the skeleton
        skelWtr.write(skelSection.toString());
        
        // Convert the ArrayList to an array of StringTableEntry's and return it
        StringTableEntry[] retSte = new StringTableEntry[stArrayList.size()];
        return stArrayList.toArray(retSte);
    }
    
    /** 
     * Return the file type that this converter handles. (For importers, this
     * means the file type that it imports <i>to</i> XLIFF; for exporters, it
     * is the file type that ie exports to (from XLIFF).
     * @return the Windows RC type.
     */
    public FileType getFileType() {
        return FileType.WINRC;
    }

    // Matcher to capture Unicode chracters consisting of two hex digits
    private Matcher uCodeMatcher2 
        = Pattern.compile("^(.*?)(\\\\x([0-9a-fA-F]{2}))(.*)$",Pattern.DOTALL).matcher("");

    // Matcher to capture Unicode chracters consisting of four hex digits
    private Matcher uCodeMatcher4 
        = Pattern.compile("^(.*?)(\\\\x([0-9a-fA-F]{4}))(.*)$",Pattern.DOTALL).matcher("");
    
    /**
     * Passed a string that includes unicode characters specified by 2 or 4
     * hexadecimal characters (introduced by \x), convert the string to UTF-8. 
     * Two kinds of strings can be passed:
     * <ol>
     * <li>If the string is preceded (in the rc file) by an uppercase "L",
     *     the x-escaped characters consist of 4 hex-digits. (The string
     *     can also include non-x-escaped ASCII characters.)</li>
     * <li>If the string is not preceded by "L", the x-escaped characters consist
     *     of 2 hexadecimal digits.
     * </ol>
     * @param xEncodedStr The string encoded as something like
     *        \x0421\x043f\x0440\x0430\x0432\x043a\x0430
     * @param hasLPrefix Indication of whether the string has an L prefix
     * @return the input string encoded as UTF-8.
     */
    private String getUnicodeString(String xEncodedStr, boolean hasLPrefix) {
        // If input string is null, return a zero-length string
        if (xEncodedStr == null) {
            return "";
        }
        
        // If the string contains only white space (or nothing) , return that 
        // same white space.
        if (xEncodedStr.trim().length() == 0) {
            return xEncodedStr;
        }
        
        StringBuilder retString = new StringBuilder();
        
        // Now process each x-encoded character, one at a time, adding it to
        // the return string builder.
        String tail = xEncodedStr;
        Matcher curMatcher = null;
        if (hasLPrefix) {
            // If string preceded by L, unicode chars are \x1234 (where 1, 2, 3,
            // and 4 are hex digits).
            curMatcher = uCodeMatcher4;
        }
        else {
            // Otherwise, unicode chars are \x12 (where 1 and 2 are hex digits).
            curMatcher = uCodeMatcher2;
        }
        while (curMatcher.reset(tail).find()) {
            String nonEscPfx = curMatcher.group(1);
            String curXChar = curMatcher.group(3);   // The 2 or 4 hex digits
            tail = curMatcher.group(4);              // The rest of the string
            
            // If non-x-escaped prefix chars, append them first
            if (nonEscPfx != null && nonEscPfx.length() > 0) {
                retString.append(nonEscPfx);
            }
            // Then the hex characters.
            retString.append((char)Integer.parseInt(curXChar, 16));
        }
        
        // If there are non x-escaped characters in the tail, add them to
        // the return string.
        if (tail != null && tail.length() > 0) {
            retString.append(tail);
        }

        // Now return the return string.
        return retString.toString();    // Good. Return the converted string
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
     * Passed the name of a Windows RC file, try to determine its
     * encoding. 
     * @param rcFileName The name of a Windows RC file--fully qualified
     * @return The encoding the file uses (or null if not apparent).
     * @throws file2xliff4j.ConversionException
     *         if an error is encountered.
     */
    public static Charset readRcEncoding(String rcFileName) 
            throws ConversionException {
        // Return null for now.
//        Charset encoding = null;      // Holds what we will return.
//        
//        // Pattern for the content-type header we are interested in.
//        Matcher charsetMatcher = Pattern.compile("Content-Type:\\s+text/plain;\\s+charset=([-_a-zA-Z0-9]+)",
//            Pattern.CASE_INSENSITIVE | Pattern.DOTALL ).matcher("");
//        
//        BufferedReader poIn = null;
//        try {
//            poIn = new BufferedReader(new InputStreamReader(new FileInputStream(
//                poFileName)));
//            String line = "";
//            StringBuilder head = new StringBuilder(); // Buffer to hold input lines
//
//            // Read until we find the first line that begins "msgstr "
//            while ((line = poIn.readLine()) != null) {
//                if (line.startsWith("msgstr ")) {
//                    break;    // We found the first msgstr line
//                }
//            }
//
//            // If we didn't find the first msgstr line, there must be no
//            // charset indication:
//            if (line == null) {
//                return encoding;    // null
//            }
//
//            // See if character set is specified in the initial msgstr string:
//            charsetMatcher.reset(line);
//            if (charsetMatcher.find()) {
//                // If it is, return it to the caller.
//                String charsetStr = charsetMatcher.group(1);
//                encoding = Charset.forName(charsetStr);
//                return encoding;
//            }
//            
//            // Otherwise, keep looking through immediately following lines
//            // consisting of quoted strings to see if the character set is found.
//            boolean endOfCat = false;    // Not found end of string concatenation ... yet
//            while (!endOfCat) {
//                line = poIn.readLine();
//                if (line == null || line.trim().length() == 0  // Empty line
//                    || line.indexOf("\"") == -1) {            // Or no quote mark
//                    // No more input
//                    return encoding;    // null
//                }
//                else if (charsetMatcher.reset(line).find()) {
//                    String charsetStr = charsetMatcher.group(1);
//                    if (!charsetStr.equalsIgnoreCase("CHARSET")) {
//                        encoding = Charset.forName(charsetStr);
//                        return encoding;
//                    }
//                    else {  // Default charset name was left unchanged
//                        return null;   // We don't know the encoding
//                    }
//                }
//                // Else keep reading ...
//            }
//        }
//        // If anything goes wrong, return a null (unknown encoding).
//        catch (Exception e) {
//            return null;
//        }

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
