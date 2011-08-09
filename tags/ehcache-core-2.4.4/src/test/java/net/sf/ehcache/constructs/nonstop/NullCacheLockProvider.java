/**
 *  Copyright 2003-2010 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.constructs.nonstop;

import java.util.concurrent.TimeoutException;

import net.sf.ehcache.concurrent.CacheLockProvider;
import net.sf.ehcache.concurrent.Sync;

public class NullCacheLockProvider implements CacheLockProvider {

    public Sync[] getAndWriteLockAllSyncForKeys(long timeout, Object... keys) throws TimeoutException {
        return null;
    }

    public Sync[] getAndWriteLockAllSyncForKeys(Object... keys) {
        return null;
    }

    public Sync getSyncForKey(Object key) {
        return null;
    }

    public void unlockWriteLockForAllKeys(Object... keys) {
        //
    }

}
