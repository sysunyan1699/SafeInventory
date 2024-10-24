package com.example.safeinventory.schedule;

import com.example.safeinventory.constants.ReservationStatus;
import com.example.safeinventory.model.InventoryReservationLogModel;
import com.example.safeinventory.mapper.InventoryReservationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@Component
public class VerifyPendingReservedInventorySchedule {

    private static final Logger logger = LoggerFactory.getLogger(VerifyPendingReservedInventorySchedule.class);

    @Value("${verify.maxTryCount:3}")
    private int verifyMaxTryCount;

    @Value("${verify.selectLimitCount:100}")
    private int verifySelectLimitCount;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(
            16,
            32,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.AbortPolicy()
    );

    @Resource
    InventoryReservationLogMapper inventoryReservationLogMapper;

    @Scheduled(fixedRate = 60000)
    public void verifyScheduledTask() {
        logger.info("verifyScheduledTask executed at: {}", new java.util.Date());
        long minID = 0;
        while (true) {
            List<InventoryReservationLogModel> reservationLogModelList = inventoryReservationLogMapper.selectPending(minID,
                    ReservationStatus.PENDING.getValue());
            if (reservationLogModelList.size() == 0) {
                logger.info("verifyScheduledTask finish  at: {}", new java.util.Date());
                return;
            }
            for (InventoryReservationLogModel m : reservationLogModelList) {
                minID = Math.max(minID, m.getId());
                doVerify(m);
                // executor.execute(new VerifyInventoryReservationLog(m));
            }
            if (reservationLogModelList.size() < verifySelectLimitCount) {
                logger.info("verifyScheduledTask finish  at: {}", new java.util.Date());
                return;
            }
        }

    }


    public void doVerify(InventoryReservationLogModel m) {
        // 防止之前状态修改有遗漏
        if (m.getVerifyTryCount() >= verifyMaxTryCount) {
            inventoryReservationLogMapper.updateStatusAndTryCount(
                    m.getRequestId(),
                    ReservationStatus.UNKNOWN.getValue(),
                    m.getVersion());
            logger.error("流水状态查询 重试已达最大次数，request:{}", m.getRequestId());
            return;
        }
        try {
            // todo 根据requestId 到业务表中查询对应结果,  判断流水状态并更新
            int status = 2;
            switch (ReservationStatus.valueOf(status)) {
                case CONFIRMED:
                    inventoryReservationLogMapper.updateStatusAndTryCount(
                            m.getRequestId(),
                            ReservationStatus.CONFIRMED.getValue(),
                            m.getVersion());

                    break;
                case ROLLBACK:
                    // 直接修改状态, 等待消息发送定时任务将消息发送至下游
                    inventoryReservationLogMapper.updateStatusAndTryCount(
                            m.getRequestId(),
                            ReservationStatus.ROLLBACK.getValue(),
                            m.getVersion());
                    break;
                default:
                    // 此次重试后达到重试最大次数后，不再重试，直接修改状态为rollback, 并且标识次rollback 状态是最终重试失败后造成的
                    if (m.getVerifyTryCount() == verifyMaxTryCount) {
                        //配置告警机制，进行告警
                        logger.error("doVerify maxTime, requestId:{}", m.getRequestId());
                        inventoryReservationLogMapper.updateStatusAndTryCount(
                                m.getRequestId(),
                                ReservationStatus.UNKNOWN.getValue(),
                                m.getVersion());
                    } else {
                        inventoryReservationLogMapper.updateTryCount(
                                m.getRequestId(),
                                m.getVersion());
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("verifyScheduledTask doVerify error, requestId:{}, error:{}", m.getRequestId(), e);
        }
    }

    class VerifyInventoryReservationLog implements Runnable {
        private InventoryReservationLogModel m;

        public VerifyInventoryReservationLog(InventoryReservationLogModel m) {
            this.m = m;
        }

        @Override
        public void run() {
            doVerify(m);
        }
    }

}
