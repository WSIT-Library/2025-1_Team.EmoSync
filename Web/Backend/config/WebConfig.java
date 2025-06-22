package com.example.capstone.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private String connectPath = "/upload/**";
    final Path FILE_ROOT = Paths.get("./").toAbsolutePath().normalize();    // resources 폴더가 아닌 위치 일때 , 현재 위치를 지정함
    private String uploadPath = FILE_ROOT.toString() + "/upload/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry){
        registry.addResourceHandler(connectPath)                	    // src 에 들어갈 경로
                .addResourceLocations("file:///"+uploadPath);               // 파일 경로
    }
}
