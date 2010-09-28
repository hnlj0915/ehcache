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

package net.sf.ehcache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.ehcache.search.Attribute;
import net.sf.ehcache.search.Direction;
import net.sf.ehcache.search.Query;
import net.sf.ehcache.search.Results;
import net.sf.ehcache.search.SearchException;
import net.sf.ehcache.search.aggregator.Aggregator;
import net.sf.ehcache.search.aggregator.AggregatorException;
import net.sf.ehcache.search.expression.Criteria;

/**
 * Query builder implementation. Instances are bound to a specific cache
 * 
 * @author teck
 */
class CacheQuery implements Query, ImmutableQuery {

    private volatile boolean frozen;
    private volatile boolean includeKeys;
    private volatile boolean includeValues;
    private volatile int maxResults = -1;

    private final List<Ordering> orderings = Collections.synchronizedList(new ArrayList<Ordering>());
    private final List<Attribute<?>> includedAttributes = Collections.synchronizedList(new ArrayList<Attribute<?>>());
    private final List<Criteria> criteria = Collections.synchronizedList(new ArrayList<Criteria>());
    private final Cache cache;

    /**
     * Create a new builder instance
     * 
     * @param cache
     */
    public CacheQuery(Cache cache) {
        this.cache = cache;
    }

    /**
     * {@inheritDoc}
     */
    public Query includeKeys() {
        checkFrozen();
        this.includeKeys = true;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query includeValues() {
        checkFrozen();
        this.includeValues = true;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query includeAttribute(Attribute<?>... attributes) {
        checkFrozen();

        for (Attribute<?> attribute : attributes) {
            this.includedAttributes.add(attribute);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query includeAggregator(Aggregator aggregator, Attribute<?> attribute) throws SearchException, AggregatorException {
        checkFrozen();

        // we should check the aggregator for attributes, keys and values
        // XXX: getClass() is not right. Attributes don't currently know their specifc type either
        if (!aggregator.supports(attribute.getClass())) {
            throw new AggregatorException("Attributes of type " + attribute.getClass().getName() + " is not supported");
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query addOrder(Attribute<?> attribute, Direction direction) {
        checkFrozen();
        this.orderings.add(new Ordering(attribute, direction));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query maxResults(int maxResults) {
        checkFrozen();
        this.maxResults = maxResults;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Query add(Criteria criteria) {
        checkFrozen();
        this.criteria.add(criteria);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Results execute() throws SearchException {
        return cache.executeQuery(snapshot());
    }

    /**
     * {@inheritDoc}
     */
    public Query end() {
        frozen = true;
        return this;
    }

    private ImmutableQuery snapshot() {
        if (frozen) {
            return this;
        }

        return new ImmutableQuery() {
            // XXX: fill me out as needed
        };
    }

    private void checkFrozen() {
        if (frozen) {
            throw new SearchException("Query is frozen and cannot be mutated");
        }
    }

    /**
     * An attribute/direction pair
     */
    private static class Ordering {

        private final Attribute<?> attribute;
        private final Direction direction;

        public Ordering(Attribute<?> attribute, Direction direction) {
            this.attribute = attribute;
            this.direction = direction;
        }

    }

}