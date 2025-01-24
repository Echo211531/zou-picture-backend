package com.zr.yunbackend.common;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JWTUtil {

    // Token 过期时间设置为2天
    public static final long EXPIRE = 1000 * 60 * 60 * 24 * 2;
    // 加密的秘钥secretKey
    private static final SecretKey SECRET_KEY = Keys.
            secretKeyFor(SignatureAlgorithm.HS256); // 应该使用更安全的密钥，并从环境变量或配置文件加载
    //令牌前缀
    public static final String TOKEN_PREFIX = "ZouPicture:";
    // subject: 主题，也就是该 JWT 所面向的用户
    public static final String SUBJECT = "user";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //根据用户信息，生成唯一令牌
    public String generateJwt(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String jti = UUID.randomUUID().toString();  // 生成唯一的Token ID
        String token = Jwts.builder().setSubject(SUBJECT)  //设置主题（subject）
                //设置payload 载荷，保存用户信息
                .claim("id", user.getId())
                .claim("account", user.getUserAccount())
                .claim("name", user.getUserName())
                .claim("avatar", user.getUserAvatar())
                .claim("role", user.getUserRole())
                .claim("quota", user.getOutPaintingQuota())
                .setId(jti)
                .setIssuedAt(new Date())  //指定Token的签发时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE)) //设置了Token的过期时间
                .signWith(SECRET_KEY) // 使用预先定义的强密钥
                .compact();
        token = TOKEN_PREFIX + token;
        saveTokenToRedis(jti, token);   //保存token到redis中
        return token;
    }

    //保存Token到Redis
    //这里要保存去掉前缀的token，因为jwt格式不能出现:否则解析会出错
    private void saveTokenToRedis(String jti, String token) {
        stringRedisTemplate.opsForValue().set(jti, token.replace(TOKEN_PREFIX, ""),
                EXPIRE, TimeUnit.MILLISECONDS);
    }

    //校验token的方法，返回信息
    public Claims checkJwt(String token) {
        try {
            // 移除前缀后再进行验证
            String cleanToken = token.replace(TOKEN_PREFIX, "");
            final Claims claims = Jwts.parser()  //创建一个JWT解析器
                    .setSigningKey(SECRET_KEY)    // 使用预先定义的强密钥
                    .build()
                    .parseClaimsJws(cleanToken)  //移除前缀后的token
                    .getBody();  //获得包含在JWT中的声明对象

            // 验证Token是否过期
            Date expiration = claims.getExpiration();
            if (expiration.before(new Date())) {
                log.warn("Token已过期.");
                return null;
            }
            // 验证Token是否存在于Redis中
            String jti = claims.getId();
            String storedToken = stringRedisTemplate.opsForValue().get(jti);
            if (storedToken == null || !StringUtils.equals(storedToken, cleanToken)) {
                log.warn("Token不存在于Redis中.");
                return null;
            }
            return claims;
        } catch (Exception e) {   //解析过程遇到任何问题都会失败
            log.error("JWT token解析失败: {}", e.getMessage());
            return null;
        }
    }
}
