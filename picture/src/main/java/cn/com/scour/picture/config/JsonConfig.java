package cn.com.scour.picture.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置
 */
// Spring Boot 的注解，用于自定义 JSON 序列化/反序列化逻辑
@JsonComponent
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 1. 创建默认的 ObjectMapper（忽略 XML 配置）
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 2. 注册自定义序列化模块
        SimpleModule module = new SimpleModule();
        // 将 Long 类型序列化为字符串
        module.addSerializer(Long.class, ToStringSerializer.instance); //包装类型Long
        module.addSerializer(Long.TYPE, ToStringSerializer.instance); //基本类型long
        objectMapper.registerModule(module);
        return objectMapper;
    }
}