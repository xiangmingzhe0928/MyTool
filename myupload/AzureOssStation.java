


import com.microsoft.azure.storage.blob.CloudBlobContainer;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 *
 * <pre>
 * 使用Azure-OSS做文件中转站,
 * 考虑到作为导入导出文件中转站,此container下文件默认保留3天。
 * see :{@link AzureOssInspectionJob}
 * </pre>
 */
@Slf4j
public class AzureOssStation implements TransactionStation {

    public static final String BUCKET = "ao-tmp";
    private AzureUtils azureUtils;
    public AzureOssStation() {
        this.azureUtils = (AzureUtils) ApplicationContextUtils.getBean("azureUtils");
    }

    @Override
    public InputStream pullResourceFromStation(String key) throws Exception {
        log.info("Read Azure OSS blobName:{}", key);
        return azureUtils.read(getBlobContainer(), key);
    }

    @Override
    public String pushResourceToStation(StationResource resource) throws Exception {
        String originName = resource.getName();
        String contentDisposition = buildContentDisposition(resource.getName());
        return azureUtils.upload(getBlobContainer(), resource.getData(), originName, contentDisposition);
    }

    @Override
    public String wrapperAccessUrl(String relationPath) {
        return azureUtils.getDomainAccessUrl(getBlobContainer(), relationPath);
    }

    @Override
    public void deleteResource(String key) throws Exception {
        CloudBlobContainer cloudBlobContainer = getBlobContainer();
        azureUtils.delete(cloudBlobContainer, key);
        if (log.isInfoEnabled()) {
            log.info("AzureOSS delete Blob>>>>> container:{}, blob:{}", cloudBlobContainer.getName(), key);
        }
    }

    private String buildContentDisposition(String originName) {
        String temp;
        try {
            temp = URLEncoder.encode(originName, "UTF-8").replaceAll("\\+", "%20");
        } catch (UnsupportedEncodingException e) {
           log.error("encode FileName error", e);
            return originName;
        }
        return "attachment;fileName=\"" + temp + "\";" + "filename*=utf-8''" + temp;
    }


    private CloudBlobContainer getBlobContainer() {
        return azureUtils.createContainer(BUCKET);
    }
}
