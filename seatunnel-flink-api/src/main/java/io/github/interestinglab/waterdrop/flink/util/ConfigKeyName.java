/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.interestinglab.waterdrop.flink.util;

public class ConfigKeyName {
    public final static String TIME_CHARACTERISTIC = "execution.time-characteristic";
    public final static String BUFFER_TIMEOUT_MILLIS = "execution.buffer.timeout";
    public final static String PARALLELISM = "execution.parallelism";
    public final static String MAX_PARALLELISM = "execution.max-parallelism";
    public final static String CHECKPOINT_INTERVAL = "execution.checkpoint.interval";
    public final static String CHECKPOINT_MODE = "execution.checkpoint.mode";
    public final static String CHECKPOINT_TIMEOUT = "execution.checkpoint.timeout";
    public final static String CHECKPOINT_DATA_URI = "execution.checkpoint.data-uri";
    public final static String MAX_CONCURRENT_CHECKPOINTS = "execution.max-concurrent-checkpoints";
    public final static String CHECKPOINT_CLEANUP_MODE = "execution.checkpoint.cleanup-mode";
    public final static String MIN_PAUSE_BETWEEN_CHECKPOINTS = "execution.checkpoint.min-pause";
    public final static String FAIL_ON_CHECKPOINTING_ERRORS = "execution.checkpoint.fail-on-error";
    public final static String RESTART_STRATEGY = "execution.restart.strategy";
    public final static String RESTART_ATTEMPTS = "execution.restart.attempts";
    public final static String RESTART_DELAY_BETWEEN_ATTEMPTS = "execution.restart.delayBetweenAttempts";
    public final static String RESTART_FAILURE_INTERVAL = "execution.restart.failureInterval";
    public final static String RESTART_FAILURE_RATE = "execution.restart.failureRate";
    public final static String RESTART_DELAY_INTERVAL = "execution.restart.delayInterval";
    public final static String MAX_STATE_RETENTION_TIME = "execution.query.state.max-retention";
    public final static String MIN_STATE_RETENTION_TIME = "execution.query.state.min-retention";
    public final static String STATE_BACKEND = "execution.state.backend";

}
