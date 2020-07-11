package link.klauser.flatfetcher.model;

import java.util.UUID;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

@Entity
@Getter
@Setter
@ToString(exclude = {"car"})
@Table(indexes = @Index(columnList = "carId"))
@NoArgsConstructor
public class Door extends BaseEntity {

    public Door(boolean open) {
        this.open = open;
    }

    @Column(nullable = false)
    boolean open;

    @JoinColumn(name = "carId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;

    @Column(name = "carId", insertable = false, updatable = false)
    UUID carId;
}
