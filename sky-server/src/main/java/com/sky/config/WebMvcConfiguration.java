package com.sky.config;

import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override // 补充@Override注解（原代码遗漏）
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器..");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**") // 拦截所有/admin开头的请求
                .excludePathPatterns("/admin/employee/login"); // 排除登录接口（无需token）
    }

    /**
     * 兼容 Spring Boot 2.6+ 与 Springfox 3.x
     * 关闭 PathPatternParser，回归到 AntPathMatcher
     */
    @Override
    protected void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setPatternParser(null);
    }

    /**
     * Springfox 3.0.0 + Spring Boot 2.6+ 兼容性处理
     * 过滤使用 PathPatternParser 的处理器映射，避免空指针异常
     */
    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
        Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
        if (field == null) {
            return Collections.emptyList();
        }
        field.setAccessible(true);
        try {
            return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void customizeSpringfoxHandlerMappings(List<RequestMappingInfoHandlerMapping> mappings) {
        List<RequestMappingInfoHandlerMapping> copy = mappings.stream()
                .filter(mapping -> mapping.getPatternParser() == null)
                .collect(Collectors.toList());
        mappings.clear();
        mappings.addAll(copy);
    }

    /**
     * 通过knife4j生成接口文档
     * @return Docket 接口文档配置对象
     */
    @Bean
    public Docket docket() {
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("苍穹外卖项目接口文档")
                .version("2.0")
                .description("苍穹外卖项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.sky.controller")) // 扫描controller包下的接口
                .paths(PathSelectors.any()) // 匹配所有路径
                .build();
        return docket;
    }

    /**
     * 设置静态资源映射（支持knife4j文档访问）
     * @param registry 资源处理器注册器
     */
    @Override // 补充@Override注解（原代码遗漏）
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射doc.html到META-INF/resources目录
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        // 映射webjars资源
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器");
        MappingJackson2CborHttpMessageConverter converter = new MappingJackson2CborHttpMessageConverter();
        converter.setObjectMapper(new JacksonObjectMapper());
        converters.add(0,converter);



    }
}