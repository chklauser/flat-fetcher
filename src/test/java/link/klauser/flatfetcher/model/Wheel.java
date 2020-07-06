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

import link.klauser.flatfetcher.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

@Entity
@Table(indexes = @Index(columnList = "carId"))
@Getter
@Setter
@ToString(exclude = {"car"})
@NoArgsConstructor
public class Wheel extends BaseEntity {

    public Wheel(int size) {
        this.size = size;
    }

    @Column
    int size;

    @JoinColumn(name = "carId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;

    @Column(insertable = false, updatable = false)
    UUID carId;
}
