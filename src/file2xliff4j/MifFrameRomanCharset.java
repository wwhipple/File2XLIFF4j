/*
 * MifFrameRomanCharset.java
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

import java.nio.charset.*;
import java.nio.*;
import java.util.*;

/**
 * A Charset implementation that maps between the "7-bit" ASCII (well, it is
 * actually 8-bit ASCII ...) used by FrameMaker's Maker Interchange Format (MIF)
 * to/from Unicode (UTF-8).
 *
 * <p><i>Note:</i> This is not applicable to the Asian encodings that are
 * implied by the presence of a MIFEncoding tag in the MIF file.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class MifFrameRomanCharset extends Charset {
    
    // The name of the base charset encoding we delegate to.
    private static final String BASE_CHARSET_NAME = "UTF-8";

    Charset baseCharset; // The non-UTF-8 encoding
    
    // Map of MIF characters (bytes) to Unicode characters
    // (May decide to convert this to an array of char ... eventually)
    char [] b2c = {
        '\ufffd', // 0x00 -> Undefined (Well, sorta)
        '\ufffd', // 0x01 -> Undefined
        '\ufffd', // 0x02 -> Undefined
        '\ufffd', // 0x03 -> Undefined
        '\u00ad', // 0x04 -> SOFT HYPHEN <Char DiscHyphen>
        '\u200d', // 0x05 -> ZERO WIDTH JOINER <Char NoHyphen>
        '\u2010', // 0x06 -> HYPHEN <Char SoftHyphen> // delete this?
        '\ufffd', // 0x07 -> Undefined
        '\u0009', // 0x08 -> Tab <Char Tab>
        '\u0009',     // 0x09 -> hard return <Char HardReturn>
//        '\r',     // 0x09 -> hard return <Char HardReturn>
        '\n',     // 0x0a -> new line
        '\ufffd', // 0x0b -> Undefined
        '\ufffd', // 0x0c -> Undefined
        '\r', // 0x0d -> Undefined
//        '\ufffd', // 0x0d -> Undefined
        '\ufffd', // 0x0e -> Undefined
        '\ufffd', // 0x0f -> Undefined
        '\u2007', // 0x10 -> figure space <Char NumberSpace>
        '\u00a0', // 0x11 -> no-break space <Char HardSpace>
        '\u2009', // 0x12 -> thin space <Char ThinSpace>
        '\u2002', // 0x13 -> en space <Char EnSpace>
        '\u2003', // 0x14 -> em space  <Char EmSpace>
        '\u2011', // 0x15 -> non-breaking hyphen <Char HardHyphen>
        '\ufffd', // 0x16 -> Undefined
        '\ufffd', // 0x17 -> Undefined
        '\ufffd', // 0x18 -> Undefined
        '\ufffd', // 0x19 -> Undefined
        '\ufffd', // 0x1a -> Undefined
        '\ufffd', // 0x1b -> Undefined
        '\ufffd', // 0x1c -> Undefined
        '\ufffd', // 0x1d -> Undefined
        '\ufffd', // 0x1e -> Undefined
        '\ufffd', // 0x1f -> Undefined

        // These (lower ascii) values map to their same-valued Unicode equivalent
        '\u0020', // SPACE
        '\u0021', // !
        '\u0022', // "
        '\u0023', // #
        '\u0024', // $
        '\u0025', // %
        '\u0026', // &
        '\'',     // '
        '\u0028', // (
        '\u0029', // )
        '\u002a', // *
        '\u002b', // +
        '\u002c', // ,
        '\u002d', // _
        '\u002e', // .
        '\u002f', // /
        '\u0030', // 0
        '\u0031', // 1
        '\u0032', // 2
        '\u0033', // 3
        '\u0034', // 4
        '\u0035', // 5
        '\u0036', // 6
        '\u0037', // 7
        '\u0038', // 8
        '\u0039', // 9
        '\u003a', // ;
        '\u003b', // :
        '\u003c', // < 
        '\u003d', // =
        '\u003e', // >
        '\u003f', // ?
        '\u0040', // @
        '\u0041', // A
        '\u0042', // B
        '\u0043', // C
        '\u0044', // D
        '\u0045', // E
        '\u0046', // F
        '\u0047', // G
        '\u0048', // H
        '\u0049', // I
        '\u004a', // J
        '\u004b', // K
        '\u004c', // L
        '\u004d', // M
        '\u004e', // N
        '\u004f', // O
        '\u0050', // P
        '\u0051', // Q
        '\u0052', // R
        '\u0053', // S
        '\u0054', // T
        '\u0055', // U
        '\u0056', // V
        '\u0057', // W
        '\u0058', // X
        '\u0059', // Y
        '\u005a', // Z
        '\u005b', // [
        '\\',     // \  '\\'
        '\u005d', // ]
        '\u005e', // ^
        '\u005f', // _
        '\u0060', // `
        '\u0061', // a
        '\u0062', // b
        '\u0063', // c
        '\u0064', // d
        '\u0065', // e
        '\u0066', // f
        '\u0067', // g
        '\u0068', // h
        '\u0069', // i
        '\u006a', // j
        '\u006b', // k
        '\u006c', // l
        '\u006d', // m
        '\u006e', // n
        '\u006f', // o
        '\u0070', // p
        '\u0071', // q
        '\u0072', // r
        '\u0073', // s
        '\u0074', // t
        '\u0075', // u
        '\u0076', // v
        '\u0077', // w
        '\u0078', // x
        '\u0079', // y
        '\u007a', // z
        '\u007b', // {
        '\u007c', // |
        '\u007d', // }
        '\u007e', // ~
        '\u007f', // DEL


        // These follow Apple's Roman. (See http://unicode.org/Public/MAPPINGS/VENDORS/APPLE/ROMAN.TXT)
        '\u00c4', // 0x80 -> latin capital letter a with diaeresis
        '\u00c5', // 0x81 -> latin capital letter a with ring above
        '\u00c7', // 0x82 -> latin capital letter c with cedilla
        '\u00c9', // 0x83 -> latin capital letter e with acute
        '\u00d1', // 0x84 -> latin capital letter n with tilde
        '\u00d6', // 0x85 -> latin capital letter o with diaeresis
        '\u00dc', // 0x86 -> latin capital letter u with diaeresis
        '\u00e1', // 0x87 -> latin small letter a with acute
        '\u00e0', // 0x88 -> latin small letter a with grave
        '\u00e2', // 0x89 -> latin small letter a with circumflex
        '\u00e4', // 0x8a -> latin small letter a with diaeresis
        '\u00e3', // 0x8b -> latin small letter a with tilde
        '\u00e5', // 0x8c -> latin small letter a with ring above
        '\u00e7', // 0x8d -> latin small letter c with cedilla
        '\u00e9', // 0x8e -> latin small letter e with acute
        '\u00e8', // 0x8f -> latin small letter e with grave
        '\u00ea', // 0x90 -> latin small letter e with circumflex
        '\u00eb', // 0x91 -> latin small letter e with diaeresis
        '\u00ed', // 0x92 -> latin small letter i with acute
        '\u00ec', // 0x93 -> latin small letter i with grave
                                                                   //   (don't believe Adobe's docs!
        '\u00ee', // 0x94 -> latin small letter i with circumflex
        '\u00ef', // 0x95 -> latin small letter i with diaeresis
        '\u00f1', // 0x96 -> latin small letter n with tilde
        '\u00f3', // 0x97 -> latin small letter o with acute
        '\u00f2', // 0x98 -> latin small letter o with grave
        '\u00f4', // 0x99 -> latin small letter o with circumflex
        '\u00f6', // 0x9a -> latin small letter o with diaeresis
        '\u00f5', // 0x9b -> latin small letter o with tilde
        '\u00fa', // 0x9c -> latin small letter u with acute
        '\u00f9', // 0x9d -> latin small letter u with grave
        '\u00fb', // 0x9e -> latin small letter u with circumflex
        '\u00fc', // 0x9f -> latin small letter u with diaeresis
        '\u2020', // 0xa0 -> dagger <Char Dagger>
        '\u00b0', // 0xa1 -> degree sign
        '\u00a2', // 0xa2 -> cent sign <Char Cent>
        '\u00a3', // 0xa3 -> pound sign <Char Pound>
        '\u00a7', // 0xa4 -> section sign
        '\u2022', // 0xa5 -> bullet <Char Bullet>
        '\u00b6', // 0xa6 -> pilcrow sign
        '\u00df', // 0xa7 -> latin small letter sharp s
        '\u00ae', // 0xa8 -> registered sign
        '\u00a9', // 0xa9 -> copyright sign
        '\u2122', // 0xaa -> trademark sign
        '\u00b4', // 0xab -> acute accent
        '\u00a8', // 0xac -> diaeresis
        
        // This doesn't follow Apple's ascii ...
        '\u00a6', // 0xad -> broken bar
        '\u00c6', // 0xae -> latin capital letter ae
        '\u00d8', // 0xaf -> latin capital letter o with stroke
        '\u00d7', // 0xb0 -> multiplication sign
        '\u00b1', // 0xb1 -> plus-minus sign
        '\u00f0', // 0xb2 -> latin small letter eth
        '\u0160', // 0xb3 -> latin capital letter s with caron
        '\u00a5', // 0xb4 -> yen sign <Char Yen>
        '\u00b5', // 0xb5 -> micro sign
        '\u00b9', // 0xb6 -> superscript one
        '\u00b2', // 0xb7 -> superscript two
        '\u00b3', // 0xb8 -> superscript three
        '\u00bc', // 0xb9 -> vulgar fraction one quarter
        '\u00bd', // 0xba -> vulgar fraction one half
        '\u00aa', // 0xbb -> feminine ordinal indicator
        '\u00ba', // 0xbc -> masculine ordinal indicator
        '\u00be', // 0xbd -> vulgar fraction three quarters
        '\u00e6', // 0xbe -> latin small letter ae
        '\u00f8', // 0xbf -> latin small letter o with stroke
        '\u00bf', // 0xc0 -> inverted question mark
        '\u00a1', // 0xc1 -> inverted exclamation mark
        '\u00ac', // 0xc2 -> not sign
        '\u00d0', // 0xc3 -> latin capital letter eth
        '\u0192', // 0xc4 -> latin small letter f with hook
        '\u00dd', // 0xc5 -> latin capital letter y with acute
        '\u00fd', // 0xc6 -> latin small letter y with acute
        '\u00ab', // 0xc7 -> left-pointing double angle quotation mark
        '\u00bb', // 0xc8 -> right-pointing double angle quotation mark
        '\u2026', // 0xc9 -> horizontal ellipsis
        '\u00fe', // 0xca -> latin small letter thorn
        '\u00c0', // 0xcb -> latin capital letter a with grave
        '\u00c3', // 0xcc -> latin capital letter a with tilde
        '\u00d5', // 0xcd -> latin capital letter o with tilde
        '\u0152', // 0xce -> latin capital ligature oe
        '\u0153', // 0xcf -> latin small ligature oe
        '\u2013', // 0xd0 -> en dash <Char EnDash>
        '\u2014', // 0xd1 -> em dash <Char EmDash>
        '\u201c', // 0xd2 -> left double quotation mark
        '\u201d', // 0xd3 -> right double quotation mark
        '\u2018', // 0xd4 -> left single quotation mark
        '\u2019', // 0xd5 -> right single quotation mark
        '\u00f7', // 0xd6 -> division sign
        '\u00de', // 0xd7 -> latin capital letter thorn
        '\u00ff', // 0xd8 -> latin small letter y with diaeresis
        '\u0178', // 0xd9 -> latin capital letter y with diaeresis
        '\u2044', // 0xda -> fraction slash
        '\u00a4', // 0xdb -> currency sign
        '\u2039', // 0xdc -> single left-pointing angle quotation mark
        '\u203a', // 0xdd -> single right-pointing angle quotation mark
        '\ufb01', // 0xde -> latin small ligature fi
        '\ufb02', // 0xdf -> latin small ligature fl
        '\u2021', // 0xe0 -> double dagger <Char DoubleDagger>
        '\u00b7', // 0xe1 -> middle dot
        '\u201a', // 0xe2 -> single low-9 quotation mark
        '\u201e', // 0xe3 -> double low-9 quotation mark
        '\u2030', // 0xe4 -> per mille sign
        '\u00c2', // 0xe5 -> latin capital letter a with circumflex
        '\u00ca', // 0xe6 -> latin capital letter e with circumflex
        '\u00c1', // 0xe7 -> latin capital letter a with acute
        '\u00cb', // 0xe8 -> latin capital letter e with diaeresis
        '\u00c8', // 0xe9 -> latin capital letter e with grave
        '\u00cd', // 0xea -> latin capital letter i with acute
        '\u00ce', // 0xeb -> latin capital letter i with circumflex
        '\u00cf', // 0xec -> latin capital letter i with diaeresis
        '\u00cc', // 0xed -> latin capital letter i with grave
        '\u00d3', // 0xee -> latin capital letter o with acute
        '\u00d4', // 0xef -> latin capital letter o with circumflex
        '\u0161', // 0xf0 -> latin small letter s with caron
        '\u00d2', // 0xf1 -> latin capital letter o with grave
        '\u00da', // 0xf2 -> latin capital letter u with acute
        '\u00db', // 0xf3 -> latin capital letter u with circumflex
        '\u00d9', // 0xf4 -> latin capital letter u with grave
        '\u20ac', // 0xf5 -> euro sign
        '\u02c6', // 0xf6 -> modifier letter circumflex accent
        '\u02dc', // 0xf7 -> small tilde
        '\u00af', // 0xf8 -> macron
        '\u02c7', // 0xf9 -> caron
        '\u017d', // 0xfa -> latin capital letter z with caron
        '\u02da', // 0xfb -> ring above
        '\u00b8', // 0xfc -> cedilla
        '\u02dd', // 0xfd -> double acute accent
        '\u017e', // 0xfe -> latin small letter z with caron
        '\ufffd'  // 0xff -> Undefined.        
        };
    
    // Map of Unicode characters back to MIF characters (bytes)
    HashMap<Character,String> utf2frame = new HashMap<Character,String>();
    

    /**
     * Constructor for the MIF charset. Call the superclass
     * constructor to pass along the name(s) we'll be known by.
     * Then save a reference to the delegate Charset.
     * @param canonical The canonical name of this character set
     * @param aliases An array of this charset's aliases, or null if it has no
     *          aliases.
     */
    protected MifFrameRomanCharset (String canonical, String [] aliases)
    {
        super (canonical, aliases);

        // Map the unicode characters back to bytes--using MIF encoding.
        utf2frame.put(Character.valueOf('\u00ad'), "\\x04 "); // SOFT HYPHEN <Char DiscHyphen>
        utf2frame.put(Character.valueOf('\u200d'), "\\x05 "); // ZERO WIDTH JOINER <Char NoHyphen>
        utf2frame.put(Character.valueOf('\u2010'), "\\x06 "); // HYPHEN <Char SoftHyphen> // delete this?
//        utf2frame.put(Character.valueOf('\u0009'), "\\x08 "); // Tab <Char Tab>
        utf2frame.put(Character.valueOf('\u0009'), "\u0009"); // Tab <Char Tab>
        utf2frame.put(Character.valueOf('\r'), "\r");         // hard return <Char HardReturn>
        utf2frame.put(Character.valueOf('\n'), "\n");         // new line
        utf2frame.put(Character.valueOf('\u2007'), "\\x10 "); // figure space <Char NumberSpace>
        utf2frame.put(Character.valueOf('\u00a0'), "\\x11 "); // no-break space <Char HardSpace>
        utf2frame.put(Character.valueOf('\u2009'), "\\x12 "); // thin space <Char ThinSpace>
        utf2frame.put(Character.valueOf('\u2002'), "\\x13 "); // en space <Char EnSpace>
        utf2frame.put(Character.valueOf('\u2003'), "\\x14 "); // em space  <Char EmSpace>
        utf2frame.put(Character.valueOf('\u2011'), "\\x15 "); // non-breaking hyphen <Char HardHyphen>

        // These follow Apple's Roman. (See http://unicode.org/Public/MAPPINGS/VENDORS/APPLE/ROMAN.TXT)
        utf2frame.put(Character.valueOf('\u00c4'), "\\x80 "); // latin capital letter a with diaeresis
        utf2frame.put(Character.valueOf('\u00c5'), "\\x81 "); // latin capital letter a with ring above
        utf2frame.put(Character.valueOf('\u00c7'), "\\x82 "); // latin capital letter c with cedilla
        utf2frame.put(Character.valueOf('\u00c9'), "\\x83 "); // latin capital letter e with acute
        utf2frame.put(Character.valueOf('\u00d1'), "\\x84 "); // latin capital letter n with tilde
        utf2frame.put(Character.valueOf('\u00d6'), "\\x85 "); // latin capital letter o with diaeresis
        utf2frame.put(Character.valueOf('\u00dc'), "\\x86 "); // latin capital letter u with diaeresis
        utf2frame.put(Character.valueOf('\u00e1'), "\\x87 "); // latin small letter a with acute
        utf2frame.put(Character.valueOf('\u00e0'), "\\x88 "); // latin small letter a with grave
        utf2frame.put(Character.valueOf('\u00e2'), "\\x89 "); // latin small letter a with circumflex
        utf2frame.put(Character.valueOf('\u00e4'), "\\x8a "); // latin small letter a with diaeresis
        utf2frame.put(Character.valueOf('\u00e3'), "\\x8b "); // latin small letter a with tilde
        utf2frame.put(Character.valueOf('\u00e5'), "\\x8c "); // latin small letter a with ring above
        utf2frame.put(Character.valueOf('\u00e7'), "\\x8d "); // latin small letter c with cedilla
        utf2frame.put(Character.valueOf('\u00e9'), "\\x8e "); // latin small letter e with acute
        utf2frame.put(Character.valueOf('\u00e8'), "\\x8f "); // latin small letter e with grave
        utf2frame.put(Character.valueOf('\u00ea'), "\\x90 "); // latin small letter e with circumflex
        utf2frame.put(Character.valueOf('\u00eb'), "\\x91 "); // latin small letter e with diaeresis
        utf2frame.put(Character.valueOf('\u00ed'), "\\x92 "); // latin small letter i with acute
        utf2frame.put(Character.valueOf('\u00ec'), "\\x93 "); // latin small letter i with grave
                                                                   //   (don't believe Adobe's docs!
        utf2frame.put(Character.valueOf('\u00ee'), "\\x94 "); // latin small letter i with circumflex
        utf2frame.put(Character.valueOf('\u00ef'), "\\x95 "); // latin small letter i with diaeresis
        utf2frame.put(Character.valueOf('\u00f1'), "\\x96 "); // latin small letter n with tilde
        utf2frame.put(Character.valueOf('\u00f3'), "\\x97 "); // latin small letter o with acute
        utf2frame.put(Character.valueOf('\u00f2'), "\\x98 "); // latin small letter o with grave
        utf2frame.put(Character.valueOf('\u00f4'), "\\x99 "); // latin small letter o with circumflex
        utf2frame.put(Character.valueOf('\u00f6'), "\\x9a "); // latin small letter o with diaeresis
        utf2frame.put(Character.valueOf('\u00f5'), "\\x9b "); // latin small letter o with tilde
        utf2frame.put(Character.valueOf('\u00fa'), "\\x9c "); // latin small letter u with acute
        utf2frame.put(Character.valueOf('\u00f9'), "\\x9d "); // latin small letter u with grave
        utf2frame.put(Character.valueOf('\u00fb'), "\\x9e "); // latin small letter u with circumflex
        utf2frame.put(Character.valueOf('\u00fc'), "\\x9f "); // latin small letter u with diaeresis
        utf2frame.put(Character.valueOf('\u2020'), "\\xa0 "); // dagger <Char Dagger>
        utf2frame.put(Character.valueOf('\u00b0'), "\\xa1 "); // degree sign
        utf2frame.put(Character.valueOf('\u00a2'), "\\xa2 "); // cent sign <Char Cent>
        utf2frame.put(Character.valueOf('\u00a3'), "\\xa3 "); // pound sign <Char Pound>
        utf2frame.put(Character.valueOf('\u00a7'), "\\xa4 "); // section sign
        utf2frame.put(Character.valueOf('\u2022'), "\\xa5 "); // bullet <Char Bullet>
        utf2frame.put(Character.valueOf('\u00b6'), "\\xa6 "); // pilcrow sign
        utf2frame.put(Character.valueOf('\u00df'), "\\xa7 "); // latin small letter sharp s
        utf2frame.put(Character.valueOf('\u00ae'), "\\xa8 "); // registered sign
        utf2frame.put(Character.valueOf('\u00a9'), "\\xa9 "); // copyright sign
        utf2frame.put(Character.valueOf('\u2122'), "\\xaa "); // trademark sign
        utf2frame.put(Character.valueOf('\u00b4'), "\\xab "); // acute accent
        utf2frame.put(Character.valueOf('\u00a8'), "\\xac "); // diaeresis
        
        // This doesn't follow Apple's ascii ...
        utf2frame.put(Character.valueOf('\u00a6'), "\\xad "); // broken bar
        utf2frame.put(Character.valueOf('\u00c6'), "\\xae "); // latin capital letter ae
        utf2frame.put(Character.valueOf('\u00d8'), "\\xaf "); // latin capital letter o with stroke
        utf2frame.put(Character.valueOf('\u00d7'), "\\xb0 "); // multiplication sign
        utf2frame.put(Character.valueOf('\u00b1'), "\\xb1 "); // plus-minus sign
        utf2frame.put(Character.valueOf('\u00f0'), "\\xb2 "); // latin small letter eth
        utf2frame.put(Character.valueOf('\u0160'), "\\xb3 "); // latin capital letter s with caron
        utf2frame.put(Character.valueOf('\u00a5'), "\\xb4 "); // yen sign <Char Yen>
        utf2frame.put(Character.valueOf('\u00b5'), "\\xb5 "); // micro sign
        utf2frame.put(Character.valueOf('\u00b9'), "\\xb6 "); // superscript one
        utf2frame.put(Character.valueOf('\u00b2'), "\\xb7 "); // superscript two
        utf2frame.put(Character.valueOf('\u00b3'), "\\xb8 "); // superscript three
        utf2frame.put(Character.valueOf('\u00bc'), "\\xb9 "); // vulgar fraction one quarter
        utf2frame.put(Character.valueOf('\u00bd'), "\\xba "); // vulgar fraction one half
        utf2frame.put(Character.valueOf('\u00aa'), "\\xbb "); // feminine ordinal indicator
        utf2frame.put(Character.valueOf('\u00ba'), "\\xbc "); // masculine ordinal indicator
        utf2frame.put(Character.valueOf('\u00be'), "\\xbd "); // vulgar fraction three quarters
        utf2frame.put(Character.valueOf('\u00e6'), "\\xbe "); // latin small letter ae
        utf2frame.put(Character.valueOf('\u00f8'), "\\xbf "); // latin small letter o with stroke
        utf2frame.put(Character.valueOf('\u00bf'), "\\xc0 "); // inverted question mark
        utf2frame.put(Character.valueOf('\u00a1'), "\\xc1 "); // inverted exclamation mark
        utf2frame.put(Character.valueOf('\u00ac'), "\\xc2 "); // not sign
        utf2frame.put(Character.valueOf('\u00d0'), "\\xc3 "); // latin capital letter eth
        utf2frame.put(Character.valueOf('\u0192'), "\\xc4 "); // latin small letter f with hook
        utf2frame.put(Character.valueOf('\u00dd'), "\\xc5 "); // latin capital letter y with acute
        utf2frame.put(Character.valueOf('\u00fd'), "\\xc6 "); // latin small letter y with acute
        utf2frame.put(Character.valueOf('\u00ab'), "\\xc7 "); // left-pointing double angle quotation mark
        utf2frame.put(Character.valueOf('\u00bb'), "\\xc8 "); // right-pointing double angle quotation mark
        utf2frame.put(Character.valueOf('\u2026'), "\\xc9 "); // horizontal ellipsis
        utf2frame.put(Character.valueOf('\u00fe'), "\\xca "); // latin small letter thorn
        utf2frame.put(Character.valueOf('\u00c0'), "\\xcb "); // latin capital letter a with grave
        utf2frame.put(Character.valueOf('\u00c3'), "\\xcc "); // latin capital letter a with tilde
        utf2frame.put(Character.valueOf('\u00d5'), "\\xcd "); // latin capital letter o with tilde
        utf2frame.put(Character.valueOf('\u0152'), "\\xce "); // latin capital ligature oe
        utf2frame.put(Character.valueOf('\u0153'), "\\xcf "); // latin small ligature oe
        utf2frame.put(Character.valueOf('\u2013'), "\\xd0 "); // en dash <Char EnDash>
        utf2frame.put(Character.valueOf('\u2014'), "\\xd1 "); // em dash <Char EmDash>
        utf2frame.put(Character.valueOf('\u201c'), "\\xd2 "); // left double quotation mark
        utf2frame.put(Character.valueOf('\u201d'), "\\xd3 "); // right double quotation mark
        utf2frame.put(Character.valueOf('\u2018'), "\\xd4 "); // left single quotation mark
        utf2frame.put(Character.valueOf('\u2019'), "\\xd5 "); // right single quotation mark
        utf2frame.put(Character.valueOf('\u00f7'), "\\xd6 "); // division sign
        utf2frame.put(Character.valueOf('\u00de'), "\\xd7 "); // latin capital letter thorn
        utf2frame.put(Character.valueOf('\u00ff'), "\\xd8 "); // latin small letter y with diaeresis
        utf2frame.put(Character.valueOf('\u0178'), "\\xd9 "); // latin capital letter y with diaeresis
        utf2frame.put(Character.valueOf('\u2044'), "\\xda "); // fraction slash
        utf2frame.put(Character.valueOf('\u00a4'), "\\xdb "); // currency sign
        utf2frame.put(Character.valueOf('\u2039'), "\\xdc "); // single left-pointing angle quotation mark
        utf2frame.put(Character.valueOf('\u203a'), "\\xdd "); // single right-pointing angle quotation mark
        utf2frame.put(Character.valueOf('\ufb01'), "\\xde "); // latin small ligature fi
        utf2frame.put(Character.valueOf('\ufb02'), "\\xdf "); // latin small ligature fl
        utf2frame.put(Character.valueOf('\u2021'), "\\xe0 "); // double dagger <Char DoubleDagger>
        utf2frame.put(Character.valueOf('\u00b7'), "\\xe1 "); // middle dot
        utf2frame.put(Character.valueOf('\u201a'), "\\xe2 "); // single low-9 quotation mark
        utf2frame.put(Character.valueOf('\u201e'), "\\xe3 "); // double low-9 quotation mark
        utf2frame.put(Character.valueOf('\u2030'), "\\xe4 "); // per mille sign
        utf2frame.put(Character.valueOf('\u00c2'), "\\xe5 "); // latin capital letter a with circumflex
        utf2frame.put(Character.valueOf('\u00ca'), "\\xe6 "); // latin capital letter e with circumflex
        utf2frame.put(Character.valueOf('\u00c1'), "\\xe7 "); // latin capital letter a with acute
        utf2frame.put(Character.valueOf('\u00cb'), "\\xe8 "); // latin capital letter e with diaeresis
        utf2frame.put(Character.valueOf('\u00c8'), "\\xe9 "); // latin capital letter e with grave
        utf2frame.put(Character.valueOf('\u00cd'), "\\xea "); // latin capital letter i with acute
        utf2frame.put(Character.valueOf('\u00ce'), "\\xeb "); // latin capital letter i with circumflex
        utf2frame.put(Character.valueOf('\u00cf'), "\\xec "); // latin capital letter i with diaeresis
        utf2frame.put(Character.valueOf('\u00cc'), "\\xed "); // latin capital letter i with grave
        utf2frame.put(Character.valueOf('\u00d3'), "\\xee "); // latin capital letter o with acute
        utf2frame.put(Character.valueOf('\u00d4'), "\\xef "); // latin capital letter o with circumflex
        utf2frame.put(Character.valueOf('\u0161'), "\\xf0 "); // latin small letter s with caron
        utf2frame.put(Character.valueOf('\u00d2'), "\\xf1 "); // latin capital letter o with grave
        utf2frame.put(Character.valueOf('\u00da'), "\\xf2 "); // latin capital letter u with acute
        utf2frame.put(Character.valueOf('\u00db'), "\\xf3 "); // latin capital letter u with circumflex
        utf2frame.put(Character.valueOf('\u00d9'), "\\xf4 "); // latin capital letter u with grave
        utf2frame.put(Character.valueOf('\u20ac'), "\\xf5 "); // euro sign
        utf2frame.put(Character.valueOf('\u02c6'), "\\xf6 "); // modifier letter circumflex accent
        utf2frame.put(Character.valueOf('\u02dc'), "\\xf7 "); // small tilde
        utf2frame.put(Character.valueOf('\u00af'), "\\xf8 "); // macron
        utf2frame.put(Character.valueOf('\u02c7'), "\\xf9 "); // caron
        utf2frame.put(Character.valueOf('\u017d'), "\\xfa "); // latin capital letter z with caron
        utf2frame.put(Character.valueOf('\u02da'), "\\xfb "); // ring above
        utf2frame.put(Character.valueOf('\u00b8'), "\\xfc "); // cedilla
        utf2frame.put(Character.valueOf('\u02dd'), "\\xfd "); // double acute accent
        utf2frame.put(Character.valueOf('\u017e'), "\\xfe "); // latin small letter z with caron
        
        // Save the base charset we're delegating to.
        baseCharset = Charset.forName (BASE_CHARSET_NAME);
    }

    /**
     * Passed a Charset, return true if every character in the specified
     * Charset is representable by the MifFrameRomanCharset.
     * This method must be implemented by concrete Charsets.  We always
     * say no, which is safe.
     * 
     * @param cs The Charset that might (or might not--depending on the return
     *           value from this method) be a subset of MifFrameRomanCharset.
     * @return true if, and only if, the given charset is contained in this charset
     */
    public boolean contains (Charset cs)
    {
        return (false);
    }

   /**
     * Get a decoder for the MifFrameRomanCharset character set.
     * <p><i>Note:</i> This implementation instantiates an instance of a private 
     * class (defined below) and passes it a decoder from the base Charset.
     * 
     * @return a decoder for the MifFrameRomanCharset charset
     */
    public CharsetDecoder newDecoder()
    {
        return new MifCharsetDecoder (this, baseCharset.newDecoder());
    }

    /**
    * Return an encoder that will convert from UTF-8 to the MIF character
    * set.
    * This implementation instantiates an instance of a private class
    * (defined below) and passes it an encoder from the base Charset.
    */
   public CharsetEncoder newEncoder()
   {
        return new MifCharsetEncoder(this, baseCharset.newEncoder());
   }

   /**
    * Class to convert bytes in MIF strings (non-Asian language) to Unicode
    * characters.
    */
    private class MifCharsetDecoder extends CharsetDecoder {

        
        /**
         * Create a new MifCharsetDecoder. Call the superclass ctor
         * with the Charset object and pass along the chars/byte values from 
         * delegated decoder.
         * @param cs The Charset (that we will pass to the super class).
         * @param baseDecoder The base decoder that we will delegate to.
         */
        MifCharsetDecoder(Charset cs, CharsetDecoder baseDecoder) {
            super (cs, baseDecoder.averageCharsPerByte(),
                baseDecoder.maxCharsPerByte());
            
        }

        /** 
         * Decode one or more MIF bytes into one or more Unicode (UTF-8) 
         * characters.
         * <p>Decode as many bytes as possible from the in buffer (until it
         * runs one of input or the output buffer's space is exhausted).
         * @param in The input byte buffer (containing MIF non-Asian characters)
         * @param out The output character buffer--to which the resulting unicode
         *        (UTF-8) characters--are written.
         * @return An indication of the reason for stopping the conversion.
         */
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out)
        {
            CoderResult result = CoderResult.UNDERFLOW; // Assume the output buf is long enough
            // While more bytes to read ...
            while (in.hasRemaining())
            {
                // Get the next byte from the input buffer.
                // Note: A byte is a *signed* variable in the range -128 .. +127.
                // To get its unsigned value, bitwise *and* it with hex ff and
                // assign it to an int.
                int inputByte = 0xff & in.get();

                // Try to get the Unicode character it maps to
                char outChar = b2c[inputByte];

                // Write the resulting char to the output char buffer.
                if (out.hasRemaining()) {
                    // But only if the output buffer has room.
                    out.put(outChar);
                }
                else {  // Outta space!
                    result = CoderResult.OVERFLOW; // Output filled up first
                    break;
                }
            }

            return result;  // UNDERFLOW if enough room in output; else OVERFLOW
        }

    }
    
   /**
    * Class to convert Unicode characters (non-Asian languages only) to in MIF 
    * bytes.
    */
    private class MifCharsetEncoder extends CharsetEncoder {

//        private CharsetEncoder baseEncoder;

        /**
         * Create a new MifCharsetEncoder. Call the superclass constructor
         * with the Charset object and pass along the chars/byte values from 
         * delegated decoder.
         * @param cs The Charset (that we will pass to the super class).
         * @param baseEncoder The base encoder that we will delegate to.
         */
        MifCharsetEncoder(Charset cs, CharsetEncoder baseEncoder) {
            super (cs, baseEncoder.averageBytesPerChar() * 2,
                baseEncoder.maxBytesPerChar());
        }


        /** 
         * Encode one or more Unicode characters into one or more MIF Frame
         * Roman characters (non-Asian-language). 
         * <p>Decode as many chars as possible from the in buffer (until it
         * runs one of input or the output buffer's space is exhausted). If an
         * encoding error occurs, stop and report the error.
         * @param in The input character buffer--from which the unicode (UTF-8) 
         *         characters--are read.
         * @param out The output byte buffer (to which MIF non-Asian characters)
         *         are written.
         * @return An indication of the reason for stopping the conversion.
         */
        protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out)
        {
            CoderResult result = CoderResult.UNDERFLOW; // Assume the output buf is long enough
            // While more bytes to read ...
            MAIN_ENCODE_LOOP:
            while (in.hasRemaining())
            {
                // Get the next character from the input buffer.
                char inputChar = in.get();

                // Try to get the MIF character (byte) it maps to
                String outputChar = "";
                
                if ((inputChar >= 20) && (inputChar <= 0x7e)) {  // Not 0x7f
                    // Not in the map. See if it is in the lower ASCII range.
                    outputChar = Character.toString(inputChar);
                }
                else {    // Extended ascii or less than 32decimal
                    outputChar = utf2frame.get(Character.valueOf(inputChar));
                }
                
                if (outputChar == null) {  // Not found
//                    // Badness! We can't map this character to MIF!!
//                    result = CoderResult.unmappableForLength(in.remaining());
//                    System.err.println("Cannot map Unicode character " 
//                            + Integer.toHexString(Character.getNumericValue(inputChar)) 
//                            + " to FrameRoman character set.");
//                    break MAIN_ENCODE_LOOP;
                    outputChar = "\ufffd";
                }

                // Write the resulting char to the output char buffer.
                for (int i = 0; i < outputChar.length(); i++) {
                    if (out.hasRemaining()) {
                        // But only if the output buffer has room.
                        out.put((byte)outputChar.charAt(i));
                    }
                    else {  // Outta space!
                        result = CoderResult.OVERFLOW; // Output filled up first
                        break MAIN_ENCODE_LOOP;
                    }
                }
            }

            return result;  // UNDERFLOW if enough room in output; else OVERFLOW
        }
    }
}
