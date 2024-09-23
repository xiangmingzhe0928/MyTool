import lombok.*;

/**
 * @Date 2021/10/19
 * @author mingzhe.xiang
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskParam {
    private String       mart;
    private Query query;
    private String       language;
    private UserBaseInfo userBaseInfo;

    // 补货需求检核使用
    private Integer entrance;
}
