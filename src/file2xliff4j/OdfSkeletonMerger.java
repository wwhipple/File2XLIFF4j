/*
 * OdfSkeletonMerger.java
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
 * <li>The original ODF stream from which the XLIFF was created.
 * </ul>
 * It outputs a final Skeleton file that can be used to generate
 * ODF from XLIFF.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class OdfSkeletonMerger /* extends Thread */ implements SkeletonMerger {
    
    private StringBuilder skelBuf;              // Starts with copy of ODF document
    private int curTagSeq = 0;
    private int prevTagSeq = 0;
    private String previousTag = "";           // The tag matched before this one.
    private int odfStreamPos = 0;              // How far have we read in the original ODF?
    private static final int BLKSIZE = 8192;   // How much to read at a time.
    private String skelTemp = "";              // Temporary file name for multi-level processing
    
    /**
     * Constructor for the skeleton merger object
     */
    public OdfSkeletonMerger()  { 
    }
    
    /**
     * Create a skeleton file from the pseudo skeleton and the original odf.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param odfInStream The original ODF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the odf
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream odfInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException {
        // This method will never be called by the ODF Importer. Let's use a
        // default maxTuDepth of 3, though.
        this.merge(tSkelInStream, odfInStream, skeletonOutStream, encoding, 3);
    }

    /**
     * Create a skeleton file from the pseudo skeleton and the original odf.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param odfInStream The original ODF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the odf
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs.
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream odfInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException {
        
        if (tSkelInStream == null || odfInStream == null
                || skeletonOutStream == null || encoding == null) {
            throw (new IllegalArgumentException());
        }

        BufferedReader tSkel = null;
        BufferedReader odf =  new BufferedReader(new InputStreamReader(odfInStream, encoding));
        BufferedWriter skel  = new BufferedWriter(new OutputStreamWriter(skeletonOutStream, encoding));

        // Read the ODF stream into a buffer for easy (quick and dirty for now)
        // manipulation
        // Note: The ODF file has two lines: The first is the XML declaration; the
        // second is one long line without intervening newlines. (At least, this
        // is apparently the way that OpenOffice creates the ODF ...)
        skelBuf = new StringBuilder();
        char[] buf = new char [BLKSIZE];          // For reading
        int numRead;
        while ((numRead = odf.read(buf)) > 0) {
            skelBuf.append(buf,0,numRead);
        }
      
        String curSkelLine = "";              // The temp skel line read in this iteration
        String prevSkelLine = "";             // The line read last time
        
        // If there are no nested text:p elements, we can read directly from the tskeleton
        if (maxTuDepth == 1) {
            tSkel = new BufferedReader(new InputStreamReader(tSkelInStream, encoding));
        }
        
        // Otherwise we need to make several passes and delete all the elements
        // in the original content.xml (on which the new skeleton will be based)
        // that appear in tskeleton with depth > 1.
     
        else {
            // Read the input tskeleton into a temporary file
//            File cwd = new File(System.getProperty("user.dir"));  // Get current directory
//            File tempTSkeleton = File.createTempFile("tskel", "tmp", cwd);
//            tempTSkeleton.deleteOnExit();                  // Delete the file on our way out.

            String curTempTSkeleton = this.skelTemp; // Name of temporary work file
            OutputStream tempOutStream = new FileOutputStream(curTempTSkeleton);
            // Write the input to the file
            int n = 0; byte[] b = new byte[this.BLKSIZE];
            while ((n = tSkelInStream.read(b)) != -1) {
                tempOutStream.write(b,0,n);
            }
            tempOutStream.flush();
            tempOutStream.close(); 
            
            Matcher skelM = Pattern.compile("depth='(\\d+)'").matcher("");
            boolean[] isEmpty = new boolean[maxTuDepth + 1];
            // We will remove all but the depth=1 text:p elements from the odfInStream
            for (int i = maxTuDepth; i > 1; i--) {

                // We'll make several reading passes through our temporary copy of the tskeleton
                // input stream this method was passed.
                BufferedReader tSkelRdr = new BufferedReader(new InputStreamReader(
                        new FileInputStream(curTempTSkeleton), encoding));

                // These refer to lines read from the temporary Skeleton input stream:
                boolean done = false;     // Not done yet

                boolean iterationDone = false; // This while iteration not done yet
                this.odfStreamPos = 0;         // Start at the beginning of the odf stream again
                while (! iterationDone) {
                    curSkelLine = tSkelRdr.readLine();
                    if (curSkelLine == null) {        // When we reach end of stream,
                        iterationDone = true;
                    }
                    else {
                        int curDepth = 0;    // Safe default.
                        skelM.reset(curSkelLine);
                        if (skelM.find()) {
                            curDepth = Integer.parseInt(skelM.group(1));
                        }
                        if (curSkelLine.contains("depth='" + i + "'")) {
                            if (curSkelLine.startsWith("<text:p ")) {
                                deleteElement(curSkelLine);
                            }
                        }
                        else if (curSkelLine.startsWith("<text:")
                            && (curDepth < i)) {
                            // While seeking, note if it is an empty tag (ends w/ "/>")
                            isEmpty[curDepth] = seekToTag(curSkelLine);
                        }
                        else if (curSkelLine.startsWith("</text:")
                            && (curDepth < i)) {
                            if (isEmpty[curDepth]) {
                                // Previous opening tag was also a closing (empty)
                                // tag. Don't seek to it; just reset its isEmpty flag
                                isEmpty[curDepth] = false;
                            }
                            else {
                                // Previous opening tag wasn't empty, so seek to
                                // its closing tag.
                                seekToTag(curSkelLine);
                            }
                        }
                    }
                }

                // This iteration is done; close our temporary tSkeletons and reopen them
                // for the next iteration.
                tSkelRdr.close();
            }
            
            // We're done with the tskeleton. Reopen it for the main depth=1 loop
            tSkel = new BufferedReader(new InputStreamReader(
                    new FileInputStream(curTempTSkeleton), encoding));
        }
        
        // At the "root" level, we need to keep track of the IDs of adjacent 
        // segments (that are within a larger paragraph), because--at export time--
        // we will need to treat them as a single group.
        ArrayList<String> segIds = new ArrayList<String>();  // IDs of sequence of adjacent segments
        int paraLen = 0;                      // Accumulated length of adjacent segments
        int totSegs = -1;                     // Total adjacent segments in this run of TUs
        int curSeg = -1;                      // Number of this segment (in run of adjacent TUs)
//        String tuOrFormat = "";               // Is the current tag a tu or format entry?
        boolean inSegmentRun = false;         // Not currently in a multi-segment run of TUs
        
        // Matcher to capture information from a tu line. (In practice, there might not be format
        // lines in ODF files?)
        Matcher tuMatcher = Pattern.compile("^<tu\\s+id=(['\"])([-\\w]+)\\1\\s+length=(['\"])(\\d+)\\3"
                + "\\s+depth=(['\"])(\\d+)\\5"
                + "\\s+no=(['\"])(\\d+)\\7\\s+of=(['\"])(\\d+)\\9>").matcher("");

        // Now deal with the lines (in the tskeleton) that have a depth of 1.
        boolean isEmpty = false;     // Notes if element is empty (<text:p ... />)
        odfStreamPos = 0;       // We'll start at the first again.
        for (;;) {
            if (!inSegmentRun) {
                prevSkelLine = curSkelLine;       // Remember what we read last time
            }
            // Read a line from the temporary skeleton file:
            curSkelLine = tSkel.readLine();
            if (curSkelLine == null) {        // When we reach end of stream,
                break;                        //   quit looping
            }
            // Skip any lines that have depth > 1
            else if (!curSkelLine.contains(" depth='1'")) {
                continue;                     // ... and keep reading
            }
            else if (curSkelLine.startsWith("<text:p ")
                || curSkelLine.startsWith("<text:h ")) {
                isEmpty = seekToTag(curSkelLine);   // Seeks to *just after* the text:p tag
            }
            else if (curSkelLine.startsWith("</text:h ")) {
                if (!isEmpty) {
                    this.deleteToTag(curSkelLine);
                }
            }
            else if (curSkelLine.startsWith("</text:p ")) {
                if (!isEmpty) {
                    if (prevSkelLine.startsWith("<tu id=")
                        || prevSkelLine.startsWith("<format id=")) {
                        // We just inserted a TU or a format line in the previous iteration;
                        // The TU/format line's bx/ex/x tags will restore/preserve the original
                        // formatting/non-text tags
                        deleteToTag(curSkelLine);
                    }
                    else {
                        // We didn't just insert a TU (the previous iteration). Since
                        // This tag wasn't empth (i.e. not <text:p/>), let's guess
                        // that there were probably some non-text formatting tags
                        // in the text:p element, which we need to preserve.   ... So
                        // just seek to the end test:p tag, leaving all the intervening
                        // tags.
                        seekToTag(curSkelLine);
                    }
                }
            }
            else if (curSkelLine.startsWith("<tu id=")) {  // Refers to a translation unit
                tuMatcher.reset(curSkelLine);    // So we can capture components

                if (tuMatcher.find()) {           // Is this a tu or format line?

                    // Yes! Capture information from the line.
                    // Add the tu/format ID to the list of IDs in this
                    // run of adjacent segments.
                    // Each entry is something like either of the following:
                    // format:203
                    // tu:b2a0a465-263a-4106-ade8-a3f466bb624f
                    //
                    segIds.add("tu:" + tuMatcher.group(2));  
                    paraLen += Integer.parseInt(tuMatcher.group(4)); 
                    curSeg = Integer.parseInt(tuMatcher.group(8));
                    totSegs = Integer.parseInt(tuMatcher.group(10));

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
                    insertTu(curSkelLine, segIds); 
                    segIds.clear();     // Start over.
                }
                else {
                    System.err.println("Unable to parse tu line in tskeleton!");
                }
                
            }
            else if (curSkelLine.startsWith("<format id=")) {  // Refers to a translation unit
                // Substitute <trans-unit lt:format id /> in new skeleton
                insertFormat(curSkelLine); // 0 might be a dummy
            }
            else if (curSkelLine.startsWith("<maxtudepth")) { // Last line in file
                // do nothing for now
            }
            else {
                System.err.println("Unexpected line " + curSkelLine + " in temporary skeleton.");
            }
        }
        
        // Write the skeleton buffer to the skel stream
        for (int i = 0; i < skelBuf.length(); i++) {
        //for (int i = 0; i < odfStreamPos; i++) {
            skel.write(skelBuf.charAt(i));
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
        
        if (property.equals("http://www.lingotek.com/converters/properties/skelTemp")) {
            this.skelTemp = value.toString();
        }
        return;
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the "current" position) in the skeleton Buffer (represented
     * by the value in odfStreamPos).
     * Then, delete everything between that beginning tag and the next occurrence
     * of an end tag 
     *
     * <p>Upon exit, the odfStreamPos varible should be at the position in the buffer
     * where the deleted element formerly began (before it was deleted).
     *
     * <p>This tag will be called only (at the time of its first implementation)
     * to delete nexted text:p elements)
     * @param tagString The tag line read from the intermediate skeleton stream
     */
    private void deleteElement(String tagString) {
        // Get tag prefix and sequence number from the string passed in.
        String tagPrefix = null;
        
        // Typical tag: <draw:text-box seq='29' pass='deletion'>
        String tagPatt = "^([^ ]+) seq='(\\d+)'[^>]*>";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(tagString);
        if (tm.find()) {
            tagPrefix = tm.group(1);
            curTagSeq = Integer.parseInt(tm.group(2));  // For use in other methods?
        }
        
        // Where does the tag we're looking for start?
        int elementStartsAt = skelBuf.indexOf(tagPrefix,odfStreamPos);
        int endOfElement = -1;                 // Future odfStreamPos
        if (elementStartsAt > -1) {                // We found the opening tag
            // Handle the case where this is an empth element--something like
            // <text:p/>
            int gtPos = skelBuf.indexOf(">",elementStartsAt);
            if (skelBuf.charAt(gtPos-1) == '/') {
                endOfElement = gtPos + 1; // After close of empty element
            }
            else {
                // Find the end tag:
                endOfElement = skelBuf.indexOf("</" + tagPrefix.substring(1) + ">", 
                        elementStartsAt + tagPrefix.length());
                if (endOfElement > -1) {
                    // Add 2 to the position--one for the closing '>' and one
                    // to put it one past the end of the element.
                    endOfElement += (2 + tagPrefix.length());
                }
            }
        }
        if (endOfElement == -1) {
            System.err.println("OdfSkeletonMerger.deleteElement: Can't locate"
                    + " beginning of element " + tagPrefix.substring(1));
            return; // Can't delete
        }

        // Verify that the position we're deleting to isn't past the end of the
        // buffer. 
        if (endOfElement > skelBuf.length()) {
            System.err.println("OdfSkeletonMerger.deleteElement: End of element" 
                    + " is beyond the end of the skelBuf.");
        }
        
        // Now delete everything we were asked to delete
        skelBuf.delete(elementStartsAt, endOfElement);
        
        // Now set the ODF Stream Position for next time.
        odfStreamPos = elementStartsAt;
        return;
    }

    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the "current" position) in the skeleton Buffer (represented
     * by the value in odfStreamPos).
     * Then, delete everything between odfStreamPos and the beginning of the specified tag 
     * string.
     *
     * Upon exit, the odfStreamPos varible should be at the position one space to the
     * right of the close (">") of the tag passed in as the tagString argument.
     * Note: The tag passed in should be an ending tag--probably </text:p> or
     * </text:h>. This method is called immediately after a TU placeholder element
     * is inserted into the skeleton stream.
     * @param tagString The tag string read from the intermediate skeleton stream
     */
    private void deleteToTag(String tagString) {
        // Get tag prefix and sequence number from the string passed in.
        String tagPrefix = null;
        
        // Typical tag: </style:paragraph-properties seq='29'>
        String tagPatt = "^([^ ]+) seq='(\\d+)'.*>";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(tagString);
        if (tm.find()) {
            tagPrefix = tm.group(1);
            curTagSeq = Integer.parseInt(tm.group(2));  // For use in other methods.
        }
        
        // Where does the tag we're looking for start?
        
        // Use nextChar so that we won't mistake </text:page-number> for </text:p>
        String nextChar = " ";  
        if (tagPrefix.charAt(1) == '/') {
            nextChar = ">";
        }
        int tagStartsAt = skelBuf.indexOf(tagPrefix + nextChar, odfStreamPos);
        int endOfTag = -1;                     // Future odfStreamPos
        if (tagStartsAt > -1) {                // We found the tag
            // Find the tag end:
            endOfTag = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (endOfTag > -1) {
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                endOfTag++;
            }
            else {
                return; // Can't delete (Serious!!)
            }
        }
        else {
            return; // Can't delete
        }

        // Before deleting, save the tag we're deleting "to" in what will (to a
        // future method call) be the previous Tag.
        previousTag = skelBuf.substring(tagStartsAt,endOfTag);           // In case anyone cares

        // Now delete everything we were asked to delete
        skelBuf.delete(odfStreamPos, tagStartsAt);
        
        // Decrement the tag end position by the number of characters we deleted
        endOfTag-= (tagStartsAt-odfStreamPos);
        
        // Now set the ODF Stream Position for next time.
        odfStreamPos = endOfTag;
        prevTagSeq = curTagSeq;            // For seeking ...
        return;
    }

    /**
     * Passed a format tag string (from the intermediate skel file), insert a
     * tu place holder of the format <lt:format id='[id]'/> in the current
     * skel position. 
     * @param formatString The line from the intermediate skeleton file that
     *        represents the format placeholder we are to insert
     * @return The status of the operation: true=succeeded, false=failed
     */
    private boolean insertFormat(String formatString) {
        String formatID = "";               // What is this TU ID?
//        String tuLength = "";           // How long is the text of the TU?
        
        // Get the TU ID (number, UUID, etc.) from the intermediate skeleton file
        String tagPatt = "^<format id='([^\']+)'.*>$";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(formatString);
        if (tm.find()) {
            formatID = tm.group(1);
//            tuLength = tm.group(2);
        }
        else {
            return false;   // I guess we'll have to skip this TU; we can't figure where
                            // to insert it (sigh)
        }
        
        // The empty element we will insert
        String placeHolder = "<lt:format id='" + formatID + "'/>";
        skelBuf.insert(odfStreamPos, placeHolder);  // Insert it now!
        odfStreamPos+=placeHolder.length();         // Increment cur position in stream
        
        return true;
    }
    
    /**
     * Passed a tu tag string (from the intermediate skel file), insert a
     * tu place holder of the format <lt:tu id='[id]'/> in the current
     * skel position. 
     * @param tuTagString The line from the intermediate skeleton file that
     *        represents the TU holder we are to insert
     * @return The status of the operation: true=succeeded, false=failed
     */
    private boolean insertTu(String tuTagString, ArrayList<String> segIds) {
        String tuID = "";               // What is this TU ID?
//        String tuLength = "";           // How long is the text of the TU?
        String placeHolder = "";        // Placeholder for skeleton file
        
        // If we have no reason to suspect that we are creating a placeholder
        // for multiple adjacent segments, the placeholder will reference a
        // single TU
        if ((segIds == null || segIds.size() <= 1)) {
            // Get the TU ID (number, UUID, etc.) from the intermediate skeleton file
            String tagPatt = "^<tu id='([^\']+)'.*>$";
            Pattern tp = Pattern.compile(tagPatt);
            Matcher tm = tp.matcher(tuTagString);
            if (tm.find()) {
                tuID = tm.group(1);
            }
            else {
                return false;   // I guess we'll have to skip this TU; we can't figure where
                                // to insert it (sigh)
            }

            // The empty element we will insert
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
        
        skelBuf.insert(odfStreamPos, placeHolder);  // Insert it now!
        odfStreamPos+=placeHolder.length();         // Increment cur position in stream
        
        return true;
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the current position) in the skeleton Buffer. 
     * Upon exit, the skelPosStack's top should be at the position one space to the
     * right of the close (">") of the tag.
     * <p>If the tag passed in is an end tag (</tag>) it *could* be a "dummy" tag
     * reported by SAX: Whenever SAX encounters an "empty" tag (<tag/>) it responds
     * by reporting a beginning tag followed immediately by an end tag. This method
     * checks to see if the previous tag in the original ODF was an empty tag. If it was
     * then this method just exits ...
     * Note: This could be either a beginning or ending tag.
     * @param tagString The tag string read from the intermediate skeleton stream
     * @return true if this is an empty tag, else false.
     */
    private boolean seekToTag(String tagString) {
        // Find more about this tag from the intermediate skeleton file
        String tagPrefix = null;
        String tagPatt = "^([^ ]+) seq='(\\d+)'.*>";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(tagString);
        if (tm.find()) {
            tagPrefix = tm.group(1);
            curTagSeq = Integer.parseInt(tm.group(2));
        }

        // Is this a closing tag?
        boolean isEndTag = false;
        if (tagString.charAt(1) == '/') {
            isEndTag = true;
        }
        
        // If so, was the previous tag in the input stream an emptytag? If so,
        // we're done--no seeking is necessary
//        if (isEndTag && previousTag.endsWith("/>") && (curTagSeq == (prevTagSeq+1))) {
//            return false;      // We're done. BTW, not an empty tag either
//        }

        // Where does the tag we're looking for start?
        int tagStartsAt = skelBuf.indexOf(tagPrefix,odfStreamPos);
        int endOfTag = -1;                     // Future odfStreamPos
        if (tagStartsAt > -1) {                // We found the tag
            // Find the tag end:
            endOfTag = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (endOfTag > -1) {
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                endOfTag++;
            }
            else {
                return false; // Can't find. Return--maybe future call will succeed?
            }
        }
        else {
            return false; // Ditto
        }

        // Before seeking, save the tag we're seeking past in what will (to a
        // future method call) be the previous Tag.
        previousTag = skelBuf.substring(tagStartsAt,endOfTag); 
        prevTagSeq = curTagSeq;             // Save its sequence number as well

        // Then move past the tag.
        odfStreamPos = endOfTag;
        if ((skelBuf.charAt(odfStreamPos - 1) == '>') &&
                (skelBuf.charAt(odfStreamPos - 2) == '/')) {
            return true;
        }
        else {
            return false;
        }
    }
    
    
//    private void skipElement(String tagString) {
//        
//    }

}
