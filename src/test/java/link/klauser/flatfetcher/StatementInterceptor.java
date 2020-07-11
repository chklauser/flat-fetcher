package link.klauser.flatfetcher;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.EmptyInterceptor;
import org.springframework.test.context.event.annotation.BeforeTestExecution;

public class StatementInterceptor extends EmptyInterceptor {

	private final List<String> preparedStatements = new ArrayList<>();

	@Override
	public synchronized String onPrepareStatement(String sql) {
		preparedStatements.add(sql);
		return super.onPrepareStatement(sql);
	}

	public synchronized void reset() {
		preparedStatements.clear();
	}

	public List<String> getPreparedStatements() {
		return List.copyOf(preparedStatements);
	}

	@BeforeTestExecution
	public void beforeTest() {
		reset();
	}
}
