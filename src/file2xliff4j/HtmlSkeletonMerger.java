/*
 * HtmlSkeletonMerger.java
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

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/**
 * This class merges the following:
 * <ol>
 * <li>The pseudoSkeleton stream generated as XLIFF is created
 *     from XML.</li>
 * <li>The original HTML stream from which the XLIFF was created.
 * </ul>
 * It outputs a final Skeleton file that can be used to generate
 * HTML from XLIFF.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class HtmlSkeletonMerger /* extends Thread */ implements SkeletonMerger {
    
    private final static int BLKSIZE = 8192;    // Size of input blocks


    private StringBuilder skelBuf;              // Starts with copy of HTML document
    private Stack<Integer> skelPosStack = new Stack<Integer>();
    private int curTagSeq = 0;
    private int prevTagSeq = 0;
    private boolean noMoreComments = false;    // Any more comments in the file?
    
    // We use the following when we encounter a series of adjacent tags, all
    // with alt attributes. (For example, applet, img and embed tags in
    // succession. ... or multiple img tags in succession.) These alt tags can
    // appear either within TUs or outside of TUs.
    private int lastAltEndTagOffset = -1;   // Use this in case we need to seek
                                            // from *after* an img tag (for example)
    
    // Each time we encounter a line that begins "<attr name='alt" and the
    // previous non-tu line also began "<attr name='alt", we increment the
    // following. Then, when we encounter a tu line in the tskeleton that
    // isn't preceded by <attr, we pop the count of numConsecutiveAltAttrs of
    // the skelPosStack and reset numConsecutiveAltAttrs to 0.
    // (Note: When this is non-zero, it will actually be one less than the total
    // number of consecutire <attr lines in a "run"--if there are three <attr/<tu
    // pairs in succession, after the third pair, the value of numConsecutiveAltAttrs
    // will be 2.)
    private int numConsecutiveAltAttrs = 0; 
    
    private HashSet<String> altTags = new HashSet<String>();
    
    // This holds the set of tags that can cause TU breaks. (These are the
    // ones that can occur in the tskeleton file.)
    private HashSet<String> breakTags = new HashSet<String>();
    
    // Matcher for <tu ... lines in tskeleton
    Matcher tuMatcher = Pattern.compile("^<tu id='([^\']+)' length='([^\']+)'").matcher("");
    
    // Matcher for <attr ... lines in tskeleton
    Matcher attrMatcher = Pattern.compile("^<attr name='([^']+)'(?: tag='(.+)')?>$").matcher("");
    
    // Matcher for other tags in tskeleton (for seeking/deleting to)
    Matcher tagMatcher = Pattern.compile("^([^ ]+) seq='(\\d+)'>").matcher("");

    // This matcher looks for possible opening tags in a text string
//    Matcher breakTagMatcher = Pattern.compile("<([^/].*?)(\\b.*)$",Pattern.DOTALL).matcher("");
    Matcher breakTagMatcher = Pattern.compile("<(\\w+)(.+)$",Pattern.DOTALL).matcher("");
        
    // This matcher matches complete strings consisting exclusively of whitespace.
    Matcher spaceMatcher = Pattern.compile("^(?:&nbsp;|" + TuPreener.SECONDARY_WHITE_SPACE_CLASS
          + "|" + TuPreener.WHITE_SPACE_CLASS + ")*$",Pattern.DOTALL).matcher("");
    
    // Does this HTML file have any (actual) </br> tags?
    private boolean hasEndBr = false;     // Assume not
    
    /**
     * Constructor for the skeleton merger object
     */
    public HtmlSkeletonMerger()  { 
        skelPosStack.push(Integer.valueOf(-1));   // Start looking at position 0 in the html stream
//        // Ticket #1036: Initialize both elements of stack to 0 (instead of having -1 in the
//        // bottom of the stack).
//        skelPosStack.push(Integer.valueOf(0));    // Start looking at position 0 in the html stream
        skelPosStack.push(Integer.valueOf(0));    // Initialize the stack.
    }
    
    /**
     * Create a skeleton file from the pseudo skeleton and the original html.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param htmlInStream The original HTML to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the html
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream htmlInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException {
        this.merge(tSkelInStream, htmlInStream, skeletonOutStream, encoding, 1);
    }

        /**
     * Create a skeleton file from the pseudo skeleton and the original html.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param htmlInStream The original HTML to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the html
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs. (Not used in HTML format)
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream htmlInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException {
        
        if (tSkelInStream == null || htmlInStream == null
                || skeletonOutStream == null || encoding == null) {
            throw (new IllegalArgumentException());
        }

        BufferedReader tSkel = new BufferedReader(new InputStreamReader(tSkelInStream, Charset.forName("UTF-8")));
        BufferedReader html =  new BufferedReader(new InputStreamReader(htmlInStream, encoding));
        BufferedWriter skel  = new BufferedWriter(new OutputStreamWriter(skeletonOutStream, Charset.forName("UTF-8")));
        
        // Read the HTML stream into a buffer for easy manipulation
        skelBuf = new StringBuilder();
        char[] buf = new char[BLKSIZE];
        int i;
        while ((i = html.read(buf)) > 0) {
            skelBuf.append(buf,0,i);
        }
        
        // See if there are any end br tags (actually, physically in the file)
        int endBrPos = skelBuf.indexOf("</br");
        if (endBrPos > -1) {
            hasEndBr = true;
        }
        
        ArrayList<String> segIds = new ArrayList<String>();  // IDs of sequence of adjacent segments
        int paraLen = 0;                      // Accumulated length of adjacent segments
        int totSegs = -1;                     // Total adjacent segments in this run of TUs
        int curSeg = -1;                      // Number of this segment (in run of adjacent TUs)
        String tuOrFormat = "";               // Is the current tag a tu or format entry?
        boolean inSegmentRun = false;         // Not currently in a multi-segment run of TUs
        
        // Matcher to capture information from a tu line.
        Matcher tuMatcher = Pattern.compile("^<(tu|format) id=(['\"])([-\\w]+)\\2\\s+length=(['\"])(\\d+)\\4"
                + "\\s+no=(['\"])(\\d+)\\6\\s+of=(['\"])(\\d+)\\8>").matcher("");
        // Delete all open/closing tags that map to bx/ex tags from the skelBuf
        
        String curSkelLine = "";            // The temp skel line read in this iteration
        String prevSkelLine = "";             // The line read last time
        int curTagSeq = 0;                    // Current tag sequence number
        int curTuId = 0;                      // Which Tu Number
        
        boolean done = false;     // Not done yet
        int     attrSeekStatus = 0; // -1: seek/delete was backwards in stream
                                    //  0: seek/delete failed
                                    // +1: seek/delete was forward (later) in stream
        boolean tuOK = false;     // TU insertion not OK (yet)
        boolean lastTuWasAttr = false;  // Did we just insert a TU in an attribute's value?
        
        // 2/21/7 wwhipple: We Need to handle a case something like the following,
        // where three img tags in a row are not mapped to x tags. Before today's
        // fix, skeleton merger does the following:
        // 1. Seek left to the previous tag for an alt attribute.
        // 2. Not finding it, seek right, finding the alt attribute of an img
        //    tag, and substituting a placeholder for the alt attribute's value.
        // 3. Then seek left to the beginning of the (first) img tag.
        // <attr name='alt' tag='img'>
        // <tu id='90019f43-33a8-4b88-a455-db44a0abfdec' length='12' no='1' of='1'>
        //
        // 4. When it immediately thereafter encounters the following two lines
        //    skeleton merger removes the previously inserted placeholder and
        //    replaces it with the next placeholder (rather than moving right
        //    and replacing the alt attribute of the second img tag.
        // <attr name='alt' tag='img'>
        // <tu id='088bd56b-78aa-42e6-bf2e-e89e3e10e656' length='12' no='1' of='1'>
        // 
        // 5. Ditto
        // <attr name='alt' tag='img'>
        // <tu id='93e995a9-07aa-4344-a6a3-b89ccbb88975' length='12' no='1' of='1'>
        //
        // 6. Afterward finding a p tag (after a tu tag), we would delete all the
        //    image tags in the skeleton. (Not a good idea).
        //
        // We need to fix the above!
        altTags.add("img"); 
        altTags.add("applet"); 
        altTags.add("embed");
       
        String prevNonTuLine = "";   // What was the previous non-TU line (was it
                                     // an attr?)
        boolean prevWasPhantomTag = false;
        boolean justSkippedEndBr = false;
        
        // The outer loop is driven by the temporary skeleton file, which includes (one per line)
        while (! done) {
            if (!justSkippedEndBr) {   // If we just skipped </br>, change no state vars ...
                if (!inSegmentRun && !prevWasPhantomTag) {
                    prevSkelLine = curSkelLine;       // Remember what we read last time
                    paraLen = 0;                      // Zero out accumulated length of adjacent segments
                }
                prevWasPhantomTag = false;
            }
            justSkippedEndBr = false;

            // Note: If we *are* in the middle of reading a multi-segment run,
            // maintain prevSkelLine that occurred before the first segment of the
            // multi-segment run.
            curSkelLine = tSkel.readLine();
            if (curSkelLine == null) {
                break;                        // Break out of this while loop.
            }
            // If file has no actual </br> tags, don't waste time looking
            else if ((!hasEndBr) && curSkelLine.toLowerCase().startsWith("</br ")) {
                justSkippedEndBr = true;
                continue;                     // Move on to next line
            }
            
            tuMatcher.reset(curSkelLine);     // Prepare to check the latest line
            if (tuMatcher.find()) {           // Is this a tu or format line?
                
                // Yes! Capture information from the line.
                tuOrFormat = tuMatcher.group(1);          // tu or format? 

                // Add the tu/format ID to the list of IDs in this
                // run of adjacent segments.
                // Each entry is something like either of the following:
                // format:203
                // tu:b2a0a465-263a-4106-ade8-a3f466bb624f
                //
                segIds.add(tuOrFormat + ":" + tuMatcher.group(3));  
                paraLen += Integer.parseInt(tuMatcher.group(5)); 
                curSeg = Integer.parseInt(tuMatcher.group(7));
                totSegs = Integer.parseInt(tuMatcher.group(9));
                
                if (totSegs > 1) {
                    if (curSeg < totSegs) {
                        inSegmentRun = true; // Note that we're in a multi-segment run
                        continue;            // Then read the next line (segment)
                    }
                    else {
                        // We've just reached the last segment of a multi-segment run
                    }
                }
                inSegmentRun = false;  // Resume copying curline to prevline.
                
                // Substitute <trans-unit lt:tu-id /> in new skeleton
                // If prevSkelLine is an <attr tag, tell the TU substituter (when
                // it is finished substituting the attribute value with the TU tag)
                // to "seek" to one past the ">" that closes the tag of which the
                // attribute is an attribute.
                if (prevSkelLine.startsWith("<attr") && (attrSeekStatus != 0)) {
                    // This TU goes in an attribute ... and the seek to the attr's
                    // value was successful. Proceed.
                    
                    // Here is what we expect insertTu to do:
                    // If attrSeekStatus is -1, then (after inserting the TU) seek
                    // to the end of the tag (+1) that contains the attribute.
                    //
                    // If attrSeekStatus is +1, we *might* have sought to an
                    // attribute that is part of a tag that will be replaced by
                    // a bx/ex/x tag (rendering our effors in vain). In order to
                    // allow that to happen, just pop the stack, leaving us positioned
                    // at the beginning of the tag that contained the attribute.
                    tuOK = insertTu(curSkelLine, attrSeekStatus, segIds); 
                    lastTuWasAttr = true;
                }
                else {
                    // Each time we have encountered a second (third, etc.)
                    // alt attr in succession, we have moved too far to the
                    // right of where we need to insert a non-attribute TU
                    // place holder. Each time we encountered a second (third, etc.)
                    // tag with an alt attribute, we have left addresses that we
                    // need to clean off the stack.
                    if (prevSkelLine.startsWith("<tu id")) {
                        // It *could* have been a "<tu id" line associated with
                        // an <attr line. If so, then the tags with attrs 
                        // will be bx/ex/x tags in this TU.
                        // If, OTOH, some other tag (neither <attr nor <tu ...)
                        // preceded this entry, then we have a new "base" at the
                        // top of the stack, from which to seek.
                        
                        // If prev was a tu, give an opportunity to pop the stack
                        // (if two+ attr's appeared in succession)
                        for (int j = 0; j < numConsecutiveAltAttrs; j++) {
                            skelPosStack.pop();
                        }
                    }
                    // else leave the stack intact. The stack's top offset is a
                    // good reference point.
                    numConsecutiveAltAttrs = 0;
                    tuOK = insertTu(curSkelLine, 0, segIds); // 0 means not in an attribute, I s'pose
                    lastTuWasAttr = false;
                }
                segIds.clear();     // Start over.
            }
            else if (curSkelLine.startsWith("<attr name=")) {  // An attribute with a tu in it
                // Find attribute (part of tag in prevSkelLine) and substitute
                // <lt:tu id='<id>'/> in its place
                // This actually finds the attribute value, deletes everything
                // in between the quotes, and sets up for the following <tu ...>
                // tag
                
                String searchDirection = "";
                if (prevNonTuLine.startsWith("<attr name='alt'") 
                    && curSkelLine.startsWith("<attr name='alt'")) {
                    // If 2+ img/applet/imbed tags (which have alt attrs)
                    // occur in succession, start looking at the beginning of
                    // the following tag, and start searching to the *right*
                    this.skelPosStack.push(lastAltEndTagOffset);
                    searchDirection = "right";
                    numConsecutiveAltAttrs++;
                }
                
                attrSeekStatus = seekToAttr(curSkelLine, searchDirection); // Returns -1, 0 or +1
                prevNonTuLine = curSkelLine;
                searchDirection = "";
            }
            else if (curSkelLine.startsWith("<")) {  // Some html open or close tag
                if ((prevSkelLine.startsWith("<tu id=") || prevSkelLine.startsWith("<format id=")) 
                    && !lastTuWasAttr) {
                    // Delete up to the beginning of the tag represented in curSkelLine
                    prevWasPhantomTag = deleteToTag(curSkelLine);  
                }
                else { // We didn't just insert a TU (or it was in an attribute val), so
                       // we can just keep walking the HTML.
                    prevWasPhantomTag = seekToTag(curSkelLine, prevSkelLine);
                }
                prevNonTuLine = curSkelLine;
            }
        }
        
        // Write the skeleton buffer to the skel stream
        for (int j = 0; j < skelBuf.length(); j++) {
            skel.write(skelBuf.charAt(j));
        }
        
        skel.flush();
        skel.close();
        
    }
    
    /**
     * Set a format-specific property that might affect the way that the
     * merger process is conducted.
     * @param property The name of the property
     * @param value The value of the property
     * @throws file2xliff4j.ConversionException
     *         If the property or value can't be recognized.
     */
    public void setProperty(String property, Object value)
            throws ConversionException {
        if (property.equals("http://www.lingotek.com/converters/properties/breaktags")) {
            if (value instanceof String[]) {
                String[] tagList = (String[])value;
                if (tagList == null || tagList.length == 0) {
                    return;
                }
                else {
                    // Set the breakTags HashSet to contain the tags that can cause a break.
                    Collections.addAll(breakTags, tagList);
                    return;
                }
            }
        }
        
        throw new ConversionException("Unrecognized property " + property);
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the "current" position) in the skeleton Buffer (represented
     * by what is on the top of the skelPosStack).
     * Then, delete everything between the position (on the top of the stack) and
     * the beginning of the specified tag string.
     *
     * Upon exit, the skelPosStack's top should be at the position one space to the
     * right of the close (">") of the tag passed in as the tagString argument.
     * Note: This can be either a beginning or ending tag.
     * @param tagString The tag string read from the intermediate skeleton stream
     * @return A boolean value indicating if a "phantom" end tag prevented us
     *         from deleting to an end tag (that didn't exist in the file)
     */
    private boolean deleteToTag(String tagString) {
//        boolean isPhantomEndTag = false;
        
        // Remember where we'll be deleting from
        int deleteStartPos = skelPosStack.peek().intValue();
        int tagStartPos = -1;                // Record where the tag starts
        int tagEndPos = -1;                  //   and ends.
        int stackSize = skelPosStack.size(); // Remember the height of the stack
        
        // Remember our exact state in case we encounter a "phantom" end tag
        // (reported by NekoHTML but not actually in the original file).
        // Note: When you have time, forget Object's clone() method and use
        // (instead) a class that has a copy constructor. (Stack doesn't seem to
        // have one.
        Object saveStack = null;
//        Stack<Integer> saveStack = null;
        
        // Skip over HTML comments-- if there are any more.
        int commentStart = -1;
        if (!noMoreComments) {
            // Find where next comment begins (so that we can compare it to the position
            // of the next tag and skip over the comments if necesary.
            commentStart = skelBuf.indexOf("<!--",skelPosStack.peek().intValue());
            if (commentStart == -1) {
                noMoreComments = true;
            }
        }
        
        // Find more about this tag from the intermediate skeleton file
        String tagPrefix = null;
        // When I wrote this comment, the tagMatcher looked something like:
        // "^([^ ]+) seq='(\\d+)'>"
        tagMatcher.reset(tagString);
        if (tagMatcher.find()) {
            tagPrefix = tagMatcher.group(1);
            curTagSeq = Integer.parseInt(tagMatcher.group(2));
        }

        // Now find the next occurrence of tag in the skeleton buffer--an occurrence
        // that isn't inside a comment
        boolean foundTag = false;
        
        // Special handling for end tags--which might not actually exist in the
        // original file (but could have been supplied by NekoHTML when it encountered
        // another opening tag that implied the closing of the tag).
        boolean isEndTag = false;
        if (tagPrefix.charAt(1) == '/') {
            isEndTag = true;
            saveStack = skelPosStack.clone();
            // Let's use Stack's copy ctor sometime in the future.
//            saveStack = new Stack<Integer>(skelPosStack);
        }
        
        while (! foundTag) {
            int pos = skelBuf.indexOf(tagPrefix, skelPosStack.peek().intValue());
            if (pos > -1) {   // We found the tag
               
                tagStartPos = pos;     // Where the tag starts
                // Find the tag end:
                int endOfTag = skelBuf.indexOf(">", pos + tagPrefix.length());
                if (endOfTag > -1) {
                    /* 1/4/7 Ticket 687. Fix absurd condition where HTML page had
                     * nested (!!) <form> tags, and neko dutifully closed the
                     * first one before opening the nested one. I don't know if
                     * I should do this, but I will. (It is kind of bogus, IMHO,
                     * to have nested form tags!!) */
                    if (tagPrefix.equalsIgnoreCase("</form")) {
                        // Check for another opening <form> tag between
                        // the position at the top of the skelPosStack and the
                        // place where this end form tag was found. If an
                        // opening form tag is found, bail. (Don't delete
                        // anything!)
                        String formCatcher 
                            = new String(skelBuf.substring(skelPosStack.peek().intValue(),
                                tagStartPos));
                        Matcher fm = Pattern.compile("<form\\b",Pattern.CASE_INSENSITIVE).matcher(formCatcher);
                        if (fm.find()) {
                            System.err.println("HtmlSkeletonMerger encountered nested form tags. Ignoring ...");
                            return false;   // Not a phantom end tag ???
                        }
                    }
                    
                    // Add one to the position, so that it will be one position
                    // to the right of the end of the tag.
                    skelPosStack.push(Integer.valueOf(endOfTag + 1));
                    tagEndPos = endOfTag + 1;
                }
                else {
                    return true;    // *Is* a phantom end tag. (Not so bad, I guess.)
                }
            }
            else {  // Hmmm... We didn't find the tag
                    // Don't push it onto the stack
                    // 3/26/2007: Handle this sequence (and similar ones):
                    // <p>A bunch of text.<p>Another bunch of text.</body>
                    // 
                    // Neko returns the following:
                    // <p>A bunch of text.</p><p>Another bunch of text.</p></body>
                    //
                    // ... but the two "</p>" tags aren't found, and we end up
                    // in this "else". Indicate that this is a phanton end tag.
                return true;     // This *is* a phantom end tag.
            }
            
            // OK, so we found the tag. Before returning, make sure that it is
            // not inside an HTML comment. (It it is, we will need to find the
            // end of the comment and look again for the tag)
            int endComment = Integer.MAX_VALUE;
            // Note: We found the *first* comment Start value at the top of this
            // method.
            if ((!noMoreComments) && (commentStart < pos)) {
                // Skip comment(s) that start before the index ("pos") of the tag
                // that we found in the skeleton buffer
                endComment = skipComment(commentStart,pos);
            
                if (endComment <= skelPosStack.peek().intValue()) {
                    skelPosStack.push(Integer.valueOf(pos)); // If we found the position of the tag, remember it
                    foundTag = true;    // Quit looking
                }
                else { // The comment ended after the tag position. We need to look again.
                
                    // Advance past the comment, so we can look more for the tag
                    skelPosStack.push(Integer.valueOf(endComment));

                    // Find where the next comment starts
                    commentStart = skelBuf.indexOf("<!--",skelPosStack.peek().intValue());
                    if (commentStart == -1) {
                        noMoreComments = true;
                    }                
                }
            }
            else { // Either no more comments or they all start after the tag whose end we
                   // found. The tag we found is good.
                foundTag = true;
            }
        }

        // 2/22/7: wwhipple:
        // If we were asked to delete text up to an end tag, make certain that 
        // no intervening tu-breaking start tag occurs. If it does, this end tag
        // is probably a "phantom" tag reported by NekoHtml. (In that case, 
        // pretend this method wasn't called and return. (We will be called later
        // with the intervening break tag.
        if (isEndTag) {
            String charRun = skelBuf.substring(deleteStartPos, tagStartPos).toString();
            if (hasInterveningBreakTag(charRun)) {
                // Restore the stack to match what it was upon entering this
                // method.
                skelPosStack = (Stack<Integer>)saveStack;
                return true;    // This *is* a phantom end tag.
            }
        }
                
        
        
        // Now delete everything we were asked to delete
        skelBuf.delete(deleteStartPos, tagStartPos);
        // Decrement the tag end position by the number of characters we deleted
        tagEndPos-= (tagStartPos-deleteStartPos);
        
        // Now place the (new) tagEndPos on the top of the stack. (Let the stack be
        // the same size it was when we entered this method)
        while (skelPosStack.size() > (stackSize + numConsecutiveAltAttrs)) {
            // Delete from the bottom of the stack
            skelPosStack.remove(0);                 // Prune the stack  
        }
        
        if (skelPosStack.peek().intValue() > tagEndPos) {
            // Replace the top fo the stack if it is bigger than the tagEndPos
            skelPosStack.setElementAt(Integer.valueOf(tagEndPos), skelPosStack.size()-1);
        }
        else {
            // Otherwise push the stack.
            skelPosStack.push(Integer.valueOf(tagEndPos));
        }
        return false;     // This *isn't* a phantom end tag
    }

    /**
     * Find the next occurrence of the specified HTML end tag in the skelBuf--
     * ignoring case.
     * <p><i>Note:</i> The Neko parser is configured (by setting the property
     * "http://cyberneko.org/html/properties/names/elems" to "match") to have
     * the startElement method pass the start element using the exact case of
     * the original as it appears in the HTML file. That same setting makes the
     * matching endElement be passed using the same case--even when it doesn't
     * reflect what is in the actual file. This is a problem if a start tag
     * such as &lt;script&gt; is closed (in the HTML file) as &lt;/SCRIPT&gt;.
     * This method thus does a case-insensitive search for the specified end
     * tag in the skelBuf.
     * @param tagName The name of the end tag to look for
     * @param startOffset The offset to start looking from
     * @return the offset of the tag, or -1 if not found
     */
    private int findEndTag(String tagName, int startOffset) {
        if ("".equals(tagName) || (startOffset >= this.skelBuf.length())
                || startOffset < 0) {
            return -1;
        }
        
        // Virtual else:
        boolean done = false;
        int curStartOffset = startOffset;
        while (curStartOffset < skelBuf.length()) {
            int startTag = skelBuf.indexOf("</", curStartOffset);
            if (startTag == -1) {
                // We're outta end tags!
                return -1;       // Can't find end tag
            }
            
            // Virtual else
            // Look for the > that closes the end tag
            int tagEnd = skelBuf.indexOf(">", startTag+2);
            if (tagEnd == -1) {
                // Reached end of file.
                return -1;       // Can't find a complete end tag.
            }
            
            // Virtual else
            // Let's see if this end tag matches:
            String candidateTag = new String(skelBuf.substring(startTag+2, tagEnd));
            if (candidateTag.toLowerCase().startsWith(tagName.toLowerCase())) {
                return startTag;
            }
            
            // Virtual else
            curStartOffset = tagEnd;
        }
        
        return -1;  // Just in case we get this far.
    }
    
    /** 
     * Passed a string of characters that will likely be deleted, check to
     * see if the string contains any opening tags that signal a TU break.
     * If such a tag is present, report it.
     * @param charRun A sequence of characters to check for a breaking tag.
     * @return true if a breaking tag is present, else false
     */
    private boolean hasInterveningBreakTag(String charRun) {
        if (charRun == null || charRun.trim().length() == 0) {
            return false;
        }
        
        // WLW 7/12/2007. Strip all comments from the charRun
        // rather than calling the bug-riddled inComment() method.
        if (charRun.contains("<!--")) {
            Matcher cmtMatcher = Pattern.compile("<!--.*?-->",Pattern.DOTALL).matcher("");
            
            charRun = cmtMatcher.reset(charRun).replaceAll("");
        }
        
        // Look for opening breaking tags. 
        String tail =  charRun;
        while (tail.length() > 0) {
            if (breakTagMatcher.reset(tail).find()) {
//            if (breakTagMatcher.find()) {
                String tag = breakTagMatcher.group(1);
                // The interval where the tag begins
                int tagStart = breakTagMatcher.start(1) - 1;
                int tagNameEnd = breakTagMatcher.end(1);
                tail = breakTagMatcher.group(2);
                if (breakTags.contains(tag.toLowerCase())) {
//                    && (!inComment(tagStart, tagNameEnd, charRun))) {
                    return true;     // We found a breaking tag!!
                }
            }
            else {
                break;
            }
        }
        
        // If we made it this far, then there are no intervening break tags
        return false;
    }
    
//    /**
//     * Passed a begin and end+1 offset of a sequence of characters, together with
//     * a superstring that contains a substring within those offsets, determine
//     * if the characters at that subrange are within an HTML comment.
//     * @param begin The index position (in charRun) where the substring to be
//     *        checked begins.
//     * @param afterEnd One past the position (in charRun) where the substring to
//     *        be checked ends.
//     * @return true if the subsequence is within an HTML comment, else false.
//     */
//    private boolean inComment(int begin, int afterEnd, String charRun) {
//        if (begin < "<!--".length() || begin > charRun.length()
//            || afterEnd < 0 || afterEnd < begin
//            || "".equals(charRun)) {
//            return false;             // Bogus values. Not in a comment
//        }
//        if (!charRun.contains("<!--")) {
//            return false;         // No opening comment--can't be in a comment!
//        }
//        
//        // Implied else:
//        int startAt = 0;
//        while (startAt < charRun.length()) {
//            int commentStart = charRun.indexOf("<!--", startAt);
//            if (commentStart == -1) {
//                return false;
//            }
//            // Implied else
//            int commentEnd = charRun.indexOf("-->", commentStart + 4);
//            if (commentEnd == -1) {
//                // Hmmm... Unclosed comment (bad)
////                System.err.println("HtmlSkeletonMerger.inComment: Unclosed "
////                    + "comment around string " + charRun.substring(begin,afterEnd));
//                if (begin >= commentStart+4) {
//                    return true;    // Within an open comment (??)
//                }
//                else {
//                    return false;   
//                }
//            }
//            
//            // Implied else
//            // We have the index of a begin and end comment. See if the subsequence
//            // is within that range:
//            if ((begin > commentStart+3) && afterEnd < commentEnd) {
//                return true;
//            }
//            startAt = commentEnd+2;
//        }
//        return false;    // For good measure.
//    }
    
    /**
     * Passed a tu tag string (from the intermediate skel file), insert a
     * tu place holder of the format <lt:tu id='[id]'/> in the current
     * skel position. If this is an attribute value, seek to the closing ">"
     * of the tag of which this value's attribute is a member. Then seek one
     * more position to the right (just past the tag's close)
     * @param tuTagString The line from the intermediate skeleton file that
     *        represents the TU holder we are to insert
     * @param attrSeekIndicator Has the following possible values:
     * <ul>
     * <li>-1 We are inserting a TU placeholder tag in the tag that
     *        we had to "backtrack" (in the skeleton buffer) to find.
     *        Since we backtracked, after inserting the placeholder tag,
     *        seek to the end of the tag that holds the attribute and *replace*
     *        the top of the stack with that location.
     * <li> 0 We are inserting a TU placeholder in the main-stream text (a
     *        "text element" somewhere--not as part of an attr value.)
     * <li>+1 We are inserting a TU placeholder tag in the tag that
     *        we had to "look ahead" (in the skeleton buffer) to find. Because
     *        the HTML tag that contains the attribute that we are inserting a
     *        placeholder for *could* be part of a TU (say an img, applet, etc.
     *        tag inside the flow of text), it is actually possible that we will
     *        delete the img (etc) tag and include it in a bx/ex/x tag in the
     *        format file (deleting this entire tag). 
     *        <br>Therefore [longwinded!], after inserting the placeholder tag,
     *        pop the stack, leaving on top the position of the beginning of
     *        the tag that included this attribute.
     * <li>
     * </ul>
     * @param segIds An array of adjacent segments to represent in the
     *        skeleton
     * @return The status of the operation: true=succeeded, false=failed
     */
    private boolean insertTu(String tuTagString, int attrSeekIndicator,
            ArrayList<String> segIds) {
        String tuID = "";               // What is this TU ID?
        String tuLength = "";           // How long is the text of the TU?
        String placeHolder = "";        // Placeholder for Skeleton file
        
        // If we have no reason to suspect that we are creating a placeholder
        // for multiple adjacent segments, the placeholder will reference a
        // single TU
        if ((segIds == null || segIds.size() <= 1)) {
            // Find more about this TU tag from the intermediate skeleton file
            tuMatcher.reset(tuTagString);
            if (tuMatcher.find()) {
                tuID = tuMatcher.group(1);
                tuLength = tuMatcher.group(2);
            }
            else {
                // I guess we'll have to skip this TU; we can't figure where
                // to insert it (sigh)
                // Pop the stack first (repositioning us at the start of this tag.)
                skelPosStack.pop();
                return false;
            }

            placeHolder = "<lt:tu id='" + tuID + "'/>";
        }
        else {
            // The segIds array list has multiple format/tu references. Include
            // all of them in the placeholder (so that, at export time, we can
            // concatenate all of them and validate that they are meaningful,
            // etc.--as a paragraph.
            placeHolder = "<lt:tu ids='";
            
            // Add all the format:<id> or tu:<id> elements  ...
            for (int i = 0; i < segIds.size(); i++) {
                placeHolder += (segIds.get(i) + " ");
            }
            placeHolder += "'/>";
        }

        // Insert a TU placeholder tag into the skeleton
        skelBuf.insert(skelPosStack.peek().intValue(), placeHolder);
        
        // Adjust the stack as necessary.
        
        // If this was an attribute value and we sought backwards to find it, 
        // then find the next ">" (which closes the tag that encloses this 
        // attribute) and push that position+1 onto the stack (where we'll 
        // resume processing next).
        if (attrSeekIndicator == -1) {
            // Add placeHolder length to curPos below, so that the seek to ">"
            // doesn't find the closing ">" of the placeholder
            int curPos = skelPosStack.pop().intValue() + placeHolder.length();
            curPos = skelBuf.indexOf(">", curPos);
        
            if (curPos == -1) {           // Failed to find ">" !!
                return false;             // 
            }
            else {  // Place "pointer" at character after the end of this tag
                skelPosStack.push(Integer.valueOf(curPos + 1));
            }
        }
        
        // If we had to look ahead to find the attribute, position us at the
        // beginning of the tag that contained the attribute. Fortunately,
        // that location is the number below the top of the stack. ... So
        // just pop the stack and return.
        else if (attrSeekIndicator == +1) {
            // 2/22/7 WLW: Yes, we pop the stack, but let's remember where
            // the tag that contained the attribute ends ... +1
            lastAltEndTagOffset = skelPosStack.pop().intValue() + placeHolder.length();
            lastAltEndTagOffset = skelBuf.indexOf(">", lastAltEndTagOffset) + 1;
        }
        
        else {  // This TU is in a text node somewhere
            // Move the pointer past the placeholder tag we just inserted
            
            // Note: In the case of text that isn't in an attribute's value
            // somewhere, we just inserted the tag before the already existing
            // text. When we read the next tag from the temporary skeleton,
            // we will delete everything from the end of the placeholder
            // up to the tag that comes next in the inputstream.
            int newPos = skelPosStack.pop().intValue() + placeHolder.length();
            skelPosStack.push(Integer.valueOf(newPos));
        }
        return true;
    }
    
    // The following matcher matches a placeholder at the very end of a 
    // character sequence:
    private Matcher placeHolderMatcher
        = Pattern.compile("<lt:tu ids?=(['\"])[^'\"]+\\1/>$", Pattern.DOTALL).matcher("");
        
    
    /**
     * Passed a tag string (from the intermediate skel file), find attribute of
     * specified name in the previous tag, remove its value (leaving only the
     * quotes around an empty value), and leave the top of stack pointing to
     * the position of the second quotation.
     * @param tagString The <attr  string read from the intermediate skeleton stream
     * @param direction The direction to search. (Either "right" or "");
     * @return one of the following:
     * <ul>
     * <li>-1 if we sought backward in the buffer to find the attribute
     * <li> 0 if we failed to find the attribute
     * <li>+1 if we sought forward (toward the "end" of the buffer) to find the
     *     attribute 
     * </ul>
     */
    private int seekToAttr(String tagString, String direction) {
        String attName = "";           // What is the name of this attribute?
        String parentTag = "";         // What tag is this attribute a part of?
        
        // Find more about this tag from the intermediate skeleton file
        attrMatcher.reset(tagString);
        if (attrMatcher.find()) {
            attName = attrMatcher.group(1);
            parentTag = attrMatcher.group(2);
        }
        else {
            return 0;   // I guess we'll have to skip this TU; we can't figure where
                            // to insert it (sigh)
        }

        int endOfTag = -1;
        
        // Ticket #1036: Handle case where very first tag in file is an anchor
        // tag (!!), which has a title attribute with translatable text:
        if (skelPosStack.size() == 2 && skelPosStack.elementAt(0) == -1
                && skelPosStack.elementAt(1) == 0) {
            direction = "right";
        }
        
        // Pop (and discard) the top of the skeleton position stack, returning us to
        // the beginning of the tag that has the attribute within it, then seek to the
        // attribute name. DO THIS ONLY IF the "direction" parameter isn't "right"
        if (!direction.equals("right")) {
            boolean foundAttrBefore = true;   // We found attribute earlier

            endOfTag = skelPosStack.pop();  // If attr starts after the end of tag, we're in
                                                // deep doodoo. On second thought,
                                                // we aren't. (See further below.)
            // Now the top of stack has the beginning of the previous tag.
            String thisTagOnly = new String(skelBuf.substring(skelPosStack.peek().intValue(), endOfTag));

            String attVal = "";         // String to hold the attribute value
            boolean quoteLess = false;  // Change to true if att value is unquoted.
            // Use a regex to identify the value of the attribute (which we will delete)
            Matcher am = Pattern.compile("\\b(" + attName + ")=(['\"])(.*?)(\\2)",
                    Pattern.CASE_INSENSITIVE).matcher(thisTagOnly);

            if (am.find()) {  // If we found the above pattern ...
                attVal = am.group(3);
                // Make sure that we didn't substitute a placeholder in a
                // previous invocation ... and check for an "empty" attr value
                if (attVal.startsWith("<lt:tu id")
                    || (attVal.trim().length() == 0)) {
                    // We processed this attribute value last time. We need to
                    // look to the right.
                    foundAttrBefore = false;
                }
                // This next handles files that start with (for example) php
                // segment introduced by <?, containing an assigment to a variable
                // whose name (say, "$title") is very similar to an attribute
                // with a name (say, "title") that is interesting to us. (This
                // actully happened to us ...)
                else if (skelPosStack.peek().intValue() == 0
                        && (!thisTagOnly.substring(1, parentTag.length() + 1).equalsIgnoreCase(parentTag))) {
                        foundAttrBefore = false;
                }
                // Another variation on the above "else if"--to make sure that 
                // the attribute name isn't preceded by a $.
                else {
                    int attNameStart = am.start(1);  // Where the attribute name starts
                    if (attNameStart > 0 && thisTagOnly.charAt(attNameStart-1) == '$') {
                        // This is a (for example) PHP variable, not an attribute
                        foundAttrBefore = false;
                    }
                }
            }
            else {
                // The above pattern assumes that attribute values are surrounded
                // by quotes. However, the HTML spec doesn't *require* that they
                // be quoted (if there are no intermediate spaces, for example).
                Matcher noQuoteMatcher = Pattern.compile("\\b" + attName + "=([^\"'> \\n\\r]*)",
                        Pattern.CASE_INSENSITIVE).matcher(thisTagOnly);
                if (noQuoteMatcher.find()) {
                    attVal = noQuoteMatcher.group(1);
                    // No need to check for pre-existing <lt:tu ... placeholder. If
                    // we previously inserted one, we would have added quotes.
                    quoteLess = true;
                }
                else {
                    foundAttrBefore = false;
                }
            }

            if (foundAttrBefore) {
                // Since we're still here, attVal has the attribute value string. Delete
                // that string and position us above the second " or ' that quotes
                // the attribute value
                int attrPos = skelBuf.indexOf(attName + "=", skelPosStack.peek().intValue());
                if (attrPos == -1 || attrPos > endOfTag) { // Sanity check
                    // What!!? Either we couldn't find the attr name (-1) or it is out of range--
                    // too far to the right. Bail out!!
                    return 0;      // We're failures
                }

                // Fine the exact start position of the attribute's value
                int valPos = skelBuf.indexOf(attVal, attrPos);
                if (valPos == -1 || attrPos > endOfTag) {  // Sanity check
                    return 0; // Bail out--same problem as above!
                }

                // Well, since we're still, here, let's delete the attribute value
                skelBuf.delete(valPos,valPos + attVal.length());
                if (quoteLess) {
                    // Add two quotes
                    skelBuf.insert(valPos, "\"\"");
                    skelPosStack.push(Integer.valueOf(valPos + 1)); // Add 1 for opening quote
                }
                else {
                    skelPosStack.push(Integer.valueOf(valPos));
                }
                return -1;    // A backwards seek was successful
            }
        }
            
        // @WLW 5/18/2006 New functionality for the case where the previous tag
        // in the tskeleton doesn't correspond to the previous tag in the original
        // file, because (say) this is an attribute to an img tag, which we don't
        // normally put in the tskeleton, because it can be an x (or is it a bx)
        // tag if it comes as part of a text string.
        // Here's what we will do: look ahead for a tag with the attribute
        // mentioned in the tskeleton and replace the attribute value with
        // an <lt:tu id='tuid'/> tag, then reposition ourselves where were
        // at the beginning of whatever place we were in the buffer when seekToAttr
        // was called. If subsequent merging ends up deleting this work because it
        // is part of a string that references the img (say) tag, so be it.
        if (endOfTag != -1) {
            skelPosStack.push(endOfTag); // Let's restore the stack so that the top points
                                         // to the current position in the main HTML.
        }
        
        int parentTagStart = skelBuf.indexOf("<" + parentTag,skelPosStack.peek().intValue());
        
        // We really *should* find the tag later in the file (and not very later)
        if (parentTagStart != -1) {
            // Find the nearest close '>' after the parent start tag.
            int parentTagEnd = skelBuf.indexOf(">", parentTagStart);
            
            // Ticket 1036 7/10/2007 WLW: Handle the case where there are
            // multiple translatable attribute values in the same tag (e.g.
            // alt and title. If this is the *second* (or higher) attribute,
            // the above value in parentTagEnd will point to the ">" that
            // ends the lt:tu placeholder. We need to verify that parentTagEnd
            // is actually the ">" that ends the parent tag. If it is a placeholder,
            // search to the next ">" until we find one that is actually the
            // end of the parent tag of the attribute.
            if (parentTagEnd > 0 && skelBuf.charAt(parentTagEnd -1) == '/') {
                // This is a potential problem. Check further:
                boolean parentTagEndFound = false;
                while (!parentTagEndFound) {
                    // Check to see if this '>' ends a placeholder (instead of 
                    // ending the parent tag):
                    if (placeHolderMatcher.reset(skelBuf.subSequence(parentTagStart,
                            parentTagEnd+1)).find()) {
                        // The '>' that we *thought* was the end of the parent
                        // tag was actually the end of the TU placeholder. Find the
                        // next '>':
                        parentTagEnd = skelBuf.indexOf(">", parentTagEnd+1);
                        // Check to verify that we didn't fail to find a '>'
                        // later in the file. If we didn't find one, we're
                        // in deep doodoo--the file is probably corrupt.
                        if (parentTagEnd == -1) { // Very bad!!
                            System.err.println("HtmlSkeletonMerger.SeekToAttr: "
                                + "parentTagEnd not found!!");
                            return 0;  // Couldn't find the attribute
                        }
                    }
                    else {
                        // The '>' wasn't the end of a placeholder. I guess it
                        // must've been the end of the parent tag. (We can proceed.)
                        parentTagEndFound = true;
                    }
                }
            }
            
            
            // Find the named attribute within the bounds of parentTagStart and
            // parentTagEnd
            int attrNamePos = skelBuf.indexOf(attName + "=",parentTagStart);
            
            if ((attrNamePos > (parentTagStart + parentTag.length() + 1))
                && (attrNamePos < (parentTagEnd - attName.length() - 3))) {
                // I guess we're within bounds; let's replace the attribute value
                int valStart = attrNamePos + attName.length() + 2; // Where it starts
                int valEnd = -1;                           // Where the value ends
                char quotChar = skelBuf.charAt(valStart - 1);  // Double or single quote?
                
                // 4/5/7 WLW: HTML doesn't require that values be quoted if they
                // have no internal spaces. The next "if" handles the most common
                // case, where att values *are* quoted.
                if ((quotChar == '\'') || (quotChar == '"')) {  // Value is quoted
                    valEnd = skelBuf.indexOf(Character.toString(quotChar),valStart);
                    // If we can identify the start and end of the attribute's value ...
                    if ((valEnd != -1) && (valEnd < parentTagEnd)) {
                        skelBuf.delete(valStart,valEnd); // Now value is empty
                        skelPosStack.push(Integer.valueOf(valStart));     // Where to insert tag ... later
                        return +1;
                    }
                }
                else {
                    // Attribute value is *not* quoted. 
                    // Adjust valStart (which assumed--above--the presence of a
                    // quote) by decrementing it by 1
                    valStart--;
                    int nextSpace = skelBuf.indexOf(" ",valStart);
                    int nextCR = skelBuf.indexOf("\r",valStart);
                    int nextLF = skelBuf.indexOf("\n", valStart);
                    int nextLT = skelBuf.indexOf(">", valStart);
                    if (nextSpace == -1) {nextSpace = Integer.MAX_VALUE;}
                    if (nextCR    == -1) {nextCR    = Integer.MAX_VALUE;}
                    if (nextLF    == -1) {nextLF    = Integer.MAX_VALUE;}
                    if (nextLT    == -1) {nextLT    = Integer.MAX_VALUE;}
                    valEnd = Math.min(Math.min(Math.min(nextSpace,nextCR),nextLF),nextLT);
                    skelBuf.delete(valStart,valEnd);  // We deleted the value
                    skelBuf.insert(valStart,"\"\"");  // We added two double quotes
                    // Add 1 to the valStart value we push on the stack (for the opening quote)
                    skelPosStack.push(Integer.valueOf(valStart+1));     // Where to insert tag ... later
                    return +1;
                }
            }
        }
        
        return 0;   // Couldn't find the attribute.
        
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the current position) in the skeleton Buffer. 
     * Upon exit, the skelPosStack's top should be at the position one space to the
     * right of the close (">") of the tag.
     * Note: This could be either a beginning or ending tag.
     * @param tagString The tag string read from the intermediate skeleton stream
     * @param prevTagString The immediately preceding string read from the
     *        intermediate skeleton stream.
     * @return A boolean value indicating if a "phantom" start tag prevented us
     *         from seeking to a start tag that was reported by NekoHTML but
     *         didn't exist in the original file. (<i>Note:</i> We have observed
     *         a situation where a displayable HTML file was missing its
     *         start p tag, but had an end p tag (!!). NekoHTML dutifully
     *         detected the situation and reported a start p tag immediately
     *         before the (existing) end p tag.)
     */
    private boolean seekToTag(String tagString, String prevTagString) {
        
        // Find more about this tag from the intermediate skeleton file
        String tagPrefix = null;
        // When this comment was written, tagMatcher was:
        //  "^([^ ]+) seq='(\\d+)'>"
        tagMatcher.reset(tagString);
        if (tagMatcher.find()) {
            tagPrefix = tagMatcher.group(1);
            curTagSeq = Integer.parseInt(tagMatcher.group(2));
        }
        
        // Special handling for end tags--which might not actually exist in the
        // original file (but could have been supplied by NekoHTML when it encountered
        // another opening tag that implied the closing of the tag).
        boolean isEndTag = false;
        if (tagPrefix.charAt(1) == '/') {
            isEndTag = true;
            // Don't check for phantom div tags--they can be nested ... and
            // are *always* paired (sure they are ... :-)
            if (tagPrefix.equalsIgnoreCase("</div") 
                && prevTagString.toLowerCase().startsWith("<div")) {
                isEndTag = false;
            }
        }
        
        // The next doesn't apply to div's--which can be tightly
        // nested: We can see <div><div><div></div> without it implying
        //                    <div></div><div></div><div></div>
        
        if (isEndTag && prevTagString.toLowerCase().startsWith(("<" + tagPrefix.substring(2)).toLowerCase())) {
            // This end tag might signify that one of the following exists (for example):
            //  <p/>
            //  <p></p>
            //  <p>      [End tag is a phantom tag supplied by NekoHtml]
            if (skelBuf.substring(skelPosStack.peek()-2,skelPosStack.peek()).equals("/>")) {
                // This is a phantom close tag to an empty tag (like <p />) that occurred earlier
                return true;                 // Just return without seeking. Case 1 in the above comment.
            }
            // See if an empty tag does *not* exist (i.e. the second in <p></p>
            // does *not* exist).
//            int endPos = skelBuf.indexOf("</" + tagPrefix.substring(2), skelPosStack.peek());
            int endPos = findEndTag(tagPrefix.substring(2), skelPosStack.peek());
            if (endPos == -1) {
                // No more end tags! We'll never find this one
                return true;                 // Case three in the three lines in the comment above
            }
            // We need to be case-insensitive, so we'll do it this way
            String fileEndTag = skelBuf.substring(endPos, endPos + (tagPrefix.length()));
            if (!tagPrefix.equalsIgnoreCase(fileEndTag)) {
                // Can't possibly be a close tag. This is a phantom one.
                System.err.println("Is a phantom start tag in seekToTag");
                return true;    // Is phantom start tag
            }
        }

        // Skip over HTML comments-- if there are any more.
        int commentStart = -1;
        if (!noMoreComments) {
            // Find where next comment begins (so that we can compare it to the position
            // of the next tag and skip over the comments if necesary.
            commentStart = skelBuf.indexOf("<!--",skelPosStack.peek().intValue());
            if (commentStart == -1) {
                noMoreComments = true;
            }
        }

        // Now find the next occurrence of tag in the skeleton buffer--an occurrence
        // that isn't inside a comment
        boolean foundTag = false;
        
        int seekFrom = skelPosStack.peek().intValue();  // Save this in case ...
        int prev = skelPosStack.get(skelPosStack.size() - 2);  // This, too.
        
        while (! foundTag) {
            int pos = skelBuf.indexOf(tagPrefix, skelPosStack.peek().intValue());
            if (pos > -1) {   // We found the tag
                // Find the tag end:
                int endOfTag = skelBuf.indexOf(">", pos + tagPrefix.length());
                if (endOfTag > -1) {
                    // Add one to the position, so that it will be one position
                    // to the right of the end of the tag.
                    skelPosStack.push(Integer.valueOf(endOfTag + 1));
                    // Keep the stack from growing out of control. We're really
                    // Only interested in the top four elements on the stack.
                    while (skelPosStack.size() > (4 + numConsecutiveAltAttrs)) {
                        skelPosStack.remove(0);
                    }
                }
                else {  // We've encountered EOF in the middle of the tag!
                    System.err.println("HtmlSkeletonMerger encountered end-of-stream"
                            + " condition in middle of tag that begins " + tagPrefix);
                    if (!isEndTag) {        // A phantom start tag, I guess
                        return true;        // Is a phantom start tag.
                    }
                    return false;           // false if this is an end tag ...
                }
            }
            else {  // We didn't find the tag
                    // It could be a "freebie" close tag that neko HTML returned 
                    // during the parse. (Example: A </meta ... > tag that doesn't
                    // actually occur in the source ...)
                    // 2/23/7 WWhipple: This could also be a "phantom" start
                    // tag (!!)--one that doesn't exist in the source document
                    // (in error, but the browser still "works"), but is reported
                    // by NekoHTML immediately before the end tag.
                if (!isEndTag) {        // A phantom start tag, I guess
                    return true;          // Phantom start tag
                }
                return false;             // false if this is an end tag ...
            }
            
            // OK, so we found the tag. Before returning, make sure that it is
            // not inside an HTML comment. (It it is, we will need to find the
            // end of the comment and look again for the tag)
            int endComment = Integer.MAX_VALUE;
            // Note: We found the *first* comment Start value at the top of this
            // method.
            if ((!noMoreComments) && (commentStart < pos)) {
                // Skip comment(s) that start before the index ("pos") of the tag
                // that we found in the skeleton buffer
                endComment = skipComment(commentStart,pos);
            
                if ((endComment == -1) || (endComment <= skelPosStack.peek().intValue())) {
                    
                    /* @WLW 5/18/2006: Comment out the following, because we have already
                     * sought past the end of the requested tag.
                    skelPosStack.push(Integer.valueOf(pos)); // If we found the position of the tag, remember it
                     */
                    foundTag = true;    // Quit looking
                }
                else { // The comment ended after the tag position. We need to look again.
                
                    // Advance past the comment, so we can look more for the tag
                    skelPosStack.push(Integer.valueOf(endComment));

                    // Find where the next comment starts
                    commentStart = skelBuf.indexOf("<!--",skelPosStack.peek().intValue());
                    if (commentStart == -1) {
                        noMoreComments = true;
                    }                
                }
            }
            else { // Either no more comments or they all start after the tag whose end we
                   // found. The tag we found is good.
                foundTag = true;
            }
            
            // Before blindly seeking, handle the same sort of case we do in 
            // the delete to tag method--where a breaking tag comes before a
            // closing tag.
            if (foundTag) {
                if (isEndTag || tagPrefix.equalsIgnoreCase("</div")) {
                    // This *does* apply to </div ... tags--as witnessed by
                    // test case HtmlImporterFixBadSkelTest, where (at line 399)
                    // the neko parser closes the div that is immediately before
                    // the opening h1 tag. (The file actually closes the div
                    // *after* the closing /h1 [sigh]).
                    String charRun = skelBuf.substring(seekFrom, pos);
                    if (hasInterveningBreakTag(charRun)) {
                        // Restore the stack to match what it was upon entering this
                        // method.
                        skelPosStack.push(Integer.valueOf(prev));
                        skelPosStack.push(seekFrom);
                        return false; // False--this is a phantom *end* (not start) tag
                    }
                }
                else {    // A start tag
                    // This could also be a phantom *start* tag!! Most browsers will
                    // correctly display the following HTML:
                    //
                    // <p>This is paragraph 1.</p>
                    // This is paragraph 2.</p>
                    // <p>This is paragraph 3.</p>
                    //
                    // (Note that paragraph 2 is missing its start p tag.) 
                    //
                    // NekoHTML reports the above as if it were:
                    //
                    // <p>This is paragraph 1.</p>
                    // This is paragraph 2.<p></p>
                    // <p>This is paragraph 3.</p>
                    //
                    // The <p> after "paragraph 2." should be ignored and reported as
                    // a phantom start tag.

                    // If this is not an end tag (i.e., a [possibly phantom] start tag),
                    // look for an intervening end tag of the same name. If one is found,
                    // report this as a phantom start tag and restore the skelPosStack's
                    // top two positions.
                    String charRun = skelBuf.substring(seekFrom, pos).toString().toLowerCase();
                    
                    // In the example above, if we were looking for the (nonexistent)
                    // <p> at the end of paragraph 2 (above), but instead found the
                    // <p> at the beginning of paragraph 3, we should find a </p>
                    // intervening (the </p> at the end of paragraph 2).
                    int endTagPos = charRun.indexOf("</" + tagPrefix.substring(1).toLowerCase() + ">");
                    if (endTagPos > -1) {    // This is a phantom start tag!
                        // Restore the stack to match what it was upon entering this
                        // method.
                        skelPosStack.push(Integer.valueOf(prev));
                        skelPosStack.push(seekFrom);
                        return true;  // This is a phantom start tag ... and we're done
                    }
                }
            }
        }
        
        return false;        // Not a phantom start tag
    }

    /**
     * Passed the position of the start of a known comment, skip
     * comments until we have skipped all comments that begin before
     * the position represented in the second param.
     * @param startComment The index position of an existing comment
     * @param searchTo Find the end of any intervening comments that begin
     *        before the position represented in searchTo.
     * @return The position that is one past the last character of an
     *        end of comment (i.e. -->)
     */
    private int skipComment(int startComment, int searchTo) {
        boolean done = false;
        int retValue = searchTo;     // Default value (OK??)
        int curCommentStart = startComment;
        int curCommentEnd = -1;
        int prevCommentStart;
        int prevCommentEnd;
        while (! done) {
            prevCommentEnd = curCommentEnd;
            curCommentEnd = skelBuf.indexOf("-->",curCommentStart);
        
            // Help! Runaway comment!! (This shouldn't happen!! It means that the rest
            // of the stream is a comment)
            if (curCommentEnd == -1) {
                retValue = Integer.MAX_VALUE;  // No end in sight
                done = true;
            }
            else { // We found an end comment
                // Increment the end by three (to go past the -->)
                curCommentEnd += 3;
                if (curCommentEnd >= searchTo) {
                    // No more comments can possibly start before the searchTo
                    // position. We're done
                    retValue = curCommentEnd;
                    done = true;
                }
                else {
                    // The comment end was before the searchTo value. Make sure
                    // that no additional comments start (and possibly end) before
                    // the searchTo position
                    prevCommentStart = curCommentStart;
                    // Look ahead for the next comment start
                    curCommentStart = skelBuf.indexOf("<!--", curCommentEnd);
                    if ((curCommentStart == -1) || (curCommentStart > searchTo)) {
                        // We're done
                        retValue = curCommentEnd;
                        done = true;
                    }
                    else { // The next comment started before searchTo
                           // Continue looping to find where it ends.
                        // Go through the while again.
                    }
                }
            }
        }
        
        return retValue; // The position one after the end of the last comment
                         // that *began* before the searchTo position
    }
}
