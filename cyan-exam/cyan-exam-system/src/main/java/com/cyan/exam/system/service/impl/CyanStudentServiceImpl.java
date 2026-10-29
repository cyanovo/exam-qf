package com.cyan.exam.system.service.impl;

import java.util.List;
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
import com.cyan.exam.system.mapper.SysUserMapper;
import com.cyan.exam.system.mapper.SysUserRoleMapper;
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
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    private static final Long DEFAULT_DEPT_ID = 103L; // 请替换为有效的部门ID

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
            userService.insertUserAuth(user.getUserId(), new Long[]{roles.get(0).getRoleId()});
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
        for (Long id : studentIds) {
            CyanStudent student = studentMapper.selectCyanStudentByStudentId(id);
            System.out.println("删除学生ID=" + id + "，查到的userId=" + (student != null ? student.getUserId() : "null"));
            if (student != null && student.getUserId() != null) {
                // 删除角色关联
                sysUserRoleMapper.deleteUserRoleByUserId(student.getUserId());
                // 物理删除用户（注意：若依默认是逻辑删除，此处强制物理删除）
                sysUserMapper.deleteUserById(student.getUserId());
            }
        }
        return studentMapper.deleteCyanStudentByStudentIds(studentIds);
    }

    @Override
    @Transactional
    public String importStudents(CyanStudentImportDTO dto) {
        SysRole searchRole = new SysRole();
        searchRole.setRoleKey("student");
        List<SysRole> roleList = roleMapper.selectRoleList(searchRole);
        if (roleList.isEmpty()) {
            return "请先创建角色标识为 'student' 的角色";
        }
        Long roleId = roleList.get(0).getRoleId();

        StringBuilder errors = new StringBuilder();
        int created = 0;
        for (int i = 0; i < dto.getCount(); i++) {
            String loginName = dto.getPrefix() + (dto.getStartNo() + i);
            if (userService.selectUserByUserName(loginName) != null) {
                errors.append(loginName).append(" 已存在；");
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
            userService.insertUserAuth(user.getUserId(), new Long[]{roleId});

            CyanStudent student = new CyanStudent();
            student.setUserId(user.getUserId());
            student.setStudentNo(loginName);
            student.setClassName(dto.getClassName());
            student.setGrade(dto.getGrade());
            studentMapper.insertCyanStudent(student);
            created++;
        }
        if (errors.length() > 0) {
            return "成功创建 " + created + " 名学生，失败：" + errors.toString();
        }
        return null;
    }
}