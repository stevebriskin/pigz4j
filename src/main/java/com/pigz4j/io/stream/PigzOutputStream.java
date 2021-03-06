package com.pigz4j.io.stream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extends buffered stream to re-use the buffering implementation, which in turn
 * ensures that at least <code>pBufferSize</code> is passed to deflate.
 *
 * The buffer size used should be relatively high for reasonable compression.
 */
public class PigzOutputStream extends BufferedOutputStream {

    /**
     * Default size of buffered output stream.
     */
    public static final int DEFAULT_BUFSZ = 1 << 17; // 128kB

    /**
     * Default size of buffered output stream.
     */
    public static final int DEFAULT_BLOCKSZ = 1 << 17; // 128kB

    static final Logger LOG = Logger.getLogger( PigzOutputStream.class.getName() );

    private final PigzDeflaterOutputStream _deflaterDelegate;

    private boolean _closed = false;

    public PigzOutputStream(final OutputStream pOut) throws IOException {
        this(pOut, DEFAULT_BUFSZ);
    }

    public PigzOutputStream(final OutputStream pOut, final int pBufferSize) throws IOException {
        this(pOut, pBufferSize, DEFAULT_BLOCKSZ);
    }

    public PigzOutputStream(final OutputStream pOut, final int pBufferSize, final int pBlockSize) throws IOException {
        this(pOut, pBufferSize, pBlockSize, PigzDeflaterFactory.DEFAULT, getDefaultExecutorService());
    }

    public PigzOutputStream(final OutputStream pOut, final int pBufferSize, final int pBlockSize,
                            final IPigzDeflaterFactory pDeflaterFactory,
                            final ExecutorService pExecutorService) throws IOException {
        this(new PigzDeflaterOutputStream(pOut, pBlockSize, pDeflaterFactory, pExecutorService), pBufferSize);
    }

    private PigzOutputStream(final PigzDeflaterOutputStream pOut, final int pBufferSize) throws IOException {
        super(pOut, pBufferSize);
        _deflaterDelegate = pOut;
    }

    public long getTotalIn() {
        return _deflaterDelegate.getTotalIn();
    }

    public long getTotalOut() {
        return _deflaterDelegate.getTotalOut();
    }

    public double getCompressionRatio() {
        return _deflaterDelegate.getCompressionRatio();
    }

    /**
     * Waits Long.MAX_VALUE millis (indefinitely) for completion of all compression threads.
     * @throws IOException
     */
    public void finish() throws IOException {
        finish(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    /**
     * Waits up to specified amount of time for completion of all compression threads.
     * @throws IOException
     */
    public void finish(final long pTime, final TimeUnit pUnit) throws IOException {
        // flush any remaining buffered stream data to deflater.
        flush();

        _deflaterDelegate.finish(pTime, pUnit);
    }

    @Override
    public void close() throws IOException {
        close( !_deflaterDelegate.isDefaultExecutor() );
    }

    public void close(boolean pShutDownExecutor) throws IOException {
        if ( !_closed ) {
            LOG.log(Level.FINE, "Closing output stream shutdownExecutor={0}", pShutDownExecutor);
            finish();

            _deflaterDelegate.close(pShutDownExecutor);
            _closed = true;
        }
    }

    private static class ServiceHolder {
        public static final ExecutorService SERVICE =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), DAEMON_FACTORY);
    }

    public static ExecutorService getDefaultExecutorService() {
        return ServiceHolder.SERVICE;
    }

    public static final ThreadFactory DAEMON_FACTORY = new DaemonThreadFactory();

    private static final class DaemonThreadFactory implements ThreadFactory {
        static final AtomicInteger SERIAL = new AtomicInteger(0);
        static final String NAME_PREFIX = "pigz4j-GzipWorker-";
        final ThreadGroup _group;

        public DaemonThreadFactory() {
            this(Thread.currentThread().getThreadGroup());
        }

        public DaemonThreadFactory(final ThreadGroup pThreadGroup) {
            _group = pThreadGroup;
        }

        public Thread newThread(final Runnable pRunnable) {
            final Thread thread = new Thread(pRunnable, NAME_PREFIX + SERIAL.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

}
