package com.cyan.exam.system.service;

import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.system.mapper.EquipmentMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentServiceTest {
    @Test void optionalContactAndRemarkMayBeOmitted() {
        var item=new EquipmentService.Item(null,"ROBOT-2","Model","AVAILABLE",null,null,null,null,null);
        assertDoesNotThrow(()->EquipmentService.validate(item,false));
        assertEquals("",item.contact());
        assertEquals("",item.remark());
    }
    private EquipmentService.Item device(String state,Long station) {
        return new EquipmentService.Item(null,"ROBOT-1","Model",state,station,"","","",null);
    }
    @Test void validatesAssetStatesAndRetiredAssignments() {
        assertDoesNotThrow(()->EquipmentService.validate(device("AVAILABLE",1L),false));
        assertThrows(ServiceException.class,()->EquipmentService.validate(device("OTHER",null),false));
        assertThrows(ServiceException.class,()->EquipmentService.validate(device("RETIRED",1L),false));
        assertThrows(ServiceException.class,()->EquipmentService.validate(device("AVAILABLE",null),true));
    }
    @Test void preventsDeletionOfOccupiedStation() {
        var mapper=mock(EquipmentMapper.class); var service=new EquipmentService(mapper);
        when(mapper.lockStation(1L)).thenReturn(1L); when(mapper.assigned(1L)).thenReturn(2);
        assertThrows(ServiceException.class,()->service.delete(1L,1,true));
        verify(mapper,never()).deleteStation(any(),anyInt());
    }
    @Test void missingStationCannotBeAssigned() {
        var mapper=mock(EquipmentMapper.class); var service=new EquipmentService(mapper);
        when(mapper.lockStation(1L)).thenReturn(null);
        assertThrows(ServiceException.class,()->service.save(device("AVAILABLE",1L),false));
        verify(mapper,never()).addDevice(any());
    }
    @Test void staleUpdatesCannotOverwrite() {
        var service=new EquipmentService(mock(EquipmentMapper.class));
        var input=new EquipmentService.Item(1L,"A","B","AVAILABLE",null,"","","",1);
        assertThrows(ServiceException.class,()->service.save(input,false));
    }
}
