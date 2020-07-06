package link.klauser.flatfetcher;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaRepositories(basePackageClasses = FlatFetcherTestApplication.class)
@EnableTransactionManagement
@Import(FlatFetcherConfiguration.class)
public class FlatFetcherTestApplication {
}
