package link.klauser.flatfetcher;

import javax.persistence.EntityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlatFetcherConfiguration {
	@Bean
	public FlatFetcher flatFetcher(EntityManager em) {
		return new FlatFetcher(em);
	}
}
