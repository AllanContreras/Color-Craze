package com.Color_craze.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(prefix = "redis", name = {"host", "port"})
    public RedisConnectionFactory redisConnectionFactory(org.springframework.core.env.Environment env) {
        String host = env.getProperty("redis.host", "localhost");
        int port = Integer.parseInt(env.getProperty("redis.port", "6379"));
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    @ConditionalOnProperty(prefix = "redis", name = {"host", "port"})
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
