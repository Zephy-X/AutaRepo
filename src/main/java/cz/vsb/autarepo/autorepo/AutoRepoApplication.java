package cz.vsb.autarepo.autorepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "cz.vsb.autarepo.autorepo.feature")
public class AutoRepoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoRepoApplication.class, args);
    }

}
