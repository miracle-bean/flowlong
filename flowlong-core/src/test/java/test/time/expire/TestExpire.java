package test.time.expire;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flowlong.bpm.engine.entity.Task;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import com.flowlong.bpm.engine.assist.DateUtils;
import com.flowlong.bpm.engine.entity.Instance;
import test.TestFlowLong;
import test.mysql.MysqlTest;
/**
 * @author wangzi
 *
 */
public class TestExpire extends MysqlTest {
	@Test
	public void test() {
		Long processId = this.deployByResource("test/time/expire/process.long");
		System.out.println("部署的流程定义ID = " + processId);
		System.out.println(DateUtils.parseTime(new DateTime(2023, 3, 4, 14, 21).toDate()));
		Map<String, Object> args = new HashMap<>();
		args.put("task1.operator", new String[]{"1"});
		args.put("task1.expireTime", new DateTime(2023, 3, 11, 14, 00).toDate());
		args.put("task1.reminderTime", new DateTime(2023, 3, 11, 14, 59).toDate());
		Instance instance = flowLongEngine.startInstanceByName("expire", null, "2", args);
		Long id = instance.getId();
		System.out.println("流程实例ID = " + id);
		List<Task> tasks = flowLongEngine.queryService().getTasksByInstanceId(id);
		for (Task task : tasks) {
			System.out.println("************************begin:::::" + task);
			flowLongEngine.executeTask(task.getId(), "1", args);
			System.out.println("************************end:::::" + task);
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
