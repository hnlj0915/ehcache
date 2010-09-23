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

package net.sf.ehcache.config;

import net.sf.ehcache.search.attribute.AttributeExtractor;
import net.sf.ehcache.search.attribute.ReflectionAttributeExtractor;
import net.sf.ehcache.util.ClassLoaderUtil;

/**
 * A cache search attribute. Search attributes must have a name and either an expression or class set
 *
 * @author teck
 */
/**
 * @author teck
 *
 */
public class SearchAttribute {

    private String name;
    private String className;
    private String expression;

    /**
     * Set the attribute name
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set the extractor class for this attribute. This class must be available at runtime and must implement {@link AttributeExtractor}
     *
     * @param className
     */
    public void setClass(String className) {
        if (expression != null) {
            throw new InvalidConfigurationException("Cannot set both class and expression for a serach attribute");
        }
        this.className = className;
    }

    /**
     * Set the attribute expression. See {@link ReflectionAttributeExtractor} for more information
     *
     * @param expression
     */
    public void setExpression(String expression) {
        if (className != null) {
            throw new InvalidConfigurationException("Cannot set both class and expression for a serach attribute");
        }
        this.expression = expression;
    }

    /**
     * Get the extractor class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Get the attribute expression
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Get the attribute name
     */
    public String getName() {
        return name;
    }

    /**
     * Construct the extractor for this attribute configuration
     */
    public AttributeExtractor constructExtractor() {
        if (name == null) {
            throw new InvalidConfigurationException("search attribute has no name");
        }

        if (expression != null) {
            return new ReflectionAttributeExtractor(expression);
        } else if (className != null) {
            return (AttributeExtractor) ClassLoaderUtil.createNewInstance(className);
        }

        throw new InvalidConfigurationException("Neither expression or class set for search attribute");
    }

}
