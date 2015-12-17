/*
 * PdfPasswordException.java
 *
 * Copyright (C) 2007. Lingotek, Inc. All rights reserved.
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
 * Exception thrown when unable to decode encrypted PDF file because of
 * missing/incorrect password.
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public class PdfPasswordException extends ConversionException {
    
    /**
     * No-argument version of PdfPasswordException constructor
     */
    public PdfPasswordException() { }
    
    /**
     * PdfPasswordException constructor that takes a message
     * string as an argument
     * 
     * @param msg Informational message about the exception.
     */
    public PdfPasswordException(String msg) {
        super(msg);
    }
    
}
