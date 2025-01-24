package com.zr.yunbackend.config;
import com.zr.yunbackend.common.JWTUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private JWTUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从请求头中获取jwt token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith(JWTUtil.TOKEN_PREFIX)) {
            Claims claims = jwtUtil.checkJwt(token);
            if (claims != null) {
                // 如果有有效的Token，则设置到当前请求的currentUser，并允许请求继续
                request.setAttribute("currentUser", claims);
                return true;
            } else {
                // Token无效或过期，返回401状态码并终止请求
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return false;
            }
        }
        //如果没有token，正常放行，后端做了用户的权限校验
        return true;
    }
}