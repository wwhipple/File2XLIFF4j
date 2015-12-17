/*
 * MifCharsetProvider.java
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

import java.nio.charset.spi.*;
import java.nio.charset.*;
import java.util.*;

/**
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class MifCharsetProvider extends CharsetProvider {
    
    // The name of the Charset we provide
    private static final String CHARSET_NAME = "X-MIF-FRAMEROMAN";
    
    String[] aliases = {"X-MIFCHARSET", "X-MIF_CHARSET", "MIF_CHARSET", "MIF", 
            "X-MIF", "MIF-FRAMEROMAN"};

    // Handle to the Charset object
    private Charset mifCharSet = null;
    
    
    /**
     * This is the required zero-argument constructor of this concrete
     * subclass of CharsetProvider
     */
    public MifCharsetProvider() {
        this.mifCharSet = new MifFrameRomanCharset(CHARSET_NAME,aliases);
    }
    
    /**
     * Passed a name for the MIF character set, return the MifCharSet
     * @param name The name of the requested charset--either canonical or an alias.
     * @return The MifCharSet (or null if no match)
     */
    public Charset charsetForName(String name) {
        if (name.equalsIgnoreCase(CHARSET_NAME)) {
            return new MifFrameRomanCharset(CHARSET_NAME, aliases);
        }

        // Check our aliases
        for (String csName : aliases) {
            if (name.equalsIgnoreCase(csName)) {
                return new MifFrameRomanCharset(CHARSET_NAME, aliases);
            }
        }
        
        return null;
    }

    /**
     * Method to return an Iterator over the charsets supported by "this 
     * provider" (i.e., by us).
     * <p><i>Note:</i> Since this "implementation" supports only one character
     * set, thie iterator iterates only through that one set.
     * @return The Charset Iterator
     */ 
    public Iterator<Charset> charsets() {
        Collection<Charset> csCollection = new ArrayList<Charset>();
        
        // Add the MifCharSet to the collection
        csCollection.add(Charset.forName("X-MIF"));
        
        // Return an iterator
        return csCollection.iterator();
    }
}
