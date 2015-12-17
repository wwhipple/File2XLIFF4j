/*
 * FileType.java
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

/**
 * File types that we know how to deal with
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public enum FileType {

    // Always put this one *first*
    MSOFFICEDOC,  // Microsoft Office Doc (Word, Excel, PPT, etc.)

    HTML,     
    ODT,      // OpenOffice.org's OpenDocument Text (odt)
    WORD,
    RTF,
    XLIFF,
    EXCEL,
    PPT,
    MIF,      // Maker Interchange Format (FrameMaker)
    ODS,      // OpenOffice.org's OpenDocument Spreadsheet (Calc) (ods)
    ODP,      // OpenOffice.org's Presentation (Impress) (odp)
    XML,
    PDF,      // Portable Document Format
    PLAINTEXT,  // Plaintext--no formatting or tags.
    JAVA_PROPERTIES, // Java property resource bundle.
    XULDTD,   // XML DTD (entities used for XUL)
    PO,       // GNU gettext Portable Object (Template)
    WINRC,    // Windows RC file
}
