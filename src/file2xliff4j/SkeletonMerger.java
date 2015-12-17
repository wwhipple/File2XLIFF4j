/*
 * SkeletonMerger.java
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
 * Interface for implementations that merge temporary skeleton files with
 * original native files and create the final skeleton file. (Substitute
 * stream for file in the above to describe the Java implementation.)
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public interface SkeletonMerger {

    /**
     * Merge a temporary skeleton input stream with the original native
     * input stream to yield the final skeleton that can be used to export
     * XLIFF documents and yield a document in the original native format, but
     * in a different language.
     * <p><i>Note:</i> Depending on the native format, the meaning of temporary
     * skeleton (if one exists) may vary. In fact, it is possible that for some
     * native formats, there is no temporary skeleton.
     * @param tSkelInStream The temporary or intermediate skeleton 
     * @param nativeInStream The original-format file's input stream (or a
     *        substitute)
     * @param skeletonOutStream Where the final skeleton is written to
     * @param encoding The character encoding of the native input stream, if
     *        applicable.
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream nativeInStream,
            OutputStream skeletonOutStream, 
            Charset encoding) throws IllegalArgumentException, IOException;

    /**
     * Variant of the merge method above that adds an argument that receives
     * the maximum TU depth.
     * @param tSkelInStream The temporary or intermediate skeleton 
     * @param nativeInStream The original-format file's input stream (or a
     *        substitute)
     * @param skeletonOutStream Where the final skeleton is written to
     * @param encoding The character encoding of the native input stream, if
     *        applicable.
     * @param maxTuDepth The maximum depth to which TUs are imbedded within
     *        other TUs.
     * @throws java.util.IllegalArgumentException
     *         if an argument is bogus or non-existent
     * @throws java.io.IOException
     *         if an I/O error occurs.
     */
    public void merge(InputStream tSkelInStream,
            InputStream nativeInStream,
            OutputStream skeletonOutStream, 
            Charset encoding,
            int maxTuDepth) throws IllegalArgumentException, IOException;

    /**
     * Set a format-specific property that might affect the way that the
     * merger process is conducted.
     * @param property The name of the property
     * @param value The value of the property
     * @throws file2xliff4j.ConversionException
     *         If the property or value can't be recognized.
     */
    public void setProperty(String property, Object value)
            throws ConversionException;
    
}
