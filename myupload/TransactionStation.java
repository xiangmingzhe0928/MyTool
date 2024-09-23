import java.io.InputStream;

/**
 * @Description 资源中转站
 * @Date 2021/8/3
 * @author mingzhe.xiang
 */
public interface TransactionStation {

	/**
	 * 从中转站拉取资源
	 * @param key 资源Key
	 * @return 资源数据
	 */
	InputStream pullResourceFromStation(String key) throws Exception;

	/**
	 * 将数据推送到中转站
	 * @param resource 资源数据
	 * @return 资源Key
	 */
	String pushResourceToStation(StationResource resource) throws Exception;

	/**
	 * 释放中转站资源
	 * @param key 资源KEy
	 * @throws Exception
	 */
	void deleteResource(String key) throws Exception;


	/**
	 * 包装生成最终访问的URL
	 * <pre>
	 * 某些Station {@link com.dmall.cx.biz.beans.excel.station.TransactionStation#pushResourceToStation(StationResource)} 方法返回资源在Station中的相对路径{@code stationPath}
	 * 最终调用方能访问的URL需要再一次包装.
	 * 子类自行决定是否需要包装,默认不做包装直接返回{@code stationPath}
	 * </pre>
	 * @param stationPath 资源在Station上的相对路径
	 * @return 调用方使用的最终路径
	 */
	default String wrapperAccessUrl(String stationPath){
		return stationPath;
	}


}
