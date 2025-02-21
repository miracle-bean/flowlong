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

import com.flowlong.bpm.engine.ProcessService;
import com.flowlong.bpm.engine.entity.Process;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试简单流程
 *
 * @author xdg
 */
public class TestProcess extends MysqlTest {

    @BeforeEach
    public void before() {
        processId = this.deployByResource("test/process.json", testCreator);
    }

    @Test
    public void test() {
        ProcessService processService = flowLongEngine.processService();

        // 根据流程定义ID查询
        Process process = processService.getProcessById(processId);
        if (null != process) {
            // 根据流程定义ID和版本号查询
            Assertions.assertNotNull(processService.getProcessByVersion(process.getName(), process.getVersion()));
        }

        // 启动指定流程定义ID启动流程实例
        Map<String, Object> args = new HashMap<>();
        args.put("day", 8);
        args.put("assignee", testUser1);
        flowLongEngine.startInstanceById(processId, testCreator, args).ifPresent(instance -> {

            // 发起，执行条件路由
            this.executeActiveTasks(instance.getId(), testCreator);

            // 领导审批，流程结束
            this.executeActiveTasks(instance.getId(), testCreator);
        });

        // 卸载指定的定义流程
        // Assertions.assertTrue(processService.undeploy(processId));
    }


    /**
     * 测试流程的级联删除
     */
    @Test
    public void cascadeRemove() {
        ProcessService processService = flowLongEngine.processService();

        // 测试级联删除
        processService.cascadeRemove(processId);
    }

}
