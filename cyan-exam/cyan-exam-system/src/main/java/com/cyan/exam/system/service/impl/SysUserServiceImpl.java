package com.cyan.exam.system.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cyan.exam.common.annotation.DataScope;
import com.cyan.exam.common.constant.UserConstants;
import com.cyan.exam.common.core.domain.entity.SysRole;
import com.cyan.exam.common.core.domain.entity.SysUser;
import com.cyan.exam.common.exception.ServiceException;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.common.utils.StringUtils;
import com.cyan.exam.common.utils.bean.BeanValidators;
import com.cyan.exam.common.utils.spring.SpringUtils;
import com.cyan.exam.system.domain.SysUserRole;
import com.cyan.exam.system.mapper.SysPostMapper;
import com.cyan.exam.system.mapper.SysRoleMapper;
import com.cyan.exam.system.mapper.SysUserMapper;
import com.cyan.exam.system.mapper.SysUserPostMapper;
import com.cyan.exam.system.mapper.SysUserRoleMapper;
import com.cyan.exam.system.service.ISysConfigService;
import com.cyan.exam.system.service.ISysDeptService;
import com.cyan.exam.system.service.ISysUserService;

@Service
public class SysUserServiceImpl implements ISysUserService
{
    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    protected Validator validator;

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUserList(SysUser user)
    {
        return userMapper.selectUserList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectAllocatedList(SysUser user)
    {
        return userMapper.selectAllocatedList(user);
    }

    @Override
    @DataScope(deptAlias = "d", userAlias = "u")
    public List<SysUser> selectUnallocatedList(SysUser user)
    {
        return userMapper.selectUnallocatedList(user);
    }

    @Override
    public SysUser selectUserByUserName(String userName)
    {
        return userMapper.selectUserByUserName(userName);
    }

    @Override
    public SysUser selectUserById(Long userId)
    {
        return userMapper.selectUserById(userId);
    }

    @Override
    public String selectUserRoleGroup(String userName)
    {
        List<SysRole> list = roleMapper.selectRolesByUserName(userName);
        StringBuilder idsStr = new StringBuilder();
        for (SysRole role : list)
        {
            idsStr.append(role.getRoleName()).append(",");
        }
        if (idsStr.length() > 0)
        {
            idsStr.setLength(idsStr.length() - 1);
        }
        return idsStr.toString();
    }

    @Override
    public String selectUserPostGroup(String userName)
    {
        return "";
    }

    @Override
    public boolean checkUserNameUnique(SysUser user)
    {
        return StringUtils.isNull(userMapper.checkUserNameUnique(user.getUserName()));
    }

    @Override
    public boolean checkPhoneUnique(SysUser user)
    {
        return StringUtils.isNull(userMapper.checkPhoneUnique(user.getPhonenumber()));
    }

    @Override
    public boolean checkEmailUnique(SysUser user)
    {
        return StringUtils.isNull(userMapper.checkEmailUnique(user.getEmail()));
    }

    @Override
    public void checkUserAllowed(SysUser user)
    {
        if (StringUtils.isNotNull(user.getUserId()) && user.isAdmin())
        {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

   // ... existing code ...

    @Override
    public void checkUserDataScope(Long userId)
    {
        SysUser currentUser = new SysUser();
        currentUser.setUserId(SecurityUtils.getUserId());
        if (!currentUser.isAdmin())
        {
            SysUser user = new SysUser();
            user.setUserId(userId);
            List<SysUser> users = SpringUtils.getAopProxy(this).selectUserList(user);
            if (users.isEmpty())
            {
                throw new ServiceException("没有权限访问用户数据！");
            }
        }
    }

// ... existing code ...


    @Override
    @Transactional
    public int insertUser(SysUser user)
    {
        deptService.checkDeptDataScope(user.getDeptId());
        if (!checkUserNameUnique(user))
        {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !checkPhoneUnique(user))
        {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !checkEmailUnique(user))
        {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setCreateBy(SecurityUtils.getUsername());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        return userMapper.insertUser(user);
    }

    @Override
    @Transactional
    public boolean registerUser(SysUser user)
    {
        return userMapper.insertUser(user) > 0;
    }

    @Override
    @Transactional
    public int updateUser(SysUser user)
    {
        Long userId = user.getUserId();
        checkUserAllowed(user);
        checkUserDataScope(userId);
        deptService.checkDeptDataScope(user.getDeptId());
        if (!checkUserNameUnique(user))
        {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !checkPhoneUnique(user))
        {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !checkEmailUnique(user))
        {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUpdateBy(SecurityUtils.getUsername());
        return userMapper.updateUser(user);
    }

    @Override
    @Transactional
    public void insertUserAuth(Long userId, Long[] roleIds)
    {
        checkUserDataScope(userId);
        userRoleMapper.deleteUserRoleByUserId(userId);
        if (StringUtils.isNotEmpty(roleIds))
        {
            List<SysUserRole> list = new ArrayList<>();
            for (Long roleId : roleIds)
            {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                list.add(ur);
            }
            userRoleMapper.batchUserRole(list);
        }
    }

    @Override
    @Transactional
    public int updateUserStatus(SysUser user)
    {
        return userMapper.updateUserStatus(user.getUserId(), user.getStatus());
    }

    @Override
    @Transactional
    public int updateUserProfile(SysUser user)
    {
        return userMapper.updateUser(user);
    }

    @Override
    @Transactional
    public boolean updateUserAvatar(Long userId, String avatar)
    {
        return userMapper.updateUserAvatar(userId, avatar) > 0;
    }

    @Override
    @Transactional
    public void updateLoginInfo(Long userId, String loginIp, java.util.Date loginDate)
    {
        userMapper.updateLoginInfo(userId, loginIp, loginDate);
    }

    @Override
    @Transactional
    public int resetPwd(SysUser user)
    {
        return userMapper.resetUserPwd(user.getUserId(), user.getPassword());
    }

    @Override
    @Transactional
    public int resetUserPwd(Long userId, String password)
    {
        return userMapper.resetUserPwd(userId, password);
    }

    @Override
    @Transactional
    public int deleteUserById(Long userId)
    {
        userMapper.deleteUserById(userId);
        return userRoleMapper.deleteUserRoleByUserId(userId);
    }

    @Override
    @Transactional
    public int deleteUserByIds(Long[] userIds)
    {
        for (Long userId : userIds)
        {
            checkUserAllowed(new SysUser(userId));
            checkUserDataScope(userId);
        }
        userMapper.deleteUserByIds(userIds);
        return userRoleMapper.deleteUserRole(userIds);
    }

    @Override
    public String importUser(List<SysUser> userList, Boolean isUpdateSupport, String operName)
    {
        if (StringUtils.isNull(userList) || userList.size() == 0)
        {
            throw new ServiceException("导入用户数据不能为空！");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (SysUser user : userList)
        {
            try
            {
                SysUser u = userMapper.selectUserByUserName(user.getUserName());
                if (StringUtils.isNull(u))
                {
                    BeanValidators.validateWithException(validator, user);
                    deptService.checkDeptDataScope(user.getDeptId());
                    String password = configService.selectConfigByKey("sys.user.initPassword");
                    user.setPassword(SecurityUtils.encryptPassword(password));
                    user.setCreateBy(operName);
                    userMapper.insertUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 导入成功");
                }
                else if (isUpdateSupport)
                {
                    BeanValidators.validateWithException(validator, user);
                    checkUserAllowed(u);
                    checkUserDataScope(u.getUserId());
                    user.setUserId(u.getUserId());
                    user.setUpdateBy(operName);
                    userMapper.updateUser(user);
                    successNum++;
                    successMsg.append("<br/>" + successNum + "、账号 " + user.getUserName() + " 更新成功");
                }
                else
                {
                    failureNum++;
                    failureMsg.append("<br/>" + failureNum + "、账号 " + user.getUserName() + " 已存在");
                }
            }
            catch (Exception e)
            {
                failureNum++;
                String msg = "<br/>" + failureNum + "、账号 " + user.getUserName() + " 导入失败：";
                failureMsg.append(msg + e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new ServiceException(failureMsg.toString());
        }
        else
        {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
}
