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
package com.flowlong.bpm.engine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.flowlong.bpm.engine.core.enums.InstanceState;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * 历史流程实例实体类
 *
 * <p>
 * 尊重知识产权，CV 请保留版权，爱组搭 http://aizuda.com 出品，不允许非法使用，后果自负
 * </p>
 *
 * @author hubin
 * @since 1.0
 */
@Getter
@Setter
@ToString
@TableName("flw_his_instance")
public class HisInstance extends Instance {
    /**
     * 状态 0，结束 1，活动
     */
    protected Integer instanceState;
    /**
     * 结束时间
     */
    protected Date endTime;

    public static HisInstance of(Instance instance, InstanceState instanceState) {
        HisInstance hisInstance = new HisInstance();
        hisInstance.id = instance.getId();
        hisInstance.instanceState = instanceState.getValue();
        hisInstance.processId = instance.getProcessId();
        hisInstance.createTime = instance.getCreateTime();
        hisInstance.expireTime = instance.getExpireTime();
        hisInstance.createId = instance.getCreateId();
        hisInstance.createBy = instance.getCreateBy();
        hisInstance.priority = instance.getPriority();
        hisInstance.instanceNo = instance.getInstanceNo();
        hisInstance.variable = instance.getVariable();
        return hisInstance;
    }
}
