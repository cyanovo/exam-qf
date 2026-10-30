package com.cyan.exam.framework.security.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.cyan.exam.common.core.domain.model.LoginUser;
import com.cyan.exam.common.utils.SecurityUtils;
import com.cyan.exam.common.utils.StringUtils;
import com.cyan.exam.framework.web.service.TokenService;

/**
 * token过滤器 验证token有效性
 * 
 * @author ruoyi
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter
{
    @Autowired
    private TokenService tokenService;
    @Autowired
    private com.cyan.exam.system.service.ISysUserService userService;
    @Autowired
    private com.cyan.exam.framework.web.service.SysPermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (loginUser != null) {
            var current = userService.selectUserById(loginUser.getUserId());
            if (current == null || !"0".equals(current.getStatus()) || !"0".equals(current.getDelFlag())) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"账号不可用\"}");
                return;
            }
            // Reload role membership and status so revocation applies to existing sessions.
            current.setRoles(current.getRoles().stream().filter(role -> "0".equals(role.getStatus())).toList());
            loginUser.setUser(current);
            loginUser.setPermissions(permissionService.getMenuPermission(current));
            String path = request.getServletPath();
            boolean allowed = ("GET".equals(request.getMethod()) && java.util.Set.of("/getInfo", "/getRouters").contains(path))
                    || ("PUT".equals(request.getMethod()) && "/system/user/profile/updatePwd".equals(path));
            if (current.getPwdUpdateDate() == null && !allowed) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":428,\"msg\":\"首次登录必须修改初始密码\"}");
                return;
            }
        }
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNull(SecurityUtils.getAuthentication()))
        {
            tokenService.verifyToken(loginUser);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        chain.doFilter(request, response);
    }
}
