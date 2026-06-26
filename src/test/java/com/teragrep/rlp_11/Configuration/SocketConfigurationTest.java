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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SocketConfigurationTest {

    @Test
    public void testReadTimeout() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.readtimeout", "1000");
        final SocketConfiguration defaultConfig = new SocketConfiguration(Collections.emptyMap());
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertEquals(5000, defaultConfig.readTimeout());
        Assertions.assertEquals(1000, enabledConfig.readTimeout());
    }

    @Test
    public void testWriteTimeout() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.writetimeout", "1000");
        final SocketConfiguration defaultConfig = new SocketConfiguration(Collections.emptyMap());
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertEquals(5000, defaultConfig.writeTimeout());
        Assertions.assertEquals(1000, enabledConfig.writeTimeout());
    }

    @Test
    public void testConnectTimeout() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.connecttimeout", "1000");
        final SocketConfiguration defaultConfig = new SocketConfiguration(Collections.emptyMap());
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertEquals(5000, defaultConfig.connectTimeout());
        Assertions.assertEquals(1000, enabledConfig.connectTimeout());
    }

    @Test
    public void testKeepAlive() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.keepalive", "true");
        final SocketConfiguration defaultConfig = new SocketConfiguration(Collections.emptyMap());
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertFalse(defaultConfig.keepAlive());
        Assertions.assertTrue(enabledConfig.keepAlive());
    }

    @Test
    public void testKeepAliveNonBooleanInputDefaultsFalse() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.keepalive", "non-boolean");
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertFalse(enabledConfig.keepAlive());
    }

    @Test
    public void testNonValidIntegerThrows() {
        final Map<String, String> config = new HashMap<>();
        config.put("socket.readtimeout", "true");
        config.put("socket.writetimeout", "999999999999999999999999999999999999");
        config.put("socket.connecttimeout", "");
        final SocketConfiguration enabledConfig = new SocketConfiguration(config);
        Assertions.assertThrows(NumberFormatException.class, enabledConfig::readTimeout);
        Assertions.assertThrows(NumberFormatException.class, enabledConfig::writeTimeout);
        Assertions.assertThrows(NumberFormatException.class, enabledConfig::connectTimeout);
    }

    @Test
    public void testContract() {
        EqualsVerifier.forClass(SocketConfiguration.class).withIgnoredFields("LOGGER").verify();
    }
}
