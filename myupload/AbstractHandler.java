import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Date;
import java.util.UUID;

/**
 * @author mingzhe.xiang
 * @Description 异步导入导出抽象类
 * @Date 2021/8/3
 */
@Slf4j
public abstract class AbstractHandler {

    private RedisTemplate<String> cxRedisTemplate;
    private TransactionStation transactionStation;
    /**
     * RedisKey 此处无需复杂fmt CxRedisTemplate自动增强命名
     **/
    private static final String TASK_STATUS_FMT = "IMEX:TASK:%s";
    private static final long TASK_EXPIRE_SECONDS = 60 * 10;
    private static final long RESULT_EXPIRE_SECONDS = 60 * 3;
    /**
     * 默认单次导出行数
     **/
    private static final int COUNT_PER_DEFAULT = 50000;

    @SuppressWarnings("unchecked")
    public AbstractHandler(TransactionStation transactionStation) {
        this.transactionStation = transactionStation;
        this.cxRedisTemplate = (CxRedisTemplate<String>) ApplicationContextUtils.getBean("cxRedisTemplate");
    }

    /**
     * 导入/上传文件
     *
     * <p>资源存入中转站, 生成对应任务进入RUNNING状态等待执行
     *
     * @param file 资源数据
     * @return 任务状态redis Key
     */
    @SuppressWarnings("unchecked")
    public String upload(MultipartFile file) {
        String userName = LoginContext.getLoginContext().getUserName();
        final UserBaseInfo userBaseInfo = new UserBaseInfo();
        userBaseInfo.setUserName(userName);

        final String language = GlobalUtils.getLanguage();
        final String mart = MartContextManager.getCode();

        // Redis设置任务状态
        String taskKey = buildTaskKey();
        final TaskParam taskParam = TaskParam.builder().mart(mart).language(language).userBaseInfo(userBaseInfo).build();
        // 执行上传操作
        ExcelHandlerExecutors.execute(() -> executeUpload(file, taskKey, taskParam));
        return taskKey;
    }

    public String upload(MultipartFile file, TaskParam taskParam) {
        assert taskParam != null;

        // Redis设置任务状态
        String taskKey = buildTaskKey();
        // 执行上传操作
        ExcelHandlerExecutors.execute(() -> executeUpload(file, taskKey, taskParam));
        return taskKey;
    }

    /**
     * 异步执行导入任务
     *
     * @param file      资源K
     * @param taskKey   任务Key
     * @param taskParam 任务参数对象
     */
    @SuppressWarnings("unchecked")
    private void executeUpload(MultipartFile file, String taskKey, TaskParam taskParam) {

        TaskResultVo result = null;
        InputStream resourceStream = null;
        try {
            MartContextManager.setByCode(taskParam.getMart());
            resourceStream = file.getInputStream();
            result = handleUploadData(resourceStream, taskParam);
            log.info("Excel HandlerUpload executeUpload taskKey:{}, result:{}", taskKey, JSON.toJSONString(result));
        } catch (Exception e) {
            log.info("ExcelAbstractHandler executeUpload error: taskKey:{}", taskKey, e);
            result = TaskResultVo.failedResult();
        } finally {
            // 更新任务状态
            setRedis(taskKey, result, RESULT_EXPIRE_SECONDS);
            // 关闭资源
            try {
                if (null != resourceStream) {
                    resourceStream.close();
                }
            } catch (Exception e) {
                log.error("Clear TransactionStation Failed taskKey:{}", taskKey, e);
            }
        }
    }

    /**
     * 导出文件
     *
     * <p>资源存入中转站, 生成对应任务进入RUNNING状态等待执行
     *
     * @param query    查询条件
     * @param fileName 导出文件名
     * @return 任务Key
     */
    public String download(CxBaseQuery query, String fileName) {
        final String language = GlobalUtils.getLanguage();

        final UserBaseInfo userBaseInfo = new UserBaseInfo();
        String userName = LoginContext.getLoginContext().getUserName();
        userBaseInfo.setUserName(userName);

        final TaskParam taskParam = TaskParam.builder()
                .mart(MartContextManager.getCode())
                .language(language)
                .userBaseInfo(userBaseInfo)
                .query(query).build();
        String taskKey = buildTaskKey();
        ExcelHandlerExecutors.execute(() -> executeDownload(taskKey, fileName, taskParam));
        return taskKey;
    }

    /**
     * 异步执行导出任务
     *
     * @param taskKey   任务Key
     * @param fileName  导出文件名
     * @param taskParam 任务参数对象
     */
    @SuppressWarnings("unchecked")
    private void executeDownload(String taskKey, String fileName, TaskParam taskParam) {
        TaskResultVo result = null;
        File tempFile = null;
        OutputStream out = null;
        InputStream in = null;

        try {
            // 设置上下文
            setContextThread(taskParam);

            // 是否控制导出数量
            int thresholdCnt = countThreshold();
            if (supportCountLimit() && count(taskParam.getQuery()) > thresholdCnt) {
                result = TaskResultVo.failedResult(CxI18nUtil.translate("excel.abstractHandler.export.count.limit", thresholdCnt));
                return;
            }

            // 获取数据 写入临时文件
            tempFile = createTempFileWithDateTimeSuffix(fileName);
            out = new FileOutputStream(tempFile);
            handleDownloadData(out, taskParam);

            // 待导出文件推送到Station
            in = new FileInputStream(tempFile);
            String stationPath = transactionStation.pushResourceToStation(new StationResource(in, tempFile.getName(), taskParam.getLanguage()));
            // 包装成最终客户端能直接访问Url
            String downloadUrl = transactionStation.wrapperAccessUrl(stationPath);

            result = TaskResultVo.doneResult(new CxBatchResult(Lists.newArrayList(downloadUrl)));
            log.info("AbstractHandler executeDownload stationPath:{}, downloadUrl:{}", stationPath, downloadUrl);

        } catch (Exception e) {
            log.error("AbstractHandler executeDownload error", e);
            result = TaskResultVo.failedResult();
        } finally {
            if (log.isInfoEnabled()) {
                log.info("AbstractHandler executeDownload file:{}, Result:{}", fileName, JSON.toJSONString(result));
            }
            // 删除临时文件
            if (null != tempFile && tempFile.exists()) {
                if (log.isDebugEnabled()) {
                    log.debug("delete download tempFile:[path:{}, name:{}]", tempFile.getAbsolutePath(), tempFile.getName());
                }
                if (!tempFile.delete()) {
                    tempFile.deleteOnExit();
                }
            }
            // 关闭输入输出流
            try {
                if (null != out) {
                    out.close();
                }
                if (null != in) {
                    in.close();
                }
            } catch (Exception e) {
                log.error("close InPut/OutPut Stream error", e);
            }
            if (null == result) {
                result = TaskResultVo.failedResult();
            }
            //更新任务状态
            setRedis(taskKey, result, RESULT_EXPIRE_SECONDS);
        }
    }

    /**
     * 设置相关线程上下文信息
     *
     * @param taskParam
     */
    private void setContextThread(TaskParam taskParam) {
        MartContextManager.setByCode(taskParam.getMart());
        GlobalUtils.setLanguage(taskParam.getLanguage());
    }

    /**
     * 创建临时文件
     *
     * @param originFileName 原始文件名
     * @return 临时文件
     * @throws IOException
     */
    private File createTempFile(String originFileName) throws IOException {
        String baseName = FilenameUtils.getBaseName(originFileName);
        String originExtension = FilenameUtils.getExtension(originFileName);
        String extension = StringUtils.isBlank(originExtension) ? ".tmp" : String.format(".%s", originExtension);
        return File.createTempFile(baseName, extension);
    }

    private File createTempFileWithDateTimeSuffix(String originFileName) throws IOException {
        String baseName = FilenameUtils.getBaseName(originFileName);
        String originExtension = FilenameUtils.getExtension(originFileName);
        String extension = StringUtils.isBlank(originExtension) ? ".tmp" : String.format(".%s", originExtension);
        String dateTimeStr = DateUtil.formatDateTimeStr(new Date());
        return new File(baseName+"-"+ dateTimeStr+extension);
    }

    /**
     * 构造任务Key
     *
     * @return
     */
    private String buildTaskKey() {
        String taskKey = String.format(TASK_STATUS_FMT, UUID.randomUUID());
        boolean prepareRes = setRedis(taskKey, TaskResultVo.runningResult(), TASK_EXPIRE_SECONDS);

        if (!prepareRes) {
            throw new RuntimeException("upload task failed");
        }

        return taskKey;
    }


    /**
     * 任务结束后是否需要释放中转站资源
     *
     * @return
     */
    protected boolean clearStationResource() {
        return true;
    }

    /**
     * 处理上传数据
     *
     * @param inputStream 资源流
     * @param taskParam   任务参数对象
     * @return 处理结果
     * @throws Exception 上传失败抛出异常
     */
    protected abstract TaskResultVo handleUploadData(InputStream inputStream, TaskParam taskParam) throws Exception;

    /**
     * 处理下载数据
     *
     * @param outputStream 输出流
     * @param taskParam    任务参数对象
     * @return
     * @throws Exception 下载失败抛出异常
     */
    protected abstract void handleDownloadData(OutputStream outputStream, TaskParam taskParam) throws Exception;

    /**
     * 获取任务结果
     *
     * @param taskKey 任务Key
     * @return 任务结果对象
     */
    public TaskResultVo checkTaskStatus(String taskKey) {
        try {
            String taskJson = this.cxRedisTemplate.get(taskKey);
            return StringUtils.isBlank(taskJson) ? TaskResultVo.expiredResult() : JSON.parseObject(taskJson, TaskResultVo.class);
        } catch (Exception e) {
            return TaskResultVo.failedResult();
        }
    }

    private boolean setRedis(String key, TaskResultVo taskResultVo, long timeOutSec) {
        return this.cxRedisTemplate.set(key, JSON.toJSONString(taskResultVo), timeOutSec);
    }

    /**
     * 是否限制导出数量<p>
     * 子類自行覆蓋
     *
     * @return
     */
    protected boolean supportCountLimit() {
        return false;
    }

    /**
     * 每次最大导出量( {@code #supportCountLimit()}为 <i>true</>时 生效)<p>
     * 子類自行覆蓋
     *
     * @return
     */
    protected int countThreshold() {
        return COUNT_PER_DEFAULT;
    }

    /**
     * 導出數量<p>
     * 子類自行覆蓋
     *
     * @return
     */
    protected int count(CxBaseQuery query) {
        return 0;
    }
}
