/*
 * convert.java
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

import file2xliff4j.*;
import java.util.Locale;
import java.nio.charset.*;

/**
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class convert {
    
    /**
     * Command-line method to invoke the file2xliff4j converter.
     */
    public static void main(String[] args) {
        if (args.length == 1) {
            showHelp();
            System.exit(1);
        }
        else if (args.length != 6) {
            Gui gui = new Gui();
            gui.convert();
            return;
        }
        
        String filename = args[0];
        String basedir  = args[1];
        String mode     = args[2];
        String lang     = args[3];
        String encoding = args[4];
        String filetype = args[5];
        
        Charset charset = Charset.forName(encoding);
        
        Locale locale = null;
        
        String[] split = lang.split("_");
        if (split.length == 3) {
            locale = new Locale(split[0], split[1], split[2]);
        }
        else if (split.length == 2) {
            locale = new Locale(split[0], split[1]);
        }
        else {
            locale = new Locale(lang);
        }
        
        FileType type= null;
        
        if (filetype.equalsIgnoreCase("WORD")) {
            type = FileType.WORD;
        }
        else if (filetype.equalsIgnoreCase("HTML")) {
            type = FileType.HTML;
        }
        else if (filetype.equalsIgnoreCase("EXCEL")) {
            type = FileType.EXCEL;
        }
        else if (filetype.equalsIgnoreCase("ODT")) {
            type = FileType.ODT; // OpenOffice.org document text
        }
        else if (filetype.equalsIgnoreCase("ODS")) {
            type = FileType.ODS; // OpenOffice.org spreadsheet (Calc)
        }
        else if (filetype.equalsIgnoreCase("ODP")) {
            type = FileType.ODP; // OpenOffice.org presentation (Impress)
        }
        else if (filetype.equalsIgnoreCase("RTF")) {
            type = FileType.RTF;
        }
        else if (filetype.equalsIgnoreCase("PPT")) {
            type = FileType.PPT; // PowerPoint
        }
        else if (filetype.equalsIgnoreCase("MIF")) {
            type = FileType.MIF; // Maker Interchange Format
        }
        else if (filetype.equalsIgnoreCase("XML")) {
            type = FileType.XML; // Extensible Markup Language
        }
        else if (filetype.equalsIgnoreCase("PLAINTEXT")) {
            type = FileType.XML; // Extensible Markup Language
        }
        else {
            System.out.println("Unrecognized filetype " + filetype);
        }
        
        Converter converter = null;
        ConversionMode cMode = null;
        if (mode.equalsIgnoreCase("toxliff")) {
            cMode = ConversionMode.TO_XLIFF;
            try {
                converter = ConverterFactory.createConverter(type, FileType.XLIFF);
            }
            catch(ConversionException e) {
                System.out.println("Error creating " + filetype + "-to-XLIFF"
                        + "converter: " + e.getMessage());
                System.exit(2);
            }
        }
        else if (mode.equalsIgnoreCase("fromxliff")) {
            cMode = ConversionMode.FROM_XLIFF;
            try {
                converter = ConverterFactory.createConverter(FileType.XLIFF, type);
            }
            catch(ConversionException e) {
                System.out.println("Error creating XLIFF-to-" + filetype 
                        + "converter: " + e.getMessage());
                System.exit(3);
            }
        }
        else {
            System.out.println("Unrecognized mode " + mode);
            System.exit(4);
        }
        
        // Now invoke the converter:
        try {
            converter.convert(cMode, locale, null, 0, charset, type, filename,
                    basedir, null, null, null);
        }
        catch(ConversionException e) {
            System.out.println("Error converting file " + filename + ": "
                    + e.getMessage());
            System.exit(5);
        }
        
        // Still here? We're done!
        System.out.println("Conversion completed successfully.");
    }
    
    /**
     * Display the convert syntax.
     */
    private static void showHelp() {
        
        // If only Java had here-documents! [sigh]
        System.out.println(
                "No-argument syntax:\r\n"
              + "  java convert\r\n\r\n"
              + "Syntax with arguments (all 6 arguments required, in order):\r\n" 
              + "  java convert <filename> <basedir> <mode> <lang> <encoding>           \r\n"
              + "               <filetype>                                              \r\n"
              + "                                                                       \r\n"
              + "  java convert <filename> <basedir> <mode> <lang> <encoding>           \r\n"
              + "               <filetype>                                              \r\n"
              + "where:                                                                 \r\n" 
              + "  <filename>    is the name of the file (without directory prefix)     \r\n"
              + "                to be converted.                                       \r\n"
              + "                If converting to XLIFF, it is the actual name of       \r\n"
              + "                the file to convert.                                   \r\n"
              + "                If converting from XLIFF, it is the name of the        \r\n"
              + "                original file initially converted to XLIFF.            \r\n"
              + "  <basedir>     is the name of the directory that contains the file    \r\n"
              + "                to be converted to XLIFF. This is also the directory   \r\n"
              + "                that will hold the generated XLIFF, skeleton, format   \r\n"
              + "                and other temporary and intermediate files.            \r\n"
              + "                If the conversion will generate an original-format     \r\n"
              + "                document from one of the <target> languages in the     \r\n"
              + "                XLIFF file (which must exist--along with the skeleton, \r\n"
              + "                format, and original file(s)), the generated files     \r\n"
              + "                will have names that match the original <source> file, \r\n"
              + "                except that the language will be inserted before the   \r\n"
              + "                extension.                                             \r\n"
              + "  <mode>        is either \"toxliff\" or \"fromxliff\".                \r\n"
              + "  <lang>        is the ISO language code of the source document (if    \r\n"
              + "                converting to XLIFF) or the code of the target language\r\n"
              + "                (if converting a target to the original format).       \r\n"
              + "  <encoding>    is the encoding (e.g. ISO-8859-1, SHIFT-JIS, etc.) of  \r\n"
              + "                the native document.                                   \r\n"
              + "  <filetype>    one of: \"HTML\"                                       \r\n"
              + "                        \"WORD\"                                       \r\n"
              + "                        \"EXCEL\"                                      \r\n"
              + "                        \"PPT\"                                        \r\n"
              + "                        \"ODT\"                                        \r\n"
              + "                        \"ODS\"                                        \r\n"
              + "                        \"ODP\"                                        \r\n"
              + "                        \"RTF\"                                        \r\n"
              + "                        \"MIF\"                                        \r\n"
              + "                        \"XML\"                                        \r\n"
              + "                        \"PLAINTEXT\"                                  \r\n"
             );
    }
}
