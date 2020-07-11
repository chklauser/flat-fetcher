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
