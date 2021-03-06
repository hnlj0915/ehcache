/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2003 - 2004 Greg Luck.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by Greg Luck
 *       (http://sourceforge.net/users/gregluck) and contributors.
 *       See http://sourceforge.net/project/memberlist.php?group_id=93232
 *       for a list of contributors"
 *    Alternately, this acknowledgement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "EHCache" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For written
 *    permission, please contact Greg Luck (gregluck at users.sourceforge.net).
 *
 * 5. Products derived from this software may not be called "EHCache"
 *    nor may "EHCache" appear in their names without prior written
 *    permission of Greg Luck.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL GREG LUCK OR OTHER
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by contributors
 * individuals on behalf of the EHCache project.  For more
 * information on EHCache, please see <http://ehcache.sourceforge.net/>.
 *
 */
package net.sf.ehcache.store;

import net.sf.ehcache.Element;
import net.sf.ehcache.MemoryStoreTester;
import net.sf.ehcache.StopWatch;
import net.sf.ehcache.AbstractCacheTest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Test class for LfuMemoryStore
 * <p/>
 * @author <a href="ssuravarapu@users.sourceforge.net">Surya Suravarapu</a>
 * @version $Id: LfuMemoryStoreTest.java,v 1.1 2006/03/09 06:38:20 gregluck Exp $
 */
public class LfuMemoryStoreTest extends MemoryStoreTester {

    private static final Log LOG = LogFactory.getLog(LfuMemoryStoreTest.class.getName());

    /**
     * setup test
     */
    protected void setUp() throws Exception {
        super.setUp();
        createMemoryStore(MemoryStoreEvictionPolicy.LFU);
    }


    /**
     * Tests the put by reading the config file
     */
    public void testPutFromConfig() throws Exception {
        createMemoryStore(AbstractCacheTest.TEST_CONFIG_DIR + "ehcache-policy-test.xml", "sampleLFUCache1");
        putTest();
    }

    /**
     * Tests the put by reading the config file
     */
    public void testPutFromConfigZeroMemoryStore() throws Exception {
        createMemoryStore(AbstractCacheTest.TEST_CONFIG_DIR + "ehcache-policy-test.xml", "sampleLFUCache2");
        Element element = new Element("1", "value");
        store.put(element);
        assertNull(store.get("1"));
    }

    /**
     * Tests the remove() method by using the parameters specified in the config file
     */
    public void testRemoveFromConfig() throws Exception {
        createMemoryStore(AbstractCacheTest.TEST_CONFIG_DIR + "ehcache-policy-test.xml", "sampleLFUCache1");
        removeTest();
    }

    /**
     * Benchmark to test speed.
     * This takes a little longer for LFU than the others.
     * Takes about 7400ms
     */
    public void testBenchmarkPutGetSurya() throws Exception {
        benchmarkPutGetSuryaTest(9000);
    }

    /**
     * Tests the LFU policy
     */
    public void testLfuPolicy() throws Exception {
        createMemoryStore(MemoryStoreEvictionPolicy.LFU, 4);
        lfuPolicyTest();
    }

    /**
     * Tests the LFU policy by using the parameters specified in the config file
     */
    public void testLfuPolicyFromConfig() throws Exception {
        createMemoryStore(AbstractCacheTest.TEST_CONFIG_DIR + "ehcache-policy-test.xml", "sampleLFUCache1");
        lfuPolicyTest();
    }


    private void lfuPolicyTest() throws IOException {
        //Make sure that the store is empty to start with
        assertEquals(0, store.getSize());

        // Populate the store till the max limit
        Element element = new Element("key1", "value1");
        store.put(element);
        assertEquals(1, store.getSize());

        element = new Element("key2", "value2");
        store.put(element);
        assertEquals(2, store.getSize());

        element = new Element("key3", "value3");
        store.put(element);
        assertEquals(3, store.getSize());

        element = new Element("key4", "value4");
        store.put(element);
        assertEquals(4, store.getSize());

        //Now access the elements to boost the hits count
        store.get("key1");
        store.get("key1");
        store.get("key3");
        store.get("key3");
        store.get("key3");
        store.get("key4");

        //Create a new element and put in the store so as to force the policy
        element = new Element("key5", "value5");
        store.put(element);

        assertEquals(4, store.getSize());
        //The element with key "key2" is the LFU element so should be removed
        assertNull(store.get("key2"));

        // Make some more accesses
        store.get("key5");
        store.get("key5");

        // Insert another element to force the policy
        element = new Element("key6", "value6");
        store.put(element);
        assertEquals(4, store.getSize());
        assertNull(store.get("key4"));
    }

    /**
     * Benchmark to test speed.
     * new sampling LFU 417ms
     */
    public void testBenchmarkPutGetRemove() throws Exception {
        super.testBenchmarkPutGetRemove();
    }


    /**
     * Multi-thread read, put and removeAll test.
     * This checks for memory leaks
     * using the removeAll which was the known cause of memory leaks with LruMemoryStore in JCS
     * new sampling LFU has no leaks
     */
    public void testMemoryLeak() throws Exception {
        super.testMemoryLeak();
    }

    /**
     * Benchmark to test speed.
     * new sampling LFU 132ms
     */
    public void testBenchmarkPutGet() throws Exception {
        super.testBenchmarkPutGet();
    }

    /**
     * Tests how random the java.util.Map iteration is by measuring the differences in iterate order.
     * <p/>
     * If iterate was ordered in either insert or reverse insert order the mean difference would be 1.
     * Using Random gives a mean difference of 343.
     * The observed value is 175 but always 175 for a key set of 500 because it always iterates in the same order,
     * just not an obvious order.
     * <p/>
     * Conclusion: Unable to use the iterator as a pseudorandom selector.
     */
    public void testRandomnessOfIterator() {
        int mean = 0;
        int absoluteDifferences = 0;
        int lastReading = 0;
        Map map = new HashMap();
        for (int i = 1; i <= 500; i++) {
            mean += i;
            map.put("" + i, " ");
        }
        mean = mean / 500;
        for (Iterator iterator = map.keySet().iterator(); iterator.hasNext();) {
            String string = (String) iterator.next();
            int thisReading = Integer.parseInt(string);
            absoluteDifferences += Math.abs(lastReading - thisReading);
            lastReading = thisReading;
        }
        LOG.info("Mean difference through iteration: " + absoluteDifferences / 500);

        //Random selection without replacement
        Random random = new Random();
        while (map.size() != 0) {
            int thisReading = random.nextInt(501);
            Object o = map.remove("" + thisReading);
            if (o == null) {
                continue;
            }
            absoluteDifferences += Math.abs(lastReading - thisReading);
            lastReading = thisReading;
        }
        LOG.info("Mean difference with random selection without replacement : " + absoluteDifferences / 500);
        LOG.info("Mean of range 1 - 500 : " + mean);

    }


    /**
     * Check nothing breaks and that we get the right number of samples
     *
     * @throws IOException
     */
    public void testSampling() throws IOException {
        createMemoryStore(MemoryStoreEvictionPolicy.LFU, 1000);
        Element[] elements = null;
        for (int i = 0; i < 10; i++) {
            store.put(new Element("" + i, new Date()));
            elements = ((LfuMemoryStore) store).sampleElements(i + 1);
        }

        for (int i = 10; i < 2000; i++) {
            store.put(new Element("" + i, new Date()));
            elements = ((LfuMemoryStore) store).sampleElements(10);
            assertEquals(10, elements.length);
        }
    }


    /**
     * Check we get reasonable results for 2000 entries where entry 0 is accessed once increasing to entry 1999 accessed
     * 2000 times.
     * <p/>
     * 1 to 5000 population, with hit counts ranging from 1 to 500, not selecting lowest half. 5000 tests
     * S  Cost  No
     * 7        38 99.24% confidence
     * 8        27 99.46% confidence
     * 9        10
     * 10 11300 4  99.92% confidence
     * 12       2
     * 20 11428 0  99.99% confidence
     * <p/>
     * 1 to 5000 population, with hit counts ranging from 1 to 500, not selecting lowest quarter. 5000 tests
     * S        No
     * 10       291 94.18% confidence
     * 20       15
     * 30 11536 1 99.99% confidence
     * <p/>
     * For those with a statistical background the branch of stats which deals with this is hypothesis testing and
     * the Student's T distribution. The higher your sample the greater confidence you can have in a hypothesis, in
     * this case whether or not the "lowest" value lies in the bottom half or quarter of the distribution. Adding
     * samples rapidly increases confidence but the return from extra sampling rapidly diminishes.
     * <p/>
     * Cost is not affected much by sample size. Profiling shows that it is the iteration that is causing most of the
     * time. If we had access to the array backing Map, all would work very fast. Still, it is fast enough.
     * <p/>
     * A 99.99% confidence interval can be achieved that the "lowest" element is actually in the bottom quarter of the
     * hit count distribution.
     * @throws IOException
     */
    public void testLowest() throws IOException {
        createMemoryStore(MemoryStoreEvictionPolicy.LFU, 5000);
        Element element = null;
        Element newElement = null;
        for (int i = 0; i < 10; i++) {
            newElement = new Element("" + i, new Date());
            store.put(newElement);
            int j;
            for (j = 0; j <= i; j++) {
                store.get("" + i);
            }
            if (i > 0) {
                element = ((LfuMemoryStore) store).findRelativelyUnused(newElement);
                assertTrue(!element.equals(newElement));
                assertTrue(element.getHitCount() < 2);
            }
        }

        int lowestQuarterNotIdentified = 0;

        long findTime = 0;
        StopWatch stopWatch = new StopWatch();
        for (int i = 10; i < 5000; i++) {
            store.put(new Element("" + i, new Date()));
            int j;
            int maximumHitCount = 0;
            for (j = 0; j <= i; j += 10) {
                store.get("" + i);
                maximumHitCount++;
            }

            stopWatch.getElapsedTime();
            element = ((LfuMemoryStore) store).findRelativelyUnused(newElement);
            findTime  += stopWatch.getElapsedTime();
            long lowest = element.getHitCount();
            long bottomQuarter = (Math.round(maximumHitCount / 4.0) + 1);
            assertTrue(!element.equals(newElement));
            if (lowest > bottomQuarter) {
                lowestQuarterNotIdentified++;
                //LOG.info(i + " " + maximumHitCount + " " + element);
            }
        }
        LOG.info("Find time: " + findTime);
        assertTrue(findTime < 1500);
        LOG.info("Selections not in lowest quartile: " + lowestQuarterNotIdentified);
        assertTrue(lowestQuarterNotIdentified < 3);

    }

}
