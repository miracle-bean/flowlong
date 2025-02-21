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
package com.flowlong.bpm.engine.core.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowlong.bpm.engine.TaskAccessStrategy;
import com.flowlong.bpm.engine.TaskService;
import com.flowlong.bpm.engine.assist.Assert;
import com.flowlong.bpm.engine.assist.DateUtils;
import com.flowlong.bpm.engine.assist.ObjectUtils;
import com.flowlong.bpm.engine.core.Execution;
import com.flowlong.bpm.engine.core.FlowCreator;
import com.flowlong.bpm.engine.core.enums.PerformType;
import com.flowlong.bpm.engine.core.enums.TaskState;
import com.flowlong.bpm.engine.core.enums.TaskType;
import com.flowlong.bpm.engine.core.mapper.*;
import com.flowlong.bpm.engine.entity.Process;
import com.flowlong.bpm.engine.entity.*;
import com.flowlong.bpm.engine.exception.FlowLongException;
import com.flowlong.bpm.engine.listener.TaskListener;
import com.flowlong.bpm.engine.model.NodeAssignee;
import com.flowlong.bpm.engine.model.NodeModel;
import com.flowlong.bpm.engine.model.ProcessModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 任务执行业务类
 *
 * <p>
 * 尊重知识产权，CV 请保留版权，爱组搭 http://aizuda.com 出品，不允许非法使用，后果自负
 * </p>
 *
 * @author hubin
 * @since 1.0
 */
@Service
public class TaskServiceImpl implements TaskService {
    private TaskAccessStrategy taskAccessStrategy;
    private ProcessMapper processMapper;
    private TaskListener taskListener;
    private InstanceMapper instanceMapper;
    private TaskMapper taskMapper;
    private TaskCcMapper taskCcMapper;
    private TaskActorMapper taskActorMapper;
    private HisTaskMapper hisTaskMapper;
    private HisTaskActorMapper hisTaskActorMapper;

    public TaskServiceImpl(@Autowired(required = false) TaskAccessStrategy taskAccessStrategy, @Autowired(required = false) TaskListener taskListener,
                           ProcessMapper processMapper, InstanceMapper instanceMapper, TaskMapper taskMapper,
                           TaskCcMapper taskCcMapper, TaskActorMapper taskActorMapper, HisTaskMapper hisTaskMapper,
                           HisTaskActorMapper hisTaskActorMapper) {
        this.taskAccessStrategy = taskAccessStrategy;
        this.processMapper = processMapper;
        this.taskListener = taskListener;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.taskCcMapper = taskCcMapper;
        this.taskActorMapper = taskActorMapper;
        this.hisTaskMapper = hisTaskMapper;
        this.hisTaskActorMapper = hisTaskActorMapper;
    }

    /**
     * 完成指定任务
     * 该方法仅仅结束活动任务，并不能驱动流程继续执行
     */
    @Override
    public Task complete(Long taskId, FlowCreator flowCreator, Map<String, Object> args) {
        return this.executeTask(taskId, flowCreator, args, TaskState.finish, TaskListener.EVENT_COMPLETE);
    }

    /**
     * 执行任务
     *
     * @param taskId      任务ID
     * @param flowCreator 任务创建者
     * @param args        执行参数
     * @param taskState   任务状态
     * @param event       执行事件
     * @return
     */
    protected Task executeTask(Long taskId, FlowCreator flowCreator, Map<String, Object> args, TaskState taskState, String event) {
        Task task = taskMapper.getCheckById(taskId);
        task.setVariable(args);
        Assert.isFalse(isAllowed(task, flowCreator.getCreateId()), "当前参与者 [" + flowCreator.getCreateBy() + "]不允许执行任务[taskId=" + taskId + "]");

        // 迁移 task 信息到 flw_his_task
        HisTask hisTask = HisTask.of(task);
        hisTask.setFinishTime(DateUtils.getCurrentDate());
        hisTask.setTaskState(taskState);
        hisTask.setCreateId(flowCreator.getCreateId());
        hisTask.setCreateBy(flowCreator.getCreateBy());
        hisTaskMapper.insert(hisTask);

        // 迁移任务参与者
        List<TaskActor> actors = taskActorMapper.selectListByTaskId(taskId);
        if (ObjectUtils.isNotEmpty(actors)) {
            // 将 task 参与者信息迁移到 flw_his_task_actor
            actors.forEach(t -> hisTaskActorMapper.insert(HisTaskActor.of(t)));
            // 移除 flw_task_actor 中 task 参与者信息
            taskActorMapper.deleteByTaskId(taskId);
        }

        // 删除 flw_task 中指定 task 信息
        taskMapper.deleteById(taskId);

        // 任务监听器通知
        this.taskNotify(event, task);
        return task;
    }


    protected void taskNotify(String event, Task task) {
        if (null != taskListener) {
            taskListener.notify(event, task);
        }
    }

    /**
     * 更新任务对象的finish_Time、createBy、expire_Time、version、variable
     *
     * @param task 任务对象
     */
    @Override
    public void updateTaskById(Task task) {
        taskMapper.updateById(task);
        // 任务监听器通知
        this.taskNotify(TaskListener.EVENT_UPDATE, task);
    }

    @Override
    public boolean readTask(Long taskId, TaskActor taskActor) {
        if (taskActorMapper.selectCount(Wrappers.<TaskActor>lambdaQuery().eq(TaskActor::getTaskId, taskId)
                .eq(TaskActor::getActorId, taskActor.getActorId())) > 0) {
            /**
             * 设置任务为已阅状态
             */
            Task task = new Task();
            task.setId(taskId);
            task.setRead(1);
            return taskMapper.updateById(task) > 0;
        }
        return false;
    }

    /**
     * 任务设置超时
     *
     * @param taskId 任务ID
     */
    @Override
    public boolean taskTimeout(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (null != task) {
            // 1，保存任务状态为超时，设置完成时间
            HisTask hisTask = HisTask.of(task);
            hisTask.setFinishTime(DateUtils.getCurrentDate());
            hisTask.setTaskState(TaskState.timeout);
            hisTaskMapper.insert(hisTask);

            // 2，级联删除任务和对应的任务参与者
            taskActorMapper.deleteByTaskId(taskId);
            taskMapper.deleteById(taskId);

            // 3，任务监听器通知
            this.taskNotify(TaskListener.EVENT_TIMEOUT, task);
        }
        return true;
    }

    /**
     * 根据 任务ID 认领任务，删除其它任务参与者
     */
    @Override
    public Task claim(Long taskId, TaskActor taskActor) {
        Task task = taskMapper.getCheckById(taskId);
        if (!isAllowed(task, taskActor.getActorId())) {
            throw new FlowLongException("当前执行用户ID [" + taskActor.getActorName() + "] 不允许提取任务 [taskId=" + taskId + "]");
        }
        // 删除任务参与者
        taskActorMapper.deleteByTaskId(taskId);
        // 插入当前用户ID作为唯一参与者
        taskActorMapper.insert(taskActor);
        return task;
    }

    /**
     * 根据 任务ID 分配任务给指定办理人、重置任务类型
     *
     * @param taskId            任务ID
     * @param taskType          任务类型
     * @param taskActor         任务参与者
     * @param assigneeTaskActor 指定办理人
     * @return
     */
    @Override
    public boolean assigneeTask(Long taskId, TaskType taskType, TaskActor taskActor, TaskActor assigneeTaskActor) {
        // 转办权限验证
        List<TaskActor> taskActors = taskActorMapper.selectList(Wrappers.<TaskActor>lambdaQuery().eq(TaskActor::getTaskId, taskId)
                .eq(TaskActor::getActorId, taskActor.getActorId()));
        Assert.isTrue(ObjectUtils.isEmpty(taskActors), "无权转办该任务");

        // 设置任务为委派任务或者为转办任务
        Task task = new Task();
        task.setId(taskId);
        task.setTaskType(taskType);
        task.setAssignorId(taskActor.getActorId());
        task.setAssignor(taskActor.getActorName());
        taskMapper.updateById(task);

        // 删除任务历史参与者
        taskActorMapper.deleteBatchIds(taskActors.stream().map(t -> t.getId()).collect(Collectors.toList()));

        // 分配任务给办理人
        assignTask(taskId, taskActors.get(0).getInstanceId(), assigneeTaskActor);
        return true;
    }

    /**
     * 拿回任务、根据历史任务ID撤回下一个节点的任务、恢复历史任务
     */
    @Override
    public Optional<Task> reclaimTask(Long taskId, FlowCreator flowCreator) {
        return this.undoHisTask(taskId, flowCreator, hisTask -> {
            List<Task> taskList = taskMapper.selectListByInstanceId(hisTask.getInstanceId());
            if (ObjectUtils.isNotEmpty(taskList)) {
                List<Long> taskIds = taskList.stream().map(t -> t.getId()).collect(Collectors.toList());
                // 删除当前任务
                taskMapper.deleteBatchIds(taskIds);
                // 删除当前任务处理人
                taskActorMapper.deleteByTaskIds(taskIds);
            }
        });
    }

    /**
     * 唤醒指定的历史任务
     */
    @Override
    public Task resume(Long taskId, TaskActor taskActor) {
        HisTask histTask = hisTaskMapper.getCheckById(taskId);
        Assert.isTrue(ObjectUtils.isEmpty(histTask.getCreateBy()) || !Objects.equals(histTask.getCreateBy(), taskActor.getActorId()),
                "当前参与者[" + taskActor.getActorId() + "]不允许唤醒历史任务[taskId=" + taskId + "]");

        // 流程实例结束情况恢复流程实例
        Instance instance = instanceMapper.selectById(histTask.getInstanceId());
        Assert.isNull(instance, "已结束流程任务不支持唤醒");

        // 历史任务恢复
        Task task = histTask.cloneTask(null);
        taskMapper.insert(task);

        // 分配任务
        assignTask(task.getInstanceId(), taskId, taskActor);
        return task;
    }

    /**
     * 撤回指定的任务
     */
    @Override
    public Optional<Task> withdrawTask(Long taskId, FlowCreator flowCreator) {
        return this.undoHisTask(taskId, flowCreator, hisTask -> {
            List<Task> tasks = null;
            PerformType performType = PerformType.get(hisTask.getPerformType());
            if (performType == PerformType.countersign) {
                // 根据父任务ID查询所有子任务
                tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery().eq(Task::getParentTaskId, hisTask.getId()));
            } else {
                List<Long> hisTaskIds = hisTaskMapper.selectList(Wrappers.<HisTask>lambdaQuery().eq(HisTask::getInstanceId, hisTask.getInstanceId())
                                .eq(HisTask::getTaskName, hisTask.getTaskName()).eq(HisTask::getParentTaskId, hisTask.getParentTaskId()))
                        .stream().map(HisTask::getId).collect(Collectors.toList());
                if (ObjectUtils.isNotEmpty(hisTaskIds)) {
                    tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery().in(Task::getParentTaskId, hisTaskIds));
                }
            }
            if (ObjectUtils.isEmpty(tasks)) {
                throw new FlowLongException("后续活动任务已完成或不存在，无法撤回.");
            }
            List<Long> taskIds = tasks.stream().map(FlowEntity::getId).collect(Collectors.toList());
            // 查询任务参与者
            List<Long> taskActorIds = taskActorMapper.selectList(Wrappers.<TaskActor>lambdaQuery().in(TaskActor::getTaskId, taskIds))
                    .stream().map(TaskActor::getId).collect(Collectors.toList());
            if (ObjectUtils.isNotEmpty(taskActorIds)) {
                taskActorMapper.deleteBatchIds(taskActorIds);
            }
            taskMapper.deleteBatchIds(tasks.stream().map(FlowEntity::getId).collect(Collectors.toList()));
        });
    }

    @Override
    public Optional<Task> rejectTask(Task currentTask, FlowCreator flowCreator, Map<String, Object> args) {
        Long parentTaskId = currentTask.getParentTaskId();
        Assert.isTrue(Objects.equals(parentTaskId, 0L), "上一步任务ID为空，无法驳回至上一步处理");

        // 执行任务驳回
        this.executeTask(currentTask.getId(), flowCreator, args, TaskState.reject, TaskListener.EVENT_REJECT);

        // 撤回至上一级任务
        return this.undoHisTask(parentTaskId, flowCreator, null);
    }

    /**
     * 撤回历史任务
     *
     * @param hisTaskId       历史任务ID
     * @param flowCreator     任务创建者
     * @param hisTaskConsumer 历史任务业务处理
     * @return
     */
    protected Optional<Task> undoHisTask(Long hisTaskId, FlowCreator flowCreator, Consumer<HisTask> hisTaskConsumer) {
        HisTask hisTask = hisTaskMapper.getCheckById(hisTaskId);
        if (null != hisTaskConsumer) {
            hisTaskConsumer.accept(hisTask);
        }
        // 撤回历史任务
        Task task = hisTask.undoTask(flowCreator);
        taskMapper.insert(task);
        // 撤回任务参与者
        List<HisTaskActor> hisTaskActors = hisTaskActorMapper.selectListByTaskId(hisTaskId);
        if (null != hisTaskActors) {
            hisTaskActors.forEach(t -> {
                TaskActor taskActor = new TaskActor();
                taskActor.setTenantId(t.getTenantId());
                taskActor.setTaskId(task.getId());
                taskActor.setType(t.getType());
                taskActor.setActorId(t.getActorId());
                taskActor.setActorName(t.getActorName());
                taskActorMapper.insert(taskActor);
            });
        }
        return Optional.ofNullable(task);
    }

    /**
     * 对指定的任务分配参与者。参与者可以为用户、部门、角色
     *
     * @param instanceId 实例ID
     * @param taskId     任务ID
     * @param taskActor  任务参与者
     */
    protected void assignTask(Long instanceId, Long taskId, TaskActor taskActor) {
        taskActor.setId(null);
        taskActor.setInstanceId(instanceId);
        taskActor.setTaskId(taskId);
        taskActorMapper.insert(taskActor);
    }

    /**
     * 根据已有任务、任务类型、参与者创建新的任务
     * 适用于转派，动态协办处理
     */
    @Override
    public List<Task> createNewTask(Long taskId, TaskType taskType, List<TaskActor> taskActors) {
        Assert.isTrue(ObjectUtils.isEmpty(taskActors), "参与者不能为空");
        Task task = taskMapper.getCheckById(taskId);
        Task newTask = task.cloneTask(null);
        newTask.setTaskType(taskType);
        newTask.setParentTaskId(taskId);
        return this.saveTask(newTask, PerformType.sort, taskActors, null);
    }

    /**
     * 获取超时或者提醒的任务
     *
     * @return List<Task> 任务列表
     */
    @Override
    public List<Task> getTimeoutOrRemindTasks() {
        Date currentDate = DateUtils.getCurrentDate();
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery().le(Task::getExpireTime, currentDate).or().le(Task::getRemindTime, currentDate));
    }

    /**
     * 获取任务模型
     *
     * @param taskId 任务ID
     * @return TaskModel
     */
    @Override
    public NodeModel getTaskModel(Long taskId) {
        Task task = taskMapper.getCheckById(taskId);
        Instance instance = instanceMapper.selectById(task.getInstanceId());
        Assert.notNull(instance);
        Process process = processMapper.selectById(instance.getProcessId());
        ProcessModel model = process.getProcessModel();
        NodeModel nodeModel = model.getNode(task.getTaskName());
        Assert.notNull(nodeModel, "任务ID无法找到节点模型.");
        return nodeModel;
    }

    /**
     * 创建 task 根据 model 决定是否分配参与者
     *
     * @param nodeModel 节点模型
     * @param execution 执行对象
     * @return 任务列表
     */
    @Override
    public List<Task> createTask(NodeModel nodeModel, Execution execution) {
        // 执行任务
        Task task = this.createTaskBase(nodeModel, execution);

        // 模型中获取参与者信息
        List<TaskActor> taskActors = this.getTaskActors(nodeModel, execution);
        List<Task> tasks = new LinkedList<>();

        // 处理流程任务
        Integer nodeType = nodeModel.getType();
        if (0 == nodeType || 1 == nodeType) {
            /**
             * 0，发起人 1，审批人
             */
            PerformType performType = PerformType.get(nodeModel.getExamineMode());
            tasks.addAll(this.saveTask(task, performType, taskActors, execution));
        } else if (2 == nodeType) {
            /**
             * 2，抄送任务
             */
            this.saveTaskCc(nodeModel, execution);
            NodeModel nextNode = nodeModel.getChildNode();
            if (null != nextNode) {
                // 继续执行普通任务
                this.createTask(nextNode, execution);
            }
        } else if (3 == nodeType) {
            /**
             * 3，条件审批
             */
            Task singleTask = task.cloneTask(null);
            PerformType performType = PerformType.get(nodeModel.getExamineMode());
            tasks.addAll(this.saveTask(singleTask, performType, taskActors, execution));
        }
        return tasks;
    }

    /**
     * 保存抄送任务
     *
     * @param nodeModel 节点模型
     * @param execution 执行对象
     * @return Task任务对象
     */
    public void saveTaskCc(NodeModel nodeModel, Execution execution) {
        if (ObjectUtils.isNotEmpty(nodeModel.getNodeUserList())) {
            Long parentTaskId = execution.getTask().getId();
            List<NodeAssignee> nodeUserList = nodeModel.getNodeUserList();
            for (NodeAssignee nodeUser : nodeUserList) {
                TaskCc taskCc = new TaskCc();
                taskCc.setParentTaskId(execution.getTask().getId());
                taskCc.setCreateId(execution.getCreateId());
                taskCc.setCreateBy(execution.getCreateBy());
                taskCc.setCreateTime(DateUtils.getCurrentDate());
                taskCc.setInstanceId(execution.getInstance().getId());
                taskCc.setParentTaskId(parentTaskId);
                taskCc.setTaskName(nodeModel.getNodeName());
                taskCc.setDisplayName(nodeModel.getNodeName());
                taskCc.setActorId(nodeUser.getId());
                taskCc.setActorName(nodeUser.getName());
                taskCc.setType(0);
                taskCc.setState(1);
                taskCcMapper.insert(taskCc);
            }
        }
    }

    /**
     * 根据模型、执行对象、任务类型构建基本的task对象
     *
     * @param nodeModel 节点模型
     * @param execution 执行对象
     * @return Task任务对象
     */
    private Task createTaskBase(NodeModel nodeModel, Execution execution) {
        Task task = new Task();
        task.setCreateId(execution.getCreateId());
        task.setCreateBy(execution.getCreateBy());
        task.setCreateTime(DateUtils.getCurrentDate());
        task.setInstanceId(execution.getInstance().getId());
        task.setTaskName(nodeModel.getNodeName());
        task.setDisplayName(nodeModel.getNodeName());
        task.setTaskType(nodeModel.getType());
        task.setParentTaskId(execution.getTask() == null ? 0L : execution.getTask().getId());
        return task;
    }

    /**
     * 保存任务及参与者信息
     *
     * @param task       任务对象
     * @param taskActors 参与者ID集合
     * @return
     */
    protected List<Task> saveTask(Task task, PerformType performType, List<TaskActor> taskActors, Execution execution) {
        List<Task> tasks = new ArrayList<>();
        if (performType == PerformType.unknown) {
            // 发起、其它
            taskMapper.insert(task);
            if (ObjectUtils.isNotEmpty(taskActors)) {
                // 发起人保存参与者
                taskActors.forEach(t -> this.assignTask(task.getInstanceId(), task.getId(), t));
            }
            tasks.add(task);
            return tasks;
        }

        Assert.isTrue(ObjectUtils.isEmpty(taskActors), "任务参与者不能为空");
        task.setPerformType(performType);
        if (performType == PerformType.orSign) {
            /**
             * 或签一条任务多个参与者
             */
            taskMapper.insert(task);
            taskActors.forEach(t -> this.assignTask(task.getInstanceId(), task.getId(), t));
            tasks.add(task);

            // 创建任务监听
            this.taskNotify(TaskListener.EVENT_CREATE, task);
            return tasks;
        }

        if (performType == PerformType.sort) {
            /**
             * 按顺序依次审批，一个任务按顺序多个参与者依次添加
             */
            taskMapper.insert(task);
            tasks.add(task);

            // 分配一个参与者
            TaskActor nextTaskActor = null;
            if (null != execution) {
                nextTaskActor = execution.getNextTaskActor();
            }
            this.assignTask(task.getInstanceId(), task.getId(), null == nextTaskActor ? taskActors.get(0) : nextTaskActor);

            // 创建任务监听
            this.taskNotify(TaskListener.EVENT_CREATE, task);
            return tasks;
        }

        /**
         * 会签每个参与者生成一条任务
         */
        taskActors.forEach(t -> {
            Task newTask = task.cloneTask(null);
            taskMapper.insert(newTask);
            tasks.add(newTask);

            // 分配参与者
            this.assignTask(newTask.getInstanceId(), newTask.getId(), t);

            // 创建任务监听
            this.taskNotify(TaskListener.EVENT_CREATE, newTask);
        });
        return tasks;
    }

    /**
     * 根据Task模型的assignee、assignmentHandler属性以及运行时数据，确定参与者
     *
     * @param nodeModel 节点模型
     * @param execution 执行对象
     * @return 参与者数组
     */
    private List<TaskActor> getTaskActors(NodeModel nodeModel, Execution execution) {
        List<TaskActor> taskActors = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(nodeModel.getNodeUserList())) {
            // 指定用户审批
            nodeModel.getNodeUserList().forEach(t -> taskActors.add(TaskActor.ofUser(t.getId(), t.getName())));
        } else if (ObjectUtils.isNotEmpty(nodeModel.getNodeRoleList())) {
            // 指定角色审批
            nodeModel.getNodeRoleList().forEach(t -> taskActors.add(TaskActor.ofRole(t.getId(), t.getName())));
        }
        return ObjectUtils.isEmpty(taskActors) ? null : taskActors;
    }

    /**
     * 根据 taskId、createBy 判断创建人createBy是否允许执行任务
     *
     * @param task   任务对象
     * @param userId 用户ID
     * @return
     */
    @Override
    public boolean isAllowed(Task task, String userId) {
        // 未指定创建人情况，默认为不验证执行权限
        if (null == task.getCreateBy()) {
            return true;
        }

        // 任务执行创建人不存在
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }

        // 任务参与者列表
        List<TaskActor> actors = taskActorMapper.selectListByTaskId(task.getId());
        if (ObjectUtils.isEmpty(actors)) {
            // 未设置参与者，默认返回 true
            return true;
        }
        return taskAccessStrategy.isAllowed(userId, actors);
    }

    /**
     * 向指定的任务ID添加参与者
     *
     * @param taskId     任务ID
     * @param taskActors 参与者列表
     */
    @Override
    public boolean addTaskActor(Long taskId, PerformType performType, List<TaskActor> taskActors) {
        Task task = taskMapper.getCheckById(taskId);
        List<TaskActor> taskActorList = this.getTaskActorsByTaskId(taskId);
        Map<String, TaskActor> taskActorMap = taskActorList.stream().collect(Collectors.toMap(TaskActor::getActorId, t -> t));
        for (TaskActor taskActor : taskActors) {
            // 不存在的参与者
            if (null == taskActorMap.get(taskActor.getActorId())) {
                this.assignTask(task.getInstanceId(), taskId, taskActor);
            }
        }
        // 更新任务参与类型
        Task temp = new Task();
        temp.setId(taskId);
        temp.setPerformType(performType);
        return taskMapper.updateById(temp) > 0;
    }

    protected List<TaskActor> getTaskActorsByTaskId(Long taskId) {
        List<TaskActor> taskActorList = taskActorMapper.selectListByTaskId(taskId);
        Assert.isTrue(ObjectUtils.isEmpty(taskActorList), "not found task actor");
        return taskActorList;
    }

    @Override
    public boolean removeTaskActor(Long taskId, List<String> actorIds) {
        List<TaskActor> taskActorList = this.getTaskActorsByTaskId(taskId);
        Assert.isTrue(Objects.equals(actorIds.size(), taskActorList.size()), "cannot all be deleted");

        // 删除参与者表，任务关联关系
        taskActorMapper.delete(Wrappers.<TaskActor>lambdaQuery().eq(TaskActor::getTaskId, taskId).in(TaskActor::getActorId, actorIds));
        return true;
    }

    /**
     * 级联删除 flw_his_task, flw_his_task_actor, flw_task, flw_task_cc, flw_task_actor
     *
     * @param instanceId 流程实例ID
     */
    @Override
    public void cascadeRemoveByInstanceId(Long instanceId) {
        // 删除历史任务及参与者
        List<HisTask> hisTaskList = hisTaskMapper.selectList(Wrappers.<HisTask>lambdaQuery().select(HisTask::getId).eq(HisTask::getInstanceId, instanceId));
        if (ObjectUtils.isNotEmpty(hisTaskList)) {
            List<Long> hisTaskIds = hisTaskList.stream().map(t -> t.getId()).collect(Collectors.toList());
            hisTaskActorMapper.deleteByTaskIds(hisTaskIds);
            hisTaskMapper.delete(Wrappers.<HisTask>lambdaQuery().eq(HisTask::getInstanceId, instanceId));
        }

        // 删除任务及参与者
        List<Task> taskList = taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getId).eq(Task::getInstanceId, instanceId));
        if (ObjectUtils.isNotEmpty(taskList)) {
            List<Long> taskIds = taskList.stream().map(t -> t.getId()).collect(Collectors.toList());
            taskActorMapper.delete(Wrappers.<TaskActor>lambdaQuery().in(TaskActor::getTaskId, taskIds));
            taskMapper.delete(Wrappers.<Task>lambdaQuery().eq(Task::getInstanceId, instanceId));
        }

        // 删除任务抄送
        // TODO
    }

}
