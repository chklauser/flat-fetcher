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
