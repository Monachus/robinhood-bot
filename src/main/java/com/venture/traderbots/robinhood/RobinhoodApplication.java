package com.venture.traderbots.robinhood;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import conrad.weiser.robinhood.api.RobinhoodApi;
import conrad.weiser.robinhood.api.throwables.RobinhoodApiException;


@SpringBootApplication
@EnableAsync
public class RobinhoodApplication {
	
	@Value("${username}")
    private String username;

	@Value("${password}")
    private String password;
	

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public static void main(String[] args) {
		for(String argument:args) {
			System.out.println("received boot argument"+argument);
		}
		SpringApplication.run(RobinhoodApplication.class, args);
	}
	
    @Bean
    public Executor taskExecutor() {
    	ExecutorService executor = Executors.newSingleThreadExecutor();
		return executor;
    }
	
	@Bean
	public RobinhoodApi getRobinhoodAPI() throws RobinhoodApiException {
		return new RobinhoodApi(username, password);
	}

}
