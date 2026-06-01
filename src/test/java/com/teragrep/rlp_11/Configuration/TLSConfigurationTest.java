/*
 * RELP Commit Latency Probe RLP-11
 * Copyright (C) 2024 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.rlp_11.Configuration;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public final class TLSConfigurationTest {

    @Test
    public void testIsEnabled() {
        final Map<String, String> defaultMap = new HashMap<>();
        final Map<String, String> enabledMap = new HashMap<>();
        enabledMap.put("tls.enabled", "true");
        final TLSConfiguration defaultConfig = new TLSConfiguration(defaultMap);
        final TLSConfiguration enabledConfig = new TLSConfiguration(enabledMap);
        Assertions.assertFalse(defaultConfig.isTLSEnabled());
        Assertions.assertTrue(enabledConfig.isTLSEnabled());
    }

    @Test
    public void testValues() {
        final Map<String, String> optionsMap = new HashMap<>();
        optionsMap.put("tls.enabled", "true");
        optionsMap.put("tls.keystore.path", "keystore/path");
        optionsMap.put("tls.keystore.password", "testpass");
        optionsMap.put("tls.protocol", "TLSv1.2");
        final TLSConfiguration tlsConfiguration = new TLSConfiguration(optionsMap);
        Assertions.assertTrue(tlsConfiguration.isTLSEnabled());
        Assertions.assertEquals("keystore/path", tlsConfiguration.keyStorePath());
        Assertions.assertEquals("testpass", tlsConfiguration.keyStorePassword());
        Assertions.assertEquals("TLSv1.2", tlsConfiguration.protocol());
    }

    @Test
    public void testMissingValuesThrows() {
        final Map<String, String> optionsMap = new HashMap<>();
        final TLSConfiguration tlsConfiguration = new TLSConfiguration(optionsMap);
        Assertions.assertFalse(tlsConfiguration.isTLSEnabled());
        final ConfigurationException pathException = Assertions
                .assertThrows(ConfigurationException.class, tlsConfiguration::keyStorePath);
        final ConfigurationException passwordException = Assertions
                .assertThrows(ConfigurationException.class, tlsConfiguration::keyStorePassword);
        final ConfigurationException protocolException = Assertions
                .assertThrows(ConfigurationException.class, tlsConfiguration::protocol);
        Assertions.assertEquals("Invalid value for <tls.keystore.path> received", pathException.getMessage());
        Assertions.assertEquals("Invalid value for <tls.keystore.password> received", passwordException.getMessage());
        Assertions.assertEquals("Invalid value for <tls.protocol> received", protocolException.getMessage());
    }

    @Test
    public void testContract() {
        EqualsVerifier.forClass(TLSConfiguration.class).verify();
    }
}
