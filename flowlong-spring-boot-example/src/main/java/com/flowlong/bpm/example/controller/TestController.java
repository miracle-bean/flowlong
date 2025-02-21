package com.flowlong.bpm.example.controller;

import com.flowlong.bpm.engine.FlowLongEngine;
import com.flowlong.bpm.engine.core.FlowCreator;
import com.flowlong.bpm.engine.entity.Instance;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/process")
@AllArgsConstructor
public class TestController {
    protected FlowLongEngine flowLongEngine;

    protected static FlowCreator testCreator = FlowCreator.of("test001", "测试001");

    /**
     * 流程部署
     *
     * http://localhost:8000/process/deploy
     */
    @GetMapping("/deploy")
    public Long deployByResource() {
        return flowLongEngine.processService().deployByResource("process.json", testCreator, false);
    }

    /**
     * 启动流程实例
     *
     * http://localhost:8000/process/instance-start
     */
    @GetMapping("/instance-start")
    public Instance instanceStart() {
        Map<String, Object> args = new HashMap<>();
        args.put("task1.assignee", "1");
        return flowLongEngine.startInstanceByName("simple", null, testCreator, args).get();
    }

}
