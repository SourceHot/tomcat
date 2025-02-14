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
 * and represents a reference to a (external) class.
 *
 * @see Constant
 */
public final class ConstantClass extends Constant {

    private final int nameIndex; // Identical to ConstantString except for the name


    /**
     * Constructs an instance from file data.
     *
     * @param dataInput Input stream
     * @throws IOException if an I/O error occurs reading from the given {@code dataInput}.
     */
    ConstantClass(final DataInput dataInput) throws IOException {
        super(Const.CONSTANT_Class);
        this.nameIndex = dataInput.readUnsignedShort();
    }


    /**
     * @return Name index in constant pool of class name.
     */
    public int getNameIndex() {
        return nameIndex;
    }
}
