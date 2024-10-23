package com.example.safeinventory.mapper;

import com.example.safeinventory.model.InventoryReservationLogModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface InventoryReservationLogMapper {

    @Select("select * from inventory_reservation_log where request_id = #{requestId}")
    InventoryReservationLogModel selectByRequestId(@Param("requestId") String requestId);


    // 查找 5 mins 以前创建, 但是状态还未流转的记录
    @Select("select * from inventory_reservation_log " +
            "where status = #{status} " +
            "AND create_time <= CURRENT_TIMESTAMP - INTERVAL 5 MINUTE")
    List<InventoryReservationLogModel> selectPending(@Param("minId") long minId,
                                                     @Param("status") int status);


    @Insert("INSERT INTO inventory_reservation_log (" +
            "request_id, " +
            "product_id, " +
            "reservation_quantity, " +
            "reservation_status)" +
            "VALUE (" +
            "#{requestId}," +
            "#{productId}, " +
            "#{reservationQuantity}," +
            "#{reservationStatus})"
    )
    int insertInventoryReservationLog(InventoryReservationLogModel inventoryReservationLogModel);


    @Update("UPDATE inventory_reservation_log SET " +
            "status = #{status}, " +
            "version =  version + 1 " +
            "WHERE " +
            "request_id = #{requestId} " +
            "AND version =  #{version}")
    int updateStatus(String requestId, int status, int version);


    @Update("UPDATE inventory_reservation_log SET " +
            "status = #{status}, " +
            "version =  version + 1, " +
            "verify_try_count = verify_try_count +1," +
            "WHERE " +
            "request_id = #{requestId} " +
            "AND version =  #{version}")
    int updateStatusAndTryCount(String requestId, int status, int version);


    @Update("UPDATE inventory_reservation_log SET " +
            "version =  version + 1, " +
            "verify_try_count = verify_try_count +1," +
            "WHERE " +
            "request_id = #{requestId} " +
            "AND version =  #{version}")
    int updateTryCount(String requestId, int version);


}
