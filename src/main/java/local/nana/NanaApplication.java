package local.nana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NanaApplication {
    public static void main(String[] args) { SpringApplication.run(NanaApplication.class, args); }
}
