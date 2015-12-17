/*
 * XliffSkeletonMerger.java
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
 *     from the original XLIFF input.</li>
 * <li>The original XLIFF stream from which the "normalized" XLIFF was created.
 * </ul>
 * It outputs a final Skeleton file that can be used to generate
 * the original XLIFF (with modified/added targets) from the normalized XLIFF.
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class XliffSkeletonMerger implements SkeletonMerger {
    
    private StringBuilder skelBuf;              // Starts with copy of XLIFF document
    private int curTagSeq = 0;
    private int prevTagSeq = 0;
    private String previousTag = "";            // The tag matched before this one.
    private int xliffStreamPos = 0;             // How far have we read in the original XLIFF?
    private String curTuID = "";                // Keep track of the current TU ID for use
                                                // of the wildcard tag we insert before the
                                                // </trans-unit> tag
    
    /**
     * Constructor for the skeleton merger object
     */
    public XliffSkeletonMerger()  { 
    }
    
    /**
     * Create a skeleton file from the pseudo skeleton and the original XLIFF.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param xliffInStream The original XLIFF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the XLIFF
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream xliffInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException {
        this.merge(tSkelInStream, xliffInStream, skeletonOutStream, encoding, 1);
    }

    /**
     * Create a skeleton file from the pseudo skeleton and the original XLIFF.
     * @param tSkelInStream The intermediate skeleton we will read from
     * @param xliffInStream The original XLIFF to read from and merge with
     * @param skeletonOutStream Where I write the new skeleton
     * @param encoding The encoding used by the XLIFF
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs.
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream xliffInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException {

        if (tSkelInStream == null || xliffInStream == null
                || skeletonOutStream == null || encoding == null) {
            throw (new IllegalArgumentException());
        }

        BufferedReader tSkel = new BufferedReader(new InputStreamReader(tSkelInStream, encoding));
        BufferedReader xliff =  new BufferedReader(new InputStreamReader(xliffInStream, encoding));
        BufferedWriter skel  = new BufferedWriter(new OutputStreamWriter(skeletonOutStream, encoding));
        
        // Read the XLIFF stream into a buffer for easy (quick and dirty for now)
        // manipulation
        // Note: The XLIFF file has two lines: The first is the XML declaration; the
        // second is one long line without intervening newlines. (At least, this
        // is apparently the way that OpenOffice creates the xliff ...)
        skelBuf = new StringBuilder();
        int ch;          // For reading
        
        while ((ch = xliff.read()) != -1) {
            skelBuf.append((char)ch);
        }
        
        // These refer to lines read from the temporary Skeleton input stream:
        String curSkelLine = "";              // The temp skel line read in this iteration
        String prevSkelLine = "";             // The line read last time
        int curTagSeq = 0;                    // Current tag sequence number
        int curTuId = 0;                      // Which Tu Number
        
        boolean done = false;     // Not done yet
        boolean tuOK = false;     // TU insertion not OK (yet)

        // Hmmm ... Maybe some future iteration should look for and "mark"
        // areas of the buffer that are "out-of-bounds" because they are comments?
        // <!-- ..... --> of CDATA <![CDATA[  ... ]]>
        // Then have sub deletion check to make sure we're not "out-of-bounds"
        
        // If the XLIFF document contains any sub elements, delete them
        int endSub = -1;
        int beginSub = -1;
        // Look repeatedly for the first </sub> tag in the buffer
        while ((endSub = skelBuf.indexOf("</sub>")) != -1) {
            // Find each </sub>'s nearest preceding <sub ...>
            beginSub = skelBuf.substring(0,endSub).lastIndexOf("<sub");
            if (beginSub == -1) {  // We're in deep doodoo--not wellformed! (or comments?)
                System.err.println("Unmatched sub tags encountered.");
                break;   // ... but keep going for now
            }
            skelBuf.delete(beginSub, endSub + "</sub>".length());
        }
        
        // The outer loop is driven by the temporary skeleton file, which includes (one per line)
        // tags from the original file that don't map to bx/ex/x tags in the XLIFF.
        for (;;) {
            prevSkelLine = curSkelLine;       // Remember what we read last time
            // Read a line from the temporary skeleton file:
            curSkelLine = tSkel.readLine();
            if (curSkelLine == null) {        // When we reach end of stream,
                break;                        //   quit looping
            }
            if (curSkelLine.startsWith("<tu id=")) {  // Refers to a translation unit
                // Substitute <trans-unit lt:tu-id /> in new skeleton
                tuOK = insertTu(curSkelLine); // 0 might be a dummy

                // We just inserted a TU. Delete the original text up to
                // the next tag (that we are just about to read)
                curSkelLine = tSkel.readLine();
                if (curSkelLine == null) {
                    break;                    // End of stream (prematurely!)
                }
                else {
                    deleteToTag(curSkelLine);     // Should be closed up now.
                }
            }
            else { // We didn't just insert a TU, so
                   // we can just keep walking the XLIFF.
                // First, though, see if we just ended a <trans-unit>. If we
                // did, insert a place holder that signals where to insert 
                // new targets that were added during the translation process.
                // *Then* seek to the </trans-unit> tag
                if (curSkelLine.startsWith("</trans-unit ")) {
                    tuOK = insertTu(curSkelLine); // 0 might be a dummy
                }
                
                seekToTag(curSkelLine);
            }
        }
        
        // Write the skeleton buffer to the skel stream
        for (int i = 0; i < skelBuf.length(); i++) {
        //for (int i = 0; i < xliffStreamPos; i++) {
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
        return;
    }
    
    /**
     * Passed a tag string (from the intermediate skel file), find the first occurrence of
     * that tag (beginning at the "current" position) in the skeleton Buffer (represented
     * by the value in xliffStreamPos).
     * Then, delete everything between xliffStreamPos and the beginning of the specified tag 
     * string.
     *
     * Upon exit, the xliffStreamPos varible should be at the position one space to the
     * right of the close (">") of the tag passed in as the tagString argument.
     * Note: The tag passed in should be an ending tag--probably </source> or
     * </target>. This method is called immediately after a TU placeholder element
     * is inserted into the skeleton stream.
     * @param tagString The tag string read from the intermediate skeleton stream
     */
    private void deleteToTag(String tagString) {
        // Get tag prefix and sequence number from the string passed in.
        String tagPrefix = null;
        
        // Typical tag: </source seq='29'>
        String tagPatt = "^([^ ]+) seq='(\\d+)'>";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(tagString);
        if (tm.find()) {
            tagPrefix = tm.group(1);
            curTagSeq = Integer.parseInt(tm.group(2));  // For use in other methods?
        }
        
        // Where does the tag we're looking for start?
        int tagStartsAt = skelBuf.indexOf(tagPrefix,xliffStreamPos);
        int endOfTag = -1;                     // Future xliffStreamPos
        if (tagStartsAt > -1) {                // We found the tag
            // Find the tag end:
            endOfTag = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (endOfTag > -1) {
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                endOfTag++;
            }
            else {
                System.err.println("XliffSkeletonMerger found beginning of tag to"
                    + " delete to, but the end is missing!");
                return; // Can't delete (Serious!!)
            }
        }
        else {
            System.err.println("XliffSkeletonMerger cannot find tag to"
                + " delete to!");
            return; // Can't delete
        }

        // Before deleting, save the tag we're deleting "to" in what will (to a
        // future method call) be the previous Tag.
        previousTag = skelBuf.substring(tagStartsAt,endOfTag);           // In case anyone cares

        // Now delete everything we were asked to delete
        skelBuf.delete(xliffStreamPos, tagStartsAt);
        
        // Decrement the tag end position by the number of characters we deleted
        endOfTag-= (tagStartsAt-xliffStreamPos);
        
        // Now set the XLIFF Stream Position for next time.
        xliffStreamPos = endOfTag;
        prevTagSeq = curTagSeq;            // For seeking ...
        return;
    }

    /**
     * Passed a tu tag string (from the intermediate skel file), insert a
     * tu place holder of the format <lt:tu id='[id]'/> in the current
     * skel position. 
     * @param tuTagString The line from the intermediate skeleton file that
     *        represents the TU holder we are to insert
     * @return The status of the operation: true=succeeded, false=failed
     */
    private boolean insertTu(String tuTagString) {
        String isTarget = "";           // Is this target (or source)?
        String xmlLang = "";            // xml:lang=what? (if target)
        String placeHolder = "";
        
        if (tuTagString.startsWith("</trans-unit ")) {
            placeHolder = "<lt:tu id='" + curTuID 
                + "' istarget='wildcard' xml:lang='remaining'/>";
            
            skelBuf.insert(xliffStreamPos, placeHolder);  // Insert it now!
            xliffStreamPos+=placeHolder.length();         // Increment cur position in stream

            return true;
        }
        
        curTuID = "";
        // Get the TU ID (number, UUID, etc.) from the intermediate skeleton file
        String tagPatt = "^<tu id='([^\']+)' istarget='([^\']+)' xml:lang='([^\']+)'>$";
        Pattern tp = Pattern.compile(tagPatt);
        Matcher tm = tp.matcher(tuTagString);
        if (tm.find()) {
            curTuID = tm.group(1);
            isTarget = tm.group(2);
            xmlLang = tm.group(3);
        }
        else {
            System.err.println("XliffSkeletonMerger cannot parse tu tag from tskeleton.");
            return false;   // I guess we'll have to skip this TU; we can't figure where
                            // to insert it (sigh)
        }
        
        // The empty element we will insert
        placeHolder = "<lt:tu id='" + curTuID + "' istarget='" + isTarget + 
            "' xml:lang='" + xmlLang + "'/>";
        skelBuf.insert(xliffStreamPos, placeHolder);  // Insert it now!
        xliffStreamPos+=placeHolder.length();         // Increment cur position in stream
        
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
     * checks to see if the previous tag in the original XLIFF was an empty tag. If it was
     * then this method just exits ...
     * Note: This could be either a beginning or ending tag.
     * @param tagString The tag string read from the intermediate skeleton stream
     */
    private void seekToTag(String tagString) {
        // Find more about this tag from the intermediate skeleton file
        String tagPrefix = null;
        String tagPatt = "^([^ ]+) seq='(\\d+)'>";
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
        // Hmmm... I don't think this is ever true ...
        if (isEndTag && previousTag.endsWith("/>") && (curTagSeq == (prevTagSeq+1))) {
            return;      // We're done
        }

        // Where does the tag we're looking for start?
        int tagStartsAt = skelBuf.indexOf(tagPrefix,xliffStreamPos);
        int endOfTag = -1;                     // Future xliffStreamPos
        if (tagStartsAt > -1) {                // We found the tag
            // Find the tag end:
            endOfTag = skelBuf.indexOf(">", tagStartsAt + tagPrefix.length());
            if (endOfTag > -1) {
                // Add one to the position, so that it will be one position
                // to the right of the end of the tag.
                endOfTag++;
            }
            else {
                return; // Can't find. Return--maybe future call will succeed?
            }
        }
        else {
            return; // Ditto
        }

        // Before seeking, save the tag we're seeking past in what will (to a
        // future method call) be the previous Tag.
        previousTag = skelBuf.substring(tagStartsAt,endOfTag); 
        prevTagSeq = curTagSeq;             // Save its sequence number as well

        // Then move past the tag.
        xliffStreamPos = endOfTag;
        return;
    }
}
