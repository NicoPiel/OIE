/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file;

import java.io.Serializable;

import com.mirth.connect.donkey.util.purge.Purgable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "File connector scheme-specific properties. Serialized as a concrete subtype.", oneOf = {
        FTPSchemeProperties.class,
        SftpSchemeProperties.class,
        S3SchemeProperties.class,
        SmbSchemeProperties.class
})
public abstract class SchemeProperties implements Serializable, Purgable {
    public SchemeProperties() {
    }

    /**
     * Internal helper used by the file connector implementation.
     *
     * IMPORTANT: Hide from OpenAPI to avoid generating a self-referential schema
     * ("fileSchemeProperties" -> SchemeProperties).
     */
    @Schema(hidden = true)
    public abstract SchemeProperties getFileSchemeProperties();

    public abstract String getSummaryText();

    public abstract String toFormattedString();

    @Override
    public abstract SchemeProperties clone();
}
