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

import com.codahale.metrics.MetricRegistry;
import com.teragrep.cnf_01.PathConfiguration;
import com.teragrep.net_01.channel.socket.PlainFactory;
import com.teragrep.net_01.channel.socket.TLSFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.net_01.server.Server;
import com.teragrep.net_01.server.ServerFactory;
import com.teragrep.rlp_01.client.RelpConfig;
import com.teragrep.rlp_01.client.RelpConnectionFactory;
import com.teragrep.rlp_01.client.SSLContextSupplier;
import com.teragrep.rlp_01.client.SSLContextSupplierKeystore;
import com.teragrep.rlp_01.client.SocketConfig;
import com.teragrep.rlp_01.client.SocketConfigImpl;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameDelegate;
import com.teragrep.rlp_11.Configuration.ProbeConfiguration;
import com.teragrep.rlp_11.Configuration.MetricsConfiguration;
import com.teragrep.rlp_11.Configuration.SocketConfiguration;
import com.teragrep.rlp_11.Configuration.TLSConfiguration;
import com.teragrep.rlp_11.Configuration.TargetConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConnectionTest {

    private final int serverPort = 12345;
    private final int tlsServerPort = 12346;
    private Thread eventLoopThread;
    private EventLoop eventLoop;
    private ThreadPoolExecutor threadPoolExecutor;
    private final List<String> records = new ArrayList<>();
    private Server server;
    private Server tlsServer;

    @BeforeEach
    public void startServer() {
        final Map<String, String> tlsConfig = Assertions
                .assertDoesNotThrow(() -> new PathConfiguration("src/test/resources/tls-connect.properties").asMap());
        EventLoopFactory eventLoopFactory = new EventLoopFactory();
        eventLoop = Assertions.assertDoesNotThrow(eventLoopFactory::create);

        eventLoopThread = new Thread(eventLoop);
        eventLoopThread.start();

        Supplier<FrameDelegate> frameDelegateSupplier = () -> new DefaultFrameDelegate((frameContext) -> {
            // Adds random latency before finishing and acking the event
            Assertions.assertDoesNotThrow(() -> Thread.sleep((long) (Math.random() * 500)));
            records.add(frameContext.relpFrame().payload().toString());
        });

        threadPoolExecutor = new ThreadPoolExecutor(
                1,
                1,
                Long.MAX_VALUE,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                threadPoolExecutor,
                new PlainFactory(),
                new FrameDelegationClockFactory(frameDelegateSupplier)
        );

        final TLSConfiguration tlsConfiguration = new TLSConfiguration(tlsConfig);
        final SSLContextSupplier sslContextSupplier = new SSLContextSupplierKeystore(
                tlsConfiguration.keyStorePath(),
                tlsConfiguration.keyStorePassword(),
                tlsConfiguration.protocol()
        );
        final SSLContext context = sslContextSupplier.get();
        final Function<SSLContext, SSLEngine> sslEngineFunction = (ctx) -> {
            final SSLEngine sslEngine = ctx.createSSLEngine();
            sslEngine.setUseClientMode(false);
            return sslEngine;
        };
        final ServerFactory tlsServerFactory = new ServerFactory(
                eventLoop,
                threadPoolExecutor,
                new TLSFactory(context, sslEngineFunction),
                new FrameDelegationClockFactory(frameDelegateSupplier)
        );
        server = Assertions.assertDoesNotThrow(() -> serverFactory.create(serverPort));
        tlsServer = Assertions.assertDoesNotThrow(() -> tlsServerFactory.create(tlsServerPort));
    }

    @AfterEach
    public void stopServer() {
        eventLoop.stop();
        threadPoolExecutor.shutdown();
        Assertions.assertDoesNotThrow(() -> eventLoopThread.join());
        Assertions.assertDoesNotThrow(server::close);
        Assertions.assertDoesNotThrow(tlsServer::close);
        records.clear();
    }

    @Test
    public void connectToServerTest() {
        Map<String, String> map = Assertions
                .assertDoesNotThrow(() -> new PathConfiguration("src/test/resources/connect.properties").asMap());
        final ProbeConfiguration probeConfiguration = new ProbeConfiguration(map);
        final RecordFactory recordFactory = new RecordFactory("localhost", "rlp_11", "rlp_11");
        final TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        final MetricsConfiguration metricsConfiguration = new MetricsConfiguration(map);
        final SocketConfiguration socketConfiguration = new SocketConfiguration(map);

        final RelpConfig relpConfig = new RelpConfig(
                targetConfiguration.hostname(),
                targetConfiguration.port(),
                targetConfiguration.reconnectInterval(),
                5,
                true,
                Duration.ofSeconds(10),
                true
        );

        final SocketConfig socketConfig = new SocketConfigImpl(
                socketConfiguration.readTimeout(),
                socketConfiguration.writeTimeout(),
                socketConfiguration.connectTimeout(),
                socketConfiguration.keepAlive()
        );

        final RelpConnectionFactory relpConnectionFactory = new RelpConnectionFactory(relpConfig, socketConfig);
        RelpProbe relpProbe = new RelpProbe(
                relpConnectionFactory,
                targetConfiguration,
                probeConfiguration,
                metricsConfiguration,
                recordFactory,
                new MetricRegistry()
        );

        TimerTask task = new TimerTask() {

            public void run() {
                relpProbe.stop();
            }
        };
        Timer timer = new Timer("Timer");
        timer.schedule(task, 5_000L);

        relpProbe.start();
    }

    @Test
    public void connectToTSLServerTest() {
        final Map<String, String> map = Assertions
                .assertDoesNotThrow(() -> new PathConfiguration("src/test/resources/tls-connect.properties").asMap());
        final ProbeConfiguration probeConfiguration = new ProbeConfiguration(map);
        final RecordFactory recordFactory = new RecordFactory("localhost", "rlp_11", "rlp_11");
        final TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        final MetricsConfiguration metricsConfiguration = new MetricsConfiguration(map);
        final TLSConfiguration tlsConfiguration = new TLSConfiguration(map);
        final MetricRegistry metricRegistry = new MetricRegistry();
        final SocketConfiguration socketConfiguration = new SocketConfiguration(map);

        final RelpConfig relpConfig = new RelpConfig(
                targetConfiguration.hostname(),
                targetConfiguration.port(),
                targetConfiguration.reconnectInterval(),
                5,
                true,
                Duration.ofSeconds(10),
                true
        );
        final SocketConfig socketConfig = new SocketConfigImpl(
                socketConfiguration.readTimeout(),
                socketConfiguration.writeTimeout(),
                socketConfiguration.connectTimeout(),
                socketConfiguration.keepAlive()
        );
        final SSLContextSupplier sslContextSupplier = new SSLContextSupplierKeystore(
                tlsConfiguration.keyStorePath(),
                tlsConfiguration.keyStorePassword(),
                tlsConfiguration.protocol()
        );
        final RelpConnectionFactory relpConnectionFactory = new RelpConnectionFactory(
                relpConfig,
                socketConfig,
                sslContextSupplier
        );
        final RelpProbe relpProbe = new RelpProbe(
                relpConnectionFactory,
                targetConfiguration,
                probeConfiguration,
                metricsConfiguration,
                recordFactory,
                metricRegistry
        );
        final TimerTask task = new TimerTask() {

            public void run() {
                relpProbe.stop();
            }
        };
        final Timer timer = new Timer("Timer");
        timer.schedule(task, 5_000L);
        Assertions.assertDoesNotThrow(relpProbe::start, "starting probe should not throw exceptions");
        final long connectCount = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "connects")).getCount();
        final long disconnectCount = metricRegistry
                .counter(MetricRegistry.name(RelpProbe.class, "disconnects"))
                .getCount();
        final long retriedConnects = metricRegistry
                .counter(MetricRegistry.name(RelpProbe.class, "retriedConnects"))
                .getCount();
        final long resends = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "resends")).getCount();
        final long records = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "records")).getCount();
        Assertions.assertEquals(1, connectCount, "probe should connect once");
        Assertions.assertEquals(1, disconnectCount, "probe should disconnect once");
        Assertions.assertEquals(0, retriedConnects, "probe should not have to retry to connect");
        Assertions.assertEquals(0, resends, "probe should not have to resend");
        // exact count depends on scheduling variations, assert that there are records sent
        Assertions.assertTrue(records > 0, "probe should have at least one record sent during timer");
    }

    /**
     * Connection starts initially closed and then opened after some connect retries have run
     */
    @Test
    public void connectToTSLServerRetryTest() {
        // configuration
        final Map<String, String> map = Assertions
                .assertDoesNotThrow(() -> new PathConfiguration("src/test/resources/tls-connect.properties").asMap());
        final ProbeConfiguration probeConfiguration = new ProbeConfiguration(map);
        final RecordFactory recordFactory = new RecordFactory("localhost", "rlp_11", "rlp_11");
        final TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        final MetricsConfiguration metricsConfiguration = new MetricsConfiguration(map);
        final TLSConfiguration tlsConfiguration = new TLSConfiguration(map);
        final MetricRegistry metricRegistry = new MetricRegistry();
        final SocketConfiguration socketConfiguration = new SocketConfiguration(map);

        // override TLS port
        final RelpConfig relpConfig = new RelpConfig(
                targetConfiguration.hostname(),
                tlsServerPort,
                targetConfiguration.reconnectInterval(),
                5,
                true,
                Duration.ofSeconds(10),
                true
        );
        final SocketConfig socketConfig = new SocketConfigImpl(
                socketConfiguration.readTimeout(),
                socketConfiguration.writeTimeout(),
                socketConfiguration.connectTimeout(),
                socketConfiguration.keepAlive()
        );
        // tls enabled factory
        final SSLContextSupplier sslContextSupplier = new SSLContextSupplierKeystore(
                tlsConfiguration.keyStorePath(),
                tlsConfiguration.keyStorePassword(),
                tlsConfiguration.protocol()
        );
        final RelpConnectionFactory relpConnectionFactory = new RelpConnectionFactory(
                relpConfig,
                socketConfig,
                sslContextSupplier
        );
        final RelpProbe relpProbe = new RelpProbe(
                relpConnectionFactory,
                targetConfiguration,
                probeConfiguration,
                metricsConfiguration,
                recordFactory,
                metricRegistry
        );
        // close server for initial connection attempts
        Assertions.assertDoesNotThrow(() -> tlsServer.close());

        // allow OS to release port and tear down socket
        Assertions.assertDoesNotThrow(() -> Thread.sleep(300));

        // start probe task, tries to connect to closed server
        final TimerTask stopTask = new TimerTask() {

            public void run() {
                relpProbe.stop();
            }
        };

        // stop probe after fixed time
        final Timer timer = new Timer("Timer");
        timer.schedule(stopTask, 5000L);
        Thread probeThread = new Thread(relpProbe::start);
        probeThread.start();

        // await for retry attempts
        Assertions.assertDoesNotThrow(() -> Thread.sleep(2000));

        // Restart TLS server while probe is retrying
        Assertions.assertDoesNotThrow(() -> {
            tlsServer = new ServerFactory(
                    eventLoop,
                    threadPoolExecutor,
                    new TLSFactory(sslContextSupplier.get(), ctx -> {
                        SSLEngine engine = ctx.createSSLEngine();
                        engine.setUseClientMode(false);
                        return engine;
                    }),
                    new FrameDelegationClockFactory(() -> new DefaultFrameDelegate(fc -> {
                    }))
            ).create(tlsServerPort);
        });

        // allow probe to reconnect successfully after server start
        Assertions.assertDoesNotThrow(() -> Thread.sleep(1500));

        // ensure probe thread has stopped before asserting metrics
        Assertions.assertDoesNotThrow(() -> probeThread.join(10000L), "probe thread should finish before 10s timeout");
        final long connects = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "connects")).getCount();
        final long retries = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "retriedConnects")).getCount();
        Assertions.assertEquals(1, connects, "probe should connect successfully once");
        // retry attempts count is dependent on timing, assert that there were some retry attempts
        Assertions.assertTrue(retries > 0, "probe should have retry attempts");
    }

    /**
     * Uses custom server and port that simulates connection drop on the first message
     */
    @Test
    public void ensureSentRetryBranchIsCoveredWithStallingTest() {
        final int isolatedResendPort = 10605;
        final Map<String, String> map = Assertions
                .assertDoesNotThrow(() -> new PathConfiguration("src/test/resources/tls-connect.properties").asMap());
        final ProbeConfiguration probeConfiguration = new ProbeConfiguration(map);
        // unique signature string to identify test related batches
        final String uniqueSignature = "Resend-Test-Signature";
        final RecordFactory recordFactory = new RecordFactory("localhost", uniqueSignature, uniqueSignature);
        final TargetConfiguration targetConfiguration = new TargetConfiguration(map);
        final MetricsConfiguration metricsConfiguration = new MetricsConfiguration(map);
        final TLSConfiguration tlsConfiguration = new TLSConfiguration(map);
        final MetricRegistry metricRegistry = new MetricRegistry();
        final SocketConfiguration socketConfiguration = new SocketConfiguration(map);

        final RelpConfig relpConfig = new RelpConfig(
                targetConfiguration.hostname(),
                isolatedResendPort,
                targetConfiguration.reconnectInterval(),
                5,
                true,
                Duration.ofMillis(500),
                true
        );

        final SocketConfig socketConfig = new SocketConfigImpl(
                socketConfiguration.readTimeout(),
                socketConfiguration.writeTimeout(),
                socketConfiguration.connectTimeout(),
                socketConfiguration.keepAlive()
        );

        final SSLContextSupplier sslContextSupplier = new SSLContextSupplierKeystore(
                tlsConfiguration.keyStorePath(),
                tlsConfiguration.keyStorePassword(),
                tlsConfiguration.protocol()
        );
        final AtomicInteger logMessageCount = new AtomicInteger(0);
        final Supplier<FrameDelegate> frameDelegateSupplier = () -> new DefaultFrameDelegate(frameContext -> {
            final String payload = frameContext.relpFrame().payload().toString();

            // filter our unique test syslog record payloads
            if (payload != null && payload.contains(uniqueSignature)) {
                final int currentCount = logMessageCount.incrementAndGet();

                // simulate connection drop on first message
                if (currentCount == 1) {
                    throw new RuntimeException("Simulating transient connection drop mid-flight");
                }
            }
        });
        final Timer shutdownTimer = new Timer("ProbeShutdownTimer");
        final Server server = Assertions
                .assertDoesNotThrow(
                        () -> new ServerFactory(
                                eventLoop,
                                threadPoolExecutor,
                                new TLSFactory(sslContextSupplier.get(), ctx -> {
                                    SSLEngine engine = ctx.createSSLEngine();
                                    engine.setUseClientMode(false);
                                    return engine;
                                }),
                                new FrameDelegationClockFactory(frameDelegateSupplier)
                        ).create(isolatedResendPort)
                );
        final RelpConnectionFactory relpConnectionFactory = new RelpConnectionFactory(
                relpConfig,
                socketConfig,
                sslContextSupplier
        );
        final RelpProbe relpProbe = new RelpProbe(
                relpConnectionFactory,
                targetConfiguration,
                probeConfiguration,
                metricsConfiguration,
                recordFactory,
                metricRegistry
        );

        final TimerTask stopTask = new TimerTask() {

            public void run() {
                relpProbe.stop();
            }
        };
        shutdownTimer.schedule(stopTask, 3000L);
        final Thread probeThread = new Thread(relpProbe::start);
        probeThread.start();
        Assertions.assertDoesNotThrow(() -> probeThread.join(10000L), "probe thread should exit cleanly.");
        final long resends = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "resends")).getCount();
        final long recordsCount = metricRegistry.counter(MetricRegistry.name(RelpProbe.class, "records")).getCount();
        // ensure at least 1 event, results depend on the timings
        Assertions.assertTrue(recordsCount > 0, "probe should have processed at least one record");
        Assertions.assertTrue(resends > 0, "probe should have resend attempts");
        shutdownTimer.cancel();
        Assertions.assertDoesNotThrow(server::close);
    }
}
