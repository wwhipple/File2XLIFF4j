/**
 * OdfHandler.java
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
import org.apache.xerces.xni.*;

import java.util.*;
import java.lang.*;
import java.io.*;
import java.util.regex.*;

/**
 * Utility class representing a candidate TU's text and UUID.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class TuListEntry {
    private StringBuilder candidateTu = new StringBuilder();
    
    private UUID tuID = UUID.randomUUID();  // The TU ID of the first segment
                                            // of the candidate TU. (This one
                                            // is referenced by footnote refs.
    private boolean hasTuChildren;    // Indicate if this tu has children
        
    /** Return TU entry's Identifier
     * @return the Translation Unit Identifier of this TU
     */
    public UUID getTuID() { return tuID;}
       
    /** Return the candidate Tu text as a String
     * @return the TU's text
     */
    public String getTuText() { return candidateTu.toString(); }

    /**
     * Append some text to this trans-unit
     * @param text The text to append.
     */
    public void append(String text) {
        candidateTu.append(text);
    }
    
    /**
     * Indicate whether this TU has imbedded (children) TUs
     * @param tOrF true if has childr
     */
    public void setHasTuChildren(boolean tOrF) {
        this.hasTuChildren = tOrF;
    }
    
    /**
     * Return whether or not thus TU has imbedded (children) TUs
     * @return true if has 1+ children, else false
     */
    public boolean hasChildren() {
        return this.hasTuChildren;
    }
}

/**
 * Utility class to implement a skeletal Attributes interface. (Yes,
 * this is a dirty rotten hack that needs to be reimplemented someday.)
 */
class ThinAttributes implements Attributes {
    private Map<String,String> attMap = new LinkedHashMap<String,String>();

    /** Return how many attributes there are
     * @return The number of attributes
     */
    public int getLength() {return attMap.size();}
    
    /** Return the ith fully-qualified attribute name
     * @param i The index to the attribute name (0 <= i < getLength())
     */
    public String getQName(int i) { 
        if (i >= getLength()) {
            return "";
        }
        
        Set<String> keySet = attMap.keySet();
        Iterator<String> keyIt = keySet.iterator();
        // The following will stop before the requested name
        for (int j = 0; j < i; j++) {
            String junk = keyIt.next();
        }
        
        // The next call will return the desired name
        return keyIt.next();
    }

    /** Return the ith attribute value
     * @param i The index to the value (0 <= i < getLength())
     */
    public String getValue(int i) {
        if (i >= getLength()) {
            return "";
        }
        
        Set<String> keySet = attMap.keySet();
        Iterator<String> keyIt = keySet.iterator();
        // The following will stop before the requested name
        for (int j = 0; j < i; j++) {
            String junk = keyIt.next();
        }
        
        // The next call will return the desired name
        String key = keyIt.next();
        return attMap.get(key);
    }

    // The following methods are defined in the Attributes interface, but
    // not meaningfully implemented here
    public int getIndex(String qName) {return -1;}
    public int getIndex(String uri, String localName) {return -1;}
    public String getLocalName(int index) { return "";}
    public String getType(String qName) { return "";}
    public String getType(int index) { return "";}
    public String getType(String uri, String localName) { return "";}
    public String getURI(int index) {return "";}
    public String getValue(String qName) {return "";}
    public String getValue(String uri, String localName) { return "";}
    
    /**
     * Store an attribute and its value
     * @param name The attribute's name
     * @param value The attribute's value
     */
    void putAttr(String name, String value) {
        attMap.put(name, value);
    }
}

/**
 * Class OdfHandler uses SAX to read and parse the XML files.
 */
public class OdfHandler extends DefaultHandler {
    public enum TagType { START, END, EMPTY }
    
    // Create a map of Style names that map to bold, italic, underline,
    // superscript and subscript. (The link ctype has a tag of its own:
    // <text:a/>
    // 1/29/07 WWhipple: Add wingdings to list of styles.
    private Map<String,String> styleMap = new HashMap<String,String>();

    // The elements that indicate the ctypes that we preserve in bx/mx
    // tags are children of <style:style> element. Specifically, they
    // are attributes of the <style:text-properties> element
    private String currentStyleName = "";
    
    // This one we will populate only if actually used:
    private int curLinkRid;

    private Stack<Integer> textSpanRidStack = new Stack<Integer>();
    
    /** The following stack records where currently open text:p tags were
     *  recorded--in tskeleton (as a text:p tag), or in format (as a bx
     *  tag). By checking the stack, we can make sure that we close
     *  the text:p tag in the same place it was opened.
     */
    private Stack<String> textPStack = new Stack<String>();
    
    private OutputStreamWriter outXliff;    // Where to write XLIFF
    private OutputStreamWriter outSkeleton;   // Where to write Target file
    private OutputStreamWriter outFormat;   // Where to write Format file
    private Locale sourceLang;              // Natural language of original
    private String docType;                 // Should be ODF
    private String originalFileName;        // For <file>'s "original" attribute
    
    private int curIndent = 0;              // How far to indent the next element
    
    private SegmentBoundary boundaryType;
    
    // All of the above share a common RID numbering space. The following is used each
    // time a new bx is encountered.
    private int nextAvailRid = 1;           // All of the above use the same numbering space
    
    // Counter for the unique identifiers for the bx/ex formatting tags:
    private int bxExXId = 1;
  
    private int curTagNum = 0;              // For skeleton
    
    // This will handle footnotes and other cases of TU's within TU's
    protected ArrayList<TuListEntry> tuList = new ArrayList<TuListEntry>();
    
    // Footnotes etc. will be written to the xliffAppendix and then
    // flushed to the xliff output stream at the end of document.
    private StringBuilder xliffAppendix = new StringBuilder();
    
    private boolean inNote;               // We're not in a note at present
    private boolean inAnnotation;         // We're not in an annotation either
    private boolean inWingdings;          // Not in a Wingdings font sequence

    private int maxTuDepth = 0;           // Keep track of how deep our text:p goes
    
    // Does this run need a prolog? an epilog? both?
    public enum XliffSegmentMode { PROLOG, EPILOG, BOTH }

    private XliffSegmentMode xliffSegmentMode = XliffSegmentMode.BOTH;  
    
    private OdfStateObject odfState;      // Preserve state across multiple calls

    // Regex and Matcher for span-tab-endspan block
    String tabPattern = "^(.*?)"
        // Match the opening text:span tag
        + "(<bx id=(['\"])\\d+\\3 +ctype=(['\"])x-odf-span\\4 +rid=(['\"])(\\d+)\\5 */>" // 1st rid is group 6
        // Then match the tab tab
        +  "<x id=(['\"])\\d+\\7 +ctype=(['\"])x-odf-tab\\8 */>"
        // ... and close the text:span
        +  "<ex id=(['\"])\\d+\\9 +rid=(['\"])\\6\\10 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    Matcher tabMatcher = Pattern.compile(tabPattern,Pattern.CASE_INSENSITIVE).matcher("");

    // Regex for bx-bx-text-ex-ex block
    String bxExPattern = "^(.*?)"
        // Match the first of two adjacent bx tags
        // Group 5 is 1st ctype; group 7 is 1st rid
        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
        // important of the two ctypes
        // 2nd ctype is group 10
        // 2nd rid is group 12
        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>)" 
        // Then stuff between the above bxes and the closing exes
        + "([^<].*?)"                            // At least 1 character long, group 13
        // Then the first ex (with rid matching the second bx above)
        +  "(<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\12\\16 */>"
        // And the second ex (with rid matching the first bx above)
        +  "<ex id=(['\"])\\d+\\17[^>]+?rid=(['\"])\\7\\18 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    Matcher bxExMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");

    // Regex for bx-bx-ex-ex block (no intervening text) to map to x tag.
    String bxExToXPattern = "^(.*?)"
        // Match the first of two adjacent bx tags
        // Group 5 is 1st ctype; group 7 is 1st rid
        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
        // important of the two ctypes
        // 2nd ctype is group 10
        // 2nd rid is group 12
        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>" 
        // Then the first ex (with rid matching the second bx above)
        +  "<ex id=(['\"])\\d+\\13[^>]+?rid=(['\"])\\12\\14 */>"
        // And the second ex (with rid matching the first bx above)
        +  "<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\7\\16 */>)"
        // Text that follows the span:
        +  "(.*)$";  // What follows

    Matcher bxExToXMatcher = Pattern.compile(bxExToXPattern,Pattern.CASE_INSENSITIVE).matcher("");
    
    // Map of Wingdings to Unicode characters
    private final char [] wingdings2char = {   
        // Treat 0-31 as undefined ...
        '\ufffd', // 0x00 -> Undefined
        '\ufffd', // 0x01 -> Undefined
        '\ufffd', // 0x02 -> Undefined
        '\ufffd', // 0x03 -> Undefined
        '\ufffd', // 0x04 -> Undefined
        '\ufffd', // 0x05 -> Undefined
        '\ufffd', // 0x06 -> Undefined
        '\ufffd', // 0x07 -> Undefined
        '\ufffd', // 0x08 -> Undefined
        '\ufffd', // 0x09 -> Undefined
        '\ufffd', // 0x0a -> Undefined
        '\ufffd', // 0x0b -> Undefined
        '\ufffd', // 0x0c -> Undefined
        '\ufffd', // 0x0d -> Undefined
        '\ufffd', // 0x0e -> Undefined
        '\ufffd', // 0x0f -> Undefined
        '\ufffd', // 0x10 -> Undefined
        '\ufffd', // 0x11 -> Undefined
        '\ufffd', // 0x12 -> Undefined
        '\ufffd', // 0x13 -> Undefined
        '\ufffd', // 0x14 -> Undefined
        '\ufffd', // 0x15 -> Undefined
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

        // After the space, the values map to various wing dings ...
        '\u0020', // 0x20 -> SPACE
        '\u270f', // 0x21 -> Pencil
        '\u2702', // 0x22 -> Black scissors
        '\u2701', // 0x23 -> Upper blade scissors
        '\ufffd', // 0x24 -> Spectacles (no equivalent)
        '\ufffd', // 0x25 -> Bell       (no equivalent)
        '\ufffd', // 0x26 -> Book       (no equivalent)
        '\ufffd', // 0x27 -> Candle     (no equivalent)
        '\u260e', // 0x28 -> Black telephone
        '\u2706', // 0x29 -> Telephone location sign
        '\u2709', // 0x2a -> Envelope
        '\ufffd', // 0x2b -> Envelope with address and stamp (no equivalent)
        '\ufffd', // 0x2c -> Closed mailbox with flag down   (no equivalent)
        '\ufffd', // 0x2d -> Closed mailbox with flag up     (no equivalent)
        '\ufffd', // 0x2e -> Open mailbox with flag up       (no equivalent)
        '\ufffd', // 0x2f -> Open mailbox with flag down     (no equivalent)
        '\ufffd', // 0x30 -> Folder closed                   (no equivalent)
        '\ufffd', // 0x31 -> Folder open                     (no equivalent)
        '\ufffd', // 0x32 -> Printed page with corner turned (no equivalent)
        '\ufffd', // 0x33 -> Printed page                    (no equivalent)
        '\ufffd', // 0x34 -> Stack of printed pages          (no equivalent)
        '\ufffd', // 0x35 -> Filing cabinet                  (no equivalent)
        '\u231b', // 0x36 -> Hourglass 
        '\u2328', // 0x37 -> Keyboard
        '\ufffd', // 0x38 -> Mouse                           (no equivalent)
        '\ufffd', // 0x39 -> Trackball                       (no equivalent)
        '\ufffd', // 0x3a -> Computer                        (no equivalent)
        '\ufffd', // 0x3b -> Hard disk                       (no equivalent)
        '\ufffd', // 0x3c -> 3 1/2" floppy disk              (no equivalent)
        '\ufffd', // 0x3d -> 5 1/4" floppy disk              (no equivalent)
        '\u2707', // 0x3e -> Tape drive
        '\u2703', // 0x3f -> Writing hand
        '\ufffd', // 0x40 -> Writing left hand               (no equivalent)
        '\u270c', // 0x41 -> Victory hand
        '\ufffd', // 0x42 -> Hand with thumb and finger looped (no equivalent)
        '\ufffd', // 0x43 -> Hand with thumb up                (no equivalent)
        '\ufffd', // 0x44 -> Hand with thumb down              (no equivalent)
        '\u261c', // 0x45 -> White left pointing index
        '\u261e', // 0x46 -> White right pointing index
        '\u261d', // 0x47 -> White up pointing index
        '\u261f', // 0x48 -> White down pointing index
        '\ufffd', // 0x49 -> Open hand                         (no equivalent)
        '\u263a', // 0x4a -> White smiling face
        '\ufffd', // 0x4b -> White neutral face                (no equivalent)
        '\u2639', // 0x4c -> White frowning face
        '\ufffd', // 0x4d -> Bomb                              (no equivalent)
        '\u2620', // 0x4e -> Skull and crossbones
        '\u2690', // 0x4f -> White flag
        '\ufffd', // 0x50 -> Pennant on pole                   (no equivalent)
        '\u2708', // 0x51 -> Airplane
        '\u263c', // 0x52 -> White sun with rays
        '\ufffd', // 0x53 -> Droplet                           (no equivalent)
        '\u2744', // 0x54 -> Snowflake
        '\u271e', // 0x55 -> White Latin cross (no equivalent--substitute Shadowed white Latin cross)
        '\u271e', // 0x56 -> Shadowed white Latin cross
        '\ufffd', // 0x57 -> Celtic cross                      (no equivalent)
        '\u2720', // 0x58 -> Maltese cross
        '\u2721', // 0x59 -> Star of David
        '\u262a', // 0x5a -> Star and crescent
        '\u262f', // 0x5b -> Yin Yang
        '\u0950', // 0x5c -> Devanagari Om
        '\u2638', // 0x5d -> Wheel of Dharma
        '\u2648', // 0x5e -> Aries
        '\u2649', // 0x5f -> Taurus
        '\u264a', // 0x60 -> Gemini
        '\u264b', // 0x61 -> Cancer
        '\u264c', // 0x62 -> Leo
        '\u264d', // 0x63 -> Virgo
        '\u264e', // 0x64 -> Libra
        '\u264f', // 0x65 -> Scorpio
        '\u2650', // 0x66 -> Sagittarius
        '\u2651', // 0x67 -> Capricorn
        '\u2652', // 0x68 -> Aquarius
        '\u2653', // 0x69 -> Pisces
        '\u0026', // 0x6a -> Ampersand
        '\u0026', // 0x6b -> Ampersand (again)
        '\u25cf', // 0x6c -> Black circle
        '\u274d', // 0x6d -> Shadowed white circle
        '\u25a0', // 0x6e -> Black square
        '\u25a1', // 0x6f -> White square
        '\ufffd', // 0x70 -> Bold white square                 (no equivalent)
        '\u2751', // 0x71 -> Lower right shadowed white square
        '\u2752', // 0x72 -> Upper right shadowed white square
        '\u2666', // 0x73 -> Small black diamond suit (no equivalent--use black diamond suit)
        '\u2666', // 0x74 -> Black diamond suit
        '\u25c6', // 0x75 -> Black diamond
        '\u2756', // 0x76 -> Black diamond minus white X
        '\u25c6', // 0x77 -> Small black diamond (no equivalent--use black diamond)
        '\u2327', // 0x78 -> X in a rectangle box
        '\u2353', // 0x79 -> APL functional symbol quad up caret
        '\u2318', // 0x7a -> Place of interest sign
        '\u2740', // 0x7b -> White florette
        '\u273f', // 0x7c -> Black florette
        '\u275d', // 0x7d -> Heave double turned comma quotation mark ornament
        '\u275e', // 0x7e -> Heavy double comma quotation mark ornament
        '\u25af', // 0x7f -> White vertical rectangle

        '\u24ea', // 0x80 -> Circled digit zero
        '\u2460', // 0x81 -> Circled digit one
        '\u2461', // 0x82 -> Circled digit two
        '\u2462', // 0x83 -> Circled digit three
        '\u2463', // 0x84 -> Circled digit four
        '\u2464', // 0x85 -> Circled digit five
        '\u2465', // 0x86 -> Circled digit six
        '\u2466', // 0x87 -> Circled digit seven
        '\u2467', // 0x88 -> Circled digit eight
        '\u2468', // 0x89 -> Circled digit nine
        '\u2469', // 0x8a -> Circled number ten
        '\u24ff', // 0x8b -> Negative circled digit zero
        '\u2776', // 0x8c -> Dingbat negative circled digit one
        '\u2777', // 0x8d -> Dingbat negative circled digit two
        '\u2778', // 0x8e -> Dingbat negative circled digit three
        '\u2779', // 0x8f -> Dingbat negative circled digit four
        '\u277a', // 0x90 -> Dingbat negative circled digit five
        '\u277b', // 0x91 -> Dingbat negative circled digit six
        '\u277c', // 0x92 -> Dingbat negative circled digit seven
        '\u277d', // 0x93 -> Dingbat negative circled digit eight
        '\u277e', // 0x94 -> Dingbat negative circled digit nine
        '\u277f', // 0x95 -> Dingbat negative circled number ten
        '\ufffd', // 0x96 -> Bud and leaf north east (no equivalent)
        '\ufffd', // 0x97 -> Bud and leaf north west (no equivalent)
        '\ufffd', // 0x98 -> Bud and leaf south west (no equivalent)
        '\ufffd', // 0x99 -> Bud and leaf south east (no equivalent)
        '\ufffd', // 0x9a -> Vine leaf bold north east (no equivalent)
        '\ufffd', // 0x9b -> Vine leaf bold north west (no equivalent)
        '\ufffd', // 0x9c -> Vine leaf bold south west (no equivalent)
        '\ufffd', // 0x9d -> Vine leaf bold south east (no equivalent)
        '\u00b7', // 0x9e -> Middle dot
        '\u2022', // 0x9f -> Bullet
        '\u25aa', // 0xa0 -> Black small square
        '\u25cb', // 0xa1 -> White circle
        '\ufffd', // 0xa2 -> Bold white circle         (no equivalent)
        '\ufffd', // 0xa3 -> Extra bold white circle   (no equivalent)
        '\u25c9', // 0xa4 -> Fisheye
        '\u25ce', // 0xa5 -> Bullseye
        '\ufffd', // 0xa6 -> Upper right shadowed white circle (no equivalent)
        '\u25aa', // 0xa7 -> Black small square (see 0xa0 above)
        '\u25fb', // 0xa8 -> White medium square
        '\ufffd', // 0xa9 -> Black three pointed star          (no equivalent)
        '\u2726', // 0xaa -> Black four pointed star
        '\u2705', // 0xab -> Black star
        '\u2736', // 0xac -> Six pointed black star
        '\u2734', // 0xad -> Eight pointed black star
        '\u2739', // 0xae -> Twelve pointed black star
        '\u2735', // 0xaf -> Eight pointed pinwheel star
        '\ufffd', // 0xb0 -> Square register mark              (no equivalent)
        '\u2316', // 0xb1 -> Position indicator
        '\u2727', // 0xb2 -> White four pointed star
        '\u2311', // 0xb3 -> Square lozenge
        '\ufffd', // 0xb4 -> Question mark in white diamone    (no equivalent)
        '\u272a', // 0xb5 -> Circled white star
        '\u2730', // 0xb6 -> Shadowed white star
        '\ufffd', // 0xb7 -> Clock at 1 o'clock                (no equivalent)
        '\ufffd', // 0xb8 -> Clock at 2 o'clock                (no equivalent)
        '\ufffd', // 0xb9 -> Clock at 3 o'clock                (no equivalent)
        '\ufffd', // 0xba -> Clock at 4 o'clock                (no equivalent)
        '\ufffd', // 0xbb -> Clock at 5 o'clock                (no equivalent)
        '\ufffd', // 0xbc -> Clock at 6 o'clock                (no equivalent)
        '\ufffd', // 0xbd -> Clock at 7 o'clock                (no equivalent)
        '\ufffd', // 0xbe -> Clock at 8 o'clock                (no equivalent)
        '\ufffd', // 0xbf -> Clock at 9 o'clock                (no equivalent)
        '\ufffd', // 0xc0 -> Clock at 10 o'clock               (no equivalent)
        '\ufffd', // 0xc1 -> Clock at 11 o'clock               (no equivalent)
        '\ufffd', // 0xc2 -> Clock at 12 o'clock               (no equivalent)
        '\ufffd', // 0xc3 -> White arrow pointing downwards then curving leftwards  (no equivalent)
        '\ufffd', // 0xc4 -> White arrow pointing downwards then curving rightwards (no equivalent)
        '\ufffd', // 0xc5 -> White arrow pointing upwards then curving leftwards    (no equivalent)
        '\ufffd', // 0xc6 -> White arrow pointing upwards then curving rightwards   (no equivalent)
        '\ufffd', // 0xc7 -> White arrow pointing leftwards then curving upwards    (no equivalent)
        '\ufffd', // 0xc8 -> White arrow pointing rightwards then curving upwards   (no equivalent)
        '\ufffd', // 0xc9 -> White arrow pointing leftwards then curving downwards  (no equivalent)
        '\ufffd', // 0xca -> White arrow pointing rightwards then curving downwards (no equivalent)
        '\ufffd', // 0xcb -> Quilt square                                           (no equivalent)
        '\ufffd', // 0xcc -> Quilt square inverted                                  (no equivalent)
        '\ufffd', // 0xcd -> Leaf counterclockwise south west                       (no equivalent)
        '\ufffd', // 0xce -> Leaf counterclockwise north west                       (no equivalent)
        '\ufffd', // 0xcf -> Leaf counterclockwise south east                       (no equivalent)
        '\ufffd', // 0xd0 -> Leaf counterclockwise north east                       (no equivalent)
        '\ufffd', // 0xd1 -> Leaf north west                                        (no equivalent)
        '\ufffd', // 0xd2 -> Leaf south west                                        (no equivalent)
        '\ufffd', // 0xd3 -> Leaf north east                                        (no equivalent)
        '\ufffd', // 0xd4 -> Leaf south east                                        (no equivalent)
        '\u232b', // 0xd5 -> Erase to the left
        '\u2326', // 0xd6 -> Erase to the right
        '\ufffd', // 0xd7 -> Three-D top-lighted leftwards arrowhead                (no equivalent)
        '\u27a2', // 0xd8 -> Three-D top-lighted rightwards arrowhead
        '\ufffd', // 0xd9 -> Three-D right-lighted upwards arrowhead                (no equivalent)
        '\ufffd', // 0xda -> Three-D left-lighted downwards arrowhead               (no equivalent)
        '\ufffd', // 0xdb -> Circled heavy white leftwards arrow                    (no equivalent)
        '\u27b2', // 0xdc -> Circled heavy white rightwards arrow
        '\ufffd', // 0xdd -> Circled heavy white upwards arrow                      (no equivalent)
        '\ufffd', // 0xde -> Circled heavy white downwards arrow                    (no equivalent)
        '\ufffd', // 0xdf -> Wide-headed leftwards arrow                            (no equivalent)
        '\ufffd', // 0xe0 -> Wide-headed rightwards arrow                           (no equivalent)
        '\ufffd', // 0xe1 -> Wide-headed upwards arrow                              (no equivalent)
        '\ufffd', // 0xe2 -> Wide-headed downwards arrow                            (no equivalent)
        '\ufffd', // 0xe3 -> Wide-headed north west arrow                           (no equivalent)
        '\ufffd', // 0xe4 -> Wide-headed north east arrow                           (no equivalent)
        '\ufffd', // 0xe5 -> Wide-headed south west arrow                           (no equivalent)
        '\ufffd', // 0xe6 -> Wide-headed south east arrow                           (no equivalent)
        '\ufffd', // 0xe7 -> Heavy wide-headed leftwards arrow                      (no equivalent)
        '\u2794', // 0xe8 -> Heavy wide-headed rightwards arrow
        '\ufffd', // 0xe9 -> Heavy wide-headed upwards arrow                        (no equivalent)
        '\ufffd', // 0xea -> Heavy wide-headed downwards arrow                      (no equivalent)
        '\ufffd', // 0xeb -> Heavy wide-headed north west arrow                     (no equivalent)
        '\ufffd', // 0xec -> Heavy wide-headed north east arrow                     (no equivalent)
        '\ufffd', // 0xed -> Heavy wide-headed south west arrow                     (no equivalent)
        '\ufffd', // 0xee -> Heavy wide-headed south east arrow                     (no equivalent)
        '\u21e6', // 0xef -> Leftwards white arrow
        '\u21e8', // 0xf0 -> Rightwards white arrow
        '\u21e7', // 0xf1 -> Upwards white arrow
        '\u21e9', // 0xf2 -> Downwards white arrow
        '\u2b04', // 0xf3 -> Left right white arrow
        '\u21f3', // 0xf4 -> Up down white arrow
        '\u2b00', // 0xf5 -> North east white arrow
        '\u2b01', // 0xf6 -> North west white arrow
        '\u2b03', // 0xf7 -> South west white arrow
        '\u2b02', // 0xf8 -> South east white arrow
        '\u25ad', // 0xf9 -> White rectangle
        '\u25ab', // 0xfa -> White small square
        '\u2717', // 0xfb -> Ballot X
        '\u2713', // 0xfc -> Check mark
        '\u2612', // 0xfd -> Ballot box with X
        '\u2611', // 0xfe -> Ballot box with check
        '\ufffd'  // 0xff -> Windows logo                       (no equivalent)
    };
    
    /**
     * This constructor sets up the OdfHandler to be notified as
     * each tag (etc.) is encountered in the HTML input stream.
     * This handler then does whatever is appropriate with the HTML
     * input.
     * @param outXliff Where to write the XLIFF 
     * @param outSkeleton Where to write the target
     * @param outFormat Where to write the format
     * @param sourceLang The language of this original
     * @param docType What kind of document is this
     * @param originalFileName The original file's name (required as an attribute
     *        in the "file" element)
     * @param boundary Indication of whether to do paragraph or sentence
     *        segmentation
     * @param odfState Object to maintain state across multiple calls
     * @param mode Whether this is being called to process content.xml or
     *        styles.xml.
     */
    public OdfHandler(OutputStreamWriter outXliff, 
            OutputStreamWriter outSkeleton, OutputStreamWriter outFormat,
            Locale sourceLang, String docType, String originalFileName,
            SegmentBoundary boundary, OdfStateObject odfState,
            String mode) {
        
        this.outXliff = outXliff;
        this.outSkeleton = outSkeleton;
        this.outFormat = outFormat;
        this.sourceLang = sourceLang;       // Natural language of original
        this.docType = docType;             // HTML most of the time. 
        this.originalFileName = originalFileName;
        
        if (mode.equals("styles.xml")) {
            xliffSegmentMode = XliffSegmentMode.EPILOG;
        }
        else if (mode.equals("content.xml")) {
            xliffSegmentMode = XliffSegmentMode.PROLOG;
        }

        if (boundary == null) {  // Default to SENTENCE segmentation 
            this.boundaryType = SegmentBoundary.SENTENCE;
        }
        else {
            this.boundaryType = boundary;       // paragraph or sentence?        
        }
        
        // Set out internal id counters (preserving values from earlier call if
        // this is a call for styles.xml
        this.odfState = odfState;
        this.nextAvailRid = odfState.getNextAvailRid();           
        this.bxExXId = odfState.getBxExXId();
        this.curTagNum = odfState.getCurSkelTagNum();
    }

    /**
     * Method called by SAX parser at the beginning of document parsing.
     */
    public void startDocument() throws SAXException {

        if (!xliffSegmentMode.equals(XliffSegmentMode.EPILOG)) {
            // Write the beginning of the XLIFF document
            try {
                // Write the "prolog" of the XLIFF file
                outXliff.write(Converter.xmlDeclaration);
                outXliff.write(Converter.startXliff);
                outXliff.write(indent() + "<file original='" 
                    + originalFileName.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\"", "&quot;")
                    + "' source-language='" + sourceLang.toString() 
                    + "' datatype='x-" + docType.toLowerCase() + "'>\r\n");
                outXliff.write(indent() + "<header");
                switch(boundaryType) {
                    case PARAGRAPH:  
                        outXliff.write(" lt:segtype='paragraph'");
                        break;
                    case SENTENCE:
                        outXliff.write(" lt:segtype='sentence'");
                        break;
                    default:
                        outXliff.write(" lt:segtype='sentence'");
                }
                outXliff.write(">\r\n" + indent('0') + "</header>\r\n" + indent('0') + "<body>\r\n");
                outXliff.flush();

            }
            catch(IOException e) {
                System.err.println("Error writing XLIFF's XML declaration and preliminaries.");
                System.err.println(e.getMessage());
            }

            // Also write the beginning of the format file
            try {
                outFormat.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\r\n");
                outFormat.write("<lt:LT xmlns:lt=\"http://www.lingotek.com/\">\r\n");
                outFormat.write("<tags formatting=''>\r\n");   // No value in formatting attr means CDATA
                outFormat.flush();
            }
            catch(IOException e) {
                System.err.println("Error writing format file's declaration and preliminaries.");
                System.err.println(e.getMessage());
            }
        }
    }
    
    /**
     * Method called whenever a start element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string if qualified names are not available
     * @param atts The specified or defaulted attributes.
     */
    public void startElement(String namespaceURI, 
			     String localName,
			     String qualifiedName,
			     Attributes atts) throws SAXException {
        // We earlier set the namespace-prefixes feature, so the qualified
        // names should be passed in.
	String elementName = qualifiedName;
        curTagNum++;
       
        /******************************************************************
         * Process styles (italics, etc.) that tend to appear towards the *
         * beginning of the ODF document.                                 *
         ******************************************************************/
        // The <style:style> element (which has the name referenced in text parts of the
        // document) is the parent of <style:text-properties>, which has the style
        // information of interest in the ctype attribute of bx/ex XLIFF tags
        if (elementName.equals("style:style")) {
            // Find the style name and remember it.
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    if (aName.equals("style:name")) {
                        currentStyleName = atts.getValue(i);
                    }
                }
            }
        }
        // style:text-properties is a child of style:style and has attrs that
        // indicate if the style is for bold, italic, underline, superscript
        // subscript (and other styles we aren't interested in ...)
        else if (elementName.equals("style:text-properties")) {
            // Look for styles of interest
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    // Handle WingDings (WWhipple, 1/29/7)
                    if (aName.equals("style:font-name") 
                        && atts.getValue(i).equalsIgnoreCase("Wingdings")) {
                        if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName,"wingdings");
                        }
                    }
                    // A bold font style?
                    else if (aName.equals("fo:font-weight")
                        || aName.startsWith("style:font-weight")) {
                        if (atts.getValue(i).equals("bold")) {
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),atts.getValue(i)));
                            }
                        }
                    }
                    // An italic font style?
                    else if (aName.equals("fo:font-style")
                        || aName.startsWith("style:font-style")) {
                        if (atts.getValue(i).equals("italic")) {
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),atts.getValue(i)));
                            }
                        }
                    }
                    // sub- or superscript?
                    else if (aName.equals("style:text-position")) {
                        if (atts.getValue(i).startsWith("sub")) {
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),"subscript"));
                            }
                        }
                        else if (atts.getValue(i).startsWith("super")) {
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),"superscript"));
                            }
                        }
                        else if (atts.getValue(i).startsWith("-")) {
                            // A negative percentage -> subscript
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),"subscript"));
                            }
                        }
                        else {
                            // The only other option is a positive percentage
                            // -> superscript
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),"superscript"));
                            }
                        }
                    }
                    // Underlining
                    else if (aName.equals("style:text-underline-type")) {
                        // Possible values: none, single, double:
                        if (! atts.getValue(i).equals("none")) {  // not none
                            if ((currentStyleName != null) && (currentStyleName.length() > 0)) {
                                styleMap.put(currentStyleName, 
                                    addCtypeStyle(styleMap.get(currentStyleName),"underline"));
                            }
                        }
                    }
                }
            }
        }

        /***************************************************************
         * We're done looking at preliminary information (styles, etc.).
         * Now look for actual text.
         ***************************************************************/
        
        /** 
         * All tags within a translation unit *must* have a representation
         * within the TU text ... either as a bx, mx or x (for the
         * moment, at least).
         */
        // Check for tab within a paragraph
        else if (elementName.equals("text:tab")) {
            if (tuList.size() > 0) {
                // Access the "top" entry in the list of Tu's and append text
                (tuList.get(tuList.size() - 1)).append("<x id='" + bxExXId + "' ctype='x-odf-tab'/>");
                
                // Write format file ... 
                writeFormatEntry(elementName, atts, bxExXId, TagType.EMPTY);
                bxExXId++;      // For next time
            }
        }

        // Check for a space within a paragraph
        else if (elementName.equals("text:s")) {
            if (tuList.size() > 0) {
                // Access the "top" entry in the list of Tu's and append text
                (tuList.get(tuList.size() - 1)).append("<x id='" + bxExXId + "' ctype='x-odf-s'/>");
                
                // Write format file ... 
                writeFormatEntry(elementName, atts, bxExXId, TagType.EMPTY);
                bxExXId++;      // For next time
            }
        }

        else if (elementName.equals("text:line-break")) {
            if (tuList.size() > 0) {
                // Access the "top" entry in the list of Tu's and append text
                (tuList.get(tuList.size() - 1)).append("<x id='" + bxExXId + "' ctype='lb'/>");
                
                // Write format file ... 
                writeFormatEntry(elementName, atts, bxExXId, TagType.EMPTY);
                bxExXId++;      // For next time
            }
        }
        
        // Hyperlink (anchor)
        else if (elementName.equals("text:a")) {
            // Access the "top" entry in the list of Tu's and append text
            if (tuList.size() > 0) {
                (tuList.get(tuList.size() - 1)).append("<bx id='" + bxExXId + 
                        "' ctype='link' rid='" + nextAvailRid + "'/>");

                // Save current rid for matching closing tag; increment next avail for next time
                curLinkRid = nextAvailRid;

                nextAvailRid++;
            
                // Write format file ...
                writeFormatEntry(elementName, atts, bxExXId, TagType.START);

                bxExXId++;      // For next bx, ex or x
            }
        }
        
        // Other of the 5 ctypes we remember
        else if (elementName.equals("text:span")) {
            // This signals the bx part of a bx/ex pair. Depending on
            // its attributes, there may be a ctype that we care about
            // (bold, italic, underline, superscript, subscript). We
            // determine that by looking at the text:style-name attribute
            // and comparing it with the style names we examined at the
            // beginning of this method.
            if (tuList.size() > 0) {
                String ctype = "";                   // No ctype yet
                String styleName = "";               // No style name yet
                if (atts != null) {
                    STYLE_NAME_SEARCH:
                    for (int i = 0; i < atts.getLength(); i++) {
                        String aName = atts.getQName(i); // Attr name
                        if (aName.equals("text:style-name")) {
                            styleName = atts.getValue(i);
                            // We're done; quit looking
                            break STYLE_NAME_SEARCH;
                        }
                    }
                }
                if ((styleName.length() > 0) && styleMap.containsKey(styleName)) {
                    ctype = styleMap.get(styleName);
                }

                // If this is a Wingdings style (typically converted from RTF),
                // we will convert it to unicode and not convert back (on export),
                // but leave as Unicode.
                if (ctype.equals("wingdings")) {
                    this.inWingdings = true;
                }
                else {
                    // Access the "top" entry in the list of Tu's and append a bx tag
                    TuListEntry entry = tuList.get(tuList.size() - 1);
                    entry.append("<bx id='" + bxExXId + "'");
                    if (ctype.length() > 0) {    // Include ctype if it has one
                        entry.append(" ctype='" + ctype + "'");
                    }
                    else {                       // Otherwise formulate one.
                        entry.append(" ctype='x-odf-span'");
                    }
                    entry.append(" rid='" + nextAvailRid + "'/>");

                    // Save current rid for matching closing tag. A stack (pushed
                    // at start-tag-time and popped at end-tag-time) should make
                    // the bxes and exes rids match up.
                    textSpanRidStack.push(Integer.valueOf(nextAvailRid));

                    // Increment next avail for next time
                    nextAvailRid++;

                    // Write format file ...
                    writeFormatEntry(elementName, atts, bxExXId, TagType.START);

                    bxExXId++;      // For next bx, ex or x
                }
            }
        }
   
        // A heading. (Can these be impedded in text:p's???
        else if (elementName.equals("text:h")) {
            // Create a place to write this tranlation unit
            tuList.add(new TuListEntry());      // characters method will write here next

            if (tuList.size() > this.maxTuDepth) {
                maxTuDepth = tuList.size();
            }
                
            // Write something to the temporary skeleton
            writeTSkeletonEntry(elementName, curTagNum, false, tuList.size());
        }

        // This starts a new paragraph.
        else if (elementName.equals("text:p")) {
            // If this paragraph has no parent TUs, treat it as the
            // beginning of a TU
            // Create a place to write this tranlation unit
            tuList.add(new TuListEntry());      // characters method will write here next
            
            if (tuList.size() > this.maxTuDepth) {
                maxTuDepth = tuList.size();
            }

            // Write something to the temporary skeleton
            writeTSkeletonEntry(elementName, curTagNum, false, tuList.size());
            
            // If this is not a top-level text:p, put a link from the parent
            // TU to this one
            if (tuList.size() > 1) {  // Greater than 1 after we added the entry above
                
                // Also add a bx entry at the root of the  "current" (new) TU 
                // that maps to this text:p tag
                String ctype = inNote ? "x-odf-note-target" : inAnnotation ? "x-odf-annotation-target"
                        : "x-odf-imbedded-text-p";
                (tuList.get(tuList.size() - 1)).append("<bx id='" + bxExXId + 
                        "' ctype='" + ctype + "' rid='" + nextAvailRid + "'/>");
//                      "' rid='" + nextAvailRid + "' ctype='" + ctype + "'/>");

                // Save current rid for matching closing tag. A stack (pushed
                // at start-tag-time and popped at end-tag-time) should make
                // the bxes and exes rids match up.
                textSpanRidStack.push(Integer.valueOf(nextAvailRid));

                // Increment next avail for next time
                nextAvailRid++;
            
                // Write format file ... This will let us reconstruct the original
                // text:p and its attributes on export
                writeFormatEntry(elementName, atts, bxExXId, TagType.START);

                bxExXId++;      // For next bx, ex or x
            }
        }

        // Some other tag we aren't specifically checking for. If we are somewhere
        // in a TU (as evidenced by the tuList size being greater than 0), then
        // store this tag as a bx tag in the current TU); otherwise, just write
        // to the temporary skeleton
        else {
            if (tuList.size() > 0) {
                if (elementName.equals("text:note")) {
                    inNote = true;      // We set this because TUs for notes go at the *end* of the XLIFF
                }
                else if (elementName.equals("office:annotation")) {
                    this.inAnnotation = true;
                }

                TuListEntry entry = tuList.get(tuList.size() - 1);
                entry.append("<bx id='" + bxExXId + "'");
                entry.append(" ctype='x-odf-" + elementName.replace(":","-") 
                        + "' rid='" + nextAvailRid + "'/>");
//              entry.append(" rid='" + nextAvailRid + "' ctype='x-odf-"
//                      + elementName.replace(":","-") + "'/>");

                // Save current rid for matching closing tag. A stack (pushed
                // at start-tag-time and popped at end-tag-time) should make
                // the bxes and exes rids match up.
                textSpanRidStack.push(Integer.valueOf(nextAvailRid));

                // Increment next avail for next time
                nextAvailRid++;

                // Write format file ...
                writeFormatEntry(elementName, atts, bxExXId, TagType.START);

                bxExXId++;      // For next bx, ex or x
            }
        }
    }
    
    /**
     * Method called whenever an end element is encountered
     * @param namespaceURI The URI of the namespace
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qualifiedName The qualified name (with prefix), or the empty string if qualified names are not available
     */
    public void endElement(String namespaceURI, 
			   String localName,
			   String qualifiedName) throws SAXException {
	
        String elementName = qualifiedName;
        curTagNum++;
        
        if (elementName.equals("style:style")) {
            // Cancel the style name we set in startElement
            currentStyleName = "";
        }

        // The tab and s[pace] elements are always "empty". We wrote them
        // to the format file that way in startElement, so we will
        // ignore them here.
        else if (elementName.equals("text:tab")
                || elementName.equals("text:s")
                || elementName.equals("text:line-break")) {
            return;                    // Do nothing (ignore the end tags)
        }

        // End of a hyperlink/anchor
        else if (elementName.equals("text:a")) {
            if (tuList.size() > 0) {
                // Access the "top" entry in the list of Tu's and append text
                TuListEntry entry = tuList.get(tuList.size() - 1);

                // Write to the XLIFF stream
                entry.append("<ex id='" + bxExXId + "' rid='" + curLinkRid + "'/>");
                // Write format file ...
                writeFormatEntry(elementName, null, bxExXId, TagType.END);

                bxExXId++;      // For next bx, ex or x
            }
        }

        // End of one of the 5 ctypes we remember
        else if (elementName.equals("text:span")) {
            // This signals the ex part of a bx/ex pair. 
            if (tuList.size() > 0) {
            
                // If this span closes a run of Wingdings chracters, just note
                // that we are leaving the run. We didn't use a bx at the beginning
                // of the wingdings, and won't record an ex now.
                if (this.inWingdings) {
                    this.inWingdings = false;
                }
                else {
                    // Get the rid that matches the bx that this ex closes
                    String ridStr = textSpanRidStack.pop().toString();

                    // Access the "top" entry in the list of Tu's and append a bx tag
                    (tuList.get(tuList.size() - 1)).append("<ex id='" + bxExXId + "' rid='" + ridStr + "'/>");

                    // Write format file ...
                    writeFormatEntry(elementName, null, bxExXId, TagType.END);

                    bxExXId++;      // For next bx, ex or x
                }
            }
        }

        // A heading has ended
        else if (elementName.equals("text:h")) {
            writeTu();               // Write out the TU at the top of the stack
            
            // Write something to the temporary skeleton
            // The size is the size *before* poppint the stack, which was popped
            // by WriteTu
            writeTSkeletonEntry(elementName, curTagNum, true, tuList.size() + 1);
        }
        

        // This ends a paragraph.
        else if (elementName.equals("text:p")) {
            // If the TU stack size is greater than 1, this is the
            // end of a text:p within a text:p. In that case, we need to
            // represent that ending tag as an ex tag in the leaf TU
            // (to match the opening bx tag that we added in the startElement
            // method).
            
            // Does this TU have any children (imbedded TUs)?
            boolean hasTuChildren = tuList.get(tuList.size() - 1).hasChildren();
            // Does it have any translatable text?
            boolean hasTranslatableText = false;  // false until shown otherwise
            
            if (tuList.size() > 1) {
                // Get the rid that matches the bx that this ex closes
                String ridStr = textSpanRidStack.pop().toString();

                // Access the "top" entry in the list of Tu's and append an ex tag
                (tuList.get(tuList.size() - 1)).append("<ex id='" + bxExXId + 
                    "' rid='" + ridStr + "'/>");
            
                // Write format file ...
                writeFormatEntry(elementName, null, bxExXId, TagType.END);

                bxExXId++;      // For next bx, ex or x

                // See if the text:p that is being closed actually has any
                // translatable text. (There are often "placeholder" text:p
                // elements that contain no text.)
                // If the text:p has no translatable text, writeTu will (quietly)
                // not create a new trans-unit element. If that is the case, we
                // still need to preserve all the xliff tags for exporting the
                // document later.
                String tuText = (tuList.get(tuList.size() - 1)).getTuText();
                if (TuPreener.markCoreTu(tuText).length() > 0) {
                    hasTranslatableText = true;
                }
                
                if (hasTranslatableText) {
                    // text:p contains translatable text. Create an x tag
                    // in the parent TU that references this one.
                    
                    // Get the TU ID of the leaf TU (the one we're closing)
                    UUID curTuId = (tuList.get(tuList.size() - 1)).getTuID();
                    // Add it to the parent TU in an <x/> tag
                    String ctype = inNote ? "x-odf-note-ref" : inAnnotation ? "x-odf-annotation-ref"
                            : "x-odf-child-text-p";
                    (tuList.get(tuList.size() - 2)).append("<x id='" + bxExXId + "' xid='" 
                          + curTuId.toString() + "' ctype='" + ctype + "'/>");
                    
                    // Note: The above x's id doesn't exist in the format file.
                    // We use the xid to retrieve the contents of the child
                    // TU
                }
                else {
                    // The text:p we're closing doesn'thave translatable text, but it
                    // *does* have some xliff tags (by now)--tags which have 
                    // format file mappings that are necessary to export the XLIFF
                    // back to the original format (in a target language)
                    // We need to put those tags in a format file entry (which will be
                    // recursively expanded), and insert an x tag in the parent tu
                    // with an id that references what we put in the format file.
                    (tuList.get(tuList.size() - 2)).append("<x id='" + bxExXId 
                            + "' ctype='x-odf-empty-text-p'/>");

                    // Get a fresh copy of tuText (one that includes the matching
                    // ex tag for the /text:p that we added bove.)
                    tuText = (tuList.get(tuList.size() - 1)).getTuText();
                    writeFormatLiteral(tuText, bxExXId);
                    
                    // Even though this TU might not have translatable text, it
                    // *might* have child TU's, referenced by x tags in the xliff.
                    // In that case, we need to put a placeholder in the skeleton file
                    // so that the exporter can retrieve that XLIFF and follow the
                    // xid's to find children TUs
                    if (tuList.get(tuList.size() - 1).hasChildren()) {
                        try {
                            outSkeleton.write("<format id='" + bxExXId + "' depth='" + (tuList.size()) + "'>\r\n");
                            outSkeleton.flush();
                        }
                        catch(IOException e) {
                            System.err.println("Error while writing skeleton file.");
                            System.err.println(e.getMessage());
                        }
                    }
                    
                }

                bxExXId++;      // For next bx, ex or x                
            }
            else {  // This is a "root"-level TU
                // If it doesn't have any translatable text, but still has children,
                // we need to make sure we capture the XLIFF that references the
                // children in a format entry
                String tuText = (tuList.get(tuList.size() - 1)).getTuText();
                if (TuPreener.markCoreTu(tuText).length() > 0) {
                    hasTranslatableText = true;
                }
                
                if ((!hasTranslatableText) && hasTuChildren) {
                    // Get a fresh copy of tuText (one that includes the matching
                    // ex tag for the /text:p that we added bove.)
                    tuText = (tuList.get(tuList.size() - 1)).getTuText();
                    writeFormatLiteral(tuText, bxExXId);
                    
                    // Put a placeholder in the tskeleton file.
                    try {
                        outSkeleton.write("<format id='" + bxExXId + "' depth='" + (tuList.size()) + "'>\r\n");
                        outSkeleton.flush();
                    }
                    catch(IOException e) {
                        System.err.println("Error while writing skeleton file.");
                        System.err.println(e.getMessage());
                    }
                    
                    bxExXId++;      // For next bx, ex or x                                
                }

            }
            
            writeTu();          // Write out the TU at the top of the stack
            
            writeTSkeletonEntry(elementName, curTagNum, true, tuList.size() + 1);
        }

        // Some other tag we aren't specifically checking for. If we are somewhere
        // in a TU (as evidenced by the tuList size being greater than 0), then
        // store this tag as a bx tag in the current TU); otherwise, just write
        // to the temporary skeleton
        else {
            if (tuList.size() > 0) {
                if (elementName.equals("text:note")) {
                    inNote = false;
                }
                else if (elementName.equals("office:annotation")) {
                    inAnnotation = false;
                }
                
                // Get the rid that matches the bx that this ex closes
                if (textSpanRidStack.empty()) {
                    System.err.println("textSpanRidStack is empty when about"
                            + " to pop the stack. Elementname is " + elementName);
                }
                String ridStr = textSpanRidStack.pop().toString();

                // Access the "top" entry in the list of Tu's and append a bx tag
                TuListEntry entry = tuList.get(tuList.size() - 1);
                entry.append("<ex id='" + bxExXId + "' rid='" + ridStr + "'/>");

                // Write format file ...
                writeFormatEntry(elementName, null, bxExXId, TagType.END);

                bxExXId++;      // For next bx, ex or x

            }
        }
    }

    /**
     * Called whenever characters are encountered
     * @param ch Array containing characters encountered
     * @param start Position in array of first applicable character
     * @param length How many characters are of interest?
     */
    public void characters(char[] ch,
			   int start,
			   int length) throws SAXException {
        String theString = new String(ch, start, length);

        // If this isn't a blank line add the characters to the TU buffer at the
        // top of the stack.
//        if (! theString.matches("^\\s*$")) {
            if (tuList.size() > 0) {       //
                if (inWingdings) {
                    theString = mapWingdings2Unicode(theString);
                }
                (tuList.get(tuList.size() - 1)).append(TuStrings.escapeTuString(theString));
            }
//        }
    }

    /**
     * When the end-of-document is encountered, save the "candidate
     * epilog" (the characters that follow the final TU), etc.
     */
    public void endDocument()
	throws SAXException {
        
        try {
            // If we have any footnotes etc., write their TUs
            if (xliffAppendix.length() > 0) {
                outXliff.write(xliffAppendix.toString());
                xliffAppendix.setLength(0);  // In case anyone cares
            }
        }
        catch(IOException e) {
            System.err.println("Error writing footnotes (etc.) at end of document.");
            System.err.println(e.getMessage());
        }
        
        
        if (!xliffSegmentMode.equals(XliffSegmentMode.PROLOG)) {
            // Finish off the XLIFF file:
            try {
//                // If we have any footnotes etc., write their TUs
//                if (xliffAppendix.length() > 0) {
//                    outXliff.write(new String(xliffAppendix));
//                    xliffAppendix.setLength(0);  // In case anyone cares
//                }

                outXliff.write(indent('-') + "</body>\r\n"); // Close the body element
                outXliff.write(indent('-') + "</file>\r\n"); // Close the file element
                outXliff.write("</xliff>\r\n");               // Close the xliff element
                outXliff.flush();             // Well-bred writers flush when finished

            }
            catch(IOException e) {
                System.err.println("Error writing tags at end of XLIFF document.");
                System.err.println(e.getMessage());
            }

            // Also finish off the format file:
            try {
                outFormat.write("</tags>\r\n");
                outFormat.write("</lt:LT>\r\n");
                outFormat.flush();
            }
            catch(IOException e) {
                System.err.println("Error writing tags at end of format document.");
                System.err.println(e.getMessage());
            }

            // Close the skeleton file
            try {
//                outSkeleton.write("<maxtudepth value='" + maxTuDepth + "'>\n");
                outSkeleton.flush();
            }
            catch(IOException e) {
                System.err.println("Error flushing skeleton stream.");
                System.err.println(e.getMessage());
            }
        }
        
        else {   // Not processing styles
            // Close the skeleton file
            try {
                outSkeleton.write("<maxtudepth value='" + maxTuDepth + "'>\n");
                outSkeleton.flush();
            }
            catch(IOException e) {
                System.err.println("Error flushing skeleton stream.");
                System.err.println(e.getMessage());
            }
            
            // Save id counters for next call (for styles.xml).
            this.odfState.setNextAvailRid(this.nextAvailRid); 
            this.odfState.setBxExXId(this.bxExXId);
            this.odfState.setCurSkelTagNum(this.curTagNum);
            
        }
    }

    /**
     * Intended for the client to call after parsing is complete, this method
     * returns the maximum TU depth (i.e. the depth to which text:p elements
     * contain text:p elements, for example).
     * @return the maximum TU depth
     */
    public int getTuDepth() {
        return this.maxTuDepth;
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
     * Passed the full text of an ODF Translation Unit source (including
     * core start and end markers), check for bx/x/ex sequences that represent
     * office:annotation structures. Collapse them into a single x tag that
     * references the TU that contains the text of the annotation. Add a format
     * file entry that maps the single x tag to the bx/x/ex tag sequence.
     * A typical office:annotation sequence looks something like:
     * <pre>
     * &lt;id="244" ctype="x-odf-span" rid="121"/&gt;
     *   &lt;bx id="245" rid="122" ctype="x-odf-office-annotation"/&gt;
     *     &lt;bx id="246" rid="123" ctype="x-odf-dc-creator"/&gt;Weldon Whipple
     *     &lt;ex id="247" rid="123"/&gt;
     *     &lt;bx id="248" rid="124" ctype="x-odf-dc-date"/&gt;2006-10-24T00:00:00
     *     &lt;ex id="249" rid="124"/&gt;
     *     &lt;x id="252" xid="738becd0-8a5d-4e37-8663-580ff7e66534" ctype="x-odf-annotation-ref"/&gt;
     *   &lt;ex id="253" rid="122"/&gt;
     * &lt;ex id="254" rid="121"/&gt;
     * </pre>
     * <p>This method will replace each such sequence with a single x element that
     * references the xid of the trans-unit that has the text of the annotation.
     * (This amounts to replacing all of the above with the x tag shown in the example,
     * with a different (and unique) id attribute).
     * <p>This assumes that the creator and date elements do not contain translatable
     * text.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseAnnotations (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // See if the input string includes the office-annotation ctype. If it
        // doesn't, return the input string.
        if (in.indexOf("x-odf-office-annotation") == -1) {
            return in;
        }

        // Loop through core text looking for the office annotation sequence.
        // When one is found, create a new x tag that maps to the sequence
        
        // Regex for office-annotation block
        String oaRE = "^(.*?)"
            // Match the opening text:span tag
            + "(<bx id=(['\"])\\d+\\3 +ctype=(['\"])x-odf-span\\4 +rid=(['\"])(\\d+)\\5 */>" // 1st rid is group 6
            // And the offication:annotation tag
            +  "<bx id=(['\"])\\d+\\7 [^>/]*?ctype=(['\"])x-odf-office-annotation\\8.*?/>" 
            // ... and the creator tag
            +  "<bx id=(['\"])\\d+\\9 [^>/]*?ctype=(['\"])x-odf-dc-creator\\10.*?/>"
            // Then the creator's name, etc.
            +  ".*?"                                                             // Creator
            // Closing creator tag
            +  "<ex id=(['\"])\\d+\\11.*?/>"
            // Match the date
            +  "<bx id=(['\"])\\d+\\12 [^>/]*?ctype=(['\"])x-odf-dc-date\\13.*?/>"
            +  "\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d"                 // Timestamp
            +  "<ex id=(['\"])\\d+\\14.*?/>"
            // Then match the reference to the TU that has the annotation text.
            +  "(<x id=(['\"]))\\d+(\\16 +xid=(['\"])[^'\"]+\\18 +ctype=(['\"])x-odf-annotation-ref\\19.*?/>)"
            // Close the office:annotation
            +  "<ex id=(['\"])\\d+\\20 +rid=(['\"])\\d+\\21.*?/>"
            // ... and the text:span
            +  "<ex id=(['\"])\\d+\\22 +rid=(['\"])\\6\\23.*?/>)"
            // Text that follows the annotation:
            +  "(.*)$";  // What follows

        Matcher oaM = Pattern.compile(oaRE,Pattern.CASE_INSENSITIVE).matcher("");

        String newTu = "";           // pfxText + TuPreener.CORE_START_TAG;  // The beginnings of a new TU
        String tuTail = in;          // We'll "eat" this as we loop through a matching TU
        String oaBlock = "";         // The current block of oa tags, etc.
        String xPrefix = "";         // Existing x tag up to the id value 
        String xSuffix = "";         // Existing x tag after the id valud
        
        while (tuTail.length() > 0) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            oaM.reset(tuTail);   // Search what's left of the TU
            if (oaM.find()) {
                newTu += oaM.group(1);     // Append characters before the annotation block
                oaBlock = oaM.group(2);    // The tags we will replace with a single x
                xPrefix = oaM.group(15);   // Beginning of existing x tag
                xSuffix = oaM.group(17);   // End of existing x tag
                tuTail  = oaM.group(24);   // Remainder of core text ... for next iteration.

                writeFormatLiteral(oaBlock,bxExXId);

                // Substitute an x tag for the office:annotation block (in the new TU)
                // Make it match the original except for the id attribute.
                newTu += xPrefix + bxExXId + xSuffix;

                // Increment the x/bx/ex id for the next format file writer
                bxExXId++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }
        
        // If there are non-oa-matching characters in tuTail (highly probable), 
        // append them to newTu, followed by the ending x-coretext mrk tag and the 
        // core suffix
        return (newTu + tuTail);   //  + TuPreener.CORE_END_MRK + sfxText);
    }

    /**
     * Passed the full text of an ODF Translation Unit source (including
     * core start and end markers), check for bx/x/ex sequences that represent
     * text:note structures. Collapse them into a single x tag that
     * references the TU that contains the text of the note. Add a format
     * file entry that maps the single x tag to the bx/x/ex tag sequence.
     * A typical text:note sequence looks something like:
     * <pre>
     * &lt;bx id="244" ctype="x-odf-span" rid="121"/&gt;
     *   &lt;bx id="245" rid="122" ctype="x-odf-text-note"/&gt;
     *     &lt;bx id="246" rid="123" ctype="x-odf-text-note-citation"/&gt;2
     *     &lt;ex id="247" rid="123"/&gt;
     *     &lt;bx id="248" rid="124" ctype="x-odf-text-note-body"/&gt;
     *       &lt;x id="252" xid="738becd0-8a5d-4e37-8663-580ff7e66534" ctype="x-odf-note-ref"/&gt;
     *     &lt;ex id="253" rid="124"/&gt;
     *   &lt;ex id="254" rid="122"/&gt;
     * &lt;ex id="254" rid="121"/&gt;
     * </pre>
     * <p>This method will replace each such sequence with a single x element that
     * references the xid of the trans-unit that has the text of the footnote.
     * (This amounts to replacing all of the above with the x tag shown in the example,
     * with a different (and unique) id attribute).
     * <p>This assumes that the citation (footnote number) doesn't include translatable
     * text.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseFootnotes (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }


        // See if the input string includes the text:note ctype. If it
        // doesn't, return the input string.
        if (in.indexOf("x-odf-text-note") == -1) {
            return in;
        }

        // Loop through core text looking for the text:note sequence.
        // When one is found, create a new x tag that maps to the sequence
        
        // Regex for text-note block
        String noteRE = "^(.*?)"
            // Match the opening text:span tag
            + "((?:<bx id=(['\"])\\d+\\3 +ctype=(['\"])x-odf-span\\4 +rid=(['\"])(\\d+)\\5 */>)?" // 1st rid is group 6
            // And the text:note tag
            +  "<bx id=(['\"])\\d+\\7 [^>/]*?ctype=(['\"])x-odf-text-note\\8.*?/>" 
            // ... footnote number (citation)
            +  "<bx id=(['\"])\\d+\\9 [^>/]*?ctype=(['\"])x-odf-text-note-citation\\10.*?/>"
            // Then the text of the "number""
            +  ".*?"                                                             // Creator
            // Closing citation tag:
            +  "<ex id=(['\"])\\d+\\11.*?/>"
            // Now the text-note body
            +  "<bx id=(['\"])\\d+\\12 [^>/]*?ctype=(['\"])x-odf-text-note-body\\13.*?/>"
            // Then match the reference to the TU that has the text of the note
            +  "(<x id=(['\"]))\\d+(\\15 +xid=(['\"])[^'\"]+\\17 +ctype=(['\"])x-odf-note-ref\\18.*?/>)"
            // Close the text-note-body:
            +  "<ex id=(['\"])\\d+\\19 +rid=(['\"])\\d+\\20.*?/>"
            // Close the text:note
            +  "<ex id=(['\"])\\d+\\21 +rid=(['\"])\\d+\\22.*?/>"
            // ... and the text:span
            +  "(?:<ex id=(['\"])\\d+\\23 +rid=(['\"])\\6\\24.*?/>)?)"
            // Text that follows the annotation:
            +  "(.*)$";  // What follows

        Matcher noteM = Pattern.compile(noteRE,Pattern.CASE_INSENSITIVE).matcher("");

        String newTu = "";           // The beginnings of a new TU
        String tuTail = in;          // We'll "eat" this as we loop through a matching TU
        String noteBlock = "";       // The current block of note tags, etc.
        String xPrefix = "";         // Existing x tag up to the id value 
        String xSuffix = "";         // Existing x tag after the id valud
        
        while (tuTail.length() > 0) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            noteM.reset(tuTail);   // Search what's left of the TU
            if (noteM.find()) {
                newTu += noteM.group(1);     // Append characters before the note block
                noteBlock = noteM.group(2);  // The tags we will replace with a single x
                xPrefix = noteM.group(14);   // Beginning of existing x tag
                xSuffix = noteM.group(16);   // End of existing x tag
                tuTail  = noteM.group(25);   // Remainder of core text ... for next iteration.

                writeFormatLiteral(noteBlock,bxExXId);

                // Substitute an x tag for the text:note block (in the new TU)
                // Make it match the original except for the id attribute.
                newTu += xPrefix + bxExXId + xSuffix;

                // Increment the x/bx/ex id for the next format file writer
                bxExXId++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }
        
        // If there are non-oa-matching characters in tuTail (highly probable), 
        // append them to newTu, followed by the ending x-coretext mrk tag and the 
        // core suffix
        return (newTu + tuTail);      //ltlt + TuPreener.CORE_END_MRK + sfxText);
    }

//////////////////////////////////////////////
    
    
    /**
     * Passed the full text of an ODF Translation Unit source, check for nested
     * bx and ex tags. Replace multiply nested bx/ex tags with a single pair
     * of bx/ex tags, adding a format file entry to map the single bx tag to the
     * bx tags it replaces and the single ex tag to the ex tags it replaces.
     * A typical nested bx/ex sequence looks something like:
     * <pre>
     * &lt;bx id="1432" ctype="x-odf-span" rid="578"/&gt;
     *    &lt;bx id="1433" rid="579" ctype="x-odf-text-page-number"/&gt;
     *        107
     *    &lt;ex id="1434" rid="579"/&gt;
     * &lt;ex id="1435" rid="578"/&gt;
     * &lt;bx id="1436" ctype="x-odf-span" rid="580"/&gt;
     *    &lt;x id="1437" ctype="x-odf-tab"/&gt; Associate’s Guide
     * &lt;ex id="1438" rid="580"/&gt;
     * </pre>
     * <p>Following processing by this method, it will look something like:
     * <pre>
     * &lt;bx id="1439" rid="581" ctype="x-odf-text-page-number"/&gt;
     *    107
     * &lt;ex id="1440" rid="581"/&gt;
     * &lt;bx id="1436" ctype="x-odf-span" rid="580"/&gt;
     *    &lt;x id="1437" ctype="x-odf-tab"/&gt; Associate’s Guide
     * &lt;ex id="1438" rid="580"/&gt;
     * </pre>
     * where the double bx/ex tags around "107" are replaced by a single bx and
     * ex pair.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseNestedBxEx (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // If the input string doesn't include at least one bx tag, return the
        // input string unchanged.
        if (in.indexOf("<bx") == -1) {
            return in;
        }

        // Loop through the text looking for the span-tab-endspan sequence.
        // When one is found, create a new x-odf-tab tag that maps to the sequence
        
//        // Regex for span-tab-endspan block
//        String bxExPattern = "^(.*?)"
//            // Match the first of two adjacent bx tags
//            // Group 5 is 1st ctype; group 7 is 1st rid
//            + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
//            // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
//            // important of the two ctypes
//            // 2nd ctype is group 10
//            // 2nd rid is group 12
//            + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>)" 
//            // Then stuff between the above bxes and the closing exes
//            + "([^<].*?)"                            // At least 1 character long, group 13
//            // Then the first ex (with rid matching the second bx above)
//            +  "(<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\12\\16 */>"
//            // And the second ex (with rid matching the first bx above)
//            +  "<ex id=(['\"])\\d+\\17[^>]+?rid=(['\"])\\7\\18 */>)"
//            // Text that follows the span:
//            +  "(.*)$";  // What follows
//
//        Matcher bxExMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");

        String curTuState = in;   // Start with what was passed in.
        
        // Two conditions might exist:
        // 1. 3+ levels of adjacent nesting might occur
        // 2. Nesting at the beginning of the TU might be followed by more
        //    nesting later on in the TU.
        boolean done = false;
        
        while (! done) {
            // Search on the remainder of the text
            // Start by checking the next nested tag.
            bxExMatcher.reset(curTuState);   // Search what's left of the TU
            if (bxExMatcher.find()) {
                String prefix = bxExMatcher.group(1);  // Before the first bx
                String bxBx   = bxExMatcher.group(2);  // First two adjacent bxes
                String enclosed = bxExMatcher.group(13); // Enclosed by bxbx exex 
                String exEx   = bxExMatcher.group(14); // Matching adjacent exes
                String suffix = bxExMatcher.group(19); // AFter last ex
                String outerCtype = bxExMatcher.group(5);  // Outer ctype
                String innerCtype = bxExMatcher.group(10); // Inner ctype

                // Write adjacent bxes to format file
                writeFormatLiteral(bxBx,bxExXId);
                // and increment the id
                int bxId = bxExXId++;
                
                // Ditto for the adjacent exes:
                writeFormatLiteral(exEx,bxExXId);
                int exId = bxExXId++;

                // Select a ctype that will make most sense to the translator
                // If both start with "x-", use the inner
                // If one starts with "x-", use the other one
                // If neither starts with "x-", prefer "link", else inner ctype
                String preferredCtype = "";
                if (outerCtype.equals("link") || innerCtype.equals("link")) {
                    preferredCtype = "link";
                }
                else if (outerCtype.startsWith("x-")) {
                    preferredCtype = innerCtype;
                }
                else { // outer *doesn't* start with "x-"
                    if (innerCtype.startsWith("x-")) {
                        preferredCtype = outerCtype;
                    }
                    else {  // Neither starts with "x-"; prefer inner
                        preferredCtype = innerCtype;
                    }
                }
                
                // Reconstruct the string with fewer bx/ex elements.
                curTuState = prefix + "<bx id='" + bxId + "' ctype='" + preferredCtype
                        + "' rid='" + nextAvailRid + "'/>"
                        + enclosed
                        + "<ex id='" + exId + "' rid='" + nextAvailRid + "'/>"
                        + suffix;

                // Increment nextAvailRid
                nextAvailRid++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }

//    // Regex for bx-bx-ex-ex block (no intervening text) to map to x tag.
//    String bxExToXPattern = "^(.*?)"
//        // Match the first of two adjacent bx tags
//        // Group 5 is 1st ctype; group 7 is 1st rid
//        + "(<bx id=(['\"])\\d+\\3[^>]+?ctype=(['\"])([^'\"]*)\\4[^>]+?rid=(['\"])(\\d+)\\6[^/>]*/>"
//        // Then the second immediately adjoining bx tag. Capture its ctype--it is the more
//        // important of the two ctypes
//        // 2nd ctype is group 10
//        // 2nd rid is group 12
//        + "<bx id=(['\"])\\d+\\8[^>]+?ctype=(['\"])([^'\"]*)\\9[^>]+?rid=(['\"])(\\d+)\\11[^/>]*/>" 
//        // Then the first ex (with rid matching the second bx above)
//        +  "<ex id=(['\"])\\d+\\13[^>]+?rid=(['\"])\\12\\14 */>"
//        // And the second ex (with rid matching the first bx above)
//        +  "<ex id=(['\"])\\d+\\15[^>]+?rid=(['\"])\\7\\16 */>)"
//        // Text that follows the span:
//        +  "(.*)$";  // What follows
//
//    Matcher bxExToXMatcher = Pattern.compile(bxExPattern,Pattern.CASE_INSENSITIVE).matcher("");
        
        // 4/11/2007: Convert <bx/><bx/><ex/><ex/> (with no intervening text) to a single x.
        done = false;
        
        while (! done) {
            // Search on the remainder of the text
            // Start by checking the next nested tag.
            bxExToXMatcher.reset(curTuState);   // Search what's left of the TU
            if (bxExToXMatcher.find()) {
                String prefix = bxExToXMatcher.group(1);     // Before the first bx
                String bxBxExEx = bxExToXMatcher.group(2);   // All four adjacent bx/bx/ex/ex
                String suffix = bxExToXMatcher.group(17);    // After last ex
                String outerCtype = bxExToXMatcher.group(5);  // Outer ctype
                String innerCtype = bxExToXMatcher.group(10); // Inner ctype

                // Write adjacent bxes to format file
                writeFormatLiteral(bxBxExEx,bxExXId);
                // and increment the id
                int bxBxExExId = bxExXId++;
                
                // Select a ctype that will make most sense to the translator
                // If both start with "x-", use the inner
                // If one starts with "x-", use the other one
                // If neither starts with "x-", prefer "link", else inner ctype
                String preferredCtype = "";
                if (outerCtype.equals("link") || innerCtype.equals("link")) {
                    preferredCtype = "link";
                }
                else if (outerCtype.startsWith("x-")) {
                    preferredCtype = innerCtype;
                }
                else { // outer *doesn't* start with "x-"
                    if (innerCtype.startsWith("x-")) {
                        preferredCtype = outerCtype;
                    }
                    else {  // Neither starts with "x-"; prefer inner
                        preferredCtype = innerCtype;
                    }
                }
                
                // Reconstruct the string with fewer bx/ex elements.
                curTuState = prefix + "<x id='" + bxBxExExId + "' ctype='" + preferredCtype
                        + "'/>"
                        + suffix;

                // Increment nextAvailRid
                nextAvailRid++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }
        
        return (curTuState);  // Return the collapsed TU
    }
    
    /**
     * Passed the full text of an ODF Translation Unit source (including
     * core start and end markers), check for bx/x/ex sequences that represent
     * a tab surrounded by text:span begin and end tags. Replace the sequence
     * with a single x tag representing a tab. Add a format file entry that maps 
     * the single x tag to the bx/x/ex tag sequence.
     * A typical office:annotation sequence looks something like:
     * <pre>
     * &lt;bx id="159" ctype="x-odf-span" rid="74"/&gt;
     *   &lt;x id="160" ctype="x-odf-tab"/&gt;
     * &lt;ex id="161" rid="74"/&gt;
     * </pre>
     * <p>This method will replace each such sequence with a single x element that
     * references the xid of the trans-unit that has the text of the annotation.
     * @param in The full text of the Translation Unit source 
     * @return The modified text of the Translation Unit.
     */
    private String collapseTabSpans (String in) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // See if the input string includes both the x-odf-span and x-odf-tab ctypes. 
        // If it doesn't, return the input string.
        if (in.indexOf("x-odf-span") == -1 || in.indexOf("x-odf-tab") == -1) {
            return in;
        }

        // Loop through the text looking for the span-tab-endspan sequence.
        // When one is found, create a new x-odf-tab tag that maps to the sequence
        
        // Regex for span-tab-endspan block
//        String tabPattern = "^(.*?)"
//            // Match the opening text:span tag
//            + "(<bx id=(['\"])\\d+\\3 +ctype=(['\"])x-odf-span\\4 +rid=(['\"])(\\d+)\\5 */>" // 1st rid is group 6
//            // Then match the tab tab
//            +  "<x id=(['\"])\\d+\\7 +ctype=(['\"])x-odf-tab\\8 */>)"
//            // ... and close the text:span
//            +  "<ex id=(['\"])\\d+\\9 +rid=(['\"])\\6\\10 */>)"
//            // Text that follows the span:
//            +  "(.*)$";  // What follows
//
//        Matcher tabMatcher = Pattern.compile(tabPattern,Pattern.CASE_INSENSITIVE).matcher("");

        StringBuilder newTu = new StringBuilder(); // The beginnings of a new TU
        String tuTail = in;          // We'll "eat" this as we loop through a matching TU
        String tabBlock = "";        // The current block of oa tags, etc.
        
        while (tuTail.length() > 0) {
            // Search on the remainder of the core text
            // Start by checking for leading tag.
            tabMatcher.reset(tuTail);   // Search what's left of the TU
            if (tabMatcher.find()) {
                if (tabMatcher.group(1).length() > 0) {
                    newTu.append(tabMatcher.group(1)); // Append characters before the annotation block
                }
                tabBlock = tabMatcher.group(2);    // The tags we will replace with a single x
                tuTail   = tabMatcher.group(11);   // Remainder of core text ... for next iteration.

                writeFormatLiteral(tabBlock,bxExXId);

                // Substitute an x tag for the office:annotation block (in the new TU)
                // Make it match the original except for the id attribute.
                newTu.append("<x id='" + bxExXId + "' ctype='x-odf-tab'/>");

                // Increment the x/bx/ex id for the next format file writer
                bxExXId++;                   // Increment our local copy
            }
            else {  // We're done!
                break;
            }
        }
        
        return (newTu.toString() + tuTail);  // Return the collapsed TU
    }
    
    /**
     * Passed the current ctype string (which includes indications of
     * zero or more of bold, italic, superscript, subscript and underline
     * styles), incorporate the new ctype style in the current ctype string,
     * in a canonical order, avoiding duplicates.
     * @param curCtype The current ctype string (which might be null or of
     *                 length 0)
     * @param newVal The new ctype value (which might be null or of length 0)
     * @return The new ctype value.
     */
    private String addCtypeStyle(String curCtype, String newVal) {
        // If there is nothing to add, return the original current ctype
        if ((newVal == null) || (newVal.trim().length() == 0)) {
            return curCtype;   // Whatever it is--null, zero-length, or other
        }
        
        // Variables to indicate whether ctype has a given style
        boolean hasBold = false, hasItalic = false, hasUnderline = false;
        boolean hasSuperscript = false, hasSubscript = false;
        int totStyles = 0;
        
        // Find out what the current ctype's styles are
        if (curCtype != null) {
            if (curCtype.indexOf("bold") > -1) {
                hasBold = true;
                totStyles++;
            }
            if (curCtype.indexOf("italic") > -1) {
                hasItalic = true;
                totStyles++;
            }
            if (curCtype.indexOf("underline") > -1) {
                hasUnderline = true;
                totStyles++;
            }
            if (curCtype.indexOf("superscript") > -1) {
                hasSuperscript = true;
                totStyles++;
            }
            if (curCtype.indexOf("subscript") > -1) {
                hasSubscript = true;
                totStyles++;
            }
        }
        
        // Now add/merge the new value
        if (newVal.equalsIgnoreCase("bold") && !hasBold) {
            hasBold = true;
            totStyles++;
        }
        if (newVal.equalsIgnoreCase("italic") && !hasItalic) {
            hasItalic = true;
            totStyles++;
        }
        if (newVal.equalsIgnoreCase("underline") && !hasUnderline) {
            hasUnderline = true;
            totStyles++;
        }
        if (newVal.equalsIgnoreCase("superscript") && !hasSuperscript) {
            hasSuperscript = true;
            totStyles++;
        }
        if (newVal.equalsIgnoreCase("subscript") && !hasSubscript) {
            hasSubscript = true;
            totStyles++;
        }

        // Now construct the return value
        String retVal = null;
        if (totStyles == 1) {   // Easy; the new val is the ctype (with x-odf- prefixed in
                                // some cases.
            if (newVal.equalsIgnoreCase("bold") || newVal.equalsIgnoreCase("italic")
                || newVal.equalsIgnoreCase("underline")) {
                retVal = newVal.toLowerCase();
            }
            else {   // superscript and subscript have to be prefixed with x- (and we 
                     // decided to add odf-)
                retVal = "x-odf-" + newVal.toLowerCase();
            }
        }
        else { // Multiple styles in this ctype
            retVal = "x-odf";
            if (hasBold) {
                retVal += "-bold";
            }
            if (hasItalic) {
                retVal += "-italic";
            }
            if (hasUnderline) {
                retVal += "-underline";
            }
            if (hasSuperscript) {
                retVal += "-superscript";
            }
            if (hasSubscript) {
                retVal += "-subscript";
            }
        }
        return retVal;
    }

    /** Passed a string that purportedly contains WingDings, remap the wingdings
     * to equivalent Unicode Characters.
     * @param inString The input string that contains WingDings
     * @return The string with Wingdings converted to Unicode
     */
    private String mapWingdings2Unicode(String inString) {
        if (inString == null) {    // No input string; return zero-length string
            return "";
        }
        if (inString.trim().length() == 0) {  // Just space; return the input string
            return inString;
        }
        
        // Map the characters in the string to unicode
        StringBuilder outString = new StringBuilder();
        for (int i = 0; i < inString.length(); i++) {
            char curCh = inString.charAt(i);
            if ((curCh >= 0) && (curCh < this.wingdings2char.length)) {
                outString.append(this.wingdings2char[curCh]);
            }
            else {  // Out of range; can't be a Wingdings character
                outString.append(curCh);
            }
        }
        return outString.toString();
    }
    
    /**
     * Mark all TU ancestor text:p's as having a descendant TU. This method deals 
     * with the situation where a text:p element is a descendant (child, 
     * grandchild, etc.) of one or more text:p elements. In case one or more 
     * ancestor text:p nodes have no translatable text, they still need to be 
     * included in the skeleton, so that (during export) they will be "expanded," 
     * and the descendant text:p element included in the constructed document.
     */
    private void markTuAncestry() {
        // Mark all TU's on the stack
        for (int i = (tuList.size() - 1) ; i >= 0 ; i--) {
            tuList.get(i).setHasTuChildren(true);
        }
    }

    /**
     * Write an entry in the format file. (This records what the bx, ex and
     * x tags really were in the original source document.)
     * @param element The element (tag) that occurred in the original document
     * @param atts The attribute list that appeared in the original document
     * @param id The id number that this tag will have in the format document
     * @param type Is this an empty tag?
     */
    private void writeFormatEntry(String element, Attributes atts, int id,
            TagType type) {
        // Write format file ...
        try {
            outFormat.write("  <tag id='" + id + "'><![CDATA[<");
        
            // Insert / if a start tag.
            if (type == TagType.END) { outFormat.write("/");} 
            
            outFormat.write(element);
                
            // Now write the attributes (if they exist)
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String aName = atts.getQName(i); // Attr name
                    
                    // Ticket 851 (2/14/07): Need to preserve entities in attribute values
                    String aValue = atts.getValue(i); // Resolves <>"'& (trying to be helpful)
                    if (aValue.contains("&")
                        || aValue.contains("<")
                        || aValue.contains(">")
                        || aValue.contains("\"")
                        || aValue.contains("'")) {
                        aValue =TuStrings.escapeTuString(aValue);
                    }
                    
                    outFormat.write(" " + aName + "=\"" + aValue + "\"");
                }
            }
            
            if (type == TagType.EMPTY)  {                 // This is an empty XML element
                outFormat.write("/");   // Put / before last > to close it
            }

            // Then close the tag
            outFormat.write(">]]></tag>\r\n");
            outFormat.flush();
        }
        catch (IOException io) {
            System.err.println("Unable to write to format output stream.");
            System.err.println(io.getMessage());
        }
    }

    /**
     * Write a literal entry entry in the format file. It must include everything
     * that goes between an opening CDATA and corresponding closing tag.
     * @param litString The literal string to write
     * @param id The id number that this tag will have in the format document
     */
    private void writeFormatLiteral(String litString, int id) {
        // Write format file ...
        try {
            outFormat.write("  <tag id='" + id + "'><![CDATA[");
        
            outFormat.write(litString);
                
            // Then close the tag
            outFormat.write("]]></tag>\r\n");  // End of CDATA, etc.
            outFormat.flush();
        }
        catch (IOException io) {
            System.err.println("Unable to write to format output stream.");
            System.err.println(io.getMessage());
        }
    }

    /**
     * Write an entry in the temporary skeleton file. 
     * @param tag The tag that occurred in the original document
     * @param seqNum The tag's sequence number
     * @param isEndTag Is this an "end" tag?
     * @param depth The depth (in the TU tree) of this entry. (In the case of
     *        an opening text:p, text:h, etc., it is the depth *after*
     *        adding a TU entry to the TU stack. In the case of a corresponding
     *        end tag, it is the depth *before* popping the TU stack.)
     */
    private void writeTSkeletonEntry(String tag, int seqNum, boolean isEndTag,
            int depth) {
        try {
            outSkeleton.write("<" + (isEndTag?"/":"") + tag + " seq='" + seqNum + "'");
            outSkeleton.write(" depth='" + depth + "'");
            outSkeleton.write(">\r\n");
            outSkeleton.flush();
        }
        catch(IOException e) {
            System.err.println("Error writing " + tag + " " + seqNum
                + " to tskeleton.");
            System.err.println(e.getMessage());
        }
    }
    
    /**
     * Close the current translation unit (if one is in progress). Actually, what this
     * amounts to is checking the tuList (array/stack of TuListEntry's) to see if 
     * there is something to output to the XLIFF. If there is, then output the 
     * entry. When finished, remove the last/top entry from the tuList.
     */
    private void writeTu() {
        if (! inNote) {
            // Pop the stack, essentially.
            TuListEntry candidateTu = tuList.remove(tuList.size() - 1);

            String theText = candidateTu.getTuText();
            // If the segment includes an office:annotation "block," collapse it
            theText = this.collapseAnnotations(theText);
            // Ditto for a text:note block
            theText = this.collapseFootnotes(theText);
            // ... and for span/tag/endspan blocks
            theText = this.collapseTabSpans(theText);
            // ... and for confusing nested <bx/><bx/>text<ex/><ex/> tags
            theText = this.collapseNestedBxEx(theText);
            
            // Get the core segments:
            SegmentInfo[] coreTus = TuPreener.getCoreSegments(theText, 
                this.boundaryType, this.sourceLang);
            
            // The ID of the first segment in the candidate TU is important because
            // it might (if, for example, this set of segments represents a
            // footnote) be the target of an xid from some bx/ex/x tag elsewhere
            // in the document.
            UUID idOfCurSegment = candidateTu.getTuID();  // The first seg's TU ID
            
            // If document is being segmented at sentence boundaries, we need
            // to identify the paragraph that is the parent of all the 
            // sentences ... with a UUID (of course).
            // For now, the paragraph ID will be the UUID of the first segment.
            UUID paraID = idOfCurSegment;
            
            // Process each segment. If a segment contains translatable text,
            // create a TU for it (in the XLIFF) and write a "tu" line to the temporary
            // skeleton. If the segment doesn't contain translatable text, prepend or
            // append it to a neighboring segment (where it will appear outside the
            // core, of course.)

            // Untranslatable "segment" that hasn't been merged with neighboring
            // translatable segment:
            String accumulatedUntranslatable = "";
            boolean encounteredTranslatableSegment = false;
            
            // We will "chain" together "sibling" (i.e. in the same candidate TU
            // buffer) TU sequences of depth greater than 1. The first of the
            // sequence will be referenced by its parent TU. We will create a
            // linked attribute (lt:next-tu-id) in the oldest sibling that
            // references its next younger sibling. We will need to temporarily
            // store the last part of the older sibling TU to allow us to
            // add an lt:next-tu-id attribute when we confirm that a younger
            // sibling exists. This is that buffer
            
            StringBuilder siblingTuSuffix = new StringBuilder();

            // WWhipple 1/16/7: For TU sequences of depth == 1, we will indicate
            // that a TU can be merged with its following sibling by using the
            // attribute lt:mergeable (rather than lt:next-tu-id)
            boolean useMergeableAttr = false;
            
            for (int i = 0; i < coreTus.length; i++) {
                if (coreTus[i].translatable()) { 
                    if (encounteredTranslatableSegment /* previously */) {
                        // We left the source and trans-unit elements open to
                        // receive untranslatable appendages. ... So we need to
                        // close the earlier source and trans-unit.
                        try {
                            if (siblingTuSuffix.length() > 0) {
                                if (useMergeableAttr) {
                                    outXliff.write("lt:mergeable='true'");
                                }
                                else { // use next-tu-id attr.
                                    // Write a link from previous segment to this one
                                    outXliff.write("lt:next-tu-id='" + idOfCurSegment + "'");
                                }
                                // Then write out the rest of the previous
                                // segment
                                outXliff.write(siblingTuSuffix.toString());
                                siblingTuSuffix.setLength(0);
                            }
                            
                            // Close the source element
                            outXliff.write("</source>\r\n");

                            // Close the trans-unit element
                            outXliff.write(indent('-') + "</trans-unit>\r\n");
                        }
                        catch(IOException e) {
                            System.err.println("Error writing translation-unit characters to the XLIFF file.");
                            System.err.println(e.getMessage());
                        }
                        
                    }
                    
                    encounteredTranslatableSegment = true;  // Meaningful if this is the first one
                    String coreSeg = coreTus[i].getSegmentStr();  // Includes x-coretext mrk tags

                    try {
                        // Open the trans-unit element (No offset attribute in the following (yet?))
                        outXliff.write(indent('0')
                            + "<trans-unit id='" + idOfCurSegment.toString() + "' "
                            + "lt:paraID='" + paraID + "' ");
                            
                        if (tuList.size() > 0) {   // This is an "imbedded" TU
                            useMergeableAttr = false;
                        }
                        else { // This TU has no ancestral TUs
                            useMergeableAttr = true;
                        }
                        
                        siblingTuSuffix.append(">\r\n");

                        // Open the source tag
                        siblingTuSuffix.append(indent('+') + "<source xml:lang='" + sourceLang.toString() + "'>");

                        // If there is any residual untranslatable text, prepend it to this <source>
                        // (where it will fall before the opening x-coretext mrk tag.)
                        if (accumulatedUntranslatable.length() > 0) {
                            siblingTuSuffix.append(accumulatedUntranslatable);
                            accumulatedUntranslatable = "";
                        }

                        // Write the actual text of the TU:
                        // It should be already escaped (shouldn't it?)
                        siblingTuSuffix.append(coreSeg);
                        
                    }
                    catch(IOException e) {
                        System.err.println("Error writing translation-unit characters to the XLIFF file.");
                        System.err.println(e.getMessage());
                    }

                    // Write to the intermediate skeleton file
                    if (tuList.size() == 0) { // It was 1 before we removed this TU segment group
                        try {
                            // Note: The no=x of=y attributes are a ceiling values.
                            // Because some segments will be merged with neighbors, the
                            // no= sequence will probably skip some numbers, and 
                            // the of= value generally be high.
                            
                            outSkeleton.write("<tu id='" + idOfCurSegment.toString() + "' length='" 
                                + coreSeg.length() + "' depth='" + (tuList.size() + 1) + "' "
                                + "no='" + (i+1) + "' of='" + coreTus.length + "'>\r\n");
                            outSkeleton.flush();
                        }
                        catch(IOException e) {
                            System.err.println("Error while writing skeleton file.");
                            System.err.println(e.getMessage());
                        }
                    }
                    idOfCurSegment = UUID.randomUUID();  // UUID for next segment, if necessary.

                }
                else {  // Not translatable
                    // This particular segment (of the set of 2+ segments in the
                    // candidate TU buffer--1+ segment of which has translatable
                    // text) doesn't have translatable text. We still need to
                    // capture that non-translatable text when we construct a 
                    // target translation from the skeleton file and targets. 
                    // ... so store the text in the format file and reference it
                    // in the tskeleton
                    try {
                        // Save the untranslatable text in the format file, 
                        // with a reference to it in an x tag (which will be
                        // placed outside the core of a neighboring translatable
                        // segment).
                        accumulatedUntranslatable += "<x id='" + bxExXId + "'/>";
                                
                        outFormat.write("  <tag id='" + bxExXId + "'>");
                        outFormat.write(coreTus[i].getSegmentStr());
                        outFormat.write("</tag>\r\n");
                        outFormat.flush();

                        bxExXId++;
                    }
                    catch(IOException e) {
                        System.err.println("Error while writing non-translatable "
                                + "segment to format or skeleton file.");
                        System.err.println(e.getMessage());
                    }

                }
            } // for
            
            // Finished looping, we now need to close remaining source and
            // trans-unit elements that are still open.
            if (coreTus.length > 0) {
                try {
                    if (siblingTuSuffix.length() > 0) {
                        // Then write out the rest of the previous translatable
                        // segment
                        outXliff.write(siblingTuSuffix.toString());
                        siblingTuSuffix.setLength(0);
                    }

                    // If there is any accumulated untranslatable text that hasn't been
                    // included in an adjoining segment, add it now:
                    if (accumulatedUntranslatable.length() > 0) {
                        outXliff.write(accumulatedUntranslatable);
                    }

                    // Close the source element
                    outXliff.write("</source>\r\n");

                    // ... and the trans-unit element
                    outXliff.write(indent('-') + "</trans-unit>\r\n");
                    outXliff.flush();
                }
                catch(IOException e) {
                    System.err.println("Error writing translation-unit characters to the XLIFF file.");
                    System.err.println(e.getMessage());
                }
            }
            
            // We need to include the tags (and text, too)--but even if only tags
            // we need to include them. So claim ancestry.
            if ((tuList.size() > 0) && (candidateTu.getTuText().trim().length() > 0)) {
                markTuAncestry();            // ######  May need to revisit !!! ####
            }
               
        }  // If not in note
        
        // If we're in a note or annotation, save this TU for the end of
        // the XLIFF. 
        else {  // We're in a note; save the TU for the end of the XLIFF
            
            TuListEntry candidateTu = tuList.remove(tuList.size() - 1);

            String theText = candidateTu.getTuText();
            // Try simplifying things a bit
            theText = this.collapseTabSpans(theText);
            // ... and for confusing nested <bx/><bx/>text<ex/><ex/> tags
            theText = this.collapseNestedBxEx(theText);
            
            // Get the core segments:
            SegmentInfo[] coreTus = TuPreener.getCoreSegments(theText, 
                this.boundaryType, this.sourceLang);

            // The ID of the first segment in the candidate TU is important because
            // it might (if, for example, this set of segments represents a
            // footnote) be the target of an xid from some bx/ex/x tag elsewhere
            // in the document.
            UUID idOfCurSegment = candidateTu.getTuID();  // The first seg's TU ID

            // If document is being segmented at sentence boundaries, we need
            // to identify the paragraph that is the parent of all the 
            // sentences ... with a UUID (of course).
            // For now, the paragraph ID will be the UUID of the first segment.
            UUID paraID = idOfCurSegment;

            // Process each segment of the note. If a segment contains translatable text,
            // create a TU for it (in the XLIFF) and write a "tu" line to the temporary
            // skeleton. If the segment doesn't contain translatable text, prepend or
            // append it to a neighboring segment (where it will appear outside the
            // core, of course.)

            // Untranslatable "segment" that hasn't been merged with neighboring
            // translatable segment:
            String accumulatedUntranslatable = "";
            boolean encounteredTranslatableSegment = false;
            
            // We will "chain" together "sibling" (i.e. in the same candidate TU
            // buffer) TU sequences of depth greater than 1. The first of the
            // sequence will be referenced by its parent TU. We will create a
            // linked attribute (lt:next-tu-id) in the oldest sibling that
            // references its next younger sibling. We will need to temporarily
            // store the last part of the older sibling TU to allow us to
            // add an lt:next-tu-id attribute when we confirm that a younger
            // sibling exists. This is that buffer
            StringBuilder siblingTuSuffix = new StringBuilder();

            for (int i = 0; i < coreTus.length; i++) {
                if (coreTus[i].translatable()) { 
                    if (encounteredTranslatableSegment /* previously */) {
                        // We left the source and trans-unit elements open to
                        // receive untranslatable appendages. ... So we need to
                        // close the earlier source and trans-unit.
                        if (siblingTuSuffix.length() > 0) {
                            // Write a link from previous segment to this one
                            xliffAppendix.append("lt:next-tu-id='" + idOfCurSegment + "'");

                            // Then write out the rest of the previous
                            // segment
                            xliffAppendix.append(siblingTuSuffix.toString());
                            siblingTuSuffix.setLength(0);
                        }

                        // Close the source element
                        xliffAppendix.append("</source>\r\n");

                        // Close the trans-unit element
                        xliffAppendix.append(indent('-') + "</trans-unit>\r\n");
                    }
                    
                    encounteredTranslatableSegment = true;  // Meaningful if this is the first one
                    String coreSeg = coreTus[i].getSegmentStr();
            
                    // Open the trans-unit element (No offset attribute in the following (yet?))
                    xliffAppendix.append(indent('0')
                        + "<trans-unit id='" + idOfCurSegment + "' "
                            + "lt:paraID='" + paraID + "' ");
                    
                    siblingTuSuffix.append(">\r\n");

                    // Open the source tag
                    siblingTuSuffix.append(indent('+') + "<source xml:lang='" + sourceLang.toString() + "'>");

                    // If there is any residual untranslatable text, prepend it to this <source>
                    // (where it will fall before the opening x-coretext mrk tag.)
                    if (accumulatedUntranslatable.length() > 0) {
                        siblingTuSuffix.append(accumulatedUntranslatable);
                        accumulatedUntranslatable = "";
                    }
                    
                    // Write the actual text of the TU:
                    siblingTuSuffix.append(coreSeg);

                    // Don't close the source or trans-unit elements yet--in case some trailing
                    // untranslatable "segments" need to be appended after this source
                    // Close the source element

                    idOfCurSegment = UUID.randomUUID();  // UUID for next segment, if necessary.
                    // (Note: Notes will *always* have a non-empty parent TU, so we don't
                    // need to set the parent TU's hasTuChildrenFlag)
                }
                else { // Not translatable
                    // This particular segment (of the set of 2+ segments in the
                    // candidate TU buffer--1+ segment of which has translatable
                    // text) doesn't have translatable text. We still need to
                    // capture that non-translatable text when we construct a 
                    // target translation from the targets. 
                    // ... so store the text in the format file and reference it
                    // in the tskeleton
                    try {
                        // Save the untranslatable text in the format file, 
                        // with a reference to it in an x tag (which will be
                        // placed outside the core of a neighboring translatable
                        // segment).
                        accumulatedUntranslatable += "<x id='" + bxExXId + "'/>";
                                
                        outFormat.write("  <tag id='" + bxExXId + "'>");
                        outFormat.write(coreTus[i].getSegmentStr());
                        outFormat.write("</tag>\r\n");
                        outFormat.flush();

                        bxExXId++;
                    }
                    catch(IOException e) {
                        System.err.println("Error while writing non-translatable "
                                + "segment to format or skeleton file.");
                        System.err.println(e.getMessage());
                    }
                }
            }  // For 

            if (coreTus.length > 0) {
                // Finished looping, we now need to close remaining source and
                // trans-unit elements that are still open.
                if (siblingTuSuffix.length() > 0) {
                    // Then write out the rest of the previous translatable
                    // segment
                    xliffAppendix.append(siblingTuSuffix.toString());
                    siblingTuSuffix.setLength(0);
                }

                // If there is any accumulated untranslatable text that hasn't been
                // included in an adjoining segment, add it now:
                if (accumulatedUntranslatable.length() > 0) {
                    xliffAppendix.append(accumulatedUntranslatable);
                }

                // Close the source element
                xliffAppendix.append("</source>\r\n");

                // ... and the trans-unit element
                xliffAppendix.append(indent('-') + "</trans-unit>\r\n");
            }
        } 
    }  // End writeTu
} // End ODFHandler
