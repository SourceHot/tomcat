/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.ha.deploy;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This factory is used to read files and write files by splitting them up into
 * smaller messages. So that entire files don't have to be read into memory.
 * <BR>
 * The factory can be used as a reader or writer but not both at the same time.
 * When done reading or writing the factory will close the input or output
 * streams and mark the factory as closed. It is not possible to use it after
 * that. <BR>
 * To force a cleanup, call cleanup() from the calling object. <BR>
 * This class is not thread safe.
 *
 * @version 1.0
 */
public class FileMessageFactory {
    /**
     * The number of bytes that we read from file
     */
    public static final int READ_SIZE = 1024 * 10; //10kb
    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(FileMessageFactory.class);
    private static final StringManager sm = StringManager.getManager(FileMessageFactory.class);
    /**
     * The file that we are reading/writing
     */
    protected final File file;

    /**
     * True means that we are writing with this factory. False means that we are
     * reading with this factory
     */
    protected final boolean openForWrite;
    /**
     * Messages received out of order are held in the buffer until required. If
     * everything is worked as expected, messages will spend very little time in
     * the buffer.
     */
    protected final Map<Long, FileMessage> msgBuffer = new ConcurrentHashMap<>();
    /**
     * Once the factory is used, it cannot be reused.
     */
    protected boolean closed = false;
    /**
     * When openForWrite=false, the input stream is held by this variable
     */
    protected FileInputStream in;
    /**
     * When openForWrite=true, the output stream is held by this variable
     */
    protected FileOutputStream out;
    /**
     * The number of messages we have written
     */
    protected int nrOfMessagesProcessed = 0;
    /**
     * The total size of the file
     */
    protected long size = 0;
    /**
     * The total number of packets that we split this file into
     */
    protected long totalNrOfMessages = 0;
    /**
     * The number of the last message processed. Message IDs are 1 based.
     */
    protected AtomicLong lastMessageProcessed = new AtomicLong(0);
    /**
     * The bytes that we hold the data in, not thread safe.
     */
    protected byte[] data = new byte[READ_SIZE];

    /**
     * Flag that indicates if a thread is writing messages to disk. Access to
     * this flag must be synchronised.
     */
    protected boolean isWriting = false;

    /**
     * The time this instance was created. (in milliseconds)
     */
    protected long creationTime = 0;

    /**
     * The maximum valid time(in seconds) from creationTime.
     */
    protected int maxValidTime = -1;

    /**
     * Private constructor, either instantiates a factory to read or write. <BR>
     * When openForWrite==true, then a the file, f, will be created and an
     * output stream is opened to write to it. <BR>
     * When openForWrite==false, an input stream is opened, the file has to
     * exist.
     *
     * @param f            File - the file to be read/written
     * @param openForWrite boolean - true means we are writing to the file, false means
     *                     we are reading from the file
     * @throws FileNotFoundException -
     *                               if the file to be read doesn't exist
     * @throws IOException           -
     *                               if the system fails to open input/output streams to the file
     *                               or if it fails to create the file to be written to.
     */
    private FileMessageFactory(File f, boolean openForWrite)
            throws FileNotFoundException, IOException {
        this.file = f;
        this.openForWrite = openForWrite;
        if (log.isDebugEnabled()) {
            log.debug("open file " + f + " write " + openForWrite);
        }
        if (openForWrite) {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    throw new IOException(sm.getString("fileNewFail", file));
                }
            }
            out = new FileOutputStream(f);
        }
        else {
            size = file.length();
            totalNrOfMessages = (size / READ_SIZE) + 1;
            in = new FileInputStream(f);
        }//end if
        creationTime = System.currentTimeMillis();
    }

    /**
     * Creates a factory to read or write from a file. When opening for read,
     * the readMessage can be invoked, and when opening for write the
     * writeMessage can be invoked.
     *
     * @param f            File - the file to be read or written
     * @param openForWrite boolean - true, means we are writing to the file, false means
     *                     we are reading from it
     * @return FileMessageFactory
     * @throws FileNotFoundException -
     *                               if the file to be read doesn't exist
     * @throws IOException           -
     *                               if it fails to create the file that is to be written
     */
    public static FileMessageFactory getInstance(File f, boolean openForWrite)
            throws FileNotFoundException, IOException {
        return new FileMessageFactory(f, openForWrite);
    }

    /**
     * Reads file data into the file message and sets the size, totalLength,
     * totalNrOfMsgs and the message number <BR>
     * If EOF is reached, the factory returns null, and closes itself, otherwise
     * the same message is returned as was passed in. This makes sure that not
     * more memory is ever used. To remember, neither the file message or the
     * factory are thread safe. Don't hand off the message to one thread and
     * read the same with another.
     *
     * @param f FileMessage - the message to be populated with file data
     * @return FileMessage - returns the same message passed in as a parameter,
     * or null if EOF
     * @throws IllegalArgumentException -
     *                                  if the factory is for writing or is closed
     * @throws IOException              -
     *                                  if a file read exception occurs
     */
    public FileMessage readMessage(FileMessage f)
            throws IllegalArgumentException, IOException {
        checkState(false);
        int length = in.read(data);
        if (length == -1) {
            cleanup();
            return null;
        }
        else {
            f.setData(data, length);
            f.setTotalNrOfMsgs(totalNrOfMessages);
            f.setMessageNumber(++nrOfMessagesProcessed);
            return f;
        }//end if
    }

    /**
     * Writes a message to file. If (msg.getMessageNumber() ==
     * msg.getTotalNrOfMsgs()) the output stream will be closed after writing.
     *
     * @param msg FileMessage - message containing data to be written
     * @return returns true if the file is complete and outputstream is closed,
     * false otherwise.
     * @throws IllegalArgumentException -
     *                                  if the factory is opened for read or closed
     * @throws IOException              -
     *                                  if a file write error occurs
     */
    public boolean writeMessage(FileMessage msg)
            throws IllegalArgumentException, IOException {
        if (!openForWrite) {
            throw new IllegalArgumentException(sm.getString("fileMessageFactory.cannotWrite"));
        }
        if (log.isDebugEnabled()) {
            log.debug("Message " + msg + " data " + HexUtils.toHexString(msg.getData())
                    + " data length " + msg.getDataLength() + " out " + out);
        }

        if (msg.getMessageNumber() <= lastMessageProcessed.get()) {
            // Duplicate of message already processed
            log.warn(sm.getString("fileMessageFactory.duplicateMessage", msg.getContextName(), msg.getFileName(),
                    HexUtils.toHexString(msg.getData()), Integer.valueOf(msg.getDataLength())));
            return false;
        }

        FileMessage previous =
                msgBuffer.put(Long.valueOf(msg.getMessageNumber()), msg);
        if (previous != null) {
            // Duplicate of message not yet processed
            log.warn(sm.getString("fileMessageFactory.duplicateMessage", msg.getContextName(), msg.getFileName(),
                    HexUtils.toHexString(msg.getData()), Integer.valueOf(msg.getDataLength())));
            return false;
        }

        FileMessage next = null;
        synchronized (this) {
            if (!isWriting) {
                next = msgBuffer.get(Long.valueOf(lastMessageProcessed.get() + 1));
                if (next != null) {
                    isWriting = true;
                }
                else {
                    return false;
                }
            }
            else {
                return false;
            }
        }

        while (next != null) {
            out.write(next.getData(), 0, next.getDataLength());
            lastMessageProcessed.incrementAndGet();
            out.flush();
            if (next.getMessageNumber() == next.getTotalNrOfMsgs()) {
                out.close();
                cleanup();
                return true;
            }
            synchronized (this) {
                next =
                        msgBuffer.get(Long.valueOf(lastMessageProcessed.get() + 1));
                if (next == null) {
                    isWriting = false;
                }
            }
        }

        return false;
    }//writeMessage

    /**
     * Closes the factory, its streams and sets all its references to null
     */
    public void cleanup() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignore) {
            }
        }
        in = null;
        out = null;
        size = 0;
        closed = true;
        data = null;
        nrOfMessagesProcessed = 0;
        totalNrOfMessages = 0;
        msgBuffer.clear();
        lastMessageProcessed = null;
    }

    /**
     * Check to make sure the factory is able to perform the function it is
     * asked to do. Invoked by readMessage/writeMessage before those methods
     * proceed.
     *
     * @param openForWrite The value to check
     * @throws IllegalArgumentException if the state is not the expected one
     */
    protected void checkState(boolean openForWrite)
            throws IllegalArgumentException {
        if (this.openForWrite != openForWrite) {
            cleanup();
            if (openForWrite) {
                throw new IllegalArgumentException(sm.getString("fileMessageFactory.cannotWrite"));
            }
            else {
                throw new IllegalArgumentException(sm.getString("fileMessageFactory.cannotRead"));
            }
        }
        if (this.closed) {
            cleanup();
            throw new IllegalArgumentException(sm.getString("fileMessageFactory.closed"));
        }
    }

    public File getFile() {
        return file;
    }

    public boolean isValid() {
        if (maxValidTime > 0) {
            long timeNow = System.currentTimeMillis();
            int timeIdle = (int) ((timeNow - creationTime) / 1000L);
            if (timeIdle > maxValidTime) {
                cleanup();
                if (file.exists() && !file.delete()) {
                    log.warn(sm.getString("fileMessageFactory.deleteFail", file));
                }
                return false;
            }
        }
        return true;
    }

    public int getMaxValidTime() {
        return maxValidTime;
    }

    public void setMaxValidTime(int maxValidTime) {
        this.maxValidTime = maxValidTime;
    }

}
