/**
 * OdfStateObject.java
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
 * Class to preserve SAX parser state between calls for parsing
 * content.xml and styles.xml
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class OdfStateObject {
    // All of the above share a common RID numbering space. The following is used each
    // time a new bx is encountered.
    private int nextAvailRid = 1;           // All of the above use the same numbering space
    
    // Counter for the unique identifiers for the bx/ex formatting tags:
    private int bxExXId = 1;
  
    private int curTagNum = 0;              // For skeleton

    /**
     * Get the bx/ex/x id counter
     */
    public int getBxExXId() {
        return bxExXId;
    }

    /**
     * Get the current skeleton tag number
     */
    public int getCurSkelTagNum() {
        return curTagNum;
    }

    /**
     * Get the next available rid.
     */
    public int getNextAvailRid() {
        return nextAvailRid;
    }

    /**
     * Set the bx/ex/x id counter
     */
    public void setBxExXId(int bxExXId) {
        this.bxExXId = bxExXId;
    }

    /**
     * Set the current skeleton tag number
     */
    public void setCurSkelTagNum(int curTagNum) {
        this.curTagNum = curTagNum;
    }

    /**
     * Set the next available rid.
     */
    public void setNextAvailRid(int nextAvailRid) {
        this.nextAvailRid = nextAvailRid;
    }
    
} // End OdfStateObject
