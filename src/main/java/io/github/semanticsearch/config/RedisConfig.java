package io.github.semanticsearch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis cache serialization.
 *
 * <p>Deliberately small. spring-boot-starter-data-redis already builds the connection factory from
 * {@code spring.data.redis.*}, and {@code spring.cache.type=redis} already builds a cache manager
 * that picks up any {@link RedisCacheConfiguration} bean. Declaring a connection factory here as
 * well would shadow Boot's without replacing it, leaving two views of where Redis lives.
 *
 * <p>All this contributes is JSON value serialization, so cache entries are readable rather than
 * Java-serialized blobs. {@code @EnableCaching} lives on the application class.
 */
@Configuration
@ConditionalOnProperty(name = "cache.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

  @Bean
  public RedisCacheConfiguration redisCacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()));
  }
}
