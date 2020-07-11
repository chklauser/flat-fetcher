package link.klauser.flatfetcher;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaRepositories(basePackageClasses = FlatFetcherTestApplication.class)
@EnableTransactionManagement
@Import(FlatFetcherConfiguration.class)
public class FlatFetcherTestApplication {

	@Bean
	public StatementInterceptor statementInterceptor() {
		return new StatementInterceptor();
	}

	@Bean
	public HibernatePropertiesCustomizer interceptorRegistration() {
		return hibernateProperties -> hibernateProperties.put("hibernate.session_factory.interceptor", statementInterceptor());
	}
}
