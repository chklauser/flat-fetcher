// Copyright 2020 Christian Klauser
//
// Licensed under the Apache License,Version2.0(the"License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,software
// distributed under the License is distributed on an"AS IS"BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package link.klauser.flatfetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.util.Set;
import javax.persistence.EntityManager;

import link.klauser.flatfetcher.model.Car;
import link.klauser.flatfetcher.model.Door;
import link.klauser.flatfetcher.model.Engine;
import link.klauser.flatfetcher.model.Wheel;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("CodeBlock2Expr")
@ExtendWith(SpringExtension.class)
@Transactional(propagation = NOT_SUPPORTED)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.datasource.url=jdbc:h2:~/flatfetcher;AUTO_SERVER=TRUE")
@ActiveProfiles("test")
@Slf4j
public class FlatFetcherTest {

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private StatementInterceptor statementInterceptor;

	private TransactionTemplate rwTx;
	private TransactionTemplate roTx;

	@BeforeEach
	public void setUp() {
		rwTx = new TransactionTemplate(transactionManager);
		roTx = new TransactionTemplate(transactionManager);
		roTx.setReadOnly(true);
		rwTx.executeWithoutResult(s -> {
			deleteAll(Door.class);
			deleteAll(Wheel.class);
			deleteAll(Car.class);
			deleteAll(Engine.class);
		});
	}

	private <X> void deleteAll(Class<X> entityType) {
		var cb = em.getCriteriaBuilder();
		var deleteQ = cb.createCriteriaDelete(entityType);
		deleteQ.from(entityType);
		em.createQuery(deleteQ).executeUpdate();
	}

	@Autowired
	FlatFetcher flatFetcher;

	@Autowired
	EntityManager em;


	static void addMappedByReferences(Car car) {
		car.getWheels().forEach(w -> w.setCar(car));
		car.getDoors().forEach(d -> d.setCar(car));
	}

	@Test
	void fetchFullCar() {
		///// GIVEN ////
		testData1();

		statementInterceptor.reset();
		log.info("Test data set up; Fetching roots...");
		var rootCars = rwTx.execute(status ->
				em.createQuery("select c from Car c", Car.class).getResultList()
		);
		var rootStmts = statementInterceptor.getPreparedStatements();
		assertThat(rootCars).as("rootCars").isNotNull();

		///// WHEN /////
		statementInterceptor.reset();
		log.info("Roots fetched; fetching graph...");
		rwTx.executeWithoutResult(status -> {
			flatFetcher.fetch(Car.class, rootCars, "full");
		});
		log.info("Done fetching graph.");
		var fetchStmts = statementInterceptor.getPreparedStatements();

		///// THEN /////
		assertThat(rootCars).allSatisfy(rootCar -> {
			assertThat(rootCar.getDoors()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
			assertThat(rootCar.getWheels()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
			assertThat(rootCar.getEngine()).isNotNull();
		});

		SoftAssertions.assertSoftly(s -> {
			s.assertThat(rootStmts).as("SQL statements to fetch roots")
					.hasSize(1);
			s.assertThat(fetchStmts).as("SQL statements to fetch graph")
					.hasSize(3);
		});

	}

	@Test
	void fetchFullGraphFromEngines() {
		///// GIVEN ////
		testData1();

		statementInterceptor.reset();
		log.info("Test data set up; Fetching roots...");
		var rootEngines = rwTx.execute(status -> {
			var result = em.createQuery("select e from Engine e", Engine.class).getResultList();
			result.forEach(em::detach);
			return result;
		});
		var rootStmts = statementInterceptor.getPreparedStatements();
		assertThat(rootEngines).as("rootEngines").isNotNull();

		///// WHEN /////
		statementInterceptor.reset();
		log.info("Roots fetched; fetching graph...");
		roTx.executeWithoutResult(status -> {
			flatFetcher.fetch(Engine.class, rootEngines, "EngineEntity.full");
		});
		log.info("Done fetching graph.");
		var fetchStmts = statementInterceptor.getPreparedStatements();

		///// THEN /////
		assertThat(rootEngines).allSatisfy(rootEngine -> {
			assertThat(rootEngine.getCar()).isNotNull();
			var car = rootEngine.getCar();
			assertThat(car.getDoors()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
			assertThat(car.getWheels()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
			assertThat(car.getEngine()).isNotNull();
		});

		SoftAssertions.assertSoftly(s -> {
			s.assertThat(rootStmts).as("SQL statements to fetch roots")
					.hasSize(1);
			s.assertThat(fetchStmts).as("SQL statements to fetch graph")
					.hasSize(3);
		});
	}

	private void testData1() {
		rwTx.executeWithoutResult(status -> {
			var car1 = new Car("limousine");
			car1.setDoors(Set.of(
					new Door(true),
					new Door(true),
					new Door(true),
					new Door(true),
					new Door(true)
			));
			car1.setEngine(new Engine());
			car1.getEngine().setPower(207);
			car1.getEngine().setCar(car1);
			car1.setWheels(Set.of(
					new Wheel(20),
					new Wheel(20),
					new Wheel(22),
					new Wheel(22)
			));
			addMappedByReferences(car1);

			var car2 = new Car("trike");
			car2.setDoors(Set.of(
					new Door(false),
					new Door(false)
			));
			car2.setEngine(new Engine());
			car2.getEngine().setPower(71);
			car2.getEngine().setCar(car2);
			car2.setWheels(Set.of(
					new Wheel(18),
					new Wheel(19),
					new Wheel(19)
			));
			addMappedByReferences(car2);

			em.persist(car1);
			em.persist(car2);
		});
	}
}
