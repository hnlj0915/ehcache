/**
 *  Copyright 2003-2009 Terracotta, Inc.
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
package net.sf.ehcache.hibernate.strategy;

import java.io.Serializable;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.access.SoftLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Chris Dennis
 */
public class EhCacheReadWriteCache extends AbstractEhCacheConcurrencyStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(EhCacheReadWriteCache.class);
    
    private final UUID uuid = UUID.randomUUID();
    private final AtomicLong nextLockId = new AtomicLong();

    /**
     * {@inheritDoc}
     */
    public Object get(Object key, long txTimestamp) throws CacheException {
        Lockable item = (Lockable) cache.get(key);

        boolean readable = item != null && item.isReadable(txTimestamp);
        if (readable) {
            return item.getValue();
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean put(Object key, Object value, long txTimestamp, Object version, Comparator versionComparator, boolean minimalPut)
            throws CacheException {

        cache.lock(key);
        try {
            Lockable item = (Lockable) cache.get(key);
            boolean writeable = item == null || item.isWriteable(txTimestamp, version, versionComparator);
            if (writeable) {
                cache.put(key, new Item(value, version, cache.nextTimestamp()));
                return true;
            } else {
                return false;
            }
        } finally {
            cache.unlock(key);
        }
    }

    /**
     * Soft-locks the associated mapping prior to updating/inserting a new value.
     */
    public SoftLock lock(Object key, Object version) throws CacheException {
        cache.lock(key);
        try {
            Lockable item = (Lockable) cache.get(key);
            long timeout = cache.nextTimestamp() + cache.getTimeout();
            final Lock lock = (item == null) ? createSoftLock(timeout, version) : item.lock(timeout);
            cache.update(key, lock);
            return lock;
        } finally {
            cache.unlock(key);
        }
    }

    /**
     * Soft-unlocks the associated mapping.
     */
    public void release(Object key, SoftLock lock) throws CacheException {
        cache.lock(key);
        try {
            Lockable item = (Lockable) cache.get(key);

            if ((item != null) && item.isUnlockable(lock)) {
                decrementLock(key, (Lock) item);
            } else {
                handleLockExpiry(key);
            }
        } finally {
            cache.unlock(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean afterUpdate(Object key, Object value, Object version, SoftLock softlock) throws CacheException {
        cache.lock(key);
        try {
            Lockable item = (Lockable) cache.get(key);

            if (item != null && item.isUnlockable(softlock)) {
                Lock lock = (Lock) item;
                if (lock.wasLockedConcurrently()) {
                    decrementLock(key, lock);
                    return false;
                } else {
                    cache.update(key, new Item(value, version, cache.nextTimestamp()));
                    return true;
                }
            } else {
                handleLockExpiry(key);
                return false;
            }
        } finally {
            cache.unlock(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean afterInsert(Object key, Object value, Object version) throws CacheException {
        cache.lock(key);
        try {
            Lockable item = (Lockable) cache.get(key);
            if (item == null) {
                cache.update(key, new Item(value, version, cache.nextTimestamp()));
                return true;
            } else {
                return false;
            }
        } finally {
            cache.unlock(key);
        }
    }

    /**
     * A No-Op, since we are an asynchronous cache concurrency strategy.
     */
    public void evict(Object key) throws CacheException {
        // no-op - since we are not transactional
    }

    /**
     * A No-Op, since we are an asynchronous cache concurrency strategy.
     */
    public boolean update(Object key, Object value, Object currentVersion, Object previousVersion) throws CacheException {
        return false;
    }

    /**
     * A No-Op, since we are an asynchronous cache concurrency strategy.
     */
    public boolean insert(Object key, Object value, Object currentVersion) throws CacheException {
        return false;
    }

    private Lock createSoftLock(long timeout, Object version) {
        return new Lock(timeout, uuid, nextLockId.getAndIncrement(), version);
    }
    
    private void decrementLock(Object key, Lock lock) {
        lock.unlock(cache.nextTimestamp());
        cache.update(key, lock);
    }

    private void handleLockExpiry(Object key) {
        long ts = cache.nextTimestamp() + cache.getTimeout();
        // create new lock that times out immediately
        Lock lock = createSoftLock(ts, null);
        lock.unlock(ts);
        cache.update(key, lock);
    }

    /**
     * Interface type implemented by all wrapper objects in the cache.
     */
    private static interface Lockable {

        public boolean isReadable(long txTimestamp);

        public boolean isWriteable(long txTimestamp, Object version, Comparator versionComparator);

        public Object getValue();

        public boolean isUnlockable(SoftLock lock);

        public Lock lock(long timeout);
    }

    /**
     * Wrapper type representing unlocked items.
     */
    private final class Item implements Serializable, Lockable {

        private static final long serialVersionUID = 1L;
        private final Object value;
        private final Object version;
        private final long timestamp;

        private Item(Object value, Object version, long timestamp) {
            this.value = value;
            this.version = version;
            this.timestamp = timestamp;
        }

        public boolean isReadable(long txTimestamp) {
            return txTimestamp > timestamp;
        }

        public boolean isWriteable(long txTimestamp, Object newVersion, Comparator versionComparator) {
            return version != null && versionComparator.compare(version, newVersion) < 0;
        }

        public Object getValue() {
            return value;
        }

        public boolean isUnlockable(SoftLock lock) {
            return false;
        }

        public Lock lock(long timeout) {
            return createSoftLock(timeout, version);
        }
    }

    /**
     * Wrapper type representing locked items.
     */
    private final static class Lock implements Serializable, Lockable, SoftLock {

        private static final long serialVersionUID = 2L;

        private final UUID sourceUuid;
        private final long lockId;
        private final Object version;
        
        private long timeout;
        private boolean concurrent;
        private int multiplicity;
        private long unlockTimestamp;

        Lock(long timeout, UUID sourceUuid, long lockId, Object version) {
            this.timeout = timeout;
            this.lockId = lockId;
            this.version = version;
            this.sourceUuid = sourceUuid;
        }

        public boolean isReadable(long txTimestamp) {
            return false;
        }

        public boolean isWriteable(long txTimestamp, Object newVersion, Comparator versionComparator) {
            if (txTimestamp > timeout) {
                // if timedout then allow write
                return true;
            }
            if (multiplicity > 0) {
                // if still locked then disallow write
                return false;
            }
            return version == null ? txTimestamp > unlockTimestamp : versionComparator.compare(version, newVersion) < 0;
        }

        public Object getValue() {
            return null;
        }

        public boolean isUnlockable(SoftLock lock) {
            return equals(lock);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Lock) {
                return (lockId == ((Lock) o).lockId) && sourceUuid.equals(((Lock) o).sourceUuid);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hash = (sourceUuid != null ? sourceUuid.hashCode() : 0);
            int temp = (int) lockId;
            for (int i = 1; i < Long.SIZE / Integer.SIZE; i++) {
                temp ^= (lockId >>> (i * Integer.SIZE));
            }
            return hash + temp;
        }

        private boolean wasLockedConcurrently() {
            return concurrent;
        }

        public Lock lock(long timeout) {
            concurrent = true;
            multiplicity++;
            this.timeout = timeout;
            return this;
        }

        private void unlock(long timestamp) {
            if (--multiplicity == 0) {
                unlockTimestamp = timestamp;
            }
        }
    }
}
