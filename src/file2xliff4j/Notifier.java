/*
 * Notifier.java
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
 * Interface to send notifications. If an implementation of this interface
 * is passed to the converters, then may (over time) be modified to call
 * the Notifier's sendNotification method.
 *
 * <p>An implementation will typically provide a customized implementation
 * of the interface, which will be passed to the converter. 
 *
 * @author Weldon Whipple &lt;weldon@lingotek.com&gt;
 */
public interface Notifier {
    
    /** An informational notification--used as third argument to sendNotification */
    public static final int INFORMATIONAL  = 0;   
    
    /** A warning notification--used as third argument to sendNotification */
    public static final int WARNING = 1;   

    /** An error notification--used as third argument to sendNotification */
    public static final int ERROR = 2;   

    /**
     * Send a notification
     * @param noticeID The identifier of this notice.
     * @param origin The origin (originator) of the notice.
     * @param severity The severity of the notice.
     * @param notice The text of the message to send.
     * @throws file2xliff4j.ConversionNotificationException
     *         If unable to send the notification
     */
    public void sendNotification(String noticeID, String origin, int severity, 
            String notice) throws ConversionNotificationException;
}
