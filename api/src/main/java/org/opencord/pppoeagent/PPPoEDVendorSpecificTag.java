/*
 * Copyright 2021-present Open Networking Foundation
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
package org.opencord.pppoeagent;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents the "Vendor-Specific" PPPoED tag.
 * This class is used by the PPPoE agent in order to read/build the tag that carries the
 * vendor-id, circuit-id and remote-id information, which are attached to the upstrem packets.
 */
public class PPPoEDVendorSpecificTag {
    public static final int BBF_IANA_VENDOR_ID = 3561;
    private static final byte CIRCUIT_ID_OPTION = 1;
    private static final byte REMOTE_ID_OPTION = 2;

    private int vendorId = BBF_IANA_VENDOR_ID;
    private String circuitId = null;
    private String remoteId = null;

    /**
     * Creates an empty Vendor-Specific tag object.
     *
     */
    public PPPoEDVendorSpecificTag() {
    }

    /**
     * Creates a new Vendor-Specific tag object.
     *
     * @param circuitId circuit-id information
     * @param remoteId remote-id information
     */
    public PPPoEDVendorSpecificTag(String circuitId, String remoteId) {
        this.circuitId = circuitId;
        this.remoteId = remoteId;
    }

    /**
     * Sets the vendor-id.
     *
     * @param value vendor-id to be set.
     */
    public void setVendorId(int value) {
        this.vendorId = value;
    }

    /**
     * Gets the vendor-id.
     *
     * @return the vendor-id.
     */
    public Integer getVendorId() {
        return this.vendorId;
    }

    /**
     * Sets the circuit-id.
     *
     * @param value circuit-id to be set.
     */
    public void setCircuitId(String value) {
        this.circuitId = value;
    }

    /**
     * Gets the circuit-id.
     *
     * @return the circuit-id.
     */
    public String getCircuitId() {
        return this.circuitId;
    }

    /**
     * Sets the remote-id.
     *
     * @param value remote-id to be set.
     */
    public void setRemoteId(String value) {
        this.remoteId = value;
    }

    /**
     * Gets the remote-id.
     *
     * @return the remote-id.
     */
    public String getRemoteId() {
        return this.remoteId;
    }

    /**
     * Returns the representation of the PPPoED vendor-specific tag as byte array.
     * @return returns byte array
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        byte[] bytes = ByteBuffer.allocate(4).putInt(this.vendorId).array();
        buf.write(bytes, 0, bytes.length);

        // Add sub option if set
        if (circuitId != null) {
            buf.write(CIRCUIT_ID_OPTION);
            buf.write((byte) circuitId.length());
            bytes = circuitId.getBytes(StandardCharsets.UTF_8);
            buf.write(bytes, 0, bytes.length);
        }

        // Add sub option if set
        if (remoteId != null) {
            buf.write(REMOTE_ID_OPTION);
            buf.write((byte) remoteId.length());
            bytes = remoteId.getBytes(StandardCharsets.UTF_8);
            buf.write(bytes, 0, bytes.length);
        }

        return buf.toByteArray();
    }

    /**
     * Returns a PPPoEDVendorSpecificTag object from a byte array.
     * @param data byte array data to convert to PPPoEDVendorSpecificTag object.
     * @return vendor specific tag from given byte array.
     * */
    public static PPPoEDVendorSpecificTag fromByteArray(byte[] data) {
        int position = 0;
        final int vendorIdLength = 4;

        PPPoEDVendorSpecificTag vendorSpecificTag = new PPPoEDVendorSpecificTag();

        if (data.length < vendorIdLength) {
            return vendorSpecificTag;
        }

        int vId = ByteBuffer.wrap(data, position, vendorIdLength).getInt();
        vendorSpecificTag.setVendorId(vId);

        position += vendorIdLength;

        while (data.length > position) {
            byte code = data[position];
            position++;

            if (data.length < position) {
                break;
            }

            int length = (int) data[position];
            position++;

            if (data.length < (position + length)) {
                break;
            }

            String clvString = new String(Arrays.copyOfRange(data, position, length + position),
                                          StandardCharsets.UTF_8);

            position += length;

            if (code == CIRCUIT_ID_OPTION) {
                vendorSpecificTag.setCircuitId(clvString);
            } else if (code == REMOTE_ID_OPTION) {
                vendorSpecificTag.setRemoteId(clvString);
            }
        }

        return vendorSpecificTag;
    }
}
