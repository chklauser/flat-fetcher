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

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.NamedAttributeNode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedSubgraph;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

@Entity
@Table()
@Getter
@Setter
@ToString(exclude = {"car"})
@NamedEntityGraph(name = "EngineEntity.full",
        attributeNodes = {@NamedAttributeNode(value = "car", subgraph = "fullCar")},
        subgraphs = @NamedSubgraph(name = "fullCar", attributeNodes = {
                @NamedAttributeNode("wheels"),
                @NamedAttributeNode("doors")
        }))
public class Engine extends BaseEntity {

    @Column
    int power;

    @OneToOne(fetch = FetchType.LAZY, optional = true, mappedBy = "engine")
    @LazyToOne(LazyToOneOption.NO_PROXY)
    @Access(AccessType.PROPERTY)
    Car car;
}
