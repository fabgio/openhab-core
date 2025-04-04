/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.io.transport.mqtt;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.MqttVersion;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection.Protocol;

/**
 * Contains configuration for a MqttBrokerConnection.
 *
 * @author David Graeff - Initial contribution
 * @author Mark Herwege - Added flag for hostname validation
 */
@NonNullByDefault
public class MqttBrokerConnectionConfig {
    // Optional connection name
    public @Nullable String name;
    // Connection parameters (host+port+secure+hostnameValidated)
    public @Nullable String host;
    public @Nullable Integer port;
    public boolean secure = true;
    public boolean hostnameValidated = true;
    // Protocol parameters
    public Protocol protocol = MqttBrokerConnection.DEFAULT_PROTOCOL;
    public MqttVersion mqttVersion = MqttBrokerConnection.DEFAULT_MQTT_VERSION;
    // Authentication parameters
    public @Nullable String username;
    public @Nullable String password;
    public @Nullable String clientID;
    // MQTT parameters
    public Integer qos = MqttBrokerConnection.DEFAULT_QOS;
    /** Keepalive in seconds */
    public @Nullable Integer keepAlive;
    // Last will parameters
    public @Nullable String lwtTopic;
    public @Nullable String lwtMessage;
    public Integer lwtQos = MqttBrokerConnection.DEFAULT_QOS;
    public Boolean lwtRetain = false;

    /**
     * Return the brokerID of this connection. This is either the name or host:port(:s), for instance "myhost:8080:s".
     * This method will return an empty string, if none of the parameters is set.
     */
    public String getBrokerID() {
        final String name = this.name;
        if (name != null && !name.isEmpty()) {
            return name;
        } else {
            StringBuilder b = new StringBuilder();
            if (host != null) {
                b.append(host);
            }
            final Integer port = this.port;
            if (port != null) {
                b.append(":");
                b.append(port.toString());
            }
            if (secure) {
                b.append(":s");
            }
            return b.toString();
        }
    }

    /**
     * Output the name, host, port, secure flag and hostname validation flag
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (name != null) {
            b.append(name);
            b.append(", ");
        }
        if (host != null) {
            b.append(host);
        }
        final Integer port = this.port;
        if (port != null) {
            b.append(":");
            b.append(port.toString());
        }
        if (secure) {
            b.append(":s");
        }
        if (hostnameValidated) {
            b.append(":v");
        }
        return b.toString();
    }
}
