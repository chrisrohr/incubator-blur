/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.blur.manager.writer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.blur.server.IndexSearcherClosable;
import org.apache.lucene.index.IndexWriter;

public abstract class IndexAction {

  private AtomicInteger _writesWaiting;

  public abstract void doPreCommit(IndexSearcherClosable indexSearcher, IndexWriter writer) throws IOException;

  public abstract void doPostCommit(IndexWriter writer) throws IOException;

  public abstract void doPreRollback(IndexWriter writer) throws IOException;

  public abstract void doPostRollback(IndexWriter writer) throws IOException;

  public abstract void performMutate(IndexSearcherClosable searcher, IndexWriter writer) throws IOException;

  public void setWritesWaiting(AtomicInteger writesWaiting) {
    _writesWaiting = writesWaiting;
  }

  public boolean isWritersWaiting() {
    if (_writesWaiting.get() > 0) {
      return true;
    }
    return false;
  }
}
