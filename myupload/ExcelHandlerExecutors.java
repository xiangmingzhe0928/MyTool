import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;


@Slf4j
public class ExcelHandlerExecutors {

    /**
     * 核心线程数
     */
    private static final int CORE_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * 线程池名称
     */
    private static final String THREAD_POOL_NAME = "ExcelHandlerPool-%d";

    /**
     * 线程工厂名称
     */
    private static final ThreadFactory FACTORY = new ThreadFactoryBuilder().setNameFormat(THREAD_POOL_NAME).build();

    /**
     * 空闲线程存活时间
     */
    private static final long KEEP_ALIVE = 60L;

    /**
     * Executor
     */
    private static ExecutorService executor;

    /**
     * 执行队列
     * <p>考虑实际导入导出场景 300已经足够<p/>
     */
    private static BlockingQueue<Runnable> executeQueue = new ArrayBlockingQueue<>(300);

    /**
     * 拒绝策略：DiscardOldestPolicy
     */
    private static RejectedExecutionHandler rejectedHandler = new ThreadPoolExecutor.DiscardOldestPolicy();

    static {
        try {
            executor = new ThreadPoolExecutor(CORE_SIZE, CORE_SIZE * 4, KEEP_ALIVE, TimeUnit.SECONDS, executeQueue, FACTORY, rejectedHandler);
        } catch (Exception e) {
            log.error("ExcelHandlerPool init error", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private ExcelHandlerExecutors() {}

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        try {
            executor.shutdown();
            log.info("ExcelHandlerExecutors shutdown success");
        } catch (Exception e) {
            log.warn("ExcelHandlerExecutors shutdown failed. Starting shutdowNow...");
            executor.shutdownNow();
        }
    }

    /**
     * {@link Executor#execute(Runnable)}
     * @param task
     */
    public static void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (Exception e) {
            log.error("ExcelTask failed ", e);
            throw new RuntimeException("ExcelTask execute error");
        }
    }

    /**
     * {@link ExecutorService#submit(Callable)}
     * @param task
     * @param <T>
     * @return
     */
    public static <T> Future<T> submit(Callable<T> task) {
        try {
            return executor.submit(task);
        }catch (Exception e) {
            log.error("ExcelTask failed ", e);
            throw new RuntimeException("ExcelTask execute error");
        }
    }
}
