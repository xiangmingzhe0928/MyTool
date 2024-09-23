import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.io.Serializable;

/**
 * @Description
 * @Date 2021/8/3
 * @author mingzhe.xiang
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class StationResource implements Serializable {

	private static final long serialVersionUID = -4010977836263627136L;
	/**
	 * 数据输入流
	 */
	private InputStream data;
	/**
	 * 数据名 (ie. File类则指代文件名)
	 */
	private String name;
	/**
	 * 操作时国际化语言
	 */
	private String language;
}
