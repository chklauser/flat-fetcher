package link.klauser.flatfetcher.model;

import java.util.Set;
import java.util.UUID;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

@Entity
@Getter
@Setter
@ToString(exclude = {"wheels", "doors", "engine"})
@NamedEntityGraph(name = "full", attributeNodes = {
        @NamedAttributeNode("wheels"),
        @NamedAttributeNode("doors"),
        @NamedAttributeNode("engine")
})
@NoArgsConstructor
public class Car extends BaseEntity {

    public Car(String name) {
        this.name = name;
    }

    @Column
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "car")
    @Access(AccessType.PROPERTY)
    private Set<Wheel> wheels;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "car")
    @Access(AccessType.PROPERTY)
    private Set<Door> doors;


    @JoinColumn(name = "engineId")
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    private Engine engine;

    @Column(name = "engineId", updatable = false, insertable = false)
    private UUID engineId;

}
