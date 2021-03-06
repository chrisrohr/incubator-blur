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
package org.apache.blur.analysis.type.spatial;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.blur.analysis.BaseFieldManager;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.Test;

public class SpatialTermQueryPrefixTreeStrategyFieldTypeDefinitionQuadPrefixTest extends BaseSpatialFieldTypeDefinitionTest {

  @Test
  public void testTermQueryPrefixTree() throws IOException, ParseException {
    runGisTypeTest();
  }

  protected void setupGisField(BaseFieldManager fieldManager) throws IOException {
    Map<String, String> props = new HashMap<String, String>();
    props.put(BaseSpatialFieldTypeDefinition.SPATIAL_PREFIX_TREE, BaseSpatialFieldTypeDefinition.QUAD_PREFIX_TREE);
    fieldManager.addColumnDefinition("fam", "geo", null, false,
        SpatialTermQueryPrefixTreeStrategyFieldTypeDefinition.NAME, false, props);
  }

}
