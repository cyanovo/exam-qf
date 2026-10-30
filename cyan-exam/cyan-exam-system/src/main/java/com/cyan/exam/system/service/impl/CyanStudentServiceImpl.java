package com.cyan.exam.system.service.impl;

import java.util.List;
import com.cyan.exam.common.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.common.core.domain.entity.SysRole;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.common.utils.StringUtils;
import com.cyan.exam.system.domain.CyanStudent;
import com.cyan.exam.system.domain.dto.CyanStudentImportDTO;
import com.cyan.exam.system.mapper.CyanStudentMapper;
import com.cyan.exam.system.mapper.SysRoleMapper;
import com.cyan.exam.system.mapper.StudentLifecycleMapper;
import com.cyan.exam.system.mapper.SysUserRoleMapper;
import com.cyan.exam.system.domain.SysUserRole;
import com.cyan.exam.system.service.ICyanStudentService;
import com.cyan.exam.system.service.ISysUserService;

@Service
public class CyanStudentServiceImpl implements ICyanStudentService {

    @Autowired
    private CyanStudentMapper studentMapper;
    @Autowired
    private ISysUserService userService;
    @Autowired
    private SysRoleMapper roleMapper;
    @Autowired
    private StudentLifecycleMapper studentLifecycleMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;

    private static final Long DEFAULT_DEPT_ID = 102L; // 考生分组，与考试系统初始化分组一致

    @Override
    public List<CyanStudent> selectCyanStudentList(CyanStudent student) {
        return studentMapper.selectCyanStudentList(student);
    }

    @Override
    public CyanStudent selectCyanStudentByStudentId(Long studentId) {
        return studentMapper.selectCyanStudentByStudentId(studentId);
    }

    @Override
    @Transactional
    public int insertCyanStudent(CyanStudent student) {
        if (isStudentNumberOccupied(student.getStudentNo())) {
            throw new ServiceException("学号已被使用（含历史学生记录），请使用其他学号");
        }
        // 创建系统用户
        SysUser user = new SysUser();
        user.setUserName(student.getStudentNo());
        // 优先使用前端传入的姓名，若为空则使用学号作为昵称
        user.setNickName(
                StringUtils.isNotEmpty(student.getNickName()) ? student.getNickName() : student.getStudentNo()
        );
        user.setDeptId(DEFAULT_DEPT_ID);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setCreateBy(SecurityUtils.getUsername());
        user.setPassword(SecurityUtils.encryptPassword("temp"));
        userService.insertUser(user);
        userService.resetUserPwd(user.getUserId(), SecurityUtils.encryptPassword("123456"));

        // 分配学生角色
        SysRole searchRole = new SysRole();
        searchRole.setRoleKey("student");
        List<SysRole> roles = roleMapper.selectRoleList(searchRole);
        if (!roles.isEmpty()) {
            assignNewStudentRole(user.getUserId(), roles.get(0).getRoleId());
        }
        student.setUserId(user.getUserId());
        return studentMapper.insertCyanStudent(student);
    }

    @Override
    @Transactional
    public int updateCyanStudent(CyanStudent student) {
        // 1. 更新学生扩展信息（班级、年级等）
        int rows = studentMapper.updateCyanStudent(student);

        // 2. 如果传入了姓名，同步更新 sys_user 的 nick_name
        if (student.getUserId() != null && StringUtils.isNotEmpty(student.getNickName())) {
            SysUser user = new SysUser();
            user.setUserId(student.getUserId());
            user.setNickName(student.getNickName());
            user.setUpdateBy(SecurityUtils.getUsername());
            userService.updateUserProfile(user);  // 若依提供的安全更新方法
        }
        return rows;
    }

    @Override
    @Transactional
    public int deleteCyanStudentByStudentIds(Long[] studentIds) {
        java.util.Set<Long> userIds = new java.util.LinkedHashSet<>();
        for (Long id : studentIds) {
            CyanStudent student = studentMapper.selectCyanStudentByStudentId(id);
            if (student != null && student.getUserId() != null) {
                userIds.add(student.getUserId());
            }
        }
        return userIds.isEmpty() ? 0 : userService.deleteUserByIds(userIds.toArray(new Long[0]));
    }

    @Override
    @Transactional
    public String importStudents(CyanStudentImportDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getPrefix())
                || dto.getStartNo() == null || dto.getStartNo() < 1
                || dto.getCount() == null || dto.getCount() < 1 || dto.getCount() > 200
                || StringUtils.isBlank(dto.getPassword())) {
            throw new ServiceException("请填写账号前缀、正整数起始编号、1至200的数量和默认密码");
        }
        String prefix = dto.getPrefix().trim();
        long lastNo = (long) dto.getStartNo() + dto.getCount() - 1;
        if ((prefix + lastNo).length() > 20) {
            throw new ServiceException("生成的登录账号不能超过20个字符");
        }
        SysRole searchRole = new SysRole();
        searchRole.setRoleKey("student");
        List<SysRole> roleList = roleMapper.selectRoleList(searchRole);
        if (roleList.isEmpty()) {
            throw new ServiceException("请先创建角色标识为 'student' 的角色");
        }
        Long roleId = roleList.get(0).getRoleId();

        StringBuilder errors = new StringBuilder();
        int created = 0;
        for (int i = 0; i < dto.getCount(); i++) {
            String loginName = prefix + ((long) dto.getStartNo() + i);
            if (isStudentNumberOccupied(loginName)
                    || userService.selectUserByUserName(loginName) != null) {
                errors.append(loginName).append("；");
                continue;
            }
            SysUser user = new SysUser();
            user.setUserName(loginName);
            user.setNickName("学生" + (dto.getStartNo() + i));
            user.setDeptId(DEFAULT_DEPT_ID);
            user.setStatus("0");
            user.setDelFlag("0");
            user.setCreateBy(SecurityUtils.getUsername());
            user.setPassword(SecurityUtils.encryptPassword("temp"));
            userService.insertUser(user);
            userService.resetUserPwd(user.getUserId(), SecurityUtils.encryptPassword(dto.getPassword()));
            assignNewStudentRole(user.getUserId(), roleId);

            CyanStudent student = new CyanStudent();
            student.setUserId(user.getUserId());
            student.setStudentNo(loginName);
            student.setClassName(dto.getClassName());
            student.setGrade(dto.getGrade());
            try {
                studentMapper.insertCyanStudent(student);
            } catch (DuplicateKeyException ex) {
                // Rethrowing rolls back the whole batch, including newly created accounts.
                throw new ServiceException("创建期间学号被其他操作占用，本批次已回滚，请重试");
            }
            created++;
        }
        if (errors.length() > 0) {
            return "成功创建 " + created + " 名学生，跳过 " + (dto.getCount() - created)
                    + " 个已占用编号（含历史记录）：" + errors;
        }
        return "成功创建 " + created + " 名学生";
    }

    private void assignNewStudentRole(Long userId, Long roleId) {
        // A newly inserted account has no roles to delete; avoid DELETE gap locks.
        SysUserRole assignment = new SysUserRole();
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        userRoleMapper.batchUserRole(List.of(assignment));
    }

    private boolean isStudentNumberOccupied(String studentNo) {
        // Avoid DELETE gap locks for fresh numbers during parallel batch creation.
        if (studentMapper.countByStudentNo(studentNo) == 0) {
            return false;
        }
        studentLifecycleMapper.releaseDeletedStudentByNumber(studentNo);
        return studentMapper.countByStudentNo(studentNo) > 0;
    }
}
