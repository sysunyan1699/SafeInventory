package com.example.safeinventory.mapper;

import com.example.safeinventory.model.InventoryReservationLogModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InventoryReservationLogMapper {

    InventoryReservationLogModel selectByRequestId(@Param("requestId") String requestId);


    // 查找 5 mins 以前创建, 但是状态还未流转的记录
    List<InventoryReservationLogModel> selectPending(@Param("minId") long minId,
                                                     @Param("status") int status);


    int insertInventoryReservationLog(InventoryReservationLogModel inventoryReservationLogModel);

    int updateStatus(String requestId, int status, int version);


    int updateStatusAndTryCount(String requestId, int status, int version);


    int updateTryCount(String requestId, int version);


}
