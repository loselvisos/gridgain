/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.testsuites;

import junit.framework.*;
import org.gridgain.grid.kernal.processors.cache.*;

/**
 * Test suite for cache DGC.
 */
public class GridCacheDgcSelfTestSuite extends TestSuite {
    /**
     * @return Cache DGC test suite.
     * @throws Exception If failed.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = new TestSuite("Gridgain Cache DGC Test Suite");

        suite.addTest(new TestSuite(GridCacheDgcManagerNonTxSelfTest.class));
        suite.addTest(new TestSuite(GridCacheDgcManagerReportLocksSelfTest.class));
        suite.addTest(new TestSuite(GridCacheDgcManagerTxOnDemandSelfTest.class));
        suite.addTest(new TestSuite(GridCacheDgcManagerTxSelfTest.class));
        suite.addTest(new TestSuite(GridCacheDgcManagerNonTxNearDisabledSelfTest.class));

        return suite;
    }
}