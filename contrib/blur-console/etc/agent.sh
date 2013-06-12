#!/bin/sh

#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#  
#  http://www.apache.org/licenses/LICENSE-2.0
#  
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.


bin=`dirname $0`
bin=`cd "$bin"; pwd`

export BLUR_AGENT_HOME="$bin"/..
export BLUR_AGENT_HOME_LIB=$BLUR_AGENT_HOME/lib

for f in $BLUR_AGENT_HOME_LIB/*.jar; do
  BLUR_AGENT_CLASSPATH="$BLUR_AGENT_CLASSPATH:$f"
done

cd $BLUR_AGENT_HOME/bin

$JAVA_HOME/bin/java -cp $BLUR_AGENT_CLASSPATH com.nearinfinity.agent.Agent $1
