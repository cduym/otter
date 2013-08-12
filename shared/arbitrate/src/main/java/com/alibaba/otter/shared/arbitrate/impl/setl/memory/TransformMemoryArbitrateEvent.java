package com.alibaba.otter.shared.arbitrate.impl.setl.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.alibaba.otter.shared.arbitrate.impl.setl.ArbitrateFactory;
import com.alibaba.otter.shared.arbitrate.impl.setl.TransformArbitrateEvent;
import com.alibaba.otter.shared.arbitrate.impl.setl.monitor.PermitMonitor;
import com.alibaba.otter.shared.arbitrate.model.EtlEventData;
import com.alibaba.otter.shared.common.model.config.channel.ChannelStatus;
import com.alibaba.otter.shared.common.model.config.enums.StageType;

/**
 * 基于memory的仲裁器实现
 * 
 * @author jianghang 2012-9-27 下午10:05:08
 * @version 4.1.0
 */
public class TransformMemoryArbitrateEvent implements TransformArbitrateEvent {

    private static final Logger logger = LoggerFactory.getLogger(TransformMemoryArbitrateEvent.class);

    public EtlEventData await(Long pipelineId) throws InterruptedException {
        Assert.notNull(pipelineId);

        PermitMonitor permitMonitor = ArbitrateFactory.getInstance(pipelineId, PermitMonitor.class);
        permitMonitor.waitForPermit();// 阻塞等待授权

        MemoryStageController stageController = ArbitrateFactory.getInstance(pipelineId, MemoryStageController.class);
        Long processId = stageController.waitForProcess(StageType.TRANSFORM); // 符合条件的processId

        ChannelStatus status = permitMonitor.getChannelPermit();
        if (status.isStart()) {// 即时查询一下当前的状态，状态随时可能会变
            return stageController.getLastData(processId);
        } else {
            logger.warn("pipelineId[{}] transform ignore processId[{}] by status[{}]", new Object[] { pipelineId,
                    processId, status });
            return await(pipelineId);// 递归调用
        }
    }

    public void single(EtlEventData data) {
        Assert.notNull(data);
        MemoryStageController stageController = ArbitrateFactory.getInstance(data.getPipelineId(),
                                                                             MemoryStageController.class);
        stageController.single(StageType.TRANSFORM, data);// 通知下一个节点
    }

}