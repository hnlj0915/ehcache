/**
 *  Copyright 2003-2008 Luck Consulting Pty Ltd
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

package net.sf.ehcache.distribution.jms;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import static net.sf.ehcache.distribution.jms.JMSUtil.ACKNOWLEDGEMENT_MODE;
import static net.sf.ehcache.distribution.jms.JMSUtil.DEFAULT_LOADER_ARGUMENT;
import static net.sf.ehcache.distribution.jms.JMSUtil.GET_QUEUE_BINDING_NAME;
import static net.sf.ehcache.distribution.jms.JMSUtil.GET_QUEUE_CONNECTION_FACTORY_BINDING_NAME;
import static net.sf.ehcache.distribution.jms.JMSUtil.INITIAL_CONTEXT_FACTORY_NAME;
import static net.sf.ehcache.distribution.jms.JMSUtil.PASSWORD;
import static net.sf.ehcache.distribution.jms.JMSUtil.PROVIDER_URL;
import static net.sf.ehcache.distribution.jms.JMSUtil.SECURITY_CREDENTIALS;
import static net.sf.ehcache.distribution.jms.JMSUtil.SECURITY_PRINCIPAL_NAME;
import static net.sf.ehcache.distribution.jms.JMSUtil.TIMEOUT_MILLIS;
import static net.sf.ehcache.distribution.jms.JMSUtil.URL_PKG_PREFIXES;
import static net.sf.ehcache.distribution.jms.JMSUtil.USERNAME;
import net.sf.ehcache.loader.CacheLoaderFactory;
import net.sf.ehcache.util.PropertyUtil;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A factory to create JMSCacheLoaders.
 *
 * @author Greg Luck
 */
public class JMSCacheLoaderFactory extends CacheLoaderFactory {

    /**
     * The default timeoutMillis - time in milliseconds to wait for a reply from a JMS Cache Loader. 
     */
    protected static final int DEFAULT_TIMEOUT_INTERVAL_MILLIS = 30000;

    private static final Logger LOG = Logger.getLogger(JMSCacheLoaderFactory.class.getName());

    /**
     * Creates a CacheLoader using the Ehcache configuration mechanism at the time the associated cache
     * is created.
     *
     * @param properties implementation specific properties. These are configured as comma
     *                   separated name value pairs in ehcache.xml
     * @return a constructed CacheLoader
     */
    public net.sf.ehcache.loader.CacheLoader createCacheLoader(Ehcache cache, Properties properties) {

        String securityPrincipalName = PropertyUtil.extractAndLogProperty(SECURITY_PRINCIPAL_NAME, properties);
        String securityCredentials = PropertyUtil.extractAndLogProperty(SECURITY_CREDENTIALS, properties);
        String initialContextFactoryName = PropertyUtil.extractAndLogProperty(INITIAL_CONTEXT_FACTORY_NAME, properties);
        String urlPkgPrefixes = PropertyUtil.extractAndLogProperty(URL_PKG_PREFIXES, properties);
        String providerURL = PropertyUtil.extractAndLogProperty(PROVIDER_URL, properties);
        String getQueueConnectionFactoryBindingName =
                PropertyUtil.extractAndLogProperty(GET_QUEUE_CONNECTION_FACTORY_BINDING_NAME, properties);
        if (getQueueConnectionFactoryBindingName == null) {
            throw new CacheException("getQueueConnectionFactoryBindingName is not configured.");
        }
        String getQueueBindingName = PropertyUtil.extractAndLogProperty(GET_QUEUE_BINDING_NAME, properties);
        if (getQueueBindingName == null) {
            throw new CacheException("getQueueBindingName is not configured.");
        }

        String defaultLoaderArgument = PropertyUtil.extractAndLogProperty(DEFAULT_LOADER_ARGUMENT, properties);

        String userName = PropertyUtil.extractAndLogProperty(USERNAME, properties);
        String password = PropertyUtil.extractAndLogProperty(PASSWORD, properties);
        String acknowledgementMode = PropertyUtil.extractAndLogProperty(ACKNOWLEDGEMENT_MODE, properties);

        int timeoutMillis = extractTimeoutMillis(properties);

        AcknowledgementMode effectiveAcknowledgementMode = AcknowledgementMode.forString(acknowledgementMode);

        Context context = null;

        QueueConnection getQueueConnection;
        QueueConnectionFactory queueConnectionFactory;
        Queue getQueue;

        try {

            context = JMSUtil.createInitialContext(securityPrincipalName, securityCredentials, initialContextFactoryName,
                    urlPkgPrefixes, providerURL, null, null,
                    getQueueBindingName, getQueueConnectionFactoryBindingName);

            queueConnectionFactory = (QueueConnectionFactory) JMSUtil.lookup(context, getQueueConnectionFactoryBindingName);
            getQueue = (Queue) JMSUtil.lookup(context, getQueueBindingName);

            JMSUtil.closeContext(context);
        } catch (NamingException ne) {
            throw new CacheException("NamingException " + ne.getMessage(), ne);
        }

        try {
            getQueueConnection = createQueueConnection(userName, password, queueConnectionFactory);
        } catch (JMSException e) {
            throw new CacheException("Problem creating connections: " + e.getMessage(), e);
        }

        return new JMSCacheLoader(cache, defaultLoaderArgument, getQueueConnection, getQueue,
                effectiveAcknowledgementMode, timeoutMillis);
    }


    private QueueConnection createQueueConnection(String userName, String password,
                                                  QueueConnectionFactory queueConnectionFactory) throws JMSException {
        QueueConnection queueConnection;
        if (userName != null) {
            queueConnection = queueConnectionFactory.createQueueConnection(userName, password);
        } else {
            queueConnection = queueConnectionFactory.createQueueConnection();
        }
        return queueConnection;
    }


    /**
     * Extracts the value of timeoutMillis. Sets it to 30000ms if
     * either not set or there is a problem parsing the number
     *
     * @param properties
     */
    protected int extractTimeoutMillis(Properties properties) {
        int timeoutMillis = 0;
        String timeoutMillisString =
                PropertyUtil.extractAndLogProperty(TIMEOUT_MILLIS, properties);
        if (timeoutMillisString != null) {
            try {
                timeoutMillis = Integer.parseInt(timeoutMillisString);
            } catch (NumberFormatException e) {
                LOG.warning("Number format exception trying to set timeoutMillis. " +
                        "Using the default instead. String value was: '" + timeoutMillisString + "'");
                timeoutMillis = DEFAULT_TIMEOUT_INTERVAL_MILLIS;
            }
        } else {
            timeoutMillis = DEFAULT_TIMEOUT_INTERVAL_MILLIS;
        }
        return timeoutMillis;
    }

}
