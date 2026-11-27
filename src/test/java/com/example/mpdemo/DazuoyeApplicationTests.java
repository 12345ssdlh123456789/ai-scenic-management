package com.example.mpdemo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Requires MySQL datasource and real DB connection")
class DazuoyeApplicationTests {

    @Test
    void contextLoads() {
    }
}
