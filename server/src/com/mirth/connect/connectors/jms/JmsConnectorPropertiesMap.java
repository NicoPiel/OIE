/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.jms;

import java.util.LinkedHashMap;

/**
 * OpenAPI typing helper used for representing a map of JMS connector templates.
 * <p>
 * This class is only intended for use in OpenAPI annotations (schema
 * implementation overrides).
 */
public class JmsConnectorPropertiesMap extends LinkedHashMap<String, JmsConnectorProperties> {

	private static final long serialVersionUID = 1L;
}
