/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.security.auth.message.module;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.MessagePolicy;
import jakarta.security.auth.message.ServerAuth;

import javax.security.auth.callback.CallbackHandler;
import java.util.Map;

public interface ServerAuthModule extends ServerAuth {

    @SuppressWarnings("rawtypes")
        // JASPIC API uses raw types
    void initialize(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
                    CallbackHandler handler, Map options) throws AuthException;

    @SuppressWarnings("rawtypes")
        // JASPIC API uses raw types
    Class[] getSupportedMessageTypes();
}
