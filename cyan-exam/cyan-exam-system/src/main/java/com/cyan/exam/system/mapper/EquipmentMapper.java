package com.cyan.exam.system.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;
import com.cyan.exam.system.service.EquipmentService.Item;

public interface EquipmentMapper {
    @Select("SELECT e.id,e.code,e.model,e.asset_state AS assetState,e.station_id AS stationId,e.contact,e.remark,e.revision,e.updated_at AS updatedAt,w.code AS stationCode FROM exam_equipment e LEFT JOIN exam_workstation w ON w.id=e.station_id ORDER BY e.id DESC")
    List<Map<String,Object>> devices();
    @Select("SELECT id,code,location,contact,remark,revision,updated_at AS updatedAt FROM exam_workstation ORDER BY code")
    List<Map<String,Object>> stations();
    @Select("SELECT id FROM exam_workstation WHERE id=#{id} FOR UPDATE")
    Long lockStation(Long id);
    @Insert("INSERT INTO exam_equipment(code,model,asset_state,station_id,contact,remark) VALUES(#{code},#{model},#{assetState},#{stationId},#{contact},#{remark})")
    int addDevice(Item item);
    @Insert("INSERT INTO exam_workstation(code,location,contact,remark) VALUES(#{code},#{location},#{contact},#{remark})")
    int addStation(Item item);
    @Update("UPDATE exam_equipment SET code=#{code},model=#{model},asset_state=#{assetState},station_id=#{stationId},contact=#{contact},remark=#{remark},revision=revision+1,updated_at=NOW() WHERE id=#{id} AND revision=#{revision}")
    int updateDevice(Item item);
    @Update("UPDATE exam_workstation SET code=#{code},location=#{location},contact=#{contact},remark=#{remark},revision=revision+1,updated_at=NOW() WHERE id=#{id} AND revision=#{revision}")
    int updateStation(Item item);
    @Delete("DELETE FROM exam_equipment WHERE id=#{id} AND revision=#{revision}")
    int deleteDevice(@Param("id") Long id,@Param("revision") int revision);
    @Delete("DELETE FROM exam_workstation WHERE id=#{id} AND revision=#{revision}")
    int deleteStation(@Param("id") Long id,@Param("revision") int revision);
    @Select("SELECT COUNT(*) FROM exam_equipment WHERE station_id=#{id}")
    int assigned(Long id);
}
