/* Copyright 2023-2025 www.flowlong.com
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

import com.flowlong.bpm.engine.core.FlowState;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 抄送实例实体类
 *
 * @author hubin
 * @since 1.0
 */
@Getter
@Setter
@ToString
public class CCInstance implements Serializable {
    private String instanceId;
    private String actorId;
    private String creator;
    private String createTime;
    private String finishTime;
    private Integer status;

    public void setStatus(FlowState flowState) {
        this.status = flowState.getValue();
    }
}
