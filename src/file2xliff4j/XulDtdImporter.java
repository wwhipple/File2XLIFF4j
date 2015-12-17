/*
 * XulDtdImporter.java
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

import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * Import to XLIFF a Document Type Definition (DTD) file that declares entities 
 * for use in XML User Interface Language (XUL) localization. 
 * <p>See <a href="http://developer.mozilla.org/en/docs/XUL_Tutorial:Localization">XUL 
 * Tutorial:Localization</a> for more information about the DTD's this importer
 * handles.
 * <p>On Linux, the DTDs this converter converts are located in a jar file
 * located at (for example):
 * <pre><tt>
 * /usr/lib[64]/firefox-1.5.0.7/extensions/langpack-<i>ll</i>-<i>CC</i>@firefox.mozilla.org/chrome/<i>ll-CC</i>.jar
 * </tt></pre>
 * where <i>ll-CC</i> represents an ISO language code.
 * <p>Within the <i>ll-CC</i>.jar file, DTDs are located in the about/browser
 * directory.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XulDtdImporter implements Converter {

    private int curIndent = 0;
    /**
     * Create an XulDtdImporter 
     */
    public XulDtdImporter() {
    }

    // Matcher to find/capture information about a DTD ENTITY. (Might not be
    // complete on one line.)
    private Matcher entityMatcher 
        = Pattern.compile("^(\\s*<!ENTITY\\s+\\S+\\s+(['\"]))(.*?)(\\2.*)?$").matcher("");
    
    // Matcher used for the last line of an ENTITY that spans multiple lines.
    private Matcher endEntityMatcher
        = Pattern.compile("^(.*)['\"]\\s*>\\s*$").matcher("");
    
    /**
     * Extract the entity declarations from a DTD, generating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (Standard UTF-8 is assumed.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be XULDTD.)
     * @param nativeFileName The name of the input properties file. 
     * @param baseDir The directory that contains the input properties file--from 
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
     *        property string becomes one segment in the XLIFF.)
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
            throw new ConversionException("DTD Importer supports only conversions"
                    + " of XML DTDs files to XLIFF.");
        }

        // Make sure the nativeFileName was specified.
        if (nativeFileName == null || nativeFileName.trim().length() == 0) {
            System.err.println("Input DTD file name omitted. Cannot proceed.");
            throw new ConversionException("Input DTD file name omitted. Cannot proceed.");
        }

        // Construct the names of the files we will be working with.
        String inDtd = baseDir + File.separator + nativeFileName;
        String outXliff = baseDir + File.separator + nativeFileName 
                + Converter.xliffSuffix;
        String outSkel  = baseDir + File.separator + nativeFileName 
                + Converter.skeletonSuffix;
        
        File dtdFile = new File(inDtd);
        if (!dtdFile.exists()) {
            System.err.println("Input DTD file does not exist.");
            throw new ConversionException("Input DTD file does not exist.");
        }

        // Output the XLIFF prolog
        try {
            // Create input reader and output writers
            BufferedReader dtdRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(inDtd), Charset.forName("UTF-8")));
            
            BufferedWriter xliffWtr  = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outXliff), "UTF8"));

            BufferedWriter skelWtr  = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outSkel), "UTF8"));
            
            // Write the "prolog" of the XLIFF file
            xliffWtr.write(Converter.xmlDeclaration);
            xliffWtr.write(Converter.startXliff);
            xliffWtr.write(indent() + "<file original='" 
                + nativeFileName.replace("&", "&amp;").replace("<",
                    "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                + "' source-language='" + language.toString() + "' datatype='xmldtd'>\r\n");
            xliffWtr.write(indent() + "<header lt:segtype='sentence'");
            xliffWtr.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
            xliffWtr.flush();
        
            // Now read every line in the input DTD, looking for lines with
            // ENTITY declarations
            String dtdLine;
            while ((dtdLine = dtdRdr.readLine()) != null) {
                entityMatcher.reset(dtdLine);
                // If this line has an entity declaration ...
                if (entityMatcher.find()) {
                    String prefix = entityMatcher.group(1);
                    String quoteStr = entityMatcher.group(2);  // Single or double quote?
                    String entityVal = entityMatcher.group(3);
                    String suffix = entityMatcher.group(4);
                    // It is possible that the text of the ENTITY might span
                    // multiple lines (Example: aboutDialog.dtd's copyrightText).
                    // We have to handle that case.
                    if (suffix == null || suffix.trim().length() == 0
                        || !suffix.trim().endsWith(">")) {
                        // This spills onto another line. Keep reading lines until
                        // we find the end of the entity
                        boolean foundEnd = false;
                        String nextLine;
                        entityVal = entityVal.trim();
                        while ((!foundEnd) && (nextLine = dtdRdr.readLine()) != null) {
                            endEntityMatcher.reset(nextLine);
                            if (endEntityMatcher.find()) {
                                foundEnd = true;
                                String continuation = endEntityMatcher.group(1);
                                entityVal += (" " + continuation.trim());
                            }
                            else {
                                // This isn't the last line; append it to what we have
                                // accumulated already.
                                entityVal += (" " + nextLine.trim()); 
                            }
                        }
                    }
                    
                    
                    UUID curTuID = UUID.randomUUID();
                    xliffWtr.write(indent('0')
                        + "<trans-unit id='" + curTuID + "' "
                        + "lt:paraID='" + curTuID + "'>\r\n");
                    // Open the source element
                    xliffWtr.write(indent('+') + "<source xml:lang='" 
                        + language.toString() + "'><mrk mtype='x-coretext'>"
                        + entityVal.replace("&", "&amp;").replace("<", "&lt;").replace(">", 
                            "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                        + "</mrk></source>\r\n");

                    // ... and the trans-unit end tag
                    xliffWtr.write(indent('-') + "</trans-unit>\r\n");
                    xliffWtr.flush();
                    
                    // Write the ENTITY (substituting a TU ID placeholder for
                    // the value) to the skeleton
                    // NOTE: The curTuID is *not* surrounded by quotes. (After
                    // all, the skeleton *isn't* well-formed XML.)
                    skelWtr.write(prefix + "<lTLt:tu id=" + curTuID 
                        + "/>" + quoteStr + ">\n");
                }
                else {
                    // Otherwise (this line *doesn't* have an entity declaration)
                    // echo the input to the output
                    skelWtr.write(dtdLine + "\n");
                }
                skelWtr.flush();
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
                notice = "DTD importer didn't create a skeleton file named " + outSkel;
                System.err.println(notice);
                notifier.sendNotification("0001", "DtdImporter", Notifier.ERROR, notice);
            }
            
            // Does the output XLIFF file exist?
            notice = "";
            File xliffFile = new File(outXliff);
            if (!xliffFile.exists()) {
                notice = "DTD importer didn't create an XLIFF file named " + outXliff;
                System.err.println(notice);
                notifier.sendNotification("0002", "DtdImporter", Notifier.ERROR, notice);
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
                        notifier.sendNotification("0003", "DtdImporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "DtdImporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "DtdImporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    notice = "The validator of XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0006", "DtdImporter", Notifier.ERROR, notice);
                }
            }
        }        

        
        if (generatedFileName != null) {
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Extract the entity declarations from a DTD, generating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (Standard UTF-8 is assumed.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be XULDTD.)
     * @param nativeFileName The name of the input properties file. 
     * @param baseDir The directory that contains the input properties file--from 
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
     *        property string becomes one segment in the XLIFF.)
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
     * Extract the entity declarations from a DTD, generating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (Standard UTF-8 is assumed.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be XULDTD.)
     * @param nativeFileName The name of the input properties file. 
     * @param baseDir The directory that contains the input properties file--from 
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
     * @return the Java Properties (Resource Bundle) file type.
     */
    public FileType getFileType() {
        return FileType.XULDTD;
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
