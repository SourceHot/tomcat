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
package org.apache.catalina.startup;


import java.util.Enumeration;


/**
 * Abstraction of the set of users defined by the operating system on the
 * current server platform.
 *
 * @author Craig R. McClanahan
 */
public interface UserDatabase {


    // ----------------------------------------------------------- Properties


    /**
     * @return the UserConfig listener with which we are associated.
     */
    UserConfig getUserConfig();


    /**
     * Set the UserConfig listener with which we are associated.
     *
     * @param userConfig The new UserConfig listener
     */
    void setUserConfig(UserConfig userConfig);


    // ------------------------------------------------------- Public Methods


    /**
     * @param user User for which a home directory should be retrieved
     * @return an absolute pathname to the home directory for the specified user.
     */
    String getHome(String user);


    /**
     * @return an enumeration of the usernames defined on this server.
     */
    Enumeration<String> getUsers();


}
