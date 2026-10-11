package cz.vsb.autarepo.autorepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "cz.vsb.autarepo.autorepo.feature")
@EnableScheduling
public class AutoRepoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoRepoApplication.class, args);
    }

}
