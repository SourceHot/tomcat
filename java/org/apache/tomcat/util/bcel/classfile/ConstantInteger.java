/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tomcat.util.bcel.classfile;

import org.apache.tomcat.util.bcel.Const;

import java.io.DataInput;
import java.io.IOException;

/**
 * This class is derived from the abstract {@link Constant}
 * and represents a reference to an int object.
 *
 * @see Constant
 */
public final class ConstantInteger extends Constant {

    private final int bytes;


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException If an I/O occurs reading from the provided
     *                     InoutStream
     */
    ConstantInteger(final DataInput file) throws IOException {
        super(Const.CONSTANT_Integer);
        this.bytes = file.readInt();
    }


    /**
     * @return data, i.e., 4 bytes.
     */
    public int getBytes() {
        return bytes;
    }
}
