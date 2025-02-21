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
package com.flowlong.bpm.engine.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowlong.bpm.engine.entity.HisTaskActor;

import java.util.List;

/**
 * 历史任务参与者 Mapper
 *
 * <p>
 * 尊重知识产权，CV 请保留版权，爱组搭 http://aizuda.com 出品，不允许非法使用，后果自负
 * </p>
 *
 * @author hubin
 * @since 1.0
 */
public interface HisTaskActorMapper extends BaseMapper<HisTaskActor> {

    /**
     * 通过任务ID获取参与者列表
     *
     * @param taskId 任务ID
     */
    default List<HisTaskActor> selectListByTaskId(Long taskId) {
        return this.selectList(Wrappers.<HisTaskActor>lambdaQuery().eq(HisTaskActor::getTaskId, taskId));
    }

    /**
     * 通过任务ID删除参与者
     *
     * @param taskIds 任务ID列表
     */
    default boolean deleteByTaskIds(List<Long> taskIds) {
        return this.delete(Wrappers.<HisTaskActor>lambdaQuery().in(HisTaskActor::getTaskId, taskIds)) > 0;
    }

}
