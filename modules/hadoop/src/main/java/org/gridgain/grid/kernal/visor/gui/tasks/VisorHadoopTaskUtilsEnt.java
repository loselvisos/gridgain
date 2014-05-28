/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.visor.gui.tasks;

import org.gridgain.grid.GridException;
import org.gridgain.grid.ggfs.GridGgfs;
import org.gridgain.grid.ggfs.GridGgfsMode;
import org.gridgain.grid.kernal.processors.ggfs.GridGgfsEx;
import org.gridgain.grid.kernal.visor.cmd.VisorTaskUtils;
import org.gridgain.grid.util.GridUtils;
import org.gridgain.grid.util.typedef.F;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import static org.gridgain.grid.ggfs.GridGgfsConfiguration.*;
import static org.gridgain.grid.kernal.ggfs.hadoop.GridGgfsHadoopLogger.*;

/**
 * Contains utility methods for Visor tasks and jobs.
 */
@SuppressWarnings("ExtendsUtilityClass")
public class VisorHadoopTaskUtilsEnt extends VisorTaskUtils {
    // Named column indexes in log file.
    public static final int LOG_COL_TIMESTAMP = 0;
    public static final int LOG_COL_THREAD_ID = 1;
    public static final int LOG_COL_ENTRY_TYPE = 3;
    public static final int LOG_COL_PATH = 4;
    public static final int LOG_COL_GGFS_MODE = 5;
    public static final int LOG_COL_STREAM_ID = 6;
    public static final int LOG_COL_DATA_LEN = 8;
    public static final int LOG_COL_APPEND = 9;
    public static final int LOG_COL_OVERWRITE = 10;
    public static final int LOG_COL_POS = 13;
    public static final int LOG_COL_READ_LEN = 14;
    public static final int LOG_COL_USER_TIME = 17;
    public static final int LOG_COL_SYSTEM_TIME = 18;
    public static final int LOG_COL_TOTAL_BYTES = 19;

    /** List of log entries that should be parsed. */
    public static final Set<Integer> LOG_TYPES = F.asSet(
            TYPE_OPEN_IN,
            TYPE_OPEN_OUT,
            TYPE_RANDOM_READ,
            TYPE_CLOSE_IN,
            TYPE_CLOSE_OUT
    );

    /**
     * Resolve GGFS profiler logs directory.
     *
     * @param ggfs GGFS instance to resolve logs dir for.
     * @return `Some(Path)` to log dir or `None` if not found.
     * @throws org.gridgain.grid.GridException if failed to resolve.
     */
    public static Path resolveGgfsProfilerLogsDir(GridGgfs ggfs) throws GridException {
        String logsDir;

        if (ggfs instanceof GridGgfsEx)
            logsDir = ((GridGgfsEx) ggfs).clientLogDirectory();
        else if (ggfs == null)
            throw new GridException("Failed to get profiler log folder (GGFS instance not found)");
        else
            throw new GridException("Failed to get profiler log folder (unexpected GGFS instance type)");

        URL logsDirUrl = GridUtils.resolveGridGainUrl(logsDir != null ? logsDir : DFLT_GGFS_LOG_DIR);

        if (logsDirUrl != null)
            return new File(logsDirUrl.getPath()).toPath();
        else
            return null;
    }
}
