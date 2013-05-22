package org.apache.blur.server;

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
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.blur.log.Log;
import org.apache.blur.log.LogFactory;
import org.apache.hadoop.io.IOUtils;
import org.apache.thrift.server.ServerContext;

/**
 * The thrift session that will hold index reader references to maintain across
 * query and fetch calls. Since there is a fixed size thread pool issuing calls
 * that involve the _threadsToContext map where Thread is the key we don't need
 * to clear or reset threads.
 */
public class ShardServerContext implements ServerContext {

  private static final Log LOG = LogFactory.getLog(ShardServerContext.class);

  private final static Map<Thread, ShardServerContext> _threadsToContext = new ConcurrentHashMap<Thread, ShardServerContext>();
  private final Map<String, IndexSearcherClosable> _indexSearcherMap = new HashMap<String, IndexSearcherClosable>();

  /**
   * Registers the {@link ShardServerContext} for this thread.
   * 
   * @param context
   *          the {@link ShardServerContext}.
   */
  public static void registerContextForCall(ShardServerContext context) {
    _threadsToContext.put(Thread.currentThread(), context);
  }

  /**
   * Gets the {@link ShardServerContext} for this {@link Thread}.
   * 
   * @return the {@link ShardServerContext}.
   */
  public static ShardServerContext getShardServerContext() {
    return _threadsToContext.get(Thread.currentThread());
  }

  /**
   * Resets the context, this closes and releases the index readers.
   */
  public static void resetSearchers() {
    ShardServerContext shardServerContext = getShardServerContext();
    if (shardServerContext != null) {
      shardServerContext.reset();
    }
  }

  /**
   * Closes this context, this happens when the client closes it's connect to
   * the server.
   */
  public void close() {
    reset();
  }

  /**
   * Resets the {@link ShardServerContext} by closing the searchers.
   */
  public void reset() {
    Collection<IndexSearcherClosable> values = _indexSearcherMap.values();
    for (IndexSearcherClosable indexSearcherClosable : values) {
      LOG.info("Closing [{0}]", indexSearcherClosable);
      IOUtils.cleanup(LOG, indexSearcherClosable);
    }
    _indexSearcherMap.clear();
  }

  /**
   * Gets the cached {@link IndexSearcherClosable} (if any) for the given table
   * and shard.
   * 
   * @param table
   *          the stable name.
   * @param shard
   *          the shard name.
   * @return the {@link IndexSearcherClosable} or null if not present.
   */
  public IndexSearcherClosable getIndexSearcherClosable(String table, String shard) {
    IndexSearcherClosable indexSearcherClosable = _indexSearcherMap.get(getKey(table, shard));
    if (indexSearcherClosable != null) {
      LOG.info("Using cached searcher [{0}] for table [{1}] shard [{2}]", indexSearcherClosable, table, shard);
    }
    return indexSearcherClosable;
  }

  /**
   * Sets the index searcher for this {@link ShardServerContext} for the given
   * table and shard.
   * 
   * @param table
   *          the table name.
   * @param shard
   *          the shard name.
   * @param searcher
   *          the {@link IndexSearcherClosable}.
   * @throws IOException
   */
  public void setIndexSearcherClosable(String table, String shard, IndexSearcherClosable searcher) throws IOException {
    IndexSearcherClosable indexSearcherClosable = _indexSearcherMap.put(getKey(table, shard), searcher);
    if (indexSearcherClosable != null && searcher != indexSearcherClosable) {
      indexSearcherClosable.close();
    }
  }

  private String getKey(String table, String shard) {
    return table + "/" + shard;
  }

}