import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * @Description 任务执行结果
 * @Date 2021/8/3
 * @author mingzhe.xiang
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskResultVo implements Serializable {
	private static final long serialVersionUID = 3998639953596568154L;
	private int status;
	/**使用CxBatchResult 无奈之举 兼容老导出**/
	private BatchResult result;

	private String msg;



	@AllArgsConstructor
	@Getter
	public enum TaskStatus {
		RUNNING(1), DONE(2), FAILED(-1), EXPIRED(-2);

		private int val;
		}


	/**
	 *
	 * @return [运行]对象
	 */
	public static TaskResultVo runningResult() {
		return new TaskResultVo(TaskStatus.RUNNING.val, new CxBatchResult(), "running");
	}

	/**
	 *
	 * @return [超时]对象
	 */
	public static TaskResultVo expiredResult() {
		return new TaskResultVo(TaskStatus.EXPIRED.val, new CxBatchResult(), "expired");
	}

	/**
	 *
	 * @return [异常]对象
	 */
	public static TaskResultVo failedResult(String errorMsg) {
		return new TaskResultVo(TaskStatus.FAILED.val, new CxBatchResult(), errorMsg);
	}

	/**
	 *
	 * @return [异常]对象
	 */
	public static TaskResultVo failedResult() {
		return new TaskResultVo(TaskStatus.FAILED.val, new CxBatchResult(), "failed");
	}


	/**
	 *
	 * @param result 数据
	 * @return [完成]对象
	 */
	public static TaskResultVo doneResult(CxBatchResult result) {
		return new TaskResultVo(TaskStatus.DONE.getVal(), result, "success");
	}

}
