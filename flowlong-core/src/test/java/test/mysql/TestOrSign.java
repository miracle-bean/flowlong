/* Copyright 2023-2025 jobob@qq.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test.mysql;

import com.flowlong.bpm.engine.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * 测试或签流程
 *
 * @author 青苗
 */
@Slf4j
public class TestOrSign extends MysqlTest {

    @BeforeEach
    public void before() {
        processId = this.deployByResource("test/orSign.json", testCreator);
    }

    @Test
    public void test() {
        // 启动指定流程定义ID启动流程实例
        flowLongEngine.startInstanceById(processId, testCreator).ifPresent(instance -> {

            // 发起
            this.executeActiveTasks(instance.getId(), testCreator);

            // test1 驳回任务（领导审批驳回，任务至发起人）
            TaskService taskService = flowLongEngine.taskService();
            this.executeActiveTasks(instance.getId(), t ->
                    taskService.rejectTask(t, testCreator, new HashMap<String, Object>() {{
                        put("reason", "不符合要求");
                    }})
            );

            // 调整申请内容，重新提交审批
            this.executeActiveTasks(instance.getId(), t ->
                    flowLongEngine.executeTask(t.getId(), testCreator)
            );

            // test3 领导审批同意
            this.executeActiveTasks(instance.getId(), test3Creator);

            // 抄送人力资源，流程自动结束

        });
    }
}
