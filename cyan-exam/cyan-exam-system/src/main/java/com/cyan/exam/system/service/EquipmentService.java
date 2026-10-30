package com.cyan.exam.system.service;

import java.util.*;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.mapper.EquipmentMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {
    private final EquipmentMapper mapper;
    public EquipmentService(EquipmentMapper mapper) { this.mapper=mapper; }
    public record Item(Long id, String code, String model, String assetState, Long stationId,
                       String location, String contact, String remark, Integer revision) {
        public Item {
            contact=Objects.toString(contact,"");
            remark=Objects.toString(remark,"");
        }
    }
    public Map<String,Object> list() { return Map.of("devices",mapper.devices(),"stations",mapper.stations()); }
    public static void validate(Item i, boolean station) {
        if (i==null) throw new ServiceException("缺少台账内容");
        text(i.code(),64,true,"编号");
        text(i.contact(),100,false,"联系方式");
        text(i.remark(),500,false,"备注");
        if(station) text(i.location(),200,true,"工位位置");
        else {
            text(i.model(),100,true,"设备型号");
            if(i.assetState()==null || !Set.of("AVAILABLE","MAINTENANCE","RETIRED").contains(i.assetState()))
                throw new ServiceException("资产状态无效");
            if("RETIRED".equals(i.assetState()) && i.stationId()!=null)
                throw new ServiceException("报废设备请先解除工位分配");
        }
        if(i.id()!=null && (i.id()<=0 || i.revision()==null || i.revision()<1))
            throw new ServiceException("缺少记录版本，请刷新");
    }
    private static void text(String s,int max,boolean required,String name) {
        if(required && (s==null || s.isBlank()) || s!=null && s.length()>max)
            throw new ServiceException(name+"不能为空或超过长度限制");
    }
    @Transactional
    public void save(Item item,boolean station) {
        validate(item,station);
        if(!station && item.stationId()!=null && mapper.lockStation(item.stationId())==null)
            throw new ServiceException("工位不存在，请刷新");
        try {
            int count=station ? (item.id()==null?mapper.addStation(item):mapper.updateStation(item))
                    : (item.id()==null?mapper.addDevice(item):mapper.updateDevice(item));
            if(count!=1) throw new ServiceException("记录已变化，请刷新后重试");
        } catch(DataIntegrityViolationException e) { throw new ServiceException("编号已存在或工位已变化，请检查后重试"); }
    }
    @Transactional
    public void delete(Long id,int revision,boolean station) {
        if(id==null || id<=0 || revision<1) throw new ServiceException("记录参数无效");
        if(station && mapper.lockStation(id)!=null && mapper.assigned(id)>0)
            throw new ServiceException("工位仍有设备，请先在设备台账解除分配");
        try {
            int count=station?mapper.deleteStation(id,revision):mapper.deleteDevice(id,revision);
            if(count!=1) throw new ServiceException("记录已变化，请刷新后重试");
        } catch(DataIntegrityViolationException e) { throw new ServiceException("工位仍有设备，不能删除"); }
    }
}
