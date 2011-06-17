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

package net.sf.ehcache.pool;

/**
 * PoolAccessors are used by stores to tell the pools about their resource consumption
 *
 * @author Ludovic Orban
 */
public interface PoolAccessor {

    /**
     * Add an element to the pool.
     *
     * @param key the key of the element
     * @param value the value of the element
     * @param container the element-container object
     * @param force true if the pool should accept adding the element, even if it's out of resources
     * @return how many bytes have been added to the pool or -1 if add failed.
     */
    long add(Object key, Object value, Object container, boolean force);

    /**
     * Check if there is enough room in the pool to add an element without provoking any eviction
     * @param key the key of the element
     * @param value the value of the element
     * @param container the element-container object
     * @return true if there is enough room left
     */
    boolean canAddWithoutEvicting(Object key, Object value, Object container);

    /**
     * Delete an element from the pool.
     *
     * @param key the key of the element
     * @param value the value of the element
     * @param container the element-container object
     * @return how many bytes have been freed from the pool.
     */
    long delete(Object key, Object value, Object container);

    /**
     * Replace an element's component from the pool
     *
     * @param role which element component to replace
     * @param current the currently replaced object
     * @param replacement the replacement object
     * @param force true if the pool should accept replacing the element, even if it's out of resources
     * @return how many bytes have been freed from the pool, may be negative. Long.MAX_VALUE is returned if replace failed.
     */
    long replace(Role role, Object current, Object replacement, boolean force);

    /**
     * Return how many bytes this accessor consumes from the pool.
     *
     * @return how many bytes this accessor consumes from the pool.
     */
    long getSize();

    /**
     * unlink this PoolAccessor from its pool.
     */
    void unlink();

    /**
     * Free resources used by this accessor.
     */
    void clear();

}
