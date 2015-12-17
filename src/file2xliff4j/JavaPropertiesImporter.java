/*
 * JavaPropertiesImporter.java
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
 * Import a Java Property Resource Bundle to XLIFF. 
 * 
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class JavaPropertiesImporter implements Converter {

    private int curIndent = 0;
    /**
     * Create a JavaPropertiesImporter 
     */
    public JavaPropertiesImporter() {
    }
    
    /**
     * Convert a Java property resource bundle to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF). It should be 
     *        ConversionMode.TO_XLIFF in this case.
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (Standard UTF-8 is assumed.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be JAVA_PROPERTIES.)
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
            throw new ConversionException("Properties Importer supports only conversions"
                    + " of Properties files to XLIFF.");
        }
        
        // We assume (for now, at least) that properties files are encoded in
        // UTF-8
        Charset encoding = Charset.forName("UTF-8");
        
        // Create output stream writer for the XLIFF
        OutputStreamWriter xliffOut = null;

        if (nativeFileName == null || nativeFileName.trim().length() == 0) {
            System.err.println("Input properties file name omitted. Cannot proceed.");
            throw new ConversionException("Input properties file name omitted. Cannot proceed.");
        }

        // Where to store the skeleton properties file.
        String skelPropFileName = baseDir + File.separator + nativeFileName 
            + Converter.skeletonSuffix;
        
        try {
            xliffOut  = new OutputStreamWriter(new FileOutputStream(
                    baseDir + File.separator + nativeFileName + Converter.xliffSuffix),
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

        Properties inProps = new Properties();
        String propFileName = baseDir + File.separator + nativeFileName;
        File pFile = new File(propFileName);
        if (!pFile.exists()) {
            System.err.println("Input properties file does not exist.");
            throw new ConversionException("Input properties file does not exist.");
        }
        
        try {
            inProps.load(new FileInputStream(pFile));
        }
        catch(IOException e) {
            System.err.println("I/O error reading input properties file.");
            throw new ConversionException("I/O error reading input properties file: " 
                    + e.getMessage());
        }
        Properties skelProps = new Properties();
        
        // Output the XLIFF prolog
        try {
            // Write the "prolog" of the XLIFF file
            xliffOut.write(Converter.xmlDeclaration);
            xliffOut.write(Converter.startXliff);
            xliffOut.write(indent() + "<file original='" 
                + nativeFileName.replace("&", "&amp;").replace("<",
                    "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                + "' source-language='" + language.toString() 
                + "' datatype='javapropertyresourcebundle'>\r\n");
            xliffOut.write(indent() + "<header lt:segtype='sentence'");
            xliffOut.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
            xliffOut.flush();
        
            // Write all the TUs
            Enumeration<Object> keys = inProps.keys();
            while (keys.hasMoreElements()) {
                String curKey = (String)keys.nextElement();
                String curVal = inProps.getProperty(curKey);
                UUID curTuID = UUID.randomUUID();
                xliffOut.write(indent('0')
                    + "<trans-unit id='" + curTuID + "' "
                    + "lt:paraID='" + curTuID + "'>\r\n");
                // Open the source tag
                xliffOut.write(indent('+') + "<source xml:lang='" 
                    + language.toString() + "'><mrk mtype='x-coretext'>"
                    + curVal.replace("&", "&amp;").replace("<", "&lt;").replace(">", 
                        "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                    + "</mrk></source>\r\n");

                // ... and the trans-unit element
                xliffOut.write(indent('-') + "</trans-unit>\r\n");
                xliffOut.flush();
                
//                // Store the skeleton mapping (key is UUID, value is property name)
//                skelProps.setProperty(curTuID.toString(), curKey);
                
                // Store the skeleton mapping (key is property name, value is TU ID)
                skelProps.setProperty(curKey, curTuID.toString());
            }
        
            // Then finish off the XLIFF file
            xliffOut.write(indent('0') + "</body>\r\n"); // Close the body element
            xliffOut.write(indent('-') + "</file>\r\n"); // Close the file element
            xliffOut.write("</xliff>\r\n");               // Close the xliff element
            xliffOut.flush();             // Well-bred writers flush when finished
            
            /* Close the files we created above */
            xliffOut.close();     

//            // Write out the skeleton properties to a file.
//            skelProps.store(new FileOutputStream(skelPropFileName), 
//                "Skeleton properties file generated by " + this.getClass().getName());
            int status = createSkeleton(propFileName, skelPropFileName, skelProps);
        }
        catch(java.io.IOException e) {
            System.err.println("Error generating XLIFF and/or skeleton file: "
                    + e.getMessage());
        }
        
        
        // Before returning, see if the notifier is non-null. If it is, make sure
        // a skeleton exists and verify that the XLIFF is well-formed XML.
        if (notifier != null) {
            String notice = "";
            File skelFile = new File(skelPropFileName);
            if (!skelFile.exists()) {
                notice = "Properties importer didn't create a skeleton file named "
                        + baseDir + File.separator
                        + nativeFileName + Converter.skeletonSuffix;
                System.err.println(notice);
                notifier.sendNotification("0001", "PropertiesImporter", Notifier.ERROR, notice);
            }
            
            // Does the output XLIFF file exist?
            notice = "";
            File xliffFile = new File(baseDir + File.separator + nativeFileName 
                    + Converter.xliffSuffix);
            if (!xliffFile.exists()) {
                notice = "Properties importer didn't create an XLIFF file named "
                        + baseDir + File.separator + nativeFileName + Converter.xliffSuffix;
                System.err.println(notice);
                notifier.sendNotification("0002", "PropertiesImporter", Notifier.ERROR, notice);
            }
            else {
                // The XLIFF exists. Is it well-formed?
                Charset charset = Charset.forName("UTF-8");
        
                try {
                    XMLReader parser = XMLReaderFactory.createXMLReader();

                    // We don't care about namespaces at the moment.
                    parser.setFeature("http://xml.org/sax/features/namespaces", false);

                    Reader inReader = new InputStreamReader(new FileInputStream(xliffFile), charset);
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
                        notifier.sendNotification("0003", "PropertiesImporter", Notifier.ERROR, notice);
                    }
                }
                catch(SAXParseException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " is not well-formed at line "
                            + e.getLineNumber() + ", column " + e.getColumnNumber()
                            + "\n" + e.getMessage() + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0004", "PropertiesImporter", Notifier.ERROR, notice);
                }
                catch(SAXException e) {
                    notice = "XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " caused an XML parser error: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0005", "PropertiesImporter", Notifier.ERROR, notice);
                }
                catch(IOException e) {
                    notice = "The validator of XLIFF file " + baseDir + File.separator
                            + nativeFileName + Converter.xliffSuffix
                            + " experienced an I/O error while reading input: " + e.getMessage()
                            + "\n" + this.getStackTrace(e);
                    System.err.println(notice);
                    notifier.sendNotification("0006", "PropertiesImporter", Notifier.ERROR, notice);
                }
            }
        }        

        
        if (generatedFileName != null) {
            generatedFileName.write(nativeFileName + Converter.xliffSuffix);
        }
        
        return ConversionStatus.CONVERSION_SUCCEEDED;
    }

    /**
     * Convert a Java property resource bundle to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (The importer assumes that the encoding is UTF-8.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be JAVA_PROPERTIES.)
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
     * Convert a Java property resource bundle to XLIFF, creating XLIFF and a 
     * skeleton file as output.
     * @param mode The mode of conversion (to or from XLIFF).
     * @param language The language of the input file.
     * @param nativeEncoding The encoding of the properties file. This value is
     *        ignored. (The importer assumes that the encoding is UTF-8.)
     * @param phaseName The target phase-name. This value is ignored.
     * @param maxPhase The maximum phase number. This value is ignored.
     * @param nativeFileType The type of the original native file. This value is
     *        ignored (It is assumed to be JAVA_PROPERTIES.)
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
    
    private Matcher propMatcher = Pattern.compile("^([^=#]+)=").matcher("");
    
    /**
     * Passed the name of the original properties file, the name of a skeleton
     * file to create, and the skeleton properties (mapping property names to
     * TU UUIDs), generate the skeleton file named skelPropFileName.
     * @param propFileName The fully qualified name of the original Java property
     *        resource bundle.
     * @param skelPropFileName The fully qualified name of the skeleton file
     *        to create.
     * @param skelProps A Properties object that maps property names to UUIDs.
     * @return 0 if successful, else non-zero
     */
    private int createSkeleton(String propFileName, String skelPropFileName, 
            Properties skelProps) {
        
        // Create a reader to read the original properties file and a writer to
        // write the skeleton properties file.
        try {
            BufferedReader propRdr = new BufferedReader(new InputStreamReader(
                new FileInputStream(propFileName), Charset.forName("UTF-8")));
            BufferedWriter skelWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(skelPropFileName), Charset.forName("UTF-8")));
            // Now read

            String curLine = "";
            while ((curLine = propRdr.readLine()) != null) {
                String propName = "";
                String tuID = "";
                propMatcher.reset(curLine);
                // Determine if the current line is a property of interest.
                if (propMatcher.find()) {
                    propName = propMatcher.group(1);
                    tuID = skelProps.getProperty(propName.trim());
                }
                
                // If this line is a property of interest, write the property's
                // value as a TUID placeholder
                if (propName != null && propName.trim().length() > 0
                        && tuID != null && tuID.trim().length() > 0) {
                    // This *is* a property of interest
                    skelWriter.write(propName + "=<LtlT:tu id='" + tuID + "'/>\n");
                }
                else {
                    skelWriter.write(curLine + "\n");
                }
                skelWriter.flush();
            }
            // Close before leaving.
            propRdr.close();
            skelWriter.close();
        }

        catch (FileNotFoundException e) {
            System.err.println("JavaPropertiesImporter.createSkeleton cannot "
                    + "find the input properties file.");
            return 1;
        }
        catch (IOException io) {
            System.err.println("JavaPropertiesImporter.createSkeleton caught "
                    + "an I/O exception. Skeleton not created.");
            return 1;
        }
        
        return 0;
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
        return FileType.JAVA_PROPERTIES;
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
