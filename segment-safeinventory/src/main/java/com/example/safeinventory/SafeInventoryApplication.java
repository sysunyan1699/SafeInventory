package com.example.safeinventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.safeinventory.mapper")
public class SafeInventoryApplication {
	public static void main(String[] args) {
		SpringApplication.run(SafeInventoryApplication.class, args);
	}

}
