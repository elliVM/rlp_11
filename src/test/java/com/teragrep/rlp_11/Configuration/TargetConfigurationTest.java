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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class TargetConfigurationTest {

    // target.hostname
    @Test
    public void testNonNullHostname() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertEquals("target-hostname", targetConfiguration.hostname());
    }

    @Test
    public void testNullHostname() {
        Map<String, String> map = baseConfig();
        map.remove("target.hostname");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::hostname);
    }

    // target.port
    @Test
    public void testGoodPort() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertEquals(601, targetConfiguration.port());
    }

    @Test
    public void testNullPort() {
        Map<String, String> map = baseConfig();
        map.remove("target.port");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::port);
    }

    @Test
    public void testTooSmallPort() {
        Map<String, String> map = baseConfig();
        map.put("target.port", "0");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::port);
    }

    @Test
    public void testTooHighPort() {
        Map<String, String> map = baseConfig();
        map.put("target.port", "65536");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::port);
    }

    @Test
    public void testNonNumericPort() {
        Map<String, String> map = baseConfig();
        map.put("target.port", "not a number");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(NumberFormatException.class, targetConfiguration::port);
    }

    // target.reconnectinterval
    @Test
    public void testGoodReconnectInterval() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertEquals(15000, targetConfiguration.reconnectInterval());
    }

    @Test
    public void testNullReconnectInterval() {
        Map<String, String> map = baseConfig();
        map.remove("target.reconnectinterval");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::reconnectInterval);
    }

    @Test
    public void testTooSmallReconnectInterval() {
        Map<String, String> map = baseConfig();
        map.put("target.reconnectinterval", "0");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::reconnectInterval);
    }

    @Test
    public void testNonNumericReconnectInterval() {
        Map<String, String> map = baseConfig();
        map.put("target.reconnectinterval", "not a number");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(NumberFormatException.class, targetConfiguration::reconnectInterval);
    }

    // "target.rebindamount"
    @Test
    public void testValidRebindRequestAmount() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertEquals(5, targetConfiguration.rebindRequestAmount());
    }

    @Test
    public void testNullRebindRequestAmount() {
        Map<String, String> map = baseConfig();
        map.remove("target.rebindamount");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::rebindRequestAmount);
    }

    @Test
    public void testTooSmallRebindRequestAmount() {
        Map<String, String> map = baseConfig();
        map.put("target.rebindamount", "0");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::rebindRequestAmount);
    }

    @Test
    public void testNonNumericRebindRequestAmount() {
        Map<String, String> map = baseConfig();
        map.put("target.rebindamount", "not a number");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(NumberFormatException.class, targetConfiguration::rebindRequestAmount);
    }

    // target.rebind-enabled
    @Test
    public void testTrueRebindEnabled() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertTrue(targetConfiguration::isRebindEnabled);
    }

    @Test
    public void testFalseRebindEnabled() {
        Map<String, String> map = baseConfig();
        map.put("target.rebind-enabled", "false");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertFalse(targetConfiguration::isRebindEnabled);
    }

    @Test
    public void testNullRebindEnabled() {
        Map<String, String> map = baseConfig();
        map.remove("target.rebind-enabled");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::isRebindEnabled);
    }

    @Test
    public void testInvalidRebindEnabledDefaultsFalse() {
        Map<String, String> map = baseConfig();
        map.put("target.rebind-enabled", "non-boolean");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertFalse(targetConfiguration::isRebindEnabled);
    }

    // target.maxidleseconds
    @Test
    public void testValidMaxIdleSeconds() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertEquals(Duration.ofSeconds(10), targetConfiguration.maxIdleSeconds());
    }

    @Test
    public void testNegativeMaxIdleSeconds() {
        Map<String, String> map = baseConfig();
        map.put("target.maxidleseconds", "-15");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::maxIdleSeconds);
    }

    @Test
    public void testNullMaxIdleSeconds() {
        Map<String, String> map = baseConfig();
        map.remove("target.maxidleseconds");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::maxIdleSeconds);
    }

    @Test
    public void testNonNumericMaxIdleSeconds() {
        Map<String, String> map = baseConfig();
        map.put("target.maxidleseconds", "non-numeric");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(NumberFormatException.class, targetConfiguration::maxIdleSeconds);
    }

    // target.maxidle-enabled
    @Test
    public void testTrueMaxIdle() {
        Map<String, String> map = baseConfig();
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertTrue(targetConfiguration::isMaxIdleEnabled);
    }

    @Test
    public void testFalseMaxIdle() {
        Map<String, String> map = baseConfig();
        map.put("target.maxidle-enabled", "false");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertFalse(targetConfiguration::isMaxIdleEnabled);
    }

    @Test
    public void testNullMaxIdle() {
        Map<String, String> map = baseConfig();
        map.remove("target.maxidle-enabled");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertThrowsExactly(ConfigurationException.class, targetConfiguration::isMaxIdleEnabled);
    }

    @Test
    public void testInvalidMaxIdleDefaultsFalse() {
        Map<String, String> map = baseConfig();
        map.put("target.maxidle-enabled", "non-boolean");
        TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        Assertions.assertFalse(targetConfiguration::isMaxIdleEnabled);
    }

    @Test
    public void testContract() {
        EqualsVerifier.forClass(TargetConfiguration.class).withIgnoredFields("LOGGER").verify();
    }

    private Map<String, String> baseConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("target.hostname", "target-hostname");
        map.put("target.port", "601");
        map.put("target.reconnectinterval", "15000");
        map.put("target.rebindamount", "5");
        map.put("target.rebind-enabled", "true");
        map.put("target.maxidleseconds", "10");
        map.put("target.maxidle-enabled", "true");
        return map;
    }
}
