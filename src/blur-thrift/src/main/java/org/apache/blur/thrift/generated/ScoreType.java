/**
 * Autogenerated by Thrift Compiler (0.9.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package org.apache.blur.thrift.generated;

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




import java.util.Map;
import java.util.HashMap;

import org.apache.blur.thirdparty.thrift_0_9_0.TEnum;

/**
 * The scoring type used during a SuperQuery to score multi Record hits within a ColumnFamily.<br/><br/>
 * SUPER - During a multi Record match, a calculation of the best match Record plus how often it occurs within the match Row produces the score that is used in the scoring of the SuperQuery.<br/><br/>
 * AGGREGATE - During a multi Record match, the aggregate score of all the Records within a ColumnFamily is used in the scoring of the SuperQuery.<br/><br/>
 * BEST - During a multi Record match, the best score of all the Records within a ColumnFamily is used in the scoring of the SuperQuery.<br/><br/>
 * CONSTANT - A constant score of 1 is used in the scoring of the SuperQuery.<br/>
 */
public enum ScoreType implements org.apache.blur.thirdparty.thrift_0_9_0.TEnum {
  SUPER(0),
  AGGREGATE(1),
  BEST(2),
  CONSTANT(3);

  private final int value;

  private ScoreType(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static ScoreType findByValue(int value) { 
    switch (value) {
      case 0:
        return SUPER;
      case 1:
        return AGGREGATE;
      case 2:
        return BEST;
      case 3:
        return CONSTANT;
      default:
        return null;
    }
  }
}
