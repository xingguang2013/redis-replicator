/*
 * Copyright 2016 leon chen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moilioncircle.redis.replicator.rdb;

import com.moilioncircle.redis.replicator.AbstractReplicator;
import com.moilioncircle.redis.replicator.event.Event;
import com.moilioncircle.redis.replicator.event.PostFullSyncEvent;
import com.moilioncircle.redis.replicator.event.PreFullSyncEvent;
import com.moilioncircle.redis.replicator.io.RedisInputStream;
import com.moilioncircle.redis.replicator.rdb.datatype.DB;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

import static com.moilioncircle.redis.replicator.Constants.*;

/**
 * Redis RDB format
 * rdb version 6
 * rdb version 7
 *
 * @author leon.chen
 *         [https://github.com/antirez/redis/blob/3.0/src/rdb.c]
 *         [https://github.com/sripathikrishnan/redis-rdb-tools/wiki/Redis-RDB-Dump-File-Format]
 * @since 2016/8/11
 */
public class RdbParser {

    protected final RedisInputStream in;
    protected RdbVisitor rdbVisitor;
    protected final AbstractReplicator replicator;
    protected static final Log logger = LogFactory.getLog(RdbParser.class);

    public RdbParser(RedisInputStream in, AbstractReplicator replicator) {
        this.in = in;
        this.replicator = replicator;
        this.rdbVisitor = this.replicator.getRdbVisitor();
    }

    /**
     * ----------------------------# RDB is a binary format. There are no new lines or spaces in the file.
     * 52 45 44 49 53              # Magic String "REDIS"
     * 30 30 30 33                 # RDB Version Number in big endian. In this case, version = 0003 = 3
     * ----------------------------
     * FE 00                       # FE = code that indicates database selector. db number = 00
     * ----------------------------# Key-Value pair starts
     * FD $unsigned int            # FD indicates "expiry time in seconds". After that, expiry time is read as a 4 byte unsigned int
     * $value-type                 # 1 byte flag indicating the type of value - set, map, sorted set etc.
     * $string-encoded-name         # The name, encoded as a redis string
     * $encoded-value              # The value. Encoding depends on $value-type
     * ----------------------------
     * FC $unsigned long           # FC indicates "expiry time in ms". After that, expiry time is read as a 8 byte unsigned long
     * $value-type                 # 1 byte flag indicating the type of value - set, map, sorted set etc.
     * $string-encoded-name         # The name, encoded as a redis string
     * $encoded-value              # The value. Encoding depends on $value-type
     * ----------------------------
     * $value-type                 # This name value pair doesn't have an expiry. $value_type guaranteed != to FD, FC, FE and FF
     * $string-encoded-name
     * $encoded-value
     * ----------------------------
     * FE $length-encoding         # Previous db ends, next db starts. Database number read using length encoding.
     * ----------------------------
     * ...                         # Key value pairs for this database, additonal database
     * FF                          ## End of RDB file indicator
     * 8 byte checksum             ## CRC 64 checksum of the entire file.
     *
     * @return read bytes
     * @throws IOException when read timeout
     */
    public long parse() throws IOException {
        /*
         * ----------------------------
         * 52 45 44 49 53              # Magic String "REDIS"
         * 30 30 30 33                 # RDB Version Number in big endian. In this case, version = 0003 = 3
         * ----------------------------
         */
        this.replicator.submitEvent(new PreFullSyncEvent());
        rdbVisitor.applyMagic(in);
        int version = rdbVisitor.applyVersion(in);
        DB db = null;
        long checksum;
        /*
         * rdb
         */
        loop:
        while (true) {
            int type = rdbVisitor.applyType(in);
            Event event = null;
            switch (type) {
                case RDB_OPCODE_EXPIRETIME:
                    event = rdbVisitor.applyExpireTime(in, db, version);
                    break;
                case RDB_OPCODE_EXPIRETIME_MS:
                    event = rdbVisitor.applyExpireTimeMs(in, db, version);
                    break;
                case RDB_OPCODE_AUX:
                    event = rdbVisitor.applyAux(in, version);
                    break;
                case RDB_OPCODE_RESIZEDB:
                    rdbVisitor.applyResizeDB(in, db, version);
                    break;
                case RDB_OPCODE_SELECTDB:
                    db = rdbVisitor.applySelectDB(in, version);
                    break;
                case RDB_OPCODE_EOF:
                    checksum = rdbVisitor.applyEof(in, version);
                    break loop;
                case RDB_TYPE_STRING:
                    event = rdbVisitor.applyString(in, db, version);
                    break;
                case RDB_TYPE_LIST:
                    event = rdbVisitor.applyList(in, db, version);
                    break;
                case RDB_TYPE_SET:
                    event = rdbVisitor.applySet(in, db, version);
                    break;
                case RDB_TYPE_ZSET:
                    event = rdbVisitor.applyZSet(in, db, version);
                    break;
                case RDB_TYPE_ZSET_2:
                    event = rdbVisitor.applyZSet2(in, db, version);
                    break;
                case RDB_TYPE_HASH:
                    event = rdbVisitor.applyHash(in, db, version);
                    break;
                case RDB_TYPE_HASH_ZIPMAP:
                    event = rdbVisitor.applyHashZipMap(in, db, version);
                    break;
                case RDB_TYPE_LIST_ZIPLIST:
                    event = rdbVisitor.applyListZipList(in, db, version);
                    break;
                case RDB_TYPE_SET_INTSET:
                    event = rdbVisitor.applySetIntSet(in, db, version);
                    break;
                case RDB_TYPE_ZSET_ZIPLIST:
                    event = rdbVisitor.applyZSetZipList(in, db, version);
                    break;
                case RDB_TYPE_HASH_ZIPLIST:
                    event = rdbVisitor.applyHashZipList(in, db, version);
                    break;
                case RDB_TYPE_LIST_QUICKLIST:
                    event = rdbVisitor.applyListQuickList(in, db, version);
                    break;
                case RDB_TYPE_MODULE:
                    event = rdbVisitor.applyModule(in, db, version);
                    break;
                default:
                    throw new AssertionError("unexpected value-type:" + type + ", please check your ModuleParser");
            }
            if (event == null) continue;
            if (replicator.verbose() && logger.isDebugEnabled()) logger.debug(event);
            this.replicator.submitEvent(event);
        }
        this.replicator.submitEvent(new PostFullSyncEvent(checksum));
        return in.total();
    }
}

