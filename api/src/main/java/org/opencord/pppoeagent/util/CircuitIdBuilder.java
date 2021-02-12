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
package org.opencord.pppoeagent.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Range;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.onosproject.net.Device;
import org.opencord.sadis.UniTagInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Circuit-id builder util.
 */
public class CircuitIdBuilder {
    //#region Circuit-id Builder
    private CircuitIdConfig circuitIdConfig;
    private DeviceService deviceService;
    private BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private ConnectPoint connectPoint;
    private String[] exclusiveExpressions;
    private Map<CircuitIdFieldName, String> customSeparators;
    private UniTagInformation uniTagInformation;

    private final ArrayList<CircuitIdField> availableFields = new ArrayList<>(
        Arrays.asList(new CircuitIdAccessNodeIdentifier(),
                new CircuitIdNetworkTechnology(),
                new CircuitIdSlot(),
                new CircuitIdPort(),
                new CircuitIdOnuSerialNumber(),
                new CircuitIdUniPortNumber(),
                new CircuitIdSvId(),
                new CircuitIdCvId())
    );

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new CircuitId builder with default configuration.
     */
    public CircuitIdBuilder() {
        // Instantiate the circuit-id config Object.
        circuitIdConfig = new CircuitIdConfig();
        circuitIdConfig.setSeparator("/");
        // This is a default list with exclusive expressions to validate the field value.
        exclusiveExpressions = new String[] {circuitIdConfig.getSeparator()};

        // At the end we set the default field list - in the future these fields will be defined by the operator.
        circuitIdConfig.setFieldListWithDefault(availableFields);
        customSeparators = new HashMap<>();
    }

    /**
     * Sets the connect-point.
     *
     * @param value connect-point.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder setConnectPoint(ConnectPoint value) {
        this.connectPoint = value;
        return this;
    }

    /**
     * Sets the device service.
     *
     * @param value device service.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder setDeviceService(DeviceService value) {
        this.deviceService = value;
        return this;
    }

    /**
     * Sets the SADIS service.
     *
     * @param value SADIS service.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder setSubsService(BaseInformationService<SubscriberAndDeviceInformation> value) {
        this.subsService = value;
        return this;
    }

    /**
     * Sets the UniTag information.
     *
     * @param value UniTag information.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder setUniTagInformation(UniTagInformation value) {
        this.uniTagInformation = value;
        return this;
    }

    /**
     * Adds a custom separator.
     *
     * @param field the circuit-id field associated to the custom separator.
     * @param separator the custom separator to be added.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder addCustomSeparator(CircuitIdFieldName field, String separator) {
        if (!circuitIdConfig.getSeparator().equals(separator)) {
            customSeparators.put(field, separator);
        } else {
            log.warn("Not defining custom separator '{}' for {} field since it's equals to the configured separator.",
                    separator, field.name());
        }

        return this;
    }

    /**
     * Gets the custom separators.
     *
     * @return a map with the circuit-id fields and its custom separators.
     */
    public Map<CircuitIdFieldName, String> getCustomSeparators() {
        return customSeparators;
    }

    /**
     * Gets the circuit-id configuration object.
     *
     * @return the configuration for circuit-id builder.
     */
    public CircuitIdConfig getCircuitIdConfig() {
        return this.circuitIdConfig;
    }

    /**
     * Sets the circuit-id configuration.
     *
     * @param fieldNameList the list of desired fields.
     * @return circuit-id builder.
     */
    public CircuitIdBuilder setCircuitIdConfig(ArrayList<CircuitIdFieldName> fieldNameList) {
        circuitIdConfig.setFieldList(fieldNameList, availableFields);
        return this;
    }

    /**
     * Generates the circuit-id based on the provided configuration and default/custom separators.
     *
     * @return circuit-id.
     */
    public String build() {
        String circuitId = "";

        // Get the field list.
        ArrayList<CircuitIdField> fieldList = circuitIdConfig.getFieldList();

        // Check if it's valid.
        if (fieldList == null || fieldList.size() <= 0) {
            log.error("Failed to build the circuit Id: there's no entries in the field list.");
            return "";
        }

        // This list is filtered to ignore prefix fields.
        ArrayList<CircuitIdField> filteredFieldList = fieldList;

        // Go throughout the field list to build the string.
        for (int i = 0; i <  filteredFieldList.size(); i++) {
            CircuitIdField field = filteredFieldList.get(i);

            String value = "";
            try {
                value = field.build();
            } catch (MissingParameterException | FieldValidationException e) {
                log.error(e.getMessage());
                return "";
            }

            // Concat the obtained value with the rest of id.
            circuitId = circuitId.concat(value);

            // If this is not the last "for" iteration, isolate with the separator.
            if (i != (filteredFieldList.size() - 1)) {
                String separator = customSeparators.containsKey(field.getFieldName()) ?
                        customSeparators.get(field.getFieldName()) : circuitIdConfig.getSeparator();

                circuitId = circuitId.concat(separator);
            }
        }
        // At the end, it returns the fully built string.
        return circuitId;
    }
    //#endregion

    //#region Field Classes
    private SubscriberAndDeviceInformation getSadisEntry() {
        Port p = deviceService.getPort(connectPoint);
        String subscriber = p.annotations().value(AnnotationKeys.PORT_NAME);
        return subsService.get(subscriber);
    }

    private class CircuitIdAccessNodeIdentifier extends CircuitIdField {
        CircuitIdAccessNodeIdentifier() {
            this.setFieldName(CircuitIdFieldName.AcessNodeIdentifier)
                    .setIsNumber(false)
                    .setExclusiveExpressions(exclusiveExpressions);
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {
            // This field must be the serial number of the OLT which the packet came from.
            // So we try to recover this information using the device Id of the connect point.
            Device device = deviceService.getDevice(connectPoint.deviceId());

            if (device == null) {
                String errorMsg = String.format("Device not found at device service: %s.", connectPoint.deviceId());
                throw new MissingParameterException(errorMsg);
            }

            String accessNodeId = device.serialNumber();

            if (isValid(accessNodeId)) {
                return accessNodeId;
            } else {
                throw new FieldValidationException(this, accessNodeId);
            }
        }
    }

    private static class CircuitIdSlot extends CircuitIdField {
        CircuitIdSlot() {
            this.setFieldName(CircuitIdFieldName.Slot)
                    .setIsNumber(true)
                    .setMaxLength(2)
                    .setRange(Range.between(0L, 99L));
        }

        @Override
        String build() {
            // At first, this will be hard-coded as "0". But this may change in future implementation.
            return "0";
        }
    }

    private class CircuitIdPort extends CircuitIdField {
        CircuitIdPort() {
            this.setFieldName(CircuitIdFieldName.Port)
                    .setIsNumber(true)
                    .setMaxLength(3)
                    .setRange(Range.between(1L, 256L));
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {
            String port = "";

            // If there's no connect-point we can't build this field.
            if (connectPoint == null) {
                String errorMsg = "ConnectPoint not passed to circuit-id builder - can't build PORT field.";
                throw new MissingParameterException(errorMsg);
            }

            long connectPointPort = connectPoint.port().toLong();
            int ponPort = ((int) connectPointPort >> 12) + 1;

            port = String.valueOf(ponPort);
            if (isValid(port)) {
                return port;
            } else {
                throw new FieldValidationException(this, port);
            }
        }
    }

    private class CircuitIdOnuSerialNumber extends CircuitIdField {
        CircuitIdOnuSerialNumber() {
            this.setFieldName(CircuitIdFieldName.OnuSerialNumber)
                    .setIsNumber(false)
                    .setExclusiveExpressions(exclusiveExpressions);
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {

            if (deviceService == null) {
                String errorMsg = "Device service not passed to circuit-id builder - can't build ONU SN field.";
                throw new MissingParameterException(errorMsg);
            }

            Port p = deviceService.getPort(connectPoint);
            String onuSn = p.annotations().value(AnnotationKeys.PORT_NAME);

            // The reason of the following block is that in some cases, the UNI port number can be
            // appended in the ONU serial number. So it's required to remove it.
            CircuitIdUniPortNumber uniPortNumberField = new CircuitIdUniPortNumber();
            String uniPortNumber = uniPortNumberField.build();

            if (uniPortNumberField.isValid(uniPortNumber)) {
                // Build the suffix it shouldn't have based on the UNI port number.
                uniPortNumber = "-" + (Integer.parseInt(uniPortNumber));

                // Checks if the serial number contains this suffix.
                if (onuSn.substring(onuSn.length() - uniPortNumber.length()).equals(uniPortNumber)) {
                    // Remove it if this is the case.
                    onuSn = onuSn.substring(0, onuSn.indexOf(uniPortNumber));
                }

            } else {
                log.debug("Failed to get the UNI port number, the ONU serial number could be wrong;");
            }


            if (isValid(onuSn)) {
                return onuSn;
            } else {
                throw new FieldValidationException(this, onuSn);
            }
        }
    }

    private class CircuitIdUniPortNumber extends CircuitIdField {
        CircuitIdUniPortNumber() {
            this.setFieldName(CircuitIdFieldName.UniPortNumber)
                    .setIsNumber(true)
                    .setMaxLength(2)
                    .setRange(Range.between(1L, 99L));
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {
            // If there's no connect-point we can't build this field.
            if (connectPoint == null) {
                String errorMsg = "ConnectPoint not passed to circuit-id builder - can't build UNI PORT NUMBER field.";
                throw new MissingParameterException(errorMsg);
            }

            long connectPointPort = connectPoint.port().toLong();
            int uniPortNumber = ((int) connectPointPort & 0xF) + 1;

            String uniPortString = String.valueOf(uniPortNumber);

            if (isValid(uniPortString)) {
                return uniPortString;
            } else {
                throw new FieldValidationException(this, uniPortString);
            }
        }
    }

    private class CircuitIdSvId extends CircuitIdField {
        CircuitIdSvId() {
            this.setFieldName(CircuitIdFieldName.SvID)
                    .setIsNumber(true)
                    .setMaxLength(4)
                    .setRange(Range.between(0L, 4095L));
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {
            if (uniTagInformation == null) {
                String errorMsg = String.format("UNI TAG info not found for %s while looking for S-TAG.", connectPoint);
                throw new MissingParameterException(errorMsg);
            }

            String sVid = uniTagInformation.getPonSTag().toString();
            if (isValid(sVid)) {
                return sVid;
            } else {
                throw new FieldValidationException(this, sVid);
            }
        }
    }

     private class CircuitIdCvId extends CircuitIdField {
        CircuitIdCvId() {
            this.setFieldName(CircuitIdFieldName.CvID)
                    .setIsNumber(true)
                    .setMaxLength(4)
                    .setRange(Range.between(0L, 4095L));
        }

        @Override
        String build() throws MissingParameterException,
                              FieldValidationException {
            if (uniTagInformation == null) {
                String errorMsg = String.format("UNI TAG info not found for %s looking for C-TAG", connectPoint);
                throw new MissingParameterException(errorMsg);
            }

            String cVid = uniTagInformation.getPonCTag().toString();
            if (isValid(cVid)) {
                return cVid;
            } else {
                throw new FieldValidationException(this, cVid);
            }
        }
    }

    private class CircuitIdNetworkTechnology extends CircuitIdField {
        CircuitIdNetworkTechnology() {
            this.setFieldName(CircuitIdFieldName.NetworkTechnology)
                    .setIsNumber(false)
                    .setExclusiveExpressions(exclusiveExpressions);
        }

        // For now this is fixed.
        @Override
        String build() {
            return "xpon";
        }
    }
    //#endregion
}