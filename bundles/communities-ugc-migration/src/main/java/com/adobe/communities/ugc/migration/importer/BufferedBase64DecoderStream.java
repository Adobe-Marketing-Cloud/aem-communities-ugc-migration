/*************************************************************************
 *
 * ADOBE SYSTEMS INCORPORATED
 * Copyright 2015 Adobe Systems Incorporated
 * All Rights Reserved.
 *
 * NOTICE:  Adobe permits you to use, modify, and distribute this file in accordance with the
 * terms of the Adobe license agreement accompanying it.  If you have received this file from a
 * source other than Adobe, then your use, modification, or distribution of it requires the prior
 * written permission of Adobe.
 **************************************************************************/
package com.adobe.communities.ugc.migration.importer;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.binary.Base64;

public class BufferedBase64DecoderStream extends InputStream {
    final private int WRITE_BUFFER_SIZE = 1920; // 1920 chosen because it's the number of characters in a base64
// String
    // representing 1440 bytes, which was the chunk size used by the UGCExportHelper
    private byte[] readBuffer = null;
    private char[] writeBuffer;
    private int counter = 0;
    private InputStream inputStream;
    private Boolean endOfInput = false;

    public BufferedBase64DecoderStream(final long offset, final InputStream inputStream) throws IOException {
        inputStream.skip(offset);
        this.inputStream = inputStream;
        writeBuffer = new char[WRITE_BUFFER_SIZE];
        readToBuffer();
    }

    @Override
    public int read() throws IOException {
        if (readBuffer == null) { // will only happen if no data found in initial "readToBuffer" call in constructor
            return -1;
        }
        if (counter > readBuffer.length) {
            final int length = readToBuffer();
            if (length <= 0) {
                return -1;
            }
            counter = 0;
        }
        final int returnValue = readBuffer[counter];
        counter++;
        return returnValue;
    }

    private int readToBuffer() throws IOException {
        if (endOfInput) {
            return -1;
        }
        int pos = 0;
        for (; pos < WRITE_BUFFER_SIZE; pos++) {
            int c = inputStream.read();
            if (c == 34) { // "34" is a quotation mark, which ends the text field
                endOfInput = true;
                break;
            } else if (c == -1) {
                throw new IOException("Reached end of file before we encountered closing quotation mark");
            } else if (!( // represents all 65 valid values inside of a base64 string (64 characters and 1 byte pad)
            (c >= 97 && c <= 122) || (c >= 65 && c <= 90) || (c >= 48 && c <= 57) || c == 47 || c == 43 || c == 61)) {
                throw new IOException("Data was expected to be base64 - encountered invalid byte: " + c);
            }
            writeBuffer[pos] = (char) c;
        }
        if (pos < WRITE_BUFFER_SIZE) { // need to cut buffer down to size before decoding
            char[] buffer = new char[pos];
            System.arraycopy(writeBuffer, 0, buffer, 0, pos);
            writeBuffer = buffer;
        }
        readBuffer = Base64.decodeBase64(new String(writeBuffer));
        return readBuffer.length;
    }
}
