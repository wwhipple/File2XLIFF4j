/*
 * TuStrings.java
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

import java.util.*;
import java.io.*;
import java.util.regex.*;

/**
 * Class to represent a TU target in the TU map.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class TuMapEntry {
    private String tuText;       // The text of the TU target (between start and
                                 // end target tags).
    private String nextSegTuId;    // UUID of the next segment
//    private int position;        // Zero-based position of this TU entry in the
//                                 //   original XLIFF file.
    
    /**
     * Constructor that takes the text of the TU and its next segment's TU ID
     * @param tuText The Text of the TU (between begin and end target tags)
     * @param nextSegTuId The UUID of the next segment (or null if none)
     */
    public TuMapEntry(String tuText, String nextSegTuId) {
        this.tuText = tuText;
        this.nextSegTuId = nextSegTuId;
//        this.position = position;
    }
    
    /**
     * Constructor that takes the text of the TU and its next segment's TU ID
     * @param tuText The Text of the TU (between begin and end target tags)
     */
    @Deprecated
    public TuMapEntry(String tuText) {
        this.tuText = tuText;
        this.nextSegTuId = null;
//        this.position = -1;
    }

    /**
     * Return the text of the TU
     * @return the text.
     */
    public String getText() {
        return this.tuText;
    }
    
    /**
     * Return the UUID of the next segment. 
     * @return the UUID of the next segment (may be null).
     */
    public String getNextSegTuId() {
        return this.nextSegTuId;
    }
    
//    /**
//     * Return the position of this segment in the XLIFF file (counting from 0)
//     * @return the 0-based position in the file.
//     */
//    public int getPosition() {
//        return this.position;
//    }

}


/**
 * This class encapsulates the target strings that an XLIFF file
 * contains for a specified language. (Note: It might be later
 * enhanced to be able to hold the source strings rather than
 * a target string.)
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class TuStrings {
    
    /** Place to store the strings that we will return. We return them
     * based on their ID. */
    private HashMap<String,TuMapEntry> tuMap = new HashMap<String,TuMapEntry>();

    /** Where we will read the XLIFF from */
    private BufferedReader xliffIn;
    
    /** Characters "left over" from a previous TU that might have ended
     * "mid-line". */
    private String prevRemainder = "";
    
    /** Creates a new instance of TuStrings */
    public TuStrings() {
        
    }
    
    /** 
     * Passed a trans-unit identifier, return the target string associated
     * with it.
     * @param id The trans-unit identifier
     * @return The string associated with the identifier
     */
    public String getTu(String id) {
        return this.getTu(id, "", false);
    }

    /** 
     * Passed a trans-unit identifier, return the target string associated
     * with it.
     * @param id The trans-unit identifier
     * @param datatype The datatype of the native file from which the XLIFF was
     *        created
     * @return The string associated with the identifier
     */
    public String getTu(String id, String datatype) {
        return this.getTu(id, datatype, false);
    }

    /** 
     * Passed a trans-unit identifier, return the target string associated
     * with it.
     * @param id The trans-unit identifier
     * @param datatype The datatype of the native file from which the XLIFF was
     *        created
     * @param followNextAttr Boolean value indicating whether to follow the
     *        lt:next-tu-id attribute (referring to a "chain" of adjacent
     *        sentence segments). If set to true, the return string will
     *        include multiple TUs concatenated together.
     * @return The string associated with the identifier
     */
    public String getTu(String id, String datatype, boolean followNextAttr) {
        return this.getTu(id, datatype, followNextAttr, true);
    }

    /** 
     * Passed a trans-unit identifier, return the target string associated
     * with it.
     * @param id The trans-unit identifier
     * @param datatype The datatype of the native file from which the XLIFF was
     *        created
     * @param followNextAttr Boolean value indicating whether to follow the
     *        lt:next-tu-id attribute (referring to a "chain" of adjacent
     *        sentence segments). If set to true, the return string will
     *        include multiple TUs concatenated together.
     * @param validate If true: in the process of retrieving the target, validate
     *        (and repair if appropriate) the core text of the target. If false:
     *        don't validate (and repair).
     * @return The string associated with the identifier
     */
    public String getTu(String id, String datatype, boolean followNextAttr,
            boolean validate) {
        if (datatype == null) {
            datatype = "";
        }
        
        if (!followNextAttr) {
            // Get the text of the TU. If the TU doesn't exist, use a zero-length
            // string.
            TuMapEntry me = tuMap.get(id);
            String tuText = "";
            if (me != null) {
                tuText = me.getText();
            }
            
            if (validate) {
                return TuPreener.getPrefixText(tuText) 
                     + TuPreener.CORE_START_MRK
                     + (datatype.equalsIgnoreCase("html") ? 
                         TuPreener.checkAndRepairTuTags(TuPreener.getCoreText(tuText)) : 
                         TuPreener.validateAndRepairTu(TuPreener.getCoreText(tuText))) 
                     + TuPreener.CORE_END_MRK
                     + TuPreener.getSuffixText(tuText);
            }
            else {
                return tuText;
            }
        }
        else {
            String tuSequence = "";
            String nextId = id;
//            UUID nextId = UUID.fromString(id);
            while ((nextId != null) && (nextId.trim().length() > 0)) {
                TuMapEntry me = tuMap.get(nextId);
                String tuText = "";
                if (me != null) {
                    tuText = me.getText();
                }
                tuSequence += tuText; 
                if (me != null) {
                    nextId = me.getNextSegTuId();
                }
                else {
                    nextId = null;
                }
            }
            
            // Now check and repair ...
            if (datatype.equalsIgnoreCase("html")) {
                tuSequence = TuPreener.checkAndRepairTuTags(tuSequence);
            }
            else {  // XML--more strict
                tuSequence = TuPreener.validateAndRepairTu(tuSequence);
            }
            return tuSequence;
        }
    }
    
//    /** Passed the name of an XLIFF file and a target Locale, read the strings for
//     * the specified locale from the file and place them in a HashMap for
//     * later access. 
//     * @param xliffFile Fully-qualified name of the xliff file
//     * @param lang The language whose strings we're interested in.
//     * @param phaseName The phase-name of the targets we are requesting. If your
//     *        XLIFF doesn't use targets, specify null, and loadStrings will
//     *        use the <i>first</i> target it finds for each target language.
//     *        <p>If phaseName if formatted like an integer greater than 1 and
//     *        a matching phase is not found, loadStrings will search
//     *        recursively for a matching target, decrementing the phase
//     *        "number" until phase "1" has been reached.
//     * @throws file2xliff4j.ConversionException
//     *         If unable to load the XLIFF file
//     */
//    public void loadStrings(String xliffFile, Locale lang, String phaseName) 
//            throws ConversionException {
//        this.loadStrings(xliffFile, lang, phaseName, 0);
//    }

    /** Passed the name of an XLIFF file and a target Locale, read the strings for
     * the specified locale from the file and place them in a HashMap for
     * later access. 
     * @param xliffFile Fully-qualified name of the xliff file
     * @param lang The language whose strings we're interested in.
     * @param phaseName The phase-name of the targets we are requesting. If your
     *        XLIFF doesn't use targets, specify null, and loadStrings will
     *        use the <i>first</i> target it finds for each target language.
     *        <p>If phaseName if formatted like an integer greater than 1 and
     *        a matching phase is not found, loadStrings will search
     *        recursively for a matching target, decrementing the phase
     *        "number" until phase "1" has been reached.
     * @param maxPhase If phaseName is specified as "0" and maxPhase is
     *        a non-negative integer, search for the highest "numbered" phase,
     *        starting at maxPhase, and searching down to phase "1".
     * @param ampEntities If true, convert all bare ampersands (in error messages
     *        for "not yet translated" segments) to the amp entity.
     * @throws file2xliff4j.ConversionException
     *         If unable to load the XLIFF file
     */
    public void loadStrings(String xliffFile, Locale lang, String phaseName, 
            int maxPhase, boolean ampEntities) 
            throws ConversionException {
        
        try {
            xliffIn = new BufferedReader(new InputStreamReader(new FileInputStream(
                xliffFile), "UTF8"));
        }
        catch (IOException e) {
            System.err.println("Error reading XLIFF file: " + e.getMessage());
            throw new ConversionException("Error reading XLIFF file: " 
                    + e.getMessage());
        }

        String wholeTu = "";
        String langStr = lang.toString();
        int counter = 0;
        while ((wholeTu = readNextTu()).length() > 0) {
            storeTarget(wholeTu, langStr, phaseName, maxPhase, counter, ampEntities);
            counter++;
        }

        // We're done now.
        try {
            xliffIn.close();     // Close before leaving.
        }
        catch (IOException e) {
            System.err.println("Error closing XLIFF file: " + e.getMessage());
            
            // Ignore it (I guess).
        }
    }
    
    /**
     * Passed the text of a trans-unit element (i.e., the characters between the
     * opening and closing trans-unit tags), a language and a phase name, look for
     * a target string that matches both language and phase. Return that text.
     * <p><b>Special functionality</b>: If the phaseName is formatted like a
     * positive integer with value less than or equal to maxPhase and greater
     * than 1, and the target isn't found, decrement the numeric phaseName and 
     * look again (stopping when phase-name 1 has been reached).
     * <p>If phaseName is "0" and maxPhase is a positive integer, search for the
     * highest numbered phase-name (searching from maxPhase down to 1).
     * @param tuText The text and all other subelements of a trans-unit element
     *        to search.
     * @param langStr The language string to match against xml:lang attributes
     *        when searching for the target
     * @param phaseName The name of the phase to match atainst phase-name
     *        attributes in target elements in the tuText buffer. If phaseName
     *        represents a number in the range 2 <= phaseName <= maxPhase
     *        and a target is not found, try to find the next-highest numbered
     *        matching target.
     *        If phaseName is "0" and maxPhase is positive, search for the
     *        target with highest-numbered phase (searching from maxPhase down
     *        to 1).
     * @param maxPhase The highest phase number to search (if phaseName is a
     *        string that "looks like" an integer.) 
     * @return A string containing all the text and subelements of a matching
     *        target element, or a zero-length string if no match is found.
     */
    private String getTargetTextByPhase(String tuText, String langStr, String phaseName,
            int maxPhase) {
        String target = "";                // No target found so far
        boolean foundTarget = false;       // Not found ... yet.

        // Matcher for the target language as an attribute of the target element
        Matcher tm = Pattern.compile("<target([^>]+xml:lang=['\"]" + langStr + "['\"][^>]*)>(.*?)</target>",
            Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher("");

        // Matcher for the target languge as an attribute of the target element's
        // parent alt-trans element:
        Matcher atm = Pattern.compile("<alt-trans[^>]+?xml:lang=['\"]" + langStr + "['\"][^>]*>"
            + ".*?<target([^>]*)>(.*?)</target>", Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher("");
        
        // We need to be able to look for the requested phase-name within each candidate
        // target
        Matcher phM = Pattern.compile("\\bphase-name=['\"]" + phaseName + "['\"]",Pattern.DOTALL).matcher("");

        // First check the target elements (
        tm.reset(tuText);    // Start afresh with the Text of the trans-unit elt.
        while (tm.find()) {  // Look for matching xml:lang in each target element
            // We found one a matching language.
            String targetAttStr = tm.group(1);    // The guts of the target tag
            String candidateTarget = tm.group(2); // Text of target element

            // Does the phase-name attribute match?
            phM.reset(targetAttStr);
//            if (phM.find(0)) {
            if (phM.find()) {
                // Yes!!
                target = candidateTarget;
                foundTarget = true;
                break;    // Look no further
            }
        }

        // If we didn't find a target with both the sought-after xml:lang and
        // phase name (i.e., if foundTarget is still false), check out the
        // parent alt-trans begin tag for a matching xml:lang, then look
        // in its child target element's attributes for a matching phase-name
        if (! foundTarget) {
            atm.reset(tuText);
            while (atm.find()) {  // Look for matching xml:lang in alt-trans tag
                // We found a matching language
                String targetAttStr = atm.group(1);  // The guts of the target tag
                String candidateTarget = atm.group(2); // Text of the target element

                // Does the phase-name match (in the target tag guts)
                phM.reset(targetAttStr);
                if (phM.find(0)) {
                    // Yes!!
                    target = candidateTarget;
                    foundTarget = true;
                    break;    // Look no further
                }                    
            }
        }
        
        // If we haven't found the target and the phase-name is an integer between
        // 2 and MAX_SEARCH_PHASES inclusive, "decrement" the name and look 
        // some more.
        if (!foundTarget && phaseName.matches("\\d+")) { // If numeric
            int phaseNum = Integer.parseInt(phaseName);
            if ((phaseNum <= maxPhase) && (phaseNum > 1)) {
                phaseNum--;
                target = this.getTargetTextByPhase(tuText, langStr, 
                        Integer.toString(phaseNum), maxPhase);
            }
            else if ((phaseNum == 0) && (maxPhase > 0)) {
                phaseNum = maxPhase;
                target = this.getTargetTextByPhase(tuText, langStr, 
                        Integer.toString(phaseNum), maxPhase);
            }
        }
        
        return target;
    }
    
    /**
     * Passed the full text of a trans-unit element, return a plainText version
     * of the source element's text node.
     * @param tuElement String containing the full text of the TU.
     * @return The source string from the specified translation unit
     */
    private String parseTuSourcePlaintext(String tuElement) {
        if (tuElement == null || tuElement.trim().length() == 0) {
            return "";
        }
        
        String plainSource = "";
        // "Lazy" pattern matching will find the first <source>
        String sPattern = ".*?<source[^>]*>(.*?)</source>";
        Matcher sourceMatcher = Pattern.compile(".*?<source[^>]*>(.*?)</source>", Pattern.DOTALL).matcher(tuElement);
        if (sourceMatcher.find()) {
            plainSource = sourceMatcher.group(1);  // Here is the source string
        }

        // Replace odf text:tab's with a single space
        Pattern tabp = Pattern.compile("<x id=['\"][^'\"]+['\"] ctype=['\"](?:x-odf-tab|x-odf-s|lb)['\"]/>",Pattern.DOTALL);
        plainSource = tabp.matcher(plainSource).replaceAll(" ");

        // Delete all other tags:
        Pattern tagPattern = Pattern.compile("</?[^>]+>",Pattern.DOTALL);
        plainSource = tagPattern.matcher(plainSource).replaceAll("");
        
        // Change multiple internal whitespace characters to a single \u0020 character
        Pattern whiteSp = Pattern.compile("[\\s\\u00a0\\u000a\\u000d\\u0085\\u2028\\u2029]+",Pattern.DOTALL);
        plainSource = whiteSp.matcher(plainSource).replaceAll(" ");
        
        return plainSource; 
    }

    /** Read the next <trans-unit> element from the input XLIFF file 
     * @return The next translation unit (complete with opening and
     *          closing <trans-unit> tags
     */
    private String readNextTu() {
        
        StringBuilder thisTu = new StringBuilder();
        
        // Deal with the case where a <trans-unit> begins on the same line
        // as the previous <trans-unit> ended.
        if (prevRemainder.length() > 0) {
            // Strip off any leading whitespace. This will also handle the
            // case where the line is just trailing whitespace and newlines
            // from the previous TU
            Matcher m = 
                Pattern.compile("^[\\s\\u00a0\\u000a\\u000d\\u0085\\u2028\\u2029]+(.*)").matcher(prevRemainder);
            
            // If there is leading whitespace, remove it.
            if (m.find()) {
                prevRemainder = m.group(1);  // What comes after leading whitespace.
            }
        }
        
        // If there is meaningful (?) content from the previous call to readNextTu,
        // start with it.
        if (prevRemainder.length() > 0) {
            thisTu.append(prevRemainder);
        }
        
        // Initialize some variables for the next while loop.
        boolean eof = false;             // Check for end of file with this one
        String lineBuffer = "";          // Read into this.

        // Look for where the next <trans-unit> starts
        try {
            // Look for opening <trans-unit> tag
            while ((thisTu.indexOf("<trans-unit") == -1) && !eof) {
                lineBuffer = xliffIn.readLine();
                if (lineBuffer != null) {
//                    thisTu.append(lineBuffer + "\r\n");
                    thisTu.append(lineBuffer + "\n");
                }
                else {
                    eof = true;
                }
            }
        }
        catch (IOException e) {
            System.err.println("I/O Exception while reading next translation unit.");
            System.err.println(e.getMessage());
            return "";                     // Nothing to return.
        }
        
        // If there are any more <trans-unit>s, the opening tag of the next one
        // is in the "thisTu" buffer.
        int bpos = -1;        // Points to <trans-unit ("beginning position")
        // If can't find <trans-unit ...
        if ((bpos = thisTu.indexOf("<trans-unit")) == -1) {
            return "";        // We're done--return nothing.
        }
        
        if (bpos > 0) {
            thisTu.delete(0, bpos);     // Remove chars before <trans-unit
        }
        
        // Now look for the end trans-unit tag that corresponds to the opening
        // trans-unit tag.
        try {
            // Look for closing </trans-unit> tag
            while ((thisTu.indexOf("</trans-unit>") == -1) && !eof) {
                lineBuffer = xliffIn.readLine();
                if (lineBuffer != null) {
//                    thisTu.append(lineBuffer + "\r\n");
                    thisTu.append(lineBuffer + "\n");
                }
                else {
                    eof = true;
                }
            }
        }
        catch (IOException e) {
            System.err.println("I/O Exception while reading next translation unit.");
            System.err.println(e.getMessage());
            return "";                     // Nothing to return.
        }
        
        // The closing tag </trans-unit> should be in the thisTu buffer now.
        int epos = -1;        // Points to </trans-unit> ("end position")
        // If can't find <trans-unit ...
        if ((epos = thisTu.indexOf("</trans-unit")) == -1) {
            return "";        // Last trans-unit truncated; return nothing.
        }
        
        if (epos > 0) {          // We found the end.
            epos += "</trans-unit>".length();
            
            if (epos < thisTu.length()) {
                prevRemainder = thisTu.substring(epos);
                thisTu.delete(epos, thisTu.length());     // Remove chars before <trans-unit
            }
        }
        
        // The new Tu should now be in the thisTu variable; return it.
        return thisTu.toString();
    }

//    /**
//     * Passed a <trans-unit> element--complete with opening and closing tags--
//     * extract the TU ID and the specified <target> string, and store them in the
//     * tuMap for later access.
//     * @param tuElement The entire trans-unit element (with opening and closing tags)
//     * @param langStr The target language of interest.
//     * @param phaseName The phase-name of the target to store. If null, use the 
//     * <i>first</i> target that matches the specified target language. 
//     */
//    @Deprecated
//    private void storeTarget(String tuElement, String langStr, String phaseName) {
//        this.storeTarget(tuElement, langStr, phaseName, 0, -1);
//    }
    
    /**
     * Passed a <trans-unit> element--complete with opening and closing tags--
     * extract the TU ID and the specified <target> string, and store them in the
     * tuMap for later access. If the trans-unit tag has an lt:next-tu-id attribute,
     * store that as well.
     * @param tuElement The entire trans-unit element (with opening and closing tags)
     * @param langStr The target language of interest.
     * @param phaseName The phase-name of the target to store. If null, use the 
     * <i>first</i> target that matches the specified target language. If the
     * phaseName represents a non-negative integer, do the following:
     * <ul>
     * <li>If the number is 0, look for the target with the highest numbered
     *     phase (counting down from maxPhase).
     * <li>If phaseName represents a number in the range 2 <= phaseName <= maxPhase
     *     and a target is not found, try to find the next-highest numbered
     *     matching target.
     * </ul>
     * @param maxPhase The highest phase number to search (if phaseName is a
     *        string that "looks like" an integer.) 
     * @param counter zero-based index to this target's position in the XLIFF file.
     * @param ampEntities If true, change bare ampersands to amp entities
     *        in source that appears in not-yet-translated messages. (If false,
     *        don't.)
     */
    private void storeTarget(String tuElement, String langStr, String phaseName,
            int maxPhase, int counter, boolean ampEntities) {
        String tuId = "";             // trans-unit's id attribute's value
        String tuText = "";           // Text of the trans-unit element
        String target = "";
        String tuAtts = "";           // All the attributes in the opening trans-unit tag
        String nextTuId = null;         // ID of the next TU (if there is one).
        boolean foundTarget = false;  // Not yet, at least
        
        // The <trans-unit tag should start at position 0 ... but we will allow it not to
        // This pattern will capture the id and the content of the <trans-unit> element
        Matcher tum = Pattern.compile("<trans-unit(.+?\\bid=(['\"])([^'\"]+)\\2[^>]*)>(.*)</trans-unit>",
                Pattern.DOTALL).matcher(tuElement);
            
        // There *ought* to be a match!! (... or we wouldn't have been called!)
        if (tum.find()) {
            tuId = tum.group(3);    // Probably a UUID
            tuText = tum.group(4);  // Everything between <trans-unit> and </trans-unit>
            tuAtts = tum.group(1);  // All the attributes in the opening trans-unit tag
        }

        // If the segments are at less-than-paragraph level, the TU might refer to the
        // ID of the next TU in a sequence of TUs in a paragraph. If so, get the UUID
        // of the next TU:
        if (tuAtts.contains("lt:next-tu-id=")) {
            Matcher ntum = Pattern.compile("lt:next-tu-id=(['\"])(.+?)\\1",Pattern.DOTALL).matcher(tuAtts);
            if (ntum.find()) {
                nextTuId = ntum.group(2);
            }
        }
        
        // If we didn't find the target, it might be that the translation is incomplete ...
        // of that the TU was deleted because it was merged.
        // If we have a tuId, store at least something!
        if ((tuId == null) || (tuId.length() == 0)) {
            System.err.println("Couldn't locate the trans-unit id in storeTarget. The"
                    + " tuElement was:\n" + tuElement);
            // Just return. (We're probably in deep doodoo)
            return;
        }
        
        // Matcher for the target language as an attribute of the target element
        Matcher tm = Pattern.compile("<target([^>]+xml:lang=['\"]" + langStr + "['\"][^>]*)>(.*?)</target>",
            Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher("");

        // Matcher for the target languge as an attribute of the target element's
        // parent alt-trans element:
        Matcher atm = Pattern.compile("<alt-trans[^>]+?xml:lang=['\"]" + langStr + "['\"][^>]*>"
            + ".*?<target([^>]*)>(.*?)</target>", Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher("");
        
        if (phaseName == null || phaseName.length() == 0) {
            // Since a phase wasn't specified, return the first target with 
            // matching language.
            // Look for a target with a matching xml:lang attribute
            tm.reset(tuText);
        
            if (tm.find()) {
                target = tm.group(2);
                foundTarget = true;
            }
        
            // If we didn't find a target with xml:lang attribute, see if its parent
            // <alt-trans> has the sought-for xml:lang value
            if (! foundTarget) {
                atm.reset(tuText);
        
                if (atm.find()) {
                    target = atm.group(2);
                    foundTarget = true;
                }
            }
        }
        else { 
            target = getTargetTextByPhase(tuText, langStr, phaseName, maxPhase);
            if (target != null && target.length() > 0) {
                foundTarget = true;
            }
        }
        
        // Store this TU in the tuHash
        if (foundTarget) {
            tuMap.put(tuId, new TuMapEntry(target, nextTuId));
        }
        else {
            if (ampEntities) { // ODF (doc, etc.)
                tuMap.put(tuId, new TuMapEntry(" [Segment " 
                    + Integer.toString(counter+1) + " not yet translated: "
//                    + unEscapeTuString(this.parseTuSourcePlaintext(tuElement)).replace("&", "&amp;") 
                    + this.parseTuSourcePlaintext(tuElement) 
                    + "] ", 
                    nextTuId));
            }
            else {  // Things like HTML and Plaintext ...
                tuMap.put(tuId, new TuMapEntry(" [Segment " 
                    + Integer.toString(counter+1) + " not yet translated: "
//                    + unEscapeTuString(this.parseTuSourcePlaintext(tuElement)) + "] ", 
//                    + unEscapePlaintextTuSourceString(this.parseTuSourcePlaintext(tuElement)) + "] ", 
                    + this.parseTuSourcePlaintext(tuElement).replace("&amp;", "&") 
                    + "] ", 
                    nextTuId));
            }
        }
    }

    /**
     * Escape string to store safely as valid XML
     * @param in String to be escaped.
     * @return The string appropriately escaped
     */
    public static String escapeTuString(String in) {
        String retString = in;
        if ((in != null) && (in.length() > 0)) {
            /** Note: order is important! */
            retString = retString.replace("&","&amp;"); // Must be first!!
            retString = retString.replace("<","&lt;").replace(">","&gt;").replace("'","&apos;").replace("\"","&quot;");
        }
        return retString;
    }

    /**
     * Un-do the escaping performed by escapeTuString
     * @param in String to be unescaped.
     * @return The string appropriately unescaped
     */
    public static String unEscapeTuString(String in) {
        String retString = in;      // What we will return.
        if ((in != null) && (in.length() > 0)) {
            /** Note: order is important: Unescape &amp; last
             * so that we don't end up converting &apos; &quot;
             * &lt; and &gt; in the original document to their
             * unescaped versions. (In the conversion to XLIFF,
             * those will have becomd &amp;apos;, &amp;quot;
             * &amp;lt; and &amp;gt respectively. See the
             * characters method in HtmlHandler.
             *
             */
            retString = retString.replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
            retString = retString.replace("&amp;", "&");    // This must come last!!
        }
        return retString;
    }

//    /**
//     * Special unescape *only* for the strings in the original source--displayed
//     * in exported HTML files following the "Not yet translated" error message.
//     * @param in String to be unescaped.
//     * @return The string appropriately unescaped
//     */
//    public static String unEscapePlaintextTuSourceString(String in) {
//        String retString = in;      // What we will return.
//        if ((in != null) && (in.length() > 0)) {
//            
//            retString = retString.replace("&amp;", "&");    // Note that this is *first* in this method!!
////            retString = retString.replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">");
//        }
//        return retString;
//    }
    
    
}
