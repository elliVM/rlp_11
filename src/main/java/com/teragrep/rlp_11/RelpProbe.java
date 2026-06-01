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
package com.teragrep.rlp_11;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.client.IManagedRelpConnection;
import com.teragrep.rlp_01.client.RelpConnectionFactory;
import com.teragrep.rlp_11.Configuration.ProbeConfiguration;
import com.teragrep.rlp_11.Configuration.MetricsConfiguration;
import com.teragrep.rlp_11.Configuration.TargetConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.codahale.metrics.MetricRegistry.name;

public class RelpProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelpProbe.class);
    private final RelpConnectionFactory connectionFactory;
    private final TargetConfiguration targetConfiguration;
    private final RecordFactory recordFactory;
    private final ProbeConfiguration probeConfiguration;
    private final AtomicBoolean stayRunning = new AtomicBoolean(true);
    private IManagedRelpConnection relpConnection;
    private final CountDownLatch latch = new CountDownLatch(1);
    private boolean connected = false;
    private final Counter records;
    private final Counter resends;
    private final Counter connects;
    private final Counter disconnects;
    private final Counter retriedConnects;
    private final Timer sendLatency;
    private final Timer connectLatency;

    public RelpProbe(
            final RelpConnectionFactory connectionFactory,
            final TargetConfiguration targetConfiguration,
            final ProbeConfiguration probeConfiguration,
            final MetricsConfiguration metricsConfiguration,
            final RecordFactory recordFactory,
            final MetricRegistry metricRegistry
    ) {
        this(
                connectionFactory,
                targetConfiguration,
                probeConfiguration,
                recordFactory,
                metricRegistry.counter(name(RelpProbe.class, "records")),
                metricRegistry.counter(name(RelpProbe.class, "resends")),
                metricRegistry.counter(name(RelpProbe.class, "connects")),
                metricRegistry.counter(name(RelpProbe.class, "disconnects")),
                metricRegistry.counter(name(RelpProbe.class, "retriedConnects")),
                metricRegistry.timer(name(RelpProbe.class, "sendLatency"), () -> new Timer(new SlidingWindowReservoir(metricsConfiguration.window()))), metricRegistry.timer(name(RelpProbe.class, "connectLatency"), () -> new Timer(new SlidingWindowReservoir(metricsConfiguration.window())))
        );
    }

    public RelpProbe(
            final RelpConnectionFactory connectionFactory,
            final TargetConfiguration targetConfiguration,
            final ProbeConfiguration probeConfiguration,
            final RecordFactory recordFactory,
            final Counter records,
            final Counter resends,
            final Counter connects,
            final Counter disconnects,
            final Counter retriedConnects,
            final Timer sendLatency,
            final Timer connectLatency
    ) {
        this.connectionFactory = connectionFactory;
        this.targetConfiguration = targetConfiguration;
        this.probeConfiguration = probeConfiguration;
        this.recordFactory = recordFactory;
        this.records = records;
        this.resends = resends;
        this.connects = connects;
        this.disconnects = disconnects;
        this.retriedConnects = retriedConnects;
        this.sendLatency = sendLatency;
        this.connectLatency = connectLatency;
    }

    public void start() {
        connect();
        while (stayRunning.get()) {
            final RelpBatch relpBatch = new RelpBatch();
            relpBatch.insert(recordFactory.createRecord());

            try (final Timer.Context context = sendLatency.time()) {
                long sendTries = relpConnection.ensureSent(relpBatch);
                // check if ensure sent had to do a resend and increment resends accordingly
                if (sendTries > 1) {
                    resends.inc(sendTries - 1);
                }
                records.inc();
            }

            try {
                LOGGER.debug("Sleeping before sending next record");
                TimeUnit.MILLISECONDS.sleep(probeConfiguration.interval());
            }
            catch (InterruptedException e) {
                LOGGER.warn("Sleep interrupted: <{}>", e.getMessage());
            }
        }
        try {
            relpConnection.close();
            disconnects.inc();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        latch.countDown();
    }

    private void connect() {
        while (!connected && stayRunning.get()) {
            try (final Timer.Context context = connectLatency.time()) {
                LOGGER.debug("Connecting to <[{}:{}]>", targetConfiguration.hostname(), targetConfiguration.port());
                relpConnection = connectionFactory.get();
                long connectionTries = relpConnection.connect();
                connected = true;
                LOGGER.debug("Connected after <{}> tries", connectionTries);
                connects.inc(connectionTries);
            }
            catch (IOException e) {
                LOGGER
                        .warn(
                                "Failed to connect to <[{}:{}]>: <{}>", targetConfiguration.hostname(),
                                targetConfiguration.port(), e.getMessage()
                        );
            }
            if (!connected) {
                try {
                    LOGGER.debug("Sleeping for <[{}]>ms before reconnecting", targetConfiguration.reconnectInterval());
                    TimeUnit.MILLISECONDS.sleep(targetConfiguration.reconnectInterval());
                    retriedConnects.inc();
                }
                catch (InterruptedException e) {
                    LOGGER.warn("Sleep was interrupted: <{}>", e.getMessage());
                }
            }
        }
    }

    public void stop() {
        LOGGER.debug("Stop called");
        stayRunning.set(false);
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                LOGGER.error("Timed out while waiting for probe to shutdown.");
                throw new RuntimeException("Timed out while waiting for probe to shutdown.");
            }
        }
        catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for latch countdown");
            throw new RuntimeException(e);
        }
        LOGGER.debug("RelpProbe stopped.");
    }
}
