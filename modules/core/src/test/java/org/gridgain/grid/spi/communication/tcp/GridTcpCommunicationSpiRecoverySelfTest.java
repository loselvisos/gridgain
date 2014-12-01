/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.communication.tcp;

import org.gridgain.grid.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.communication.*;
import org.gridgain.grid.util.direct.*;
import org.gridgain.grid.util.lang.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.junits.*;
import org.gridgain.testframework.junits.spi.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 *
 */
@GridSpiTest(spi = GridTcpCommunicationSpi.class, group = "Communication SPI")
public class GridTcpCommunicationSpiRecoverySelfTest<T extends GridCommunicationSpi> extends GridSpiAbstractTest<T> {
    /** */
    private static final Collection<GridTestResources> spiRsrcs = new ArrayList<>();

    /** */
    protected static final List<GridTcpCommunicationSpi> spis = new ArrayList<>();

    /** */
    protected static final List<GridNode> nodes = new ArrayList<>();

    /** */
    private static final int SPI_CNT = 2;

    /**
     *
     */
    static {
        GridTcpCommunicationMessageFactory.registerCustom(new GridTcpCommunicationMessageProducer() {
            @Override public GridTcpCommunicationMessageAdapter create(byte type) {
                return new GridTestMessage();
            }
        }, GridTestMessage.DIRECT_TYPE);
    }

    /** */
    @SuppressWarnings({"deprecation"})
    private class TestListener implements GridCommunicationListener<GridTcpCommunicationMessageAdapter> {
        /** */
        private boolean block;

        /** */
        private CountDownLatch blockLatch;

        /** */
        private Set<Long> msgIds = new HashSet<>();

        /** */
        private AtomicInteger rcvCnt = new AtomicInteger();

        /** {@inheritDoc} */
        @Override public void onMessage(UUID nodeId, GridTcpCommunicationMessageAdapter msg, GridRunnable msgC) {
            info("Test listener received message: " + msg);

            assertTrue("Unexpected message: " + msg, msg instanceof GridTestMessage);

            GridTestMessage msg0 = (GridTestMessage)msg;

            assertTrue("Duplicated message received: " + msg0, msgIds.add(msg0.getMsgId()));

            rcvCnt.incrementAndGet();

            msgC.run();

            try {
                synchronized (this) {
                    while (block) {
                        info("Test listener blocks.");

                        assert blockLatch != null;

                        blockLatch.countDown();

                        wait();

                        if (block)
                            continue;

                        info("Test listener throws exception.");

                        throw new RuntimeException("Test exception.");
                    }
                }
            }
            catch (InterruptedException e) {
                fail("Unexpected error: " + e);
            }
        }

        /**
         *
         */
        void block() {
            synchronized (this) {
                block = true;

                blockLatch = new CountDownLatch(1);
            }
        }

        /**
         *
         */
        void unblock() {
            synchronized (this) {
                block = false;

                notifyAll();
            }
        }

        /** {@inheritDoc} */
        @Override public void onDisconnected(UUID nodeId) {
            // No-op.
        }
    }

    /**
     * @return Value for {@link GridTcpCommunicationSpi#isDualSocketConnection()} property.
     */
    protected boolean dualSocket() {
        return false;
    }

    /**
     * @throws Exception If failed.
     */
    public void test() throws Exception {
        // Test listener throws exception and stops selector thread, so must restart SPI.
        for (int i = 0; i < 10; i++) {
            log.info("Creating SPIs: " + i);

            createSpis();

            try {
                checkResend();
            }
            finally {
                stopSpis();
            }
        }
    }

    /**
     * @throws Exception If failed.
     */
    @SuppressWarnings("BusyWait")
    private void checkResend() throws Exception {
        GridTcpCommunicationSpi spi0 = spis.get(0);
        GridTcpCommunicationSpi spi1 = spis.get(1);

        final TestListener lsnr1 = (TestListener)spi1.getListener();

        GridNode node0 = nodes.get(0);

        GridNode node1 = nodes.get(1);

        int msgId = 0;

        for (int i = 0; i < 5; i++) {
            log.info("Iteration: " + i);

            lsnr1.block();

            for (int j = 0; j < 10; j++)
                spi0.sendMessage(node1, new GridTestMessage(node0.id(), ++msgId, 0));

            lsnr1.blockLatch.await();

            lsnr1.unblock();

            Thread.sleep(500);

            int errCnt = 0;

            int msgs = 0;

            while (true) {
                try {
                    int id = msgId + 1;

                    spi0.sendMessage(node1, new GridTestMessage(node0.id(), id, 0));

                    msgId++;

                    msgs++;

                    if (msgs == 10)
                        break;
                }
                catch (GridSpiException e) {
                    errCnt++;

                    if (errCnt > 10)
                        fail("Failed to send message: " + e);
                }
            }

            final int expMsgs = (i + 1) * 20;

            GridTestUtils.waitForCondition(new GridAbsPredicate() {
                @Override public boolean apply() {
                    return lsnr1.rcvCnt.get() >= expMsgs;
                }
            }, 5000);

            assertEquals(expMsgs, lsnr1.rcvCnt.get());
        }
    }

    /**
     * @param idx SPI index.
     * @return SPI instance.
     */
    protected GridTcpCommunicationSpi getSpi(int idx) {
        GridTcpCommunicationSpi spi = new GridTcpCommunicationSpi();

        spi.setSharedMemoryPort(-1);
        spi.setLocalPort(GridTestUtils.getNextCommPort(getClass()));
        spi.setIdleConnectionTimeout(10_000);
        spi.setTcpNoDelay(true);
        spi.setDualSocketConnection(dualSocket());
        spi.setRecoveryAcknowledgementCount(5);
        spi.setSelectorsCount(10);

        return spi;
    }

    /**
     * @throws Exception If failed.
     */
    private void createSpis() throws Exception {
        spis.clear();
        nodes.clear();
        spiRsrcs.clear();

        Map<GridNode, GridSpiTestContext> ctxs = new HashMap<>();

        for (int i = 0; i < SPI_CNT; i++) {
            GridTcpCommunicationSpi spi = getSpi(i);

            GridTestUtils.setFieldValue(spi, "gridName", "grid-" + i);

            GridTestResources rsrcs = new GridTestResources();

            GridTestNode node = new GridTestNode(rsrcs.getNodeId());

            GridSpiTestContext ctx = initSpiContext();

            ctx.setLocalNode(node);

            spiRsrcs.add(rsrcs);

            rsrcs.inject(spi);

            spi.setListener(new TestListener());

            node.setAttributes(spi.getNodeAttributes());

            nodes.add(node);

            spi.spiStart(getTestGridName() + (i + 1));

            spis.add(spi);

            spi.onContextInitialized(ctx);

            ctxs.put(node, ctx);
        }

        // For each context set remote nodes.
        for (Map.Entry<GridNode, GridSpiTestContext> e : ctxs.entrySet()) {
            for (GridNode n : nodes) {
                if (!n.equals(e.getKey()))
                    e.getValue().remoteNodes().add(n);
            }
        }
    }

    /**
     * @throws Exception If failed.
     */
    private void stopSpis() throws Exception {
        for (GridCommunicationSpi<GridTcpCommunicationMessageAdapter> spi : spis) {
            spi.setListener(null);

            spi.spiStop();
        }

        for (GridTestResources rsrcs : spiRsrcs) {
            rsrcs.stopThreads();
        }

        spis.clear();
        nodes.clear();
        spiRsrcs.clear();
    }
}
