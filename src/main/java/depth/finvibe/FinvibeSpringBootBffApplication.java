package depth.finvibe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 🔥 추가

@SpringBootApplication
@EnableScheduling // 🔥 이거 추가하면 스케줄러 동작함
public class FinvibeSpringBootBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinvibeSpringBootBffApplication.class, args);
    }
}