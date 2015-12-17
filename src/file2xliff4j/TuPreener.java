/*
 * TuPreener.java
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
import java.util.regex.*;
import java.text.BreakIterator;
//import com.ibm.icu.text.BreakIterator;

/**
 * Class to represent an XLIFF element (e.g., bx, ex, x ...) and its adjacent white
 * space (if it exists)
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class ElementAndSpace {
    private String elementText;
    private String elementName;
    private String adjacentSpace;
    private String rid;               // Reference Identifier (rid attribute value)
    private boolean outsideOfCore;    // Defaults to false
    
    // rid matcher.
    private Matcher ridM 
        = Pattern.compile("\\brid=['\"]([^'\"]+)['\"]",Pattern.CANON_EQ).matcher("");
    
    /**
     * Set the characters that make up he element
     * @param element The element's text--probably an "empty" tag, with
     *                attributes.
     */
    public void setElement(String element) {
        elementText = element;

        // While we're at it, extract and set the reference identifier (rid)
        ridM.reset(element);
        if (ridM.find()) {
            rid = ridM.group(1);
        }
        else {
            rid = "";
        }
        
        // Also set the element name
    } 
    
    /**
     * Return the String representing the characters of the element
     * @return The element's characters
     */
    public String getElement() {
        if (elementText == null) {
            return "";
        }
        return elementText;
    }
    
    /**
     * Set the value of the white space that is adjacent to the element
     * represented by this object
     * @param space The white space adjacent to the XLIFF element
     */
    public void setAdjacentSpace(String space) {
        adjacentSpace = space;
    }
    
    /**
     * Return a string (possibly of zero length) that represents the characters
     * in the whitespace adjacent to the element
     * @return The adjacent whitespace
     */
    public String getAdjacentSpace() {
        if (adjacentSpace == null) {
            return "";
        }
        return adjacentSpace;
    }
    
    public void setOutsideOfCore(boolean isOutside) {
        outsideOfCore = isOutside;
    }
    
    public boolean isOutsideOfCore() {
        return outsideOfCore;
    }
    
    public String getRid() {
        if (rid == null) {
            return "";
        }
        return rid;
    }
}

/**
 * Class to represent a bx or ex element and its rid on a stack.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class BxExStackEntry {
    private String elementName;       // bx or ex
    private String rid;               // Reference Identifier (rid attribute value)
    
    /**
     * Constructor that takes an element Name and rid
     */
    public BxExStackEntry(String elementName, String rid) {
        this.elementName = elementName;
        this.rid = rid;
    }
    
    /**
     * Return the element name (bx or ex)
     * @return the name.
     */
    public String getElementName() {
        return elementName;
    }
    
    /**
     * Return the bx or ex element's rid attribute's value.
     * @return the rid value
     */
    public String getRid() {
        return rid;
    }
}

/**
 * Class to represent a (sentence, probably) segment in a muti-sentence
 * paragraph.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
class SegmentInfo {
    private String segment;       // Candidate segment string
    private boolean translatable; // Does this candidate have translatable text?
    
    /**
     * Constructor that takes a candidate segment string and an indication of
     * whether it is translatable text. (If the segment contains no translatable
     * information, we will write it to the format file and put a placeholder
     * in the skeleton file to retrieve the information and put it in the
     * translated document that is generated from a target in the XLIFF.)
     * @param segment A (sentence) segment in a paragraph
     * @param isTranslatable Indicator of whether the segment contains
     *        translatable text.
     */
    public SegmentInfo(String segment, boolean isTranslatable) {
        this.segment = segment;
        this.translatable = isTranslatable;
    }
    
    /**
     * Return the candidate segment string
     * @return The segment.
     */
    public String getSegmentStr() {
        return segment;
    }

    public void setSegmentStr(String segStr) {
        this.segment = segStr;
    }
    
    /**
     * Indicate whether the segment has translatable text.
     * @return true if text is translatable, else false.
     */
    public boolean translatable() {
        return translatable;
    }
}

/**
 * Class to "preen" translation units--i.e. to identify the "core" text that
 * is completely enclosed by paired bx/ex tags. (If x tags are located outside
 * the core text, then are so identified as well.)
 * <p>The class also includes methods for retrieving and updating the core
 * text.
 *
 * @author weldon@lingotek.com
 */
public class TuPreener {
    
    /** Creates a new instance of TuPreener */
    private TuPreener() {
    }

    /** Tag that marks the beginning of the core Translation Unit source
     * or target text. <i>Note:</i> using lt:core elements within source or target
     * elements violates the XLIFF spec: 
     * <blockquote>"It is not possible to add non-XLIFF elements in either the 
     * &lt;source&gt;  or  &lt;target&gt; elements. However, the &lt;mrk&gt;
     * element can be used to markup sections of the text with user-defined 
     * values assigned to the mtype attribute. You can also add non-XLIFF 
     * attributes to most of the inline elements used in &lt;source&gt; and 
     * &lt;target&lt;</blockquote>
     */
    @Deprecated
    public static final String CORE_START_TAG = "<lt:core>";
    
    /** Tag that marks the end of the core Translation Unit source
     * or target text */
    @Deprecated
    public static final String CORE_END_TAG = "</lt:core>";
    
    /** The namespace URI of the lt:core tags */
    public static final String NAME_SPACE_URI = "http://www.lingotek.com/";

    /** Instead of lt:core elements, use XLIFF mrk with the mtype='x-coretext'
     * attribute. */
    public static final String CORE_START_MRK = "<mrk mtype='x-coretext'>";
    
    /** For the present, we can assume that </mrk> signals the end of core text
     * (since the only other occurrence of the mrk tag is as an "empty" mrk
     * element with mtype='x-mergeboundary'). If/when we decide to use another
     * non-empty mrk element in source or target elements, we will need to
     * implement less trivial parsing to match the CORE_END_MRK with the proper
     * start tag. */
    public static final String CORE_END_MRK = "</mrk>";
    

    // u00a0 is a non-breaking space
    // u00b7 is a middle dot
    // u2002 is an en space
    // u2003 is an em space
    // u2022 is a bullet character
    // u2023 is a triangular bullet
    // u2043 is a hyphen bullet
    // u204c is a black leftwards bullet
    // u204d is a black rightwards bullet
    // u2219 is a bullet operator
    // u25c9 is a tainome (Japanese "fish eye" bullet)
    // u25d8 is an inverse bullet
    // u25e6 is a white bullet
    //
    // Ticket 472 9/25/2006 WLW    
    // In addition the the above, when checking the "core" TU to see if it 
    // consists exclusively of whitespace, add the following characters.
    // (In the case of underscore, for example, if a TU consists solely of
    // underscores and XLIFF tags, there is no translatable text. However,
    // it isn't clear if we want to preen leading underscores, which can
    // sometimes be the first character of programming variables. 
    // u005f spacing underscore
    // u2381 continuous underline symbol
    // u2382 discontinuous underline symbol
    // u0332 underline
    // u0333 double underline
    // u2017 double underscore, spacing
    //
    // Ticket 578 10/20/2006 WLW
    // Add the Unicode byte-order mark ufeff to the list of white space, so that
    // HTML files won't generate XLIFF with a first TU consisting solely of a
    // byte-order mark.
    // 
    // Ticket 795 1/29/2007 WLW 
    // Add checkboxes
    // u2751 Lower right shadowed white square
    // u2752 Upper right shadowed white square
    public static final String WHITE_SPACE_CLASS 
            = "[\\u0009\\u0020\\u00a0\\u00b7\\u2002\\u2003\\u2022\\u2023"
                + "\\u2043\\u204c\\u204d\\u2219\\u25c9\\u25d8\\u25e6\\u005f"
                + "\\u2381\\u2382\\u0332\\u0333\\u2017\\ufeff\\u2751\\u2752"
                + "\\u0085\\s]";

    public static final String ORRED_WHITE_SPACE
            = "\\u0009|\\u0020|\\u00a0|\\u00b7|\\u2002|\\u2003|\\u2022|\\u2023"
                + "|\\u2043|\\u204c|\\u204d|\\u2219|\\u25c9|\\u25d8|\\u25e6|\\u005f"
                + "|\\u2381|\\u2382|\\u0332|\\u0333|\\u2017|\\ufeff|\\u2751|\\u2752"
                + "|\\u0085|\\s]";
    
    // u002d hyphen
    public static final String SECONDARY_WHITE_SPACE_CLASS = "[\\u002d]";
    
    // Certain generic XML documents in our test suite "hide" tags at the
    // beginning or end of segments--often HTML formatting tags. Their opening
    // less-than and closing greater-than characters are represented (in the
    // TU) as entities. We will try to consider those in leading/trailing
    // WHITE_SPACE. Be very conservative at first: 1) Allow only alphabetic
    // characters in the tag names; 2) allow a maximum of 10 characters before
    // the closing gt entity.
    public static final String HTML_TAGS_AS_ENTITIES = "&lt;/?[a-zA-Z][a-zA-Z0-9]*[^&]*/?&gt;";
    
    private static final Matcher coreMatch 
        = Pattern.compile("^.*?<mrk\\s+mtype=(['\"])x-coretext\\1>(.*)</mrk>",Pattern.DOTALL).matcher("");
    
    /**
     * Return the text between the core start and end tags
     * @param fullText The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The text between the core start and end tags.
     */
    public static synchronized String getCoreText(String fullText) {
        // If full text is nothing, return nothing
        if (fullText == null || fullText.length() == 0) {
            return "";
        }

        // WW 6/4/7 Ticket 978: Remove soft hyphens.
        if (fullText != null && fullText.contains("\u00ad")) {
            fullText = fullText.replace("\u00ad", "");
        }
        
        // Find where the core text starts and ends. OLD METHOD first
        int coreStart = fullText.indexOf(CORE_START_TAG) + CORE_START_TAG.length();
        int coreEnd = fullText.indexOf(CORE_END_TAG);
        
        // Assuming there are core start and end tags, return what is
        // between them.
        if ((coreStart > -1) && (coreEnd >= coreStart)) {
            return fullText.substring(coreStart,coreEnd);
        }
        
        // Still here? Try the new (spec-conformant) way of delimiting the core
        coreMatch.reset(fullText);
        if (coreMatch.find()) {
            return coreMatch.group(2);
        }
        
        // Couldn't find anything; return the whole string
        return fullText;
    }
            
    /**
     * Passed the full text of a Translation Unit source or target,
     * return the text before the core start tag
     * @param fullText The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The text before the core start tag
     */
    public static String getPrefixText(String fullText) {
        // If full text is nothing, return nothing
        if (fullText == null || fullText.length() == 0) {
            return "";
        }
        
        // Find where the core text starts
        int coreStart = fullText.indexOf(CORE_START_TAG);
        
        if (coreStart > 0) {  // At least one character to return
            return fullText.substring(0,coreStart);
        }

        // Didn't find the "old-style" core mark. Try the XLIFF spec-compliant one.
        Matcher prefixMatch 
            = Pattern.compile("^(.*?)<mrk\\s+mtype=(['\"])x-coretext\\2>",Pattern.DOTALL).matcher(fullText);
        if (prefixMatch.find()) {
            return prefixMatch.group(1);
        }
        
        return "";   // No prefix.
    }

    /**
     * Passed the full text of a Translation Unit source or target,
     * return the text after the core end tag
     * @param fullText The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The text after the core end tag
     */
    public static String getSuffixText(String fullText) {
        // If full text is nothing, return nothing
        if (fullText == null || fullText.length() == 0) {
            return "";
        }

        int suffixStart = -1;
        int endTagPos = fullText.indexOf(CORE_END_TAG);
        if (endTagPos > -1) {
            // Find where the suffix text starts
            suffixStart = endTagPos + CORE_END_TAG.length();
        
            // Make sure there is something to return.
            if ((suffixStart >= (CORE_START_TAG.length() + CORE_END_TAG.length()))
                && (suffixStart <= fullText.length())) {  // At least one character to return
                return fullText.substring(suffixStart);
            }
        }
        
        // We didn't find an lt:core end tag; look for an XLIFF spec-compliant
        // </mrk> tag/
        endTagPos = fullText.indexOf(CORE_END_MRK);
        if (endTagPos > -1) {
            // Find where the suffix text starts
            suffixStart = endTagPos + CORE_END_MRK.length();
        
            // Make sure there is something to return.
            if (suffixStart <= fullText.length()) {  // At least one character to return
                return fullText.substring(suffixStart);
            }
        }
        
        return "";   // No suffix.
    }

    /**
     * Passed the full text of a Translation Unit source or target (including
     * core start and end markers) remove the tags and return what is left
     * @param fullText The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @return The new full text with marker tags removed.
     */
    public static String removeCoreMarks(String fullText) {
        // If full text is nothing, then markers already removed (!!)
        if (fullText == null || fullText.length() == 0) {
            return "";
        }
        
        String stripped = fullText;
        if (stripped.contains("lt:core>")) {
            stripped = stripped.replace(TuPreener.CORE_START_TAG, "").replace(TuPreener.CORE_END_TAG, "");
        }
         
        if (stripped.contains("x-coretext")) {
            // Strip out the XLIFF spec-compliant core marks
            stripped = stripped.replaceAll("<mrk\\s+mtype=['\"]x-coretext['\"]>", "").replace(TuPreener.CORE_END_MRK, "");
        }
        
        return stripped;  
    }

    /**
     * Passed the text of a Translation Unit source or target (with or without
     * core start and end marks), remove the mrk tags of mtype x-mergeboundary.
     * @param fullText Text of the Translation Unit source or target,
     *        with merge boundary 
     * @return The text with x-mergeboundary mrk's removed.
     */
    public static String removeMergerMarks(String fullText) {
        // If full text is nothing, then markers already removed (!!)
        if (fullText == null || fullText.length() == 0) {
            return "";
        }

        // Don't create a matcher if there are no signs of x-mergeboundary mrk tags
        if (fullText.contains("x-mergeboundary")) {
            Matcher mbMatcher = Pattern.compile("<mrk[^>]*?mtype=['\"]x-mergeboundary['\"][^>]*>",
                    Pattern.DOTALL).matcher(fullText);
            return mbMatcher.replaceAll("");
        }
        else {
            return fullText;
        }
    }
    
    
    /**
     * Passed the full text of a Translation Unit source or target (including
     * core start and end markers) and new core text, replace the old core
     * text with the new and return the new full text
     * @param fullText The full text of the Translation Unit source or target,
     *        complete with core text marker tags.
     * @param newCore The new core text to substitute for the old core text
     *                A value of null for newCore implies not to change
     *                the fullText.
     * @return The new full text (complete with marker tags.
     */
    public static String replaceCoreText(String fullText, String newCore) {
        if (fullText == null || fullText.length() == 0) {
            if (newCore == null) {    // null --> Don't change anything
                return fullText;
            }
            else {  // Note: newCore *might* be a zero-length string
                return CORE_START_MRK + newCore + CORE_END_MRK;
            }
        }
        
        else {
            if (newCore == null) {
                return fullText;
            }
            else {
                return getPrefixText(fullText) 
                    + CORE_START_MRK + newCore
                    + CORE_END_MRK + getSuffixText(fullText);
            }
        }
    }

    /**
     * Passed a String that contains a the text of a "paragraph," a 
     * segment boundary type indicator and the locale of the text in the string,
     * divide the input string into segments, marking each segment's "cores."
     * Return an array of segment objects. (If the boundary type is PARAGRAPH, 
     * return a single-element array that contains the original input string, 
     * with the core text marked with mrk elements of mtype x-coretext. If the 
     * boundary type is SENTENCE, store each sentence in an element of the 
     * return array, with the core of each marked.
     * @param in The input string that contains (potentially) a paragraph
     *        segment
     * @param bdyType Segment boundary type (e.g. paragraph, sentence)
     * @param locale The language of the string--used by the sentence break
     *        iterator to break into sentences.
     * @return An array of zero or more segment information objects, each one 
     *         potentially marked (using lt:core tags) to indicate its core 
     *         translatable text.
     */
    public static SegmentInfo[] getCoreSegments(String in, SegmentBoundary bdyType,
            Locale locale) {
        return TuPreener.getCoreSegments(in, bdyType, locale, false);
    }
    
    /**
     * Passed a String that contains a the text of a "paragraph," a 
     * segment boundary type indicator and the locale of the text in the string,
     * divide the input string into segments, marking each segment's "cores."
     * Return an array of segment objects. (If the boundary type is PARAGRAPH, 
     * return a single-element array that contains the original input string, 
     * with the core text marked with mrk elements. If the boundary type is 
     * SENTENCE, store each sentence in an element of the return array, with the 
     * core of each marked.
     * @param in The input string that contains (potentially) a paragraph
     *        segment
     * @param bdyType Segment boundary type (e.g. paragraph, sentence)
     * @param locale The language of the string--used by the sentence break
     *        iterator to break into sentences.
     * @param preenHtmlFromXML If true, look for HTML-like tags that are possibly
     *        outside the "core"--tags that represent "less-than" and "greater-than"
     *        as entities. If found on the edges of segments, move them outside
     *        the core.
     * @return An array of zero or more segment information objects, each one 
     *         potentially marked (using mrk tags) to indicate its core 
     *         translatable text.
     */
    public static SegmentInfo[] getCoreSegments(String in, SegmentBoundary bdyType,
            Locale locale, boolean preenHtmlFromXML) {
        // Handle the trivial case, where segments are split on paragraph
        // boundaries.
        if (bdyType.equals(SegmentBoundary.PARAGRAPH)) {
            String coreTu = markCoreTu(in, bdyType, preenHtmlFromXML);
            if (coreTu.length() > 0) {
                SegmentInfo[] segArr = new SegmentInfo[1];
                SegmentInfo seginfo = new SegmentInfo(coreTu,true);
                segArr[0] = seginfo;
                return segArr;
            }
            else {
                return new SegmentInfo[0];
            }
        }
        else {        // SENTENCE segment boundaries
            if (in.trim().length() == 0) {
                return new SegmentInfo[0];
            }
            
            boolean hasMeaningfulText = false;  // Not until disproved ...
            
            ArrayList<SegmentInfo> tempSegs = new ArrayList<SegmentInfo>();
            
            // We need to break the string into sentence segments. Use BreakIterator 
            // for the specified locale
            BreakIterator sBoundary = BreakIterator.getSentenceInstance(locale);

            sBoundary.setText(in);      // Tell sentence iterator about the TU
            int sStart = sBoundary.first();   // Get the start of the first sentence.
            
            SENTENCE_LOOP:
            for (int sEnd = sBoundary.next(); sEnd != BreakIterator.DONE;
                 sStart = sEnd, sEnd = sBoundary.next()) { 
                
                String curSentence = in.substring(sStart,sEnd);
                
                String coreTu = markCoreTu(curSentence, bdyType, preenHtmlFromXML);  // Mark the core of the sentence
                if (coreTu.length() > 0) {
                    SegmentInfo segInfo = new SegmentInfo(coreTu,true);
                    tempSegs.add(segInfo);
                    hasMeaningfulText = true;
                }
                else {
//                    // The "sentence" had no translatable text. We still need 
//                    // to keep track of it, though (in the skeleton and format
//                    // files), so that we can include it when we generate a
//                    // document from the targets in the XLIFF file.
//                    // Notice that we save the original sentence (curSentence)
//                    // rather than the core Tu with lt:core marks around it.
//                    SegmentInfo segInfo = new SegmentInfo(curSentence,false);
//                    tempSegs.add(segInfo);
                    // If this isn't the first segment, then append this to the
                    // last segment (in the tempSegs array). The last segment
                    // will contain lt:core beginning and end tags. This will
                    // be appended after the closing lt:core tag.
                    if (tempSegs.size() > 0) {
                        SegmentInfo segInfo = tempSegs.get(tempSegs.size() - 1);
                        
                        segInfo.setSegmentStr(segInfo.getSegmentStr() + curSentence);
                        tempSegs.set(tempSegs.size() - 1, segInfo);
                    }
                }
            }  // We've identified all the sentence segments

            if (hasMeaningfulText) {
                // Return array only if at least one segment has meaningful text
                SegmentInfo[] segArr = new SegmentInfo[tempSegs.size()];
                for (int i = 0; i < tempSegs.size(); i++) {
                    segArr[i] = tempSegs.get(i);
                }

                return segArr;
            }
            else {    // No translatable text.
                return new SegmentInfo[0];
            }
        }
    }

    /**
     * Mark the core text of a translation unit: Passed a string to be stored in
     * a trans-unit source or target, determine if
     * <ol>
     * <li>the string consists exclusively of white-space and/or tags. If it does,
     *     return a zero-length string.
     * <li>all translatable text is either preceded or followed by one or more singleton 
     *     empty tags (e.g. &lt;x/>). If so mark such tag(s) as being outside the
     *     "core" text of the TU.
     * <li>paired tags (e.g., paired bx and ex tags) completely enclose all translatable
     *     text in the TU. If so, mark them as being outside the "core" text of the TU.
     * <li>paired tags (either beginning/ending tags or matched bx/ex tags), separated
     *     only by white space (or other tags) either precede of follow all translatable 
     *     text. If so, mark such tags as being outside the "core" text of the TU.
     * </ol>
     * Surround the "core" text of the TU with paired <lt:core> and </lt:core> tags.
     * @param in The candidate input TU text to be examined
     * @return The resulting TU string, either marked with its "core" contents, or
     *  reduced to a zero length string if it contains no translatable text at all.
     */
    public static String markCoreTu(String in) {
        return TuPreener.markCoreTu(in, SegmentBoundary.PARAGRAPH, false);
    }

    /**
     * Mark the core text of a translation unit: Passed a string to be stored in
     * a trans-unit source or target, determine if
     * <ol>
     * <li>the string consists exclusively of white-space and/or tags. If it does,
     *     return a zero-length string.
     * <li>all translatable text is either preceded or followed by one or more singleton 
     *     empty tags (e.g. &lt;x/>). If so mark such tag(s) as being outside the
     *     "core" text of the TU.
     * <li>paired tags (e.g., paired bx and ex tags) completely enclose all translatable
     *     text in the TU. If so, mark them as being outside the "core" text of the TU.
     * <li>paired tags (either beginning/ending tags or matched bx/ex tags), separated
     *     only by white space (or other tags) either precede of follow all translatable 
     *     text. If so, mark such tags as being outside the "core" text of the TU.
     * </ol>
     * Surround the "core" text of the TU with paired <lt:core> and </lt:core> tags.
     * @param in The candidate input TU text to be examined
     * @param segment The type of segmentation boundary. (If PARAGRAPH, markCoreTu
     *        assumes that all tags are balanced. If SENTENCE, it will look for
     *        bx tags without ending ex tags (which might be in a later sentence,
     *        in the same paragraph, for example), or ex tags without start bx 
     *        tags (which might be in an earlier sentence in the same paragraph)
     * @return The resulting TU string, either marked with its "core" contents, or
     *  reduced to a zero length string if it contains no translatable text at all.
     */
  
    public static String markCoreTu(String in, SegmentBoundary segment) {
        return TuPreener.markCoreTu(in, segment, false);
    }
    
    
    /**
     * Mark the core text of a translation unit: Passed a string to be stored in
     * a trans-unit source or target, determine if
     * <ol>
     * <li>the string consists exclusively of white-space and/or tags. If it does,
     *     return a zero-length string.
     * <li>all translatable text is either preceded or followed by one or more singleton 
     *     empty tags (e.g. &lt;x/>). If so mark such tag(s) as being outside the
     *     "core" text of the TU.
     * <li>paired tags (e.g., paired bx and ex tags) completely enclose all translatable
     *     text in the TU. If so, mark them as being outside the "core" text of the TU.
     * <li>paired tags (either beginning/ending tags or matched bx/ex tags), separated
     *     only by white space (or other tags) either precede of follow all translatable 
     *     text. If so, mark such tags as being outside the "core" text of the TU.
     * </ol>
     * Surround the "core" text of the TU with paired <lt:core> and </lt:core> tags.
     * @param in The candidate input TU text to be examined
     * @param segment The type of segmentation boundary. (If PARAGRAPH, markCoreTu
     *        assumes that all tags are balanced. If SENTENCE, it will look for
     *        bx tags without ending ex tags (which might be in a later sentence,
     *        in the same paragraph, for example), or ex tags without start bx 
     *        tags (which might be in an earlier sentence in the same paragraph)
     * @param preenHtmlFromXML If true, look for HTML-like tags that are possibly
     *        outside the "core"--tags that represent "less-than" and "greater-than"
     *        as entities. If found on the edges of segments, move them outside
     *        the core.
     * @return The resulting TU string, either marked with its "core" contents, or
     *  reduced to a zero length string if it contains no translatable text at all.
     */
  
    public static String markCoreTu(String in, SegmentBoundary segment, 
            boolean preenHtmlFromXML) {
        // First of all, if the string is null or zero-length already, just return a
        // zero-length string.
        if ((in == null) || (in.length() == 0)
            || (in.trim().length() == 0)) {  // Nothing meaningful.
            return "";                       // Return zero-length string
        }

        // Second of all, if the string already contains lt:core marks, don't
        // reprocess the string!
        if (in.indexOf(TuPreener.CORE_START_TAG) > -1) {
            // Replace existing lt:core tags with mrks.
            return in.replace(TuPreener.CORE_START_TAG,
                    TuPreener.CORE_START_MRK).replace(TuPreener.CORE_END_TAG, 
                    TuPreener.CORE_END_MRK);
        }
        
        Matcher coreMrkMatcher = Pattern.compile("<mrk\\s+mtype=(['\"])x-coretext\\1>", Pattern.DOTALL).matcher(in);
        if (coreMrkMatcher.find()) {
            return in;                  // Already has core mrk tag.
        }
        
        // Set a default value for the segment boundary if null was specified.
        if (segment == null) {
            segment = SegmentBoundary.SENTENCE;
        }
        
        ArrayList<ElementAndSpace> prefixTags = new ArrayList<ElementAndSpace>();
        ArrayList<ElementAndSpace> suffixTags = new ArrayList<ElementAndSpace>();
        
        String leadingSpace = "";     // At the *very* beginning of the TU
        String trailingSpace = "";    // At the *very* end of the TU
        
        String core = in;             // Candidate core of the TU is the entire string

        // I S O L A T E   L E A D I N G   S P A C E   F R O M   T H E   T U   S T R I N G
        // Matcher to extract prefix whitespace
        Matcher ms;
        if (preenHtmlFromXML) {
            ms = Pattern.compile("^(" + WHITE_SPACE_CLASS + "|" + HTML_TAGS_AS_ENTITIES + ")+",
                Pattern.CANON_EQ).matcher("");
        }
        else {
            ms = Pattern.compile("^(" + WHITE_SPACE_CLASS + "+)",Pattern.CANON_EQ).matcher("");
        }
        ms.reset(core);
        if (ms.find()) {                           // Look for leading white space
            leadingSpace = ms.group(1);
            if (leadingSpace != null) {
                core = core.substring(ms.end(1));
            }
            else {
                leadingSpace = "";                 // No leading space.
            }
        }
        else {
            leadingSpace = "";         // No leading whitespace, so the core is the whole input so far
        }

        // I S O L A T E   T R A I L I N G   S P A C E   F R O M   T H E   T U   S T R I N G
        // Matcher for whitespace at end
        // Note: Java Regexes seem to be broken in a major way when it comes to
        // matching something like trailing "\n\\u0020\\u0020\\u0020"
        // We will try another tactic
        String trimmedTail = core.trim(); // Prefix already trimmed above
        if (trimmedTail.length() < core.length()) {
            trailingSpace = core.substring(trimmedTail.length());
            core = trimmedTail;
        }
        
        Matcher ns;
        if (preenHtmlFromXML) {
            ns = Pattern.compile("(" + WHITE_SPACE_CLASS + "|" + HTML_TAGS_AS_ENTITIES + ")+$",
                Pattern.CANON_EQ).matcher("");
        }
        else {
            ns = Pattern.compile("(" + WHITE_SPACE_CLASS + "+)$",Pattern.CANON_EQ).matcher("");
        }
        ns.reset(core);
        if (ns.find()) {
            trailingSpace = ns.group(1) + trailingSpace;  // Whitespace at end.
//            if (trailingSpace != null) {
                core = core.substring(0,ns.start(1));
//            }
//            else {
//                trailingSpace = "";
//            }
        }
//        else {
//            trailingSpace = "";
//        }
        
        /**************************************************************************
         * Now isolate leading tags and adjacent space (adjacent on the side of
         * the tag closest to the core TU test)
         **************************************************************************/
  
        // Matcher for a leading tag
        Matcher mt = Pattern.compile("^(<[^>]+>)",Pattern.CANON_EQ).matcher("");
        
        // Get leading tags/whitespace
        // Perl's regexes could do the following in one statement. However, Java is
        // broken in a major way, so we have to loop and do it piecemeal
        String tag = "";
        while (true) {
            ElementAndSpace curTag = new ElementAndSpace();
            mt.reset(core);
            if (mt.find()) {
                tag = mt.group(1);   // The next prefix tag
                if ((tag != null) && (tag.length() > 0)) {
                    core = core.substring(mt.end(1));
                    curTag.setElement(tag);
                }
                else {
                    break;     // Exit this loop--no more leading tags
                }

                // We found a tag. Look for adjacent whitespace.
                ms.reset(core);
                if (ms.find()) {                           // Look for leading white space
                    String prefixWhiteSpace = ms.group(1);
                    if (prefixWhiteSpace != null) {
                        core = core.substring(ms.end(1));
                        curTag.setAdjacentSpace(prefixWhiteSpace);
                    }
                }
            }
            else {
                break;
            }
            
            // Add the current tag (and adjacent space, if applicable) to the list
            // of prefix tags
            prefixTags.add(curTag);
        }

        // Now get trailing tags and whitespace at the end of the TU
        if (core.length() > 0) {    // Something besides leading tags & whitespace
            // Matcher for a  single trailing suffix tag
            Matcher nt = Pattern.compile("(<[^>]+>)$",Pattern.CANON_EQ).matcher("");

            while (true) {
                ElementAndSpace curTag = new ElementAndSpace();
                
                // Get a single trailing suffix tag
                nt.reset(core);
                if (nt.find()) {               // If we found another tag
                    tag = nt.group(1);
                    if (tag != null) {
                        core = core.substring(0,nt.start(1));
                        curTag.setElement(tag);
                    }
                    else {
                        break;     // Exit this loop, no more trailing tags
                    }

                    String suffixWhiteSpace = ""; // Candidate suffix tags/whitespace
                    // Get the whitespace at the end of the TU
                    ns.reset(core);
                    if (ns.find()) {
                        suffixWhiteSpace = ns.group(1);  // Whitespace at end.
                        if (suffixWhiteSpace != null) {
                            core = core.substring(0,ns.start(1));
                            curTag.setAdjacentSpace(suffixWhiteSpace);
                        }
                    }
                }
                else {
                    break;
                }
                suffixTags.add(curTag);
            }
        }
        
        // Check for meaningful characters in the core. 
        if (core.trim().length() == 0) {
            return "";
        }
        
        // Ticket 472 9/25/2006 WLW
        // If the core TU consists exclusively of WHITESPACE and XLIFF tags,
        // return ""
        Matcher ws;
        if (preenHtmlFromXML) {
            ws = Pattern.compile("^(?:" + WHITE_SPACE_CLASS +
                "|" + SECONDARY_WHITE_SPACE_CLASS + "|<[be]?x[^>]*>|" + 
                HTML_TAGS_AS_ENTITIES + ")*$", Pattern.DOTALL).matcher("");
        }
        else {
            ws = Pattern.compile("^(?:" + WHITE_SPACE_CLASS +
                "|" + SECONDARY_WHITE_SPACE_CLASS + "|<[be]?x[^>]*>)*$", Pattern.DOTALL).matcher("");
        }
        ws.reset(core);
        if (ws.find()) {
            return "";
        }
        
        /****************************************************************************
         * With a core string of text and lists of prefix and suffix tags and whitespace
         * we are ready to look further.
         ****************************************************************************/
        // First, however, if we have no prefix and suffix tags, just return the string
        if (prefixTags.isEmpty() && suffixTags.isEmpty()) {
            return leadingSpace + CORE_START_MRK + core + CORE_END_MRK + trailingSpace;
        }
        
        // Look through the prefix tags and determine which are inside and which
        // are outside the core.
        for (int i = 0; i < prefixTags.size(); i++) {
            boolean foundMatch = false;
            
            // Is this a singleton <x/> element (for example)?
            if (isSingleton(prefixTags.get(i).getElement())) {
                // If so, it is definitely outside the core
                prefixTags.get(i).setOutsideOfCore(true);
                foundMatch = true;   // Well, sort of--we match ourself
            }
            
            // Look at suffix tags to see if they contain a
            // matching rid (but only if we haven't found a match yet).
            for (int j = 0; (j < suffixTags.size()) && (! foundMatch) ; j++) {
                if ((prefixTags.get(i).getRid().equals(suffixTags.get(j).getRid())) 
                    && (prefixTags.get(i).getRid().length() > 0)) {
                    // We have a match!! Mark both tags as outside the core!
                    prefixTags.get(i).setOutsideOfCore(true);
                    suffixTags.get(j).setOutsideOfCore(true);
                    foundMatch = true;   // We can exit for loop
                }
            }
                
            // If not found match yet, look at later prefix tags for a matching
            // rid (but only if we haven't found a match yet).
            for (int j = i+1; (j < prefixTags.size()) && (! foundMatch) ; j++) {
                if ((prefixTags.get(i).getRid().equals(prefixTags.get(j).getRid()))
                    && (prefixTags.get(i).getRid().length() > 0)) {
                    prefixTags.get(i).setOutsideOfCore(true);
                    prefixTags.get(j).setOutsideOfCore(true);
                    foundMatch = true;   // We can exit for loop
                }
            }
            
            // WW 11/21/2006: If we are segmenting on sentence boundaries, there
            // might be a singleton bx tag in the prefix with no matching ex
            // tag in the suffix (as it occurs in a later sentence in the same
            // paragraph). If that is the case, then consider it a singleton and
            // mark it as outside the core
            if ((!foundMatch) && segment.equals(SegmentBoundary.SENTENCE)) {
                // Check the core string of text for a matching tag.
                String rid = prefixTags.get(i).getRid();
                if (rid != null && rid.length() > 0) {
                    // This is a bx or ex tag with a rid
                    String elementText = prefixTags.get(i).getElement();
                    if ((elementText != null) && (elementText.length() > 3)
                        && elementText.substring(1,3).equals("bx")) {
                        // If the core doesn't have a matching ex, treat this as
                        // a singleton and move it outside the core.
                        if (!core.matches(".*<ex\\s[^>]*?rid=['\"]" + rid + "['\"].*")) {
                            prefixTags.get(i).setOutsideOfCore(true);
                        }
                    }
                }
            }
        }
        
        // Now let's look through the suffix tags. If we found one whose rid matched
        // a suffix tag and is *outside* the core text, then all following tags 
        // are also outside the core text (since XLIFF is well-formed XML).
        boolean restAreOutsideCore = false;
        int indexOfFirstNonCoreSuffixTag = -1;
        for (int i = suffixTags.size()-1; i >= 0; i--) {
            // Note: The right-most suffix tag is at array position 0
            if (restAreOutsideCore) {
                suffixTags.get(i).setOutsideOfCore(true);
            }
            
            else if (suffixTags.get(i).isOutsideOfCore()) {
                restAreOutsideCore = true;
                indexOfFirstNonCoreSuffixTag = i;
            }
        }
        
        // The indexOfFirstNonCoreSuffixTag variable tells us the first 
        // suffix tag that we know of (so far) that is outside the core.
        // There is still a possibility that we can improve on that by
        // identifying pairs of tags in the suffix that share rids or that
        // are singleton (x) tags)
        for (int i = indexOfFirstNonCoreSuffixTag + 1;
                i < suffixTags.size(); i++) {
            boolean foundMatch = false;

            // Is this a singleton <x/> element (for example)?
            if (isSingleton(suffixTags.get(i).getElement())) {
                // If so, it is definitely outside the core
                suffixTags.get(i).setOutsideOfCore(true);
                foundMatch = true;   // Well, sort of--we match ourself
            }

            // If not found match yet, look at earlier suffix tags for a matching
            // rid (but only if we haven't found a singleton match yet).
            for (int j = i+1; (j < suffixTags.size()) && (! foundMatch) ; j++) {
                if ((suffixTags.get(i).getRid().equals(suffixTags.get(j).getRid()))
                    && (suffixTags.get(i).getRid().length() > 0)) {
                    suffixTags.get(i).setOutsideOfCore(true);
                    suffixTags.get(j).setOutsideOfCore(true);
                    foundMatch = true;   // We can exit for loop
                }
            }

            // WW 11/29/2006: If we are segmenting on sentence boundaries, there
            // might be a singleton ex tag in the suffix with no matching bx
            // tag in the suffix or the core (as it occurs in an earlier sentence
            // in the same paragraph). If that is the case, then consider it a 
            // singleton and mark it as outside the core
            if ((!foundMatch) && segment.equals(SegmentBoundary.SENTENCE)) {
                // Check the core string of text for a matching tag.
                String rid = suffixTags.get(i).getRid();
                if (rid != null && rid.length() > 0) {
                    // This is a bx or ex tag with a rid
                    String elementText = suffixTags.get(i).getElement();
                    if ((elementText != null) && (elementText.length() > 3)
                        && elementText.substring(1,3).equals("ex")) {
                        // If the core doesn't have a matching bx, treat this as
                        // a singleton and move it outside the core.
                        if (!core.matches(".*<bx\\s[^>]*?rid=['\"]" + rid + "['\"].*")) {
                            suffixTags.get(i).setOutsideOfCore(true);
                        }
                    }
                }
            }
        }
 
        /*
         * We might have a situation something like this:
         * Prefix:
         * <bx rid="484"/><bx rid="485"/>
         *
         * Suffix:
         * <x /><ex rid="487"/><ex rid="486"/><ex rid="484"/>
         *
         * The above will have flagged the pair with rid 484 (first and last
         * tags) as outsideCore. However, it will have also flagged the first
         * <x /> in the suffix as outside the core (but not rid 487 and 486
         * that follow). We need to rectify that:
         */
        
        boolean restAreInside = false;
        for (int i = 0; i < suffixTags.size(); i++) {
            if (suffixTags.get(i).isOutsideOfCore()) {
                if (restAreInside) {
                    suffixTags.get(i).setOutsideOfCore(false);
                }
                // else leave outside of core
            }
            else {
                restAreInside = true;
            }
        }
        
        /*********************************************************************
         * Whew! Now we know about all prefix and suffix tags and whitespace
         * Let's return what we know.
         *********************************************************************/
        StringBuilder outBuf = new StringBuilder();
        
        // Write the leading whitespace
        outBuf.append(leadingSpace);
        
        // Write the prefixTags and adjacent whitespace
        boolean outsideCore = true;
        for (int i = 0; i < prefixTags.size(); i++) {
            if ((! prefixTags.get(i).isOutsideOfCore()) // This tag inside core
                && outsideCore) {                       // Previous ones outside
                // This is where we cross over from outsider to insider
                outsideCore = false;                    // We have crossed over
                outBuf.append(CORE_START_MRK);          // ... and this witnesses it
            }
            outBuf.append(prefixTags.get(i).getElement());
            outBuf.append(prefixTags.get(i).getAdjacentSpace());
        }
        
        if (outsideCore) { // ... Still (meaning that the above for loop stayed outside)
            outBuf.append(CORE_START_MRK); // Then we're moving into the core
        }
        
        // Write the core
        outBuf.append(core);
        
        // Write the suffix whitespace and adjacent tags
        boolean insideCore = true;
        for (int i = suffixTags.size() -1 ; i >= 0; i--) {
            if (suffixTags.get(i).isOutsideOfCore()  // This tag is outside core
                && insideCore) {                     // Previous one was inside
                // This is where we cross over from insider to outsider
                insideCore = false;                  // We've crossed over 
                outBuf.append(CORE_END_MRK);         // ... and this witnesses it
            }
            outBuf.append(suffixTags.get(i).getAdjacentSpace()); // Space first
            outBuf.append(suffixTags.get(i).getElement());       // *Then* tag
        }
        
        if (insideCore) { // ... Still (meaning that the above for loop didn't cross over)
            outBuf.append(CORE_END_MRK);
        }
        // Now append trailing whitespace
        outBuf.append(trailingSpace);
        
        return (outBuf.toString());
    }
    
    /**
     * Is this a singleton tag? (For now, that means an empty x tag.)
     * @param tag The tag to examine for singletonness
     * @return true if a singleton tag, else false.
     */
    public static boolean isSingleton(String tag) {
        if ((tag == null) || (tag.length() == 0)) {
            return false;                          // Or should this return true?
        }
        
        // Matcher for an x tag
        Matcher xm = Pattern.compile("<x [^>]*/>",Pattern.CANON_EQ).matcher(tag);

        if (xm.find()) {       // Is this an x tag?
            return true;
        }
                                 
        return false;          // Not a singleton tag (x for now)
    }

    /**
     * Passed the core text of a tu that originates from a format that doesn't
     * necessarily map to well-formed XML (non-XHTML HTML, for example), verify 
     * that the only tags present are bx, ex and x tags (for our implementation,
     * at least). The tags need not necessarily be properly nested.
     * <p>While validating, remove non-bx/ex/x tags.
     * <p><i>Note:</i> Although XLIFF allows source and target elements to
     * include tags/elements other than bx, ex and x, this particular implementation
     * allows only those three (empty) elements. (Since the text we are passed is the
     * <i>core</i> of the TU, it doesn't include our start and end mrk tags.)
     * @param tuText The text of the TU
     * @return A checked and repaired (if necessary) text string.
     */
    public static String checkAndRepairTuTags(String tuText) {
        if (tuText == null) {
            return "";          // Return zero-length string if null
        }

        // If no tags, return the string we were passed.
        if ((tuText.trim().length() == 0) 
            || (tuText.indexOf("<") == -1)) {
            return tuText;
        }
        
        // We have a < character--a good sign of 1+ tags' presence
        String validText = "";   // What we'll eventually return.
        /*
         * Matcher to match/capture the following:
         * - Leading characters before a bx/ex/x (or other) tag
         * - The bx/ex/x (or other) tag
         * - The name of the tag (bx, ex or x)
         * - The trailing characters after the tag.
         */
        Matcher m = Pattern.compile("^(.*?)(<[^>/]*/?>)(.*)",Pattern.DOTALL).matcher("");

        String tail = tuText;
        boolean bogus = false;               // Not bogus ... yet.
        
        while ((tail != null) && (tail.length() > 0)) {  // While more tags
            m.reset(tail);
            if (m.find()) {
                String prefix = m.group(1);
                String tag = m.group(2);
                tail = m.group(3); // For next iteration

                if (prefix.length() > 0) {
                    // Make sure leading text (before first tag) has no characters
                    // that should be XML entities
                    validText += validateEntities(prefix);
                }

                // If the tag starts <bx, <ex, or <x add it to validText
                if (tag.matches("<[be]?x\\b.*")) {
                    validText += tag;
                }
                // Else don't add tag to validText
            }
            else {
                // No more matches. Put the tail on the end of the accumulated
                // valid text
                if (tail.length() > 0) {
                    validText += validateEntities(tail);
                    tail = "";
                }
            }
        }
        
        if (validText.equals(tuText)) {
            return tuText;
        }
        else {
            return validText;
        }
    }
        
//    /**
//     * Passed a string containing a sequence of adjacent TUs (from within
//     * a higher-level structure)--with each TU's core marked by lt:core marks--
//     * validate and repair each core section of each TU in the sequence. 
//     * Return the validated (and repaired--if necessary) TU.
//     * @param sequence The text of the TU sequence
//     * @return A validated/repaired (if necessary) sequence
//     */
//    public static String validateAndRepairSegmentSequence(String sequence) {
//        if (sequence == null) {
//            return "";          // Zero-length string if null
//        }
//        
//        // If no bx/ex or lt:core tags, return the input string
//        if ((sequence.trim().length() == 0)
//            || ((sequence.indexOf("<bx") == -1) && (sequence.indexOf("<ex") == -1))
//                && (sequence.indexOf("lt:core") == -1)) {
//            return sequence;
//        }
//        
//        // A matcher to extract cores from the sequence string:
//        Matcher coreM = Pattern.compile("^(.*?<lt:core>)(.*?)(</lt:core>.*)$", Pattern.DOTALL).matcher("");
//        
//        String validatedStr = "";
//        String tail = sequence;
//        
//        while ((tail != null) && (tail.length() > 0)) {
//            coreM.reset(tail);
//            if (coreM.find()) {
//                validatedStr += (coreM.group(1) + validateAndRepairTu(coreM.group(2)));
//                tail = coreM.group(3);
//            }
//            else {
//                validatedStr += tail;
//                break;
//            }
//            
//        }
//        
//        return validatedStr;
//    }
    
    /**
     * Passed the core text of a tu, verify that there is a one-to-one 
     * relationship between bx and ex tags (related by their rid's), and that 
     * they are properly nested.
     * <p>While validating, also repair the TU.
     * @param tuText The text of the TU
     * @return A validated/repaired (if necessary) text string.
     */
    public static String validateAndRepairTu(String tuText) {
        if (tuText == null) {
            return "";          // Return zero-length string if null
        }

        // If no bx/ex tags, return the string we were passed.
        if ((tuText.trim().length() == 0) 
            || ((tuText.indexOf("<bx") == -1) && (tuText.indexOf("<ex") == -1))){
            return tuText;
        }
        
        // We have a bx, ex and/or x tag. Check it/them out.
        String validText = "";   // What we'll eventually return.
        /*
         * Matcher to match/capture the following:
         * - Leading characters before a bx/ex/x tag
         * - The bx/ex/x tag
         * - The name of the tag (bx, ex or x)
         * - The trailing characters after the tag.
         */
//        Matcher m = Pattern.compile("^(.*?)(<([be]?x)\\b[^>/]*/?>)(.*)",Pattern.DOTALL).matcher("");
        Matcher m = Pattern.compile("^(.*?)(<([be]?x|/?mrk)\\b[^>/]*/?>)(.*)",Pattern.DOTALL).matcher("");

        String tail = tuText;
        Stack<BxExStackEntry> bxExStack = new Stack<BxExStackEntry>();
        boolean bogus = false;               // Not bogus ... yet.
        
        while ((tail != null) && (tail.length() > 0)) {  // While more tags
            m.reset(tail);
            if (m.find()) {
                String prefix = m.group(1);
                String tag = m.group(2);
                String tagName = m.group(3);
                tail = m.group(4); // For next iteration

                if (prefix.length() > 0) {
                    // Make sure leading text (before first tag) has no characters
                    // that should be XML entities
                    validText += validateEntities(prefix);
                }
                // WWhipple added 3/5/2007: Add code to make sure that this tag
                // has an id attribute (and that the bx/ex tag wasn't concocted
                // to match an ex/bx tag in the same sentence segment).
                // Note: This also removes mrk/end-mrk tags, which don't have
                // attributes.
                String id = idOf(tag);
                if (id == null || id.length() == 0) {
                    // If this bx/ex/x tag without an id attribute ...
                    // Just don't concatenate the tag to validText. ...
                    // ... Instead continue
                    continue;
                }

                String rid = ridOf(tag);
                
                // Now validate the tag
                if (tagName.equals("bx")) {
                    if ((rid == null) || (rid.length() == 0)) {
                        // We don't allow a bx without a rid
                        bogus = true;
                        break;
                    }
                    bxExStack.push(new BxExStackEntry(tagName,rid));
                    validText += tag;
                }
                else if (tagName.equals("ex")) {
                    if ((rid == null) || (rid.length() == 0)) {
                        // We don't allow an ex without a rid
                        bogus = true;
                        break;
                    }
                    if (bxExStack.empty()) {
                        // Signifies an ex without a matching bx; bogus
                        bogus = true;
                        break;
                    }
                    // The top of the stack should be the opening bx entry that
                    // matches this ex entry.
                    BxExStackEntry bxEntry = bxExStack.pop();
                    if (!bxEntry.getRid().equals(rid)) {
                        // If rid's don't match, this is bogus
                        bogus = true;
                        break;
                    }
                    validText += tag;
                }
                else if (tagName.equals("x")) {
                    // This is good. (This implementation supports x tags.)
                    validText += tag;
                }
                // 2/28/7 WWhipple mrk tags mark the core text
                else if (tagName.equals("mrk")) {
                    validText += tag;
                }
                else if (tagName.equals("/mrk")) {
                    // The end of core text
                    validText += tag;
                }
                else {
                    // No other tags are allowed--bogosity!
                    bogus = true;
                    break;
                }
            }
            else {
                // No more matches. Put the tail on the end of the accumulated
                // valid text
                if (tail.length() > 0) {
                    validText += validateEntities(tail);
                    tail = "";
                }
            }
        }
        
        // If the bx/ex stack isn't empty, there are more bxes than exes'
        if (!bxExStack.empty()) {
            bogus = true;
        }
        
        // We're done. If the core TU's formatting is bogus, strip *all* tags
        if (bogus) {
            
            // We have detected something bogus. We might have some partially
            // valid text (validText) with some remaining t[r]ail[ing] text (tail)
            // and who knows what in the middle. If there is some tail text,
            // append it to valid text ...
            if ((tail != null) && (tail.length() > 0)) {
                validText += tail;
            }
            
//            // Delete anything delimited with < and >
//            Pattern anyTagP = Pattern.compile("(<[^>]*>)",Pattern.DOTALL);
//            // Delete anything delimited with < and > -- except x tags
//            Pattern anyTagP = Pattern.compile("<[^x]>|<[^x]\\s[^>]*>|<[^x][^>]*>",Pattern.DOTALL);

            // Delete all bx and ex tags
            Pattern bxexP = Pattern.compile("<[be]x[^>]*>",Pattern.DOTALL);

            // Strip the bx/ex tags
            validText = bxexP.matcher(validText).replaceAll("");
            
            // Check for any tags other than <x ... (including mrk and end mark)
            if (validText.matches("(?s).*</?[^x].*")) {
                // Found one/some--delete all tags. (We've got big problems).
                Pattern anyTagP = Pattern.compile("(<[^>]*>)",Pattern.DOTALL);
                validText = anyTagP.matcher(validText).replaceAll("");
            }
            
            System.err.println("TuPreener.validateAndRepairTu: Invalid XLIFF "
                + "detected in the following target (removed many of the tags):");
            System.err.println(tuText + "\n");
            
            return validText; // That we changed
        }

        if (validText.equals(tuText)) {
            return tuText;
        }
        else {
            return validText;
        }
    }

    /**
     * Passed the text of a bx or ex or x element, return its id 
     * attribute's value
     * @param bex The bx or ex or x 
     * @return The id attribute's value
     */
    private static String idOf(String bex) {
        if ((bex == null) || (bex.trim().length() == 0)) {
            return "";
        }
        
        Matcher idM = Pattern.compile("\\bid=(['\"])(.*?)\\1").matcher(bex);
        if (idM.find()) {
            return idM.group(2);
        }
        else {
            return "";
        }
    }
    
    /**
     * Passed the text of an empty bx or ex element, return its rid 
     * attribute's value
     * @param bex The bx or ex empty element
     * @return The rid attribute's value
     */
    private static String ridOf(String bex) {
        if ((bex == null) || (bex.trim().length() == 0)) {
            return "";
        }
        
        Matcher ridM = Pattern.compile("\\brid=(['\"])(.*?)\\1").matcher(bex);
        if (ridM.find()) {
            return ridM.group(2);
        }
        else {
            return "";
        }
    }
    
    /**
     * Verify that singleton ampersands use the amp entity, and that otherwise
     * only the lt, gt, apos and quot entities are used. Also verify that
     * single characters that should be entities *are* entities.
     * @param textStr String to verify
     * @return String in which all ampersands are valid
     */
    private static String validateEntities(String textStr) {
        if (textStr == null) {
            return "";
        }
        if ((textStr.trim().length() == 0) 
            || (textStr.indexOf("&") == -1)){
            return textStr;
        }
        
        // We have at least one ampersand; validate it.
        int curIndex = 0;
        while ((curIndex = textStr.indexOf('&', curIndex)) > -1) {
            if (!(textStr.startsWith("&amp;",curIndex)
                || textStr.startsWith("&lt;",curIndex)
                || textStr.startsWith("&gt;",curIndex)
                || textStr.startsWith("&apos;",curIndex)
                || textStr.startsWith("&quot;",curIndex))) {
                // Invalid entity! Replace & with &amp;
                if (textStr.length() > (curIndex+1)) {
                    // & isn't at the very end of the string:
                    textStr = textStr.substring(0,curIndex) + "&amp;" 
                        + textStr.substring(curIndex+1);
                }
                else {
                    // & *is* at the very end of the string:
                    textStr = textStr.substring(0,curIndex) + "&amp;"; 
                    break;   // No more string
                }
            }
            // Else & is first char of the big 5 entities.
            
            curIndex++;      // Do this in either case.
        }
        
        // Now make sure that we use entities where needed (for the
        // other 4 XML entities)
        if (textStr.contains("'"))  { textStr = textStr.replace("'", "&apos;"); }
        if (textStr.contains("\"")) { textStr = textStr.replace("\"", "&quot;"); }
        if (textStr.contains("<"))  { textStr = textStr.replace("<", "&lt;"); }
        if (textStr.contains(">"))  { textStr = textStr.replace(">", "&gt;"); }

        return textStr;      // All the &'s should be OK in this one.
    }
    
}
