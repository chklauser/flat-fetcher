package link.klauser.flatfetcher.da;

import link.klauser.flatfetcher.model.Car;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {

}
