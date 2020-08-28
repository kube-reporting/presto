/*
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
package io.prestosql.plugin.hive.rubix;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.qubole.rubix.bookkeeper.BookKeeper;
import com.qubole.rubix.bookkeeper.BookKeeperServer;
import com.qubole.rubix.bookkeeper.LocalDataTransferServer;
import com.qubole.rubix.core.CachingFileSystem;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.prestosql.plugin.base.CatalogName;
import io.prestosql.plugin.hive.HdfsConfigurationInitializer;
import io.prestosql.plugin.hive.util.RetryDriver;
import io.prestosql.spi.Node;
import io.prestosql.spi.NodeManager;
import io.prestosql.spi.PrestoException;
import org.apache.hadoop.conf.Configuration;

import javax.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.airlift.concurrent.MoreFutures.addSuccessCallback;
import static io.prestosql.plugin.hive.util.ConfigurationUtils.getInitialConfiguration;
import static io.prestosql.plugin.hive.util.RetryDriver.DEFAULT_SCALE_FACTOR;
import static io.prestosql.plugin.hive.util.RetryDriver.retry;
import static io.prestosql.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static java.lang.Integer.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * Responsibilities of this initializer:
 * 1. Lazily setup RubixConfigurationInitializer with information about master when it is available
 * 2. Start Rubix Servers.
 * 3. Inject BookKeeper object into CachingFileSystem class
 */
public class RubixInitializer
{
    private static final RetryDriver DEFAULT_COORDINATOR_RETRY_DRIVER = retry()
            // unlimited attempts
            .maxAttempts(MAX_VALUE)
            .exponentialBackoff(
                    new Duration(1, SECONDS),
                    new Duration(1, SECONDS),
                    // wait for 10 minutes
                    new Duration(10, MINUTES),
                    DEFAULT_SCALE_FACTOR);

    private static final Logger log = Logger.get(RubixInitializer.class);

    private final CatalogName catalogName;
    private final RubixConfigurationInitializer rubixConfigurationInitializer;
    private final HdfsConfigurationInitializer hdfsConfigurationInitializer;

    @Inject
    public RubixInitializer(
            CatalogName catalogName,
            RubixConfigurationInitializer rubixConfigurationInitializer,
            HdfsConfigurationInitializer hdfsConfigurationInitializer)
    {
        this.catalogName = catalogName;
        this.rubixConfigurationInitializer = rubixConfigurationInitializer;
        this.hdfsConfigurationInitializer = hdfsConfigurationInitializer;
    }

    public void initializeRubix(NodeManager nodeManager)
    {
        ExecutorService initializerService = Executors.newSingleThreadExecutor();
        ListenableFuture<?> nodeJoinFuture = MoreExecutors.listeningDecorator(initializerService).submit(() ->
                DEFAULT_COORDINATOR_RETRY_DRIVER.run(
                        "waitForCoordinator",
                        () -> {
                            if (!nodeManager.getAllNodes().contains(nodeManager.getCurrentNode()) ||
                                    !nodeManager.getAllNodes().stream().anyMatch(Node::isCoordinator)) {
                                throw new PrestoException(GENERIC_INTERNAL_ERROR, "No coordinator node available");
                            }
                            return null;
                        }));

        addSuccessCallback(
                nodeJoinFuture,
                () -> startRubix(nodeManager));
    }

    private void startRubix(NodeManager nodeManager)
    {
        Node master = nodeManager.getAllNodes().stream().filter(Node::isCoordinator).findFirst().get();
        boolean isMaster = nodeManager.getCurrentNode().isCoordinator();

        rubixConfigurationInitializer.setMaster(isMaster);
        rubixConfigurationInitializer.setMasterAddress(master.getHostAndPort());
        rubixConfigurationInitializer.setCurrentNodeAddress(nodeManager.getCurrentNode().getHost());

        Configuration configuration = getInitialConfiguration();
        // Perform standard HDFS configuration initialization.
        // This will also call out to RubixConfigurationInitializer but this will be no-op because
        // cacheReady is not yet set.
        hdfsConfigurationInitializer.initializeConfiguration(configuration);
        // Apply RubixConfigurationInitializer directly suppressing cacheReady check
        rubixConfigurationInitializer.updateConfiguration(configuration);

        MetricRegistry metricRegistry = new MetricRegistry();
        BookKeeperServer bookKeeperServer = new BookKeeperServer();
        BookKeeper bookKeeper = bookKeeperServer.startServer(configuration, metricRegistry);
        LocalDataTransferServer.startServer(configuration, metricRegistry, bookKeeper);

        CachingFileSystem.setLocalBookKeeper(bookKeeper, "catalog=" + catalogName);
        log.info("Rubix initialized successfully");
        rubixConfigurationInitializer.initializationDone();
    }
}
